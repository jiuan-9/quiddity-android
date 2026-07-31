package com.quiddity.app.data.repo

import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import com.quiddity.app.data.remote.ChatApi
import com.quiddity.app.data.remote.ChatCompletionRequest
import com.quiddity.app.data.remote.ChatException
import com.quiddity.app.data.remote.ChatMessage
import com.quiddity.app.domain.ChatError
import com.quiddity.app.domain.MessageStreamCoordinator
import com.quiddity.app.domain.PromptBuilder
import com.quiddity.app.domain.StreamCoordinator
import com.quiddity.app.util.IdGenerator
import com.quiddity.app.util.QuiddityConstants

/**
 * 对话仓库：负责发起流式 API 请求，向上层暴露为 Flow<ChatStreamEvent>。
 *
 * 仓库本身只关心：构造请求、调用 API、把原始 delta 喂给协调器、把协调器产出
 * 的事件透传给上层。协调器通过 [coordinatorFactory] 注入：测试时可注入假协调器，
 * 运行时默认使用按 token + `\n\n` 切分的 [MessageStreamCoordinator]。
 */
class ChatRepository(
    private val api: ChatApi,
    private val conversationRepo: ConversationRepository,
    private val settingsRepo: SettingsRepository,
    /**
     * 协调器工厂。默认使用 [MessageStreamCoordinator]。
     * 每轮新 run 都注入新 runId（基于 UUID），保证消息 id 全局唯一。
     */
    private val coordinatorFactory: (conversationId: String, runId: String, singleMessageTokens: Int) -> StreamCoordinator =
        { conversationId, runId, singleMessageTokens ->
            MessageStreamCoordinator(conversationId, runId, singleMessageTokens)
        }
) {

    /** 对外暴露的流式事件。 */
    sealed class Event {
        /** 新消息创建（含初始空 streaming 消息）。 */
        data class NewMessage(val message: Message) : Event()
        /** 当前流式消息内容更新。 */
        data class UpdateMessage(val message: Message) : Event()
        /** 一条消息完成。 */
        data class CompleteMessage(val message: Message) : Event()
        /** 整个流结束。 */
        data object Done : Event()
        /** 错误。 */
        data class Error(val throwable: Throwable, val partialContent: String) : Event()
    }

    /**
     * 发送用户消息并启动流式回复。
     *
     * @param conv 当前会话
     * @param history 历史消息列表（含最新用户消息）
     * @param onEvent suspend 事件回调（由 ViewModel 串行化执行）
     */
    suspend fun streamAssistantReply(
        conv: Conversation,
        history: List<Message>,
        onEvent: suspend (Event) -> Unit
    ) {
        val settings = settingsRepo.currentSnapshot()
        val access = ApiAccess.resolve(settings, conv)
        if (access is ApiAccess.Failure) {
            return emitError(onEvent, access.toChatException(), "")
        }
        access as ApiAccess.Resolved

        val systemPrompt = PromptBuilder.buildSystemPrompt(
            conv = conv,
            multilineAutoSplit = settings.multilineAutoSplit
        )
        val contextLimit = if (conv.contextLimit > 0) conv.contextLimit else settings.globalContextLimit
        // 过滤 isNotice 提示气泡：不发给 LLM（UI 专用，非对话内容）
        val filteredHistory = history.filterNot { it.isNotice }
        val trimmedHistory = takeLastRounds(filteredHistory, contextLimit, buffer = 4)
        val apiMessages = PromptBuilder.toApiMessages(systemPrompt, trimmedHistory)

        val maxTokens = conv.maxTokens ?: settings.globalMaxTokens
        val singleMsgTokens = conv.singleMessageTokens ?: settings.globalSingleMessageTokens

        val request = ChatCompletionRequest(
            model = access.model,
            messages = apiMessages,
            max_tokens = maxTokens,
            temperature = 0.8,
            stream = true
        )

        val coordinator = coordinatorFactory(
            conv.id,
            // runId 是协调器内部标签，使用不带前缀的 UUID 即可；
            // 最终消息 ID 由 MessageStreamCoordinator 拼装为 `{convId}_{runId}_ai_{index}`。
            IdGenerator.newUuid(),
            if (settings.multilineAutoSplit) singleMsgTokens else Int.MAX_VALUE
        )

        runStream(api, access.apiUrl, access.apiKey, request, coordinator, onEvent)
    }

    /**
     * 让 AI 先发消息（空对话开场）。
     */
    suspend fun letAiStart(
        conv: Conversation,
        onEvent: suspend (Event) -> Unit
    ) {
        val settings = settingsRepo.currentSnapshot()
        val access = ApiAccess.resolve(settings, conv)
        if (access is ApiAccess.Failure) {
            return emitError(onEvent, access.toChatException(), "")
        }
        access as ApiAccess.Resolved

        val systemPrompt = PromptBuilder.buildSystemPrompt(
            conv = conv,
            multilineAutoSplit = settings.multilineAutoSplit
        )
        // 引导：让 AI 主动发起对话
        val guidedMessages = listOf(
            ChatMessage(role = "system", content = systemPrompt),
            ChatMessage(role = "user", content = PromptBuilder.LET_AI_START_GUIDE)
        )

        val maxTokens = conv.maxTokens ?: settings.globalMaxTokens
        val singleMsgTokens = conv.singleMessageTokens ?: settings.globalSingleMessageTokens

        val request = ChatCompletionRequest(
            model = access.model,
            messages = guidedMessages,
            max_tokens = maxTokens,
            temperature = 0.8,
            stream = true
        )

        val coordinator = coordinatorFactory(
            conv.id,
            IdGenerator.newUuid(),
            if (settings.multilineAutoSplit) singleMsgTokens else Int.MAX_VALUE
        )

        runStream(api, access.apiUrl, access.apiKey, request, coordinator, onEvent)
    }

    /**
     * 通用流式驱动：所有公开方法（用户消息 / AI 开场）共用此核心，
     * 由 [StreamCoordinator] 负责正确的 NewMessage/UpdateMessage/CompleteMessage 派发。
     */
    private suspend fun runStream(
        api: ChatApi,
        apiUrl: String,
        apiKey: String,
        request: ChatCompletionRequest,
        coordinator: StreamCoordinator,
        onEvent: suspend (Event) -> Unit
    ) {
        try {
            // 打字机延迟已在 UI 层（MessageBubble）按字渲染实现，
            // 此处不再阻塞流式消费——避免大 delta 时 API 缓冲区堆积、
            // 网络层超时，以及"逐字渲染无感"的问题（旧实现按 delta 整段延迟，
            // delta 较大时用户看到的是整段跳出而非逐字浮现）。
            api.streamChat(apiUrl, apiKey, request).collect { delta ->
                val evictions = coordinator.accept(delta)
                evictions.forEach { dispatch(onEvent, it) }
            }
            // 流结束，强制收尾
            coordinator.finalize().forEach { dispatch(onEvent, it) }
            onEvent(Event.Done)
        } catch (t: Throwable) {
            emitError(onEvent, t, coordinator.snapshot().joinToString("\n") { it.content })
        }
    }

    private suspend fun dispatch(onEvent: suspend (Event) -> Unit, signal: StreamCoordinator.Signal) {
        when (signal) {
            is StreamCoordinator.Signal.New -> onEvent(Event.NewMessage(signal.message))
            is StreamCoordinator.Signal.Update -> onEvent(Event.UpdateMessage(signal.message))
            is StreamCoordinator.Signal.Complete -> onEvent(Event.CompleteMessage(signal.message))
        }
    }

    private suspend fun emitError(onEvent: suspend (Event) -> Unit, t: Throwable, partial: String) {
        onEvent(Event.Error(t, partial))
    }

    /**
     * 人设精调：把当前会话的人设字段交给 AI 精调为结构化系统提示词。
     *
     * 设计要点：
     * - 使用当前会话的模型配置（[conv.apiCatalogId] 优先，否则全局 active）
     * - 调用 [ChatApi.completeNonStreaming] 非流式接口，system 提示词为 [PromptBuilder.PERSONA_REFINE_SYSTEM_PROMPT]
     * - 仅精调 期望特质/身份背景/性格/外观；名字、世界背景不参与（由 buildSystemPrompt 透传）
     * - 失败时抛异常，由上层（ViewModel）决定是否降级为原始字段拼接
     * - 成功时返回精调后文本，由 ViewModel 写入 [com.quiddity.app.data.model.Persona.compiledPersona]
     * - [maxOutputTokens] 写入用户消息，要求模型在不曲解原意的前提下控制输出长度
     *
     * @param conv 当前会话（用于读取 persona 字段与模型配置）
     * @param maxOutputTokens 期望模型输出的最大 token 数（仅作为提示词约束，非 API max_tokens）
     * @return 精调后的系统提示词文本
     * @throws ChatException 模型接口调用失败或密钥错误
     * @throws IllegalStateException 未配置模型配置 / 人设字段全空
     */
    suspend fun compilePersona(conv: Conversation, maxOutputTokens: Int): String {
        val settings = settingsRepo.currentSnapshot()
        val access = ApiAccess.resolve(settings, conv)
        if (access is ApiAccess.Failure) {
            when (access.reason) {
                ApiAccess.Failure.Reason.KEY_NOT_CONFIGURED ->
                    throw IllegalStateException("接口密钥未配置")
                else ->
                    throw ChatException(access.userMessage, access.cause)
            }
        }
        access as ApiAccess.Resolved

        // 精调仅处理 期望特质/身份背景/性格/外观；名字、世界背景不参与精调（由 buildSystemPrompt 透传）
        val refineInput = PromptBuilder.buildPersonaRefineInput(conv.persona)
        if (refineInput.isBlank()) {
            throw IllegalStateException("人设字段全为空，无需精调")
        }
        val userContent = refineInput + PromptBuilder.buildPersonaRefineSuffix(maxOutputTokens)

        return api.completeNonStreaming(
            apiUrl = access.apiUrl,
            apiKey = access.apiKey,
            model = access.model,
            systemPrompt = PromptBuilder.PERSONA_REFINE_SYSTEM_PROMPT,
            userContent = userContent,
            maxTokens = QuiddityConstants.PERSONA_COMPILE_MAX_TOKENS,
            temperature = QuiddityConstants.PERSONA_COMPILE_TEMPERATURE,
            emptyError = "人设精调返回空内容"
        )
    }

    /**
     * 把任意异常归类为 [ChatError]，供上层做差异化错误处理。
     * - 抛出的 ChatException 通常是接口错误或网络错误。
     * - IllegalStateException 通常是配置错误（如未配置模型配置）。
     * - 其他统一归类为 Unknown。
     */
    fun classify(t: Throwable): ChatError = when (t) {
        is IllegalStateException -> ChatError.Config(
            userMessage = t.message ?: "配置错误",
            cause = t
        )
        is ChatException -> {
            val msg = t.message ?: "接口错误"
            classifyChatException(msg, t)
        }
        else -> ChatError.Unknown(
            userMessage = t.message ?: "未知错误",
            cause = t
        )
    }

    /**
     * 压缩对话记忆。
     *
     * 将历史对话 + 上一次的压缩摘要发送给 AI，让 AI 提取关键信息：
     * - 数字、ID、日期、金额、专有名词一字不差保留
     * - 修饰词、客套话、重复解释全部删掉
     * - 解决指代不明
     * - 严禁添加原文没有的信息
     *
     * 压缩结果替代原始历史发送给 API，节省 Token。
     *
     * @param conv 当前会话（用于读取模型配置和已有压缩摘要）
     * @param messages 所有历史消息
     * @return 压缩后的记忆摘要文本
     */
    suspend fun compressConversationMemory(
        conv: Conversation,
        messages: List<Message>
    ): String {
        val settings = settingsRepo.currentSnapshot()
        val access = ApiAccess.resolve(settings, conv)
        access as? ApiAccess.Resolved
            ?: throw IllegalStateException("API 未配置，无法压缩记忆")

        // 过滤 isNotice 提示气泡：不参与压缩（UI 专用，非对话内容）
        val filteredMessages = messages.filterNot { it.isNotice }

        // 仅压缩上次压缩后的新消息：
        // 取"从第 lastCompressedAtRound 轮开始"的全部消息——以 USER 消息为锚点，
        // 自动适配"继续说"产生的多条 AI 消息、"延迟发送"在 AI 消息后追加的 USER 消息等场景。
        val newMessages = if (conv.lastCompressedAtRound > 0) {
            takeFromRound(filteredMessages, conv.lastCompressedAtRound)
        } else {
            filteredMessages
        }
        val userContent = PromptBuilder.buildCompressionUserPrompt(conv.compressedMemory, newMessages)

        // 压缩使用独立 system 提示词 + 低温，确保忠实提取、抑制发挥
        return api.completeNonStreaming(
            apiUrl = access.apiUrl,
            apiKey = access.apiKey,
            model = access.model,
            systemPrompt = PromptBuilder.COMPRESSION_SYSTEM_PROMPT,
            userContent = userContent,
            maxTokens = QuiddityConstants.COMPRESSION_MAX_TOKENS,
            temperature = QuiddityConstants.COMPRESSION_TEMPERATURE,
            emptyError = "记忆压缩返回空内容"
        )
    }

    /**
     * 快速设定：一次性生成 AI 人设 / 用户人设 / 场景设置 / 记忆设置四块结构化内容。
     *
     * - 复用 [completeNonStreaming]，与精调/压缩同一非流式入口；
     * - API 未配置时抛 [IllegalStateException]，调用方据此提示「API 未配置」；
     * - 系统提示词与 user 消息由 [QuickSetupPrompt] 提供，输出由调用方解析。
     *
     * @param conv 当前会话（用于读取模型配置）
     * @param userDescription 用户的人设描述（可能十分模糊）
     * @param tier 档位（决定字段清单与字数上限，与模型等级锁定）
     * @return LLM 返回的结构化文本（调用方用 [QuickSetupPrompt.parseQuickSetupResult] 解析）
     */
    suspend fun quickSetup(
        conv: Conversation,
        userDescription: String,
        tier: com.quiddity.app.domain.QuickSetupTier
    ): String {
        val settings = settingsRepo.currentSnapshot()
        val access = ApiAccess.resolve(settings, conv)
        if (access is ApiAccess.Failure) {
            when (access.reason) {
                ApiAccess.Failure.Reason.KEY_NOT_CONFIGURED ->
                    throw IllegalStateException("API 未配置")
                else ->
                    throw ChatException(access.userMessage, access.cause)
            }
        }
        access as ApiAccess.Resolved

        val userContent = com.quiddity.app.domain.QuickSetupPrompt
            .buildQuickSetupUserPrompt(userDescription, tier)

        return api.completeNonStreaming(
            apiUrl = access.apiUrl,
            apiKey = access.apiKey,
            model = access.model,
            systemPrompt = com.quiddity.app.domain.QuickSetupPrompt.QUICK_SETUP_SYSTEM_PROMPT,
            userContent = userContent,
            maxTokens = QuiddityConstants.QUICK_SETUP_MAX_TOKENS,
            temperature = QuiddityConstants.QUICK_SETUP_TEMPERATURE,
            emptyError = "快速设定返回空内容"
        )
    }

    private fun classifyChatException(msg: String, t: Throwable): ChatError {
        val lower = msg.lowercase()
        return when {
            "unauthorized" in lower || "401" in lower || "api key" in lower || "forbidden" in lower ->
                ChatError.Auth(userMessage = msg, cause = t)
            "timeout" in lower || "connect" in lower || "socket" in lower ->
                ChatError.Network(userMessage = msg, cause = t)
            else -> {
                // 尝试从 "HTTP 4xx/5xx: xxx" 中提取状态码
                val httpCode = Regex("""HTTP\s+(\d{3})""").find(msg)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ChatError.Api(userMessage = msg, httpCode = httpCode, cause = t)
            }
        }
    }

    /**
     * 取"最后 N 轮"对话（用于上下文裁剪）。
     *
     * 「轮」以 USER 消息为锚点。返回的子列表包含最后 N 个 USER 消息
     * 以及它们之间 / 之后的所有消息，确保不切断任何一轮的上下文。
     *
     * 适配"继续说"与"延迟发送"等导致单轮含多条消息的场景：
     * - "继续说"：AI 单轮会产生多条 ASSISTANT 消息，本函数会一并保留
     * - "延迟发送"：USER 单轮可能产生多条 USER 消息，本函数按"轮"计不按"条"计
     *
     * @param messages 全量历史消息（按时间正序）
     * @param rounds 要保留的轮数（按 USER 消息数计）
     * @param buffer 额外向前取的 buffer 消息数（保留上一轮 AI 回复的尾巴）
     * @return 截取后的子列表（顺序不变）；rounds <= 0 或消息为空时返回空列表
     */
    private fun takeLastRounds(
        messages: List<Message>,
        rounds: Int,
        buffer: Int = 0
    ): List<Message> {
        if (rounds <= 0 || messages.isEmpty()) return emptyList()
        val userIndices = messages.withIndex()
            .filter { it.value.role == Role.USER }
            .map { it.index }
        if (userIndices.isEmpty()) return emptyList()
        if (rounds >= userIndices.size) return messages
        val startUserIdx = userIndices[userIndices.size - rounds]
        val startIdx = (startUserIdx - buffer).coerceAtLeast(0)
        return messages.subList(startIdx, messages.size)
    }

    /**
     * 取"从第 startRound 轮起"的对话（用于压缩输入裁剪）。
     *
     * 「轮」以 USER 消息为锚点。返回的子列表从第 [startRound]-th USER 消息开始
     * 一直到列表末尾。
     *
     * 适配"继续说"与"延迟发送"等导致单轮含多条消息的场景：
     * - 当 [startRound] = 0 时返回全部消息
     * - 当 [startRound] >= USER 消息总数时返回空列表
     *
     * @param messages 全量历史消息（按时间正序）
     * @param startRound 起始轮次（0 表示从第一条 USER 消息开始）
     * @return 截取后的子列表（顺序不变）
     */
    private fun takeFromRound(
        messages: List<Message>,
        startRound: Int
    ): List<Message> {
        if (messages.isEmpty()) return emptyList()
        if (startRound <= 0) return messages
        val userIndices = messages.withIndex()
            .filter { it.value.role == Role.USER }
            .map { it.index }
        if (startRound >= userIndices.size) return emptyList()
        val startIdx = userIndices[startRound]
        return messages.subList(startIdx, messages.size)
    }
}
