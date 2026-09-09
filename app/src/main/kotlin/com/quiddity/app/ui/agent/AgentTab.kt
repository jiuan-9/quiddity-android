package com.quiddity.app.ui.agent

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.ui.home.ChatListPage

/**
 * Agent 模式第三 Tab：会话列表页。
 *
 * 复用 Solo/Group 的同一列表组件（[ChatListPage]），仅渲染 Agent 类型会话
 * （上游已按 ConversationType.AGENT 过滤）；空态文案独立。无状态卡片/面板。
 */
@Composable
fun AgentTab(
    conversations: List<Conversation>,
    listState: LazyListState,
    isMultiSelect: Boolean,
    selectedIds: Set<String>,
    toggleSelection: (String) -> Unit,
    syncMultiSelect: (Boolean, Set<String>) -> Unit,
    onOpenConversation: (String) -> Unit,
    hasListWallpaper: Boolean,
    newConversationId: String? = null
) {
    ChatListPage(
        conversations = conversations,
        listState = listState,
        isMultiSelect = isMultiSelect,
        selectedIds = selectedIds,
        isGroup = false,
        emptyText = "还没有 Agent 会话\n点击右上角新建",
        userAvatarUri = null,
        memberResolver = { emptyList() },
        toggleSelection = toggleSelection,
        syncMultiSelect = syncMultiSelect,
        onOpenConversation = onOpenConversation,
        hasListWallpaper = hasListWallpaper,
        newConversationId = newConversationId
    )
}
