package com.quiddity.app.ui.miniapps.spy

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quiddity.app.domain.spy.SpyGameMode
import com.quiddity.app.domain.spy.SpyGameState
import com.quiddity.app.domain.spy.SpyPlayer
import com.quiddity.app.domain.spy.SpyRole
import com.quiddity.app.ui.components.AiAvatar
import com.quiddity.app.ui.components.QuiddityPrimaryButton
import com.quiddity.app.ui.components.QuidditySecondaryButton
import com.quiddity.app.ui.theme.Motion

@Composable
internal fun SpyResultScreen(
    game: SpyGameState,
    onRematch: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val civilianWin = game.winner == SpyRole.CIVILIAN
    val winColor = if (civilianWin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val winText = when {
        civilianWin -> "平民胜利"
        game.mode == SpyGameMode.CLASSIC -> "卧底获胜"
        else -> "卧底方获胜"
    }
    val revealPlayers = buildList {
        game.spyIndices.forEach { idx -> game.player(idx)?.let { add(it to SpyRole.SPY) } }
        game.blankIndex?.let { idx -> game.player(idx)?.let { add(it to SpyRole.BLANK) } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp)
    ) {
        SpyTopBar(title = "结算", onBack = onBack)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(112.dp),
                contentAlignment = Alignment.Center
            ) {
                SpySparkleBurst(Modifier.fillMaxSize())
                SpyEntrance(0) {
                    Icon(
                        imageVector = if (civilianWin) Icons.Rounded.EmojiEvents else Icons.Rounded.Visibility,
                        contentDescription = null,
                        tint = winColor,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
            SpyEntrance(1) {
                Text(
                    text = winText,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = winColor
                )
            }
            SpyEntrance(2) {
                Text(
                    text = "共 ${game.round} 轮 · ${game.players.size} 名玩家",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.size(14.dp))
            SpyEntrance(3) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (game.mode == SpyGameMode.BLANK) "🎭 卧底 · 白板揭晓" else "🎭 卧底揭晓",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.size(6.dp))
                        revealPlayers.forEach { (player, role) ->
                            Text(
                                text = "${if (role == SpyRole.BLANK) "白板" else "卧底"}：「${player.name}」 · ${player.word.orEmpty().ifBlank { "空白牌" }}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                game.players.forEachIndexed { i, p ->
                    SpyEntrance(4 + i) {
                        ResultPlayerRow(player = p)
                    }
                }
            }
        }
        SpyEntrance(10) {
            QuiddityPrimaryButton(
                text = "再来一局",
                onClick = onRematch,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            )
        }
        Spacer(Modifier.size(8.dp))
        SpyEntrance(11) {
            QuidditySecondaryButton(
                text = "返回",
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            )
        }
        Spacer(Modifier.size(16.dp))
    }
}

@Composable
internal fun ResultPlayerRow(player: SpyPlayer) {
    val isSpecial = player.role == SpyRole.SPY || player.role == SpyRole.BLANK
    val roleColor = when (player.role) {
        SpyRole.SPY -> MaterialTheme.colorScheme.error
        SpyRole.BLANK -> MaterialTheme.colorScheme.tertiary
        SpyRole.CIVILIAN, null -> MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AiAvatar(
            avatarUri = player.avatarUri,
            name = player.name,
            size = 36.dp,
            modifier = Modifier.graphicsLayer { alpha = if (player.alive) 1f else 0.4f }
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = player.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (player.alive) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            if (!player.alive) {
                Text(
                    text = "已出局",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = roleColor.copy(alpha = 0.1f)
        ) {
            Text(
                text = when (player.role) {
                    SpyRole.SPY -> "卧底"
                    SpyRole.BLANK -> "白板"
                    SpyRole.CIVILIAN, null -> "平民"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = roleColor,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.size(2.dp))
        Text(
            text = player.word ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ===== 公共组件 =====

@Composable
internal fun SpySectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
internal fun SpySelectCard(
    title: String,
    subtitle: String,
    avatarUri: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    SpyPressable(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
            else MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .border(
                            1.5.dp,
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            CircleShape
                        )
                        .clip(CircleShape)
                ) {
                    AiAvatar(avatarUri = avatarUri, name = title, size = 40.dp)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AnimatedContent(
                    targetState = selected,
                    transitionSpec = {
                        (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) togetherWith scaleOut(tween(Motion.DurationShort)))
                    },
                    label = "select_state"
                ) { sel ->
                    if (sel) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "已加入",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Text(
                            text = "加入",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun Local(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "💡",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
internal fun SpyTopBar(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

internal const val MAX_LLM = 3
