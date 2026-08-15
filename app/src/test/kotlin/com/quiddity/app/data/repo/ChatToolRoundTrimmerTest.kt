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
    fun `trimToolRounds keeps full list when exceeding keep limit (current behavior)`() {
        val messages = listOf(
            plain("user", "u1"), toolCall("t1"), plain("tool", "r1"),
            plain("user", "u2"), toolCall("t2"), plain("tool", "r2"),
            plain("user", "u3"), toolCall("t3"), plain("tool", "r3")
        )
        assertEquals(messages, ChatToolRoundTrimmer.trimToolRounds(messages, 2))
    }

    @Test
    fun `trimResponsesToolRounds keeps full list when exceeding keep limit (current behavior)`() {
        val input = listOf(
            responsesPlain("u1"), responsesCall("t1"),
            responsesPlain("u2"), responsesCall("t2"),
            responsesPlain("u3"), responsesCall("t3")
        )
        assertEquals(input, ChatToolRoundTrimmer.trimResponsesToolRounds(input, 1))
    }

    @Test
    fun `trimResponsesToolRounds unchanged when no function calls`() {
        val input = listOf(responsesPlain("u1"), responsesPlain("u2"))
        assertEquals(input, ChatToolRoundTrimmer.trimResponsesToolRounds(input, 1))
    }
}
