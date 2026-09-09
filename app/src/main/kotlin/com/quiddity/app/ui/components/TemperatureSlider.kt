package com.quiddity.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.quiddity.app.util.QuiddityConstants
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 采样温度滑杆（0～2，0.1 一档，DeepSeek 官方取值范围）。
 *
 * 拖动时本地状态实时跟随，松手后按 0.1 离散步进并通过 [onValueChangeFinished] 落盘，
 * 避免拖动过程频繁写入存储。外部值变化（如预设按钮写入）时自动重置滑块位置。
 *
 * @param value 当前生效温度（全局默认或会话覆盖）
 * @param enabled false 时整行禁用（如思考模式下温度不生效的提示场景）
 * @param onValueChangeFinished 松手回调（已按 0.1 离散步进）
 */
@Composable
fun TemperatureSlider(
    value: Double,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (Double) -> Unit,
) {
    var sliderValue by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val display = String.format(Locale.US, "%.1f", sliderValue.toDouble())

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "温度",
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
            Text(
                text = display,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                }
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                val stepped = (sliderValue * 10f).roundToInt() / 10f
                sliderValue = stepped
                onValueChangeFinished(stepped.toDouble())
            },
            valueRange = QuiddityConstants.MIN_TEMPERATURE.toFloat()..QuiddityConstants.MAX_TEMPERATURE.toFloat(),
            steps = 19,
            enabled = enabled
        )
    }
}