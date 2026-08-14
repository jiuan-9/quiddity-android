package com.quiddity.app.data.remote

import com.quiddity.app.util.QuiddityConstants
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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
    val temperature: Double = QuiddityConstants.DEFAULT_TEMPERATURE,
    val stream: Boolean = true,
    /**
     * DeepSeek 思考深度（OpenAI 兼容 reasoning 能力字段：low / high）。
     * 仅 DeepSeek 官方模型开启思考时携带；服务端不识别时忽略该字段。
     */
    val reasoning_effort: String? = null,
    /**
     * 工具定义列表（6.6.2 记忆调用式 read_memory；默认不携带，向后兼容）。
     */
    val tools: List<ToolDefinition>? = null,
    /**
     * 工具调用策略（"auto" / "none" / "required"；null = 不携带该字段，兼容不支持工具调用的接口）。
     */
    val tool_choice: String? = null
)

/**
 * DeepSeek 官方 Responses API（POST /responses）请求体。
 *
 * 与 Chat Completions 的区别（官方文档）：
 * - 请求结构为 input（消息/工具结果 item 列表）+ instructions（系统级指令）+ tools；
 * - 内置 web_search 工具由服务端直接执行搜索，客户端无需接入第三方搜索引擎；
 * - 流式返回语义化 SSE 事件（response.output_text.delta / response.completed 等，无 [DONE]）。
 */
@Serializable
data class DeepSeekResponsesRequest(
    val model: String,
    val input: List<ResponsesInputItem>,
    val instructions: String? = null,
    val max_output_tokens: Int? = null,
    val temperature: Double = QuiddityConstants.DEFAULT_TEMPERATURE,
    val stream: Boolean = true,
    /**
     * DeepSeek 思考深度（low / high）；仅思考开启时携带。
     */
    val reasoning_effort: String? = null,
    val tools: List<ResponsesTool>? = null,
    val tool_choice: JsonElement? = null
)

/**
 * Responses API input item。
 *
 * 支持 message / function_call / function_call_output / reasoning / web_search_call；
 * 消息角色支持 user / assistant / system / developer。
 */
@Serializable
data class ResponsesInputItem(
    val type: String = "message",
    val role: String? = null,
    /**
     * message item：字符串文本；reasoning item：reasoning_text 内容块数组。
     * （DeepSeek 思考模式工具轮必须回传上一轮的 reasoning item，否则 400）
     */
    val content: JsonElement? = null,
    val call_id: String? = null,
    val name: String? = null,
    val arguments: String? = null,
    val output: String? = null
)

/**
 * Responses API 工具声明。
 *
 * - type = "web_search"：内置服务端搜索（无需任何客户端实现）
 * - type = "function"：函数工具（name / description / parameters）
 */
@Serializable
data class ResponsesTool(
    val type: String,
    val name: String? = null,
    val description: String? = null,
    val parameters: JsonObject? = null
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String? = null,
    /**
     * DeepSeek 思考模式强制要求：assistant 工具调用消息必须回传上一轮的
     * reasoning_content（reasoning_text），否则工具轮第二轮请求会被服务端以
     * "The reasoning_text in the thinking mode must be passed back to the API" 拒绝（HTTP 400）。
     */
    val reasoning_content: String? = null,
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
 * 视觉识图请求体（OpenAI 兼容多模态格式）。
 *
 * 与 [ChatCompletionRequest] 的区别：content 为内容块数组，图片以
 * `data:image/jpeg;base64,...` 形式内嵌，适配 OpenAI / Qwen-VL / GLM-4V /
 * Gemini（OpenAI 兼容端点）等主流视觉模型接口。
 */
@Serializable
data class VisionCompletionRequest(
    val model: String,
    val messages: List<VisionChatMessage>,
    val max_tokens: Int? = null,
    val temperature: Double = 0.2,
    val stream: Boolean = false
)

@Serializable
data class VisionChatMessage(
    val role: String,
    val content: List<VisionContentPart>
)

@Serializable
data class VisionContentPart(
    val type: String,
    val text: String? = null,
    val image_url: VisionImageUrl? = null
)

@Serializable
data class VisionImageUrl(
    val url: String,
    val detail: String? = null
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
    /**
     * DeepSeek 思考内容增量（deepseek 系列思考模型流式返回；
     * 普通内容在 [content]，思考内容在 reasoning_content）。
     */
    val reasoning_content: String? = null,
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
