package com.quiddity.app.data.repo

import com.quiddity.app.data.local.AgentPermissionControl
import com.quiddity.app.data.local.AgentStore
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.MemoryCompressionResult
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import com.quiddity.app.data.remote.ChatApi
import com.quiddity.app.data.remote.ChatException
import com.quiddity.app.data.remote.ChatMessage
import com.quiddity.app.data.remote.ResponsesInputItem
import com.quiddity.app.data.remote.ResponsesTool
import com.quiddity.app.data.remote.ToolDefinition
import com.quiddity.app.data.repo.ConversationRepository
import com.quiddity.app.data.repo.SettingsRepository
import com.quiddity.app.domain.ApiCatalogManager
import com.quiddity.app.domain.ChatContextTrimmer
import com.quiddity.app.domain.ChatError
import com.quiddity.app.domain.GroupReplyPlanner
import com.quiddity.app.domain.PromptBuilder
import com.quiddity.app.domain.StreamCoordinator
import com.quiddity.app.domain.agent.AgentRoundEffects
import com.quiddity.app.domain.agent.AgentToolRegistry
import com.quiddity.app.domain.agent.AgentWorkflowController
import com.quiddity.app.util.QuiddityConstants

internal class MiscOps(
    private val api: ChatApi,
    private val settingsRepo: SettingsRepository,
    private val onResolveSenderNames: (List<Message>) -> Map<String, String>
) {

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
            temperature = QuiddityConstants.clampTemperature(
                QuiddityConstants.PERSONA_COMPILE_TEMPERATURE,
                access.maxTemperature
            ),
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
     * 压缩对话记忆（6.5.2 两段化）。
     *
     * 将历史对话 + 上一次的压缩摘要发送给 AI，让 AI 提取关键信息，输出「【摘要】 + 【索引】」两段：
     * - 摘要段 → [MemoryCompressionResult.summary]（写入 Conversation.compressedMemory）
     * - 索引段 → [MemoryCompressionResult.index]，并追加程序补全的覆盖范围 `（覆盖第 a-b 轮）`
     *   （a = lastCompressedAtRound + 1，b = 当前用户轮数；无法计算轮次时省略范围括号）
     * - 摘要段为空 → [MemoryCompressionResult.success] = false，调用方保持两字段旧值
     *
     * 压缩结果替代原始历史发送给 API，节省 Token。
     *
     * @param conv 当前会话（用于读取模型配置和已有压缩摘要）
     * @param messages 所有历史消息
     * @return 两段式压缩结果（摘要 + 索引 + 成功标记）
     */
    suspend fun compressConversationMemory(
        conv: Conversation,
        messages: List<Message>
    ): MemoryCompressionResult {
        val settings = settingsRepo.currentSnapshot()
        val access = ApiAccess.resolve(settings, conv)
        access as? ApiAccess.Resolved
            ?: throw IllegalStateException("API 未配置，无法压缩记忆")

        // 过滤 isNotice 提示气泡：不参与压缩（UI 专用，非对话内容）
        val filteredMessages = messages.filterNot { it.isNotice || it.isThinking }

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
        val raw = api.completeNonStreaming(
            apiUrl = access.apiUrl,
            apiKey = access.apiKey,
            model = access.model,
            systemPrompt = PromptBuilder.COMPRESSION_SYSTEM_PROMPT,
            userContent = userContent,
            maxTokens = QuiddityConstants.COMPRESSION_MAX_TOKENS,
            temperature = QuiddityConstants.COMPRESSION_TEMPERATURE,
            emptyError = "记忆压缩返回空内容"
        )
        val parsed = PromptBuilder.parseCompressionResult(raw)
        if (!parsed.success) {
            return parsed
        }
        val userRounds = filteredMessages.count { it.role == Role.USER }
        return if (userRounds >= conv.lastCompressedAtRound + 1) {
            val range = "（覆盖第 ${conv.lastCompressedAtRound + 1}-$userRounds 轮）"
            parsed.copy(index = (parsed.index + range).trim())
        } else {
            parsed
        }
    }

    // ============================================================
    // 群聊接口（1.5.0 实现；decideGroupResponder 按方案第三节用户点名模式不启用）
    // ============================================================

    /**
     * 群聊成员发言流式接口（4.1，2.0.0 实现）。
     *
     * 规划：复用 [runStream] + 协调器 [senderId]，让群聊消息从创建起带发言人；
     * 成员回复用自己的模型配置与额度（apiCatalogId / maxTokens / singleMessageTokens 按会话独立）。
     *
     * @param member 发言成员（私聊会话，携带该成员的模型配置与记忆）
     * @param group 群聊会话（群规则 / 群聊小本本）
     * @param transcript 群聊转述（[com.quiddity.app.domain.PromptBuilder.buildGroupTranscript] 产出）
     * @param senderId 发言人会话 id（写入消息 senderId）
     * @param regeneratePreviousReply 重说场景下该成员上一版回复的原文（null = 正常回复）。
     *   非空时提示词会标记本次为「重说」并要求换一种表达，避免输出与上一版雷同。
     */
    suspend fun compressGroupMemory(
        group: Conversation,
        transcript: List<Message>
    ): String {
        val settings = settingsRepo.currentSnapshot()
        val access = ApiAccess.resolve(settings, group)
            as? ApiAccess.Resolved
            ?: throw IllegalStateException("API 未配置，无法压缩群聊记忆")
        val transcriptText = PromptBuilder.buildGroupTranscript(
            transcript.filterNot { it.isNotice || it.isThinking },
            lastN = 0,
            senderNames = onResolveSenderNames(transcript),
            userName = "用户"
        )
        val raw = api.completeNonStreaming(
            apiUrl = access.apiUrl,
            apiKey = access.apiKey,
            model = access.model,
            systemPrompt = PromptBuilder.GROUP_MEMORY_SYSTEM_PROMPT,
            userContent = PromptBuilder.buildGroupMemorySummaryPrompt(transcriptText),
            maxTokens = QuiddityConstants.GROUP_MEMORY_MAX_TOKENS,
            temperature = QuiddityConstants.COMPRESSION_TEMPERATURE,
            emptyError = "群聊记忆压缩返回空内容"
        )
        return GroupReplyPlanner.applyMemoryCompression(raw, group.groupMemory)
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
    fun takeLastRounds(
        messages: List<Message>,
        rounds: Int,
        buffer: Int = 0
    ): List<Message> = ChatContextTrimmer.takeLastRounds(messages, rounds, buffer)

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
    fun takeFromRound(
        messages: List<Message>,
        startRound: Int
    ): List<Message> = ChatContextTrimmer.takeFromRound(messages, startRound)
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
            // 快速设定使用独立温度（面板可调）：温度越高发散性越强，避免每次生成同一套人设
            temperature = QuiddityConstants.clampTemperature(
                settings.quickSetupTemperature,
                access.maxTemperature
            ),
            emptyError = "快速设定返回空内容"
        )
    }

    /**
     * 时间库生成（非流式）。
     *
     * 对应算法文档 3.2 生成规则：
     * - 输入依据：该会话的人设 + 该会话的压缩聊天记录
     * - 输出：仅时间列表（24 小时制，精确到分钟），调用方用
     *   [com.quiddity.app.domain.TimeLibraryEngine.parseGeneratedTimes] 解析
     *
     * API 未配置时抛 [IllegalStateException]，调用方据此按"生成失败"兜底（沿用旧库）。
     *
     * @param conv 当前会话（读取人设、压缩记忆与模型配置）
     * @return LLM 返回的原始文本
     */
    suspend fun generateTimeLibrary(conv: Conversation): String {
        val settings = settingsRepo.currentSnapshot()
        val access = ApiAccess.resolve(settings, conv)
        if (access is ApiAccess.Failure) {
            throw IllegalStateException(access.userMessage)
        }
        access as ApiAccess.Resolved
        return api.completeNonStreaming(
            apiUrl = access.apiUrl,
            apiKey = access.apiKey,
            model = access.model,
            systemPrompt = PromptBuilder.TIME_LIBRARY_SYSTEM_PROMPT,
            userContent = PromptBuilder.buildTimeLibraryUserPrompt(conv, conv.compressedMemory),
            maxTokens = QuiddityConstants.ACTIVE_MESSAGE_GENERATE_MAX_TOKENS,
            temperature = QuiddityConstants.ACTIVE_MESSAGE_GENERATE_TEMPERATURE,
            emptyError = "时间库生成返回空内容"
        )
    }

    /**
     * 主动消息发送决策（非流式）。
     *
     * 对应算法文档 5.2 触发执行流程：
     * - 输入依据：该会话的人设 + 未压缩的聊天记录 + 当前触发的时间点
     * - 输出：严格等于独立数字 0 → 拦截不发送；包含任何其他内容 → 需要发送（该内容即消息），
     *   调用方用 [com.quiddity.app.domain.TimeLibraryEngine.parseDecisionResult] 解析
     *
     * 聊天记录为空时不调用本方法（由调用方判定并直接视为"不发送"）。
     *
     * @param conv 当前会话（读取人设与模型配置）
     * @param history 未压缩的聊天记录（调用方传入，已过滤 isNotice 提示气泡）
     * @param timePoint 当前触发的时间点（"HH:mm"）
     * @return LLM 返回的原始文本
     */
    suspend fun decideActiveMessage(
        conv: Conversation,
        history: List<Message>,
        timePoint: String
    ): String {
        val settings = settingsRepo.currentSnapshot()
        val access = ApiAccess.resolve(settings, conv)
        if (access is ApiAccess.Failure) {
            throw IllegalStateException(access.userMessage)
        }
        access as ApiAccess.Resolved

        // 按上下文记忆轮数裁剪未压缩聊天记录（以 USER 消息为锚点）；
        // 全部为 AI 消息（无 USER 锚点）时回退为完整列表，避免上下文丢失。
        val contextLimit = if (conv.contextLimit > 0) conv.contextLimit else settings.globalContextLimit
        val trimmed = takeLastRounds(history, contextLimit, buffer = 4)
        val effectiveHistory = if (trimmed.isEmpty() && history.isNotEmpty()) history else trimmed

        return api.completeNonStreaming(
            apiUrl = access.apiUrl,
            apiKey = access.apiKey,
            model = access.model,
            systemPrompt = PromptBuilder.buildDecisionSystemPrompt(conv),
            userContent = PromptBuilder.buildDecisionUserPrompt(effectiveHistory, timePoint),
            maxTokens = QuiddityConstants.ACTIVE_MESSAGE_DECIDE_MAX_TOKENS,
            temperature = QuiddityConstants.ACTIVE_MESSAGE_DECIDE_TEMPERATURE,
            emptyError = "主动消息决策返回空内容"
        )
    }

    fun classifyChatException(msg: String, t: Throwable): ChatError {
        val lower = msg.lowercase()
        return when {
            // 中文密钥类错误（未配置 / 格式损坏 / 无法解密）统一归为鉴权类，提示检查密钥
            "密钥" in msg -> ChatError.Auth(userMessage = msg, cause = t)
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
}
