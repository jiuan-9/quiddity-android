package com.quiddity.app.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quiddity.app.ui.components.QuiddityToggleSwitch
import com.quiddity.app.ui.components.TemperatureSlider
import com.quiddity.app.util.QuiddityConstants


/**
 * 带开关的设置行。
 *
 * 与 [MenuRow] 不同，本组件右侧显示一个带动画的 Toggle Switch，
 * 用于需要明确展示"开/关"状态并可直接切换的项（如深色模式）。
 * 点击整行或拖动开关均可触发 [onCheckedChange]。
 */
@Composable
internal fun ToggleMenuRow(
    title: String,
    subtitle: String = "",
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    // Box 替代 Surface：行内无 elevation 需求，Box+background+clip 跳过 Surface 的 CompositionLocalProvider 开销
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onCheckedChange(!checked) }
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        }
                    )
                }
            }
            QuiddityToggleSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

/**
 * 会话级采样温度编辑面板：预设快捷档 + 0～2 滑杆 + 跟随默认重置。
 * 由 [ExpandableMenuGroup] 承载，不再自带外层卡片，避免双重边框。
 */
@Composable
internal fun TemperatureEditorPanel(
    current: Double?,
    globalDefault: Double,
    onTemperatureChange: (Double?) -> Unit
) {
    val effective = current ?: globalDefault
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "本会话采样温度",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            if (current != null) {
                TextButton(onClick = { onTemperatureChange(null) }) {
                    Text("跟随默认")
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            temperaturePresets.forEach { (value, label) ->
                TemperaturePresetChip(
                    value = value,
                    label = label,
                    selected = kotlin.math.abs(effective - value) < 0.001,
                    onClick = { onTemperatureChange(value) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        TemperatureSlider(
            value = effective,
            onValueChangeFinished = { onTemperatureChange(it) }
        )
        Text(
            text = "控制本会话回复的随机与创造性：越高越发散，越低越稳定。\n" +
                "部分模型最高只支持 1.0，超出会自动收敛。\n" +
                "场景建议：0.0 严谨 · 1.0 均衡 · 1.3 对话 · 1.5 创意",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/** 官方场景建议档位（值 → 展示标签）。 */
private val temperaturePresets = listOf(
    0.0 to "0.0 严谨",
    1.0 to "1.0 均衡",
    1.3 to "1.3 对话",
    1.5 to "1.5 创意"
)

@Composable
private fun TemperaturePresetChip(
    value: Double,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

/**
 * DeepSeek 官方服务端联网搜索开关行。
 * 仅官方服务商 + 支持模型可用（由 [supported] 判定）；不支持时整行禁用并说明原因。
 */
@Composable
internal fun WebSearchMenuRow(
    enabled: Boolean,
    supported: Boolean,
    currentModelId: String,
    onWebSearchChange: (Boolean) -> Unit
) {
    ToggleMenuRow(
        title = "官方联网搜索",
        subtitle = if (supported) {
            if (enabled) {
                "已开启：AI 会先联网搜索最新信息再回复（服务端搜索）"
            } else {
                "开启后 AI 会先联网搜索最新信息再回复"
            }
        } else {
            "需要 DeepSeek 官方服务商 + " + QuiddityConstants.DEEPSEEK_RESPONSES_MODEL +
                "；当前配置（$currentModelId）无法使用"
        },
        checked = enabled,
        enabled = supported,
        onCheckedChange = { onWebSearchChange(it) }
    )
}

/**
 * DeepSeek 思考开关行 + 思考深度选择（浅 / 深）。
 * 仅 DeepSeek 官方模型可用（由 [supported] 判定）；不支持时整行禁用。
 */
@Composable
internal fun ThinkingMenuRow(
    enabled: Boolean,
    supported: Boolean,
    depth: String,
    onThinkingChange: (Boolean) -> Unit,
    onDepthChange: (String) -> Unit
) {
    ToggleMenuRow(
        title = "思考",
        subtitle = if (supported) {
            if (enabled) {
                "回复前先思考，内容单独显示"
            } else {
                "回复前先思考（默认关闭）"
            }
        } else {
            "仅 DeepSeek 官方模型支持"
        },
        checked = enabled,
        enabled = supported,
        onCheckedChange = onThinkingChange
    )
    if (enabled && supported) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "思考深度",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f)
            )
            ThinkingDepthChip(
                label = "浅（默认）",
                selected = depth != QuiddityConstants.THINKING_DEPTH_DEEP,
                onClick = { onDepthChange(QuiddityConstants.THINKING_DEPTH_SHALLOW) }
            )
            Spacer(modifier = Modifier.size(6.dp))
            ThinkingDepthChip(
                label = "深",
                selected = depth == QuiddityConstants.THINKING_DEPTH_DEEP,
                onClick = { onDepthChange(QuiddityConstants.THINKING_DEPTH_DEEP) }
            )
        }
    }
}

@Composable
private fun ThinkingDepthChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            com.quiddity.app.ui.components.glassCardColor().copy(alpha = 0.4f)
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            }
        ),
        onClick = onClick
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/**
 * 导出/导入卡片：标题、说明文字、以及「导出」「导入」两个按钮。
 *
 */
@Composable
internal fun ExportImportCard(
    title: String,
    subtitle: String,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    // Box 替代 Surface：无 elevation 需求，Box+background+clip 跳过 Surface 开销
    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onExport,
                    modifier = Modifier.weight(1f)
                ) { Text("导出") }
                TextButton(
                    onClick = onImport,
                    modifier = Modifier.weight(1f)
                ) { Text("导入") }
            }
        }
    }
}
