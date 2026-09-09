package com.quiddity.app.ui.chat.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.quiddity.app.ui.theme.Motion
import kotlin.math.sin
/**
 * 三点脉冲指示器（AI 正在思考）。
 *
 * 三个圆点以相位差 1/3 周期上下脉冲。
 * 当前规则：scale/alpha 全部通过 graphicsLayer 在 draw phase 读取 State.value 计算，
 * 避免 Modifier.scale()/alpha() 在组合阶段读取 state 导致每帧重组。
 */
@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    val phaseState = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(Motion.TypingCycleMs),
            repeatMode = RepeatMode.Restart
        ),
        label = "typing_phase"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { i ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .graphicsLayer {
                        val offset = (phaseState.value + i * 0.33f) % 1f
                        val scale = 0.6f + 0.4f * sin(offset * Math.PI).toFloat()
                        scaleX = scale
                        scaleY = scale
                        alpha = 0.4f + 0.6f * scale
                    }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant)
            )
        }
    }
}
