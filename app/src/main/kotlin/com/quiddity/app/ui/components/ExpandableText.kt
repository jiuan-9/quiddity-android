package com.quiddity.app.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 可展开/收起的文本。
 *
 * 当文本超过 [maxCollapsedLines] 行时，右下角显示"展开"按钮；
 * 点击后显示全部内容并变为"收起"。用于设置项摘要、模型配置卡片等
 * 内容可能很长的场景，避免一次性占据过多屏幕空间。
 *
 * @param text 显示文本
 * @param style 文本样式
 * @param maxCollapsedLines 折叠时最大行数
 * @param modifier 外部 modifier
 */
@Composable
fun ExpandableText(
    text: String,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    maxCollapsedLines: Int = 2,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var canExpand by remember { mutableStateOf(false) }

    Column(modifier = modifier.animateContentSize()) {
        Text(
            text = text,
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            maxLines = if (expanded) Int.MAX_VALUE else maxCollapsedLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                // 仅在折叠状态测量是否溢出，避免展开后 hasVisualOverflow 变化导致按钮消失
                if (!expanded) {
                    canExpand = result.hasVisualOverflow
                }
            }
        )
        if (expanded || canExpand) {
            Text(
                text = if (expanded) "收起" else "展开",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { expanded = !expanded }
            )
        }
    }
}
