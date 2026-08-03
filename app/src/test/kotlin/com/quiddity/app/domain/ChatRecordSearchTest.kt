package com.quiddity.app.domain

import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
}
