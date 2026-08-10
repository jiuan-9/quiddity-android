package com.quiddity.app.ui.miniapps

import androidx.compose.foundation.Canvas
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * 中心页 / 启动动画共用的应用图标：
 * 棋盘、谁是卧底、拆弹小队使用自绘彩色图标，其余走通用 Material 图标。
 */
@Composable
fun MiniAppAppIcon(app: MiniApp, modifier: Modifier = Modifier) {
    when (app.id) {
        "board" -> BoardAppIcon(modifier)
        "spy" -> SpyAppIcon(modifier)
        "defuse" -> DefuseAppIcon(modifier)
        else -> Icon(
            imageVector = app.icon,
            contentDescription = app.name,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = modifier
        )
    }
}

/** 棋盘：木纹棋盘 + 黑白子 */
@Composable
private fun BoardAppIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val side = minOf(size.width, size.height)
        val pad = side * 0.10f
        val inner = side - pad * 2f
        val cell = inner / 4f
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFE8C894), Color(0xFFC89A5C)),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            ),
            cornerRadius = CornerRadius(side * 0.18f, side * 0.18f),
            size = Size(size.width, size.height)
        )
        for (i in 0..4) {
            val p = pad + i * cell
            drawLine(
                color = Color(0xFF7A5A32).copy(alpha = 0.75f),
                start = Offset(pad, p),
                end = Offset(pad + inner, p),
                strokeWidth = side * 0.025f
            )
            drawLine(
                color = Color(0xFF7A5A32).copy(alpha = 0.75f),
                start = Offset(p, pad),
                end = Offset(p, pad + inner),
                strokeWidth = side * 0.025f
            )
        }
        drawCircle(
            color = Color(0xFF7A5A32),
            radius = side * 0.035f,
            center = Offset(pad + 2 * cell, pad + 2 * cell)
        )
        drawCircle(
            color = Color(0xFF1B1B1F),
            radius = side * 0.16f,
            center = Offset(pad + 1 * cell, pad + 1 * cell)
        )
        drawCircle(
            color = Color(0xFFF7F3EA),
            radius = side * 0.16f,
            center = Offset(pad + 3 * cell, pad + 3 * cell),
            style = Stroke(width = side * 0.04f)
        )
        drawCircle(
            color = Color(0xFF1B1B1F),
            radius = side * 0.10f,
            center = Offset(pad + 3 * cell, pad + 1 * cell)
        )
    }
}

/** 谁是卧底：放大镜 + 问号，代表"找出可疑的人" */
@Composable
private fun SpyAppIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cream = Color(0xFFF4EBDD)
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF4A58A6), Color(0xFF242A62)),
                start = Offset.Zero,
                end = Offset(w, h)
            ),
            size = size,
            cornerRadius = CornerRadius(w * 0.20f, w * 0.20f)
        )
        drawCircle(
            color = cream,
            radius = w * 0.24f,
            center = Offset(w * 0.47f, h * 0.43f),
            style = Stroke(width = w * 0.075f)
        )
        drawCircle(
            color = Color(0xFF181C40).copy(alpha = 0.78f),
            radius = w * 0.19f,
            center = Offset(w * 0.47f, h * 0.43f)
        )
        drawLine(
            color = cream,
            start = Offset(w * 0.64f, h * 0.60f),
            end = Offset(w * 0.80f, h * 0.76f),
            strokeWidth = w * 0.09f,
            cap = StrokeCap.Round
        )
        drawArc(
            color = cream,
            startAngle = 90f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(w * 0.40f, h * 0.34f),
            size = Size(w * 0.14f, h * 0.14f),
            style = Stroke(width = w * 0.05f, cap = StrokeCap.Round)
        )
        drawLine(
            color = cream,
            start = Offset(w * 0.47f, h * 0.48f),
            end = Offset(w * 0.47f, h * 0.54f),
            strokeWidth = w * 0.05f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = cream,
            radius = w * 0.026f,
            center = Offset(w * 0.47f, h * 0.59f)
        )
        for ((fx, fy) in listOf(0.70f to 0.80f, 0.78f to 0.85f, 0.86f to 0.80f)) {
            drawCircle(
                color = cream,
                radius = w * 0.030f,
                center = Offset(w * fx, h * fy)
            )
        }
    }
}

/** 拆弹小队：炸弹 + 剪刀剪断引线 */
@Composable
private fun DefuseAppIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val silver = Color(0xFFE2E4EC)
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFCC4834), Color(0xFF8E221C)),
                start = Offset.Zero,
                end = Offset(w, h)
            ),
            size = size,
            cornerRadius = CornerRadius(w * 0.20f, w * 0.20f)
        )
        drawLine(
            color = Color(0xFFF5EFE6),
            start = Offset(w * 0.18f, h * 0.30f),
            end = Offset(w * 0.36f, h * 0.30f),
            strokeWidth = w * 0.035f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFFF5EFE6),
            start = Offset(w * 0.64f, h * 0.30f),
            end = Offset(w * 0.82f, h * 0.30f),
            strokeWidth = w * 0.035f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = Color(0xFF568ED6),
            radius = w * 0.030f,
            center = Offset(w * 0.26f, h * 0.30f)
        )
        drawCircle(
            color = Color(0xFFD65656),
            radius = w * 0.030f,
            center = Offset(w * 0.74f, h * 0.30f)
        )
        drawLine(
            color = silver,
            start = Offset(w * 0.50f, h * 0.34f),
            end = Offset(w * 0.37f, h * 0.17f),
            strokeWidth = w * 0.055f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = silver,
            start = Offset(w * 0.50f, h * 0.34f),
            end = Offset(w * 0.63f, h * 0.17f),
            strokeWidth = w * 0.055f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = Color(0xFF5A5C68),
            radius = w * 0.026f,
            center = Offset(w * 0.50f, h * 0.34f)
        )
        drawCircle(
            color = silver,
            radius = w * 0.075f,
            center = Offset(w * 0.42f, h * 0.46f),
            style = Stroke(width = w * 0.045f)
        )
        drawCircle(
            color = silver,
            radius = w * 0.075f,
            center = Offset(w * 0.58f, h * 0.46f),
            style = Stroke(width = w * 0.045f)
        )
        for ((fx, fy) in listOf(0.46f to 0.26f, 0.54f to 0.26f, 0.50f to 0.32f)) {
            drawCircle(
                color = Color(0xFFFFB040),
                radius = w * 0.020f,
                center = Offset(w * fx, h * fy)
            )
        }
        drawCircle(
            color = Color(0xFF25262E),
            radius = w * 0.21f,
            center = Offset(w * 0.50f, h * 0.68f)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.18f),
            radius = w * 0.06f,
            center = Offset(w * 0.43f, h * 0.61f)
        )
        drawCircle(
            color = Color(0xFF5E606E),
            radius = w * 0.045f,
            center = Offset(w * 0.50f, h * 0.48f)
        )
    }
}
