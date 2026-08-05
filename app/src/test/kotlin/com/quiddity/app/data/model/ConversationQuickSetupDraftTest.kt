package com.quiddity.app.data.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 快速设定草稿字段序列化测试（旧 JSON 兼容 + 往返一致）。
 */
class ConversationQuickSetupDraftTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `old json without draft field decodes to empty draft`() {
        val old = """
            {"id":"c1","title":"测试","createdAt":1,"updatedAt":1}
        """.trimIndent()
        val conv = json.decodeFromString(Conversation.serializer(), old)
        assertEquals("", conv.quickSetupDraft)
    }

    @Test
    fun `draft field round trips`() {
        val conv = Conversation(
            id = "c1",
            createdAt = 1,
            updatedAt = 1,
            quickSetupDraft = "温柔的学姐"
        )
        val encoded = json.encodeToString(Conversation.serializer(), conv)
        val decoded = json.decodeFromString(Conversation.serializer(), encoded)
        assertEquals("温柔的学姐", decoded.quickSetupDraft)
    }
}
