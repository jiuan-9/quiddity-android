package com.quiddity.app.data.repo

import com.quiddity.app.data.local.AgentPermissionControl
import com.quiddity.app.data.local.AgentStore
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.MemoryCompressionResult
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.data.remote.ChatApi
import com.quiddity.app.data.remote.ChatCompletionRequest
import com.quiddity.app.data.remote.ChatMessage
import com.quiddity.app.data.remote.DeepSeekResponsesRequest
import com.quiddity.app.data.remote.ResponsesInputItem
import com.quiddity.app.data.remote.ResponsesTool
import com.quiddity.app.data.remote.ToolDefinition
import com.quiddity.app.data.repo.ConversationRepository
import com.quiddity.app.data.repo.SettingsRepository
import com.quiddity.app.domain.ApiCatalogManager
import com.quiddity.app.domain.ChatError
import com.quiddity.app.domain.MessageStreamCoordinator
import com.quiddity.app.domain.PromptBuilder
import com.quiddity.app.domain.StreamCoordinator
import com.quiddity.app.domain.agent.AgentRoundEffects
import com.quiddity.app.domain.agent.AgentContext
import com.quiddity.app.domain.agent.AgentToolRegistry
import com.quiddity.app.domain.agent.AgentWorkflowController
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive


/*
 * ============================================================================
 * 开发规范 (Development Specifications)
 * ============================================================================
 *
 * 1. 问题修复规范
 *    所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
 *    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
 *
 * 2. 代码注释规范
 *    文件内仅允许保留两类注释：
 *    - 当前规则说明注释（即本注释块）
 *    - 模块划分注释（用于标识代码功能模块边界）
 *    除此之外，禁止出现任何形式的代码注释（包括但不限于单行注释、多行注释、临时调试注释等）。
 *
 * 3. 构建交付要求
 *    在完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK 文件。
 *    APK 文件需满足以下条件：
 *    - 签名有效且符合发布标准
 *    - 包含完整的功能模块
 *    - 经过基础性能测试和兼容性测试
 *    以便在真实设备环境中进行功能验证和性能评估。
 *
 * ============================================================================
 */



/**
 * 聊天请求的统一载体：Chat Completions（所有服务商）或 DeepSeek Responses API
 * （官方服务端联网搜索）。工具轮（read_memory / search_chat）对两种协议复用同一套流程。
 *
 * 每种载体携带自己的请求目标 URL（Completions = chat/completions 端点，
 * Responses = /responses 端点），由 [ChatRepository.runWithToolRound] 按类型取 URL，
 * 调用方（私聊 / 群聊）无需各自判断。
 */
internal sealed interface ChatRoundRequest {
    val apiUrl: String
    data class Completions(val request: ChatCompletionRequest, override val apiUrl: String) : ChatRoundRequest
    data class Responses(val request: DeepSeekResponsesRequest, override val apiUrl: String) : ChatRoundRequest
}

/**
 * 兜底截断判定：内容非空且以"明显还要继续说"的字符结尾时视为截断。
 * 仅用于网关未返回 finish_reason / response.incomplete 的场景。
 *
 * 只把「未闭合的括号/引号」视为明显没说完；冒号、逗号是中文回复的常见自然结尾
 * （例如「先打开微信:」），不能据此判定截断——否则会触发自动续写，造成
 * 「说完一段又重新加载、模型重复输出相似内容」的循环。
 */
internal fun looksTruncated(content: String): Boolean {
    val trimmed = content.trim()
    if (trimmed.isEmpty()) return false
    val last = trimmed.last()
    return last == '（' || last == '(' || last == '“' || last == '「' || last == '『'
}

/**
 * 判定回复是否「只有动作描写、没有任何实际台词」。
 *
 * 正文中除成对括号内的动作、空白与标点外没有任何字词时视为仅动作。
 * 仅动作回复对用户不可读（问题 10）：视同不完整回复，触发带引导的续写，
 * 而不是作为完整回复收尾。
 */
internal fun isActionOnlyReply(content: String): Boolean {
    val text = content.trim()
    if (text.isEmpty()) return false
    if (text.none { isOpenBracket(it) }) return false
    val stack = ArrayDeque<Char>()
    var meaningfulChars = 0
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        when {
            isOpenBracket(ch) -> stack.addLast(ch)
            isCloseBracket(ch) -> {
                if (stack.isNotEmpty() && bracketMatches(stack.last(), ch)) stack.removeLast()
            }
            ch.isWhitespace() || ch in ACTION_ONLY_PUNCTUATION -> Unit
            else -> if (stack.isEmpty()) meaningfulChars++
        }
        i++
    }
    return meaningfulChars == 0 && stack.isEmpty()
}

private fun isOpenBracket(ch: Char): Boolean = when (ch) {
    '(', '（', '[', '【', '{', '<' -> true
    else -> false
}

private fun isCloseBracket(ch: Char): Boolean = when (ch) {
    ')', '）', ']', '】', '}', '>' -> true
    else -> false
}

private fun bracketMatches(open: Char, close: Char): Boolean = when (open) {
    '(' -> close == ')'
    '（' -> close == '）'
    '[' -> close == ']'
    '【' -> close == '】'
    '{' -> close == '}'
    '<' -> close == '>'
    else -> false
}

private const val ACTION_ONLY_PUNCTUATION = "，。！？、；：,.;:!?…~-—·"

/**
 * 连续消息近重复判定（协调器与事件层共用）：
 * 去掉成对括号内动作、标点与空白后，两条消息核心内容相同，且任一条含动作括号
 * （如「（动作）台词」与「台词」）时视为模型复述，丢弃后一条。
 * 不带动作括号的完全相同分片（如硬上限强制切分）不判定，避免误删合法分片。
 */
internal fun isNearDuplicateContent(previous: String, candidate: String): Boolean {
    val lastCore = stripBracketsAndPunctuation(previous)
    val candidateCore = stripBracketsAndPunctuation(candidate)
    if (lastCore.isEmpty() || candidateCore.isEmpty()) return false
    if (lastCore != candidateCore) return false
    return previous.any { isOpenBracketChar(it) } || candidate.any { isOpenBracketChar(it) }
}

/**
 * 两段文本的相似度（0f～1f）：去掉动作括号、标点与空白后，按相邻字符对（bigram）的
 * Jaccard 相似度计算。用于「重说」场景的确定性去同：新回复与上一版核心内容高度
 * 相似（同一批字词、同一种句式）时，由应用层自动触发一次换表达重写，而不是把
 * 「别写得太像」完全交给模型自觉。
 *
 * @return 0f 表示完全不同；1f 表示字词序列完全一致。
 */
internal fun replySimilarityRatio(previous: String, candidate: String): Float {
    val prevCore = stripBracketsAndPunctuation(previous)
    val candCore = stripBracketsAndPunctuation(candidate)
    if (prevCore.isEmpty() || candCore.isEmpty()) return 0f
    val prevBigrams = buildBigramSet(prevCore)
    val candBigrams = buildBigramSet(candCore)
    if (prevBigrams.isEmpty() || candBigrams.isEmpty()) return 0f
    val intersection = prevBigrams.intersect(candBigrams).size
    val union = prevBigrams.union(candBigrams).size
    return intersection.toFloat() / union.coerceAtLeast(1)
}

/** 「重说」去同阈值：核心字词相似度 ≥ 0.72 视为高度相似，需要自动换表达重写。 */
internal const val REGENERATE_SIMILARITY_THRESHOLD = 0.72f

private fun buildBigramSet(text: String): Set<String> {
    if (text.length < 2) return setOf(text)
    return (0 until text.length - 1).mapTo(LinkedHashSet()) { i -> text.substring(i, i + 2) }
}

internal fun stripBracketsAndPunctuation(text: String): String {
    val sb = StringBuilder()
    var depth = 0
    for (ch in text) {
        when {
            isOpenBracketChar(ch) -> depth++
            isCloseBracketChar(ch) -> depth = (depth - 1).coerceAtLeast(0)
            depth > 0 -> Unit
            ch.isWhitespace() || ch in DUPLICATE_IGNORED_PUNCTUATION -> Unit
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}

private fun isOpenBracketChar(ch: Char): Boolean = when (ch) {
    '(', '（', '[', '【', '{', '<' -> true
    else -> false
}

private fun isCloseBracketChar(ch: Char): Boolean = when (ch) {
    ')', '）', ']', '】', '}', '>' -> true
    else -> false
}

private const val DUPLICATE_IGNORED_PUNCTUATION =
    "，。！？、；：,.;:!?…~-—·“”‘’「」『』《》〈〉\"'"

/**
 * 构造聊天请求：启用 DeepSeek 官方联网搜索时走 Responses API（服务端 web_search），
 * 否则走 OpenAI 兼容 Chat Completions。
 *
 * @param responsesUrl 非空表示本会话已启用联网搜索且当前 API 配置支持（由 [ChatRepository.resolveWebSearch] 判定）
 * @param tools OpenAI 兼容 function 工具（记忆策略 TOOL 时携带）；Responses 路径会附加 web_search
 * @param tool_choice OpenAI 兼容工具调用策略（"auto" / null）
 */
internal fun buildChatRound(
    access: ApiAccess.Resolved,
    systemPrompt: String,
    apiMessages: List<ChatMessage>,
    maxTokens: Int,
    temperature: Double,
    responsesUrl: String?,
    reasoningEffort: String?,
    tools: List<ToolDefinition>?,
    tool_choice: String?
): ChatRoundRequest {
    if (responsesUrl.isNullOrBlank()) {
        return ChatRoundRequest.Completions(
            ChatCompletionRequest(
                model = access.model,
                messages = apiMessages,
                max_tokens = maxTokens,
                temperature = temperature,
                stream = true,
                reasoning_effort = reasoningEffort,
                tools = tools,
                tool_choice = tool_choice
            ),
            apiUrl = access.apiUrl
        )
    }
    val responsesTools = buildList {
        add(ResponsesTool(type = "web_search"))
        tools.orEmpty().forEach { add(PromptBuilder.toResponsesTool(it)) }
    }
    return ChatRoundRequest.Responses(
        DeepSeekResponsesRequest(
            model = access.model,
            input = PromptBuilder.toResponsesInput(apiMessages),
            instructions = apiMessages.firstOrNull { it.role == "system" }?.content,
            max_output_tokens = maxTokens,
            temperature = temperature,
            stream = true,
            reasoning_effort = reasoningEffort,
            tools = responsesTools,
            tool_choice = tool_choice?.let { JsonPrimitive(it) }
        ),
        apiUrl = responsesUrl
    )
}

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
     * 模型分级解析（群聊成员完整级可用 search_chat 工具检索完整群聊消息）。
     */
    private val apiCatalogManager: ApiCatalogManager? = null,
    /**
     * Agent 工具注册表（AGENT 会话分发工具调用；私聊/群聊保持原有 read_memory/search_chat）。
     */
    private val agentToolRegistry: AgentToolRegistry? = null,
    /**
     * Agent 设置存储（工具开关/白名单/审计快照来源）。
     */
    private val agentStore: AgentStore? = null,
    /**
     * 协调器工厂。默认使用 [MessageStreamCoordinator]。
     * 每轮新 run 都注入新 runId（基于 UUID），保证消息 id 全局唯一。
     * [senderId] 为群聊发言人会话 id（2.0.0 使用），私聊传 null。
     */
    private val coordinatorFactory: (conversationId: String, runId: String, splitEnabled: Boolean, singleMessageTokens: Int, senderId: String?, thinking: String) -> StreamCoordinator =
        { conversationId, runId, splitEnabled, singleMessageTokens, senderId, thinking ->
            MessageStreamCoordinator(
                conversationId, runId, singleMessageTokens, splitEnabled,
                senderId = senderId,
                thinking = thinking
            )
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
        /** 流以"被截断"结束（finish_reason=length / response.incomplete），内容不完整。 */
        data object Truncated : Event()
        /** 信息性提示（非错误）：如思考功能降级 / 未返回思考内容。 */
        data class Notice(val text: String) : Event()
        /** Agent 工具使用报告：模型调用了哪个工具（聊天页显示使用中动画）。 */
        data class ToolUse(val toolName: String) : Event()
        /** 单个工具执行完成：附成功标记与结果摘要，供聊天页展示痕迹。 */
        data class ToolResult(val toolName: String, val ok: Boolean, val summary: String) : Event()
        /** Agent 危险工具批量确认请求：一轮工具调用中需授权的工具一次列出。 */
        data class ToolConfirmBatch(
            val items: List<ToolConfirmItem>,
            val resume: (Boolean) -> Unit
        ) : Event()
        /**
         * Agent 单轮行为追踪结果（Done 前派发）：本轮创建的文件路径 + 更改项摘要，
         * 由 ViewModel 固化进最后一条 AI 消息（撤回追踪）。
         */
        data class AgentRoundEffects(
            val createdPaths: List<String>,
            val changedItems: List<String>
        ) : Event()
        /** 错误。 */
        data class Error(val throwable: Throwable, val partialContent: String) : Event()
    }

    /** 单个待确认工具（名称 + 参数），用于批量确认弹窗展示。 */
    data class ToolConfirmItem(
        val toolName: String,
        val args: JsonObject
    )

    private val miscOps = MiscOps(
        api = api,
        settingsRepo = settingsRepo,
        onResolveSenderNames = { groupReplyRunner.resolveSenderNames(it) }
    )
    private val toolResultBuilder = ToolResultBuilder(
        conversationRepo = conversationRepo,
        apiCatalogManager = apiCatalogManager,
        agentToolRegistry = agentToolRegistry,
        agentStore = agentStore
    )
    private val singleStreamRunner = SingleStreamRunner(
        api = api,
        toolResultBuilder = toolResultBuilder
    )
    private lateinit var groupReplyRunner: GroupReplyRunner
    private val toolRoundRunner = ToolRoundRunner(
        api = api,
        settingsRepo = settingsRepo,
        agentToolRegistry = agentToolRegistry,
        agentStore = agentStore,
        coordinatorFactory = coordinatorFactory,
        singleStreamRunner = singleStreamRunner,
        toolResultBuilder = toolResultBuilder,
        onTakeLastRounds = { messages, rounds, buffer -> miscOps.takeLastRounds(messages, rounds, buffer) }
    )

    init {
        groupReplyRunner = GroupReplyRunner(
            api = api,
            conversationRepo = conversationRepo,
            settingsRepo = settingsRepo,
            apiCatalogManager = apiCatalogManager,
            coordinatorFactory = coordinatorFactory,
            toolResultBuilder = toolResultBuilder,
            toolRoundRunner = toolRoundRunner
        )
    }

    suspend fun streamAssistantReply(
        conv: Conversation,
        history: List<Message>,
        memoryStrategy: String? = null,
        regeneratePreviousReply: String? = null,
        thinking: String = "",
        onEvent: suspend (Event) -> Unit
    ) = toolRoundRunner.streamAssistantReply(conv, history, memoryStrategy, regeneratePreviousReply, thinking, onEvent)
    suspend fun letAiStart(
        conv: Conversation,
        memoryStrategy: String? = null,
        regeneratePreviousReply: String? = null,
        thinking: String = "",
        onEvent: suspend (Event) -> Unit
    ) = toolRoundRunner.letAiStart(conv, memoryStrategy, regeneratePreviousReply, thinking, onEvent)
    suspend fun compilePersona(conv: Conversation, maxOutputTokens: Int): String = miscOps.compilePersona(conv, maxOutputTokens)
    fun classify(t: Throwable): ChatError = miscOps.classify(t)
    suspend fun compressConversationMemory(
        conv: Conversation,
        messages: List<Message>
    ): MemoryCompressionResult = miscOps.compressConversationMemory(conv, messages)
    suspend fun compressGroupMemory(
        group: Conversation,
        transcript: List<Message>
    ): String = miscOps.compressGroupMemory(group, transcript)
    suspend fun quickSetup(
        conv: Conversation,
        userDescription: String,
        tier: com.quiddity.app.domain.QuickSetupTier
    ): String = miscOps.quickSetup(conv, userDescription, tier)
    suspend fun generateTimeLibrary(conv: Conversation): String = miscOps.generateTimeLibrary(conv)
    suspend fun decideActiveMessage(
        conv: Conversation,
        history: List<Message>,
        timePoint: String
    ): String = miscOps.decideActiveMessage(conv, history, timePoint)
    suspend fun streamGroupMemberReply(
        member: Conversation,
        group: Conversation,
        transcript: List<Message>,
        senderId: String,
        regeneratePreviousReply: String? = null,
        onEvent: suspend (Event) -> Unit
    ) = groupReplyRunner.streamGroupMemberReply(member, group, transcript, senderId, regeneratePreviousReply, onEvent)
    suspend fun decideGroupResponder(
        members: List<Conversation>,
        transcript: List<Message>,
        message: Message
    ): String = groupReplyRunner.decideGroupResponder(members, transcript, message)

    companion object {
        /**
         * ? AGENT ???????????????? null??? read_memory/search_chat ????
         */
        internal suspend fun dispatchAgentToolIfNeeded(
            type: ConversationType,
            name: String,
            args: String,
            registry: AgentToolRegistry?,
            ctx: AgentContext?
        ): String? {
            if (type != ConversationType.AGENT || registry == null || ctx == null) return null
            return registry.dispatch(name, args, ctx)
        }
    }
}

internal class GroupReplyPrefixSanitizer(names: List<String>) {
    private val known = names.filter { it.isNotBlank() }.distinct().sortedByDescending { it.length }

    fun clean(content: String): String {
        var text = content
        while (true) {
            val name = known.firstOrNull {
                text.startsWith("$it：") || text.startsWith("$it:")
            } ?: break
            text = text.substring(name.length + 1).trimStart()
        }
        return text
    }
}

/**
 * 群聊回复前缀流式剥离器：在内容进入切分器之前，把开头可能跨 delta 分片到达的
 * 「名字：/名字:」前缀剥掉。
 *
 * 与 [GroupReplyPrefixSanitizer]（对完整内容剥离）互补：
 * - 本类负责流式开头：若前缀在切分阶段被拆成独立消息，剥离后会产生空白消息；
 * - 匹配成功后立即切换直通模式（前缀只可能出现在回复开头）；
 * - 只处理开头（含前导空白），正文中的「名字：」仍由 [GroupReplyPrefixSanitizer] 兜底。
 */
internal class GroupReplyPrefixStripper(names: List<String>) {

    private val known = names.filter { it.isNotBlank() }.distinct().sortedByDescending { it.length }
    private val pending = StringBuilder()
    private var active = true

    fun accept(delta: String): String {
        if (!active || delta.isEmpty()) return delta
        pending.append(delta)
        val text = pending.toString()
        val start = text.indexOfFirst { !it.isWhitespace() }
        if (start < 0) return ""
        val candidate = text.substring(start)
        for (name in known) {
            val full = "$name："
            if (candidate == full || candidate == "$name:") return ""
            if (candidate.startsWith(full)) return consume(candidate.substring(full.length))
            if (candidate.startsWith("$name:")) return consume(candidate.substring("$name:".length))
        }
        for (name in known) {
            if (name.startsWith(candidate)) return ""
            if ("$name：".startsWith(candidate) || "$name:".startsWith(candidate)) return ""
        }
        return consume(candidate)
    }

    private fun consume(rest: String): String {
        active = false
        pending.clear()
        return rest
    }
}

/**
 * 构造续写 / 兜底重试请求：截断时把「已输出正文 + 提示语」追加进请求；
 * 空回复时仅追加提示语（避免向接口提交空 assistant 消息）。
 */
internal fun buildContinueRequest(
    request: ChatRoundRequest,
    partialContent: String?,
    nudge: String
): ChatRoundRequest = when (request) {
    is ChatRoundRequest.Completions -> {
        val extra = buildList {
            if (!partialContent.isNullOrBlank()) {
                add(ChatMessage(role = "assistant", content = partialContent))
            }
            add(ChatMessage(role = "user", content = nudge))
        }
        ChatRoundRequest.Completions(
            request.request.copy(
                messages = request.request.messages + extra,
                tools = request.request.tools,
                tool_choice = request.request.tool_choice
            ),
            apiUrl = request.apiUrl
        )
    }
    is ChatRoundRequest.Responses -> {
        val extra = buildList {
            if (!partialContent.isNullOrBlank()) {
                add(
                    ResponsesInputItem(
                        type = "message",
                        role = "assistant",
                        content = kotlinx.serialization.json.JsonPrimitive(partialContent)
                    )
                )
            }
            add(
                ResponsesInputItem(
                    type = "message",
                    role = "user",
                    content = kotlinx.serialization.json.JsonPrimitive(nudge)
                )
            )
        }
        ChatRoundRequest.Responses(
            request.request.copy(
                input = request.request.input + extra,
                tools = request.request.tools,
                tool_choice = request.request.tool_choice
            ),
            apiUrl = request.apiUrl
        )
    }
}
