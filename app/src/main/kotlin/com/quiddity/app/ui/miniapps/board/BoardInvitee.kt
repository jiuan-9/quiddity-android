package com.quiddity.app.ui.miniapps.board

import com.quiddity.app.data.model.Character
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ConversationType

/*
 * ============================================================================
 * 开发规范 (Development Specifications)
 * ============================================================================
 *
 * 1. 问题修复规范
 *    所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
 *    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
 *
 * 2. 代码注释规范
 *    文件内仅允许保留两类注释：
 *    - 当前规则说明注释（即本注释块）
 *    - 模块划分注释（用于标识代码功能模块边界）
 *    除此之外，禁止出现任何形式的代码注释。
 *
 * ============================================================================
 */

/**
 * 棋盘小应用「邀请好友」列表实体。
 *
 * 以私聊会话（好友）为主，角色库记录作为身份 / 头像补充：
 * - 展示名与会话列表一致（persona.name 优先，回退会话标题）；
 * - 角色库记录仅在已绑定（characterId）或同名时挂到对应会话上，避免同一好友重复出现。
 */
data class BoardInvitee(
    val conversation: Conversation?,
    val character: Character?
) {
    /** LazyColumn 稳定 key：会话优先，无会话时用角色 id。 */
    val key: String
        get() = conversation?.id ?: "char_${character?.id}"

    val displayName: String
        get() {
            val convName = conversation?.persona?.name.orEmpty()
                .ifBlank { conversation?.title.orEmpty() }
            return convName.ifBlank { character?.persona?.name.orEmpty() }.ifBlank { "未命名角色" }
        }

    val subtitle: String
        get() {
            val live = conversation?.persona
            val master = character?.persona
            val text = live?.character?.ifBlank { live.persona }.orEmpty()
                .ifBlank { master?.character?.ifBlank { master.persona }.orEmpty() }
            return text.ifBlank { "点击邀请 TA 对弈" }
        }

    val avatarUri: String?
        get() = character?.aiAvatarUri
            ?: character?.persona?.aiAvatarUri
            ?: conversation?.persona?.aiAvatarUri
}

/**
 * 构建邀请名单：私聊会话全部入选（按更新时间倒序），角色库中未对应任何会话的
 * 记录追加在末尾；同名未绑定会话与角色库记录合并为一个条目。
 */
fun buildBoardInvitees(
    conversations: List<Conversation>,
    characters: List<Character>
): List<BoardInvitee> {
    val solo = conversations.filter { it.type != ConversationType.GROUP }
    val convIdToChar = mutableMapOf<String, Character>()
    val matchedChars = mutableSetOf<String>()
    characters.forEach { character ->
        val bound = solo.firstOrNull { it.characterId == character.id }
        if (bound != null) {
            convIdToChar[bound.id] = character
            matchedChars += character.id
            return@forEach
        }
        val sameName = solo.firstOrNull {
            it.characterId == null && sameName(it.persona.name, character.persona.name)
        }
        if (sameName != null) {
            convIdToChar[sameName.id] = character
            matchedChars += character.id
        }
    }
    val fromConversations = solo
        .sortedByDescending { it.updatedAt }
        .map { conv -> BoardInvitee(conversation = conv, character = convIdToChar[conv.id]) }
    val orphanCharacters = characters
        .filterNot { it.id in matchedChars }
        .map { BoardInvitee(conversation = null, character = it) }
    return fromConversations + orphanCharacters
}

private fun sameName(a: String, b: String): Boolean =
    a.isNotBlank() && b.isNotBlank() && a.trim() == b.trim()
