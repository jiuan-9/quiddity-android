package com.quiddity.app.domain

import com.quiddity.app.data.model.Message

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
 *    除此之外，禁止出现任何形式的代码注释（包括但不限于单行注释、多行调试注释等）。
 *
 * 3. 构建交付要求
 *    在完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK 文件。
 *    APK 文件需满足以下条件：
 *    - 签名有效且符合发布标准
 *    - 包含完整的功能模块
 *    - 经过基础性能测试和兼容性测试
 *    以便在真实设备环境中进行功能验证和性能评估。
 *
 * ============================================================================
 */



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
