package com.quiddity.app.data.repo

import com.quiddity.app.data.local.AgentPermissionControl
import com.quiddity.app.data.local.AgentStore
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ConversationType
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
import com.quiddity.app.domain.PromptBuilder
import com.quiddity.app.domain.StreamCoordinator
import com.quiddity.app.domain.agent.AgentRoundEffects
import com.quiddity.app.domain.agent.AgentToolRegistry
import com.quiddity.app.domain.agent.AgentWorkflowController
import com.quiddity.app.util.IdGenerator
import com.quiddity.app.util.QuiddityConstants

private const val TOOL_ROUND_LIMIT_NUDGE =
    "?????????????????????????????????????????????????????????????????"

/** ??????????????? */
internal sealed interface NoToolRoundResult {
    /** ?????????????? */
    data object Completed : NoToolRoundResult
    /** ????????????????????????? */
    data class WithToolCalls(
        val round: StreamRoundResult,
        val request: ChatRoundRequest
    ) : NoToolRoundResult
    /** ???????????????? */
    data object Failed : NoToolRoundResult
}

private const val TOOL_CALL_TRUNCATED_NUDGE =
    "?????????????????????????????????????????"

private const val MAX_FINAL_RECOVERY_ROUNDS = 3

private const val TRUNCATE_CONTINUE_NUDGE =
    "?????????????????????????????????????????????????"

private const val EMPTY_REPLY_NUDGE =
    "?????????????????????????????????????????????????"

internal class ToolRoundRunner(
    private val api: ChatApi,
    private val settingsRepo: SettingsRepository,
    private val agentToolRegistry: AgentToolRegistry?,
    private val agentStore: AgentStore?,
    private val coordinatorFactory: (conversationId: String, runId: String, splitEnabled: Boolean, singleMessageTokens: Int, senderId: String?, thinking: String) -> StreamCoordinator,
    private val singleStreamRunner: SingleStreamRunner,
    private val toolResultBuilder: ToolResultBuilder,
    private val onTakeLastRounds: (List<Message>, Int, Int) -> List<Message>
) {

    /**
     * 发送用户消息并启动流式回复。
     *
     * @param conv 当前会话
     * @param history 历史消息列表（含最新用户消息）
     * @param memoryStrategy 记忆策略覆盖值（null = 跟随 [Conversation.memoryStrategy]，
     *   仍为 null 时回退为随身带 CARRY）。TOOL 模式下请求携带 read_memory 工具，
     *   模型按需检索记忆，不再每轮重读压缩摘要。
     * @param regeneratePreviousReply 重说场景下上一版回复的原文（null = 正常回复）。
     *   非空时提示词会标记本次为「重说」并要求换一种表达，避免输出与上一版雷同。
     * @param onEvent suspend 事件回调（由 ViewModel 串行化执行）
     */
    suspend fun streamAssistantReply(
        conv: Conversation,
        history: List<Message>,
        memoryStrategy: String? = null,
        regeneratePreviousReply: String? = null,
        thinking: String = "",
        onEvent: suspend (ChatRepository.Event) -> Unit
    ) {
        val settings = settingsRepo.currentSnapshot()
        val access = ApiAccess.resolve(settings, conv)
        if (access is ApiAccess.Failure) {
            return toolResultBuilder.emitError(onEvent, access.toChatException(), "")
        }
        access as ApiAccess.Resolved

        val effectiveStrategy = memoryStrategy
            ?: conv.memoryStrategy
            ?: QuiddityConstants.MEMORY_STRATEGY_CARRY
        val isAgent = conv.type == ConversationType.AGENT
        val systemPrompt = if (isAgent) {
            PromptBuilder.buildAgentSystemPrompt(
                conv,
                effectiveStrategy,
                thinkingDepth = if (conv.thinkingEnabled) conv.thinkingDepth else null
            )
        } else {
            PromptBuilder.buildSystemPrompt(
                conv = conv,
                memoryStrategy = effectiveStrategy,
                regeneratePreviousReply = regeneratePreviousReply,
                thinkingDepth = if (conv.thinkingEnabled) conv.thinkingDepth else null
            )
        }
        val contextLimit = if (conv.contextLimit > 0) conv.contextLimit else settings.globalContextLimit
        // 过滤 isNotice 提示气泡与 isThinking 思考消息：不发给 LLM
        val filteredHistory = history.filterNot { it.isNotice || it.isThinking }
        val trimmedHistory = onTakeLastRounds(filteredHistory, contextLimit, 4)
        val apiMessages = PromptBuilder.toApiMessages(systemPrompt, trimmedHistory)

        val maxTokens = conv.maxTokens ?: settings.globalMaxTokens
        val singleMsgTokens = conv.singleMessageTokens ?: settings.globalSingleMessageTokens
        // 按模型支持的最高温度钳制：部分模型仅支持 0～1.0，超限请求会被服务端拒绝
        val temperature = QuiddityConstants.clampTemperature(
            conv.temperature ?: settings.globalTemperature,
            access.maxTemperature
        )
        val toolStrategyActive = effectiveStrategy == QuiddityConstants.MEMORY_STRATEGY_TOOL &&
            conv.compressedMemory.isNotBlank()
        val agentTools = if (isAgent) {
            agentToolRegistry?.tools()?.map { it.toToolDefinition() }
        } else {
            null
        }
        val buildRequest: (String?) -> ChatRoundRequest = {
            buildChatRound(
                access = access,
                systemPrompt = systemPrompt,
                apiMessages = apiMessages,
                maxTokens = maxTokens,
                temperature = temperature,
                responsesUrl = toolResultBuilder.resolveWebSearch(settings, conv),
                // 思考深度映射到 API reasoning_effort（浅=low / 深=high），
                // 控制模型内部推理强度；仅 DeepSeek 模型携带，其他模型忽略。
                reasoningEffort = if (conv.thinkingEnabled &&
                    access.model.contains("deepseek", ignoreCase = true)
                ) {
                    QuiddityConstants.reasoningEffortForDepth(conv.thinkingDepth)
                } else {
                    null
                },
                tools = agentTools ?: if (toolStrategyActive) {
                    listOf(
                        PromptBuilder.buildReadMemoryTool(),
                        PromptBuilder.buildSearchChatTool()
                    )
                } else {
                    null
                },
                tool_choice = if (agentTools != null || toolStrategyActive) "auto" else null
            )
        }
        runWithToolRound(
            api = api,
            apiKey = access.apiKey,
            request = buildRequest(null),
            coordinator = coordinatorFactory(
                conv.id,
                IdGenerator.newUuid(),
                settings.multilineAutoSplit && !isAgent,
                singleMsgTokens,
                null,
                thinking
            ),
            conv = conv,
            onEvent = onEvent,
            thinkingActive = conv.thinkingEnabled,
            // Agent 模式：整条用户指令共享轮次追踪器 + 任务编排器（方案 B'：去重/重试）
            roundEffects = if (isAgent) AgentRoundEffects() else null,
            workflow = if (isAgent) AgentWorkflowController() else null
        )
    }

    /**
     * 让 AI 先发消息（空对话开场）。
     *
     * @param regeneratePreviousReply 重说场景下上一版开场回复的原文（null = 正常开场）。
     *   非空时提示词会标记本次为「重说」并要求换一种表达，避免输出与上一版雷同。
     */
    suspend fun letAiStart(
        conv: Conversation,
        memoryStrategy: String? = null,
        regeneratePreviousReply: String? = null,
        thinking: String = "",
        onEvent: suspend (ChatRepository.Event) -> Unit
    ) {
        val settings = settingsRepo.currentSnapshot()
        val access = ApiAccess.resolve(settings, conv)
        if (access is ApiAccess.Failure) {
            return toolResultBuilder.emitError(onEvent, access.toChatException(), "")
        }
        access as ApiAccess.Resolved

        val effectiveStrategy = memoryStrategy
            ?: conv.memoryStrategy
            ?: QuiddityConstants.MEMORY_STRATEGY_CARRY
        val isAgent = conv.type == ConversationType.AGENT
        val systemPrompt = if (isAgent) {
            PromptBuilder.buildAgentSystemPrompt(
                conv,
                effectiveStrategy,
                thinkingDepth = if (conv.thinkingEnabled) conv.thinkingDepth else null
            )
        } else {
            PromptBuilder.buildSystemPrompt(
                conv = conv,
                memoryStrategy = effectiveStrategy,
                regeneratePreviousReply = regeneratePreviousReply,
                thinkingDepth = if (conv.thinkingEnabled) conv.thinkingDepth else null
            )
        }
        // 引导：让 AI 主动发起对话
        val guidedMessages = listOf(
            ChatMessage(role = "system", content = systemPrompt),
            ChatMessage(role = "user", content = PromptBuilder.LET_AI_START_GUIDE)
        )

        val maxTokens = conv.maxTokens ?: settings.globalMaxTokens
        val singleMsgTokens = conv.singleMessageTokens ?: settings.globalSingleMessageTokens
        val temperature = QuiddityConstants.clampTemperature(
            conv.temperature ?: settings.globalTemperature,
            access.maxTemperature
        )
        val toolStrategyActive = effectiveStrategy == QuiddityConstants.MEMORY_STRATEGY_TOOL &&
            conv.compressedMemory.isNotBlank()
        val agentTools = if (isAgent) {
            agentToolRegistry?.tools()?.map { it.toToolDefinition() }
        } else {
            null
        }
        val buildRequest: (String?) -> ChatRoundRequest = {
            buildChatRound(
                access = access,
                systemPrompt = systemPrompt,
                apiMessages = guidedMessages,
                maxTokens = maxTokens,
                temperature = temperature,
                responsesUrl = toolResultBuilder.resolveWebSearch(settings, conv),
                reasoningEffort = if (conv.thinkingEnabled &&
                    access.model.contains("deepseek", ignoreCase = true)
                ) {
                    QuiddityConstants.reasoningEffortForDepth(conv.thinkingDepth)
                } else {
                    null
                },
                tools = agentTools ?: if (toolStrategyActive) {
                    listOf(
                        PromptBuilder.buildReadMemoryTool(),
                        PromptBuilder.buildSearchChatTool()
                    )
                } else {
                    null
                },
                tool_choice = if (agentTools != null || toolStrategyActive) "auto" else null
            )
        }
        runWithToolRound(
            api = api,
            apiKey = access.apiKey,
            request = buildRequest(null),
            coordinator = coordinatorFactory(
                conv.id,
                IdGenerator.newUuid(),
                settings.multilineAutoSplit && !isAgent,
                singleMsgTokens,
                null,
                thinking
            ),
            conv = conv,
            onEvent = onEvent,
            thinkingActive = conv.thinkingEnabled
        )
    }

    /**
     * 解析会话是否启用 DeepSeek 官方服务端联网搜索。
     *
     * 判定链：会话开关开启 → 解析实际 catalog 条目 → 该条目支持 Responses 服务端搜索。
     * 返回官方 /responses 端点；不满足任一条件返回 null（走 Chat Completions）。
     */
    suspend fun runWithToolRound(
        api: ChatApi,
        apiKey: String,
        request: ChatRoundRequest,
        coordinator: StreamCoordinator,
        conv: Conversation,
        onEvent: suspend (ChatRepository.Event) -> Unit,
        thinkingActive: Boolean = true,
        /**
         * 内容进入协调器前的流式转换（群聊用于剥离「名字：」前缀，
         * 避免前缀在切分阶段被拆成独立消息、剥离后变成空白消息）。
         */
        contentTransform: (String) -> String = { it },
        /**
         * Agent 单轮行为追踪器（一条用户指令共享；非 Agent 模式为 null）。
         * 结束时把创建路径与更改项通过 [ChatRepository.Event.AgentRoundEffects] 派发出去。
         */
        roundEffects: AgentRoundEffects? = null,
        /**
         * Agent 任务编排器（方案 B'）：一条用户指令共享；非 Agent 模式为 null。
         * 负责同轮工具调用的去重与失败自动重试（0~1 次调用零介入）。
         */
        workflow: AgentWorkflowController? = null
    ): Boolean {
        val apiUrl = request.apiUrl
        var lastError: Throwable? = null
        val recordFailure: suspend (Throwable) -> Unit = { t -> lastError = t }
        val memory = PromptBuilder.buildMemoryDrawerContent(conv)
        var currentRequest = request
        var reasoningText = ""
        val budget = ToolRoundBudget()
        var lastResolved: List<Pair<ChatStreamParser.AggregatedToolCall, String>> = emptyList()

        while (!budget.reachedLimit) {
            var round = singleStreamRunner.runSingleStream(
                api, apiUrl, apiKey, currentRequest, coordinator, onEvent, contentTransform, thinkingActive,
                onFailure = recordFailure
            )
            if (round == null) {
                val recovered = handleToolRoundFailure(
                    api, apiUrl, apiKey, currentRequest, coordinator, conv, onEvent, contentTransform,
                    thinkingActive, budget.totalRounds, lastResolved, lastError, recordFailure
                )
                if (!recovered) return false
                break
            }
            if (round.reasoningText.isNotBlank()) reasoningText = round.reasoningText
            var calls = round.toolCalls
            if (calls.isEmpty()) {
                // ===== 无工具调用收尾：截断自动续写 / 空回复（AI 撤回）自动兜底重试 =====
                val recovered = recoverNoToolRound(
                    api, apiUrl, apiKey, currentRequest, coordinator, conv, onEvent, contentTransform,
                    thinkingActive, budget.totalRounds, lastResolved, lastError, recordFailure, round
                )
                when (recovered) {
                    is NoToolRoundResult.Completed -> break
                    is NoToolRoundResult.Failed -> return false
                    is NoToolRoundResult.WithToolCalls -> {
                        currentRequest = recovered.request
                        round = recovered.round
                        calls = recovered.round.toolCalls
                    }
                }
            }
            if (round.truncated && calls.isNotEmpty()) {
                // ????????????????????????????????
                // ???????????????????????
                budget.consume(emptyList())
                if (budget.reachedLimit) break
                currentRequest = buildContinueRequest(currentRequest, null, TOOL_CALL_TRUNCATED_NUDGE)
                coordinator.setMergeWithPrevious(true)
                continue
            }
            // 工具轮结束（本轮有工具调用）：记录正文段边界（合并模式下工具轮之间的
            // 正文分界，供 UI 插入工具痕迹），并把带边界的最新消息派发出去持久化
            // （协调器内部修改不会自动同步到已落库消息）
            coordinator.markSegmentEnd()
            coordinator.snapshot().lastOrNull()?.let { msg ->
                if (msg.toolSegmentEnds.isNotEmpty()) {
                    onEvent(ChatRepository.Event.UpdateMessage(msg))
                }
            }
            calls.forEach { onEvent(ChatRepository.Event.ToolUse(it.name)) }
            val resolved = toolResultBuilder.resolveToolContents(calls, conv, memory, onEvent, roundEffects, workflow)
            lastResolved = resolved
            resolved.forEach { (call, content) ->
                onEvent(ChatRepository.Event.ToolResult(call.name, toolResultBuilder.isToolResultSuccess(content), content.take(500)))
            }
            budget.consume(resolved.map { it.first.name })
            val keepTools = budget.keepTools()
            currentRequest = try {
                toolResultBuilder.buildNextRoundRequest(currentRequest, resolved, reasoningText, onEvent, keepTools)
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                toolResultBuilder.emitError(onEvent, t, coordinator.snapshot().joinToString("\n") { it.content })
                singleStreamRunner.appendFailureMessage(
                    conv, coordinator, onEvent,
                    "工具结果准备失败，无法继续回复（${t.message?.take(120) ?: t.javaClass.simpleName}）。请重试。"
                )
                return false
            }
            // 工具轮之后的轮次：正文合并到上一条消息，多轮工具循环的正文单条化
            coordinator.setMergeWithPrevious(true)
        }

        if (budget.reachedLimit) {
            currentRequest = buildContinueRequest(currentRequest, null, TOOL_ROUND_LIMIT_NUDGE)
            var finalRound = singleStreamRunner.runSingleStream(
                api, apiUrl, apiKey, currentRequest, coordinator, onEvent, contentTransform, thinkingActive,
                onFailure = recordFailure
            )
            if (finalRound == null) {
                val recovered = handleToolRoundFailure(
                    api, apiUrl, apiKey, currentRequest, coordinator, conv, onEvent, contentTransform,
                    thinkingActive, budget.totalRounds, lastResolved, lastError, recordFailure
                )
                if (!recovered) return false
            } else {
                if (finalRound.toolCalls.isNotEmpty()) {
                    // 轮次上限内的最后一轮仍发起工具调用：照常执行，不再进入下一轮
                    finalRound.toolCalls.forEach { onEvent(ChatRepository.Event.ToolUse(it.name)) }
                    val resolved = toolResultBuilder.resolveToolContents(
                        finalRound.toolCalls, conv, memory, onEvent, roundEffects, workflow
                    )
                    lastResolved = resolved
                    resolved.forEach { (call, content) ->
                        onEvent(ChatRepository.Event.ToolResult(call.name, toolResultBuilder.isToolResultSuccess(content), content.take(500)))
                    }
                    currentRequest = try {
                        toolResultBuilder.buildNextRoundRequest(
                            currentRequest, resolved, finalRound.reasoningText, onEvent, keepTools = false
                        )
                    } catch (c: kotlinx.coroutines.CancellationException) {
                        throw c
                    } catch (t: Throwable) {
                        toolResultBuilder.emitError(onEvent, t, coordinator.snapshot().joinToString("\n") { it.content })
                        singleStreamRunner.appendFailureMessage(
                            conv, coordinator, onEvent,
                            "工具结果准备失败，无法继续回复（${t.message?.take(120) ?: t.javaClass.simpleName}）。请重试。"
                        )
                        return false
                    }
                    finalRound = singleStreamRunner.runSingleStream(
                        api, apiUrl, apiKey, currentRequest, coordinator, onEvent, contentTransform, thinkingActive,
                        onFailure = recordFailure
                    )
                    if (finalRound == null) {
                        singleStreamRunner.appendFailureMessage(
                            conv, coordinator, onEvent,
                            lastError?.let {
                                "工具已执行，但模型继续回复失败：${it.message?.take(200) ?: it.javaClass.simpleName}。请重试或检查模型配置。"
                            } ?: "工具已执行，但模型未输出回复内容。请重试。"
                        )
                        return false
                    }
                }
                if (!finalRound.hasContent || finalRound.truncated) {
                    // 上限轮后的最终回复为空 / 被截断：同样走自动续写与空回复兜底
                    val recovered = recoverNoToolRound(
                    api, apiUrl, apiKey, currentRequest, coordinator, conv, onEvent, contentTransform,
                    thinkingActive, budget.totalRounds, lastResolved, lastError, recordFailure, finalRound
                    )
                    when (recovered) {
                        is NoToolRoundResult.Completed -> Unit
                        is NoToolRoundResult.WithToolCalls -> {
                            singleStreamRunner.appendFailureMessage(
                                conv, coordinator, onEvent,
                                "工具轮次已达上限且模型仍发起工具调用，无法收尾，请重试。"
                            )
                            return false
                        }
                        is NoToolRoundResult.Failed -> return false
                    }
                }
            }
        }

        singleStreamRunner.attachThinkingUnavailableReportIfNeeded(request, coordinator, thinkingActive, onEvent)
        if (roundEffects != null) {
            onEvent(
                ChatRepository.Event.AgentRoundEffects(
                    createdPaths = roundEffects.createdFiles(),
                    changedItems = roundEffects.changedItems()
                )
            )
        }
        onEvent(ChatRepository.Event.Done)
        return true
    }

    /**
     * 工具轮失败处理：首轮失败直接报错；后续轮失败降级为关闭工具的普通回复重试一次。
     *
     * @return true = 已通过降级成功收尾；false = 无法继续（错误消息已派发）
     */
    suspend fun handleToolRoundFailure(
        api: ChatApi,
        apiUrl: String,
        apiKey: String,
        request: ChatRoundRequest,
        coordinator: StreamCoordinator,
        conv: Conversation,
        onEvent: suspend (ChatRepository.Event) -> Unit,
        contentTransform: (String) -> String,
        thinkingActive: Boolean,
        rounds: Int,
        resolved: List<Pair<ChatStreamParser.AggregatedToolCall, String>>,
        lastError: Throwable?,
        recordFailure: suspend (Throwable) -> Unit
    ): Boolean {
        if (rounds == 0) {
            singleStreamRunner.appendFailureMessage(
                conv, coordinator, onEvent,
                lastError?.let {
                    "回复失败：${it.message?.take(200) ?: it.javaClass.simpleName}。请检查网络或模型配置后重试。"
                } ?: "回复失败，请检查网络或模型配置后重试。"
            )
            return false
        }
        // 工具轮请求被接口拒绝（如 400）：把服务端响应原文落盘审计并作为错误痕迹展示，
        // 供定位根因；不吞错误——降级重试照常进行，AI 仍会向用户如实报告。
        lastError?.let { err ->
            val detail = err.message?.take(500) ?: err.javaClass.simpleName
            onEvent(ChatRepository.Event.ToolResult("工具结果回填", false, detail))
            agentStore?.appendAudit(
                com.quiddity.app.data.local.AgentAuditEntry(
                    ts = System.currentTimeMillis().toString(),
                    tool = "api_error",
                    args = detail,
                    ok = false,
                    confirmed = false
                )
            )
        }
        val fallback = toolResultBuilder.buildNoToolsFallback(
            request,
            resolved,
            lastError?.message?.take(300) ?: "模型接口拒绝继续回复"
        )
        val fallbackOk = singleStreamRunner.runSingleStream(
            api, apiUrl, apiKey, fallback, coordinator, onEvent, contentTransform, thinkingActive,
            onFailure = recordFailure
        )
        if (fallbackOk == null || coordinator.snapshot().none {
                it.role == Role.ASSISTANT && !it.isNotice && !it.isThinking && it.content.isNotBlank()
            }
        ) {
            singleStreamRunner.appendFailureMessage(
                conv, coordinator, onEvent,
                lastError?.let {
                    "工具已执行，但模型继续回复失败：${it.message?.take(200) ?: it.javaClass.simpleName}。请重试或检查模型配置。"
                } ?: "工具已执行，但模型未输出回复内容。请重试。"
            )
            return false
        }
        return true
    }

    /**
     * DeepSeek 本身有概率不配合【思考】标记（业界广泛已知，非本客户端问题）：
     * 用户开启思考但整轮未产出任何思考内容时，在首条正式回复上附报告，
     * 保证开启思考后气泡内始终有可展开的内容，而不是静默空白。
     */
    suspend fun recoverNoToolRound(
        api: ChatApi,
        apiUrl: String,
        apiKey: String,
        request: ChatRoundRequest,
        coordinator: StreamCoordinator,
        conv: Conversation,
        onEvent: suspend (ChatRepository.Event) -> Unit,
        contentTransform: (String) -> String,
        thinkingActive: Boolean,
        rounds: Int,
        resolved: List<Pair<ChatStreamParser.AggregatedToolCall, String>>,
        lastError: Throwable?,
        recordFailure: suspend (Throwable) -> Unit,
        initial: StreamRoundResult
    ): NoToolRoundResult {
        var current = request
        var round = initial
        var attempt = 0
        while (attempt < MAX_FINAL_RECOVERY_ROUNDS) {
            attempt++
            val truncated = round.truncated
            val partial = coordinator.snapshot().lastOrNull {
                it.role == Role.ASSISTANT && !it.isNotice && !it.isThinking && it.content.isNotBlank()
            }?.content.orEmpty()
            current = buildContinueRequest(
                current,
                if (truncated) partial else null,
                if (truncated) TRUNCATE_CONTINUE_NUDGE else EMPTY_REPLY_NUDGE
            )
            coordinator.setMergeWithPrevious(true)
            val retried = singleStreamRunner.runSingleStream(
                api, apiUrl, apiKey, current, coordinator, onEvent, contentTransform, thinkingActive,
                onFailure = recordFailure
            )
            if (retried == null) {
                val recovered = handleToolRoundFailure(
                    api, apiUrl, apiKey, current, coordinator, conv, onEvent, contentTransform,
                    thinkingActive, rounds, resolved, lastError, recordFailure
                )
                return if (recovered) NoToolRoundResult.Completed else NoToolRoundResult.Failed
            }
            if (retried.toolCalls.isNotEmpty()) {
                return NoToolRoundResult.WithToolCalls(retried, current)
            }
            round = retried
            if (retried.hasContent && !retried.truncated) {
                return NoToolRoundResult.Completed
            }
            // 仍被截断 → 继续续写；仍为空 → 继续重试
        }
        singleStreamRunner.appendFailureMessage(
            conv, coordinator, onEvent,
            "AI 回复不完整或为空（已自动重试 $attempt 次），请重试。"
        )
        return NoToolRoundResult.Failed
    }

    /**
     * 构造工具回填后的下一轮请求：
     * - Chat Completions：assistant 工具调用消息 + tool 角色结果消息
     * - Responses API：function_call / function_call_output input item（call_id 一一对应）
     *
     * [keepTools] 为 true 时保留 tools / tool_choice，允许模型继续调用工具；
     * 为 false 时关闭工具，强制模型输出最终答复。
     */
}