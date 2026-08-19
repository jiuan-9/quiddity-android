package com.quiddity.app.data.repo

import com.quiddity.app.data.local.AgentPermissionControl
import com.quiddity.app.data.local.AgentStore
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import com.quiddity.app.data.remote.ChatApi
import com.quiddity.app.data.remote.ChatMessage
import com.quiddity.app.data.remote.ChatStreamParser
import com.quiddity.app.data.remote.ResponsesInputItem
import com.quiddity.app.data.remote.ResponsesTool
import com.quiddity.app.data.remote.ToolDefinition
import com.quiddity.app.data.repo.ConversationRepository
import com.quiddity.app.data.repo.SettingsRepository
import com.quiddity.app.domain.ApiCatalogManager
import com.quiddity.app.domain.StreamCoordinator
import com.quiddity.app.domain.agent.AgentRoundEffects
import com.quiddity.app.domain.agent.AgentToolRegistry
import com.quiddity.app.domain.agent.AgentWorkflowController
import com.quiddity.app.util.IdGenerator

internal data class StreamRoundResult(
    val toolCalls: List<ChatStreamParser.AggregatedToolCall>,
    val reasoningText: String,
    /** 本轮是否被截断（finish_reason=length / response.incomplete）。 */
    val truncated: Boolean = false,
    /** 本轮是否输出了正文（区别于 AI 撤回 / 未输出）。 */
    val hasContent: Boolean = false
) {
    /**
     * 本轮是否已产出可交付的完整正文：有内容且未被截断。
     * 工具轮收尾以此为准——完整回复直接收尾，不允许再触发兜底续写/重试。
     */
    val isCompleteReply: Boolean get() = hasContent && !truncated
}

private const val MAX_FINAL_RECOVERY_ROUNDS = 3

private const val TRUNCATE_CONTINUE_NUDGE =
    "（你的回复因达到长度上限被截断。请直接从断点继续输出，不要重复已经输出的内容，直接给出后续正文。）"

private const val EMPTY_REPLY_NUDGE =
    "（你刚才没有输出有效回复。请基于已有信息直接给出完整回复；若任务尚未完成，请继续完成并说明结果。）"

private const val DEEPSEEK_THINKING_UNAVAILABLE_REPORT = "deepseek本身模型有概率不配合，思考内容不返回"

internal class SingleStreamRunner(
    private val api: ChatApi,
    private val toolResultBuilder: ToolResultBuilder
) {

    suspend fun runSingleStream(
        api: ChatApi,
        apiUrl: String,
        apiKey: String,
        request: ChatRoundRequest,
        coordinator: StreamCoordinator,
        onEvent: suspend (ChatRepository.Event) -> Unit,
        contentTransform: (String) -> String = { it },
        thinkingActive: Boolean = true,
        onFailure: (suspend (Throwable) -> Unit)? = null
    ): StreamRoundResult? {
        var toolCalls: List<ChatStreamParser.AggregatedToolCall> = emptyList()
        val reasoningText = StringBuilder()
        var truncated = false
        val contentLenBefore = coordinator.snapshot().sumOf { it.content.length }
        try {
            // 打字机延迟已在 UI 层（MessageBubble）按字渲染实现，
            // 此处不再阻塞流式消费——避免大 delta 时 API 缓冲区堆积、
            // 网络层超时，以及"逐字渲染无感"的问题（旧实现按 delta 整段延迟，
            // delta 较大时用户看到的是整段跳出而非逐字浮现）。
            val stream = when (request) {
                is ChatRoundRequest.Completions -> api.streamChat(apiUrl, apiKey, request.request)
                is ChatRoundRequest.Responses -> api.streamResponses(apiUrl, apiKey, request.request)
            }
            stream.collect { event ->
                when (event) {
                    is ChatApi.StreamEvent.Content -> {
                        val evictions = coordinator.accept(contentTransform(event.text))
                        evictions.forEach { toolResultBuilder.dispatch(onEvent, it) }
                    }
                    is ChatApi.StreamEvent.Reasoning -> {
                        // 思考原文不展示（由提示词引导的【思考】标记负责展示），
                        // 但必须累积：DeepSeek 思考模式下工具轮第二轮请求需要回传 reasoning_text，
                        // 否则服务端以 HTTP 400 拒绝（"The reasoning_text in the thinking mode must be passed back"）。
                        reasoningText.append(event.text)
                    }
                    is ChatApi.StreamEvent.ToolCalls -> {
                        if (event.calls.isNotEmpty()) toolCalls = event.calls
                    }
                    is ChatApi.StreamEvent.Truncated -> {
                        truncated = true
                    }
                }
            }
            // 流结束，强制收尾
            coordinator.finalize().forEach { toolResultBuilder.dispatch(onEvent, it) }
            // 网关未返回截断信号时，用内容形态兜底：以冒号/逗号/未闭合括号引号结尾，
            // 几乎必然是"话没说完"，不静默吞掉
            if (!truncated) {
                val lastContent = coordinator.snapshot().lastOrNull()?.content.orEmpty()
                if (looksTruncated(lastContent)) truncated = true
            }
            // 截断信号在收尾之后派发：先保证半截内容已落盘，再提示用户内容不完整
            if (truncated) {
                onEvent(ChatRepository.Event.Truncated)
            }
        } catch (c: kotlinx.coroutines.CancellationException) {
            // 用户停止 / 页面销毁：不当作错误，向上传播取消，由上层收尾半截消息
            throw c
        } catch (t: Throwable) {
            toolResultBuilder.emitError(onEvent, t, coordinator.snapshot().joinToString("\n") { it.content })
            onFailure?.invoke(t)
            return null
        }
        val contentLenAfter = coordinator.snapshot().sumOf { it.content.length }
        return StreamRoundResult(
            toolCalls = toolCalls,
            reasoningText = reasoningText.toString(),
            truncated = truncated,
            hasContent = contentLenAfter > contentLenBefore
        )
    }

    /**
     * 回复失败兜底：在聊天界面追加一条可见的错误消息（isError 样式），
     * 避免工具已执行 / 请求失败后模型静默无话、用户只看到一闪而过的 Toast。
     */
    suspend fun appendFailureMessage(
        conv: Conversation,
        coordinator: StreamCoordinator,
        onEvent: suspend (ChatRepository.Event) -> Unit,
        text: String
    ) {
        if (coordinator.snapshot().any { it.isError && it.content == text }) return
        val msg = Message(
            id = IdGenerator.newId(IdGenerator.Prefix.AI_MESSAGE),
            conversationId = conv.id,
            role = Role.ASSISTANT,
            content = text,
            timestamp = System.currentTimeMillis(),
            isError = true,
            isStreaming = false
        )
        onEvent(ChatRepository.Event.NewMessage(msg))
        onEvent(ChatRepository.Event.CompleteMessage(msg))
    }

    /**
     * DeepSeek 思考模式兜底：整轮未产出任何思考内容时，在首条正式回复上附报告，
     * 保证开启思考后气泡内始终有可展开的内容，而不是静默空白。
     */
    suspend fun attachThinkingUnavailableReportIfNeeded(
        request: ChatRoundRequest,
        coordinator: StreamCoordinator,
        thinkingActive: Boolean,
        onEvent: suspend (ChatRepository.Event) -> Unit
    ) {
        val snapshot = coordinator.snapshot()
        if (!thinkingActive || !isDeepSeekModel(request)) return
        if (snapshot.any { it.thinking.isNotBlank() }) return
        val target = snapshot.firstOrNull {
            it.role == Role.ASSISTANT && !it.isNotice && !it.isThinking && it.content.isNotBlank()
        } ?: return
        onEvent(ChatRepository.Event.UpdateMessage(target.copy(thinking = DEEPSEEK_THINKING_UNAVAILABLE_REPORT)))
    }

    fun isDeepSeekModel(request: ChatRoundRequest): Boolean {
        val model = when (request) {
            is ChatRoundRequest.Completions -> request.request.model
            is ChatRoundRequest.Responses -> request.request.model
        }
        return model.contains("deepseek", ignoreCase = true)
    }

}
