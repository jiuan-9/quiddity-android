package com.quiddity.app.data.repo

import com.quiddity.app.data.local.ConversationStore
import com.quiddity.app.data.model.Character
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.data.model.ImportMode
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Persona
import com.quiddity.app.domain.ApiCatalogManager
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
        val conv = Conversation(
            id = IdGenerator.newId(IdGenerator.Prefix.CONVERSATION),
            title = QuiddityConstants.DEFAULT_CONVERSATION_TITLE,
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

    suspend fun updateConversation(conv: Conversation) {
        store.updateConversation(conv.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteConversation(convId: String) = store.deleteConversation(convId)

    /**
     * 批量删除多个会话（多选用）。
     *
     * 实现：由 [ConversationStore.deleteConversations] 单次过滤 + 单次写盘完成，
     * 避免 N 个会话触发 N 次整文件重写。
     *
     * @param convIds 要删除的会话 ID 列表
     */
    suspend fun deleteConversations(convIds: List<String>) {
        if (convIds.isEmpty()) return
        store.deleteConversations(convIds)
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

    suspend fun togglePin(convId: String) {
        getConversation(convId)?.let {
            updateConversation(it.copy(pinned = !it.pinned))
        }
    }

    /** 用于数据导出。 */
    suspend fun exportAllConversations(): List<Conversation> = store.conversations.value

    suspend fun exportAllMessages(): Map<String, List<Message>> = store.exportAll()

    suspend fun importAll(
        conversations: List<Conversation>,
        messages: Map<String, List<Message>>
    ) = store.importAll(conversations, messages)

    /**
     * 替换式导入：删除全部现有会话与消息，写入导入数据。
     *
     * 与 [importAll]（合并模式）互补：用户选择"替换现有数据"时调用。
     */
    suspend fun replaceAll(
        conversations: List<Conversation>,
        messages: Map<String, List<Message>>
    ) = store.replaceAll(conversations, messages)

    /**
     * v2 快照导入（4.1：replaceAll 扩展支持角色库，或新增 importV2Snapshot）。
     *
     * 写盘顺序（3.4）：角色库 → 会话 → 消息；群聊（[groupChats]）1.3.0 不导入，
     * 由 DataPorter 在解析阶段计入跳过清单。
     *
     * @param characters 角色库主档
     * @param conversations 私聊会话
     * @param messages 会话消息
     * @param mode 导入模式（替换 / 合并 / 仅导入角色库）
     */
    suspend fun importV2Snapshot(
        characters: List<Character>,
        conversations: List<Conversation>,
        messages: Map<String, List<Message>>,
        mode: ImportMode
    ) {
        when (mode) {
            ImportMode.REPLACE -> {
                characterRepository?.replaceCharacters(characters)
                store.replaceAll(conversations, messages)
            }
            ImportMode.MERGE -> {
                characterRepository?.mergeCharacters(characters)
                store.importAll(conversations, messages)
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
