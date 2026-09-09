package com.quiddity.app.domain

import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.util.QuiddityConstants

/**
 * 群聊实体规则（纯函数，便于 JVM 单测）。
 */
object GroupChatRules {

    /**
     * 构造群聊会话实体（方案二：type=GROUP、成员引用、默认上下文条数 50、
     * 默认停止模式 B、群名可空自动编号）。
     */
    fun buildGroupConversation(
        id: String,
        title: String,
        memberIds: List<String>,
        createdAt: Long,
        updatedAt: Long
    ): Conversation = Conversation(
        id = id,
        title = title.ifBlank { QuiddityConstants.GROUP_DEFAULT_TITLE_PREFIX },
        createdAt = createdAt,
        updatedAt = updatedAt,
        type = ConversationType.GROUP,
        memberConversationIds = memberIds.distinct(),
        groupContextLimit = QuiddityConstants.GROUP_DEFAULT_CONTEXT_LIMIT,
        stopMode = QuiddityConstants.GROUP_DEFAULT_STOP_MODE
    )

    /** 被 [memberId] 引用的群聊列表（私聊删除保护用）。 */
    fun groupsReferencing(groups: List<Conversation>, memberId: String): List<Conversation> =
        groups.filter {
            it.type == ConversationType.GROUP && memberId in it.memberConversationIds
        }

    /** 从所有群聊中移除成员（私聊删除后调用；历史消息气泡保留，方案十.4）。 */
    fun groupsWithoutMember(groups: List<Conversation>, memberId: String): List<Conversation> =
        groups.map { group ->
            if (group.type == ConversationType.GROUP && memberId in group.memberConversationIds) {
                group.copy(memberConversationIds = group.memberConversationIds - memberId)
            } else {
                group
            }
        }

    /**
     * 成员入群前置校验（方案七.4：用户名、AI 名必须设置）；
     * 返回 null 表示通过，非 null 为失败原因。
     */
    fun validationFailureReason(member: Conversation): String? = when {
        member.userPersona.name.isBlank() -> "用户名未设置"
        member.persona.name.isBlank() -> "AI 名未设置"
        else -> null
    }

    /**
     * 从消息文本中解析被 @ 点名的成员 id（按文本中出现顺序去重）。
     *
     * 匹配规则：成员 AI 名字前带「@」即视为被点名（如"@林晚 在吗"）。
     * 仅在群聊使用；成员名字为空或文本不含 @ 时返回空列表。
     */
    fun mentionedMemberIds(text: String, members: List<Conversation>): List<String> {
        if (text.isBlank()) return emptyList()
        val nameToId = members.mapNotNull { member ->
            val name = member.persona.name
            if (name.isNotBlank()) name to member.id else null
        }
        if (nameToId.isEmpty()) return emptyList()
        return nameToId
            .mapNotNull { (name, id) ->
                findMentionIndex(text, name)?.let { it to id }
            }
            .sortedBy { it.first }
            .map { it.second }
            .distinct()
    }

    /**
     * 判断文本中是否以「@名字」点名：@ 后必须紧跟完整名字，且名字后必须是
     * 空白 / 标点 / 文本结尾。避免「@小明」误命中「@小明哥」「@小明abc」这类
     * 更长词的前缀（子串匹配的误报根因）。
     */
    fun isMentionedAt(text: String, name: String): Boolean = findMentionIndex(text, name) != null

    /**
     * 找到首个合法 @点名 的位置（@ 后紧跟完整名字，且名字后是边界）；
     * 无合法点名返回 null。用返回的位置而非 [String.indexOf] 排序，
     * 避免「@小明哥 @小明」这类先出现长名前缀时排序位置错乱。
     */
    fun findMentionIndex(text: String, name: String): Int? {
        if (text.isBlank() || name.isBlank()) return null
        val marker = "@$name"
        var from = 0
        while (true) {
            val idx = text.indexOf(marker, from)
            if (idx < 0) return null
            val after = idx + marker.length
            // 名字后必须是边界（结尾 / 空白 / 非字母数字），否则继续向后找
            if (after >= text.length || !text[after].isLetterOrDigit()) return idx
            from = after
        }
    }
}
