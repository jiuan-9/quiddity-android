package com.quiddity.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.repo.ConversationRepository
import com.quiddity.app.domain.GlobalChatSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    fun createConversation() {
        viewModelScope.launch {
            conversationRepository.createConversation()
        }
    }

    /**
     * 批量删除多个会话（多选模式触发）。
     *
     * - 删除时同时清理 messages_<id>.json 文件 + 内存缓存 + Flow（store 内部实现）。
     */
    fun deleteConversations(convIds: List<String>) {
        if (convIds.isEmpty()) return
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
    // 首次搜索时懒加载全量消息索引并缓存；会话/消息在本次会话内变化不主动刷新，
    // 数据量小、索引一次性构建，冷启动后首次输入略慢属预期。
    private val _messageIndex = MutableStateFlow<Map<String, List<Message>>>(emptyMap())
    private var messageIndexLoaded = false

    /** 确保消息索引已加载（首次搜索时触发，只加载一次）。 */
    suspend fun ensureMessageIndexLoaded() {
        if (messageIndexLoaded) return
        _messageIndex.value = conversationRepository.exportAllMessages()
        messageIndexLoaded = true
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
