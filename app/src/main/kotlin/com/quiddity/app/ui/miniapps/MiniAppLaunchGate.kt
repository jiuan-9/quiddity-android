package com.quiddity.app.ui.miniapps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quiddity.app.ui.theme.Motion
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * 小应用通用"解压"进入动画：
 * - 小应用像压缩包一样被压着（箱体压扁 + 拉链未拉开）；
 * - 进入时展示解压过程：拉链头下滑、箱体逐渐展开、进度环走满；
 * - 箱体用中性蓝灰色，避免与棋盘小应用的木纹配色混淆；
 * - 解压完成后内容以弹性缩放展开，加载层淡出。
 */
@Composable
fun MiniAppLaunchGate(
    app: MiniApp,
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(0f) }
    val contentScale = remember { Animatable(0.9f) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(1050, easing = Motion.EasingEmphasizedDecelerate))
        loading = false
        contentAlpha.animateTo(1f, tween(220, easing = Motion.EasingEmphasizedDecelerate))
        contentScale.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        )
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = contentAlpha.value
                    scaleX = contentScale.value
                    scaleY = contentScale.value
                }
        ) {
            content()
        }
        AnimatedVisibility(
            visible = loading,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(240))
        ) {
            DecompressOverlay(appName = app.name, progress = progress.value)
        }
    }
}

@Composable
private fun DecompressOverlay(appName: String, progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.98f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            CompressBox(progress = progress)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(30.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = "${(progress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "正在解压「$appName」…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CompressBox(progress: Float) {
    val wobble = sin(progress * 18f) * (1f - progress) * 3.5f
    Canvas(
        modifier = Modifier
            .size(116.dp)
            .graphicsLayer { rotationZ = wobble }
    ) {
        val w = size.width
        val h = size.height
        val boxH = h * (0.52f + 0.48f * progress)
        val top = (h - boxH) / 2f
        val left = w * 0.16f
        val boxW = w * 0.68f
        val corner = CornerRadius(12.dp.toPx(), 12.dp.toPx())
        val boxLight = Color(0xFF454E5E)
        val boxDark = Color(0xFF262C37)
        val lineColor = Color(0xFF9AA3B0)

        drawRoundRect(
            color = Color(0x33000000),
            topLeft = Offset(left + 4.dp.toPx(), top + 6.dp.toPx()),
            size = Size(boxW, boxH),
            cornerRadius = corner
        )
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(boxLight, boxDark),
                start = Offset(left, top),
                end = Offset(left + boxW, top + boxH)
            ),
            topLeft = Offset(left, top),
            size = Size(boxW, boxH),
            cornerRadius = corner
        )
        for (i in 1..3) {
            val y = top + boxH * i / 4f
            drawLine(
                color = lineColor.copy(alpha = 0.32f),
                start = Offset(left + 6.dp.toPx(), y),
                end = Offset(left + boxW - 6.dp.toPx(), y),
                strokeWidth = 1.5.dp.toPx()
            )
        }
        drawLine(
            color = Color(0xFFB7BEC9).copy(alpha = 0.55f),
            start = Offset(w / 2f, top + boxH * 0.1f),
            end = Offset(w / 2f, top + boxH * 0.9f),
            strokeWidth = 2.dp.toPx()
        )
        val zipY = top + boxH * (0.1f + 0.8f * progress)
        drawCircle(
            color = Color(0xFFC3CAD4),
            radius = 5.5.dp.toPx(),
            center = Offset(w / 2f, zipY)
        )
        if (progress < 0.95f) {
            val a = (1f - progress).coerceAtLeast(0f)
            for (i in 0 until 3) {
                val y = top + boxH * (0.22f + 0.26f * i)
                drawLine(
                    color = lineColor.copy(alpha = 0.45f * a),
                    start = Offset(left - 9.dp.toPx(), y),
                    end = Offset(left + boxW + 9.dp.toPx(), y),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
    }
}
