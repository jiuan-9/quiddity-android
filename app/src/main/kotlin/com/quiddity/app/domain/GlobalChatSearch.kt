package com.quiddity.app.domain

import com.quiddity.app.data.model.Message

/**
 * 跨会话聊天记录搜索（首页全局搜索）。
 *
 * 复用 [ChatRecordSearch.searchResults] 的逐会话打分逻辑，再把命中合并为
 * 带会话上下文的列表，供首页搜索界面展示。命中结果按消息时间倒序（最新优先），
 * 并受每会话条数上限与总条数上限约束。
 */
object GlobalChatSearch {

    /** 每个会话最多返回的命中条数。 */
    const val PER_CONVERSATION_LIMIT = 5

    /** 全局最多返回的命中总条数。 */
    const val TOTAL_LIMIT = 30

    /**
     * 单条命中：所属会话 + 命中的消息。
     *
     * @param conversationId 所属会话 id（用于跳转）
     * @param conversationTitle 所属会话标题（用于列表展示）
     * @param message 命中的消息原文
     */
    data class Hit(
        val conversationId: String,
        val conversationTitle: String,
        val message: Message
    )

    /**
     * 在 [messagesByConversation] 中按 [query] 跨会话搜索。
     *
     * @param messagesByConversation 会话 id → 该会话完整消息列表
     * @param conversationTitles 会话 id → 会话标题（缺失时显示空串）
     * @param query 搜索关键词；空白时返回空列表
     * @param perConversationLimit 每会话上限，默认 [PER_CONVERSATION_LIMIT]
     * @param totalLimit 总上限，默认 [TOTAL_LIMIT]
     * @return 命中列表，按消息时间倒序；无命中或空查询返回空列表
     */
    fun searchAll(
        messagesByConversation: Map<String, List<Message>>,
        conversationTitles: Map<String, String>,
        query: String,
        perConversationLimit: Int = PER_CONVERSATION_LIMIT,
        totalLimit: Int = TOTAL_LIMIT
    ): List<Hit> {
        val q = query.trim()
        if (q.isEmpty() || messagesByConversation.isEmpty()) return emptyList()

        val hits = mutableListOf<Hit>()
        for ((convId, messages) in messagesByConversation) {
            // 提示气泡（isNotice）是 UI 专用内容，不参与搜索
            val searchable = messages.filterNot { it.isNotice }
            val ranked = ChatRecordSearch.searchResults(searchable, q)
                .take(perConversationLimit)
            val title = conversationTitles[convId].orEmpty()
            ranked.forEach { message ->
                hits += Hit(
                    conversationId = convId,
                    conversationTitle = title,
                    message = message
                )
            }
        }

        return hits
            .sortedByDescending { it.message.timestamp }
            .take(totalLimit)
    }
}
