package com.quiddity.app.data.remote

import kotlinx.serialization.json.Json

/**
 * SSE 流式响应解析器。
 *
 * 输入：OkHttp EventSource 监听到的 `data:` 文本行。
 * 输出：累加的内容片段。
 */
class ChatStreamParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    /**
     * 解析单行 SSE data。
     * 返回内容片段；`[DONE]` 返回 null 表示结束；解析失败返回空字符串。
     */
    fun parseDelta(dataLine: String): String? {
        if (dataLine == "[DONE]") return null
        if (dataLine.isBlank()) return ""
        return runCatching {
            val chunk = json.decodeFromString(ChatStreamChunk.serializer(), dataLine)
            chunk.choices.firstOrNull()?.delta?.content ?: ""
        }.getOrDefault("")
    }
}
