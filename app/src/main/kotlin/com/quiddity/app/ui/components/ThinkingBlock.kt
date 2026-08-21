package com.quiddity.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle

/*
 * ============================================================================
 * 开发规范 (Development Specifications)
 * ============================================================================
 *
 * 1. 问题修复规范
 *    所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
 *    修复内容需完全融入现有代码架构，保证代码逻辑的连贯性、可维护性和可扩展性。
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
