package com.quiddity.app.domain

import com.quiddity.app.data.model.AppSettings
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.remote.ChatCompletionRequest
import com.quiddity.app.data.remote.DeepSeekResponsesRequest
import com.quiddity.app.data.remote.ResponsesTool
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
        /**
         * DeepSeek 官方服务端联网搜索请求（Responses API）；null = 走 Chat Completions。
         */
        val responsesRequest: DeepSeekResponsesRequest? = null,
        /**
         * Responses API 端点（联网搜索启用时非空，HTTP 请求目标 URL）。
         */
        val responsesApiUrl: String? = null,
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
     * @param webSearchResponsesUrl 该成员启用官方联网搜索时的 Responses API 端点；
     *   非空时本计划改为构造 Responses API 请求（由调用方按能力解析，null = 不启用）
     * @param regeneratePreviousReply 重说场景下该成员上一版回复的原文（null = 正常回复）。
     *   非空时系统提示词会标记本次为「重说」并要求换一种表达，避免输出与上一版雷同。
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
        userName: String? = null,
        webSearchResponsesUrl: String? = null,
        thinkingDepth: String? = null,
        regeneratePreviousReply: String? = null
    ): Result<Plan> {
        val access = ApiAccess.resolve(settings, member)
        if (access is ApiAccess.Failure) {
            return Result.failure(access.toChatException())
        }
        access as ApiAccess.Resolved

        val systemPrompt = PromptBuilder.buildGroupSystemPrompt(
            member,
            PromptBuilder.GROUP_RULES,
            regeneratePreviousReply,
            group.groupBackground.takeIf { it.isNotBlank() },
            group.groupBackgroundMode,
            thinkingDepth
        )
        val apiMessages = PromptBuilder.toApiMessages(systemPrompt, transcript, senderNames, userName)
        val maxTokens = member.maxTokens ?: settings.globalMaxTokens
        val singleMsgTokens = member.singleMessageTokens ?: settings.globalSingleMessageTokens
        // 按成员所用模型支持的最高温度钳制：部分模型仅支持 0～1.0
        val temperature = com.quiddity.app.util.QuiddityConstants.clampTemperature(
            member.temperature ?: settings.globalTemperature,
            access.maxTemperature
        )
        // 方案六.3：基础级只带最近 N 条；进阶级/完整级可自行用工具检索完整群聊消息。
        val useSearchTool = tier != ApiCatalogManager.ModelTier.BASIC
        val request = ChatCompletionRequest(
            model = access.model,
            messages = apiMessages,
            max_tokens = maxTokens,
            temperature = temperature,
            stream = true,
            reasoning_effort = null,
            tools = if (useSearchTool) listOf(PromptBuilder.buildSearchChatTool()) else null,
            tool_choice = if (useSearchTool) "auto" else null
        )
        val responsesApiUrl = webSearchResponsesUrl?.takeIf { it.isNotBlank() }
        val responsesRequest = responsesApiUrl?.let {
            val responsesTools = buildList {
                add(ResponsesTool(type = "web_search"))
                if (useSearchTool) {
                    add(PromptBuilder.toResponsesTool(PromptBuilder.buildSearchChatTool()))
                }
            }
            DeepSeekResponsesRequest(
                model = access.model,
                input = PromptBuilder.toResponsesInput(apiMessages),
                instructions = apiMessages.firstOrNull { it.role == "system" }?.content,
                max_output_tokens = maxTokens,
                temperature = temperature,
                stream = true,
                reasoning_effort = null,
                tools = responsesTools
            )
        }
        return Result.success(
            Plan(
                request = request,
                responsesRequest = responsesRequest,
                responsesApiUrl = responsesApiUrl,
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
