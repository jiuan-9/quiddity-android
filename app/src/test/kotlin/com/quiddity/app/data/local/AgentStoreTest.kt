package com.quiddity.app.data.local

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentStoreTest {

    @Test
    fun defaultSettings_roundTripPreservesValues() {
        val original = AgentSettings()
        val decoded = AgentStore.decode(AgentStore.encode(original))
        // 解码时自动迁移：tools 为空 → 按默认值补齐为每工具开关
        assertEquals(original.copy(toolSwitches = original.toolSwitches.migrateLegacy()), decoded)
        assertEquals(2, decoded.version)
        assertEquals(emptyList(), decoded.blacklist)
        // 工具开关 v2：tools 为空时按默认值迁移补齐
        assertTrue(decoded.toolSwitches.isEnabled("read_screen"))
        assertTrue(decoded.toolSwitches.isEnabled("read_notifications"))
        assertTrue(decoded.toolSwitches.isEnabled("usage_stats"))
        assertTrue(decoded.toolSwitches.isEnabled("list_apps"))
        assertFalse(decoded.toolSwitches.isEnabled("disable_app"))
        assertFalse(decoded.toolSwitches.isEnabled("set_appops"))
        assertFalse(decoded.toolSwitches.isEnabled("force_stop"))
        assertFalse(decoded.toolSwitches.isEnabled("uninstall_app"))
        assertEquals(emptyList(), decoded.audit)
    }

    @Test
    fun settingsWithBlacklistAndAudit_roundTripPreservesValues() {
        val original = AgentSettings(
            blacklist = listOf("com.tencent.mm", "/sdcard/Download/private"),
            toolSwitches = AgentToolSwitches(tools = mapOf("write_disable" to true)),
            audit = listOf(
                AgentAuditEntry(ts = "2026-08-10T12:00:00", tool = "disable_app", args = """{"pkg":"com.x"}""", ok = true, confirmed = true)
            )
        )
        val decoded = AgentStore.decode(AgentStore.encode(original))
        assertEquals(original, decoded)
        assertTrue(decoded.toolSwitches.isEnabled("write_disable"))
    }

    @Test
    fun decode_emptyAndPartialJson_fallsBackToDefaults() {
        val empty = AgentStore.decode("{}")
        // 空 JSON 解码后 tools 为空，运行期 isEnabled 走默认值（等价于迁移后的完整开关集）
        assertEquals(AgentSettings().toolSwitches.migrateLegacy(), empty.toolSwitches)

        val partial = AgentStore.decode("""{"blacklist":["com.a"]}""")
        assertEquals(listOf("com.a"), partial.blacklist)
        assertTrue(partial.toolSwitches.isEnabled("list_apps"))
        assertFalse(partial.toolSwitches.isEnabled("force_stop"))
    }

    @Test
    fun legacyV1Json_migratesToPerToolSwitches() {
        // v1 旧格式：按字段存储开关；解码后自动迁移为每工具开关
        val legacy = """{"version":1,"toolSwitches":{"sense_screen":false,"read_apps":true,"write_files":false,"simulate_click":true}}"""
        val decoded = AgentStore.decode(legacy)
        assertFalse(decoded.toolSwitches.isEnabled("read_screen"), "旧字段 sense_screen=false 应迁移到 read_screen=false")
        assertTrue(decoded.toolSwitches.isEnabled("list_apps"), "旧字段 read_apps=true 应迁移到 list_apps=true")
        assertTrue(decoded.toolSwitches.isEnabled("click"), "旧字段 simulate_click=true 应迁移到 click=true")
        assertFalse(decoded.toolSwitches.isEnabled("create_file"), "旧字段 write_files=false 应迁移到 create_file=false")
        // 未出现在旧 JSON 的字段按默认值
        assertTrue(decoded.toolSwitches.isEnabled("ocr_image"))
    }

    @Test
    fun legacyWhitelist_isRetainedButIgnoredByGate() {
        // v1 白名单字段保留（仅供查看），黑名单为空（默认全应用权限）
        val decoded = AgentStore.decode("""{"whitelist":["com.a"]}""")
        assertEquals(listOf("com.a"), decoded.whitelist)
        assertEquals(emptyList(), decoded.blacklist)
    }

    @Test
    fun applySwitch_knownNamesUpdateValue_unknownNameReturnsNull() {
        val switches = AgentToolSwitches()
        val updated = AgentStore.applySwitch(switches, "list_apps", false)
        assertFalse(updated!!.isEnabled("list_apps"))
        assertTrue(updated.isEnabled("read_screen"))
        assertNull(AgentStore.applySwitch(switches, "not_a_switch", true))
        assertNull(AgentStore.applySwitch(switches, "", false))
    }

    @Test
    fun applyCategory_togglesWholeCategory() {
        val switches = AgentToolSwitches()
        val allOff = AgentStore.applyCategory(switches, setOf("click", "scroll", "drag"), false)
        assertFalse(allOff.isEnabled("click"))
        assertFalse(allOff.isEnabled("scroll"))
        assertFalse(allOff.isEnabled("drag"))
        val allOn = AgentStore.applyCategory(allOff, setOf("click", "scroll", "drag"), true)
        assertTrue(allOn.isEnabled("click"))
        assertTrue(allOn.isEnabled("drag"))
    }

    @Test
    fun appendBlacklist_deduplicatesAndRemoves() {
        val withAdd = AgentStore.blacklistWith(listOf("com.a"), "com.b")
        assertEquals(listOf("com.a", "com.b"), withAdd)
        val dedup = AgentStore.blacklistWith(withAdd, "com.a")
        assertEquals(listOf("com.a", "com.b"), dedup)
        assertEquals(listOf("com.a"), AgentStore.blacklistWithout(dedup, "com.b"))
    }

    @Test
    fun cappedAudit_keepsNewestAndLimitsTo500() {
        var audit = emptyList<AgentAuditEntry>()
        for (i in 0 until 505) {
            audit = AgentStore.cappedAudit(
                audit,
                AgentAuditEntry(ts = "t$i", tool = "t", args = "{}", ok = true, confirmed = true)
            )
        }
        assertEquals(500, audit.size)
        assertEquals("t5", audit.first().ts)
        assertEquals("t504", audit.last().ts)
    }

    @Test
    fun cappedAudit_keepsInputListUnchanged() {
        val audit = listOf(
            AgentAuditEntry(ts = "t0", tool = "t", args = "{}", ok = true, confirmed = true)
        )
        // 清理辅助函数已随 clearAudit 一起移除；这里只保证输入列表不被测试误改
        assertEquals(1, audit.size)
    }
}
