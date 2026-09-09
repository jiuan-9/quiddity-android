package com.quiddity.app.data.repo

import com.quiddity.app.data.remote.ChatCompletionRequest
import com.quiddity.app.data.remote.ChatMessage
import com.quiddity.app.data.remote.DeepSeekResponsesRequest
import com.quiddity.app.data.remote.ResponsesInputItem
import com.quiddity.app.data.remote.ResponsesTool
import com.quiddity.app.data.remote.ToolDefinition
import com.quiddity.app.data.remote.ToolFunction
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 最终回复兜底请求构造测试：截断续写 / 空回复重试的请求体拼接。
 *
 * 回归保护：
 * - 截断时携带「已输出正文 + 提示语」，供模型从断点继续；
 * - 空回复时只追加提示语，不提交空 assistant 消息（部分接口会拒绝）；
 * - Completions 与 Responses 两条路径行为一致，且保留 tools / tool_choice。
 */
class ChatRepositoryContinueRequestTest {

    private val completions = ChatRoundRequest.Completions(
        ChatCompletionRequest(
            model = "deepseek-chat",
            messages = listOf(
                ChatMessage(role = "system", content = "s"),
                ChatMessage(role = "user", content = "hi")
            ),
            tools = listOf(ToolDefinition(function = ToolFunction("click", "click"))),
            tool_choice = "auto"
        ),
        apiUrl = "https://api.deepseek.com/v1/chat/completions"
    )

    private val responses = ChatRoundRequest.Responses(
        DeepSeekResponsesRequest(
            model = "deepseek-reasoner",
            input = listOf(
                ResponsesInputItem(type = "message", role = "user", content = JsonPrimitive("hi"))
            ),
            tools = listOf(ResponsesTool(type = "function", name = "click")),
            tool_choice = JsonPrimitive("auto")
        ),
        apiUrl = "https://api.deepseek.com/v1/responses"
    )

    @Test
    fun `completions truncated appends partial content and nudge`() {
        val next = buildContinueRequest(completions, "已输出一半", "请继续")
        val messages = (next as ChatRoundRequest.Completions).request.messages
        assertEquals(4, messages.size)
        assertEquals("assistant", messages[2].role)
        assertEquals("已输出一半", messages[2].content)
        assertEquals("user", messages[3].role)
        assertEquals("请继续", messages[3].content)
        assertEquals("auto", next.request.tool_choice)
        assertEquals(1, next.request.tools?.size)
    }

    @Test
    fun `completions empty reply appends only nudge`() {
        val next = buildContinueRequest(completions, null, "请重新回复")
        val messages = (next as ChatRoundRequest.Completions).request.messages
        assertEquals(3, messages.size)
        assertEquals("user", messages[2].role)
        assertEquals("请重新回复", messages[2].content)
    }

    @Test
    fun `responses truncated appends assistant and user items`() {
        val next = buildContinueRequest(responses, "部分回复", "请继续")
        val input = (next as ChatRoundRequest.Responses).request.input
        assertEquals(3, input.size)
        assertEquals("assistant", input[1].role)
        assertEquals("部分回复", (input[1].content as? JsonPrimitive)?.content)
        assertEquals("user", input[2].role)
        assertEquals("请继续", (input[2].content as? JsonPrimitive)?.content)
        assertEquals(1, next.request.tools?.size)
    }

    @Test
    fun `responses empty reply appends only user item`() {
        val next = buildContinueRequest(responses, null, "请重新回复")
        val input = (next as ChatRoundRequest.Responses).request.input
        assertEquals(2, input.size)
        assertEquals("user", input[1].role)
        assertEquals("请重新回复", (input[1].content as? JsonPrimitive)?.content)
        assertNull(input[1].call_id)
    }
}
