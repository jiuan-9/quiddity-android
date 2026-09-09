package com.quiddity.app.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * DeepSeek Responses API 语义化 SSE 解析测试。
 */
class ResponsesStreamParserTest {

    private val parser = ResponsesStreamParser()

    @Test
    fun `output text delta yields content`() {
        val chunk = parser.acceptEvent(
            "response.output_text.delta",
            """{"type":"response.output_text.delta","item_id":"msg_1","output_index":0,"content_index":0,"delta":"你好"}"""
        )
        assertEquals("你好", chunk?.content)
    }

    @Test
    fun `incomplete terminal event flags truncation`() {
        val chunk = parser.acceptEvent(
            "response.incomplete",
            """{"type":"response.incomplete","incomplete_details":{"reason":"max_output_tokens"}}"""
        )
        assertNull(chunk, "incomplete 应为终态事件")
        assertTrue(parser.terminatedIncomplete, "response.incomplete 应标记为被截断")
    }

    @Test
    fun `completed terminal event does not flag truncation`() {
        val chunk = parser.acceptEvent(
            "response.completed",
            """{"type":"response.completed","response":{"id":"resp_1"}}"""
        )
        assertNull(chunk, "completed 应为终态事件")
        assertFalse(parser.terminatedIncomplete, "response.completed 不应标记为被截断")
    }

    @Test
    fun `reasoning delta is ignored as content`() {
        val chunk = parser.acceptEvent(
            "response.reasoning_text.delta",
            """{"type":"response.reasoning_text.delta","item_id":"rs_1","delta":"思考中"}"""
        )
        assertEquals("", chunk?.content)
    }

    @Test
    fun `function call arguments are aggregated and finalized on item done`() {
        parser.acceptEvent(
            "response.function_call_arguments.delta",
            """{"type":"response.function_call_arguments.delta","item_id":"fc_1","output_index":0,"delta":"{\"query\":"}"""
        )
        parser.acceptEvent(
            "response.function_call_arguments.delta",
            """{"type":"response.function_call_arguments.delta","item_id":"fc_1","output_index":0,"delta":"\"项目A\"}"}"""
        )
        parser.acceptEvent(
            "response.output_item.done",
            """{"type":"response.output_item.done","output_index":0,"item":{"type":"function_call","id":"fc_1","name":"read_memory","arguments":"{\"query\":\"项目A\"}"}}"""
        )
        val calls = parser.takeToolCalls()
        assertEquals(1, calls.size)
        assertEquals("fc_1", calls[0].id)
        assertEquals("read_memory", calls[0].name)
        assertEquals("""{"query":"项目A"}""", calls[0].arguments)
    }

    @Test
    fun `completed event ends stream and keeps aggregated calls`() {
        parser.acceptEvent(
            "response.function_call_arguments.delta",
            """{"item_id":"fc_1","delta":"{}"}"""
        )
        parser.acceptEvent(
            "response.output_item.done",
            """{"item":{"type":"function_call","id":"fc_1","name":"read_memory"}}"""
        )
        assertNull(parser.acceptEvent("response.completed", """{"type":"response.completed"}"""))
        assertEquals(1, parser.takeToolCalls().size)
    }

    @Test
    fun `failed event ends stream and carries error message`() {
        assertNull(
            parser.acceptEvent(
                "response.failed",
                """{"type":"response.failed","error":{"code":"timeout","message":"模型超时"}}"""
            )
        )
        assertEquals("模型超时", parser.terminalError)
    }

    @Test
    fun `takeToolCalls clears accumulation state`() {
        parser.acceptEvent(
            "response.function_call_arguments.delta",
            """{"item_id":"fc_1","delta":"{}"}"""
        )
        parser.acceptEvent(
            "response.output_item.done",
            """{"item":{"type":"function_call","id":"fc_1","name":"read_memory"}}"""
        )
        assertEquals(1, parser.takeToolCalls().size)
        assertEquals(0, parser.takeToolCalls().size, "再次取应返回空列表")
    }

    @Test
    fun `web search call items are informational and ignored`() {
        val chunk = parser.acceptEvent(
            "response.output_item.added",
            """{"type":"response.output_item.added","item":{"type":"web_search_call","id":"ws_1","action":{"type":"search","query":"天气"}}}"""
        )
        assertEquals("", chunk?.content)
        assertTrue(parser.takeToolCalls().isEmpty(), "服务端 web_search 无需客户端执行")
    }
}
