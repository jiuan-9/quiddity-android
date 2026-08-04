package com.quiddity.app.domain

import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import kotlin.test.Test
import kotlin.test.assertEquals
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



/** [GlobalChatSearch] 单元测试：跨会话合并、上限、isNotice 过滤、时间倒序。 */
class GlobalChatSearchTest {

    private fun msg(
        id: String,
        content: String,
        timestamp: Long,
        isNotice: Boolean = false,
        role: Role = Role.ASSISTANT
    ): Message = Message(
        id = id,
        conversationId = "c",
        role = role,
        content = content,
        timestamp = timestamp,
        isNotice = isNotice
    )

    @Test
    fun `blank query returns empty`() {
        val map = mapOf("c1" to listOf(msg("m1", "项目讨论", 1)))
        assertTrue(GlobalChatSearch.searchAll(map, emptyMap(), "  ").isEmpty())
    }

    @Test
    fun `matches are merged across conversations and sorted by time desc`() {
        val map = mapOf(
            "c1" to listOf(
                msg("a", "讨论项目进度", timestamp = 1_000),
                msg("b", "项目周报已完成", timestamp = 3_000)
            ),
            "c2" to listOf(msg("c", "项目会议纪要", timestamp = 2_000))
        )
        val titles = mapOf("c1" to "会话A", "c2" to "会话B")
        val hits = GlobalChatSearch.searchAll(map, titles, "项目")
        assertEquals(listOf("b", "c", "a"), hits.map { it.message.id })
        assertEquals("会话A", hits.first().conversationTitle)
    }

    @Test
    fun `notice messages are excluded`() {
        val map = mapOf(
            "c1" to listOf(
                msg("n", "项目提示", timestamp = 1, isNotice = true),
                msg("m", "项目真实消息", timestamp = 2)
            )
        )
        val hits = GlobalChatSearch.searchAll(map, emptyMap(), "项目")
        assertEquals(listOf("m"), hits.map { it.message.id })
    }

    @Test
    fun `per conversation and total limits apply`() {
        val c1 = (1..10).map { msg("c1m$it", "项目 $it", timestamp = 100L + it) }
        val c2 = (1..10).map { msg("c2m$it", "项目 $it", timestamp = 200L + it) }
        val map = mapOf("c1" to c1, "c2" to c2)
        val hits = GlobalChatSearch.searchAll(
            map,
            emptyMap(),
            "项目",
            perConversationLimit = 3,
            totalLimit = 4
        )
        assertEquals(4, hits.size)
        // 每会话最多 3 条；时间倒序下 c2（时间更大）占前 3 条
        assertEquals(3, hits.count { it.conversationId == "c2" })
        assertEquals(1, hits.count { it.conversationId == "c1" })
    }

    @Test
    fun `no match returns empty`() {
        val map = mapOf("c1" to listOf(msg("m1", "你好", timestamp = 1)))
        assertTrue(GlobalChatSearch.searchAll(map, emptyMap(), "不存在的关键词").isEmpty())
    }
}
