package com.quiddity.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * 设置类"玻璃卡片"统一配色（毛玻璃层级区分）。
 *
 * 层级设计：
 * - 外层面板（设置底部面板 / 汉堡菜单本体）：接近不透明（0.82～0.86），中性 surface；
 * - 内层卡片（分组 / 设置项）：明显更透（alpha 0.30），且混入极淡主题色，
 *   与外面面板在透明度和色相上都形成区分，避免糊成一片。
 */
@Composable
fun glassCardColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return lerp(scheme.surfaceContainerHigh, scheme.primary, 0.06f)
        .copy(alpha = 0.30f)
}

/**
 * 玻璃卡片描边：比常规卡片边框更淡，保持边界清晰的同时更通透。
 */
@Composable
fun glassCardBorderColor(): Color =
    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)
