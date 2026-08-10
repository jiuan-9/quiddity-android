package com.quiddity.app.domain.agent

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
 *    修复内容需完全融入现有代码架构，保证代码逻辑的连贯性、可维护性和可扩展性。
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
 * Agent 基础只读执行器的纯函数测试（不依赖 Android 框架）。
 */
class AgentExecutorsTest {

    @Test
    fun wrapUntrustedScreen_hasUnreadablePrefix() {
        val wrapped = AgentSecurity.wrapUntrustedScreen("hello")
        assertTrue(wrapped.contains("不可信数据"))
        assertTrue(wrapped.contains("hello"))
        assertTrue(wrapped.startsWith("["))
    }

    @Test
    fun wrapUntrustedNotifications_hasUnreadablePrefix() {
        val wrapped = AgentSecurity.wrapUntrustedNotifications("通知内容")
        assertTrue(wrapped.contains("不可信数据"))
        assertTrue(wrapped.contains("通知内容"))
    }

    @Test
    fun formatApps_filtersQueryCaseInsensitive_andFormatsLines() {
        val apps = listOf(
            "com.tencent.mm" to "微信",
            "com.example.demo" to "示例应用",
            "org.other.tool" to "工具"
        )
        val filtered = AgentExecutors.formatApps(apps, "WEIXIN")
        assertEquals("", filtered)
        val filteredCn = AgentExecutors.formatApps(apps, "微信")
        assertTrue(filteredCn.contains("com.tencent.mm：微信"))
        val all = AgentExecutors.formatApps(apps, null)
        assertTrue(all.contains("com.tencent.mm：微信"))
        assertTrue(all.contains("com.example.demo：示例应用"))
    }

    @Test
    fun formatNotifications_filtersSinceIso_sortsDesc() {
        val now = 1_750_000_000_000L
        val items = listOf(
            AgentNotification("com.a", "标题A", "内容A", now),
            AgentNotification("com.b", "标题B", "内容B", now - 10_000),
            AgentNotification("com.c", "标题C", "内容C", now - 60_000)
        )
        val since = java.time.Instant.ofEpochMilli(now - 30_000).toString()
        val out = AgentExecutors.formatNotifications(items, since)
        assertTrue(out.contains("标题A"))
        assertTrue(out.contains("标题B"))
        assertTrue(!out.contains("标题C"))
        val posA = out.indexOf("标题A")
        val posB = out.indexOf("标题B")
        assertTrue(posA in 0 until posB)
    }

    @Test
    fun formatUsage_formatsDurationLines() {
        val out = AgentExecutors.formatUsage(listOf("com.a" to 3_600_000L, "com.b" to 30_000L))
        assertTrue(out.contains("com.a"))
        assertTrue(out.contains("60 分钟"))
        assertTrue(out.contains("com.b"))
    }

    @Test
    fun truncateText_limitsByMaxChars() {
        val text = "a".repeat(200)
        assertEquals(100, AgentExecutors.truncate(text, 100).length)
        assertEquals(text, AgentExecutors.truncate(text, null))
        assertEquals(text, AgentExecutors.truncate(text, 500))
    }

    @Test
    fun sensorState_storesAndReturnsNotificationSnapshot() {
        AgentSensorState.updateNotifications(
            listOf(AgentNotification("com.a", "t", "x", 1L))
        )
        assertEquals(1, AgentSensorState.notifications().size)
        AgentSensorState.updateNotifications(emptyList())
        assertEquals(0, AgentSensorState.notifications().size)
    }
}
