package com.quiddity.app.domain.agent

import com.quiddity.app.data.local.AgentAuditEntry
import com.quiddity.app.data.local.AgentSettings
import com.quiddity.app.data.local.AgentToolSwitches
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ConversationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentToolRegistryTest {

    private val registry = AgentToolRegistry.defaultRegistry()

    private fun context(
        switches: AgentToolSwitches = AgentToolSwitches(),
        whitelist: Set<String> = emptySet(),
        audit: MutableList<AgentAuditEntry> = mutableListOf(),
        confirmRequest: (suspend (AgentTool, kotlinx.serialization.json.JsonObject) -> Boolean)? = null
    ): AgentContext = AgentContext(
        conversation = Conversation(
            id = "conv1",
            createdAt = 0L,
            updatedAt = 0L,
            type = ConversationType.AGENT
        ),
        switches = switches,
        whitelist = whitelist,
        auditAppend = { audit += it },
        confirmRequest = confirmRequest
    )

    private fun j(args: String) = args

    @Test
    fun defaultRegistry_containsAllTenToolsWithMetadata() {
        assertEquals(10, registry.tools().size)

        val basic = listOf("list_apps", "read_screen", "read_notifications", "usage_stats", "foreground_app")
        val advanced = listOf("disable_app", "enable_app", "set_appops", "force_stop", "uninstall_app")

        basic.forEach { name ->
            val tool = registry[name]
            assertNotNull(tool, name)
            assertEquals(AgentPermissionLevel.BASIC, tool.level, name)
            assertEquals(AgentConfirmPolicy.AUTO, tool.confirm, name)
            assertTrue(tool.enabledByDefault, name)
        }
        advanced.forEach { name ->
            val tool = registry[name]
            assertNotNull(tool, name)
            assertEquals(AgentPermissionLevel.ADVANCED, tool.level, name)
            assertEquals(AgentConfirmPolicy.ALWAYS_CONFIRM, tool.confirm, name)
            assertEquals(false, tool.enabledByDefault, name)
        }
    }

    @Test
    fun dispatch_unknownTool_returnsNotFound() {
        val result = runBlockingTest { registry.dispatch("no_such_tool", "{}", context()) }
        assertTrue(result.contains("不存在") || result.contains("not found"))
    }

    @Test
    fun dispatch_disabledTool_returnsDisabledMessage() {
        val switches = AgentToolSwitches().copy(sense_screen = false)
        val result = runBlockingTest { registry.dispatch("read_screen", "{}", context(switches = switches)) }
        assertTrue(result.contains("未启用") || result.contains("disabled"))
    }

    @Test
    fun dispatch_validReadTool_executesPlaceholder() {
        val result = runBlockingTest { registry.dispatch("list_apps", "{}", context()) }
        assertTrue(result.isNotBlank())
    }

    @Test
    fun validateArgs_rejectsInvalidPkg_acceptsValidPkg() {
        val tool = registry["disable_app"]!!
        assertNull(AgentSecurity.validateArgs(tool, parse("""{"pkg":"com.tencent.mm"}""")))
        assertNotNull(AgentSecurity.validateArgs(tool, parse("""{"pkg":"com.a_b"}""")))
        assertNotNull(AgentSecurity.validateArgs(tool, parse("""{"pkg":"evil app"}""")))
        assertNotNull(AgentSecurity.validateArgs(tool, parse("""{"pkg":""}""")))
        assertNotNull(AgentSecurity.validateArgs(tool, parse("""{}""")))
    }

    @Test
    fun validateArgs_rejectsInvalidAppOpsModeAndOp() {
        val tool = registry["set_appops"]!!
        assertNull(AgentSecurity.validateArgs(tool, parse("""{"pkg":"com.a","op":"VIBRATE","mode":"allow"}""")))
        assertNotNull(AgentSecurity.validateArgs(tool, parse("""{"pkg":"com.a","op":"NOT_A_REAL_OP","mode":"allow"}""")))
        assertNotNull(AgentSecurity.validateArgs(tool, parse("""{"pkg":"com.a","op":"VIBRATE","mode":"sometimes"}""")))
        assertNotNull(AgentSecurity.validateArgs(tool, parse("""{"pkg":"com.a","op":"VIBRATE","mode":"ask"}""")))
        assertNotNull(AgentSecurity.validateArgs(tool, parse("""{"pkg":"com.a","op":"VIBRATE"}""")))
    }

    @Test
    fun dispatch_advancedToolWithoutWhitelist_denied() {
        val result = runBlockingTest {
            registry.dispatch(
                "disable_app",
                """{"pkg":"com.tencent.mm"}""",
                context(
                    switches = AgentToolSwitches().copy(write_disable = true),
                    confirmRequest = { _, _ -> true }
                )
            )
        }
        assertTrue(result.contains("白名单") || result.contains("whitelist") || result.contains("拒绝"))
    }

    @Test
    fun dispatch_advancedTool_confirmRequestApproved_executes() {
        val audit = mutableListOf<AgentAuditEntry>()
        val result = runBlockingTest {
            registry.dispatch(
                "disable_app",
                """{"pkg":"com.tencent.mm"}""",
                context(
                    switches = AgentToolSwitches().copy(write_disable = true),
                    whitelist = setOf("com.tencent.mm"),
                    audit = audit,
                    confirmRequest = { _, _ -> true }
                )
            )
        }
        assertTrue(result.isNotBlank())
        assertEquals(1, audit.size)
        assertEquals("disable_app", audit[0].tool)
        assertEquals(true, audit[0].ok)
        assertEquals(true, audit[0].confirmed)
    }

    @Test
    fun dispatch_advancedTool_userDeclined_cancelled() {
        val audit = mutableListOf<AgentAuditEntry>()
        val result = runBlockingTest {
            registry.dispatch(
                "disable_app",
                """{"pkg":"com.tencent.mm"}""",
                context(
                    switches = AgentToolSwitches().copy(write_disable = true),
                    whitelist = setOf("com.tencent.mm"),
                    audit = audit,
                    confirmRequest = { _, _ -> false }
                )
            )
        }
        assertTrue(result.contains("取消"))
        assertEquals(1, audit.size)
        assertEquals(false, audit[0].ok)
        assertEquals(false, audit[0].confirmed)
    }

    @Test
    fun dispatch_advancedTool_modelConfirmedFlag_notTrustedWithoutDialog() {
        val audit = mutableListOf<AgentAuditEntry>()
        val result = runBlockingTest {
            registry.dispatch(
                "disable_app",
                """{"pkg":"com.tencent.mm","confirmed":true}""",
                context(
                    switches = AgentToolSwitches().copy(write_disable = true),
                    whitelist = setOf("com.tencent.mm"),
                    audit = audit
                )
            )
        }
        assertTrue(result.contains("确认"))
        assertEquals(1, audit.size)
        assertEquals(false, audit[0].ok)
        assertEquals(false, audit[0].confirmed)
    }

    @Test
    fun dispatch_advancedToolWithoutConfirm_blocked() {
        val audit = mutableListOf<AgentAuditEntry>()
        val result = runBlockingTest {
            registry.dispatch(
                "disable_app",
                """{"pkg":"com.tencent.mm"}""",
                context(
                    switches = AgentToolSwitches().copy(write_disable = true),
                    whitelist = setOf("com.tencent.mm"),
                    audit = audit
                )
            )
        }
        assertTrue(result.contains("确认") || result.contains("confirm"))
        assertEquals(1, audit.size)
        assertEquals(false, audit[0].ok)
    }

    @Test
    fun dispatch_invalidArgs_rejectedWithAudit() {
        val audit = mutableListOf<AgentAuditEntry>()
        val result = runBlockingTest {
            registry.dispatch(
                "force_stop",
                """{"pkg":"bad pkg"}""",
                context(
                    switches = AgentToolSwitches().copy(write_force_stop = true),
                    whitelist = setOf("bad pkg"),
                    audit = audit,
                    confirmRequest = { _, _ -> true }
                )
            )
        }
        assertTrue(result.isNotBlank())
        assertEquals(1, audit.size)
        assertEquals(false, audit[0].ok)
        assertEquals(true, audit[0].confirmed)
    }

    @Test
    fun dispatch_respectsStoreSwitchState() {
        val switches = AgentToolSwitches().copy(read_apps = false)
        val result = runBlockingTest { registry.dispatch("list_apps", "{}", context(switches = switches)) }
        assertTrue(result.contains("未启用") || result.contains("disabled"))
    }

    @Test
    fun auditEntry_serializableShape() {
        val entry = AgentAuditEntry(
            ts = "2026-08-10T12:00:00",
            tool = "disable_app",
            args = """{"pkg":"com.x"}""",
            ok = true,
            confirmed = true
        )
        val settings = AgentSettings(audit = listOf(entry))
        val roundTrip = com.quiddity.app.data.local.AgentStore.decode(
            com.quiddity.app.data.local.AgentStore.encode(settings)
        )
        assertEquals(entry, roundTrip.audit.first())
    }

    private fun parse(json: String): kotlinx.serialization.json.JsonObject =
        kotlinx.serialization.json.Json.parseToJsonElement(json) as kotlinx.serialization.json.JsonObject

    private fun <T> runBlockingTest(block: suspend () -> T): T =
        kotlinx.coroutines.runBlocking { block() }
}
