package com.quiddity.app.domain

import com.quiddity.app.data.model.AppSettings
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.remote.ChatCompletionRequest
import com.quiddity.app.data.repo.ApiAccess
import com.quiddity.app.data.repo.toChatException

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
 * 群聊成员回复请求规划（纯函数，便于 JVM 单测）。
 *
 * 规则（方案八）：成员被点名回复时使用各自私聊的模型配置（模型 / API key /
 * 最大 token 均按各自私聊独立）；消息 senderId 透传给协调器；
 * 方案六.3：完整级成员可携带 search_chat 工具检索完整群聊消息。
 */
object GroupReplyPlanner {

    data class Plan(
        val request: ChatCompletionRequest,
        val apiUrl: String,
        val apiKey: String,
        val senderId: String,
        val singleMessageTokens: Int,
        val useSearchTool: Boolean
    )

    /**
     * 构造成员回复请求。
     *
     * @param member 发言成员（私聊会话，携带该成员的模型配置）
     * @param group 群聊会话（仅用于消息归属，请求本身不读取群聊模型配置）
     * @param transcript 群聊转述（点击头像那一刻定格的消息快照）
     * @param senderId 发言人会话 id（写入消息 senderId）
     * @param tier 成员模型分级（由调用方解析）
     * @param senderNames 成员会话 id → 成员 AI 名字映射（转述格式「名字：内容」）
     * @param userName 用户消息的名字（方案九.3：= 该成员私聊用户人设里的名字）
     * @return 成功返回 [Plan]；API 解析失败返回失败结果（含用户提示）
     */
    fun buildPlan(
        settings: AppSettings,
        member: Conversation,
        group: Conversation,
        transcript: List<Message>,
        senderId: String,
        tier: ApiCatalogManager.ModelTier,
        senderNames: Map<String, String> = emptyMap(),
        userName: String? = null
    ): Result<Plan> {
        val access = ApiAccess.resolve(settings, member)
        if (access is ApiAccess.Failure) {
            return Result.failure(access.toChatException())
        }
        access as ApiAccess.Resolved

        val systemPrompt = PromptBuilder.buildGroupSystemPrompt(member, PromptBuilder.GROUP_RULES)
        val apiMessages = PromptBuilder.toApiMessages(systemPrompt, transcript, senderNames, userName)
        val maxTokens = member.maxTokens ?: settings.globalMaxTokens
        val singleMsgTokens = member.singleMessageTokens ?: settings.globalSingleMessageTokens
        val useSearchTool = tier == ApiCatalogManager.ModelTier.FULL
        val request = ChatCompletionRequest(
            model = access.model,
            messages = apiMessages,
            max_tokens = maxTokens,
            temperature = 0.8,
            stream = true,
            tools = if (useSearchTool) listOf(PromptBuilder.buildSearchChatTool()) else null,
            tool_choice = if (useSearchTool) "auto" else null
        )
        return Result.success(
            Plan(
                request = request,
                apiUrl = access.apiUrl,
                apiKey = access.apiKey,
                senderId = senderId,
                singleMessageTokens = singleMsgTokens,
                useSearchTool = useSearchTool
            )
        )
    }

    /**
     * 群聊小本本压缩结果应用：成功用摘要替换，失败保留旧值。
     */
    fun applyMemoryCompression(raw: String, current: String): String {
        val parsed = PromptBuilder.parseCompressionResult(raw)
        return if (parsed.success) parsed.summary else current
    }
}
