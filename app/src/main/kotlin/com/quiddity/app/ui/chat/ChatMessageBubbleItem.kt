package com.quiddity.app.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import com.quiddity.app.ui.chat.components.MessageBubble

@Composable
internal fun MessageBubbleItem(
    message: Message,
    isLastAi: Boolean,
    isGroupChat: Boolean,
    inMultiSelect: Boolean,
    isGenerating: Boolean,
    userAvatarUri: String?,
    aiAvatarUri: String?,
    aiName: String?,
    senderName: String?,
    senderAvatarUri: String?,
    bracketGrayEnabled: Boolean,
    markdownEnabled: Boolean,
    isSelected: Boolean,
    isHighlighted: Boolean,
    isWithdrawing: Boolean,
    isActionsExpanded: Boolean,
    animateEntry: Boolean,
    viewModel: ChatViewModel,
    onEnterMultiSelect: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onToggleActions: () -> Unit,
    onStartRewrite: (String) -> Unit
) {
    val mid = message.id
    val isUserMsg = message.role == Role.USER

    // ===== 缓存 viewModel 直接回调（稳定来源：viewModel）=====
    val regen = remember(viewModel, mid, isGroupChat) {
        if (isGroupChat) {
            { viewModel.regenerateGroupMemberMessage(mid) }
        } else {
            { viewModel.regenerate() }
        }
    }
    // 继续说仅私聊（继续最后一条 AI 回复）；群聊继续接话靠点头像点名，不提供继续说
    val cont = remember(viewModel) { { viewModel.continueGeneration() } }
    val withdraw = remember(viewModel, mid) {
        {
            viewModel.withdrawMessage(mid)
            onToggleActions()
        }
    }

    // ===== 缓存依赖状态的回调（key 用稳定的状态枚举）=====
    // lambda body 用 { ... } 包裹成 () -> Unit 表达式，避免 Kotlin 把单语句函数调用当成 Unit 返回值
    // （推断出 Unit 而非 () -> Unit，类型不匹配）。
    // 重说：私聊=重说 AI 这一整轮；群聊=仅限最后一条成员消息（方案：群聊重说仅末条）
    val onRegenFinal: (() -> Unit)? = if (!inMultiSelect && !isGenerating && !isUserMsg && isLastAi) {
        remember<() -> Unit>(inMultiSelect, isLastAi, isGenerating, regen) { { regen() } }
    } else null
    val onContFinal: (() -> Unit)? = if (!inMultiSelect && isLastAi && !isGenerating && !isGroupChat) {
        remember<() -> Unit>(inMultiSelect, isLastAi, isGenerating, cont) { { cont() } }
    } else null
    val onWithdrawFinal: (() -> Unit)? = if (!inMultiSelect && isUserMsg && !isGenerating) {
        remember<() -> Unit>(inMultiSelect, isUserMsg, isGenerating, withdraw) { { withdraw() } }
    } else null
    val onBubbleClickFinal: (() -> Unit)? = if (!inMultiSelect && isUserMsg && !isGenerating) {
        remember<() -> Unit>(inMultiSelect, isUserMsg, isGenerating) { { onToggleActions() } }
    } else null
    val onLongClickFinal: (() -> Unit)? = if (!inMultiSelect && !isGenerating) {
        remember<() -> Unit>(inMultiSelect, isGenerating) { { onEnterMultiSelect(mid) } }
    } else null
    // AI 消息操作面板：展开时提供改写/删除；重说/继续说按各自可用性显示。
    val onRewriteFinal: (() -> Unit)? = if (!inMultiSelect && !isUserMsg && !isGenerating && isActionsExpanded) {
        remember<() -> Unit>(inMultiSelect, isUserMsg, isGenerating, isActionsExpanded) {
            { onStartRewrite(mid) }
        }
    } else null
    val onSelectFinal: (() -> Unit)? = if (inMultiSelect) {
        remember<() -> Unit>(inMultiSelect) { { onToggleSelection(mid) } }
    } else null

    MessageBubble(
        message = message,
        isGroupChat = isGroupChat,
        userAvatarUri = userAvatarUri,
        aiAvatarUri = aiAvatarUri,
        aiName = aiName,
        senderName = senderName,
        senderAvatarUri = senderAvatarUri,
        bracketGrayEnabled = bracketGrayEnabled,
        markdownEnabled = markdownEnabled,
        isLastAiMessage = isLastAi,
        onRegenerate = onRegenFinal,
        onContinue = onContFinal,
        onWithdraw = onWithdrawFinal,
        isWithdrawing = isWithdrawing,
        onBubbleClick = onBubbleClickFinal,
        onLongClick = onLongClickFinal,
        isActionsExpanded = isActionsExpanded,
        animateEntry = animateEntry,
        onToggleActions = if (!inMultiSelect && !isUserMsg && !isGenerating) {
            remember<() -> Unit>(inMultiSelect, isUserMsg, isGenerating) { { onToggleActions() } }
        } else null,
        onRewrite = onRewriteFinal,
        isHighlighted = isHighlighted,
        multiSelectMode = inMultiSelect,
        isSelected = isSelected,
        onSelectToggle = onSelectFinal
    )
}
