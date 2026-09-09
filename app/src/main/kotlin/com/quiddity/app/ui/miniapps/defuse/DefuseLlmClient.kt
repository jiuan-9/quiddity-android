package com.quiddity.app.ui.miniapps.defuse

import com.quiddity.app.data.remote.ChatApi
import com.quiddity.app.data.repo.ApiAccess
import kotlinx.coroutines.delay

/*
 * 拆弹搭档 LLM 网关：把 ChatApi 包装成可测试的 [DefuseGateway]，
 * [DefuseLlmClient] 只关心提示词与重试（与棋盘 / 卧底同构）。
 */
fun interface DefuseGateway {
    suspend fun complete(
        systemPrompt: String,
        userContent: String,
        maxTokens: Int,
        temperature: Double
    ): Result<String>
}

fun ApiAccess.Resolved.toDefuseGateway(api: ChatApi): DefuseGateway =
    DefuseGateway { system, user, maxTokens, temperature ->
        runCatching {
            api.completeNonStreaming(
                apiUrl = apiUrl,
                apiKey = apiKey,
                model = model,
                systemPrompt = system,
                userContent = user,
                maxTokens = maxTokens,
                temperature = temperature,
                emptyError = "搭档没有给出有效回复"
            )
        }
    }

class DefuseLlmClient(private val gateway: DefuseGateway) {

    /** 请求搭档一次回复；失败返回 null（调用方用脚本搭档兜底）。 */
    suspend fun requestReply(systemPrompt: String, userContent: String): String? {
        for (attempt in 1..RETRY_COUNT) {
            val reply = gateway.complete(
                systemPrompt,
                userContent,
                REPLY_MAX_TOKENS,
                TEMPERATURE
            ).getOrNull()
            if (reply != null && reply.isNotBlank()) return reply.trim()
            if (attempt < RETRY_COUNT) delay(RETRY_DELAY_MS)
        }
        return null
    }

    private companion object {
        const val RETRY_COUNT = 2
        const val RETRY_DELAY_MS = 500L
        const val REPLY_MAX_TOKENS = 220
        const val TEMPERATURE = 0.4
    }
}
