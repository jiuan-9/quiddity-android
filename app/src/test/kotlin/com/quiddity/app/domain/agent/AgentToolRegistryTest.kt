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
        audit: MutableList<AgentAuditEntry> = mutableListOf()
    ): AgentContext = AgentContext(
        conversation = Conversation(
            id = "conv1",
            createdAt = 0L,
            updatedAt = 0L,
            type = ConversationType.AGENT
        ),
        switches = switches,
        whitelist = whitelist,
        auditAppend = { audit += it }
    )

    private fun j(args: String) = args

    @Test
    fun defaultRegistry_containsAllTenToolsWithMetadata() {
        assertEquals(10, registry.tools().size)

        val basic = listOf("列出应用", "读取屏幕", "读取通知", "用量统计", "前台应用")
        val advanced = listOf("停用应用", "启用应用", "设置应用权限", "强制停止", "卸载应用")

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
        val result = runBlockingTest { registry.dispatch("读取屏幕", "{}", context(switches = switches)) }
        assertTrue(result.contains("未启用") || result.contains("disabled"))
    }

    @Test
    fun dispatch_validReadTool_executesPlaceholder() {
        val result = runBlockingTest { registry.dispatch("列出应用", "{}", context()) }
        assertTrue(result.isNotBlank())
    }

    @Test
    fun validateArgs_rejectsInvalidPkg_acceptsValidPkg() {
        val tool = registry["停用应用"]!!
        assertNull(AgentSecurity.validateArgs(tool, parse("""{"包名":"com.tencent.mm"}""")))
        assertNotNull(AgentSecurity.validateArgs(tool, parse("""{"包名":"com.a_b"}""")))
        assertNotNull(AgentSecurity.validateArgs(tool, parse("""{"包名":"evil app"}""")))
        assertNotNull(AgentSecurity.validateArgs(tool, parse("""{"包名":""}""")))
        assertNotNull(AgentSecurity.validateArgs(tool, parse("""{}""")))
    }

    @Test
    fun validateArgs_rejectsInvalidAppOpsModeAndOp() {
        val tool = registry["设置应用权限"]!!
        assertNull(AgentSecurity.validateArgs(tool, parse("""{"包名":"com.a","操作":"VIBRATE","模式":"允许"}""")))
        assertNotNull(AgentSecurity.validateArgs(tool, parse("""{"包名":"com.a","操作":"NOT_A_REAL_OP","模式":"允许"}""")))
        assertNotNull(AgentSecurity.validateArgs(tool, parse("""{"包名":"com.a","操作":"VIBRATE","模式":"偶尔"}""")))
        assertNotNull(AgentSecurity.validateArgs(tool, parse("""{"包名":"com.a","操作":"VIBRATE"}""")))
    }

    @Test
    fun dispatch_advancedToolWithoutWhitelist_denied() {
        val result = runBlockingTest {
            registry.dispatch(
                "停用应用",
                """{"包名":"com.tencent.mm","已确认":true}""",
                context(switches = AgentToolSwitches().copy(write_disable = true))
            )
        }
        assertTrue(result.contains("白名单") || result.contains("whitelist") || result.contains("拒绝"))
    }

    @Test
    fun dispatch_advancedToolWhitelistedConfirmed_executes() {
        val audit = mutableListOf<AgentAuditEntry>()
        val result = runBlockingTest {
            registry.dispatch(
                "停用应用",
                """{"包名":"com.tencent.mm","已确认":true}""",
                context(
                    switches = AgentToolSwitches().copy(write_disable = true),
                    whitelist = setOf("com.tencent.mm"),
                    audit = audit
                )
            )
        }
        assertTrue(result.isNotBlank())
        assertEquals(1, audit.size)
        assertEquals("停用应用", audit[0].tool)
        assertEquals(true, audit[0].ok)
        assertEquals(true, audit[0].confirmed)
    }

    @Test
    fun dispatch_advancedToolWithoutConfirm_blocked() {
        val audit = mutableListOf<AgentAuditEntry>()
        val result = runBlockingTest {
            registry.dispatch(
                "停用应用",
                """{"包名":"com.tencent.mm"}""",
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
                "强制停止",
                """{"包名":"bad pkg","已确认":true}""",
                context(
                    switches = AgentToolSwitches().copy(write_force_stop = true),
                    whitelist = setOf("bad pkg"),
                    audit = audit
                )
            )
        }
        assertTrue(result.isNotBlank())
        assertEquals(1, audit.size)
        assertEquals(false, audit[0].ok)
    }

    @Test
    fun dispatch_respectsStoreSwitchState() {
        val switches = AgentToolSwitches().copy(read_apps = false)
        val result = runBlockingTest { registry.dispatch("列出应用", "{}", context(switches = switches)) }
        assertTrue(result.contains("未启用") || result.contains("disabled"))
    }

    @Test
    fun auditEntry_serializableShape() {
        val entry = AgentAuditEntry(
            ts = "2026-08-10T12:00:00",
            tool = "停用应用",
            args = """{"包名":"com.x"}""",
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
