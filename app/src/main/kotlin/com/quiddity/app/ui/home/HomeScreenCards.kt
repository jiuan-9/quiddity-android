package com.quiddity.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.ui.components.AiAvatar
import com.quiddity.app.ui.theme.Motion
import com.quiddity.app.util.DateUtils
import kotlinx.coroutines.delay


@Composable
internal fun cardColor(isMultiSelect: Boolean, isSelected: Boolean, hasListWallpaper: Boolean): Color {
    return when {
        isMultiSelect && isSelected ->
            if (hasListWallpaper) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.80f)
            else MaterialTheme.colorScheme.primaryContainer
        isMultiSelect ->
            if (hasListWallpaper) MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
        else ->
            if (hasListWallpaper) MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.78f)
            else MaterialTheme.colorScheme.surfaceContainerLow
    }
}

@Composable
internal fun ConversationCard(
    conversation: Conversation,
    isMultiSelect: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasListWallpaper: Boolean = false
) {
    // 实现策略：
    // - 壁纸不存在时：保持原有不透明 surfaceContainerLow（视觉无变化）
    // - 壁纸存在时：
    //   * 普通态：surfaceContainerLow @ 0.78f 透明度，让壁纸透出形成毛玻璃质感
    //   * 多选未选：surfaceContainerLow @ 0.55f 透明度（更透，弱化未选项）
    //   * 多选已选：primaryContainer @ 0.80f 透明度（保留选中高亮）
    // - tonalElevation 在壁纸存在时设为 0，避免 M3 自动叠加的不透明色调破坏透明效果
    val cardColor = cardColor(isMultiSelect, isSelected, hasListWallpaper)

    // - 仅保留头像 + 名字
    // - 多选模式下右侧显示选中状态勾选框
    val aiName = conversation.persona?.name?.ifBlank { null } ?: conversation.title
    val aiAvatarUri = conversation.persona?.aiAvatarUri

    // Box 替代 Surface：cardColor 已计算最终色值，Box+background+clip 跳过 Surface 的 CompositionLocalProvider 与 elevation 处理开销
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(cardColor)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isMultiSelect) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "已选中",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.size(14.dp))
            } else {
                // 名字首字头像兜底：未设置头像但有 AI 名时显示首字（方案二十一）
                AiAvatar(
                    avatarUri = aiAvatarUri,
                    name = conversation.persona?.name.orEmpty(),
                    size = 48.dp
                )
                Spacer(modifier = Modifier.size(14.dp))
            }

            Text(
                text = aiName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun GroupConversationCard(
    conversation: Conversation,
    isMultiSelect: Boolean,
    isSelected: Boolean,
    userAvatarUri: String?,
    memberResolver: (List<String>) -> List<Conversation>,
    onTap: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasListWallpaper: Boolean = false
) {
    val members = remember(conversation.memberConversationIds) {
        memberResolver(conversation.memberConversationIds)
    }
    val cardColor = cardColor(isMultiSelect, isSelected, hasListWallpaper)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(cardColor)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isMultiSelect) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "已选中",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.size(14.dp))
            } else {
                GroupAvatarComposite(
                    userAvatarUri = userAvatarUri,
                    members = members
                )
                Spacer(modifier = Modifier.size(14.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title.ifBlank { "新群聊" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.size(2.dp))
                Text(
                    text = DateUtils.formatTimestamp(conversation.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
internal fun GroupAvatarComposite(
    userAvatarUri: String?,
    members: List<Conversation>
) {
    val memberList = members.take(3)
    val overlap = 16.dp
    Box(
        modifier = Modifier.size(
            width = (28.dp.value + memberList.size * overlap.value).dp,
            height = 28.dp
        )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            if (userAvatarUri != null) {
                AsyncImage(
                    model = userAvatarUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        memberList.forEachIndexed { index, member ->
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = ((index + 1) * overlap.value).dp)
                    .size(28.dp)
            ) {
                AiAvatar(
                    avatarUri = member.persona?.aiAvatarUri,
                    name = member.persona?.name.orEmpty(),
                    size = 28.dp
                )
            }
        }
    }
}

@Composable
internal fun WelcomeContent() {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp
    val isLandscape = screenWidthDp > screenHeightDp

    val titleFontSize = (screenWidthDp.value * 0.20f).sp
    val maxTitleSize = 72.sp
    val finalTitleSize = if (titleFontSize.value > maxTitleSize.value) maxTitleSize else titleFontSize

    val subtitleFontSize = 18.sp

    val titleColor = MaterialTheme.colorScheme.onSurface
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    val verticalSpacing = if (isLandscape) 12.dp else 20.dp
    val horizontalPadding = if (isLandscape) 40.dp else 24.dp

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(40)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = 300,
                easing = Motion.EasingEmphasizedDecelerate
            )
        ) + slideInVertically(
            initialOffsetY = { it / 6 },
            animationSpec = tween(
                durationMillis = 320,
                easing = Motion.EasingEmphasizedDecelerate
            )
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterStart
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(verticalSpacing)
            ) {
                Text(
                    text = "Quiddity",
                    fontSize = finalTitleSize,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    textAlign = TextAlign.Start,
                    letterSpacing = (-1.2).sp,
                    lineHeight = (finalTitleSize.value * 1.05f).sp,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "开始你的旅程",
                    fontSize = subtitleFontSize,
                    fontWeight = FontWeight.Medium,
                    color = subtitleColor,
                    textAlign = TextAlign.Start,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
