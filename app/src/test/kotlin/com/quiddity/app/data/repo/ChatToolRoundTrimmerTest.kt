package com.quiddity.app.data.repo

import com.quiddity.app.data.remote.AssistantToolCall
import com.quiddity.app.data.remote.AssistantToolCallFunction
import com.quiddity.app.data.remote.ChatMessage
import com.quiddity.app.data.remote.ResponsesInputItem
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatToolRoundTrimmerTest {
    private fun plain(role: String, content: String) = ChatMessage(role = role, content = content)

    private fun toolCall(content: String) = ChatMessage(
        role = "assistant",
        content = content,
        tool_calls = listOf(
            AssistantToolCall(
                id = "call-$content",
                type = "function",
                function = AssistantToolCallFunction(name = "tool_$content", arguments = "{}")
            )
        )
    )

    private fun responsesPlain(content: String) = ResponsesInputItem(type = "message", role = "user", content = null)

    private fun responsesCall(content: String) =
        ResponsesInputItem(type = "function_call", role = "assistant", name = "tool_$content", arguments = "{}")

    @Test
    fun `trimToolRounds unchanged when at or below keep limit`() {
        val messages = listOf(plain("user", "u"), toolCall("t1"), plain("tool", "r1"))
        assertEquals(messages, ChatToolRoundTrimmer.trimToolRounds(messages, 2))
        assertEquals(messages, ChatToolRoundTrimmer.trimToolRounds(messages, 10))
    }

    @Test
    fun `trimToolRounds keeps only the last N tool rounds`() {
        val messages = listOf(
            plain("user", "u0"), toolCall("t0"), plain("tool", "r0"),
            plain("user", "u1"), toolCall("t1"), plain("tool", "r1"),
            plain("user", "u2"), toolCall("t2"), plain("tool", "r2"),
            plain("user", "u3"), toolCall("t3"), plain("tool", "r3")
        )
        val result = ChatToolRoundTrimmer.trimToolRounds(messages, 2)
        assertEquals(
            listOf(
                toolCall("t1"), plain("tool", "r1"),
                plain("user", "u2"),
                toolCall("t2"), plain("tool", "r2"),
                plain("user", "u3"), toolCall("t3"), plain("tool", "r3")
            ),
            result
        )
    }

    @Test
    fun `trimToolRounds keeps all tool rounds inside the current task`() {
        // 最后一条 user 消息之后有 3 轮工具调用，即使超过 keepRounds 也全部保留。
        val messages = listOf(
            plain("user", "u0"), toolCall("t0"), plain("tool", "r0"),
            plain("user", "u1"),
            toolCall("t1"), plain("tool", "r1"),
            toolCall("t2"), plain("tool", "r2"),
            toolCall("t3"), plain("tool", "r3")
        )
        val result = ChatToolRoundTrimmer.trimToolRounds(messages, 1)
        assertEquals(
            listOf(
                toolCall("t0"), plain("tool", "r0"),
                plain("user", "u1"),
                toolCall("t1"), plain("tool", "r1"),
                toolCall("t2"), plain("tool", "r2"),
                toolCall("t3"), plain("tool", "r3")
            ),
            result
        )
    }

    @Test
    fun `trimResponsesToolRounds keeps all function call rounds inside the current task`() {
        val input = listOf(
            responsesPlain("u0"), responsesCall("t0"),
            responsesPlain("u1"), responsesCall("t1"),
            responsesCall("t2"), responsesCall("t3")
        )
        val result = ChatToolRoundTrimmer.trimResponsesToolRounds(input, 1)
        assertEquals(
            listOf(responsesCall("t0"), responsesPlain("u1"), responsesCall("t1"), responsesCall("t2"), responsesCall("t3")),
            result
        )
    }

    @Test
    fun `trimResponsesToolRounds trims history but keeps the current task`() {
        val input = listOf(
            responsesPlain("u0"), responsesCall("t0"),
            responsesPlain("u1"), responsesCall("t1"),
            responsesPlain("u2"), responsesCall("t2")
        )
        val result = ChatToolRoundTrimmer.trimResponsesToolRounds(input, 1)
        assertEquals(
            listOf(responsesCall("t1"), responsesPlain("u2"), responsesCall("t2")),
            result
        )
    }

    @Test
    fun `trimToolRounds falls back to last N rounds when no user message`() {
        val messages = listOf(toolCall("t1"), plain("tool", "r1"), toolCall("t2"), plain("tool", "r2"))
        val result = ChatToolRoundTrimmer.trimToolRounds(messages, 1)
        assertEquals(listOf(toolCall("t2"), plain("tool", "r2")), result)
    }

    @Test
    fun `trimResponsesToolRounds unchanged when no function calls`() {
        val input = listOf(responsesPlain("u1"), responsesPlain("u2"))
        assertEquals(input, ChatToolRoundTrimmer.trimResponsesToolRounds(input, 1))
    }
}
