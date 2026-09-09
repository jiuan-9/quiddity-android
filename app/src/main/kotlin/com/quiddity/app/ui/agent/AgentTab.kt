package com.quiddity.app.ui.agent

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.ui.home.ChatListPage

/*
 * ============================================================================
 * 开发规范 (Development Specifications)
 * ============================================================================
 *
 * 1. 问题修复规范
 *    所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
 *    修复内容需完全融入现有代码架构，保证代码逻辑的连贯性、可维护性和可扩展性。
 *
 * 2. 代码注释规范
 *    文件内仅允许保留两类注释：
 *    - 当前规则说明注释（即本注释块）；
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
