package com.quiddity.app.data.repo

import com.quiddity.app.data.local.ConversationStore
import com.quiddity.app.data.model.Character
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.data.model.ImportMode
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Persona
import com.quiddity.app.data.model.UserPersona
import com.quiddity.app.domain.ApiCatalogManager
import com.quiddity.app.domain.GroupChatRules
import com.quiddity.app.util.IdGenerator
import com.quiddity.app.util.QuiddityConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

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
 * 会话仓库：管理会话列表与消息。
 *
 * 依赖注入：[settingsRepository] / [apiCatalogManager] 为可选依赖，
 * 测试环境可不注入（退化为 [QuiddityConstants.DEFAULT_CONTEXT_LIMIT]）。
 */
class ConversationRepository(
    private val store: ConversationStore,
    private val settingsRepository: SettingsRepository? = null,
    private val apiCatalogManager: ApiCatalogManager? = null,
    private val characterRepository: CharacterRepository? = null
) {

    companion object {
        /** Fixed title for Agent conversations. */
        private const val AGENT_DEFAULT_TITLE = "Agent"
    }

    val conversations: StateFlow<List<Conversation>> = store.conversations

    val sortedConversations: Flow<List<Conversation>> = store.conversations.map { list ->
        list.sortedWith(
            compareByDescending<Conversation> { it.pinned }
                .thenByDescending { it.updatedAt }
        )
    }

    suspend fun observeMessages(convId: String): StateFlow<List<Message>> = store.observeMessages(convId)

    suspend fun loadAll() = store.loadAll()

    /**
     * 启动时数据迁移：清理历史消息 id 重复。
     * 详见 [com.quiddity.app.data.local.ConversationStore.migrateDeduplicateMessageIds]。
     */
    suspend fun migrateDeduplicateMessageIds() = store.migrateDeduplicateMessageIds()

    /**
     * 创建新会话。
     *
     * - 默认 AI 人设：所有字段留空，输入框显示灰色占位提示。
     *   身份字段为空时，[PromptBuilder] 会回退使用
     *   [QuiddityConstants.DEFAULT_AI_IDENTITY] 作为系统提示词中的默认身份。
     * - 记忆轮数初始化：按当前激活的模型分级设置默认值
     *   （FULL=80 / ADVANCED=40 / BASIC=12）。
     *   解析失败（无 API 配置 / 依赖未注入）时退化为 DEFAULT_CONTEXT_LIMIT，保持兼容。
     */
    suspend fun createConversation(): Conversation {
        val now = System.currentTimeMillis()
        val title = settingsRepository?.nextSoloTitle() ?: QuiddityConstants.DEFAULT_CONVERSATION_TITLE
        val conv = Conversation(
            id = IdGenerator.newId(IdGenerator.Prefix.CONVERSATION),
            title = title,
            createdAt = now,
            updatedAt = now,
            // AI 人设：所有字段全部留空，输入框显示灰色占位提示引导用户设定。
            // 身份字段为空时，[PromptBuilder] 会回退使用 DEFAULT_AI_IDENTITY（"用户的AI助手"）。
            persona = Persona(
                name = "",
                persona = "",
                desired = "",
                character = "",
                appearance = "",
                worldBackground = ""
            ),
            // 用户人设：空值，输入框显示灰色占位提示（如"如 小明"）
            userPersona = com.quiddity.app.data.model.UserPersona.Empty,
            // 按当前激活模型分级初始化记忆轮数
            contextLimit = resolveDefaultContextLimit()
        ).let {
            // 压缩轮数默认与上下文记忆轮数一致
            it.copy(memoryBankRounds = it.contextLimit)
        }
        store.createConversation(conv)
        return conv
    }

    /**
     * Create an Agent-mode conversation (type = AGENT).
     * Reuses the shared Conversation/Message storage; fixed title "Agent".
     */
    suspend fun createAgentConversation(): Conversation {
        val now = System.currentTimeMillis()
        val conv = Conversation(
            id = IdGenerator.newId(IdGenerator.Prefix.CONVERSATION),
            title = AGENT_DEFAULT_TITLE,
            createdAt = now,
            updatedAt = now,
            persona = com.quiddity.app.data.model.Persona.Empty,
            userPersona = com.quiddity.app.data.model.UserPersona.Empty,
            type = com.quiddity.app.data.model.ConversationType.AGENT,
            contextLimit = resolveDefaultContextLimit()
        ).let {
            it.copy(memoryBankRounds = it.contextLimit)
        }
        store.createConversation(conv)
        return conv
    }

    /** 创建指定内容的会话（小应用邀请角色等场景使用）。 */
    suspend fun createConversation(conv: Conversation) {
        store.createConversation(syncCharacterFor(conv))
    }

    /**
     * 创建群聊会话（方案二.6）。
     *
     * @param memberIds 成员私聊会话 id（1～3 个，调用方已校验）
     * @param title 群名；空串时自动编号「新群聊 N」
     */
    suspend fun createGroupConversation(
        memberIds: List<String>,
        title: String? = null
    ): Conversation {
        val now = System.currentTimeMillis()
        val resolvedTitle = title?.takeIf { it.isNotBlank() }
            ?: settingsRepository?.nextGroupTitle()
            ?: QuiddityConstants.GROUP_DEFAULT_TITLE_PREFIX
        val conv = GroupChatRules.buildGroupConversation(
            id = IdGenerator.newId(IdGenerator.Prefix.CONVERSATION),
            title = resolvedTitle,
            memberIds = memberIds,
            createdAt = now,
            updatedAt = now
        )
        store.createConversation(conv)
        return conv
    }

    /** 被 [memberId] 引用的群聊列表（私聊删除保护用，方案十.6）。 */
    fun groupsReferencing(memberId: String): List<Conversation> =
        GroupChatRules.groupsReferencing(store.conversations.value, memberId)

    /** 从所有群聊中移除成员（私聊删除后调用；历史消息气泡保留，方案十.4）。 */
    suspend fun removeMemberFromGroups(memberId: String) {
        GroupChatRules.groupsWithoutMember(store.conversations.value, memberId)
            .filter { it.type == ConversationType.GROUP }
            .forEach { group ->
                updateConversation(group)
            }
    }

    /**
     * 成员入群校验（方案七.4：用户名、AI 名、API 测试通过）。
     *
     * @return 成功返回消息文本；失败返回异常（含失败原因）
     */
    suspend fun validateGroupMember(member: Conversation): Result<String> {
        GroupChatRules.validationFailureReason(member)?.let { reason ->
            return Result.failure(IllegalStateException(reason))
        }
        val settings = settingsRepository?.currentSnapshot()
            ?: return Result.failure(IllegalStateException("设置未加载"))
        val manager = apiCatalogManager
            ?: return Result.failure(IllegalStateException("API 名册未加载"))
        val access = ApiAccess.resolve(settings, member)
        return when (access) {
            is ApiAccess.Failure -> Result.failure(access.toChatException())
            is ApiAccess.Resolved -> manager.testConnection(access.apiUrl, access.apiKey, access.model)
        }
    }

    /**
     * 解析当前激活模型分级对应的默认上下文记忆轮数。
     *
     * 解析链路：settingsRepository → activeCatalogId → catalog 条目 → apiModel + providerId
     * → apiCatalogManager.getModelTier → defaultContextLimitForTier。
     *
     * 任一环节失败（依赖未注入 / 无 API 配置）时退化为 [QuiddityConstants.DEFAULT_CONTEXT_LIMIT]，
     * 保持向后兼容。
     */
    private fun resolveDefaultContextLimit(): Int {
        val settings = settingsRepository?.currentSnapshot() ?: return QuiddityConstants.DEFAULT_CONTEXT_LIMIT
        val catalogManager = apiCatalogManager ?: return QuiddityConstants.DEFAULT_CONTEXT_LIMIT

        // 优先使用激活的 catalog 条目，否则取列表第一项作为兜底
        val entry = settings.catalog.firstOrNull { it.id == settings.activeCatalogId }
            ?: settings.catalog.firstOrNull()
            ?: return QuiddityConstants.DEFAULT_CONTEXT_LIMIT

        val tier = catalogManager.getModelTier(entry.apiModel, entry.providerId)
        return catalogManager.defaultContextLimitForTier(tier)
    }

    /** @return 是否写盘成功（失败仅记录日志，调用方按需提示） */
    suspend fun updateConversation(conv: Conversation): Boolean {
        val resolved = syncCharacterFor(conv)
        return store.updateConversation(resolved.copy(updatedAt = System.currentTimeMillis()))
    }

    /** 删除单条消息（重复消息去重用）。 */
    suspend fun deleteMessage(convId: String, messageId: String): Boolean =
        store.deleteMessage(convId, messageId)

    /** @return 是否写盘成功 */
    suspend fun deleteConversation(convId: String): Boolean {
        val deleted = store.conversations.value.filter { it.id == convId }
        val ok = store.deleteConversation(convId)
        if (ok) cascadeDeleteCharacters(deleted)
        return ok
    }

    /**
     * 批量删除多个会话（多选用）。
     *
     * 实现：由 [ConversationStore.deleteConversations] 单次过滤 + 单次写盘完成，
     * 避免 N 个会话触发 N 次整文件重写。
     *
     * @param convIds 要删除的会话 ID 列表
     */
    /** @return 是否写盘成功 */
    suspend fun deleteConversations(convIds: List<String>): Boolean {
        if (convIds.isEmpty()) return true
        val target = convIds.toSet()
        val deleted = store.conversations.value.filter { it.id in target }
        val ok = store.deleteConversations(convIds)
        if (ok) cascadeDeleteCharacters(deleted)
        return ok
    }

    /**
     * 私聊人设 -> 角色库唯一角色卡（uid）同步。
     *
     * - 每个私聊（SOLO）会话只要存在人设内容（AI 人设 / 用户人设 / 固定记忆任一非空），
     *   就在角色库中维护一张唯一角色卡，id 即 [Conversation.characterId]；
     * - 群聊、Agent、小应用统一通过 characterId 直接引用这张卡，不再各自内嵌副本；
     * - 人设内容未变化时不写盘，避免无谓重写。
     */
    suspend fun syncCharacterFor(conv: Conversation): Conversation {
        if (conv.type != ConversationType.SOLO || !hasPersonaContent(conv)) return conv
        val repo = characterRepository ?: return conv
        val characterId = conv.characterId?.takeIf { it.isNotBlank() }
            ?: IdGenerator.newId(IdGenerator.Prefix.CHARACTER)
        val target = Character(
            id = characterId,
            persona = conv.persona,
            userPersona = conv.userPersona,
            memory = conv.memory,
            aiAvatarUri = conv.persona.aiAvatarUri
        )
        val existing = repo.getCharacter(characterId)
        if (existing == target && conv.characterId == characterId) return conv
        repo.saveCharacter(target)
        return if (conv.characterId == characterId) conv else conv.copy(characterId = characterId)
    }

    /** 启动迁移：为已有私聊补齐角色卡引用（历史数据一次性回填）。 */
    suspend fun syncAllSoloCharacters() {
        store.conversations.value
            .filter { it.type == ConversationType.SOLO }
            .forEach { conv ->
                val resolved = syncCharacterFor(conv)
                if (resolved.characterId != conv.characterId) {
                    store.updateConversation(resolved)
                }
            }
    }

    private fun hasPersonaContent(conv: Conversation): Boolean {
        val p = conv.persona
        val u = conv.userPersona
        return p.name.isNotBlank() || p.desired.isNotBlank() || p.persona.isNotBlank() ||
            p.character.isNotBlank() || p.appearance.isNotBlank() || p.worldBackground.isNotBlank() ||
            u.name.isNotBlank() || u.identity.isNotBlank() || u.gender.isNotBlank() ||
            u.age.isNotBlank() || u.appearance.isNotBlank() || conv.memory.isNotBlank()
    }

    /**
     * 删除级联（角色删除 / 会话删除）：
     *
     * - 角色卡的「所有者」是创建它的私聊（SOLO）会话：仅当所有者被删除时，
     *   才从角色库移除该卡，并把仍引用它的其他会话（如 Agent 会话）重置为
     *   默认人设（无人设），避免悬空引用影响角色卡数据；
     * - 仅删除消费方（如 Agent 会话）时保留角色卡，不影响私聊所有者的引用。
     */
    private suspend fun cascadeDeleteCharacters(deleted: List<Conversation>) {
        val removedIds = deleted.mapNotNull { it.characterId }.toSet()
        if (removedIds.isEmpty()) return
        val repo = characterRepository ?: return
        val remaining = store.conversations.value
        removedIds.forEach { id ->
            val ownerDeleted = deleted.any { it.type == ConversationType.SOLO && it.characterId == id }
            if (ownerDeleted) {
                repo.deleteCharacter(id)
                remaining
                    .filter { it.characterId == id }
                    .forEach { conv ->
                        store.updateConversation(
                            conv.copy(
                                characterId = null,
                                persona = Persona.Empty,
                                userPersona = UserPersona.Empty,
                                memory = ""
                            )
                        )
                    }
            }
        }
    }

    suspend fun appendMessage(message: Message): Boolean = store.appendMessage(message)

    suspend fun updateMessage(message: Message): Boolean = store.updateMessage(message)

    suspend fun replaceMessages(convId: String, messages: List<Message>): Boolean =
        store.replaceMessages(convId, messages)

    fun getConversation(convId: String): Conversation? =
        store.conversations.value.firstOrNull { it.id == convId }

    suspend fun renameConversation(convId: String, newTitle: String) {
        getConversation(convId)?.let {
            updateConversation(it.copy(title = newTitle))
        }
    }

    /** 用于数据导出。 */
    suspend fun exportAllConversations(): List<Conversation> = store.conversations.value

    suspend fun exportAllMessages(): Map<String, List<Message>> = store.exportAll()

    /** @return 是否全部写盘成功 */
    suspend fun importAll(
        conversations: List<Conversation>,
        messages: Map<String, List<Message>>
    ): Boolean = store.importAll(conversations, messages)

    /**
     * 替换式导入：删除全部现有会话与消息，写入导入数据。
     *
     * 与 [importAll]（合并模式）互补：用户选择"替换现有数据"时调用。
     */
    /** 替换式导入；写盘失败时回滚并抛异常，调用方负责提示。 */
    suspend fun replaceAll(
        conversations: List<Conversation>,
        messages: Map<String, List<Message>>
    ) = store.replaceAll(conversations, messages)

    /**
     * v2 快照导入（4.1：replaceAll 扩展支持角色库，或新增 importV2Snapshot）。
     *
     * 写盘顺序（3.4）：角色库 → 会话 → 消息；群聊（[groupChats]）1.3.0 不导入，
     * 1.5.0 起随调用方传入的 conversations/messages 一并恢复（方案十七.2）。
     *
     * @param characters 角色库主档
     * @param conversations 私聊会话
     * @param messages 会话消息
     * @param mode 导入模式（替换 / 合并 / 仅导入角色库）
     */
    /** 按模式导入 v2 快照；失败（写盘失败 / 回滚）时抛异常。 */
    suspend fun importV2Snapshot(
        characters: List<Character>,
        conversations: List<Conversation>,
        messages: Map<String, List<Message>>,
        mode: ImportMode
    ) {
        when (mode) {
            ImportMode.REPLACE -> {
                // write conversations/messages first (store.replaceAll has .bak backup+rollback), then characters;
                // if conversation write fails, characters untouched -> both stay consistent.
                store.replaceAll(conversations, messages)
                characterRepository?.replaceCharacters(characters)
                syncAllSoloCharacters()
            }
            ImportMode.MERGE -> {
                characterRepository?.mergeCharacters(characters)
                if (!store.importAll(conversations, messages)) {
                    throw IllegalStateException("合并导入写盘失败")
                }
                syncAllSoloCharacters()
            }
            ImportMode.CHARACTERS_ONLY -> {
                // 只登记 characters，其余不动（3.1）
                characterRepository?.mergeCharacters(characters)
            }
        }
    }

    /**
     * 当前是否有会话数据（用于导入时判断是否需要弹窗让用户抉择）。
     */
    fun hasConversations(): Boolean = store.conversations.value.isNotEmpty()
}
