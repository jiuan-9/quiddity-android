package com.quiddity.app.data.remote

import kotlinx.serialization.Serializable

/**
 * OpenAI 兼容 Chat Completions 请求/响应模型。
 */
@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val max_tokens: Int? = null,
    val temperature: Double = 0.8,
    val stream: Boolean = true
)

@Serializable
data class ChatMessage(
    val role: String,
    val content: String
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
    val role: String? = null
)
