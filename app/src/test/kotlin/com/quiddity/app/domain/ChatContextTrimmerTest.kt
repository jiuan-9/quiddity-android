package com.quiddity.app.domain

import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import kotlin.test.Test
import kotlin.test.assertEquals

class ChatContextTrimmerTest {
    private fun msg(id: String, role: Role) =
        Message(id = id, conversationId = "c1", role = role, content = id, timestamp = 0L)

    private fun user(id: String) = msg(id, Role.USER)
    private fun ai(id: String) = msg(id, Role.ASSISTANT)

    @Test
    fun `takeLastRounds returns empty for empty or non-positive rounds`() {
        assertEquals(emptyList<Message>(), ChatContextTrimmer.takeLastRounds(emptyList(), 5))
        assertEquals(emptyList<Message>(), ChatContextTrimmer.takeLastRounds(listOf(user("u1")), 0))
        assertEquals(emptyList<Message>(), ChatContextTrimmer.takeLastRounds(listOf(user("u1")), -1))
    }

    @Test
    fun `takeLastRounds returns empty when no user messages`() {
        val allAi = listOf(ai("a1"), ai("a2"))
        assertEquals(emptyList<Message>(), ChatContextTrimmer.takeLastRounds(allAi, 2))
    }

    @Test
    fun `takeLastRounds returns all messages when rounds cover all users`() {
        val messages = listOf(user("u1"), ai("a1"), user("u2"), ai("a2"))
        assertEquals(messages, ChatContextTrimmer.takeLastRounds(messages, 2))
        assertEquals(messages, ChatContextTrimmer.takeLastRounds(messages, 10))
    }

    @Test
    fun `takeLastRounds keeps only the last N user rounds`() {
        val messages = listOf(user("u1"), ai("a1"), user("u2"), ai("a2"), user("u3"), ai("a3"))
        val result = ChatContextTrimmer.takeLastRounds(messages, 2)
        assertEquals(listOf(user("u2"), ai("a2"), user("u3"), ai("a3")), result)
    }

    @Test
    fun `takeLastRounds buffer extends backward before the first kept user`() {
        val messages = listOf(user("u1"), ai("a1"), user("u2"), ai("a2"))
        val result = ChatContextTrimmer.takeLastRounds(messages, 1, buffer = 1)
        assertEquals(listOf(ai("a1"), user("u2"), ai("a2")), result)
    }

    @Test
    fun `takeLastRounds buffer clamps at list start`() {
        val messages = listOf(user("u1"), user("u2"), ai("a2"))
        val result = ChatContextTrimmer.takeLastRounds(messages, 1, buffer = 10)
        assertEquals(messages, result)
    }

    @Test
    fun `takeFromRound returns empty for empty list`() {
        assertEquals(emptyList<Message>(), ChatContextTrimmer.takeFromRound(emptyList(), 0))
    }

    @Test
    fun `takeFromRound startRound zero returns all`() {
        val messages = listOf(user("u1"), ai("a1"), user("u2"))
        assertEquals(messages, ChatContextTrimmer.takeFromRound(messages, 0))
    }

    @Test
    fun `takeFromRound beyond users returns empty`() {
        val messages = listOf(user("u1"), ai("a1"))
        assertEquals(emptyList<Message>(), ChatContextTrimmer.takeFromRound(messages, 2))
    }

    @Test
    fun `takeFromRound starts at the given user round`() {
        val messages = listOf(user("u1"), ai("a1"), user("u2"), ai("a2"), user("u3"))
        val result = ChatContextTrimmer.takeFromRound(messages, 1)
        assertEquals(listOf(user("u2"), ai("a2"), user("u3")), result)
    }
}
