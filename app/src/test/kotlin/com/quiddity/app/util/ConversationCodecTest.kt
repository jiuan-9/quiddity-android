package com.quiddity.app.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * ConversationCodec 导入时间戳行为测试。
 *
 * 修复目标：旧实现用 `timestamp - (messages.size - index)` 强行制造递增，
 * 会把同一秒内的消息时间拨早数秒，甚至把 00:00 附近的消息拨到前一天。
 * 新实现只做单调递增保护（后一条不早于前一条时顺延 1ms），不人为回拨。
 */
class ConversationCodecTest {

    private fun markdownWithTimes(times: List<String>): String {
        val header = "# Quiddity对话记录\n" +
            "> 导出时间：2026-08-06 12:00\n" +
            "> 格式版本：1（Quiddity Android 兼容）\n" +
            "> 会话标题：测试\n" +
            "> 消息条数：${times.size}\n\n" +
            "---\n\n"
        return header + times.joinToString("\n\n---\n\n") { time ->
            "**用户** ($time)\n\n消息$time"
        }
    }

    @Test
    fun `same-second messages keep increasing timestamps without artificial backshift`() {
        val result = ConversationCodec.importConversation(
            content = markdownWithTimes(listOf("12:00:00", "12:00:00", "12:00:01")),
            targetConversationId = "conv_test"
        )
        assertEquals(3, result.messages.size)
        val ts = result.messages.map { it.timestamp }
        assertTrue(ts[0] < ts[1], "同一秒内相邻消息必须保持递增（顺延 1ms）")
        assertTrue(ts[1] < ts[2], "不同秒消息保持自然顺序")
    }

    @Test
    fun `timestamps stay within today`() {
        val result = ConversationCodec.importConversation(
            content = markdownWithTimes(listOf("00:00:00", "00:00:00")),
            targetConversationId = "conv_test"
        )
        val todayStart = java.time.LocalDate.now()
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val todayEnd = todayStart + 24L * 60 * 60 * 1000
        result.messages.forEach { msg ->
            assertTrue(msg.timestamp >= todayStart, "消息时间不得早于今天 00:00")
            assertTrue(msg.timestamp < todayEnd, "消息时间不得晚于今天结束")
        }
    }
}
