package com.quiddity.app.data.model

import com.quiddity.app.util.QuiddityConstants
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 群聊模型序列化往返测试（防"写得出、读不回"导致重启闪退）。
 * 与 [ConversationStore] 使用相同的 Json 配置（ignoreUnknownKeys + encodeDefaults）。
 */
class GroupModelSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val now = 1720000000000L

    @Test
    fun `group conversation round trips`() {
        val group = Conversation(
            id = "g1",
            title = "新群聊 1",
            createdAt = now,
            updatedAt = now,
            type = ConversationType.GROUP,
            memberConversationIds = listOf("p1", "p2"),
            groupMemory = "摘要",
            groupContextLimit = 50,
            stopMode = "B"
        )
        val text = json.encodeToString(Conversation.serializer(), group)
        val decoded = json.decodeFromString(Conversation.serializer(), text)
        assertEquals(group, decoded)
    }

    @Test
    fun `old conversation json without new fields decodes with defaults`() {
        val oldJson = """
            {"id":"p1","title":"新会话","createdAt":$now,"updatedAt":$now}
        """.trimIndent()
        val decoded = json.decodeFromString(Conversation.serializer(), oldJson)
        assertEquals(ConversationType.SOLO, decoded.type)
        assertEquals(QuiddityConstants.GROUP_DEFAULT_CONTEXT_LIMIT, decoded.groupContextLimit)
        assertEquals(QuiddityConstants.GROUP_DEFAULT_STOP_MODE, decoded.stopMode)
        assertEquals("", decoded.groupBackground)
        assertEquals(QuiddityConstants.GROUP_DEFAULT_BACKGROUND_MODE, decoded.groupBackgroundMode)
        assertTrue(decoded.memberConversationIds.isEmpty())
    }

    @Test
    fun `message with senderId round trips`() {
        val msg = Message(
            id = "m1",
            conversationId = "g1",
            role = Role.ASSISTANT,
            content = "你好",
            timestamp = now,
            senderId = "p1"
        )
        val text = json.encodeToString(Message.serializer(), msg)
        val decoded = json.decodeFromString(Message.serializer(), text)
        assertEquals("p1", decoded.senderId)
    }

    @Test
    fun `conversation list round trips like store file`() {
        val list = listOf(
            Conversation(id = "p1", createdAt = now, updatedAt = now),
            Conversation(
                id = "g1",
                title = "新群聊 1",
                createdAt = now,
                updatedAt = now,
                type = ConversationType.GROUP,
                memberConversationIds = listOf("p1")
            )
        )
        val text = json.encodeToString(ListSerializer(Conversation.serializer()), list)
        val decoded = json.decodeFromString(ListSerializer(Conversation.serializer()), text)
        assertEquals(list, decoded)
    }

    @Test
    fun `app settings round trips`() {
        val settings = AppSettings.Default.copy(
            groupTutorialSeen = true,
            soloChatCounter = 3,
            groupChatCounter = 2
        )
        val text = json.encodeToString(AppSettings.serializer(), settings)
        val decoded = json.decodeFromString(AppSettings.serializer(), text)
        assertEquals(true, decoded.groupTutorialSeen)
        assertEquals(3, decoded.soloChatCounter)
        assertEquals(2, decoded.groupChatCounter)
    }
}
