package com.quiddity.app.domain

import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.util.QuiddityConstants

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
 *    除此之外，禁止出现任何形式的代码注释（包括但不限于单行注释、多行注释、临时调试注释等）。
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
}
