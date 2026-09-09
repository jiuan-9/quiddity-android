package com.quiddity.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle

/**
 * 气泡内思考块（应用内本地思考）：
 * - 思考中（流式且正文为空）：tool 同款高亮滑块「思考中…」；
 * - 思考完成：折叠的「思考」行 + 箭头，点击展开思考文本。
 */
@Composable
fun ThinkingBlock(
    thinking: String,
    isStreaming: Boolean,
    contentEmpty: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    if (isStreaming && contentEmpty) {
        ShimmerHighlightText(
            text = "思考中…",
            icon = Icons.Filled.AutoAwesome,
            modifier = modifier
        )
        return
    }
    // 思考完成：正文内仅保留灰色斜体的「已思考」标记（像工具调用一样），不展示思考全文。
    Text(
        text = "· 已思考",
        style = MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic),
        color = colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        modifier = modifier
    )
}
