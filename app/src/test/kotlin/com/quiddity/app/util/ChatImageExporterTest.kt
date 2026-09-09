package com.quiddity.app.util

import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [ChatImageExporter] 纯逻辑单元测试：消息过滤、顺序保持、超长截断。 */
class ChatImageExporterTest {

    private fun msg(
        id: String,
        content: String,
        isNotice: Boolean = false,
        isThinking: Boolean = false,
        role: Role = Role.ASSISTANT
    ): Message = Message(
        id = id,
        conversationId = "c",
        role = role,
        content = content,
        timestamp = 0L,
        isNotice = isNotice,
        isThinking = isThinking
    )

    private fun seg(isUser: Boolean, content: String, timestamp: Long = 0L) =
        ChatImageExporter.Segment(isUser = isUser, content = content, timestamp = timestamp)

    @Test
    fun `buildSegments filters notices and preserves order`() {
        val messages = listOf(
            msg("n1", "提示气泡", isNotice = true),
            msg("u1", "你好", role = Role.USER),
            msg("a1", "嗨！", role = Role.ASSISTANT)
        )
        val segments = ChatImageExporter.buildSegments(messages)
        assertEquals(listOf("你好", "嗨！"), segments.map { it.content })
        assertEquals(listOf(true, false), segments.map { it.isUser })
    }

    @Test
    fun `buildSegments filters thinking messages`() {
        val messages = listOf(
            msg("t1", "思考过程", isThinking = true),
            msg("u1", "你好", role = Role.USER),
            msg("a1", "嗨！")
        )
        val segments = ChatImageExporter.buildSegments(messages)
        assertEquals(listOf("你好", "嗨！"), segments.map { it.content })
    }

    @Test
    fun `fitSegments keeps all when within max height`() {
        val segments = listOf(seg(true, "a"), seg(false, "b"))
        val result = ChatImageExporter.fitSegments(
            segments = segments,
            maxHeight = 1_000,
            footerHeight = 50
        ) { 100 }
        assertEquals(2, result.segments.size)
        assertFalse(result.truncated)
    }

    @Test
    fun `fitSegments truncates when exceeding max height`() {
        val segments = listOf(seg(true, "a"), seg(false, "b"), seg(true, "c"))
        val result = ChatImageExporter.fitSegments(
            segments = segments,
            maxHeight = 350,
            footerHeight = 50
        ) { 150 }
        // 前两段 300 + 尾注 50 = 350 恰好放下；第三段 300 + 150 + 50 超限 → 截断
        assertEquals(2, result.segments.size)
        assertTrue(result.truncated)
    }

    @Test
    fun `fitSegments keeps first segment even if taller than max`() {
        val segments = listOf(seg(true, "超长消息"), seg(false, "b"))
        val result = ChatImageExporter.fitSegments(
            segments = segments,
            maxHeight = 200,
            footerHeight = 50
        ) { 300 }
        // 首段即使超高也保留（避免导出空图），后续段落被截断
        assertEquals(1, result.segments.size)
        assertTrue(result.truncated)
    }

    @Test
    fun `empty segments produce empty result`() {
        val result = ChatImageExporter.fitSegments(
            segments = emptyList(),
            maxHeight = 1_000,
            footerHeight = 50
        ) { 100 }
        assertTrue(result.segments.isEmpty())
        assertFalse(result.truncated)
    }

    @Test
    fun `segment row height reserves space for timestamp below bubble`() {
        val bubbleH = 320
        val rowH = ChatImageExporter.segmentRowHeight(bubbleH)
        // 时间绘制在气泡底部下方 TIME_V_OFFSET 处，占用 TIME_TEXT_HEIGHT 高度，
        // 行高必须不小于两者之和，否则会与下一个气泡重叠
        assertTrue(
            rowH >= bubbleH + ChatImageExporter.TIME_V_OFFSET + ChatImageExporter.TIME_TEXT_HEIGHT,
            "行高 $rowH 未给时间戳预留足够空间"
        )
    }
}
