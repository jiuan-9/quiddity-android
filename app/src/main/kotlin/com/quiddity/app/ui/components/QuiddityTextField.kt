package com.quiddity.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.quiddity.app.ui.theme.Motion

/**
 * 标签独立一行的全宽输入框。
 *
 * 功能：
 * - [showClearButton]：输入框有内容时显示 × 清除按钮（仅图标无外框）
 * - [collapsible]：多行字段内容超过阈值行数时显示"展开/收起"按钮，默认启用
 * - 折叠状态用 rememberSaveable(label) 保存，不依赖 value，旋转屏后保持
 */
@Composable
fun QuiddityTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    showClearButton: Boolean = true,
    helpTooltipText: String? = null,
    onHelpClick: (() -> Unit)? = null,
    collapsible: Boolean = true,
    collapsedMaxLines: Int = 3
) {
    // 折叠状态：key 仅用 label，避免输入时状态被重置
    var isCollapsed by rememberSaveable(label) { mutableStateOf(true) }
    // 单行字段强制禁用折叠
    val effectiveCollapsible = collapsible && !singleLine
    // 通过换行符估算行数
    val actualLineCount = if (effectiveCollapsible && value.isNotEmpty()) {
        value.count { it == '\n' } + 1
    } else 0
    val canCollapse = effectiveCollapsible && actualLineCount > collapsedMaxLines

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
                fontWeight = FontWeight.Medium
            )
            if (onHelpClick != null) {
                Spacer(modifier = Modifier.size(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = "帮助",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onHelpClick
                        )
                )
            } else if (helpTooltipText != null) {
                Spacer(modifier = Modifier.size(4.dp))
                HelpTooltip(text = helpTooltipText, iconSize = 18)
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = if (placeholder.isNotEmpty()) {
                { Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) }
            } else null,
            singleLine = singleLine,
            enabled = enabled,
            isError = isError,
            maxLines = if (effectiveCollapsible && isCollapsed) collapsedMaxLines else Int.MAX_VALUE,
            minLines = if (effectiveCollapsible && isCollapsed) collapsedMaxLines else 1,
            supportingText = if (supportingText != null) {
                { Text(supportingText, style = MaterialTheme.typography.bodySmall) }
            } else null,
            shape = RoundedCornerShape(12.dp),
            trailingIcon = if (showClearButton && value.isNotEmpty() && enabled) {
                {
                    Icon(
                        imageVector = Icons.Filled.Cancel,
                        contentDescription = "清除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onValueChange("") }
                    )
                }
            } else null,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )

        // 折叠/展开按钮
        AnimatedVisibility(
            visible = canCollapse,
            enter = fadeIn(tween(Motion.DurationShort)),
            exit = fadeOut(tween(Motion.DurationShort))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { isCollapsed = !isCollapsed }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isCollapsed) "展开 (${actualLineCount} 行)" else "收起",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = if (isCollapsed) Icons.Filled.ExpandMore
                        else Icons.Filled.ExpandLess,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
