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

/*
 * ============================================================================
 * 开发规范 (Development Specifications)
 * ============================================================================
 *
 * 1. 问题修复规范
 *    所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
 *    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
 *
 * 2. 代码注释规范
 *    文件内仅允许保留两类注释：
 *    - 当前规则说明注释（即本注释块）
 *    - 模块划分注释（用于标识代码功能模块边界）
 *    除此之外，禁止出现任何形式的代码注释（包括但不限于单行注释、多行注释、临时调试注释等）。
 *
 * 3. 构建交付要求
 *    在完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK 文件。
 *    APK 文件需满足以下条件：
 *    - 签名有效且符合发布标准
 *    - 包含完整的功能模块
 *    - 经过基础性能测试和兼容性测试
 *    以便在真实设备环境中进行功能验证和性能评估。
 *
 * ============================================================================
 */

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
    enabled: Boolean = true,
    onValueChangeFinished: (Double) -> Unit,
    modifier: Modifier = Modifier
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
                text = "采样温度",
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
