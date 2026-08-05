package com.quiddity.app.util

import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
 *    除此之外，禁止出现任何形式的代码注释（包括但不限于单行注释、多行调试注释等）。
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



/** [ChatImageExporter] 纯逻辑单元测试：消息过滤、顺序保持、超长截断。 */
class ChatImageExporterTest {

    private fun msg(
        id: String,
        content: String,
        isNotice: Boolean = false,
        role: Role = Role.ASSISTANT
    ): Message = Message(
        id = id,
        conversationId = "c",
        role = role,
        content = content,
        timestamp = 0L,
        isNotice = isNotice
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
