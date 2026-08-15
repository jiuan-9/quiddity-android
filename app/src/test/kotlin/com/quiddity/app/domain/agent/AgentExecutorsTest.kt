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

    @Test
    fun writeCommands_buildFixedCommandArrays() {
        assertEquals(
            listOf("pm", "disable-user", "--user", "0", "com.a"),
            AgentExecutors.disableCommand("com.a").toList()
        )
        assertEquals(
            listOf("pm", "enable", "com.a"),
            AgentExecutors.enableCommand("com.a").toList()
        )
        assertEquals(
            listOf("cmd", "appops", "set", "com.a", "VIBRATE", "allow"),
            AgentExecutors.appOpsCommand("com.a", "VIBRATE", "allow").toList()
        )
        assertEquals(
            listOf("am", "force-stop", "com.a"),
            AgentExecutors.forceStopCommand("com.a").toList()
        )
        assertEquals(
            listOf("pm", "uninstall", "com.a"),
            AgentExecutors.uninstallCommand("com.a").toList()
        )
    }

    @Test
    fun formatShellResult_successAndFailure() {
        assertEquals(
            "已停用 com.a",
            AgentExecutors.formatShellResult(AgentShellResult(0, ""), "已停用 com.a")
        )
        assertTrue(
            AgentExecutors.formatShellResult(
                AgentShellResult(0, "Package com.a new state: disabled-user"),
                "已停用 com.a"
            ).contains("disabled-user")
        )
        assertTrue(
            AgentExecutors.formatShellResult(
                AgentShellResult(1, "Error"),
                "已停用 com.a"
            ).contains("退出码 1")
        )
        assertTrue(
            AgentExecutors.formatShellResult(
                AgentShellResult(1, ""),
                "已停用 com.a"
            ).contains("请检查包名与权限")
        )
    }

    @Test
    fun formatPermissions_showsGrantedStateWithShortNames() {
        val out = AgentExecutors.formatPermissions(
            listOf(
                "android.permission.READ_EXTERNAL_STORAGE" to true,
                "android.permission.CAMERA" to false
            )
        )
        assertTrue(out.contains("READ_EXTERNAL_STORAGE：已授予"))
        assertTrue(out.contains("CAMERA：未授予"))
        assertTrue(!out.contains("android.permission.READ_EXTERNAL_STORAGE：已授予"))
    }

    @Test
    fun formatInstallInfo_listsTimesAndSource() {
        val out = AgentExecutors.formatInstallInfo(0L, 1_750_000_000_000L, "com.android.vending")
        assertTrue(out.contains("首次安装：未知"))
        assertTrue(out.contains("最近更新："))
        assertTrue(out.contains("安装来源：com.android.vending"))
        val unknown = AgentExecutors.formatInstallInfo(0L, 0L, null)
        assertTrue(unknown.contains("安装来源：未知"))
    }

    @Test
    fun formatTraffic_sortsByTotalAndFormatsBytes() {
        val entries = listOf(
            Triple("com.small", 500L, 200L),
            Triple("com.big", 3L shl 20, 1L shl 20)
        )
        val out = AgentExecutors.formatTraffic(entries, 10)
        val bigPos = out.indexOf("com.big")
        val smallPos = out.indexOf("com.small")
        assertTrue(bigPos in 0 until smallPos)
        assertTrue(out.contains("3.0 MB"))
        assertTrue(out.contains("700 B"))
        val limited = AgentExecutors.formatTraffic(entries, 1)
        assertTrue(limited.contains("com.big"))
        assertTrue(!limited.contains("com.small"))
    }

    @Test
    fun formatBytes_convertsUnits() {
        assertEquals("0 B", AgentExecutors.formatBytes(0L))
        assertEquals("500 B", AgentExecutors.formatBytes(500L))
        assertEquals("1.5 KB", AgentExecutors.formatBytes(1536L))
        assertEquals("2.0 MB", AgentExecutors.formatBytes(2L shl 20))
        assertEquals("1.0 GB", AgentExecutors.formatBytes(1L shl 30))
    }

    @Test
    fun parseBatteryBlock_extractsUidPowerLines() {
        val output = """
            Per-app battery usage:
              Uid u0a123:
                power: 12.3 mAh
                cpu: 5.2s
                wake_lock: 3.1s
                wifi: 1.2s
            Settings:
            Estimated power use:
        """.trimIndent()
        val lines = AgentExecutors.parseBatteryBlock(output)
        assertTrue(lines.any { it.startsWith("Uid u0a123") })
        assertTrue(lines.any { it.startsWith("power: 12.3 mAh") })
        assertTrue(lines.any { it.startsWith("wake_lock: 3.1s") })
        assertTrue(!lines.any { it.contains("Settings") })
        assertEquals(emptyList(), AgentExecutors.parseBatteryBlock("no stats here"))
    }

    @Test
    fun formatSystemLogs_capsLinesAndTruncatesLongLines() {
        val lines = (1..5).map { "line_$it" }
        val capped = AgentExecutors.formatSystemLogs(lines, 3)
        assertEquals("line_3\nline_4\nline_5", capped)
        val long = listOf("x".repeat(600))
        assertEquals(400, AgentExecutors.formatSystemLogs(long, 10).length)
    }

}
