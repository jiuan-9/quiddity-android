package com.quiddity.app.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [ChatStreamParser] 单元测试（6.6.3 流式 tool_calls 增量解析）。
 */
class ChatStreamParserTest {

    private val parser = ChatStreamParser()

    @Test
    fun `finish reason length is captured as truncation signal`() {
        val chunk = parser.parseChunk(
            """{"choices":[{"delta":{"content":"最后一句"}, "finish_reason":"length"}]}"""
        )
        assertEquals("最后一句", chunk?.content)
        assertEquals("length", chunk?.finishReason, "finish_reason=length 应被捕获")
    }

    @Test
    fun `finish reason is null on normal content chunks`() {
        val chunk = parser.parseChunk(
            """{"choices":[{"delta":{"content":"正常内容"}}]}"""
        )
        assertNull(chunk?.finishReason, "普通分片不应有 finish_reason")
    }

    @Test
    fun `reasoning content is parsed separately from content`() {
        val chunk = parser.parseChunk(
            """{"choices":[{"delta":{"reasoning_content":"先分析需求，","content":null}}]}"""
        )
        assertEquals("先分析需求，", chunk?.reasoning, "reasoning_content 应解析到 reasoning 字段")
        assertNull(chunk?.content, "该分片不应有普通内容")
    }

    @Test
    fun `acceptChunk keeps reasoning while aggregating`() {
        parser.acceptChunk(
            """{"choices":[{"delta":{"reasoning_content":"思考第一段"}}]}"""
        )
        parser.acceptChunk(
            """{"choices":[{"delta":{"reasoning_content":"思考第二段"}}]}"""
        )
        val first = parser.acceptChunk(
            """{"choices":[{"delta":{"content":"回复内容"}}]}"""
        )
        assertEquals("回复内容", first?.content)
        assertNull(first?.reasoning)
    }

    @Test
    fun `tool calls are aggregated by index across stream chunks`() {
        parser.acceptChunk(
            """{"choices": [{"delta": {"role": "assistant", "content": null, "tool_calls": [{"index": 0, "id": "call_abc", "type": "function", "function": {"name": "read_memory", "arguments": ""}}]}}]}"""
        )
        parser.acceptChunk(
            """{"choices": [{"delta": {"tool_calls": [{"index": 0, "function": {"arguments": "{\"topic\":\"项目A\""}}]}}]}"""
        )
        parser.acceptChunk(
            """{"choices": [{"delta": {"tool_calls": [{"index": 0, "function": {"arguments": ",\"date\":\"7-15\"}"}}]}}]}"""
        )
        val calls = parser.takeToolCalls()
        assertEquals(1, calls.size, "同一 index 的增量应聚合为一次完整调用")
        assertEquals("read_memory", calls[0].name, "函数名分片应拼接为 read_memory")
        assertEquals("""{"topic":"项目A","date":"7-15"}""", calls[0].arguments, "参数分片应完整拼接")
        assertEquals("call_abc", calls[0].id)
    }

    @Test
    fun `multiple tool call indices aggregate independently`() {
        parser.acceptChunk(
            """{"choices":[{"delta":{"tool_calls":[""" +
                """{"index":0,"function":{"name":"read_memory","arguments":"A"}},""" +
                """{"index":1,"function":{"name":"read_memory","arguments":"B"}}]}}]}"""
        )
        parser.acceptChunk(
            """{"choices":[{"delta":{"tool_calls":[""" +
                """{"index":0,"function":{"arguments":"1"}},""" +
                """{"index":1,"function":{"arguments":"2"}}]}}]}"""
        )
        val calls = parser.takeToolCalls()
        assertEquals(2, calls.size)
        assertEquals("A1", calls.first { it.index == 0 }.arguments)
        assertEquals("B2", calls.first { it.index == 1 }.arguments)
    }

    @Test
    fun `takeToolCalls clears accumulation state`() {
        parser.acceptChunk(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"name":"read_memory","arguments":"x"}}]}}]}"""
        )
        assertEquals(1, parser.takeToolCalls().size)
        assertEquals(0, parser.takeToolCalls().size, "再次取应返回空列表")
    }

    @Test
    fun `content and tool calls coexist`() {
        val chunk = parser.acceptChunk(
            """{"choices":[{"delta":{"content":"好的","tool_calls":""" +
                """[{"index":0,"function":{"name":"read_memory","arguments":"{}"}}]}}]}"""
        )
        assertEquals("好的", chunk?.content)
        assertEquals(1, chunk?.toolCalls?.size)
        val calls = parser.takeToolCalls()
        assertTrue(calls.isNotEmpty(), "出现过 tool_calls 增量应可聚合")
    }
}
