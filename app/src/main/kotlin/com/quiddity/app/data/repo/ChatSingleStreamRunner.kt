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
    /** ??????"???"???finish_reason=length / response.incomplete?? */
    val truncated: Boolean = false,
    /** ???????????????????? / AI ???????? */
    val hasContent: Boolean = false
)

private const val MAX_FINAL_RECOVERY_ROUNDS = 3

private const val TRUNCATE_CONTINUE_NUDGE =
    "?????????????????????????????????????????????????"

private const val EMPTY_REPLY_NUDGE =
    "?????????????????????????????????????????????????"

private const val DEEPSEEK_THINKING_UNAVAILABLE_REPORT = "deepseek??????????????????"

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

    /** 无工具调用轮次的兜底处理结果。 */
    private sealed interface NoToolRoundResult {
        /** 已获得有效正文，可正常收尾。 */
        data object Completed : NoToolRoundResult
        /** 重试后模型重新发起工具调用，需要外层循环继续执行。 */
        data class WithToolCalls(
            val round: StreamRoundResult,
            val request: ChatRoundRequest
        ) : NoToolRoundResult
        /** 多次兜底仍失败，失败消息已派发。 */
        data object Failed : NoToolRoundResult
    }

    /**
     * 最终回复兜底：
     * - 截断（finish_reason=length / response.incomplete）：自动续写，直到得到完整正文；
     * - 空回复（AI 撤回 / 未输出正文）：自动重试，直到得到正文或工具调用。
     * 每次尝试把「部分正文 + 提示语」或仅「提示语」并入请求重新请求，
     * 新正文通过 [StreamCoordinator.setMergeWithPrevious] 合并进上一条 AI 消息。
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
