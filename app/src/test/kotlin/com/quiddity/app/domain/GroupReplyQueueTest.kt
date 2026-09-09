package com.quiddity.app.domain

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
 * 群聊点名回复队列测试（方案四：1 个回复 + 2 个排队）。
 */
class GroupReplyQueueTest {

    private val now = 1720000000000L

    private fun msg(id: String, content: String): Message = Message(
        id = id,
        conversationId = "group",
        role = Role.USER,
        content = content,
        timestamp = now
    )

    @Test
    fun `enqueue accepts up to three members`() {
        val q = GroupReplyQueue()
        assertTrue(q.enqueue("a", listOf(msg("m1", "第一句"))))
        assertTrue(q.enqueue("b", listOf(msg("m2", "第二句"))))
        assertTrue(q.enqueue("c", listOf(msg("m3", "第三句"))))
        assertTrue(q.isFull)
        assertEquals(3, q.size)
    }

    @Test
    fun `enqueue rejects when queue is full`() {
        val q = GroupReplyQueue()
        q.enqueue("a", emptyList())
        q.enqueue("b", emptyList())
        q.enqueue("c", emptyList())
        assertFalse(q.enqueue("d", emptyList()), "第 4 个成员应被拒绝（1 回复 + 2 排队）")
        assertEquals(3, q.size)
    }

    @Test
    fun `enqueue rejects duplicate member`() {
        val q = GroupReplyQueue()
        assertTrue(q.enqueue("a", emptyList()))
        assertFalse(q.enqueue("a", emptyList()))
        assertEquals(1, q.size)
    }

    @Test
    fun `position matches replying and queued order`() {
        val q = GroupReplyQueue()
        q.enqueue("a", emptyList())
        q.enqueue("b", emptyList())
        q.enqueue("c", emptyList())
        assertEquals(0, q.positionOf("a"))
        assertEquals(1, q.positionOf("b"))
        assertEquals(2, q.positionOf("c"))
        assertEquals(-1, q.positionOf("d"))
    }

    @Test
    fun `dequeue advances the next member to replying`() {
        val q = GroupReplyQueue()
        q.enqueue("a", emptyList())
        q.enqueue("b", emptyList())
        val done = q.dequeue()
        assertEquals("a", done?.memberId)
        assertEquals(0, q.positionOf("b"))
    }

    @Test
    fun `clear empties the whole queue`() {
        val q = GroupReplyQueue()
        q.enqueue("a", emptyList())
        q.enqueue("b", emptyList())
        q.clear()
        assertTrue(q.isEmpty)
    }

    @Test
    fun `removeReplying only drops the current member`() {
        val q = GroupReplyQueue()
        q.enqueue("a", emptyList())
        q.enqueue("b", emptyList())
        q.enqueue("c", emptyList())
        val stopped = q.removeReplying()
        assertEquals("a", stopped?.memberId)
        assertEquals(0, q.positionOf("b"))
        assertEquals(1, q.positionOf("c"))
    }

    @Test
    fun `frozen messages are captured at enqueue time`() {
        val q = GroupReplyQueue()
        val frozen = listOf(msg("m1", "点击那一刻的消息"))
        q.enqueue("a", frozen)
        val item = q.snapshot().first()
        assertEquals("点击那一刻的消息", item.frozenMessages.first().content)
    }
}
