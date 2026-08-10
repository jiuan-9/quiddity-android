package com.quiddity.app.data.repo

import com.quiddity.app.data.model.Character
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.data.model.Persona
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/*
 * 角色会话匹配测试：characterId 精确绑定优先，同名私聊复用兜底。
 */
class MiniAppSessionRepositoryTest {

    private fun conversation(
        id: String,
        name: String = "",
        characterId: String? = null,
        type: ConversationType = ConversationType.SOLO
    ) = Conversation(
        id = id,
        title = "会话$id",
        createdAt = 0L,
        updatedAt = 0L,
        persona = Persona(name = name),
        characterId = characterId,
        type = type
    )

    private fun character(id: String, name: String) =
        Character(id = id, persona = Persona(name = name))

    @Test
    fun characterIdMatch_winsOverNameMatch() {
        val convs = listOf(
            conversation("bound", name = "小美", characterId = "chr_1"),
            conversation("sameName", name = "小美")
        )
        val found = findCharacterConversation(convs, character("chr_1", name = "小美"))
        assertEquals("bound", found?.id)
    }

    @Test
    fun sameNameSoloConversation_isFound() {
        val convs = listOf(conversation("c1", name = "小美"))
        val found = findCharacterConversation(convs, character("chr_1", name = "小美"))
        assertEquals("c1", found?.id)
    }

    @Test
    fun sameNameWithWhitespace_isFound() {
        val convs = listOf(conversation("c1", name = " 小美 "))
        val found = findCharacterConversation(convs, character("chr_1", name = "小美"))
        assertEquals("c1", found?.id)
    }

    @Test
    fun groupConversation_withSameName_isIgnored() {
        val convs = listOf(conversation("g1", name = "小美", type = ConversationType.GROUP))
        assertNull(findCharacterConversation(convs, character("chr_1", name = "小美")))
    }

    @Test
    fun conversationBoundToOtherCharacter_isNotReusedByName() {
        val convs = listOf(conversation("c1", name = "小美", characterId = "chr_other"))
        assertNull(findCharacterConversation(convs, character("chr_1", name = "小美")))
    }

    @Test
    fun blankName_neverMatches() {
        val convs = listOf(conversation("c1", name = "小美"))
        assertNull(findCharacterConversation(convs, character("chr_1", name = "")))
    }

    @Test
    fun noMatch_returnsNull() {
        assertNull(findCharacterConversation(emptyList(), character("chr_1", name = "小美")))
    }
}
