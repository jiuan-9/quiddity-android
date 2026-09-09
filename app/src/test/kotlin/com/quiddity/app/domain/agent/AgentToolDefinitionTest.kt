package com.quiddity.app.domain.agent

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Agent 工具定义转换与分发决策测试：
 * 工具声明可转为 OpenAI 兼容 ToolDefinition；仅 AGENT 会话走注册表分发。
 */
class AgentToolDefinitionTest {

    private val registry = AgentToolRegistry.defaultRegistry()

    @Test
    fun toolToDefinition_mapsAllFields() {
        val tool = registry["list_apps"]!!
        val def = tool.toToolDefinition()
        assertEquals("list_apps", def.function.name)
        assertEquals(tool.description, def.function.description)
        val type = def.function.parameters["type"] as? JsonPrimitive
        assertEquals("object", type?.content)
    }

    @Test
    fun everyToolSerializesToValidOpenAiSchema() {
        val json = kotlinx.serialization.json.Json {
            encodeDefaults = true
        }
        val sb = StringBuilder()
        registry.tools().sortedBy { it.name }.forEach { tool ->
            val def = tool.toToolDefinition()
            val encoded = json.encodeToString(
                com.quiddity.app.data.remote.ToolDefinition.serializer(),
                def
            )
            sb.append(encoded).append('\n')
            val type = def.function.parameters["type"] as? JsonPrimitive
            assertEquals("object", type?.content, "工具 ${tool.name} parameters.type 应为 object")
            assertTrue(def.function.name.isNotBlank(), "工具 name 非空")
            assertTrue(def.function.description.isNotBlank(), "工具 ${tool.name} description 非空")
            val parsed = json.parseToJsonElement(encoded) as? kotlinx.serialization.json.JsonObject
            assertEquals("function", parsed?.get("type")?.let { (it as? JsonPrimitive)?.content }, tool.name)
        }
        java.io.File("build/tool-defs.json").writeText(sb.toString())
    }

    private fun testContext(): AgentContext = AgentContext(
        conversation = null,
        switches = com.quiddity.app.data.local.AgentToolSwitches(),
        blacklist = emptySet()
    )
}
