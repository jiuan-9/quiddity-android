package com.quiddity.app.data.remote

import kotlinx.serialization.Serializable

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
 * OpenAI 兼容 Chat Completions 请求/响应模型。
 */
@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val max_tokens: Int? = null,
    val temperature: Double = 0.8,
    val stream: Boolean = true,
    /**
     * 工具定义列表（6.6.2 记忆调用式 read_memory；默认不携带，向后兼容）。
     */
    val tools: List<ToolDefinition>? = null,
    /**
     * 工具调用策略（"auto" / "none" / "required"；null = 不携带该字段，兼容不支持工具调用的接口）。
     */
    val tool_choice: String? = null
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String? = null,
    /**
     * 第二次请求回填的 assistant 工具调用（6.6.4）。
     */
    val tool_calls: List<AssistantToolCall>? = null,
    /**
     * tool 角色消息关联的调用 id（6.6.4）。
     */
    val tool_call_id: String? = null
)

/**
 * 工具定义（OpenAI 兼容 function 格式）。
 */
@Serializable
data class ToolDefinition(
    val type: String = "function",
    val function: ToolFunction
)

@Serializable
data class ToolFunction(
    val name: String,
    val description: String,
    val parameters: kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.JsonObject(emptyMap())
)

/**
 * assistant 消息中回填的完整工具调用（第二次请求）。
 */
@Serializable
data class AssistantToolCall(
    val id: String,
    val type: String = "function",
    val function: AssistantToolCallFunction
)

@Serializable
data class AssistantToolCallFunction(
    val name: String,
    val arguments: String
)

@Serializable
data class ChatStreamChunk(
    val choices: List<Choice> = emptyList()
)

@Serializable
data class Choice(
    val delta: Delta = Delta(),
    val finish_reason: String? = null
)

@Serializable
data class Delta(
    val content: String? = null,
    val role: String? = null,
    /**
     * 流式工具调用增量分片（6.6.3：按 index 聚合 name 与 arguments）。
     */
    val tool_calls: List<DeltaToolCall> = emptyList()
)

@Serializable
data class DeltaToolCall(
    val index: Int = 0,
    val id: String? = null,
    val function: DeltaToolCallFunction? = null
)

@Serializable
data class DeltaToolCallFunction(
    val name: String? = null,
    val arguments: String? = null
)
