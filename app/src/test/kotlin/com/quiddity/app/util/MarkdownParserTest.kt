package com.quiddity.app.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownParserTest {

    private fun parse(content: String) = MarkdownParser.parseMarkdown(content)

    @Test
    fun `plain text is unchanged without spans`() {
        val result = parse("普通文本")
        assertEquals("普通文本", result.text)
        assertTrue(result.spans.isEmpty())
    }

    @Test
    fun `empty content yields empty result`() {
        val result = parse("")
        assertEquals("", result.text)
        assertTrue(result.spans.isEmpty())
    }

    @Test
    fun `bold removes markers and spans inner text`() {
        val result = parse("**加粗**")
        assertEquals("加粗", result.text)
        val span = result.spans.single() as MarkdownParser.MarkdownSpan.Bold
        assertEquals(0, span.start)
        assertEquals(2, span.end)
    }

    @Test
    fun `bold inside plain text keeps offsets`() {
        val result = parse("普通**加粗**普通")
        assertEquals("普通加粗普通", result.text)
        val span = result.spans.single() as MarkdownParser.MarkdownSpan.Bold
        assertEquals(2, span.start)
        assertEquals(4, span.end)
    }

    @Test
    fun `italic removes markers`() {
        val result = parse("*斜体*")
        assertEquals("斜体", result.text)
        val span = result.spans.single() as MarkdownParser.MarkdownSpan.Italic
        assertEquals(0, span.start)
        assertEquals(2, span.end)
    }

    @Test
    fun `math asterisks are not treated as italic`() {
        val result = parse("5 * 3 * 2")
        assertEquals("5 * 3 * 2", result.text)
        assertTrue(result.spans.isEmpty())
    }

    @Test
    fun `inline code removes backticks`() {
        val result = parse("`code`")
        assertEquals("code", result.text)
        val span = result.spans.single() as MarkdownParser.MarkdownSpan.Code
        assertEquals(0, span.start)
        assertEquals(4, span.end)
    }

    @Test
    fun `inline code protects inner markdown`() {
        val result = parse("`*x*`")
        assertEquals("*x*", result.text)
        assertTrue(result.spans.single() is MarkdownParser.MarkdownSpan.Code)
    }

    @Test
    fun `strikethrough removes markers`() {
        val result = parse("~~删除~~")
        assertEquals("删除", result.text)
        val span = result.spans.single() as MarkdownParser.MarkdownSpan.Strikethrough
        assertEquals(0, span.start)
        assertEquals(2, span.end)
    }

    @Test
    fun `link hides syntax and keeps url`() {
        val result = parse("[谷歌](https://google.com)")
        assertEquals("谷歌", result.text)
        val span = result.spans.single() as MarkdownParser.MarkdownSpan.Link
        assertEquals(0, span.start)
        assertEquals(2, span.end)
        assertEquals("https://google.com", span.url)
    }

    @Test
    fun `heading hides hashes and keeps level`() {
        val result = parse("# 标题")
        assertEquals("标题", result.text)
        val span = result.spans.single() as MarkdownParser.MarkdownSpan.Heading
        assertEquals(0, span.start)
        assertEquals(2, span.end)
        assertEquals(1, span.level)
    }

    @Test
    fun `heading level three`() {
        val result = parse("### 三级标题")
        assertEquals("三级标题", result.text)
        val span = result.spans.single() as MarkdownParser.MarkdownSpan.Heading
        assertEquals(3, span.level)
    }

    @Test
    fun `hashtag without space is not a heading`() {
        val result = parse("#标签")
        assertEquals("#标签", result.text)
        assertTrue(result.spans.isEmpty())
    }

    @Test
    fun `quote hides marker`() {
        val result = parse("> 引用")
        assertEquals("引用", result.text)
        val span = result.spans.single() as MarkdownParser.MarkdownSpan.Quote
        assertEquals(0, span.start)
        assertEquals(2, span.end)
    }

    @Test
    fun `unordered list replaces dash with bullet`() {
        val result = parse("- 项目")
        assertEquals("• 项目", result.text)
        val span = result.spans.single() as MarkdownParser.MarkdownSpan.Bullet
        assertEquals(0, span.start)
        assertEquals(1, span.end)
    }

    @Test
    fun `ordered list keeps digits and marks bullet range`() {
        val result = parse("1. 项目")
        assertEquals("1. 项目", result.text)
        val span = result.spans.single() as MarkdownParser.MarkdownSpan.Bullet
        assertEquals(0, span.start)
        assertEquals(2, span.end)
    }

    @Test
    fun `mixed content renders all styles with correct offsets`() {
        val result = parse("# 标题\n\n**加粗** 与 *斜体* 和 `代码` 以及 ~~删除~~")
        assertEquals("标题\n\n加粗 与 斜体 和 代码 以及 删除", result.text)
        val heading = result.spans.filterIsInstance<MarkdownParser.MarkdownSpan.Heading>().single()
        assertEquals(0, heading.start)
        assertEquals(2, heading.end)
        val bold = result.spans.filterIsInstance<MarkdownParser.MarkdownSpan.Bold>().single()
        assertEquals(4, bold.start)
        assertEquals(6, bold.end)
        val italic = result.spans.filterIsInstance<MarkdownParser.MarkdownSpan.Italic>().single()
        assertEquals(9, italic.start)
        assertEquals(11, italic.end)
        val code = result.spans.filterIsInstance<MarkdownParser.MarkdownSpan.Code>().single()
        assertEquals(14, code.start)
        assertEquals(16, code.end)
        val strike = result.spans.filterIsInstance<MarkdownParser.MarkdownSpan.Strikethrough>().single()
        assertEquals(20, strike.start)
        assertEquals(22, strike.end)
    }

    @Test
    fun `link inside heading maps both spans`() {
        val result = parse("# [链接](https://x.com)")
        assertEquals("链接", result.text)
        val heading = result.spans.filterIsInstance<MarkdownParser.MarkdownSpan.Heading>().single()
        assertEquals(0, heading.start)
        assertEquals(2, heading.end)
        val link = result.spans.filterIsInstance<MarkdownParser.MarkdownSpan.Link>().single()
        assertEquals(0, link.start)
        assertEquals(2, link.end)
        assertEquals("https://x.com", link.url)
    }

    @Test
    fun `quote with link maps both spans`() {
        val result = parse("> [a](https://b.com)")
        assertEquals("a", result.text)
        assertTrue(result.spans.any { it is MarkdownParser.MarkdownSpan.Quote })
        val link = result.spans.filterIsInstance<MarkdownParser.MarkdownSpan.Link>().single()
        assertEquals(0, link.start)
        assertEquals(1, link.end)
        assertEquals("https://b.com", link.url)
    }

    @Test
    fun `multi-line offsets stay correct`() {
        val result = parse("第一行\n# 标题\n第二行")
        assertEquals("第一行\n标题\n第二行", result.text)
        val span = result.spans.single() as MarkdownParser.MarkdownSpan.Heading
        assertEquals(4, span.start)
        assertEquals(6, span.end)
    }

    @Test
    fun `fenced code block still parsed by parse`() {
        val blocks = MarkdownParser.parse("```kotlin\nval x = 1\n```")
        assertEquals(1, blocks.size)
        val code = blocks.single() as MarkdownParser.Block.CodeBlock
        assertEquals("kotlin", code.language)
        assertEquals("val x = 1", code.code)
    }
}
