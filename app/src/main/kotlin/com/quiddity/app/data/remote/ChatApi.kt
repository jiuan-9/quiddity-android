package com.quiddity.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit
import com.quiddity.app.util.QuiddityConstants

/**
 * OpenAI 兼容 API 客户端。
 *
 * 通过 SSE 流式接收响应，向上层暴露为 Flow<String>。
 * 每条 String 是一个内容片段；Flow 正常结束代表 [DONE]。
 */
class ChatApi {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(QuiddityConstants.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(QuiddityConstants.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(QuiddityConstants.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val parser = ChatStreamParser()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * 发起流式对话请求。
     *
     * @param apiUrl 完整 chat/completions URL
     * @param apiKey 明文 API Key
     * @param request 请求体
     * @return Flow<String>，每个元素为内容片段；Flow 完成表示流结束
     */
    fun streamChat(
        apiUrl: String,
        apiKey: String,
        request: ChatCompletionRequest
    ): Flow<String> = callbackFlow {
        val body = json.encodeToString(ChatCompletionRequest.serializer(), request)
            .toRequestBody(mediaType)

        val requestBuilder = Request.Builder()
            .url(apiUrl)
            .post(body)
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")

        if (apiKey.isNotEmpty()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        // 幂等关闭 channel：AtomicBoolean 保证 safeClose 只执行一次；
        // Channel.close() 本身亦幂等，已关闭时调用为 no-op。
        val closed = java.util.concurrent.atomic.AtomicBoolean(false)

        fun safeClose(cause: Throwable? = null) {
            if (!closed.compareAndSet(false, true)) return
            channel.close(cause)
        }

        val eventSourceListener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (closed.get()) return
                val delta = parser.parseDelta(data)
                if (delta == null) {
                    // [DONE] —— 结束流
                    safeClose()
                    return
                }
                if (delta.isNotEmpty()) {
                    // trySend 在 channel 满时返回失败；SSE 是高吞吐流，
                    // 消费者慢时丢弃部分片段是可接受的（不等同丢消息）。
                    trySend(delta)
                }
            }

            override fun onClosed(eventSource: EventSource) {
                safeClose()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val msg = t?.message ?: response?.let { "HTTP ${it.code}: ${it.message}" } ?: "未知错误"
                // 尝试读取错误响应体（多数 API 返回 JSON 错误描述），包装进异常
                val errorBody = runCatching { response?.peekBody(2 * 1024)?.string().orEmpty() }
                    .getOrDefault("")
                val cause = if (errorBody.isNotBlank()) {
                    ChatException("$msg — $errorBody", t)
                } else {
                    ChatException(msg, t)
                }
                safeClose(cause)
            }
        }

        val factory = EventSources.createFactory(client)
        val es = factory.newEventSource(requestBuilder.build(), eventSourceListener)

        awaitClose {
            closed.set(true)
            es.cancel()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 同步测试连接（非流式），返回是否成功。
     */
    suspend fun testConnection(apiUrl: String, apiKey: String, model: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val request = ChatCompletionRequest(
                model = model,
                messages = listOf(ChatMessage(role = "user", content = "Hi")),
                max_tokens = 16,
                temperature = 0.0,
                stream = false
            )
            val body = json.encodeToString(ChatCompletionRequest.serializer(), request)
                .toRequestBody(mediaType)
            val requestBuilder = Request.Builder()
                .url(apiUrl)
                .post(body)
                .header("Accept", "application/json")
            if (apiKey.isNotEmpty()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
            }
            client.newCall(requestBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw ChatException("HTTP ${resp.code}: ${resp.message}")
                }
                resp.body?.string() ?: ""
            }
        }
    }

    /**
     * 单轮非流式补全：人设精调、记忆压缩等工具型任务共用此入口。
     *
     * - system 消息与 user 消息均由调用方传入（提示词统一由 [com.quiddity.app.domain.PromptBuilder] 提供）
     * - 返回：choices[0].message.content（去空白）
     *
     * 错误处理：失败时抛 [ChatException]，调用方负责降级。
     *
     * @param apiUrl 完整 chat/completions URL
     * @param apiKey 明文 API Key
     * @param model 模型 id
     * @param systemPrompt 任务 system 提示词
     * @param userContent 任务 user 消息内容
     * @param maxTokens 输出 token 上限
     * @param temperature 采样温度（精调偏高鼓励表达，压缩偏低保证忠实）
     * @param emptyError 返回空内容时的错误提示文案
     */
    suspend fun completeNonStreaming(
        apiUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userContent: String,
        maxTokens: Int,
        temperature: Double,
        emptyError: String
    ): String = withContext(Dispatchers.IO) {
        val request = ChatCompletionRequest(
            model = model,
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = userContent)
            ),
            max_tokens = maxTokens,
            temperature = temperature,
            stream = false
        )
        val body = json.encodeToString(ChatCompletionRequest.serializer(), request)
            .toRequestBody(mediaType)
        val requestBuilder = Request.Builder()
            .url(apiUrl)
            .post(body)
            .header("Accept", "application/json")
        if (apiKey.isNotEmpty()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }
        client.newCall(requestBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                val errBody = runCatching { resp.peekBody(2 * 1024)?.string().orEmpty() }
                    .getOrDefault("")
                throw ChatException("HTTP ${resp.code}: ${resp.message}${if (errBody.isNotBlank()) " — $errBody" else ""}")
            }
            val raw = resp.body?.string().orEmpty()
            // 解析 OpenAI 兼容响应：choices[0].message.content
            val parsed = runCatching {
                val root = json.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonObject
                val choices = root?.get("choices") as? kotlinx.serialization.json.JsonArray
                val firstChoice = choices?.firstOrNull() as? kotlinx.serialization.json.JsonObject
                val message = firstChoice?.get("message") as? kotlinx.serialization.json.JsonObject
                val content = message?.get("content") as? kotlinx.serialization.json.JsonPrimitive
                content?.content
            }.getOrNull()
            val result = (parsed?.trim() ?: "").ifEmpty {
                throw ChatException(emptyError)
            }
            result
        }
    }
}

class ChatException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
