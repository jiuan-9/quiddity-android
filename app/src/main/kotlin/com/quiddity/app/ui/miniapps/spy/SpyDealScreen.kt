package com.quiddity.app.ui.miniapps.spy

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quiddity.app.domain.spy.SpyGameState
import com.quiddity.app.domain.spy.SpyRole
import com.quiddity.app.ui.theme.Motion

@Composable
internal fun SpyDealScreen(
    game: SpyGameState,
    userIndex: Int,
    notice: String?,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val user = game.player(userIndex) ?: return
    val role = user.role ?: return
    val word = user.word ?: return
    val haptics = spyHaptics()
    val reduced = isSystemAnimationsDisabled()
    val density = LocalDensity.current.density

    var flipped by remember(game.id) { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(Motion.DurationLong + 140, easing = Motion.EasingEmphasizedDecelerate),
        label = "deal_flip"
    )
    val borderColor by animateColorAsState(
        targetValue = if (flipped) {
            (when (role) {
                SpyRole.SPY -> MaterialTheme.colorScheme.error
                SpyRole.BLANK -> MaterialTheme.colorScheme.tertiary
                SpyRole.CIVILIAN -> MaterialTheme.colorScheme.primary
            })
                .copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        },
        animationSpec = tween(Motion.DurationLong, easing = Motion.EasingStandard),
        label = "deal_border"
    )
    val cardScale = remember { Animatable(if (reduced) 1f else 0.86f) }
    val cardAlpha = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) {
            cardAlpha.animateTo(1f, tween(Motion.DurationShort + 80, easing = Motion.EasingStandard))
            cardScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SpyTopBar(title = "发牌", onBack = onBack)
        SpyEntrance(index = 1) {
            Text(
                text = "悄悄记住你的词，别让同桌看出破绽",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .size(width = 230.dp, height = 310.dp)
                    .graphicsLayer {
                        rotationY = rotation
                        cameraDistance = 14f * density
                        alpha = cardAlpha.value
                        scaleX = cardScale.value
                        scaleY = cardScale.value
                    }
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(enabled = !flipped) {
                        flipped = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp,
                border = BorderStroke(1.5.dp, borderColor)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (rotation < 90f) {
                        CardBack()
                    } else {
                        Box(Modifier.graphicsLayer { rotationY = 180f }) {
                            CardFront(role = role, word = word)
                        }
                    }
                }
            }
        }
        SpyEntrance(index = 3) {
            PlayerStrip(players = game.players)
        }
        if (!notice.isNullOrBlank()) {
            Spacer(Modifier.size(10.dp))
            SpyEntrance(index = 4) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = notice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
        Spacer(Modifier.size(12.dp))
        Button(
            onClick = onContinue,
            enabled = flipped,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            Text(
                text = if (flipped) "记住了，开始" else "点击卡片查看你的词",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.size(20.dp))
    }
}

@Composable
internal fun CardBack() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(42.dp)
                )
            }
            Spacer(Modifier.size(18.dp))
            Text(
                text = "点击翻开你的词",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = "反扣在桌上，只有你能看",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun CardFront(role: SpyRole, word: String) {
    val roleColor = when (role) {
        SpyRole.SPY -> MaterialTheme.colorScheme.error
        SpyRole.BLANK -> MaterialTheme.colorScheme.tertiary
        SpyRole.CIVILIAN -> MaterialTheme.colorScheme.primary
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = roleColor.copy(alpha = 0.12f)
        ) {
            Text(
                text = when (role) {
                    SpyRole.SPY -> "卧底"
                    SpyRole.BLANK -> "白板"
                    SpyRole.CIVILIAN -> "平民"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = roleColor,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
            )
        }
        Spacer(Modifier.weight(0.5f))
        Text(
            text = if (role == SpyRole.BLANK) "（空白牌）" else word,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (role == SpyRole.BLANK) roleColor else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.weight(0.5f))
        Text(
            text = when (role) {
                SpyRole.SPY -> "别让别人看出你与他人不同"
                SpyRole.BLANK -> "你没有词，装成和大家一样，别被识破"
                SpyRole.CIVILIAN -> "描述词让大家都猜到，但别说出口"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ===== 对局页 =====
