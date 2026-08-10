package com.quiddity.app.data.repo

import com.quiddity.app.data.model.Character
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import com.quiddity.app.util.IdGenerator
import com.quiddity.app.util.QuiddityConstants

/*
 * 小应用与私聊会话的桥接：
 * - 邀请角色时查找/创建该角色的会话；
 * - 写入"邀请气泡"（isNotice，不参与 LLM 上下文）；
 * - 对局结束后向会话写入"对局记录气泡"（isGameLog，参与 LLM 上下文）
 *   并向 memory 注入结构化对局记忆（模块化文案由小应用提供）。
 */
class MiniAppSessionRepository(
    private val conversationRepository: ConversationRepository
) {

    /** 按角色 id（或同名私聊）查找已有会话；没有则创建并绑定 persona / memory。 */
    suspend fun findOrCreateCharacterConversation(character: Character): Conversation {
        findCharacterConversation(conversationRepository.conversations.value, character)?.let { existing ->
            if (existing.characterId == null) {
                conversationRepository.updateConversation(existing.copy(characterId = character.id))
            }
            return existing
        }
        val now = System.currentTimeMillis()
        val conv = Conversation(
            id = IdGenerator.newId(IdGenerator.Prefix.CONVERSATION),
            title = character.persona.name.ifBlank { QuiddityConstants.DEFAULT_CONVERSATION_TITLE },
            createdAt = now,
            updatedAt = now,
            persona = character.persona,
            userPersona = character.userPersona,
            memory = character.memory,
            contextLimit = QuiddityConstants.DEFAULT_CONTEXT_LIMIT,
            characterId = character.id
        )
        conversationRepository.createConversation(conv)
        return conv
    }

    /** 写入居中的"邀请气泡"（不发送给 LLM）。 */
    suspend fun appendInviteBubble(
        convId: String,
        text: String,
        miniAppId: String? = null,
        miniAppTitle: String? = null
    ) {
        conversationRepository.appendMessage(
            Message(
                id = IdGenerator.newId(IdGenerator.Prefix.USER_MESSAGE),
                conversationId = convId,
                role = Role.SYSTEM,
                content = text,
                timestamp = System.currentTimeMillis(),
                isNotice = true,
                miniAppId = miniAppId,
                miniAppTitle = miniAppTitle
            )
        )
    }

    /** 写入居中的"对局记录气泡"：角色可见、也参与 LLM 上下文，让角色知道自己刚和用户玩过。 */
    suspend fun appendGameLog(
        convId: String,
        text: String,
        miniAppId: String? = null,
        miniAppTitle: String? = null
    ) {
        conversationRepository.appendMessage(
            Message(
                id = IdGenerator.newId(IdGenerator.Prefix.USER_MESSAGE),
                conversationId = convId,
                role = Role.SYSTEM,
                content = text,
                timestamp = System.currentTimeMillis(),
                isNotice = false,
                miniAppId = miniAppId,
                miniAppTitle = miniAppTitle,
                isGameLog = true
            )
        )
    }

    /** 对局记忆追加到会话 memory（超长截断，保留最近内容）。 */
    suspend fun recordGameMemory(convId: String, memoryText: String) {
        val conv = conversationRepository.getConversation(convId) ?: return
        val combined = if (conv.memory.isBlank()) {
            memoryText
        } else {
            conv.memory.trimEnd() + "\n\n" + memoryText
        }
        conversationRepository.updateConversation(
            conv.copy(memory = combined.takeLast(MAX_MEMORY_CHARS))
        )
    }

    private companion object {
        const val MAX_MEMORY_CHARS = 2000
    }
}

/**
 * 角色会话匹配：先按 characterId 精确绑定；未绑定且同名时复用同名私聊会话
 * （群聊不参与，空名不参与）。返回 null 表示需要新建会话。
 */
internal fun findCharacterConversation(
    conversations: List<Conversation>,
    character: Character
): Conversation? {
    conversations.firstOrNull { it.characterId == character.id }?.let { return it }
    val name = character.persona.name.trim()
    if (name.isNotBlank()) {
        conversations.firstOrNull { conv ->
            conv.type != ConversationType.GROUP &&
                conv.characterId == null &&
                conv.persona.name.trim() == name
        }?.let { return it }
    }
    return null
}
