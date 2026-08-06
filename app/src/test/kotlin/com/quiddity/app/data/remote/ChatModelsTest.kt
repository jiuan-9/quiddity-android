package com.quiddity.app.data.remote

import com.quiddity.app.util.QuiddityConstants
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
 * 聊天请求模型默认值与 Responses API 序列化测试。
 */
class ChatModelsTest {

    @Test
    fun `chat completion default temperature follows official default`() {
        val request = ChatCompletionRequest(model = "m", messages = emptyList())
        assertEquals(QuiddityConstants.DEFAULT_TEMPERATURE, request.temperature)
    }

    @Test
    fun `responses request serializes web search tool and omits null fields`() {
        val json = Json {
            explicitNulls = false
            encodeDefaults = true
        }
        val request = DeepSeekResponsesRequest(
            model = "deepseek-v4-flash",
            input = listOf(ResponsesInputItem(role = "user", content = "你好")),
            instructions = "系统指令",
            tools = listOf(ResponsesTool(type = "web_search"))
        )
        val text = json.encodeToString(DeepSeekResponsesRequest.serializer(), request)
        assertTrue(text.contains("web_search"), "tools 必须携带服务端 web_search")
        assertFalse(text.contains("\"instructions\":null"), "null 字段不应编码")
        assertFalse(text.contains("\"tool_choice\":null"), "未设置的 tool_choice 不应编码")

        val decoded = json.decodeFromString(DeepSeekResponsesRequest.serializer(), text)
        assertEquals("你好", decoded.input.first().content)
        assertEquals("user", decoded.input.first().role)
        assertEquals("web_search", decoded.tools?.first()?.type)
    }

    @Test
    fun `responses function tool carries function metadata`() {
        val tool = ResponsesTool(
            type = "function",
            name = "read_memory",
            description = "检索记忆",
            parameters = kotlinx.serialization.json.JsonObject(emptyMap())
        )
        val json = Json { encodeDefaults = true }
        val text = json.encodeToString(ResponsesTool.serializer(), tool)
        assertTrue(text.contains("\"name\":\"read_memory\""))
        assertTrue(text.contains("\"description\":\"检索记忆\""))
    }
}
