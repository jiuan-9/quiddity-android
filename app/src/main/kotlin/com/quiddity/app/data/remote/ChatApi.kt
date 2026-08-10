package com.quiddity.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
 * OpenAI 兼容 API 客户端。
 *
 * 通过 SSE 流式接收响应，向上层暴露为 Flow<String>。
 * 每条 String 是一个内容片段；Flow 正常结束代表 [DONE]。
 */
open class ChatApi {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(QuiddityConstants.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(QuiddityConstants.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(QuiddityConstants.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * 单次流式响应事件。
     */
    sealed class StreamEvent {
        /** 内容片段（可能为空串，调用方自行忽略）。 */
        data class Content(val text: String) : StreamEvent()
        /** DeepSeek 思考内容片段（reasoning_content，先于普通内容到达）。 */
        data class Reasoning(val text: String) : StreamEvent()
        /** 流以"被截断"结束（finish_reason=length / response.incomplete），内容不完整。 */
        data object Truncated : StreamEvent()
        /** 流结束（[DONE] 或连接关闭）时聚合出的完整工具调用列表，无工具调用时为空列表。 */
        data class ToolCalls(val calls: List<ChatStreamParser.AggregatedToolCall>) : StreamEvent()
    }

    /**
     * 发起流式对话请求。
     *
     * - 每次调用使用独立的 [ChatStreamParser]，避免多路流并行时工具调用聚合状态互相污染；
     * - 工具调用增量按 index 聚合，在流结束（[DONE] / 连接关闭）时以 [StreamEvent.ToolCalls] 一次性下发；
     * - Flow 正常完成表示流结束。
     *
     * @param apiUrl 完整 chat/completions URL
     * @param apiKey 明文 API Key
     * @param request 请求体
     * @return [Flow] of [StreamEvent]；Flow 完成表示流结束
     */
    open fun streamChat(
        apiUrl: String,
        apiKey: String,
        request: ChatCompletionRequest
    ): Flow<StreamEvent> = callbackFlow {
        // 每次流独立 parser：工具调用累积器不复用，杜绝并行流串扰
        val parser = ChatStreamParser()
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
        // 消费者主动取消标记：awaitClose 置位后再 cancel，onFailure 据此区分
        // 「用户停止」与「真实网络错误」，避免停止生成被误报为错误。
        val cancelledByConsumer = java.util.concurrent.atomic.AtomicBoolean(false)

        fun safeClose(cause: Throwable? = null) {
            if (!closed.compareAndSet(false, true)) return
            channel.close(cause)
        }

        /**
         * 带背压的内容下发：消费者慢时阻塞 SSE 回调线程（自然形成 TCP 背压），
         * 保证任何内容片段都不会被静默丢弃；channel 已关闭时静默忽略。
         */
        fun emitContent(text: String) {
            runCatching {
                kotlinx.coroutines.runBlocking { channel.send(StreamEvent.Content(text)) }
            }
        }

        fun emitReasoning(text: String) {
            runCatching {
                kotlinx.coroutines.runBlocking { channel.send(StreamEvent.Reasoning(text)) }
            }
        }

        fun emitToolCalls(calls: List<ChatStreamParser.AggregatedToolCall>) {
            runCatching {
                kotlinx.coroutines.runBlocking { channel.send(StreamEvent.ToolCalls(calls)) }
            }
        }

        fun emitTruncated() {
            runCatching {
                kotlinx.coroutines.runBlocking { channel.send(StreamEvent.Truncated) }
            }
        }

        // finish_reason=length：输出达到 max_tokens 上限，回复被截断（部分网关也会用此信号）
        var truncated = false
        val eventSourceListener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (closed.get()) return
                val parsed = parser.acceptChunk(data)
                if (parsed == null) {
                    // [DONE] —— 结束流：先下发聚合完成的工具调用
                    emitToolCalls(parser.takeToolCalls())
                    if (truncated) emitTruncated()
                    safeClose()
                    return
                }
                if (parsed.finishReason == "length") truncated = true
                val content = parsed.content
                if (!content.isNullOrEmpty()) {
                    emitContent(content)
                } else {
                    parsed.reasoning?.let { reasoning ->
                        if (reasoning.isNotEmpty()) emitReasoning(reasoning)
                    }
                }
            }

            override fun onClosed(eventSource: EventSource) {
                // 服务端未发 [DONE] 直接关闭：仍把已聚合的工具调用下发，避免丢失
                emitToolCalls(parser.takeToolCalls())
                if (truncated) emitTruncated()
                safeClose()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                // 消费者主动取消（用户停止生成 / 页面销毁）：不是错误，静默结束
                if (cancelledByConsumer.get()) {
                    safeClose()
                    return
                }
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
            cancelledByConsumer.set(true)
            es.cancel()
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 发起 DeepSeek 官方 Responses API 流式请求（服务端联网搜索路径）。
     *
     * - 流式事件为语义化 SSE（response.output_text.delta 等），没有 `[DONE]`；
     *   最后一条事件 response.completed / response.incomplete / response.failed 由
     *   [ResponsesStreamParser] 判定为流结束。
     * - response.failed 时以 [ChatException] 关闭流，携带服务端 error.message。
     * - 服务端联网搜索（web_search 工具）由 DeepSeek 服务端直接执行，客户端无需第三方搜索。
     *
     * @param apiUrl 完整 /responses URL
     * @param apiKey 明文 API Key
     * @param request Responses API 请求体
     * @return [Flow] of [StreamEvent]；Flow 完成表示流结束
     */
    open fun streamResponses(
        apiUrl: String,
        apiKey: String,
        request: DeepSeekResponsesRequest
    ): Flow<StreamEvent> = callbackFlow {
        val parser = ResponsesStreamParser()
        val body = json.encodeToString(DeepSeekResponsesRequest.serializer(), request)
            .toRequestBody(mediaType)

        val requestBuilder = Request.Builder()
            .url(apiUrl)
            .post(body)
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")

        if (apiKey.isNotEmpty()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        val closed = java.util.concurrent.atomic.AtomicBoolean(false)
        val cancelledByConsumer = java.util.concurrent.atomic.AtomicBoolean(false)

        fun safeClose(cause: Throwable? = null) {
            if (!closed.compareAndSet(false, true)) return
            channel.close(cause)
        }

        fun emitContent(text: String) {
            runCatching {
                kotlinx.coroutines.runBlocking { channel.send(StreamEvent.Content(text)) }
            }
        }

        fun emitReasoning(text: String) {
            runCatching {
                kotlinx.coroutines.runBlocking { channel.send(StreamEvent.Reasoning(text)) }
            }
        }

        fun emitToolCalls(calls: List<ChatStreamParser.AggregatedToolCall>) {
            runCatching {
                kotlinx.coroutines.runBlocking { channel.send(StreamEvent.ToolCalls(calls)) }
            }
        }

        fun emitTruncated() {
            runCatching {
                kotlinx.coroutines.runBlocking { channel.send(StreamEvent.Truncated) }
            }
        }

        val eventSourceListener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (closed.get()) return
                val parsed = parser.acceptEvent(type, data)
                if (parsed == null) {
                    // 终态事件（completed/incomplete/failed）：下发聚合完成的函数调用后结束
                    emitToolCalls(parser.takeToolCalls())
                    // response.incomplete = 输出被截断（如达到 max_output_tokens），
                    // 单独下发 Truncated 事件，避免上层静默吞掉未说完的回复
                    if (parser.terminatedIncomplete) emitTruncated()
                    val error = parser.terminalError
                    if (error != null) {
                        safeClose(ChatException(error))
                    } else {
                        safeClose()
                    }
                    return
                }
                val content = parsed.content
                if (!content.isNullOrEmpty()) {
                    emitContent(content)
                } else {
                    parsed.reasoning?.let { reasoning ->
                        if (reasoning.isNotEmpty()) emitReasoning(reasoning)
                    }
                }
            }

            override fun onClosed(eventSource: EventSource) {
                emitToolCalls(parser.takeToolCalls())
                safeClose()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                if (cancelledByConsumer.get()) {
                    safeClose()
                    return
                }
                val msg = t?.message ?: response?.let { "HTTP ${it.code}: ${it.message}" } ?: "未知错误"
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
            cancelledByConsumer.set(true)
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
                // 只返回模型回复内容（截断），避免把整个响应 JSON 展示给用户
                val raw = resp.body?.string().orEmpty()
                val content = runCatching {
                    val obj = json.parseToJsonElement(raw) as? JsonObject ?: return@runCatching null
                    val choices = obj["choices"] as? JsonArray ?: return@runCatching null
                    val first = choices.firstOrNull() as? JsonObject ?: return@runCatching null
                    val message = first["message"] as? JsonObject ?: return@runCatching null
                    message["content"] as? JsonPrimitive
                }.getOrNull()
                content?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.take(80) ?: "连接成功"
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
    open suspend fun completeNonStreaming(
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

    /**
     * 视觉识图（非流式多模态请求）。
     *
     * 供图片 → OCR → 聊天 API 流程使用：
     * - 请求体为 OpenAI 兼容的 content 块数组，图片以 base64 data URL 内嵌；
     * - 响应解析 `choices[0].message.content`，兼容字符串与内容块数组两种返回；
     * - 失败或返回空时抛 [ChatException]，由上层提示用户检查视觉模型配置。
     *
     * @param imageData 已编码的 data URL（如 `data:image/jpeg;base64,...`）
     * @param prompt 识图指令（OCR / 图片描述）
     */
    open suspend fun completeVisionNonStreaming(
        apiUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        imageData: String,
        maxTokens: Int,
        temperature: Double
    ): String = withContext(Dispatchers.IO) {
        val request = VisionCompletionRequest(
            model = model,
            messages = listOf(
                VisionChatMessage(
                    role = "user",
                    content = listOf(
                        // 图片块在前、文字块在后：与智谱/硅基流动/月之暗面等
                        // OpenAI 兼容视觉接口的官方示例一致，兼容性最好。
                        VisionContentPart(
                            type = "image_url",
                            image_url = VisionImageUrl(url = imageData)
                        ),
                        VisionContentPart(type = "text", text = prompt),
                    )
                )
            ),
            max_tokens = maxTokens,
            temperature = temperature,
            stream = false
        )
        val body = json.encodeToString(VisionCompletionRequest.serializer(), request)
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
                throw ChatException(
                    "HTTP ${resp.code}: ${resp.message}${if (errBody.isNotBlank()) " - $errBody" else ""}"
                )
            }
            val raw = resp.body?.string().orEmpty()
            // 兼容 content 为字符串或内容块数组两种响应格式
            val parsed = runCatching {
                val root = json.parseToJsonElement(raw) as? JsonObject
                val choices = root?.get("choices") as? JsonArray
                val firstChoice = choices?.firstOrNull() as? JsonObject
                val message = firstChoice?.get("message") as? JsonObject
                val content = message?.get("content")
                when (content) {
                    is JsonPrimitive -> content.contentOrNull
                    is JsonArray -> content.mapNotNull { part ->
                        (part as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull
                    }.joinToString("\n")
                    else -> null
                }
            }.getOrNull()
            val result = (parsed?.trim() ?: "").ifEmpty {
                throw ChatException("视觉模型未返回识别内容，请检查模型是否支持图片输入")
            }
            result
        }
    }
}

class ChatException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
