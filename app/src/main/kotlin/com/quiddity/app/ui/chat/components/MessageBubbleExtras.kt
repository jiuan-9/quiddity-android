package com.quiddity.app.ui.chat.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.quiddity.app.ui.components.AiAvatar
import com.quiddity.app.ui.theme.Motion


/**
 * 气泡内思考头部：应用内本地思考块（可展开/收起）+ 独立思考消息标签。
 * 供全部渲染模式（纯文本 / 纯代码 / 混合）共用，保证任何消息都能展示思考。
 */
@Composable
internal fun BubbleThinkingHeader(
    thinking: String,
    isStreaming: Boolean,
    contentEmpty: Boolean,
    isThinking: Boolean
) {
    if (thinking.isNotBlank()) {
        com.quiddity.app.ui.components.ThinkingBlock(
            thinking = thinking,
            isStreaming = isStreaming,
            contentEmpty = contentEmpty
        )
    }
    if (isThinking) {
        Text(
            text = if (isStreaming) "思考中" else "思考",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
internal fun BubbleActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    // scale 用 State 持有而非 by 委托：按压动画期间值变化只在 graphicsLayer draw phase 读取，零重组
    val scaleState = animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = Motion.SpringSoft,
        label = "chip_press_scale"
    )
    // Box 替代 Surface：无 elevation 需求，Box+background+clip 跳过 Surface 的 CompositionLocalProvider 开销
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scaleState.value
                scaleY = scaleState.value
            }
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
internal fun AvatarSlot(
    avatarUri: String?,
    name: String? = null
) {
    AiAvatar(
        avatarUri = avatarUri,
        name = name.orEmpty(),
        size = 40.dp
    )
}

@Composable
internal fun SelectionCircle(isSelected: Boolean) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .then(
                if (isSelected) {
                    Modifier.background(MaterialTheme.colorScheme.primary)
                } else {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// Markdown 行内代码配色：与围栏代码块卡片（CodeBlockView 的 0xFF1E1E2E / 0xFFCDD6F4）保持一致
internal val InlineCodeBackground = Color(0xFF1E1E2E)
internal val InlineCodeForeground = Color(0xFFCDD6F4)

@Composable
internal fun ImageMessageCard(
    imageUri: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    var showImage by remember(imageUri) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(width = 132.dp, height = 92.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(12.dp)
            )
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .semantics { contentDescription = "查看图片" }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (onClick != null) onClick() else showImage = true
            }
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = "图片",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (showImage) {
        Dialog(
            onDismissRequest = { showImage = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showImage = false }
            ) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "图片",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showImage = false }
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
internal fun ImageMessageBubble(
    imageUri: String,
    text: AnnotatedString,
    isUser: Boolean,
    isStreaming: Boolean,
    textColor: Color,
    multiSelectMode: Boolean,
    onSelectToggle: (() -> Unit)?,
    onBubbleClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    bubbleScaleState: androidx.compose.runtime.State<Float>,
    bubbleInteractionSource: MutableInteractionSource,
    bubbleColor: Color,
    bubbleBorderColor: Color
) {
    Box(
        modifier = Modifier
            .widthIn(max = BubbleMaxWidth)
            .graphicsLayer {
                scaleX = bubbleScaleState.value
                scaleY = bubbleScaleState.value
            }
            .let { mod ->
                when {
                    multiSelectMode && onSelectToggle != null -> {
                        mod.clickable(
                            interactionSource = bubbleInteractionSource,
                            indication = null,
                            onClick = onSelectToggle
                        )
                    }
                    isUser && onBubbleClick != null -> {
                        mod.clickable(
                            interactionSource = bubbleInteractionSource,
                            indication = null,
                            onClick = onBubbleClick
                        )
                    }
                    else -> mod
                }
            }
            .clip(BubbleShape(isUser))
            .border(1.dp, bubbleBorderColor, BubbleShape(isUser))
            .background(bubbleColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // 图片图标卡片：点击弹出全屏大图（私聊/群聊/Agent 共用固定 UI）
            ImageMessageCard(
                imageUri = imageUri,
                onClick = if (multiSelectMode) onSelectToggle else null
            )
            // 用户输入的文字（纯图片消息时省略）
            if (text.text.isNotBlank() || isStreaming) {
                Spacer(modifier = Modifier.size(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    SelectableMessageText(
                        text = text,
                        textColor = textColor,
                        onBubbleClick = if (multiSelectMode) onSelectToggle else onBubbleClick,
                        onLongClick = if (multiSelectMode || !isUser) null else onLongClick,
                        modifier = Modifier.widthIn(max = BubbleInnerMaxWidth)
                    )
                    if (isStreaming) {
                        Spacer(modifier = Modifier.size(2.dp))
                        StreamingCursor()
                    }
                }
            }
        }
    }
}

@Composable
internal fun NoticeBubble(
    content: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = content,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
internal fun GameLogBubble(
    content: String,
    modifier: Modifier = Modifier,
    miniAppTitle: String? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            if (!miniAppTitle.isNullOrBlank()) {
                Text(
                    text = "🕹 $miniAppTitle · 对局记录",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                )
                Spacer(Modifier.size(4.dp))
            }
            Text(
                text = content,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
internal fun MiniAppInviteCard(
    title: String,
    content: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(110, easing = Motion.EasingEmphasizedAccelerate),
        label = "invite_card_press"
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = if (pressed) 0.dp else 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = if (pressed) 0.5f else 0.22f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Apps,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "进入小应用",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = if (pressed) 1f else 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
