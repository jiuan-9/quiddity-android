package com.quiddity.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 滑动高亮文字（shimmer）：tool 使用/思考中统一使用的高亮滑块样式。
 * [icon] 非空时在文字前显示图标。
 */
@Composable
fun ShimmerHighlightText(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "shimmer_highlight")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing)
        ),
        label = "shimmer_progress"
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
            val brush = Brush.linearGradient(
                colors = listOf(
                    colorScheme.onSurfaceVariant,
                    colorScheme.primary,
                    colorScheme.onSurfaceVariant
                ),
                start = Offset(widthPx * (progress - 0.5f), 0f),
                end = Offset(widthPx * (progress + 0.5f), 0f)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(brush = brush),
                fontWeight = FontWeight.Medium
            )
        }
    }
}
