package com.quiddity.app.data.remote

import kotlinx.serialization.json.Json

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
 * SSE 流式响应解析器。
 *
 * 输入：OkHttp EventSource 监听到的 `data:` 文本行。
 * 输出：内容片段 + 流式 tool_calls 增量分片（6.6.3）。
 *
 * tool_calls 增量解析：OpenAI 兼容流式的工具调用参数是分片到达的，
 * 按 `index` 聚合 `function.name` 与 `function.arguments` 的增量，流结束时可取完整调用。
 */
class ChatStreamParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    /**
     * 单次解析结果：内容片段 + 工具调用增量分片。
     */
    data class ParsedChunk(
        val content: String?,
        val toolCalls: List<ToolCallFragment>
    )

    /**
     * 单条工具调用增量分片（[ChatModels.DeltaToolCall] 的扁平化）。
     */
    data class ToolCallFragment(
        val index: Int,
        val id: String?,
        val name: String?,
        val arguments: String?
    )

    /**
     * 按 index 聚合后的完整工具调用。
     */
    data class AggregatedToolCall(
        val index: Int,
        val id: String?,
        val name: String,
        val arguments: String
    )

    private class ToolCallAccumulator {
        var id: String? = null
        var name: String? = null
        val arguments = StringBuilder()
    }

    private val toolCallAccumulators = mutableMapOf<Int, ToolCallAccumulator>()

    /**
     * 解析单行 SSE data。
     * 返回内容片段；`[DONE]` 返回 null 表示结束；解析失败返回空字符串。
     */
    fun parseDelta(dataLine: String): String? = parseChunk(dataLine)?.content

    /**
     * 解析单行 SSE data（内容 + 工具调用增量分片）。
     * `[DONE]` 返回 null 表示结束；解析失败返回空内容分片。
     */
    fun parseChunk(dataLine: String): ParsedChunk? {
        if (dataLine == "[DONE]") return null
        if (dataLine.isBlank()) return ParsedChunk("", emptyList())
        return runCatching {
            val chunk = json.decodeFromString(ChatStreamChunk.serializer(), dataLine)
            val choice = chunk.choices.firstOrNull()
            val delta = choice?.delta
            ParsedChunk(
                content = delta?.content,
                toolCalls = delta?.tool_calls.orEmpty().map { tc ->
                    ToolCallFragment(
                        index = tc.index,
                        id = tc.id,
                        name = tc.function?.name,
                        arguments = tc.function?.arguments
                    )
                }
            )
        }.getOrDefault(ParsedChunk("", emptyList()))
    }

    /**
     * 解析单行 SSE data 并按 index 聚合工具调用增量（6.6.3 主入口）。
     * 返回值与 [parseChunk] 相同；`[DONE]` 返回 null。
     */
    fun acceptChunk(dataLine: String): ParsedChunk? {
        val parsed = parseChunk(dataLine) ?: return null
        parsed.toolCalls.forEach { fragment ->
            val acc = toolCallAccumulators.getOrPut(fragment.index) { ToolCallAccumulator() }
            if (fragment.id != null) acc.id = fragment.id
            if (fragment.name != null) acc.name = fragment.name
            if (fragment.arguments != null) acc.arguments.append(fragment.arguments)
        }
        return parsed
    }

    /**
     * 取流结束时的完整工具调用列表，并清空聚合状态。
     * 调用方按 6.6.3 判定规则决定是否进入第二次请求。
     */
    fun takeToolCalls(): List<AggregatedToolCall> {
        val result = toolCallAccumulators.map { (index, acc) ->
            AggregatedToolCall(
                index = index,
                id = acc.id,
                name = acc.name.orEmpty(),
                arguments = acc.arguments.toString()
            )
        }.toList()
        toolCallAccumulators.clear()
        return result
    }
}
