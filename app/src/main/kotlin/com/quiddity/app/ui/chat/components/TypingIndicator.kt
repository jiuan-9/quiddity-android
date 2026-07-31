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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.quiddity.app.ui.theme.Motion
import kotlin.math.sin

/**
 * 三点脉冲指示器（AI 正在思考）。
 *
 * 三个圆点以相位差 1/3 周期上下脉冲。
 * 所有动画通过 alpha/scale modifier 在 Draw 阶段完成，零重组。
 */
@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(
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
            val offset = (phase + i * 0.33f) % 1f
            // 用 sin 平滑曲线（0..1..0）
            val scale = 0.6f + 0.4f * sin(offset * Math.PI).toFloat()
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .scale(scale)
                    .alpha(0.4f + 0.6f * scale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant)
            )
        }
    }
}
