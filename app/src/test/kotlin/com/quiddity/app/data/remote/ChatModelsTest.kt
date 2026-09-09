package com.quiddity.app.data.remote

import com.quiddity.app.util.QuiddityConstants
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    fun `chat completion serializes reasoning effort only when set`() {
        val json = Json {
            explicitNulls = false
            encodeDefaults = true
        }
        val withThinking = ChatCompletionRequest(
            model = "deepseek-v4-flash",
            messages = emptyList(),
            reasoning_effort = "low"
        )
        val thinkingText = json.encodeToString(ChatCompletionRequest.serializer(), withThinking)
        assertTrue(thinkingText.contains("\"reasoning_effort\":\"low\""), "思考开启时必须携带 reasoning_effort")

        val withoutThinking = ChatCompletionRequest(
            model = "deepseek-v4-flash",
            messages = emptyList()
        )
        val plainText = json.encodeToString(ChatCompletionRequest.serializer(), withoutThinking)
        assertFalse(plainText.contains("reasoning_effort"), "思考关闭时不得携带 reasoning_effort 字段")
    }

    @Test
    fun `responses request serializes reasoning effort only when set`() {
        val json = Json {
            explicitNulls = false
            encodeDefaults = true
        }
        val withThinking = DeepSeekResponsesRequest(
            model = "deepseek-v4-flash",
            input = emptyList(),
            reasoning_effort = "high"
        )
        val text = json.encodeToString(DeepSeekResponsesRequest.serializer(), withThinking)
        assertTrue(text.contains("\"reasoning_effort\":\"high\""))
        assertFalse(
            json.encodeToString(
                DeepSeekResponsesRequest.serializer(),
                DeepSeekResponsesRequest(model = "deepseek-v4-flash", input = emptyList())
            ).contains("reasoning_effort")
        )
    }

    @Test
    fun `responses request serializes web search tool and omits null fields`() {
        val json = Json {
            explicitNulls = false
            encodeDefaults = true
        }
        val request = DeepSeekResponsesRequest(
            model = "deepseek-v4-flash",
            input = listOf(
                ResponsesInputItem(
                    role = "user",
                    content = kotlinx.serialization.json.JsonPrimitive("你好")
                )
            ),
            instructions = "系统指令",
            tools = listOf(ResponsesTool(type = "web_search"))
        )
        val text = json.encodeToString(DeepSeekResponsesRequest.serializer(), request)
        assertTrue(text.contains("web_search"), "tools 必须携带服务端 web_search")
        assertFalse(text.contains("\"instructions\":null"), "null 字段不应编码")
        assertFalse(text.contains("\"tool_choice\":null"), "未设置的 tool_choice 不应编码")

        val decoded = json.decodeFromString(DeepSeekResponsesRequest.serializer(), text)
        assertEquals("你好", (decoded.input.first().content as kotlinx.serialization.json.JsonPrimitive).content)
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

    @Test
    fun `thinking request serializes thinking and max_completion_tokens`() {
        val json = Json {
            explicitNulls = false
            encodeDefaults = true
        }
        val request = ChatCompletionRequest(
            model = "thinking-model",
            messages = emptyList(),
            max_completion_tokens = 4096,
            thinking = ThinkingMode("enabled")
        )
        val text = json.encodeToString(ChatCompletionRequest.serializer(), request)
        assertTrue(text.contains("\"thinking\":{\"type\":\"enabled\"}"), "思考开启时必须携带 thinking.type")
        assertTrue(text.contains("\"max_completion_tokens\":4096"), "新一代模型使用 max_completion_tokens")
        assertFalse(text.contains("\"max_tokens\""), "未设置 max_tokens 时不得编码该字段")

        val plain = ChatCompletionRequest(model = "glm-5.2", messages = emptyList())
        val plainText = json.encodeToString(ChatCompletionRequest.serializer(), plain)
        assertFalse(plainText.contains("thinking"), "其他服务商不携带 thinking 字段")
        assertFalse(plainText.contains("max_completion_tokens"), "其他服务商不携带 max_completion_tokens")
    }
}
