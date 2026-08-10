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

    /**
     * 行内 Markdown 渲染结果：显示文本 + 样式区间（坐标均基于 [text]）。
     *
     * 与 [parse] 的分工：本函数只处理文本块内部的行内语法
     * （标题 / 引用 / 列表标记 / 加粗 / 斜体 / 删除线 / 行内代码 / 链接），
     * 围栏代码块仍由 [parse] 拆出后单独渲染。
     */
    data class ParsedMarkdown(
        val text: String,
        val spans: List<MarkdownSpan>
    )

    sealed class MarkdownSpan {
        abstract val start: Int
        abstract val end: Int

        /** **加粗** */
        data class Bold(override val start: Int, override val end: Int) : MarkdownSpan()

        /** *斜体* */
        data class Italic(override val start: Int, override val end: Int) : MarkdownSpan()

        /** ~~删除线~~ */
        data class Strikethrough(override val start: Int, override val end: Int) : MarkdownSpan()

        /** `行内代码` */
        data class Code(override val start: Int, override val end: Int) : MarkdownSpan()

        /** [label](url)，start/end 覆盖最终文本中的 label */
        data class Link(override val start: Int, override val end: Int, val url: String) : MarkdownSpan()

        /** # 标题，level 1..6 */
        data class Heading(override val start: Int, override val end: Int, val level: Int) : MarkdownSpan()

        /** > 引用 */
        data class Quote(override val start: Int, override val end: Int) : MarkdownSpan()

        /** 列表标记（-、*、+ 已替换为 •，有序列表保留数字） */
        data class Bullet(override val start: Int, override val end: Int) : MarkdownSpan()
    }

    /**
     * 解析行内 Markdown，返回最终显示文本与样式区间。
     *
     * 规则：
     * - 标题 `# 文本`：隐藏井号，标题加粗并按级别放大字号；
     * - 引用 `> 文本`：隐藏标记，斜体 + 弱化颜色；
     * - 无序列表 `- / * / + 文本`：标记替换为 `•`；有序列表 `1. 文本` 保留数字；
     * - 加粗 `**文本**`、斜体 `*文本*`、删除线 `~~文本~~`、行内代码 `` `文本` ``；
     * - 链接 `[label](url)`：隐藏语法，只显示 label；
     * - 解析过程中不做字符转义，文本长度可变化（区间已换算到最终文本坐标）。
     */
    fun parseMarkdown(content: String): ParsedMarkdown {
        if (content.isEmpty()) return ParsedMarkdown("", emptyList())

        val edits = mutableListOf<Edit>()
        val rawSpans = mutableListOf<RawSpan>()
        val protected = mutableListOf<Pair<Int, Int>>()

        // 1) 行级结构：链接、标题、引用、列表标记
        var lineStart = 0
        while (lineStart <= content.length) {
            val newlineIdx = content.indexOf('\n', lineStart)
            val lineEnd = if (newlineIdx == -1) content.length else newlineIdx
            collectStructuralSpans(content, lineStart, lineEnd, edits, rawSpans, protected)
            if (newlineIdx == -1) break
            lineStart = newlineIdx + 1
        }

        // 2) 行内样式：行内代码、加粗、删除线、斜体（跳过与结构区间重叠的部分）
        collectInlineEdits(content, edits, rawSpans, protected)

        // 3) 应用所有编辑，得到最终文本
        edits.sortBy { it.start }
        val mergedEdits = mutableListOf<Edit>()
        for (edit in edits) {
            if (mergedEdits.isNotEmpty() && edit.start < mergedEdits.last().end) continue
            mergedEdits.add(edit)
        }

        val finalText = buildString {
            var cursor = 0
            for (edit in mergedEdits) {
                append(content, cursor, edit.start)
                append(edit.replacement)
                cursor = edit.end
            }
            append(content, cursor, content.length)
        }

        fun toFinalBefore(raw: Int): Int {
            var shift = 0
            for (edit in mergedEdits) {
                if (edit.end <= raw) {
                    shift += edit.replacement.length - (edit.end - edit.start)
                } else {
                    break
                }
            }
            return raw + shift
        }

        // 4) 原始区间换算到最终文本坐标：
        //    - 内联样式区间整体落在对应编辑内部，最终从编辑起点开始、长度不变；
        //    - 结构区间（标题/引用/列表）在编辑外部，按累计位移映射。
        val spans = rawSpans.map { raw ->
            val covering = mergedEdits.firstOrNull { it.start <= raw.start && it.end >= raw.end }
            val start = if (covering != null) {
                toFinalBefore(covering.start)
            } else {
                toFinalBefore(raw.start)
            }
            val end = if (covering != null) {
                // 被编辑整体覆盖的区间（如链接替换），其最终文本长度等于替换文本长度
                start + covering.replacement.length
            } else {
                start + (raw.end - raw.start)
            }
            when (raw) {
                is RawSpan.Heading ->
                    MarkdownSpan.Heading(start, end, raw.level)
                is RawSpan.Quote ->
                    MarkdownSpan.Quote(start, end)
                is RawSpan.Bullet ->
                    MarkdownSpan.Bullet(start, end)
                is RawSpan.Link ->
                    MarkdownSpan.Link(start, end, raw.url)
                is RawSpan.Bold ->
                    MarkdownSpan.Bold(start, end)
                is RawSpan.Italic ->
                    MarkdownSpan.Italic(start, end)
                is RawSpan.Strikethrough ->
                    MarkdownSpan.Strikethrough(start, end)
                is RawSpan.Code ->
                    MarkdownSpan.Code(start, end)
            }
        }
        return ParsedMarkdown(finalText, spans)
    }

    private data class Edit(val start: Int, val end: Int, val replacement: String)

    private sealed class RawSpan {
        abstract val start: Int
        abstract val end: Int

        data class Heading(override val start: Int, override val end: Int, val level: Int) : RawSpan()
        data class Quote(override val start: Int, override val end: Int) : RawSpan()
        data class Bullet(override val start: Int, override val end: Int) : RawSpan()
        data class Link(override val start: Int, override val end: Int, val url: String) : RawSpan()
        data class Bold(override val start: Int, override val end: Int) : RawSpan()
        data class Italic(override val start: Int, override val end: Int) : RawSpan()
        data class Strikethrough(override val start: Int, override val end: Int) : RawSpan()
        data class Code(override val start: Int, override val end: Int) : RawSpan()
    }

    private fun collectStructuralSpans(
        content: String,
        lineStart: Int,
        lineEnd: Int,
        edits: MutableList<Edit>,
        rawSpans: MutableList<RawSpan>,
        protected: MutableList<Pair<Int, Int>>
    ) {
        val line = content.substring(lineStart, lineEnd)
        if (line.isEmpty()) return

        LINK_PATTERN.findAll(line).forEach { match ->
            val label = match.groupValues[1]
            val url = match.groupValues[2]
            val linkStart = lineStart + match.range.first
            val linkEnd = lineStart + match.range.last + 1
            edits.add(Edit(linkStart, linkEnd, label))
            rawSpans.add(RawSpan.Link(linkStart + 1, linkStart + 1 + label.length, url))
            protected.add(linkStart to linkEnd)
        }

        val heading = HEADING_PATTERN.matchEntire(line)
        if (heading != null) {
            val leading = heading.groupValues[1].length
            val hashes = heading.groupValues[2].length
            val space = heading.groupValues[3].length
            val markerEnd = lineStart + leading + hashes + space
            edits.add(Edit(lineStart + leading, markerEnd, ""))
            rawSpans.add(RawSpan.Heading(markerEnd, lineStart + line.length, hashes))
            protected.add(lineStart + leading to markerEnd)
            return
        }

        val quote = QUOTE_PATTERN.matchEntire(line)
        if (quote != null) {
            val leading = quote.groupValues[1].length
            val markerLen = 1 + quote.groupValues[2].length
            val markerEnd = lineStart + leading + markerLen
            edits.add(Edit(lineStart + leading, markerEnd, ""))
            rawSpans.add(RawSpan.Quote(markerEnd, lineStart + line.length))
            protected.add(lineStart + leading to markerEnd)
            return
        }

        val list = LIST_PATTERN.matchEntire(line)
        if (list != null) {
            val leading = list.groupValues[1].length
            val marker = list.groupValues[2]
            if (marker.length == 1 && marker[0] in BULLET_CHARS) {
                edits.add(Edit(lineStart + leading, lineStart + leading + 1, "•"))
                rawSpans.add(RawSpan.Bullet(lineStart + leading, lineStart + leading + 1))
                protected.add(lineStart + leading to lineStart + leading + 1)
            } else {
                rawSpans.add(RawSpan.Bullet(lineStart + leading, lineStart + leading + marker.length))
            }
        }
    }

    private fun collectInlineEdits(
        content: String,
        edits: MutableList<Edit>,
        rawSpans: MutableList<RawSpan>,
        protected: MutableList<Pair<Int, Int>>
    ) {
        fun overlaps(range: IntRange): Boolean =
            protected.any { it.first < range.last + 1 && range.first < it.second }

        fun addEdit(
            range: IntRange,
            inner: String,
            span: (Int, Int) -> RawSpan
        ) {
            edits.add(Edit(range.first, range.last + 1, inner))
            rawSpans.add(span(range.first + 1, range.first + 1 + inner.length))
            protected.add(range.first to range.last + 1)
        }

        CODE_PATTERN.findAll(content).forEach { match ->
            val range = match.range
            if (!overlaps(range)) {
                addEdit(range, match.groupValues[1]) { s, e -> RawSpan.Code(s, e) }
            }
        }
        BOLD_PATTERN.findAll(content).forEach { match ->
            val range = match.range
            if (!overlaps(range)) {
                addEdit(range, match.groupValues[1]) { s, e -> RawSpan.Bold(s, e) }
            }
        }
        STRIKE_PATTERN.findAll(content).forEach { match ->
            val range = match.range
            if (!overlaps(range)) {
                addEdit(range, match.groupValues[1]) { s, e -> RawSpan.Strikethrough(s, e) }
            }
        }
        ITALIC_PATTERN.findAll(content).forEach { match ->
            val inner = match.groupValues[1]
            if (inner.isBlank() || inner.startsWith(' ') || inner.endsWith(' ')) return@forEach
            val range = match.range
            if (!overlaps(range)) {
                addEdit(range, inner) { s, e -> RawSpan.Italic(s, e) }
            }
        }
    }

    private val LINK_PATTERN = Regex("\\[([^\\]\\n]+)]\\(([^)\\s]+)\\)")
    private val HEADING_PATTERN = Regex("^(\\s{0,3})(#{1,6})([ \\t]+)(.*)$")
    private val QUOTE_PATTERN = Regex("^(\\s{0,3})>([ \\t]?)(.*)$")
    private val LIST_PATTERN = Regex("^(\\s{0,3})([-*+]|\\d{1,3}[.)])([ \\t]+)(.*)$")
    private val CODE_PATTERN = Regex("`([^`\\n]+)`")
    private val BOLD_PATTERN = Regex("\\*\\*([^*\\n]+)\\*\\*")
    private val STRIKE_PATTERN = Regex("~~([^~\\n]+)~~")
    private val ITALIC_PATTERN = Regex("(?<![A-Za-z0-9*])\\*([^*\\n]+)\\*(?![A-Za-z0-9*])")
    private val BULLET_CHARS = charArrayOf('-', '*', '+')
}
