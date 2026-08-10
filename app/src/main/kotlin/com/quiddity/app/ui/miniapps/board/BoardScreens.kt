package com.quiddity.app.ui.miniapps.board

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.DonutLarge
import androidx.compose.material.icons.rounded.DonutSmall
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.quiddity.app.domain.board.BoardDifficulty
import com.quiddity.app.domain.board.BoardGameType
import com.quiddity.app.ui.theme.Motion
import kotlinx.coroutines.delay

/*
 * 棋盘小应用的前置页面：选棋种 / 选模式 / 邀请角色。
 * 视觉语言：大圆角卡片 + 细腻描边 + 图标色块，避免廉价感。
 */

@Composable
fun BoardGameSelectScreen(
    onBack: () -> Unit,
    onSelect: (BoardGameType) -> Unit
) {
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp)
    ) {
        BoardTopBar(title = "选择棋种", onBack = onBack)
        Spacer(Modifier.size(8.dp))
        Text(
            text = "想下什么棋？",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "和角色或 AI 来一局，对局中可以随时聊天",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(24.dp))
        GameTypeCard(
            icon = Icons.Rounded.DonutSmall,
            title = BoardGameType.GOMOKU.displayName,
            subtitle = "15×15 · 先连成五子获胜",
            accent = MaterialTheme.colorScheme.primary,
            onClick = { onSelect(BoardGameType.GOMOKU) }
        )
        Spacer(Modifier.size(16.dp))
        GameTypeCard(
            icon = Icons.Rounded.DonutLarge,
            title = BoardGameType.GO.displayName,
            subtitle = "9×9 · 提子、围空（简化规则）",
            accent = MaterialTheme.colorScheme.primary,
            onClick = { onSelect(BoardGameType.GO) }
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = "围棋为简化规则：提子、禁着、停一手与近似数子计分，完整规则后续优化。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun BoardModeSelectScreen(
    game: BoardGameType,
    onBack: () -> Unit,
    onInvite: () -> Unit,
    onVsComputer: () -> Unit
) {
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp)
    ) {
        BoardTopBar(title = "${game.displayName} · 选择对手", onBack = onBack)
        Spacer(Modifier.size(8.dp))
        Text(
            text = "谁来应战？",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.size(24.dp))
        GameTypeCard(
            icon = Icons.Rounded.Person,
            title = "邀请好友对战",
            subtitle = "从你的角色库里邀请一位角色",
            accent = MaterialTheme.colorScheme.primary,
            onClick = onInvite
        )
        Spacer(Modifier.size(16.dp))
        GameTypeCard(
            icon = Icons.Rounded.Computer,
            title = "与电脑对战",
            subtitle = "无需联网，本地棋力即时应手",
            accent = MaterialTheme.colorScheme.primary,
            onClick = onVsComputer
        )
    }
}

@Composable
fun BoardDifficultySelectScreen(
    game: BoardGameType,
    onBack: () -> Unit,
    onSelect: (BoardDifficulty) -> Unit
) {
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp)
    ) {
        BoardTopBar(title = "${game.displayName} · 选择难度", onBack = onBack)
        Spacer(Modifier.size(8.dp))
        Text(
            text = "电脑棋手水平",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "难度决定电脑的棋力，对局中可随时认输重开换难度",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(24.dp))
        BoardDifficulty.entries.forEachIndexed { index, difficulty ->
            GameTypeCard(
                icon = Icons.Rounded.Star,
                title = "${difficulty.label} · ${"★".repeat(index + 1)}${"☆".repeat(2 - index)}",
                subtitle = difficulty.description,
                accent = MaterialTheme.colorScheme.primary,
                onClick = { onSelect(difficulty) }
            )
            if (index < BoardDifficulty.entries.lastIndex) {
                Spacer(Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun BoardInviteScreen(
    invitees: List<BoardInvitee>,
    game: BoardGameType,
    checking: Boolean,
    onBack: () -> Unit,
    onInvite: (BoardInvitee) -> Unit
) {
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        BoardTopBar(
            title = "邀请好友 · ${game.displayName}",
            onBack = onBack,
            horizontalPadding = 20.dp
        )
        Text(
            text = "选择一位好友，TA 将作为你的对手。邀请前会自动检测 API 连接，失败则用本地电脑兜底。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(Modifier.size(8.dp))
        if (invitees.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "还没有可邀请的好友\n先去创建一个私聊角色吧",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp
                )
            ) {
                items(invitees, key = { it.key }) { invitee ->
                    BoardInviteeCard(
                        invitee = invitee,
                        enabled = !checking,
                        onClick = { onInvite(invitee) }
                    )
                }
            }
        }
    }
    if (checking) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Text(
                        text = "正在检测 API 连接…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun GameTypeCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(110, easing = Motion.EasingEmphasizedAccelerate),
        label = "card_press"
    )
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate))
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 18f
            }
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        shadowElevation = if (pressed) 0.dp else 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            accent.copy(alpha = if (pressed) 0.35f else 0.18f)
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.1f)),
                            radius = 40f
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(27.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = accent.copy(alpha = if (pressed) 0.9f else 0.45f),
                modifier = Modifier
                    .size(20.dp)
                    .rotate(180f)
            )
        }
    }
}

@Composable
private fun BoardInviteeCard(
    invitee: BoardInvitee,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val name = invitee.displayName
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = tween(110, easing = Motion.EasingEmphasizedAccelerate),
        label = "invite_press"
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = if (pressed) 0.dp else 1.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (pressed && enabled) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                if (invitee.avatarUri != null) {
                    AsyncImage(
                        model = invitee.avatarUri,
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp).clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val hint = invitee.subtitle
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "邀请",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun BoardTopBar(
    title: String,
    onBack: () -> Unit,
    horizontalPadding: androidx.compose.ui.unit.Dp = 0.dp,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        trailing?.invoke()
    }
}
