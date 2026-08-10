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
        assertEquals(original, decoded)
        assertEquals(1, decoded.version)
        assertEquals(emptyList(), decoded.whitelist)
        assertTrue(decoded.toolSwitches.sense_screen)
        assertTrue(decoded.toolSwitches.sense_notifications)
        assertTrue(decoded.toolSwitches.sense_usage)
        assertTrue(decoded.toolSwitches.read_apps)
        assertTrue(decoded.toolSwitches.read_system)
        assertFalse(decoded.toolSwitches.write_disable)
        assertFalse(decoded.toolSwitches.write_appops)
        assertFalse(decoded.toolSwitches.write_force_stop)
        assertFalse(decoded.toolSwitches.write_uninstall)
        assertEquals(emptyList(), decoded.audit)
    }

    @Test
    fun settingsWithWhitelistAndAudit_roundTripPreservesValues() {
        val original = AgentSettings(
            whitelist = listOf("com.tencent.mm", "com.example.app"),
            toolSwitches = AgentToolSwitches(write_disable = true),
            audit = listOf(
                AgentAuditEntry(ts = "2026-08-10T12:00:00", tool = "disable_app", args = """{"pkg":"com.x"}""", ok = true, confirmed = true)
            )
        )
        val decoded = AgentStore.decode(AgentStore.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun decode_emptyAndPartialJson_fallsBackToDefaults() {
        val empty = AgentStore.decode("{}")
        assertEquals(AgentSettings(), empty)

        val partial = AgentStore.decode("""{"whitelist":["com.a"]}""")
        assertEquals(listOf("com.a"), partial.whitelist)
        assertEquals(AgentToolSwitches(), partial.toolSwitches)
        assertTrue(partial.toolSwitches.read_apps)
        assertFalse(partial.toolSwitches.write_force_stop)
    }

    @Test
    fun applySwitch_knownNamesUpdateValue_unknownNameReturnsNull() {
        val switches = AgentToolSwitches()
        val updated = AgentStore.applySwitch(switches, "read_apps", false)
        assertFalse(updated!!.read_apps)
        assertTrue(updated.sense_screen)
        assertNull(AgentStore.applySwitch(switches, "not_a_switch", true))
        assertNull(AgentStore.applySwitch(switches, "", false))
    }

    @Test
    fun appendWhitelist_deduplicatesAndRemoves() {
        val withAdd = AgentStore.whitelistWith(listOf("com.a"), "com.b")
        assertEquals(listOf("com.a", "com.b"), withAdd)
        val dedup = AgentStore.whitelistWith(withAdd, "com.a")
        assertEquals(listOf("com.a", "com.b"), dedup)
        assertEquals(listOf("com.a"), AgentStore.whitelistWithout(dedup, "com.b"))
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
    fun cappedAudit_emptyAuditCleared() {
        val audit = listOf(
            AgentAuditEntry(ts = "t0", tool = "t", args = "{}", ok = true, confirmed = true)
        )
        assertEquals(emptyList(), AgentStore.clearedAudit())
        assertEquals(1, audit.size)
    }
}
