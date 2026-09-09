package com.quiddity.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.quiddity.app.data.model.AppSettings
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import com.quiddity.app.domain.ChatRecordSearch
import com.quiddity.app.ui.chat.ChatViewModel
import com.quiddity.app.ui.chat.components.GameLogBubble
import com.quiddity.app.ui.chat.components.MiniAppInviteCard
import com.quiddity.app.ui.chat.components.NoticeBubble
import com.quiddity.app.ui.chat.components.ReeditNoticeBubble
import com.quiddity.app.ui.theme.Motion

@Composable
internal fun ColumnScope.ChatMessageListArea(
    conversation: Conversation?,
    messages: List<Message>,
    isLoading: Boolean,
    isGenerating: Boolean,
    overlayReplying: Boolean,
    isGroupChat: Boolean,
    searchActive: Boolean,
    searchQuery: String,
    sceneNoticeContent: String,
    pendingReedit: PendingReedit?,
    selectedMessageIds: Set<String>,
    multiSelectMode: Boolean,
    listState: LazyListState,
    keyboardOffset: Animatable<Float, AnimationVector1D>,
    density: Density,
    settings: AppSettings,
    senderNameMap: Map<String, String>,
    senderAvatarMap: Map<String, String?>,
    highlightMessageId: String?,
    expandedActionId: String?,
    openedAtMs: Long,
    viewModel: ChatViewModel,
    onOpenMiniApp: (String) -> Unit,
    onEnterMultiSelect: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSearchResultClick: (String) -> Unit,
    onReedit: () -> Unit,
    onDismissReedit: () -> Unit,
    onStartRewrite: (String) -> Unit,
    onToggleActions: (String) -> Unit
) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    // 键盘弹起时列表区域收缩（底部让位给键盘），气泡不进入顶栏、不与输入栏脱节
                    .padding(bottom = with(density) { (-keyboardOffset.value).coerceAtLeast(0f).toDp() })
            ) {
                when {
                    searchActive && searchQuery.isNotBlank() -> {
                        val searchResults = remember(messages, searchQuery) {
                            val q = searchQuery.trim()
                            if (q.isEmpty()) emptyList()
                            else ChatRecordSearch.searchResults(
                                messages.filterNot { it.isNotice },
                                q
                            )
                        }
                        if (searchResults.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "未找到与“${searchQuery}”相关的消息",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(searchResults, key = { it.id }) { msg ->
                                    ChatSearchResultRow(
                                        message = msg,
                                        query = searchQuery,
                                        onClick = {
                                            onSearchResultClick(msg.id)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    isLoading -> Unit
                    // 当前规则：仅有 isNotice 提示气泡时也视为空对话，保留"让AI先说"按钮
                    messages.none { !it.isNotice } -> Box(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (isGroupChat) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "暂无消息\n发送后点击下方成员头像，让 TA 回复",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            EmptyChatState(
                                personaName = conversation?.persona?.name.orEmpty(),
                                onLetAiStart = { viewModel.letAiStart() },
                                isGenerating = isGenerating
                            )
                        }
                        // 场景/世界提示气泡显示在顶部，不遮挡居中的"让AI先说"按钮
                        Column(modifier = Modifier.fillMaxWidth()) {
                            messages.filter { it.isNotice && !it.miniAppId.isNullOrBlank() }
                                .forEach { invite ->
                                    MiniAppInviteCard(
                                        title = invite.miniAppTitle?.takeIf { it.isNotBlank() }
                                            ?: "小应用",
                                        content = invite.content,
                                        onClick = { invite.miniAppId?.let(onOpenMiniApp) }
                                    )
                                }
                            if (sceneNoticeContent.isNotBlank()) {
                                NoticeBubble(content = sceneNoticeContent)
                            }
                            if (!isGroupChat && isGenerating) {
                                ThinkingBubble(aiAvatarUri = conversation?.persona?.aiAvatarUri)
                            }
                        }
                    }
                    else -> {
                        val lastMsg = messages.lastOrNull { !it.isNotice }
                        // 性能：asReversed + filterNot 每次重组都会新建整份列表，流式输出时 O(n) 分配拖累动画，
                        // 这里按 messages 实例缓存，仅内容变化时重算。
                        val displayMessages = remember(messages) {
                            messages.asReversed().filterNot { it.isNotice }
                        }
                        // 当前会话是否启用思考（内部思考任意模型可用，思考期间动画气泡显示"思考中"）
                        val thinkingActive = conversation?.thinkingEnabled == true
                        // 思考气泡只在「本轮回复尚未产出任何 AI 内容」时显示：
                        // 最后一条消息是 AI 内容（无论 streaming 与否）即视为已在输出，
                        // 不再随 isStreaming 在每条消息完成瞬间闪入闪出。
                        val showThinking = !isGroupChat && (isGenerating || overlayReplying) &&
                            (lastMsg == null || lastMsg.role != Role.ASSISTANT ||
                                lastMsg.isThinking || lastMsg.isNotice)
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            // 消息从底部排列：最新一条贴近输入栏，消息少时气泡不挤在顶部
                            reverseLayout = true,
                            contentPadding = PaddingValues(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // 撤回后的「重新编辑」提示：reverseLayout 的第一项 = 最底部，
                            // 显示在最后一条消息之下（与撤回互斥：生成中不可撤回，不会与思考气泡重叠）
                            pendingReedit?.let {
                                item(key = "reedit_notice", contentType = { "reedit" }) {
                                    ReeditNoticeBubble(
                                        onReedit = onReedit,
                                        onDismiss = onDismissReedit
                                    )
                                }
                            }
                            // 思考气泡：reverseLayout 的第一项 = 最底部，紧贴输入栏（标准"正在输入"位置）
                            if (!isGroupChat) {
                                item(key = "thinking_bubble", contentType = { "thinking" }) {
                                    // 常驻 item + AnimatedVisibility：出现淡入、消失淡出，不再硬插硬删
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        AnimatedVisibility(
                                            visible = showThinking,
                                            enter = fadeIn(
                                                tween(Motion.DurationShort, easing = Motion.EasingEmphasizedDecelerate)
                                            ) + expandVertically(
                                                tween(Motion.DurationShort, easing = Motion.EasingEmphasizedDecelerate)
                                            ),
                                            exit = fadeOut(
                                                tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate)
                                            ) + shrinkVertically(
                                                tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate)
                                            )
                                        ) {
                                            ThinkingBubble(
                                                aiAvatarUri = conversation?.persona?.aiAvatarUri,
                                                thinkingLabel = if (thinkingActive) "思考中" else null
                                            )
                                        }
                                    }
                                }
                            }

                            items(
                                items = displayMessages,
                                key = { it.id },
                                contentType = { it.role.name }
                            ) { message ->
                                // 关键性能优化：key(message.id) + 独立 composable 让 ChatScreen 重组时
                                // message 内容未变的气泡完全跳过重组（流式每个 token 触发 messages 变化，
                                // 原实现会让所有气泡都重组，因为 lambda 参数每帧都是新实例）
                                val rowSelected = selectedMessageIds.contains(message.id)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (multiSelectMode && !message.isNotice && !message.isGameLog) {
                                                // 多选：点击整行勾选，选中行整行高亮
                                                Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        if (rowSelected) {
                                                            MaterialTheme.colorScheme.primaryContainer
                                                                .copy(alpha = 0.35f)
                                                        } else {
                                                            Color.Transparent
                                                        }
                                                    )
                                                    .clickable(
                                                        interactionSource = remember {
                                                            MutableInteractionSource()
                                                        },
                                                        indication = null
                                                    ) { onToggleSelection(message.id) }
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .padding(
                                            horizontal = if (multiSelectMode && !message.isNotice && !message.isGameLog) 4.dp else 0.dp
                                        )
                                ) {
                                    key(message.id) {
                                        if (message.isGameLog) {
                                            GameLogBubble(
                                                content = message.content,
                                                miniAppTitle = message.miniAppTitle
                                            )
                                        } else {
                                            MessageBubbleItem(
                                                message = message,
                                                isLastAi = message.role == Role.ASSISTANT &&
                                                    !message.isThinking &&
                                                    messages.lastOrNull { !it.isNotice && !it.isGameLog }?.id == message.id,
                                                isGroupChat = isGroupChat,
                                                inMultiSelect = multiSelectMode,
                                                isGenerating = isGenerating,
                                                userAvatarUri = settings.userAvatarUri,
                                                aiAvatarUri = conversation?.persona?.aiAvatarUri,
                                                aiName = conversation?.persona?.name,
                                                senderName = if (message.role == Role.USER) {
                                                    if (isGroupChat) "我" else null
                                                } else {
                                                    if (isGroupChat) {
                                                        message.senderId?.let {
                                                            senderNameMap[it]
                                                                ?.takeIf { name -> name.isNotBlank() }
                                                                ?: "未知成员"
                                                        }
                                                    } else null
                                                },
                                                senderAvatarUri = if (isGroupChat) {
                                                    message.senderId?.let { senderAvatarMap[it] }
                                                } else null,
                                                bracketGrayEnabled = settings.bracketGrayEnabled,
                                                markdownEnabled = settings.markdownEnabled,
                                                isSelected = selectedMessageIds.contains(message.id),
                                                isHighlighted = highlightMessageId == message.id,
                                                isWithdrawing = expandedActionId == message.id,
                                                isActionsExpanded = expandedActionId == message.id,
                                                animateEntry = message.timestamp >= openedAtMs,
                                                viewModel = viewModel,
                                                onEnterMultiSelect = onEnterMultiSelect,
                                                onToggleSelection = onToggleSelection,
                                                onToggleActions = {
                                                    if (!message.isThinking) onToggleActions(message.id)
                                                },
                                                onStartRewrite = onStartRewrite
                                            )
                                        }
                                    }
                                }

                            }

                            // 小应用邀请卡片：按时间倒序固定在列表顶部区域，点击跳回对应小应用
                            items(
                                items = messages.filter { it.isNotice && !it.miniAppId.isNullOrBlank() },
                                key = { it.id }
                            ) { invite ->
                                MiniAppInviteCard(
                                    title = invite.miniAppTitle?.takeIf { it.isNotBlank() }
                                        ?: "小应用",
                                    content = invite.content,
                                    onClick = { invite.miniAppId?.let(onOpenMiniApp) }
                                )
                            }

                            // 场景/世界提示气泡：固定在消息列表最顶部（reverseLayout 的最后一项），
                            // 长存、随场景设置实时更新
                            if (sceneNoticeContent.isNotBlank()) {
                                item(key = "scene_notice_bubble", contentType = { "notice" }) {
                                    NoticeBubble(content = sceneNoticeContent)
                                }
                            }
                        }
                    }
                }
            }

}
