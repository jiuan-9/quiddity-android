package com.quiddity.app.ui.chat

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.quiddity.app.data.model.AppSettings
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import com.quiddity.app.domain.ChatRecordSearch
import com.quiddity.app.domain.GroupReplyQueue
import com.quiddity.app.ui.chat.ChatViewModel
import com.quiddity.app.ui.chat.components.ChatInputBar
import com.quiddity.app.ui.chat.components.GameLogBubble
import com.quiddity.app.ui.chat.components.GroupAvatarBar
import com.quiddity.app.ui.chat.components.MiniAppInviteCard
import com.quiddity.app.ui.chat.components.NoticeBubble
import com.quiddity.app.ui.chat.components.ReeditNoticeBubble
import com.quiddity.app.ui.theme.Motion
import kotlinx.coroutines.launch

@Composable
internal fun ChatWallpaperLayer(wallpaperUri: String?, wallpaperScrim: Color) {
        if (wallpaperUri != null) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(wallpaperUri)
                    .size(1080)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(wallpaperScrim)
            )
        }
}

@Composable
internal fun ChatTopBarArea(
    conversation: Conversation?,
    multiSelectMode: Boolean,
    selectedMessageIds: Set<String>,
    allSelectableIds: Set<String>,
    searchActive: Boolean,
    searchQuery: String,
    isGroupChat: Boolean,
    groupEmpty: Boolean,
    onExitMultiSelect: () -> Unit,
    onSelectAll: () -> Unit,
    onBack: () -> Unit,
    onOpenMenu: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchClose: () -> Unit,
    onOpenHamburger: () -> Unit
) {
            // ===== 顶部栏（多选模式下切换为多选操作栏） =====
            if (multiSelectMode) {
                MultiSelectTopBar(
                    selectedCount = selectedMessageIds.size,
                    allSelected = selectedMessageIds == allSelectableIds && allSelectableIds.isNotEmpty(),
                    onClose = onExitMultiSelect,
                    onSelectAll = onSelectAll
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .height(56.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = conversation?.persona?.name?.takeIf { it.isNotBlank() }
                            ?: conversation?.title
                            ?: "新会话",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        textAlign = TextAlign.Center
                    )
                    // 头部不显示放大镜（私聊/群聊均无），查找聊天记录入口统一在会话设置内
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(50))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onOpenMenu() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = "菜单",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // ===== 会话内搜索条（顶栏搜索图标展开） =====
            if (searchActive) {
                ChatSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onClose = onSearchClose
                )
            }

            // ===== 群聊 0 成员横幅（方案十.7：不能点名回复，点击跳成员管理） =====
            if (isGroupChat && groupEmpty) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onOpenHamburger() }
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "请添加成员",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
}

@Composable
internal fun ChatInputBarArea(
    viewModel: ChatViewModel,
    context: Context,
    settings: AppSettings,
    isGenerating: Boolean,
    isCompressing: Boolean,
    isGroupChat: Boolean,
    multiSelectMode: Boolean,
    searchActive: Boolean,
    selectedMessageIds: Set<String>,
    pendingImageUri: String?,
    ocrState: OcrState,
    wallpaperUri: String?,
    showHamburger: Boolean,
    groupMembers: List<Conversation>,
    groupQueue: List<GroupReplyQueue.Item>,
    imagePickerLauncher: ActivityResultLauncher<String>,
    keyboardOffset: Animatable<Float, AnimationVector1D>,
    onCopy: () -> Unit,
    onExportImage: () -> Unit,
    onDelete: () -> Unit
) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = keyboardOffset.value
                    }
            ) {
                // 输入栏：多选模式下切换为底部操作栏（复制/导出长图/删除），搜索时隐藏
                if (multiSelectMode) {
                    MultiSelectActionBar(
                        selectedCount = selectedMessageIds.size,
                        onCopy = onCopy,
                        onExportImage = onExportImage,
                        onDelete = onDelete
                    )
                } else if (!searchActive) {
                    ChatInputBar(
                        enterToSend = settings.enterToSend,
                        isGenerating = isGenerating,
                        allowSendWhileGenerating = isGroupChat,
                        onSend = { text ->
                            val imageUri = pendingImageUri
                            if (imageUri != null) {
                                viewModel.sendMessageWithImage(context, text, imageUri)
                            } else {
                                viewModel.sendMessage(text)
                            }
                        },
                        onStop = { viewModel.stopGeneration() },
                        enabled = !showHamburger,
                        transparent = wallpaperUri != null,
                        onTextChange = { text -> viewModel.updateInputText(text) },
                        isCompressing = isCompressing,
                        onPickImage = { imagePickerLauncher.launch("image/*") },
                        pendingImageUri = pendingImageUri,
                        onRemoveImage = { viewModel.clearPendingImage() },
                        ocrBusy = ocrState is com.quiddity.app.ui.chat.OcrState.Recognizing,
                        // 群聊成员头像栏（方案十一：并入输入框容器、靠左、随键盘一起动）
                        header = if (isGroupChat) {
                            { mentionScope ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    GroupAvatarBar(
                                        members = groupMembers,
                                        queue = groupQueue,
                                        onTap = { member ->
                                            val memberName = member.persona?.name.orEmpty()
                                            if (mentionScope.isMentionPending && memberName.isNotBlank()) {
                                                // @ 点名：把「@名字」（蓝色）插入输入框
                                                mentionScope.insertMention(memberName)
                                            } else {
                                                // 普通点名回复：头像点击触发该成员回复
                                                viewModel.enqueueGroupMember(member.id)
                                            }
                                        }
                                    )
                                }
                            }
                        } else {
                            null
                        }
                    )
                }
            }
}
