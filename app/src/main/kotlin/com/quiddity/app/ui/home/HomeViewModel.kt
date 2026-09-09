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

