package com.quiddity.app.domain.agent

import com.quiddity.app.data.local.AgentAuditEntry
import com.quiddity.app.data.local.AgentSettings
import com.quiddity.app.data.local.AgentToolSwitches
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ConversationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentToolRegistryTest {

    private val registry = AgentToolRegistry.defaultRegistry()

    private fun context(
        switches: AgentToolSwitches = AgentToolSwitches(),
        whitelist: Set<String> = emptySet(),
        audit: MutableList<AgentAuditEntry> = mutableListOf(),
        autoConfirm: Boolean = false,
        confirmRequest: (suspend (AgentTool, kotlinx.serialization.json.JsonObject) -> Boolean)? = null,
        confirmRequestBatch: (suspend (List<Pair<AgentTool, kotlinx.serialization.json.JsonObject>>) -> Boolean)? = null
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
        autoConfirm = autoConfirm,
        confirmRequest = confirmRequest,
        confirmRequestBatch = confirmRequestBatch
    )

    private fun j(args: String) = args

    @Test
    fun defaultRegistry_containsAllFiftyFourToolsWithMetadata() {
        assertEquals(54, registry.tools().size)

        val basic = listOf(
            "list_apps", "read_screen", "read_notifications", "usage_stats", "foreground_app",
            "app_permissions", "app_install_info", "app_battery", "traffic_ranking",
            "file_access", "screenshot", "system_logs", "app_logs",
            "ocr_image", "notify_self", "toast_monitor", "notification_guard", "app_usage_detail"
        )
        val advancedConfirm = listOf(
            "disable_app", "enable_app", "set_appops", "force_stop", "uninstall_app",
            "move_file", "copy_file", "delete_file",
            "create_file", "write_file", "append_file", "rename_file", "mkdir",
            "read_clipboard", "write_clipboard", "run_shell",
            "click", "long_press", "click_text", "scroll", "global_action",
            "input_text", "click_id", "click_desc", "drag", "scroll_to_text",
            "clear_clipboard", "dismiss_notification", "reply_notification", "schedule_notify",
            "lock_screen"
        )
        val advancedAuto = listOf("read_file", "reveal_file", "list_files", "file_info", "open_app")

        basic.forEach { name ->
            val tool = registry[name]
            assertNotNull(tool, name)
            assertEquals(AgentPermissionLevel.BASIC, tool.level, name)
            assertEquals(AgentConfirmPolicy.AUTO, tool.confirm, name)
            assertTrue(tool.enabledByDefault, name)
        }
        advancedConfirm.forEach { name ->
            val tool = registry[name]
            assertNotNull(tool, name)
            assertEquals(AgentPermissionLevel.ADVANCED, tool.level, name)
            assertEquals(AgentConfirmPolicy.ALWAYS_CONFIRM, tool.confirm, name)
            assertEquals(false, tool.enabledByDefault, name)
        }
        advancedAuto.forEach { name ->
            val tool = registry[name]
            assertNotNull(tool, name)
            assertEquals(AgentPermissionLevel.ADVANCED, tool.level, name)
            assertEquals(AgentConfirmPolicy.AUTO, tool.confirm, name)
            assertEquals(false, tool.enabledByDefault, name)
        }
    }

    @Test
    fun defaultRegistry_toolsAreCategorized() {
        val expected = mapOf(
            AgentToolCategory.SENSE to setOf(
                "read_screen", "read_notifications", "usage_stats", "foreground_app"
            ),
            AgentToolCategory.READ to setOf(
                "list_apps", "app_permissions", "app_install_info", "app_battery",
                "traffic_ranking", "file_access", "screenshot", "system_logs", "app_logs"
            ),
            AgentToolCategory.FILE to setOf(
                "read_file", "list_files", "file_info", "reveal_file",
                "create_file", "write_file", "append_file", "rename_file", "mkdir",
                "move_file", "copy_file", "delete_file"
            ),
            AgentToolCategory.IMAGE to setOf("ocr_image"),
            AgentToolCategory.INTERACT to setOf(
                "notify_self", "read_clipboard", "write_clipboard",
                "clear_clipboard", "dismiss_notification", "reply_notification", "schedule_notify"
            ),
            AgentToolCategory.MONITOR to setOf("toast_monitor", "notification_guard"),
            AgentToolCategory.SYSTEM to setOf("run_shell", "app_usage_detail"),
            AgentToolCategory.TOUCH to setOf(
                "click", "long_press", "click_text", "scroll", "global_action",
                "input_text", "click_id", "click_desc", "drag", "scroll_to_text", "lock_screen"
            ),
            AgentToolCategory.WRITE to setOf(
                "disable_app", "enable_app", "set_appops", "force_stop", "uninstall_app"
            )
        )
        expected.forEach { (category, names) ->
            names.forEach { name ->
                val tool = registry[name]
                assertNotNull(tool, name)
                assertEquals(category, tool.category, name)
            }
        }
        assertEquals(54, registry.tools().size)
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
    fun validateArgs_runShell_onlyAllowsWhitelistedCommand() {
        val tool = registry["run_shell"]!!
        assertNull(AgentSecurity.validateArgs(tool, parse("""{"command":"ls","args":["-la","/sdcard"]}""")))
        assertNull(AgentSecurity.validateArgs(tool, parse("""{"command":"getprop"}""")))
        assertNull(AgentSecurity.validateArgs(tool, parse("""{"command":"cat","args":["/sdcard/note.txt"]}""")))
        assertNotNull(AgentSecurity.validateArgs(tool, parse("""{"command":"rm","args":["/sdcard/x"]}""")))
        assertNotNull(AgentSecurity.validateArgs(tool, parse("""{"command":"sh","args":["-c","id"]}""")))
        assertNotNull(AgentSecurity.validateArgs(tool, parse("""{"command":"ls","args":["/sdcard;rm -rf /"]}""")))
        assertNotNull(AgentSecurity.validateArgs(tool, parse("""{"command":"ls","args":["$(id)"]}""")))
        assertNotNull(AgentSecurity.validateArgs(tool, parse("""{"command":"ls","args":["a","b","c","d","e","f","g","h","i"]}""")))
        assertNotNull(AgentSecurity.validateArgs(tool, parse("""{"command":""}""")))
    }

    @Test
    fun validateArgs_touchTools_validateCoordinatesDirectionAndAction() {
        val click = registry["click"]!!
        assertNull(AgentSecurity.validateArgs(click, parse("""{"x":100,"y":200}""")))
        assertNotNull(AgentSecurity.validateArgs(click, parse("""{"x":-1,"y":200}""")))
        assertNotNull(AgentSecurity.validateArgs(click, parse("""{"x":20001,"y":200}""")))
        assertNotNull(AgentSecurity.validateArgs(click, parse("""{"x":100}""")))

        val scroll = registry["scroll"]!!
        assertNull(AgentSecurity.validateArgs(scroll, parse("""{"direction":"up"}""")))
        assertNull(AgentSecurity.validateArgs(scroll, parse("""{"direction":"down","distance":800}""")))
        assertNotNull(AgentSecurity.validateArgs(scroll, parse("""{"direction":"diagonal"}""")))
        assertNotNull(AgentSecurity.validateArgs(scroll, parse("""{"direction":"up","distance":0}""")))

        val action = registry["global_action"]!!
        assertNull(AgentSecurity.validateArgs(action, parse("""{"action":"back"}""")))
        assertNotNull(AgentSecurity.validateArgs(action, parse("""{"action":"power"}""")))

        val clickText = registry["click_text"]!!
        assertNull(AgentSecurity.validateArgs(clickText, parse("""{"text":"确定"}""")))
        assertNotNull(AgentSecurity.validateArgs(clickText, parse("""{"text":""}""")))

        val drag = registry["drag"]!!
        assertNull(AgentSecurity.validateArgs(drag, parse("""{"x1":0,"y1":0,"x2":100,"y2":200}""")))
        assertNotNull(AgentSecurity.validateArgs(drag, parse("""{"x1":-1,"y1":0,"x2":100,"y2":200}""")))

        val scrollToText = registry["scroll_to_text"]!!
        assertNull(AgentSecurity.validateArgs(scrollToText, parse("""{"text":"设置","maxScrolls":5}""")))
        assertNotNull(AgentSecurity.validateArgs(scrollToText, parse("""{"text":"设置","maxScrolls":11}""")))

        val schedule = registry["schedule_notify"]!!
        assertNull(AgentSecurity.validateArgs(schedule, parse("""{"text":"记得喝水","delaySeconds":300}""")))
        assertNotNull(AgentSecurity.validateArgs(schedule, parse("""{"text":"记得喝水","delaySeconds":0}""")))
    }

    @Test
    fun validateArgs_fileWriteRejectsOversizedContent() {
        val tool = registry["write_file"]!!
        assertNull(AgentSecurity.validateArgs(tool, parse("""{"path":"/sdcard/a.txt","content":"你好"}""")))
        val oversized = "a".repeat(100_001)
        assertNotNull(
            AgentSecurity.validateArgs(
                tool,
                parse("""{"path":"/sdcard/a.txt","content":"$oversized"}""")
            )
        )
    }

    @Test
    fun validateArgs_revealFilePkgIsNotWhitelistedTarget() {
        val tool = registry["reveal_file"]!!
        assertNull(AgentSecurity.validateArgs(tool, parse("""{"path":"/sdcard","pkg":"com.google.android.documentsui"}""")))
        val gate = AgentSecurity.whitelistGate(
            tool,
            parse("""{"path":"/sdcard","pkg":"com.google.android.documentsui"}"""),
            emptySet()
        )
        assertNull(gate)
    }

    @Test
    fun validateArgs_toastMonitorRejectsInvalidMaxItems() {
        val tool = registry["toast_monitor"]!!
        assertNull(AgentSecurity.validateArgs(tool, parse("""{"maxItems":10}""")))
        assertNotNull(AgentSecurity.validateArgs(tool, parse("""{"maxItems":51}""")))
        assertNotNull(AgentSecurity.validateArgs(tool, parse("""{"maxItems":0}""")))
    }

    @Test
    fun notificationGuard_switchedBySenseNotifications() {
        val on = AgentToolSwitches().copy(sense_notifications = true)
        val off = AgentToolSwitches().copy(sense_notifications = false)
        assertTrue(AgentSecurity.isSwitchEnabled(registry["notification_guard"]!!, on))
        assertFalse(AgentSecurity.isSwitchEnabled(registry["notification_guard"]!!, off))
    }

    @Test
    fun dispatchAll_batchConfirmApproved_executesAllAndAuditsOnce() {
        val audit = mutableListOf<AgentAuditEntry>()
        var confirmCount = 0
        val result = runBlockingTest {
            registry.dispatchAll(
                listOf(
                    AgentToolCallRequest("create_file", """{"path":"/sdcard/a.txt"}"""),
                    AgentToolCallRequest("list_files", """{"path":"/sdcard"}""")
                ),
                context(
                    switches = AgentToolSwitches().copy(write_files = true, read_files = true),
                    audit = audit,
                    confirmRequestBatch = { items ->
                        confirmCount++
                        assertEquals(1, items.size)
                        items.first().first.name == "create_file"
                    }
                )
            )
        }
        assertEquals(2, result.size)
        assertTrue(result[0].isNotBlank())
        assertTrue(result[1].isNotBlank())
        assertEquals(1, confirmCount)
        assertTrue(audit.any { it.tool == "create_file" && it.ok })
        assertTrue(audit.any { it.tool == "list_files" && it.ok })
    }

    @Test
    fun dispatchAll_batchConfirmDeclined_cancelsConfirmedToolsOnly() {
        val audit = mutableListOf<AgentAuditEntry>()
        val result = runBlockingTest {
            registry.dispatchAll(
                listOf(
                    AgentToolCallRequest("create_file", """{"path":"/sdcard/a.txt"}"""),
                    AgentToolCallRequest("list_files", """{"path":"/sdcard"}""")
                ),
                context(
                    switches = AgentToolSwitches().copy(write_files = true, read_files = true),
                    audit = audit,
                    confirmRequestBatch = { _ -> false }
                )
            )
        }
        assertTrue(result[0].contains("取消"))
        assertTrue(result[1].isNotBlank())
    }

    @Test
    fun dispatchAll_fullMode_skipsConfirmation() {
        val audit = mutableListOf<AgentAuditEntry>()
        val result = runBlockingTest {
            registry.dispatchAll(
                listOf(AgentToolCallRequest("create_file", """{"path":"/sdcard/a.txt"}""")),
                context(
                    switches = AgentToolSwitches().copy(write_files = true),
                    audit = audit,
                    autoConfirm = true
                )
            )
        }
        assertEquals(1, result.size)
        assertTrue(result[0].isNotBlank())
        assertTrue(audit.any { it.tool == "create_file" && it.ok && it.confirmed })
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
