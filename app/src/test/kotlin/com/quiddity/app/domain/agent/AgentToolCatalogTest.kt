package com.quiddity.app.domain.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class AgentToolCatalogTest {
    private fun args(vararg pairs: Pair<String, String>): JsonObject {
        val sb = StringBuilder("{")
        pairs.forEachIndexed { index, (k, v) ->
            if (index > 0) sb.append(",")
            sb.append("\"").append(k).append("\":\"").append(v).append("\"")
        }
        sb.append("}")
        return Json.parseToJsonElement(sb.toString()).jsonObject
    }

    @Test
    fun `actionDescription renders coordinate clicks`() {
        assertEquals(
            "模拟点击（300, 1200）",
            agentActionDescription("click", args("x" to "300", "y" to "1200"))
        )
    }

    @Test
    fun `actionDescription renders scroll direction`() {
        assertEquals("向上滑动", agentActionDescription("scroll", args("direction" to "up")))
    }

    @Test
    fun `actionDescription renders file and app ops`() {
        assertEquals("写入文件 /sdcard/a.txt", agentActionDescription("write_file", args("path" to "/sdcard/a.txt")))
        assertEquals("强制停止应用 com.tencent.mm", agentActionDescription("force_stop", args("pkg" to "com.tencent.mm")))
        assertEquals("打开应用 com.example", agentActionDescription("open_app", args("pkg" to "com.example")))
    }

    @Test
    fun `actionDescription renders shell and screen`() {
        assertEquals("执行 Shell 命令", agentActionDescription("run_shell", args()))
        assertEquals("读取屏幕", agentActionDescription("read_screen", args()))
    }

    @Test
    fun `displayName covers known tools and falls back`() {
        assertEquals("模拟点击", agentDisplayName("click"))
        assertEquals("读取屏幕", agentDisplayName("read_screen"))
        assertEquals("not_a_real_tool", agentDisplayName("not_a_real_tool"))
    }

    @Test
    fun `toolExplanation returns non-blank for known tools`() {
        assertEquals(true, agentToolExplanation("write_file").isNotBlank())
        assertEquals(true, agentToolExplanation("read_screen").isNotBlank())
    }
}
