package com.quiddity.app.domain

import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.data.model.Persona
import com.quiddity.app.data.model.UserPersona
import com.quiddity.app.util.QuiddityConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 群聊实体规则测试（方案二 / 七 / 十）。
 */
class GroupChatRulesTest {

    private val now = 1720000000000L

    private fun group(id: String, members: List<String> = emptyList()): Conversation =
        Conversation(
            id = id,
            createdAt = now,
            updatedAt = now,
            type = ConversationType.GROUP,
            memberConversationIds = members
        )

    @Test
    fun `buildGroupConversation applies group defaults`() {
        val g = GroupChatRules.buildGroupConversation(
            id = "g1",
            title = "新群聊 1",
            memberIds = listOf("a", "b", "a"),
            createdAt = now,
            updatedAt = now
        )
        assertEquals(ConversationType.GROUP, g.type)
        assertEquals(listOf("a", "b"), g.memberConversationIds)
        assertEquals(QuiddityConstants.GROUP_DEFAULT_CONTEXT_LIMIT, g.groupContextLimit)
        assertEquals(QuiddityConstants.GROUP_DEFAULT_STOP_MODE, g.stopMode)
    }

    @Test
    fun `buildGroupConversation falls back to default title when blank`() {
        val g = GroupChatRules.buildGroupConversation("g1", "", listOf("a"), now, now)
        assertEquals(QuiddityConstants.GROUP_DEFAULT_TITLE_PREFIX, g.title)
    }

    @Test
    fun `groupsReferencing only returns groups containing member`() {
        val groups = listOf(
            group("g1", listOf("a", "b")),
            group("g2", listOf("c")),
            Conversation(id = "solo", createdAt = now, updatedAt = now)
        )
        val refs = GroupChatRules.groupsReferencing(groups, "a")
        assertEquals(listOf("g1"), refs.map { it.id })
    }

    @Test
    fun `groupsWithoutMember removes member but keeps group`() {
        val updated = GroupChatRules.groupsWithoutMember(
            listOf(group("g1", listOf("a", "b"))),
            "a"
        )
        assertEquals(listOf("b"), updated.first().memberConversationIds)
        assertEquals("g1", updated.first().id)
    }

    @Test
    fun `validation fails when user or ai name missing`() {
        val noUser = group("g1").copy(userPersona = UserPersona(name = ""), persona = Persona(name = "小A"))
        val noAi = group("g1").copy(userPersona = UserPersona(name = "我"), persona = Persona(name = ""))
        val ok = group("g1").copy(userPersona = UserPersona(name = "我"), persona = Persona(name = "小A"))
        assertEquals("用户名未设置", GroupChatRules.validationFailureReason(noUser))
        assertEquals("AI 名未设置", GroupChatRules.validationFailureReason(noAi))
        assertNull(GroupChatRules.validationFailureReason(ok))
    }

    @Test
    fun `group member limit is three`() {
        assertEquals(3, QuiddityConstants.GROUP_MAX_MEMBERS)
        assertTrue(GroupChatRules.buildGroupConversation("g", "t", listOf("a", "b", "c"), now, now)
            .memberConversationIds.size <= QuiddityConstants.GROUP_MAX_MEMBERS)
    }

    @Test
    fun `mentioned member ids parsed from text in occurrence order`() {
        val members = listOf(
            group("a").copy(persona = Persona(name = "林晚")),
            group("b").copy(persona = Persona(name = "苏晴")),
            group("c").copy(persona = Persona(name = ""))
        )
        val ids = GroupChatRules.mentionedMemberIds(
            "@林晚 在吗 @苏晴 一起吃饭",
            members
        )
        assertEquals(listOf("a", "b"), ids, "按文本中出现顺序：@林晚 先出现，@苏晴 后出现")
    }

    @Test
    fun `mentioned member ids empty when no at or blank name`() {
        val members = listOf(group("a").copy(persona = Persona(name = "林晚")))
        assertEquals(emptyList(), GroupChatRules.mentionedMemberIds("没有点名", members))
        assertEquals(emptyList(), GroupChatRules.mentionedMemberIds("", members))
    }

    @Test
    fun `mentioned member ids deduplicate`() {
        val members = listOf(group("a").copy(persona = Persona(name = "林晚")))
        assertEquals(
            listOf("a"),
            GroupChatRules.mentionedMemberIds("@林晚 在吗 @林晚", members)
        )
    }

    @Test
    fun `mentioned member requires word boundary after name`() {
        val members = listOf(
            group("a").copy(persona = Persona(name = "林晚")),
            group("b").copy(persona = Persona(name = "A"))
        )
        // 名字后是字母/数字 → 属于更长词的前缀，不视为点名
        assertTrue(GroupChatRules.mentionedMemberIds("@林晚哥 在吗", members).isEmpty())
        assertTrue(GroupChatRules.mentionedMemberIds("@AI 你好", members).isEmpty())
        assertTrue(GroupChatRules.mentionedMemberIds("@A1 编号", members).isEmpty())
        // 名字后是空白 / 标点 / 结尾 → 视为点名
        assertEquals(listOf("a"), GroupChatRules.mentionedMemberIds("@林晚 在吗", members))
        assertEquals(listOf("a"), GroupChatRules.mentionedMemberIds("@林晚，吃饭", members))
        assertEquals(listOf("a"), GroupChatRules.mentionedMemberIds("@林晚", members))
        assertEquals(listOf("b"), GroupChatRules.mentionedMemberIds("快来看 @A！", members))
    }

    @Test
    fun `isMentionedAt handles repeated markers`() {
        assertTrue(GroupChatRules.isMentionedAt("说说 @小明 和 @小明哥 的区别", "小明"))
        assertFalse(GroupChatRules.isMentionedAt("看看 @小明哥", "小明"))
        assertFalse(GroupChatRules.isMentionedAt("", "小明"))
        assertFalse(GroupChatRules.isMentionedAt("无点名", ""))
    }
}
