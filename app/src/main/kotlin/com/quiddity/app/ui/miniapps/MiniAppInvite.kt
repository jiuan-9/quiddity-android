package com.quiddity.app.ui.miniapps

import com.quiddity.app.data.model.Character
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.repo.ApiAccess
import com.quiddity.app.data.repo.MiniAppSessionRepository
import com.quiddity.app.data.repo.SettingsRepository
import com.quiddity.app.domain.ApiCatalogManager

/*
 * 通用"邀请角色"协调器（小应用框架的一部分）。
 *
 * 任何小应用需要"邀请好友对战"时，只需调用 [prepare]：
 * 1. 检测当前 API 连接（决定 LLM 对手还是本地电脑兜底）；
 * 2. 查找/创建该角色的私聊会话；
 * 3. 写入小应用提供的邀请气泡文案；
 * 4. 返回开局所需信息（会话 id、对手名、人设、API 访问凭证）。
 *
 * 未来小应用无需重复实现这套流程。
 */
data class PreparedCharacterInvite(
    val conversationId: String,
    val opponentName: String,
    val opponentPersona: String?,
    val access: ApiAccess.Resolved?
)

class MiniAppInviteManager(
    private val sessionRepository: MiniAppSessionRepository,
    private val settingsRepository: SettingsRepository,
    private val apiCatalogManager: ApiCatalogManager
) {

    suspend fun prepare(
        character: Character,
        inviteBubbleText: (opponentName: String) -> String,
        miniAppId: String? = null,
        miniAppTitle: String? = null,
        existingConversation: Conversation? = null
    ): PreparedCharacterInvite {
        val settings = settingsRepository.currentSnapshot()
        val entry = settings.catalog.firstOrNull { it.id == settings.activeCatalogId }
            ?: settings.catalog.firstOrNull()
        val llmOk = entry?.let { e ->
            apiCatalogManager.testConnection(e.apiUrl, apiCatalogManager.decryptKey(e).orEmpty(), e.apiModel).isSuccess
        } ?: false

        val conversation = existingConversation
            ?: sessionRepository.findOrCreateCharacterConversation(character)
        val opponentName = resolveOpponentName(character, existingConversation)
        sessionRepository.appendInviteBubble(
            conversation.id,
            inviteBubbleText(opponentName),
            miniAppId,
            miniAppTitle
        )

        val access = if (llmOk) resolveAccess(conversation) else null
        return PreparedCharacterInvite(
            conversationId = conversation.id,
            opponentName = opponentName,
            opponentPersona = buildPersonaText(character, existingConversation),
            access = access
        )
    }

    private fun resolveAccess(conv: Conversation): ApiAccess.Resolved? {
        val settings = settingsRepository.currentSnapshot()
        val resolved = ApiAccess.resolve(settings, conv)
        if (resolved is ApiAccess.Resolved) return resolved
        // 兼容无密钥的本地/免鉴权服务（Ollama、LM Studio、本地 mock 等）：
        // ApiAccess.resolve 对空密钥返回 KEY_NOT_CONFIGURED，但 ApiCatalogManager.decryptKey
        // 允许空密钥（返回空串）。这里与之一致放行，避免"连接测试通过但开局被兜底"。
        val entry = settings.catalog.firstOrNull { it.id == conv.apiCatalogId }
            ?: settings.catalog.firstOrNull { it.id == settings.activeCatalogId }
            ?: settings.catalog.firstOrNull()
        if (entry != null && entry.apiUrl.isNotBlank() && entry.apiKeyEnc.isEmpty()) {
            return ApiAccess.Resolved(apiUrl = entry.apiUrl, apiKey = "", model = entry.apiModel)
        }
        return null
    }

    /**
     * 对手人设：以会话内实时人设优先，字段为空时回退角色库主档，保证
     * 对局中的对手与私聊里看到的是同一个人。
     */
    fun buildPersonaText(character: Character, conversation: Conversation?): String {
        val live = conversation?.persona
        fun pick(conversationValue: String, characterValue: String): String? {
            val value = conversationValue.ifBlank { characterValue }.trim()
            return value.takeIf { it.isNotBlank() }
        }
        val parts = listOfNotNull(
            pick(live?.persona.orEmpty(), character.persona.persona)?.let { "身份：$it" },
            pick(live?.character.orEmpty(), character.persona.character)?.let { "性格：$it" },
            pick(live?.appearance.orEmpty(), character.persona.appearance)?.let { "外貌：$it" },
            pick(live?.worldBackground.orEmpty(), character.persona.worldBackground)?.let { "背景：$it" }
        )
        return parts.joinToString("；")
    }

    /** 对手名：会话内实时名字（persona.name → 标题）优先，回退角色库名。 */
    private fun resolveOpponentName(character: Character, conversation: Conversation?): String {
        val convName = conversation?.persona?.name.orEmpty()
            .ifBlank { conversation?.title.orEmpty() }
        return convName.ifBlank { character.persona.name }.ifBlank { "神秘角色" }
    }
}
