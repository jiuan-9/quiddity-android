package com.quiddity.app.ui.miniapps.board

import com.quiddity.app.data.model.Character
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.data.model.Persona
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * 邀请好友名单构建测试：私聊会话全量入选、群聊排除、角色库按 id/同名合并去重。
 */
class BoardInviteeTest {

    private fun conversation(
        id: String,
        name: String = "",
        title: String = "会话$id",
        characterId: String? = null,
        type: ConversationType = ConversationType.SOLO,
        updatedAt: Long = 0L
    ) = Conversation(
        id = id,
        title = title,
        createdAt = 0L,
        updatedAt = updatedAt,
        persona = Persona(name = name),
        characterId = characterId,
        type = type
    )

    private fun character(id: String, name: String, persona: String = "", characterText: String = "") =
        Character(id = id, persona = Persona(name = name, persona = persona, character = characterText))

    @Test
    fun soloConversations_allAppearAsInvitees() {
        val convs = listOf(
            conversation("c1", name = "小美"),
            conversation("c2", name = "阿哲")
        )
        val invitees = buildBoardInvitees(convs, emptyList())
        assertEquals(listOf("小美", "阿哲"), invitees.map { it.displayName })
        assertTrue(invitees.all { it.character == null })
    }

    @Test
    fun groupConversations_areExcluded() {
        val convs = listOf(
            conversation("c1", name = "小美"),
            conversation("g1", name = "群友", type = ConversationType.GROUP)
        )
        val invitees = buildBoardInvitees(convs, emptyList())
        assertEquals(listOf("小美"), invitees.map { it.displayName })
    }

    @Test
    fun characterIdBoundConversation_isEnrichedByCharacter() {
        val convs = listOf(conversation("c1", name = "小美", characterId = "chr_1"))
        val chars = listOf(character("chr_1", name = "小美", persona = "身份", characterText = "性格"))
        val invitees = buildBoardInvitees(convs, chars)
        assertEquals(1, invitees.size)
        assertEquals("小美", invitees[0].displayName)
        assertEquals("身份", invitees[0].character?.persona?.persona)
        assertEquals("性格", invitees[0].subtitle)
    }

    @Test
    fun sameNameConversation_isMergedWithCharacter_notDuplicated() {
        val convs = listOf(conversation("c1", name = "小美"))
        val chars = listOf(character("chr_1", name = "小美"))
        val invitees = buildBoardInvitees(convs, chars)
        assertEquals(1, invitees.size)
        assertEquals("c1", invitees[0].key)
        assertEquals("chr_1", invitees[0].character?.id)
    }

    @Test
    fun orphanCharacter_isAppendedAtEnd() {
        val convs = listOf(conversation("c1", name = "小美", updatedAt = 10L))
        val chars = listOf(character("chr_1", name = "阿哲"))
        val invitees = buildBoardInvitees(convs, chars)
        assertEquals(2, invitees.size)
        assertEquals("小美", invitees[0].displayName)
        assertEquals("阿哲", invitees[1].displayName)
        assertNull(invitees[1].conversation)
    }

    @Test
    fun conversations_sortedByUpdatedAtDescending() {
        val convs = listOf(
            conversation("old", name = "旧友", updatedAt = 1L),
            conversation("new", name = "新友", updatedAt = 2L)
        )
        val invitees = buildBoardInvitees(convs, emptyList())
        assertEquals(listOf("新友", "旧友"), invitees.map { it.displayName })
    }

    @Test
    fun displayName_prefersPersonaName_overTitle() {
        val invitee = BoardInvitee(
            conversation = conversation("c1", name = "小美", title = "改过的标题"),
            character = null
        )
        assertEquals("小美", invitee.displayName)
    }

    @Test
    fun displayName_fallsBackToTitle_whenPersonaNameBlank() {
        val invitee = BoardInvitee(
            conversation = conversation("c1", name = "", title = "改过的标题"),
            character = null
        )
        assertEquals("改过的标题", invitee.displayName)
    }

    @Test
    fun blankEverything_fallsBackToUnnamed() {
        val invitee = BoardInvitee(conversation = null, character = character("chr_1", name = ""))
        assertEquals("未命名角色", invitee.displayName)
    }

    @Test
    fun nameMatching_trimsWhitespace() {
        val convs = listOf(conversation("c1", name = "  小美  "))
        val chars = listOf(character("chr_1", name = "小美"))
        val invitees = buildBoardInvitees(convs, chars)
        assertEquals(1, invitees.size)
        assertEquals("chr_1", invitees[0].character?.id)
    }
}
