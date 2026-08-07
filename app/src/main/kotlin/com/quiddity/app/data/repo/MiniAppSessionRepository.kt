package com.quiddity.app.data.repo

import com.quiddity.app.data.model.Character
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import com.quiddity.app.util.IdGenerator
import com.quiddity.app.util.QuiddityConstants

/*
 * 小应用与私聊会话的桥接：
 * - 邀请角色时查找/创建该角色的会话；
 * - 写入"邀请气泡"（isNotice，不参与 LLM 上下文）；
 * - 对局结束后向会话 memory 注入结构化对局记忆（模块化文案由小应用提供）。
 */
class MiniAppSessionRepository(
    private val conversationRepository: ConversationRepository
) {

    /** 按角色 id 查找已有私聊会话；没有则创建并绑定 persona / memory。 */
    suspend fun findOrCreateCharacterConversation(character: Character): Conversation {
        conversationRepository.conversations.value.firstOrNull { it.characterId == character.id }?.let {
            return it
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
