package com.quiddity.app.ui.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quiddity.app.ui.components.QuiddityToggleSwitch
import com.quiddity.app.util.QuiddityConstants
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


// 当前规则：母项总开关统一管控两个子项，子项不可单独关闭；左侧色条+缩进标识从属关系。
@Composable
fun DelaySettingsPanel(
    typingDelayEnabled: Boolean,
    typingDelayMsPerChar: Int,
    sendDelayEnabled: Boolean,
    sendDelaySeconds: Int,
    onTypingDelayEnabledChange: (Boolean) -> Unit,
    onTypingDelayMsPerCharChange: (Int) -> Unit,
    onSendDelayEnabledChange: (Boolean) -> Unit,
    onSendDelaySecondsChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val overallEnabled = typingDelayEnabled || sendDelayEnabled

    // ===== 三条开发规范（位于文件中间位置） =====
    // 1. 问题修复规范：所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
    //    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
    // 2. 代码注释规范：文件内仅允许保留两类注释——当前规则说明注释与模块划分注释（// ===== xxx =====）。
    //    除此之外，禁止出现任何形式的代码注释（包括单行 / 多行 / 临时调试注释）。
    // 3. 构建交付要求：完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK。
    //    APK 需满足：签名有效且符合发布标准、包含完整功能模块、经过基础性能与兼容性测试。

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // ===== 母容器：延迟设置 =====
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // ===== 母设置项：延迟设置（总开关） =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(36.dp)
                                .padding(8.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "延迟设置",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "统一管控打字机效果与发送延迟，关闭则所有延迟功能停用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    QuiddityToggleSwitch(
                        checked = overallEnabled,
                        onCheckedChange = { enabled ->
                            onTypingDelayEnabledChange(enabled)
                            onSendDelayEnabledChange(enabled)
                        }
                    )
                }

                // ===== 母/子分隔线 =====
                Spacer(modifier = Modifier.size(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                                )
                            )
                        )
                )
                Spacer(modifier = Modifier.size(14.dp))

                // ===== 子设置项容器：左侧色条 + 水平缩进 =====
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .width(4.dp)
                            .height(220.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(2.dp)
                                )
                        )
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .weight(1f)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                    Spacer(modifier = Modifier.size(14.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // ===== 子项 1：延迟输出 =====
                        DelaySliderRow(
                            label = "延迟输出（打字机效果）",
                            value = typingDelayMsPerChar.toFloat(),
                            valueRange = QuiddityConstants.MIN_TYPING_DELAY_MS_PER_CHAR.toFloat()..
                                QuiddityConstants.MAX_TYPING_DELAY_MS_PER_CHAR.toFloat(),
                            steps = QuiddityConstants.MAX_TYPING_DELAY_MS_PER_CHAR - 1,
                            enabled = overallEnabled,
                            explanation = "AI 每输出一个字后等待的毫秒数。数值越大，打字机效果越明显" +
                                "（看起来像慢速打字）；数值为 0 则无延迟，AI 输出即时显示。" +
                                "建议设置 10-30ms 以获得自然的真人打字感。",
                            valueFormatter = { "${it}ms" },
                            onCommit = { onTypingDelayMsPerCharChange(it) }
                        )

                        // ===== 子项 2：发送延迟 =====
                        DelaySliderRow(
                            label = "发送延迟",
                            value = sendDelaySeconds.toFloat(),
                            valueRange = QuiddityConstants.MIN_SEND_DELAY_SECONDS.toFloat()..
                                QuiddityConstants.MAX_SEND_DELAY_SECONDS.toFloat(),
                            steps = QuiddityConstants.MAX_SEND_DELAY_SECONDS - 2,
                            enabled = overallEnabled,
                            explanation = "点击发送后，应用等待的秒数。期间若你继续输入，" +
                                "计时会重置，直到你停止输入才真正发出请求。" +
                                "这样能避免连续输入时发出多个请求，节省 Token 消耗。",
                            valueFormatter = { "${it}s" },
                            onCommit = { onSendDelaySecondsChange(it) }
                        )
                    }
                }
            }
        }
    }
}

// 当前规则：Slider 使用本地状态跟随拖动，仅 onValueChangeFinished 时回写 ViewModel，避免每像素触发 DataStore 写入与全面板重组。
@Composable
private fun DelaySliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    enabled: Boolean,
    explanation: String,
    valueFormatter: (Int) -> String,
    onCommit: (Int) -> Unit
) {
    // remember(value)：拖动期间外部 value 不变 → 本地状态不被重置；提交后外部 value 更新 → 本地状态同步对齐，无视觉跳变
    var localValue by remember(value) { mutableFloatStateOf(value) }
    val displayText = valueFormatter(localValue.toInt())

    val labelColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }
    val valueColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    }
    val explanationColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = labelColor
            )
            Text(
                text = displayText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
                modifier = Modifier
                    .background(
                        if (enabled) valueColor.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.06f),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Spacer(modifier = Modifier.size(6.dp))
        Slider(
            value = localValue,
            onValueChange = { localValue = it },
            onValueChangeFinished = { onCommit(localValue.toInt()) },
            valueRange = valueRange,
            steps = steps,
            enabled = enabled
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = explanation,
            style = MaterialTheme.typography.labelSmall,
            color = explanationColor,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
