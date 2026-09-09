package com.quiddity.app.util

import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.data.model.ExportPayload
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import com.quiddity.app.data.model.ConversationBundle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 群聊导入校验测试（方案十七.2-3：成员引用完整恢复，悬空整体跳过）。
 */
class DataPorterGroupTest {

    private val now = 1720000000000L

    private fun solo(id: String): Conversation = Conversation(
        id = id,
        createdAt = now,
        updatedAt = now
    )

    private fun group(id: String, members: List<String>): Conversation = Conversation(
        id = id,
        createdAt = now,
        updatedAt = now,
        type = ConversationType.GROUP,
        memberConversationIds = members
    )

    private fun payload(
        privateIds: List<String>,
        groups: List<Conversation>
    ): ExportPayload {
        val bundles = privateIds.map { id ->
            ConversationBundle(
                conversation = solo(id),
                messages = listOf(
                    Message(
                        id = "m_$id",
                        conversationId = id,
                        role = Role.USER,
                        content = "你好",
                        timestamp = now
                    )
                )
            )
        }
        return ExportPayload(
            exportedAt = now,
            settings = com.quiddity.app.data.model.AppSettings.Default,
            conversations = emptyList(),
            messages = emptyMap(),
            privateChats = bundles,
            groupChats = groups.map { ConversationBundle(conversation = it) }
        )
    }

    @Test
    fun `group with complete member references is not skipped`() {
        val p = payload(listOf("a", "b"), listOf(group("g1", listOf("a", "b"))))
        val skips = DataPorter.buildSkipItems(p)
        assertTrue(skips.none { it.id == "g1" }, "成员引用完整的群聊应恢复而非跳过")
    }

    @Test
    fun `group with dangling member references is skipped`() {
        val p = payload(listOf("a"), listOf(group("g1", listOf("a", "missing"))))
        val skips = DataPorter.buildSkipItems(p)
        val groupSkip = skips.firstOrNull { it.id == "g1" }
        assertEquals("群聊", groupSkip?.objectType)
        assertTrue(groupSkip?.reason?.contains("missing") == true, "跳过原因应包含悬空成员 id")
    }
}
