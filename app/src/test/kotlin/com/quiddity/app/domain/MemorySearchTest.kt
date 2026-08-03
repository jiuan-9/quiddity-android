package com.quiddity.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemorySearchTest {

    private val memory = """
        用户的项目：Quiddity Android，目标 8 月发版。
        用户喜欢深色主题，讨厌弹窗打扰。
        昨天聊了壁纸功能，决定支持暗化调节。
        公司年会定在 12 月 20 日。
    """.trimIndent()

    @Test
    fun `finds paragraphs matching query terms`() {
        val result = MemorySearch.search(memory, "壁纸 暗化")
        assertTrue(result.found)
        assertTrue(result.content.contains("壁纸"))
        assertTrue(result.content.contains("暗化调节"))
    }

    @Test
    fun `matches chinese and english queries case-insensitively`() {
        val en = MemorySearch.search(memory, "quiddity")
        assertTrue(en.found)
        assertTrue(en.content.contains("Quiddity"))
    }

    @Test
    fun `returns not found when nothing matches`() {
        val result = MemorySearch.search(memory, "航空母舰")
        assertFalse(result.found)
        assertEquals(MemorySearch.NOT_FOUND_TEXT, result.content)
    }

    @Test
    fun `returns head when query is blank`() {
        val result = MemorySearch.search(memory, "  ")
        assertTrue(result.found)
        assertTrue(result.content.startsWith("【记忆检索结果】"))
    }

    @Test
    fun `returns not found when memory is blank`() {
        val result = MemorySearch.search("", "壁纸")
        assertFalse(result.found)
    }

    @Test
    fun `caps output length`() {
        val longMemory = (1..500).joinToString("\n") { "第 $it 条记忆：用户喜欢的数字是 $it" }
        val result = MemorySearch.search(longMemory, "喜欢")
        assertTrue(result.found)
        assertTrue(result.content.length <= MemorySearch.MEMORY_SEARCH_MAX_CHARS + 32)
    }
}
