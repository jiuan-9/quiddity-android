package com.quiddity.app.data.repo

import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.MemoryCompressionResult
import com.quiddity.app.data.model.Role
import com.quiddity.app.data.model.AppSettings
import com.quiddity.app.data.remote.ChatApi
import com.quiddity.app.data.remote.ChatCompletionRequest
import com.quiddity.app.data.remote.ChatException
import com.quiddity.app.data.remote.ChatMessage
import com.quiddity.app.data.remote.ChatStreamParser
import com.quiddity.app.data.remote.AssistantToolCall
import com.quiddity.app.data.remote.DeepSeekResponsesRequest
import com.quiddity.app.data.remote.ResponsesInputItem
import com.quiddity.app.data.remote.ResponsesTool
import com.quiddity.app.data.remote.ToolDefinition
import com.quiddity.app.domain.ChatRecordSearch
import com.quiddity.app.domain.ChatError
import com.quiddity.app.domain.ApiCatalogManager
import com.quiddity.app.domain.GroupReplyPlanner
import com.quiddity.app.domain.MemorySearch
import com.quiddity.app.domain.MessageStreamCoordinator
import com.quiddity.app.domain.PromptBuilder
import com.quiddity.app.domain.StreamCoordinator
import com.quiddity.app.util.IdGenerator
import com.quiddity.app.util.QuiddityConstants
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject

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
     * 协调器工厂。默认使用 [MessageStreamCoordinator]。
     * 每轮新 run 都注入新 runId（基于 UUID），保证消息 id 全局唯一。
     * [senderId] 为群聊发言人会话 id（2.0.0 使用），私聊传 null。
     */
    private val coordinatorFactory: (conversationId: String, runId: String, splitEnabled: Boolean, singleMessageTokens: Int, senderId: String?) -> StreamCoordinator =
        { conversationId, runId, splitEnabled, singleMessageTokens, senderId ->
            MessageStreamCoordinator(conversationId, runId, singleMessageTokens, splitEnabled, senderId = senderId)
        }
) {

    /**
     * 聊天请求的统一载体：Chat Completions（所有服务商）或 DeepSeek Responses API
     * （官方服务端联网搜索）。工具轮（read_memory / search_chat）对两种协议复用同一套流程。
     */
    private sealed interface ChatRoundRequest {
        data class Completions(val request: ChatCompletionRequest) : ChatRoundRequest
        data class Responses(val request: DeepSeekResponsesRequest) : ChatRoundRequest
    }

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
     * @param memoryStrategy 记忆策略覆盖值（null = 跟随 [Conversation.memoryStrategy]，
     *   仍为 null 时回退为随身带 CARRY）。TOOL 模式下请求携带 read_memory 工具，
     *   模型按需检索记忆，不再每轮重读压缩摘要。
     * @param onEvent suspend 事件回调（由 ViewModel 串行化执行）
     */
    suspend fun streamAssistantReply(
        conv: Conversation,
        history: List<Message>,
        memoryStrategy: String? = null,
        onEvent: suspend (Event) -> Unit
    ) {
        val settings = settingsRepo.currentSnapshot()
        val access = ApiAccess.resolve(settings, conv)
        if (access is ApiAccess.Failure) {
            return emitError(onEvent, access.toChatException(), "")
        }
        access as ApiAccess.Resolved

        val effectiveStrategy = memoryStrategy
            ?: conv.memoryStrategy
            ?: QuiddityConstants.MEMORY_STRATEGY_CARRY
        val systemPrompt = PromptBuilder.buildSystemPrompt(
            conv = conv,
            memoryStrategy = effectiveStrategy
        )
        val contextLimit = if (conv.contextLimit > 0) conv.contextLimit else settings.globalContextLimit
        // 过滤 isNotice 提示气泡：不发给 LLM（UI 专用，非对话内容）
        val filteredHistory = history.filterNot { it.isNotice }
        val trimmedHistory = takeLastRounds(filteredHistory, contextLimit, buffer = 4)
        val apiMessages = PromptBuilder.toApiMessages(systemPrompt, trimmedHistory)

        val maxTokens = conv.maxTokens ?: settings.globalMaxTokens
        val singleMsgTokens = conv.singleMessageTokens ?: settings.globalSingleMessageTokens
        val temperature = conv.temperature ?: settings.globalTemperature
        val toolStrategyActive = effectiveStrategy == QuiddityConstants.MEMORY_STRATEGY_TOOL &&
            conv.compressedMemory.isNotBlank()
        val request = buildChatRound(
            access = access,
            systemPrompt = systemPrompt,
            apiMessages = apiMessages,
            maxTokens = maxTokens,
            temperature = temperature,
            responsesUrl = resolveWebSearch(settings, conv),
            tools = if (toolStrategyActive) {
                listOf(
                    PromptBuilder.buildReadMemoryTool(),
                    PromptBuilder.buildSearchChatTool()
                )
            } else {
                null
            },
            tool_choice = if (toolStrategyActive) "auto" else null
        )

        val coordinator = coordinatorFactory(
            conv.id,
            // runId 是协调器内部标签，使用不带前缀的 UUID 即可；
            // 最终消息 ID 由 MessageStreamCoordinator 拼装为 `{convId}_{runId}_ai_{index}`。
            IdGenerator.newUuid(),
            settings.multilineAutoSplit,
            singleMsgTokens,
            null
        )

        runWithToolRound(api, access.apiUrl, access.apiKey, request, coordinator, conv, onEvent)
    }

    /**
     * 让 AI 先发消息（空对话开场）。
     */
    suspend fun letAiStart(
        conv: Conversation,
        memoryStrategy: String? = null,
        onEvent: suspend (Event) -> Unit
    ) {
        val settings = settingsRepo.currentSnapshot()
        val access = ApiAccess.resolve(settings, conv)
        if (access is ApiAccess.Failure) {
            return emitError(onEvent, access.toChatException(), "")
        }
        access as ApiAccess.Resolved

        val effectiveStrategy = memoryStrategy
            ?: conv.memoryStrategy
            ?: QuiddityConstants.MEMORY_STRATEGY_CARRY
        val systemPrompt = PromptBuilder.buildSystemPrompt(
            conv = conv,
            memoryStrategy = effectiveStrategy
        )
        // 引导：让 AI 主动发起对话
        val guidedMessages = listOf(
            ChatMessage(role = "system", content = systemPrompt),
            ChatMessage(role = "user", content = PromptBuilder.LET_AI_START_GUIDE)
        )

        val maxTokens = conv.maxTokens ?: settings.globalMaxTokens
        val singleMsgTokens = conv.singleMessageTokens ?: settings.globalSingleMessageTokens
        val temperature = conv.temperature ?: settings.globalTemperature
        val toolStrategyActive = effectiveStrategy == QuiddityConstants.MEMORY_STRATEGY_TOOL &&
            conv.compressedMemory.isNotBlank()
        val request = buildChatRound(
            access = access,
            systemPrompt = systemPrompt,
            apiMessages = guidedMessages,
            maxTokens = maxTokens,
            temperature = temperature,
            responsesUrl = resolveWebSearch(settings, conv),
            tools = if (toolStrategyActive) {
                listOf(
                    PromptBuilder.buildReadMemoryTool(),
                    PromptBuilder.buildSearchChatTool()
                )
            } else {
                null
            },
            tool_choice = if (toolStrategyActive) "auto" else null
        )

        val coordinator = coordinatorFactory(
            conv.id,
            IdGenerator.newUuid(),
            settings.multilineAutoSplit,
            singleMsgTokens,
            null
        )

        runWithToolRound(api, access.apiUrl, access.apiKey, request, coordinator, conv, onEvent)
    }

    /**
     * 构造聊天请求：启用 DeepSeek 官方联网搜索时走 Responses API（服务端 web_search），
     * 否则走 OpenAI 兼容 Chat Completions。
     *
     * @param responsesUrl 非空表示本会话已启用联网搜索且当前 API 配置支持（由 [resolveWebSearch] 判定）
     * @param tools OpenAI 兼容 function 工具（记忆策略 TOOL 时携带）；Responses 路径会附加 web_search
     * @param tool_choice OpenAI 兼容工具调用策略（"auto" / null）
     */
    private fun buildChatRound(
        access: ApiAccess.Resolved,
        systemPrompt: String,
        apiMessages: List<ChatMessage>,
        maxTokens: Int,
        temperature: Double,
        responsesUrl: String?,
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
                    tools = tools,
                    tool_choice = tool_choice
                )
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
                tools = responsesTools,
                tool_choice = tool_choice?.let { JsonPrimitive(it) }
            )
        )
    }

    /**
     * 解析会话是否启用 DeepSeek 官方服务端联网搜索。
     *
     * 判定链：会话开关开启 → 解析实际 catalog 条目 → 该条目支持 Responses 服务端搜索。
     * 返回官方 /responses 端点；不满足任一条件返回 null（走 Chat Completions）。
     */
    private fun resolveWebSearch(settings: AppSettings, conv: Conversation): String? {
        val manager = apiCatalogManager ?: return null
        if (!conv.webSearchEnabled) return null
        val entry = manager.resolveEntry(settings, conv) ?: return null
        if (!manager.supportsServerWebSearch(entry)) return null
        return manager.responsesApiUrl(entry)
    }

    /**
     * 通用流式驱动（含 read_memory 工具轮）：
     * 第一轮如聚合到工具调用，回填检索结果后发起第二轮（最多一轮，防死循环），
     * 最后由 [StreamCoordinator] 负责正确的 NewMessage/UpdateMessage/CompleteMessage 派发。
     */
    private suspend fun runWithToolRound(
        api: ChatApi,
        apiUrl: String,
        apiKey: String,
        request: ChatRoundRequest,
        coordinator: StreamCoordinator,
        conv: Conversation,
        onEvent: suspend (Event) -> Unit,
        /**
         * 内容进入协调器前的流式转换（群聊用于剥离「名字：」前缀，
         * 避免前缀在切分阶段被拆成独立消息、剥离后变成空白消息）。
         */
        contentTransform: (String) -> String = { it }
    ) {
        val firstRoundCalls = runSingleStream(
            api, apiUrl, apiKey, request, coordinator, onEvent, contentTransform
        ) ?: return
        if (firstRoundCalls.isNotEmpty()) {
            try {
                val secondRequest = buildSecondRoundRequest(request, conv, firstRoundCalls)
                // 第二轮不再聚合工具调用（模型若再次请求工具则忽略），直接流式输出最终答复
                runSingleStream(api, apiUrl, apiKey, secondRequest, coordinator, onEvent, contentTransform)
            } catch (t: Throwable) {
                // 工具回填失败不影响主流程：派发错误并结束本轮，避免异常上抛导致崩溃
                emitError(onEvent, t, coordinator.snapshot().joinToString("\n") { it.content })
            }
        }
        onEvent(Event.Done)
    }

    /**
     * 单轮流式驱动：消费 [ChatApi.StreamEvent] 并派发协调器信号。
     *
     * @return 聚合到的工具调用列表；出错返回 null（错误已通过 [Event.Error] 派发）
     */
    private suspend fun runSingleStream(
        api: ChatApi,
        apiUrl: String,
        apiKey: String,
        request: ChatRoundRequest,
        coordinator: StreamCoordinator,
        onEvent: suspend (Event) -> Unit,
        contentTransform: (String) -> String = { it }
    ): List<ChatStreamParser.AggregatedToolCall>? {
        var toolCalls: List<ChatStreamParser.AggregatedToolCall> = emptyList()
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
                        evictions.forEach { dispatch(onEvent, it) }
                    }
                    is ChatApi.StreamEvent.ToolCalls -> {
                        if (event.calls.isNotEmpty()) toolCalls = event.calls
                    }
                }
            }
            // 流结束，强制收尾
            coordinator.finalize().forEach { dispatch(onEvent, it) }
        } catch (t: Throwable) {
            emitError(onEvent, t, coordinator.snapshot().joinToString("\n") { it.content })
            return null
        }
        return toolCalls
    }

    /**
     * 构造工具回填后的第二轮请求：
     * - Chat Completions：assistant 工具调用消息 + tool 角色结果消息
     * - Responses API：function_call / function_call_output input item（call_id 一一对应）
     */
    private suspend fun buildSecondRoundRequest(
        request: ChatRoundRequest,
        conv: Conversation,
        calls: List<ChatStreamParser.AggregatedToolCall>
    ): ChatRoundRequest = when (request) {
        is ChatRoundRequest.Completions -> {
            val toolMessages = buildToolResultMessages(request.request.messages, conv, calls)
            ChatRoundRequest.Completions(
                request.request.copy(
                    messages = toolMessages,
                    tools = null,
                    tool_choice = null
                )
            )
        }
        is ChatRoundRequest.Responses -> {
            val items = buildResponsesToolResultItems(request.request.input, conv, calls)
            ChatRoundRequest.Responses(
                request.request.copy(
                    input = items,
                    tools = null,
                    tool_choice = null
                )
            )
        }
    }

    /**
     * 构造工具回填消息序列：原始消息 + assistant 工具调用 + tool 角色检索结果。
     * 只响应 read_memory；其他工具名回填"工具不存在"，避免伪造。
     */
    private suspend fun buildToolResultMessages(
        originalMessages: List<ChatMessage>,
        conv: Conversation,
        calls: List<ChatStreamParser.AggregatedToolCall>
    ): List<ChatMessage> {
        val result = originalMessages.toMutableList()
        result += ChatMessage(
            role = "assistant",
            content = null,
            tool_calls = calls.map { call ->
                AssistantToolCall(
                    id = call.id ?: "call_${call.index}",
                    type = "function",
                    function = com.quiddity.app.data.remote.AssistantToolCallFunction(
                        name = call.name,
                        arguments = call.arguments
                    )
                )
            }
        )
        val memory = PromptBuilder.buildMemoryDrawerContent(conv)
        calls.forEach { call ->
            val toolCallId = call.id ?: "call_${call.index}"
            result += ChatMessage(
                role = "tool",
                tool_call_id = toolCallId,
                content = resolveToolContent(call, conv, memory)
            )
        }
        return result
    }

    /**
     * 构造 Responses API 工具回填 input：原始消息 + function_call item + function_call_output item。
     * 官方要求 call_id 非空唯一，且每个 function_call 必须有对应 function_call_output。
     */
    private suspend fun buildResponsesToolResultItems(
        originalInput: List<ResponsesInputItem>,
        conv: Conversation,
        calls: List<ChatStreamParser.AggregatedToolCall>
    ): List<ResponsesInputItem> {
        val result = originalInput.toMutableList()
        calls.forEach { call ->
            result += ResponsesInputItem(
                type = "function_call",
                call_id = call.id ?: "call_${call.index}",
                name = call.name,
                arguments = call.arguments
            )
        }
        val memory = PromptBuilder.buildMemoryDrawerContent(conv)
        calls.forEach { call ->
            result += ResponsesInputItem(
                type = "function_call_output",
                call_id = call.id ?: "call_${call.index}",
                output = resolveToolContent(call, conv, memory)
            )
        }
        return result
    }

    /**
     * 解析单个工具调用结果：read_memory / search_chat 走本地检索，其余工具回填"不存在"。
     */
    private suspend fun resolveToolContent(
        call: ChatStreamParser.AggregatedToolCall,
        conv: Conversation,
        memory: String
    ): String = when (call.name) {
        "read_memory" -> {
            MemorySearch.search(memory, parseToolQuery(call.arguments)).content
        }
        "search_chat" -> {
            val query = parseToolQuery(call.arguments)
            val messages = conversationRepo.observeMessages(conv.id).value
                .filterNot { it.isNotice }
            ChatRecordSearch.search(messages, query).content
        }
        else -> "工具 ${call.name} 不存在"
    }

    /** 解析工具参数 JSON 中的 query 字段；解析失败时回退使用原始参数字符串。 */
    private fun parseToolQuery(arguments: String): String {
        if (arguments.isBlank()) return ""
        return runCatching {
            val obj = Json.parseToJsonElement(arguments) as? JsonObject
            (obj?.get("query") as? JsonPrimitive)?.content.orEmpty()
        }.getOrDefault(arguments.take(200))
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
     */
    suspend fun streamGroupMemberReply(
        member: Conversation,
        group: Conversation,
        transcript: List<Message>,
        senderId: String,
        onEvent: suspend (Event) -> Unit
    ) {
        val settings = settingsRepo.currentSnapshot()
        // 方案六.2：群聊记录只取最近 N 条（默认 50，范围 1～200），在点击定格快照上截断。
        val trimmed = if (group.groupContextLimit > 0 && transcript.size > group.groupContextLimit) {
            transcript.takeLast(group.groupContextLimit)
        } else {
            transcript
        }
        val senderNames = resolveSenderNames(trimmed)
        val plan = GroupReplyPlanner.buildPlan(
            settings = settings,
            member = member,
            group = group,
            transcript = trimmed,
            senderId = senderId,
            tier = resolveMemberTier(member, settings),
            senderNames = senderNames,
            userName = member.userPersona.name.takeIf { it.isNotBlank() },
            webSearchResponsesUrl = resolveWebSearch(settings, member)
        )
        // 模型有时会误输出「名字：」前缀（如回复开头带其他成员名），
        // 在事件派发前剥掉，保证落库/展示内容不带任何名字前缀（方案五）。
        val prefixNames = buildList {
            member.persona.name.takeIf { it.isNotBlank() }?.let(::add)
            member.userPersona.name.takeIf { it.isNotBlank() }?.let(::add)
            addAll(senderNames.values)
        }
        val sanitizer = GroupReplyPrefixSanitizer(prefixNames)
        // 流式剥离「名字：」前缀：在切分之前移除，避免前缀被拆成独立消息后剥离成空白
        val prefixStripper = GroupReplyPrefixStripper(prefixNames)
        val cleanEvent: suspend (Event) -> Unit = { event ->
            when (event) {
                is Event.NewMessage ->
                    onEvent(Event.NewMessage(event.message.copy(content = sanitizer.clean(event.message.content))))
                is Event.UpdateMessage ->
                    onEvent(Event.UpdateMessage(event.message.copy(content = sanitizer.clean(event.message.content))))
                is Event.CompleteMessage ->
                    onEvent(Event.CompleteMessage(event.message.copy(content = sanitizer.clean(event.message.content))))
                is Event.Done -> onEvent(event)
                is Event.Error -> onEvent(event)
            }
        }
        plan.fold(
            onSuccess = { p ->
                val coordinator = coordinatorFactory(
                    group.id,
                    IdGenerator.newUuid(),
                    settings.multilineAutoSplit,
                    p.singleMessageTokens,
                    p.senderId
                )
                val roundRequest = p.responsesRequest?.let { ChatRoundRequest.Responses(it) }
                    ?: ChatRoundRequest.Completions(p.request)
                val effectiveApiUrl = p.responsesApiUrl ?: p.apiUrl
                runWithToolRound(
                    api, effectiveApiUrl, p.apiKey, roundRequest, coordinator, group, cleanEvent
                ) { prefixStripper.accept(it) }
            },
            onFailure = { emitError(onEvent, it, "") }
        )
    }

    /**
     * 解析群聊转述中出现的成员 id → 名字（未设置名字的成员回退显示 id）。
     */
    private fun resolveSenderNames(transcript: List<Message>): Map<String, String> {
        val names = mutableMapOf<String, String>()
        transcript.forEach { msg ->
            val senderId = msg.senderId ?: return@forEach
            if (senderId !in names) {
                val name = conversationRepo.getConversation(senderId)
                    ?.persona?.name
                    ?.takeIf { it.isNotBlank() }
                names[senderId] = name ?: senderId
            }
        }
        return names
    }

    /**
     * 解析成员的模型分级（member.apiCatalogId → activeCatalogId → catalog 第一条）。
     */
    private fun resolveMemberTier(member: Conversation, settings: AppSettings): ApiCatalogManager.ModelTier {
        val manager = apiCatalogManager ?: return ApiCatalogManager.ModelTier.BASIC
        val entry = settings.catalog
            .firstOrNull { it.id == member.apiCatalogId }
            ?: settings.catalog.firstOrNull { it.id == settings.activeCatalogId }
            ?: settings.catalog.firstOrNull()
            ?: return ApiCatalogManager.ModelTier.BASIC
        return manager.getModelTier(entry.apiModel, entry.providerId)
    }

    /**
     * 群聊自然接话快速判断接口（4.1，2.0.0 实现）。
     *
     * 规划：非流式、max_tokens 小（[QuiddityConstants.GROUP_DECIDE_MAX_TOKENS]），
     * 输出约定「0 / 要说的内容」；判断与记忆读取计入该成员开销。
     */
    suspend fun decideGroupResponder(
        members: List<Conversation>,
        transcript: List<Message>,
        message: Message
    ): String {
        throw NotImplementedError("群聊功能未实现：1.3.0 仅预留接口，2.0.0 实体加入")
    }

    /**
     * 群聊小本本压缩接口（4.1，2.0.0 实现）。
     *
     * 规划：达到条数阈值（[QuiddityConstants.GROUP_MEMORY_THRESHOLD]）后对群聊转述执行压缩，
     * 复用 6.5.2 两段输出格式：摘要 → groupMemory，索引 → 小抄「群聊记忆」一行；
     * 压缩调用 token 计入群聊开销。
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
            transcript.filterNot { it.isNotice },
            lastN = 0,
            senderNames = resolveSenderNames(transcript),
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

/**
 * 群聊回复前缀剥离器：模型误输出「名字：」前缀（含其他成员名/用户名/本人名）时，
 * 从消息内容开头连续剥掉。内容流式累计，每次事件都对完整内容重新判定，无需维护状态。
 */
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
