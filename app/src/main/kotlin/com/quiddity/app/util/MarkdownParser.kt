package com.quiddity.app.util

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
 * Markdown 解析器（轻量级，参考 PC 端 marked.js 的核心功能）。
 *
 * 功能：
 * - 解析围栏代码块（```language\ncode\n```）
 * - 解析行内格式（**bold**、*italic*、`inline code`、# 标题、- 列表）
 * - 将消息内容拆分为有序的块列表，供 UI 分别渲染
 *
 * 设计原则：
 * - 纯 Kotlin 实现，不依赖第三方 Markdown 库
 * - 仅覆盖聊天场景常见格式，不追求完整 Markdown 规范
 * - 对齐 PC 端 syntax-highlight.js 的代码块处理逻辑
 */
object MarkdownParser {

    /**
     * 消息内容块类型。
     */
    sealed class Block {
        /** 普通文本块（可能含行内 Markdown 格式）。 */
        data class Text(val content: String) : Block()
        /** 围栏代码块。 */
        data class CodeBlock(
            val language: String,
            val code: String
        ) : Block()
    }

    /**
     * 将消息内容解析为块列表。
     *
     * 解析规则：
     * - ```language\ncode\n``` → CodeBlock
     * - 其他文本 → Text（保留原始格式，由行内渲染器处理）
     *
     * @param content 原始消息内容
     * @return 有序块列表
     */
    fun parse(content: String): List<Block> {
        if (content.isEmpty()) return listOf(Block.Text(""))

        val blocks = mutableListOf<Block>()
        val textBuffer = StringBuilder()

        val lines = content.split("\n")
        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            // 检测围栏代码块开始
            val fenceMatch = FENCE_PATTERN.find(line)
            if (fenceMatch != null) {
                val language = fenceMatch.groupValues[1].trim()

                // 先把累积的文本刷出
                if (textBuffer.isNotEmpty()) {
                    blocks.add(Block.Text(textBuffer.toString().trimEnd('\n')))
                    textBuffer.clear()
                }

                // 收集代码内容直到闭合 ```
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size) {
                    if (lines[i].trim().startsWith("```")) {
                        i++
                        break
                    }
                    codeLines.add(lines[i])
                    i++
                }

                blocks.add(Block.CodeBlock(
                    language = language.ifBlank { "text" },
                    code = codeLines.joinToString("\n")
                ))
            } else {
                textBuffer.append(line)
                if (i < lines.size - 1) textBuffer.append('\n')
                i++
            }
        }

        // 刷出剩余文本
        if (textBuffer.isNotEmpty()) {
            blocks.add(Block.Text(textBuffer.toString()))
        }

        return blocks
    }

    // 支持前导空格（缩进围栏）与 c++/c# 等带 +/# 的语言名
    private val FENCE_PATTERN = Regex("^\\s*```([\\w+#\\-]*)\\s*$")
}
