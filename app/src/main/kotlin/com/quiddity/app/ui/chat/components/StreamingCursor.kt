package com.quiddity.app.ui.chat.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.quiddity.app.ui.theme.Motion
/**
 * 流式输出时的闪烁光标 `▋`。
 *
 * 当前规则：alpha 通过 graphicsLayer 在 draw phase 读取 State.value，
 * 避免 Modifier.alpha() 在组合阶段读取 state 导致每帧重组。
 */
@Composable
fun StreamingCursor(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alphaState = transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(Motion.CursorBlinkMs),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )
    Text(
        text = "▋",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.graphicsLayer { alpha = alphaState.value }
    )
}
