package com.quiddity.app.domain

import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatRecordSearchTest {

    private fun msg(id: String, role: Role, content: String, ts: Long): Message =
        Message(id = id, conversationId = "c1", role = role, content = content, timestamp = ts)

    private val messages = listOf(
        msg("1", Role.USER, "我们计划下个月去成都旅行", 1_000L),
        msg("2", Role.ASSISTANT, "好的，记得带上相机", 2_000L),
        msg("3", Role.USER, "预算大概五千", 3_000L),
        msg("4", Role.ASSISTANT, "五千足够，建议提前订票", 4_000L)
    )

    @Test
    fun `finds messages matching query`() {
        val result = ChatRecordSearch.search(messages, "成都")
        assertTrue(result.found)
        assertTrue(result.content.contains("成都"))
        assertTrue(result.content.contains("用户"))
        assertTrue(result.content.contains("（"))
    }

    @Test
    fun `returns not found when nothing matches`() {
        val result = ChatRecordSearch.search(messages, "航空母舰")
        assertFalse(result.found)
        assertEquals(ChatRecordSearch.NOT_FOUND_TEXT, result.content)
    }

    @Test
    fun `returns recent messages when query is blank`() {
        val result = ChatRecordSearch.search(messages, "  ")
        assertTrue(result.found)
        assertTrue(result.content.contains("提前订票"))
    }

    @Test
    fun `returns not found when list is empty`() {
        assertFalse(ChatRecordSearch.search(emptyList(), "成都").found)
    }

    @Test
    fun `caps result count`() {
        val many = (1..20).map { msg("m$it", Role.USER, "内容包含关键词：旅行 $it", it * 1_000L) }
        val result = ChatRecordSearch.search(many, "旅行")
        val count = Regex("""\[用户\]""").findAll(result.content).count()
        assertTrue(count <= ChatRecordSearch.MAX_RESULTS)
    }

    @Test
    fun `searchResults returns ordered matches for list UI`() {
        val hits = ChatRecordSearch.searchResults(messages, "五千")
        assertEquals(listOf("3", "4"), hits.map { it.id })
        assertTrue(ChatRecordSearch.searchResults(messages, "").isEmpty())
        assertTrue(ChatRecordSearch.searchResults(emptyList(), "成都").isEmpty())
    }

    @Test
    fun `buildExcerpt centers keyword in window for long message`() {
        val content = "A".repeat(200) + "关键词" + "B".repeat(200)
        val excerpt = ChatRecordSearch.buildExcerpt(content, "关键词", maxChars = 80)!!
        assertTrue(excerpt.text.startsWith("…"), "长消息应带前省略号")
        assertTrue(excerpt.text.endsWith("…"), "长消息应带后省略号")
        assertEquals(82, excerpt.text.length)
        val hit = excerpt.highlights.first()
        assertTrue(hit.first in 30..50, "关键词应位于窗口中间附近，实际位置=${hit.first}")
        assertEquals("关键词", excerpt.text.substring(hit))
    }

    @Test
    fun `buildExcerpt keeps keyword at end visible`() {
        val content = "A".repeat(150) + "目标词"
        val excerpt = ChatRecordSearch.buildExcerpt(content, "目标词", maxChars = 80)!!
        assertTrue(excerpt.text.startsWith("…"), "关键词在末尾时前面应截断")
        assertTrue(excerpt.text.endsWith("目标词"), "关键词在末尾时窗口应收敛到末尾且不加尾省略号")
        assertEquals(81, excerpt.text.length)
        assertEquals("目标词", excerpt.text.substring(excerpt.highlights.first()))
    }

    @Test
    fun `buildExcerpt keeps keyword at start visible`() {
        val content = "目标词" + "B".repeat(150)
        val excerpt = ChatRecordSearch.buildExcerpt(content, "目标词", maxChars = 80)!!
        assertTrue(excerpt.text.startsWith("目标词"), "关键词在开头时窗口应收敛到开头且不加前省略号")
        assertTrue(excerpt.text.endsWith("…"), "关键词在开头时后面应截断")
        assertEquals(81, excerpt.text.length)
        assertEquals("目标词", excerpt.text.substring(excerpt.highlights.first()))
    }

    @Test
    fun `buildExcerpt matches keyword case-insensitively`() {
        val content = "Hello World " + "x".repeat(100)
        val excerpt = ChatRecordSearch.buildExcerpt(content, "WORLD", maxChars = 80)!!
        // 摘要保留原文大小写，仅匹配时忽略大小写
        assertEquals("World", excerpt.text.substring(excerpt.highlights.first()))
    }

    @Test
    fun `buildExcerpt highlights all terms and merges overlaps`() {
        val content = "我们计划去成都旅行，预算五千左右"
        val excerpt = ChatRecordSearch.buildExcerpt(content, "成都 成都旅行", maxChars = 80)!!
        assertTrue(excerpt.highlights.isNotEmpty())
        // 相邻/重叠命中合并为一段，避免高亮区间互相覆盖
        for (i in 1 until excerpt.highlights.size) {
            assertTrue(excerpt.highlights[i].first >= excerpt.highlights[i - 1].last + 1)
        }
        assertTrue(excerpt.text.contains("成都旅行"))
    }

    @Test
    fun `buildExcerpt returns null when query has no terms or no hit`() {
        assertNull(ChatRecordSearch.buildExcerpt("随便一段内容", "   "))
        assertNull(ChatRecordSearch.buildExcerpt("随便一段内容", "不存在的词"))
        assertNull(ChatRecordSearch.buildExcerpt("", "关键词"))
    }

    @Test
    fun `buildExcerpt keeps short content whole with highlight`() {
        val excerpt = ChatRecordSearch.buildExcerpt("今天天气不错，适合去公园散步", "公园", maxChars = 80)!!
        assertFalse(excerpt.text.startsWith("…"))
        assertFalse(excerpt.text.endsWith("…"))
        assertEquals("公园", excerpt.text.substring(excerpt.highlights.first()))
    }
}
