package com.quiddity.app.ui.miniapps.spy

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quiddity.app.data.model.Character
import com.quiddity.app.domain.spy.SpyGameState
import com.quiddity.app.domain.spy.SpyRole
import com.quiddity.app.ui.components.AiAvatar
import com.quiddity.app.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ===== 系统动画偏好 =====

@Composable
fun isSystemAnimationsDisabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

@Composable
fun spyHaptics(): HapticFeedback = LocalHapticFeedback.current

// ===== 按压反馈容器 =====

@Composable
fun SpyPressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(Motion.DurationShort, easing = Motion.EasingStandard),
        label = "spy_press_scale"
    )
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                enabled = enabled,
                onClick = onClick
            )
    ) {
        content()
    }
}

// ===== 入场动画（可交错） =====

@Composable
fun SpyEntrance(
    index: Int = 0,
    modifier: Modifier = Modifier,
    distance: Float = 14f,
    content: @Composable () -> Unit
) {
    val reduced = isSystemAnimationsDisabled()
    val appear = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) {
            delay((index * 60L).coerceAtMost(360L))
            appear.animateTo(
                targetValue = 1f,
                animationSpec = tween(Motion.DurationMedium + 60, easing = Motion.EasingEmphasizedDecelerate)
            )
        }
    }
    Box(
        modifier = modifier.graphicsLayer {
            alpha = appear.value
            translationY = (1f - appear.value) * distance
        }
    ) {
        content()
    }
}

// ===== 打字点点指示 =====

@Composable
fun SpyThinkingDots(
    text: String,
    modifier: Modifier = Modifier
) {
    val reduced = isSystemAnimationsDisabled()
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (reduced) {
            repeat(3) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
                )
            }
        } else {
            val transition = rememberInfiniteTransition(label = "spy_dots")
            repeat(3) { i ->
                val alpha by transition.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(500, delayMillis = i * 160),
                        RepeatMode.Reverse
                    ),
                    label = "spy_dot_$i"
                )
                Box(
                    Modifier
                        .size(5.dp)
                        .graphicsLayer { this.alpha = alpha }
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
        }
    }
}

// ===== 回合 / 阶段横幅 =====

@Composable
fun SpyRoundBanner(
    key: String,
    text: String,
    modifier: Modifier = Modifier
) {
    val reduced = isSystemAnimationsDisabled()
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        if (reduced) {
            progress.snapTo(1f)
            delay(900)
            progress.snapTo(0f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate))
            delay(1000)
            progress.animateTo(0f, tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedAccelerate))
        }
    }
    if (progress.value <= 0f) return
    Surface(
        modifier = modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * -10f
            scaleX = 0.94f + 0.06f * progress.value
            scaleY = 0.94f + 0.06f * progress.value
        },
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 4.dp
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
        )
    }
}

// ===== 淘汰揭晓覆盖层 =====

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpyEliminationOverlay(
    event: SpyVoteEvent,
    game: SpyGameState,
    modifier: Modifier = Modifier
) {
    val reduced = isSystemAnimationsDisabled()
    val alpha = remember { Animatable(if (reduced) 1f else 0f) }
    val scale = remember { Animatable(if (reduced) 1f else 0.72f) }
    LaunchedEffect(event) {
        alpha.snapTo(if (reduced) 1f else 0f)
        scale.snapTo(if (reduced) 1f else 0.72f)
        if (reduced) {
            delay(700)
            alpha.snapTo(0f)
        } else {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            )
            alpha.animateTo(1f, tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate))
            delay(900)
            alpha.animateTo(0f, tween(220, easing = Motion.EasingEmphasizedAccelerate))
        }
    }
    if (alpha.value <= 0f) return

    val eliminated = event.eliminatedIndex?.let { game.player(it) }
    val title = when {
        event.tie && event.pk -> "PK 平票 · 无人出局"
        event.tie -> "平票！进入 PK 对决"
        eliminated != null -> "「${eliminated.name}」出局"
        else -> "投票结束"
    }
    val subtitle = when {
        event.tie && event.pk -> "再辩一轮仍难分高下，进入下一轮"
        event.tie -> "平票者各补一句发言，其余人二选一"
        eliminated != null -> {
            val votes = event.tallies[eliminated.index] ?: 0
            if (event.gameOver) "共获得 $votes 票 · 本局结束" else "共获得 $votes 票"
        }
        else -> ""
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f * alpha.value))
            .graphicsLayer { this.alpha = alpha.value },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 26.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (event.tie) "⚔️" else "🎯",
                    style = MaterialTheme.typography.displayMedium
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (event.tie) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (event.tallies.isNotEmpty()) {
                    Spacer(Modifier.size(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        event.tallies.entries.sortedByDescending { it.value }.forEach { (index, count) ->
                            val p = game.player(index)
                            val name = p?.name ?: "玩家$index"
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (index == event.eliminatedIndex) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Text(
                                    text = "$name · $count 票",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (index == event.eliminatedIndex) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===== 结算彩带粒子 =====

private data class SparkleParticle(
    val dx: Float,
    val dy: Float,
    val size: Float,
    val delay: Float,
    val color: Color
)

@Composable
fun SpySparkleBurst(modifier: Modifier = Modifier) {
    val reduced = isSystemAnimationsDisabled()
    if (reduced) return
    val scheme = MaterialTheme.colorScheme
    val colors = listOf(scheme.primary, scheme.error, scheme.tertiary, scheme.outline)
    val particles = remember {
        val rnd = Random(42)
        List(22) {
            val angle = rnd.nextFloat() * (Math.PI.toFloat() * 2f)
            val dist = 90f + rnd.nextFloat() * 130f
            SparkleParticle(
                dx = cos(angle) * dist,
                dy = sin(angle) * dist,
                size = 2.5f + rnd.nextFloat() * 3.5f,
                delay = rnd.nextFloat() * 0.25f,
                color = colors[rnd.nextInt(colors.size)]
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(820, easing = Motion.EasingEmphasizedDecelerate))
    }
    Canvas(modifier) {
        particles.forEach { p ->
            val t = ((progress.value - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (t > 0f) {
                val ease = 1f - (1f - t) * (1f - t)
                val pos = Offset(
                    x = size.width / 2f + p.dx * ease,
                    y = size.height / 2f + p.dy * ease - ease * ease * 70f
                )
                drawCircle(
                    color = p.color.copy(alpha = (1f - t) * 0.9f),
                    radius = p.size * (1f - t * 0.4f),
                    center = pos
                )
            }
        }
    }
}

// ===== 规则弹窗 =====

@Composable
fun SpyRulesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("游戏规则", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RuleLine("玩法：经典 1 名卧底；双卧底 2 名卧底同词；白板 1 卧底 + 1 名无词白板（归属卧底方）。")
                RuleLine("每轮所有存活玩家依次用一句话描述自己的词，不能说出词本身。")
                RuleLine("发言结束后投票，票数最多的人出局；平票进入 PK：平票者各补一句发言，其余人二选一，仍平票则无人出局。")
                RuleLine("卧底方（卧底 + 白板）全部出局，平民获胜；存活卧底方人数 ≥ 平民人数，卧底方获胜。")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    )
}

@Composable
private fun RuleLine(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ===== 我的词提醒弹窗 =====

@Composable
fun SpyWordReminderDialog(
    role: SpyRole,
    word: String,
    onDismiss: () -> Unit
) {
    val isSpy = role == SpyRole.SPY
    val isBlank = role == SpyRole.BLANK
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when {
                    isBlank -> "白板身份"
                    isSpy -> "卧底身份"
                    else -> "你的词"
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when {
                        isBlank -> "白板"
                        isSpy -> "卧底"
                        else -> "平民"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isBlank -> MaterialTheme.colorScheme.tertiary
                        isSpy -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = if (isBlank) "（空白牌）" else word,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = when {
                        isBlank -> "你没有词，装成和大家一样，别被识破"
                        isSpy -> "别让别人看出你的词和大家不一样"
                        else -> "描述你的词，但千万别说出口"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isSpy) "藏好了" else if (isBlank) "装得像点" else "记住了")
            }
        }
    )
}

// ===== 配置页桌位预览 =====

@Composable
fun SpySetupSeatRow(
    userAvatarUri: String?,
    llmCharacters: List<Character>,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        SpySeatSlot(
            name = "你",
            avatarUri = userAvatarUri,
            filled = true,
            accent = true,
            modifier = Modifier.weight(1f)
        )
        repeat(3) { i ->
            val character = llmCharacters.getOrNull(i)
            if (character != null) {
                SpySeatSlot(
                    name = character.persona.name.ifBlank { "角色${i + 1}" },
                    avatarUri = character.aiAvatarUri ?: character.persona.aiAvatarUri,
                    filled = true,
                    modifier = Modifier.weight(1f)
                )
            } else {
                SpySeatSlot(
                    empty = true,
                    onClick = onAddClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SpySeatSlot(
    modifier: Modifier = Modifier,
    name: String = "",
    avatarUri: String? = null,
    filled: Boolean = false,
    accent: Boolean = false,
    empty: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (empty) {
            SpyPressable(
                onClick = onClick ?: {},
                enabled = onClick != null,
                modifier = Modifier.size(52.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .border(
                        2.dp,
                        if (accent) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                        else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f),
                        CircleShape
                    )
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                AiAvatar(avatarUri = avatarUri, name = name, size = 46.dp)
            }
        }
        Spacer(Modifier.size(4.dp))
        Text(
            text = if (empty) "点击加入" else name,
            style = MaterialTheme.typography.labelSmall,
            color = if (empty) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}
