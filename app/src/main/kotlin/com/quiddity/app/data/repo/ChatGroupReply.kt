package com.quiddity.app.data.repo

import com.quiddity.app.data.local.AgentPermissionControl
import com.quiddity.app.data.local.AgentStore
import com.quiddity.app.data.model.AppSettings
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.remote.ChatApi
import com.quiddity.app.data.remote.ChatMessage
import com.quiddity.app.data.remote.ResponsesInputItem
import com.quiddity.app.data.remote.ResponsesTool
import com.quiddity.app.data.remote.ToolDefinition
import com.quiddity.app.data.repo.ConversationRepository
import com.quiddity.app.data.repo.SettingsRepository
import com.quiddity.app.domain.ApiCatalogManager
import com.quiddity.app.domain.GroupReplyPlanner
import com.quiddity.app.domain.PromptBuilder
import com.quiddity.app.domain.StreamCoordinator
import com.quiddity.app.domain.agent.AgentRoundEffects
import com.quiddity.app.domain.agent.AgentToolRegistry
import com.quiddity.app.domain.agent.AgentWorkflowController
import com.quiddity.app.util.IdGenerator
import com.quiddity.app.util.QuiddityConstants

internal class GroupReplyRunner(
    private val api: ChatApi,
    private val conversationRepo: ConversationRepository,
    private val settingsRepo: SettingsRepository,
    private val apiCatalogManager: ApiCatalogManager?,
    private val coordinatorFactory: (conversationId: String, runId: String, splitEnabled: Boolean, singleMessageTokens: Int, senderId: String?, thinking: String) -> StreamCoordinator,
    private val toolResultBuilder: ToolResultBuilder,
    private val toolRoundRunner: ToolRoundRunner
) {

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
    suspend fun streamGroupMemberReply(
        member: Conversation,
        group: Conversation,
        transcript: List<Message>,
        senderId: String,
        regeneratePreviousReply: String? = null,
        onEvent: suspend (ChatRepository.Event) -> Unit
    ) {
        val settings = settingsRepo.currentSnapshot()
        // 思考消息不进入群聊转述（避免把成员思考内容发给其他成员）
        val cleanTranscript = transcript.filterNot { it.isThinking }
        // 方案六.2：群聊记录只取最近 N 条（默认 50，范围 1～200），在点击定格快照上截断。
        val trimmed = if (group.groupContextLimit > 0 && cleanTranscript.size > group.groupContextLimit) {
            cleanTranscript.takeLast(group.groupContextLimit)
        } else {
            cleanTranscript
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
            webSearchResponsesUrl = toolResultBuilder.resolveWebSearch(settings, member),
            thinkingDepth = if (member.thinkingEnabled) member.thinkingDepth else null,
            thinkingEnabled = member.thinkingEnabled,
            regeneratePreviousReply = regeneratePreviousReply
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
        val cleanEvent: suspend (ChatRepository.Event) -> Unit = { event ->
            when (event) {
                is ChatRepository.Event.NewMessage ->
                    onEvent(ChatRepository.Event.NewMessage(event.message.copy(content = sanitizer.clean(event.message.content))))
                is ChatRepository.Event.UpdateMessage ->
                    onEvent(ChatRepository.Event.UpdateMessage(event.message.copy(content = sanitizer.clean(event.message.content))))
                is ChatRepository.Event.CompleteMessage ->
                    onEvent(ChatRepository.Event.CompleteMessage(event.message.copy(content = sanitizer.clean(event.message.content))))
                is ChatRepository.Event.Done -> onEvent(event)
                is ChatRepository.Event.Error -> onEvent(event)
                is ChatRepository.Event.Notice -> onEvent(event)
                is ChatRepository.Event.ToolUse -> onEvent(event)
                is ChatRepository.Event.ToolResult -> onEvent(event)
                is ChatRepository.Event.ToolConfirmBatch -> onEvent(event)
                is ChatRepository.Event.AgentRoundEffects -> onEvent(event)
                is ChatRepository.Event.Truncated -> onEvent(event)
            }
        }
        plan.fold(
            onSuccess = { p ->
                val coordinator = coordinatorFactory(
                    group.id,
                    IdGenerator.newUuid(),
                    settings.multilineAutoSplit,
                    p.singleMessageTokens,
                    p.senderId,
                    ""
                )
                val roundRequest = p.responsesRequest
                    ?.let { ChatRoundRequest.Responses(it, p.responsesApiUrl ?: p.apiUrl) }
                    ?: ChatRoundRequest.Completions(p.request, p.apiUrl)
                toolRoundRunner.runWithToolRound(
                    api, p.apiKey, roundRequest, coordinator, group, cleanEvent,
                    thinkingActive = member.thinkingEnabled,
                    roundEffects = null,
                    contentTransform = { prefixStripper.accept(it) }
                )
            },
            onFailure = { toolResultBuilder.emitError(onEvent, it, "") }
        )
    }

    /**
     * 群聊小本本压缩接口（4.1，2.0.0 实现）。
     *
     * 规划：达到条数阈值（[QuiddityConstants.GROUP_MEMORY_THRESHOLD]）后对群聊转述执行压缩，
     * 复用 6.5.2 两段输出格式：摘要 → groupMemory，索引 → 小抄「群聊记忆」一行；
     * 压缩调用 token 计入群聊开销。
     */
    fun resolveSenderNames(transcript: List<Message>): Map<String, String> {
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
    fun resolveMemberTier(member: Conversation, settings: AppSettings): ApiCatalogManager.ModelTier {
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
}
