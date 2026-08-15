package com.quiddity.app.data.repo

import com.quiddity.app.data.local.AgentPermissionControl
import com.quiddity.app.data.local.AgentStore
import com.quiddity.app.data.model.AppSettings
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.data.remote.AssistantToolCall
import com.quiddity.app.data.remote.ChatApi
import com.quiddity.app.data.remote.ChatException
import com.quiddity.app.data.remote.ChatMessage
import com.quiddity.app.data.remote.ChatStreamParser
import com.quiddity.app.data.remote.ResponsesInputItem
import com.quiddity.app.data.remote.ResponsesTool
import com.quiddity.app.data.remote.ToolDefinition
import com.quiddity.app.data.repo.ConversationRepository
import com.quiddity.app.data.repo.SettingsRepository
import com.quiddity.app.domain.ApiCatalogManager
import com.quiddity.app.domain.ChatRecordSearch
import com.quiddity.app.domain.MemorySearch
import com.quiddity.app.domain.PromptBuilder
import com.quiddity.app.domain.StreamCoordinator
import com.quiddity.app.domain.agent.AgentContext
import com.quiddity.app.domain.agent.AgentRoundEffects
import com.quiddity.app.domain.agent.AgentTool
import com.quiddity.app.domain.agent.AgentToolCallRequest
import com.quiddity.app.domain.agent.AgentToolRegistry
import com.quiddity.app.domain.agent.AgentWorkflowController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val MAX_TOOL_RESULT_CHARS = 3000
private const val MAX_TOOL_ROUND_RESULT_CHARS = 12_000

internal class ToolResultBuilder(
    private val conversationRepo: ConversationRepository,
    private val apiCatalogManager: ApiCatalogManager?,
    private val agentToolRegistry: AgentToolRegistry?,
    private val agentStore: AgentStore?
) {

    /**
     * 解析会话是否启用 DeepSeek 官方服务端联网搜索。
     *
     * 判定链：会话开关开启 → 解析实际 catalog 条目 → 该条目支持 Responses 服务端搜索。
     * 返回官方 /responses 端点；不满足任一条件返回 null（走 Chat Completions）。
     */
    fun resolveWebSearch(settings: AppSettings, conv: Conversation): String? {
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
     *
     * 请求目标 URL 由 [ChatRoundRequest] 自身携带（Completions / Responses 端点不同），
     * 私聊与群聊统一在此按类型取值，不再由调用点各自传 URL。
     */
    /**
     * 通用流式驱动（含 read_memory 工具轮）。
     *
     * @return true = 本轮正常完成（含工具回填轮）；false = 任一轮出错（错误已通过 [ChatRepository.Event.Error] 派发）
     */
    suspend fun buildNextRoundRequest(
        request: ChatRoundRequest,
        resolved: List<Pair<ChatStreamParser.AggregatedToolCall, String>>,
        reasoningText: String,
        onEvent: suspend (ChatRepository.Event) -> Unit,
        keepTools: Boolean
    ): ChatRoundRequest = when (request) {
        is ChatRoundRequest.Completions -> {
            val toolMessages = buildToolResultMessages(request.request.messages, resolved, reasoningText)
            ChatRoundRequest.Completions(
                request.request.copy(
                    messages = toolMessages,
                    tools = if (keepTools) request.request.tools else null,
                    tool_choice = if (keepTools) request.request.tool_choice else null
                ),
                apiUrl = request.apiUrl
            )
        }
        is ChatRoundRequest.Responses -> {
            val items = buildResponsesToolResultItems(request.request.input, resolved, reasoningText)
            ChatRoundRequest.Responses(
                request.request.copy(
                    input = items,
                    tools = if (keepTools) request.request.tools else null,
                    tool_choice = if (keepTools) request.request.tool_choice else null
                ),
                apiUrl = request.apiUrl
            )
        }
    }

    /**
     * 工具轮失败后的降级：关闭工具，把错误原因作为普通上下文交给模型，
     * 让 AI 在回复里如实报告这次报错，而不是单开一条错误消息。
     */
    fun buildNoToolsFallback(
        request: ChatRoundRequest,
        resolved: List<Pair<ChatStreamParser.AggregatedToolCall, String>>,
        errorText: String
    ): ChatRoundRequest {
        val summary = resolved.joinToString("\n") { (call, content) ->
            "【${call.name}】${content.take(300)}"
        }.take(MAX_TOOL_RESULT_CHARS)
        val context = buildString {
            append("工具已执行，结果如下：\n").append(summary)
            if (summary.isNotBlank()) {
                append("\n\n把工具结果回填的请求被模型接口拒绝（").append(errorText).append("），")
                append("已改为直接带结果继续。请基于以上工具结果，以你的口吻向用户正常回答。")
            } else {
                append("（工具结果为空或接口异常：").append(errorText).append("）")
                append("请如实说明这次工具调用的情况与下一步建议。")
            }
        }
        return when (request) {
            is ChatRoundRequest.Completions -> ChatRoundRequest.Completions(
                request.request.copy(
                    messages = request.request.messages + ChatMessage(role = "user", content = context),
                    tools = null,
                    tool_choice = null
                ),
                request.apiUrl
            )
            is ChatRoundRequest.Responses -> ChatRoundRequest.Responses(
                request.request.copy(
                    input = request.request.input + ResponsesInputItem(
                        type = "message",
                        role = "user",
                        content = kotlinx.serialization.json.JsonPrimitive(context)
                    ),
                    tools = null,
                    tool_choice = null
                ),
                request.apiUrl
            )
        }
    }

    /**
     * 构造工具回填消息序列：原始消息 + assistant 工具调用 + tool 角色检索结果。
     * 只响应 read_memory；其他工具名回填"工具不存在"，避免伪造。
     */
    suspend fun buildToolResultMessages(
        originalMessages: List<ChatMessage>,
        resolved: List<Pair<ChatStreamParser.AggregatedToolCall, String>>,
        reasoningText: String
    ): List<ChatMessage> {
        val result = trimToolRounds(originalMessages, keepRounds = 2).toMutableList()
        result += ChatMessage(
            role = "assistant",
            content = null,
            // DeepSeek 思考模式强制要求：工具轮 assistant 消息必须携带 reasoning_content 字段
            // （校验字段存在性而非内容非空——空串也可通过；缺失字段即 400
            // "The reasoning_text in the thinking mode must be passed back to the API"）。
            // 因此无条件回传上一轮累积的 reasoning_text（可能为空串）。
            reasoning_content = reasoningText,
            tool_calls = resolved.map { (call, _) ->
                AssistantToolCall(
                    id = call.id ?: "call_${call.index}",
                    type = "function",
                    function = com.quiddity.app.data.remote.AssistantToolCallFunction(
                        name = call.name,
                        arguments = sanitizeToolArguments(call.arguments)
                    )
                )
            }
        )
        resolved.forEach { (call, content) ->
            val toolCallId = call.id ?: "call_${call.index}"
            result += ChatMessage(
                role = "tool",
                tool_call_id = toolCallId,
                content = content
            )
        }
        return result
    }

    /**
     * 构造 Responses API 工具回填 input：原始消息 + reasoning item（思考模式必回传）
     * + function_call item + function_call_output item。
     * 官方要求 call_id 非空唯一，且每个 function_call 必须有对应 function_call_output；
     * DeepSeek 思考模式下工具轮必须在 function_call 前回传上一轮的 reasoning item，
     * 否则 400 "The reasoning_text in the thinking mode must be passed back to the API"。
     */
    suspend fun buildResponsesToolResultItems(
        originalInput: List<ResponsesInputItem>,
        resolved: List<Pair<ChatStreamParser.AggregatedToolCall, String>>,
        reasoningText: String
    ): List<ResponsesInputItem> {
        val result = trimResponsesToolRounds(originalInput, keepRounds = 2).toMutableList()
        // 思考模式工具轮：回传上一轮 reasoning_text（缺失即 400）
        if (reasoningText.isNotBlank()) {
            result += ResponsesInputItem(
                type = "reasoning",
                content = kotlinx.serialization.json.JsonArray(
                    listOf(
                        kotlinx.serialization.json.buildJsonObject {
                            put("type", kotlinx.serialization.json.JsonPrimitive("reasoning_text"))
                            put("text", kotlinx.serialization.json.JsonPrimitive(reasoningText))
                        }
                    )
                )
            )
        }
        resolved.forEach { (call, _) ->
            result += ResponsesInputItem(
                type = "function_call",
                call_id = call.id ?: "call_${call.index}",
                name = call.name,
                arguments = sanitizeToolArguments(call.arguments)
            )
        }
        resolved.forEach { (call, content) ->
            result += ResponsesInputItem(
                type = "function_call_output",
                call_id = call.id ?: "call_${call.index}",
                output = content
            )
        }
        return result
    }

    /**
     * 工具轮消息裁剪：多轮工具循环会把每轮的 assistant 工具调用 + tool 结果累积进请求，
     * 轮数一多上下文迅速膨胀（可能触发接口拒绝/超时中断）。只保留最近 [keepRounds] 轮
     * 的工具消息对——更早轮次的工具消息删除（模型对它们的叙述已在正文历史里，工具消息
     * 本身不再需要；assistant 工具调用与其 tool 结果必须成对删除）。
     */
    fun trimToolRounds(messages: List<ChatMessage>, keepRounds: Int): List<ChatMessage> {
        val roundStarts = messages.indices.filter { messages[it].tool_calls?.isNotEmpty() == true }
        if (roundStarts.size <= keepRounds) return messages
        val keepFrom = roundStarts[roundStarts.size - keepRounds]
        return messages.subList(0, keepFrom) + messages.subList(keepFrom, messages.size)
    }

    /** Responses API 版本的工具轮裁剪（function_call 与 function_call_output 成对）。 */
    fun trimResponsesToolRounds(input: List<ResponsesInputItem>, keepRounds: Int): List<ResponsesInputItem> {
        val roundStarts = input.indices.filter { input[it].type == "function_call" }
        if (roundStarts.size <= keepRounds) return input
        val keepFrom = roundStarts[roundStarts.size - keepRounds]
        return input.subList(0, keepFrom) + input.subList(keepFrom, input.size)
    }

    /**
     * 解析本轮工具调用结果（批量）：
     * - AGENT 会话走注册表批量分发（读工具真实执行、写工具安全门控 + 批量授权）；
     * - 私聊/群聊保持原逻辑：read_memory / search_chat 本地检索，其余回填"不存在"。
     */
    suspend fun resolveToolContents(
        calls: List<ChatStreamParser.AggregatedToolCall>,
        conv: Conversation,
        memory: String,
        onEvent: suspend (ChatRepository.Event) -> Unit,
        roundEffects: AgentRoundEffects? = null,
        workflow: AgentWorkflowController? = null
    ): List<Pair<ChatStreamParser.AggregatedToolCall, String>> {
        val rawResults = if (conv.type == ConversationType.AGENT) {
            resolveAgentToolContents(calls, conv, onEvent, roundEffects, workflow)
        } else {
            calls.map { call -> resolveNonAgentToolContent(call, conv, memory) }
        }
        var budget = MAX_TOOL_ROUND_RESULT_CHARS
        return calls.zip(rawResults).map { (call, raw) ->
            val base = finalizeToolResult(call, raw)
            val trimmed = when {
                budget <= 0 -> "（本轮工具结果总量已达上限，后续结果已省略）"
                base.length > budget -> {
                    val cut = base.take(budget)
                    budget = 0
                    val lastNewline = cut.lastIndexOf('\n')
                    val clean = if (lastNewline > 0) cut.substring(0, lastNewline) else cut
                    "$clean\n（结果过长，已截断，共 ${base.length} 字符）"
                }
                else -> {
                    budget -= base.length
                    base
                }
            }
            call to trimmed
        }
    }

    suspend fun resolveAgentToolContents(
        calls: List<ChatStreamParser.AggregatedToolCall>,
        conv: Conversation,
        onEvent: suspend (ChatRepository.Event) -> Unit,
        roundEffects: AgentRoundEffects? = null,
        workflow: AgentWorkflowController? = null
    ): List<String> {
        val registry = agentToolRegistry
        val ctx = agentContext(conv, onEvent, roundEffects)
        if (registry == null || ctx == null) {
            return calls.map { "工具 " + it.name + " 不可用（Agent 工具未初始化）" }
        }
        // ===== 方案 B'：去重 + 失败自动重试（0~1 次调用零介入） =====
        // 1. 去重预筛：同工具同参数且已成功的调用直接复用结果（不重复执行）
        data class Plan(
            val call: ChatStreamParser.AggregatedToolCall,
            val cached: AgentWorkflowController.CallRecord?
        )
        fun parseArgs(call: ChatStreamParser.AggregatedToolCall): JsonObject =
            runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(call.arguments) as? JsonObject
            }.getOrNull() ?: JsonObject(emptyMap())
        val plans = calls.map { call ->
            Plan(call, workflow?.findDuplicate(call.name, parseArgs(call)))
        }
        val toExecute = plans.filter { it.cached == null }.map { it.call }
        // 2. 真实执行（仅去重未命中部分）
        val results = registry.dispatchAll(
            toExecute.map { AgentToolCallRequest(it.name, it.arguments) },
            ctx
        )
        // 3. 失败自动重试：真实执行失败（非用户取消/未确认）且未重试过 → 重试一次，
        //    重试时确认自动通过（用户已确认过同一操作），其余门控照常
        val finalResults = toExecute.zip(results).map { (call, raw) ->
            val retryable = isRetryableToolFailure(raw)
            if (workflow != null && workflow.shouldRetry(call.name, parseArgs(call), ok = false, retryable = retryable)) {
                workflow.markRetried(call.name, parseArgs(call))
                val retryCtx = ctx.copy(
                    confirmRequest = { _, _ -> true },
                    confirmRequestBatch = { _ -> true }
                )
                val retryRaw = registry.dispatch(call.name, call.arguments, retryCtx)
                "首次执行失败：" + raw + "\n已自动重试：" + retryRaw
            } else {
                raw
            }
        }
        // 4. 记录真实执行历史（供去重与计数），并把去重命中结果按原顺序回填
        toExecute.zip(finalResults).forEach { (call, raw) ->
            workflow?.record(call.name, parseArgs(call), ok = isToolResultSuccess(raw), result = raw)
        }
        val executedIter = finalResults.iterator()
        return plans.map { plan ->
            plan.cached?.let { cached ->
                "（已去重：该调用与此前的成功调用相同，直接复用结果）\n" + cached.result
            } ?: executedIter.next()
        }
    }

    /**
     * 是否为「可重试」的工具失败：真正的执行失败（超时/报错/无数据等）。
     * 用户取消（"用户已取消执行"）与未确认（"需要用户确认后才能执行"）导致的
     * 「失败」不可重试——重试会绕过用户意图。
     */
    fun isRetryableToolFailure(raw: String): Boolean =
        !raw.contains("用户已取消") &&
            !raw.contains("需要用户确认") &&
            !isToolResultSuccess(raw)

    suspend fun resolveNonAgentToolContent(
        call: ChatStreamParser.AggregatedToolCall,
        conv: Conversation,
        memory: String
    ): String = try {
        when (call.name) {
            "read_memory" -> MemorySearch.search(memory, parseToolQuery(call.arguments)).content
            "search_chat" -> {
                val query = parseToolQuery(call.arguments)
                val messages = conversationRepo.observeMessages(conv.id).value
                    .filterNot { it.isNotice }
                ChatRecordSearch.search(messages, query).content
            }
            else -> "工具 ${call.name} 不存在"
        }
    } catch (c: kotlinx.coroutines.CancellationException) {
        throw c
    } catch (t: Throwable) {
        // 工具执行失败不中断整轮回复：把报错信息作为工具结果回填，让模型继续输出
        "工具 ${call.name} 执行失败：${t.message?.take(200) ?: t.javaClass.simpleName}"
    }

    fun finalizeToolResult(
        call: ChatStreamParser.AggregatedToolCall,
        raw: String
    ): String {
        val marked = if (com.quiddity.app.domain.LocalThinker.isToolError(raw)) {
            "【工具返回异常】$raw"
        } else {
            raw
        }
        return if (marked.length <= MAX_TOOL_RESULT_CHARS) {
            marked
        } else {
            val cut = marked.take(MAX_TOOL_RESULT_CHARS)
            val lastNewline = cut.lastIndexOf('\n')
            val clean = if (lastNewline > 0) cut.substring(0, lastNewline) else cut
            "$clean\n（结果过长，已截断，共 ${marked.length} 字符，如需完整数据请缩小范围重试）"
        }
    }

    /** 粗略判断工具结果是否成功（供聊天页工具痕迹展示状态）。 */
    fun isToolResultSuccess(content: String): Boolean =
        !content.contains("【工具返回异常】") &&
            !content.contains("失败") &&
            !content.contains("取消") &&
            !content.contains("不存在") &&
            !content.contains("未启用") &&
            !content.contains("尚未接入") &&
            !content.contains("未获得")

    /**
     * 清洗模型回传的工具参数：空串/非法 JSON 一律补为 "{}"，
     * 避免第二轮请求携带非法 arguments 被 API 以 400 拒绝。
     */
    fun sanitizeToolArguments(arguments: String): String {
        val trimmed = arguments.trim()
        if (trimmed.isEmpty()) return "{}"
        return runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(trimmed)
        }.fold(
            onSuccess = { trimmed },
            onFailure = { "{}" }
        )
    }

    fun agentContext(
        conv: Conversation,
        onEvent: suspend (ChatRepository.Event) -> Unit,
        roundEffects: AgentRoundEffects? = null
    ): AgentContext? {
        val store = agentStore ?: return null
        val settings = store.snapshot()
        return AgentContext(
            conversation = conv,
            switches = settings.toolSwitches,
            blacklist = settings.blacklist.toSet(),
            auditAppend = { store.appendAudit(it) },
            autoConfirm = settings.permissionControl == AgentPermissionControl.FULL,
            confirmRequest = { tool, args -> requestToolConfirmBatch(onEvent, listOf(tool to args)) },
            confirmRequestBatch = { items -> requestToolConfirmBatch(onEvent, items) },
            roundEffects = roundEffects ?: AgentRoundEffects()
        )
    }

    /**
     * 弹出批量确认框并挂起等待用户决定；
     * 流被取消时自动按“取消”收尾，避免弹窗悬挂。
     */
    suspend fun requestToolConfirmBatch(
        onEvent: suspend (ChatRepository.Event) -> Unit,
        items: List<Pair<AgentTool, JsonObject>>
    ): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        onEvent(
            ChatRepository.Event.ToolConfirmBatch(
                items.map { ChatRepository.ToolConfirmItem(it.first.name, it.second) }
            ) { approved -> deferred.complete(approved) }
        )
        return try {
            deferred.await()
        } catch (c: kotlinx.coroutines.CancellationException) {
            deferred.complete(false)
            throw c
        }
    }


    /** 解析工具参数 JSON 中的 query 字段；解析失败时回退使用原始参数字符串。 */
    fun parseToolQuery(arguments: String): String {
        if (arguments.isBlank()) return ""
        return runCatching {
            val obj = Json.parseToJsonElement(arguments) as? JsonObject
            (obj?.get("query") as? JsonPrimitive)?.content.orEmpty()
        }.getOrDefault(arguments.take(200))
    }

    suspend fun dispatch(onEvent: suspend (ChatRepository.Event) -> Unit, signal: StreamCoordinator.Signal) {
        when (signal) {
            is StreamCoordinator.Signal.New -> onEvent(ChatRepository.Event.NewMessage(signal.message))
            is StreamCoordinator.Signal.Update -> onEvent(ChatRepository.Event.UpdateMessage(signal.message))
            is StreamCoordinator.Signal.Complete -> onEvent(ChatRepository.Event.CompleteMessage(signal.message))
        }
    }

    suspend fun emitError(onEvent: suspend (ChatRepository.Event) -> Unit, t: Throwable, partial: String) {
        android.util.Log.w("ChatRepository", "流式请求错误：${t.message}", t)
        onEvent(ChatRepository.Event.Error(t, partial))
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
}
