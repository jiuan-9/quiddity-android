package com.quiddity.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.repo.ConversationRepository
import com.quiddity.app.domain.GlobalChatSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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


// 当前规则：会话按更新时间排序；createConversation 只添加不跳转；支持多选删除与重命名。
class HomeViewModel(
    private val conversationRepository: ConversationRepository
) : ViewModel() {

    // 加载状态：首次 sortedConversations 发射前为 true。
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** 最近一次新建会话 id（用于列表卡片「从上到下淡入」动画，短暂保留后清除）。 */
    private val _newConversationId = MutableStateFlow<String?>(null)
    val newConversationId: StateFlow<String?> = _newConversationId.asStateFlow()

    // ===== 三条开发规范（位于文件中间位置） =====
    // 1. 问题修复规范：所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
    //    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
    // 2. 代码注释规范：文件内仅允许保留两类注释——当前规则说明注释与模块划分注释（// ===== xxx =====）。
    //    除此之外，禁止出现任何形式的代码注释（包括单行 / 多行 / 临时调试注释）。
    // 3. 构建交付要求：完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK。
    //    APK 需满足：签名有效且符合发布标准、包含完整功能模块、经过基础性能与兼容性测试。

    val conversations: StateFlow<List<com.quiddity.app.data.model.Conversation>> =
        conversationRepository.sortedConversations
            .onEach { _isLoading.value = false }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = conversationRepository.conversations.value
            )

    // ===== 私聊 / 群聊分节（方案十四：双列表各自独立） =====
    val soloConversations: StateFlow<List<Conversation>> = conversations
        .map { list -> list.filter { it.type == ConversationType.SOLO } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val groupConversations: StateFlow<List<Conversation>> = conversations
        .map { list -> list.filter { it.type == ConversationType.GROUP } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val agentConversations: StateFlow<List<Conversation>> = conversations
        .map { list -> list.filter { it.type == ConversationType.AGENT } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 群聊成员会话解析（id → Conversation），供列表头像拼合与成员校验。 */
    fun memberConversations(ids: List<String>): List<Conversation> =
        ids.mapNotNull { id -> conversationRepository.getConversation(id) }

    fun createConversation() {
        viewModelScope.launch {
            val conv = conversationRepository.createConversation()
            markNewConversation(conv.id)
        }
    }

    /**
     * 创建群聊（需求：与创建私聊一致，只创建列表项，不直接进入；
     * 成员随后在会话设置-成员管理中添加）。
     */
    fun createGroupConversation() {
        viewModelScope.launch {
            val conv = conversationRepository.createGroupConversation(emptyList(), null)
            markNewConversation(conv.id)
        }
    }

    /** 创建 Agent 会话（固定标题 Agent，不直接进入）。 */
    fun createAgentConversation() {
        viewModelScope.launch {
            val conv = conversationRepository.createAgentConversation()
            markNewConversation(conv.id)
        }
    }

    private fun markNewConversation(id: String) {
        _newConversationId.value = id
        viewModelScope.launch {
            kotlinx.coroutines.delay(2_000)
            if (_newConversationId.value == id) _newConversationId.value = null
        }
    }

    /**
     * 批量删除多个会话（多选模式触发）。
     *
     * - 删除时同时清理 messages_<id>.json 文件 + 内存缓存 + Flow（store 内部实现）。
     * - 私聊删除保护（方案十.6）：被群聊引用的私聊先回调确认，确认后走
     *   [confirmDeleteReferencedConversations] 从群聊中移除成员。
     */
    fun deleteConversations(
        convIds: List<String>,
        onReferencedByGroups: (List<String>) -> Unit = {}
    ) {
        if (convIds.isEmpty()) return
        val referenced = convIds.filter {
            conversationRepository.groupsReferencing(it).isNotEmpty()
        }
        if (referenced.isNotEmpty()) {
            onReferencedByGroups(referenced)
            return
        }
        performDelete(convIds)
    }

    /** 用户确认后删除被群聊引用的私聊，并把这些成员从群聊中移除（历史保留）。 */
    fun confirmDeleteReferencedConversations(convIds: List<String>) {
        viewModelScope.launch {
            conversationRepository.deleteConversations(convIds)
            convIds.forEach { conversationRepository.removeMemberFromGroups(it) }
        }
    }

    private fun performDelete(convIds: List<String>) {
        viewModelScope.launch {
            conversationRepository.deleteConversations(convIds)
        }
    }

    fun renameConversation(convId: String, newTitle: String) {
        viewModelScope.launch {
            conversationRepository.renameConversation(convId, newTitle)
        }
    }

    // ===== 全局消息搜索 =====
    // 首次搜索时懒加载全量消息索引并缓存；会话列表任何变化（新增/删除/消息导致
    // updatedAt 变化）都会把索引标记为过期，下次搜索自动重建，保证新消息可搜。
    private val _messageIndex = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    private var messageIndexLoaded = false
    private var indexDirty = true

    init {
        viewModelScope.launch {
            conversationRepository.conversations.collect {
                indexDirty = true
            }
        }
    }

    /** 确保消息索引已加载（首次搜索时触发，只加载一次）。 */
    suspend fun ensureMessageIndexLoaded() {
        if (messageIndexLoaded && !indexDirty) return
        _messageIndex.value = conversationRepository.exportAllMessages()
        messageIndexLoaded = true
        indexDirty = false
    }

    /**
     * 按 [query] 跨会话搜索消息，返回按时间倒序的命中列表。
     * 调用前需先 [ensureMessageIndexLoaded]。
     */
    fun searchMessages(query: String): List<GlobalChatSearch.Hit> {
        val index = _messageIndex.value
        if (query.isBlank() || index.isEmpty()) return emptyList()
        val titles = conversations.value.associate { it.id to it.title }
        return GlobalChatSearch.searchAll(index, titles, query)
    }
}

class HomeViewModelFactory(
    private val repo: ConversationRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(repo) as T
    }
}
