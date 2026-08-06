package com.quiddity.app.ui.miniapps.board

import com.quiddity.app.data.remote.ChatApi
import com.quiddity.app.data.repo.ApiAccess
import com.quiddity.app.domain.board.BoardLlmPrompt
import com.quiddity.app.domain.board.BoardState
import com.quiddity.app.domain.board.LlmMove

/*
 * LLM 对弈网关：把 ChatApi 包装成可测试的 [LlmGateway]，
 * [BoardLlmClient] 只关心 prompt 与解析，逻辑可单测。
 */
fun interface LlmGateway {
    suspend fun complete(
        systemPrompt: String,
        userContent: String,
        maxTokens: Int,
        temperature: Double
    ): Result<String>
}

fun ApiAccess.Resolved.toLlmGateway(api: ChatApi): LlmGateway =
    LlmGateway { system, user, maxTokens, temperature ->
        runCatching {
            api.completeNonStreaming(
                apiUrl = apiUrl,
                apiKey = apiKey,
                model = model,
                systemPrompt = system,
                userContent = user,
                maxTokens = maxTokens,
                temperature = temperature,
                emptyError = "LLM 未返回有效内容"
            )
        }
    }

class BoardLlmClient(private val gateway: LlmGateway) {

    /** 请求对手落子；LLM 失败或无法解析返回 null（调用方降级本地 AI）。 */
    suspend fun requestMove(
        state: BoardState,
        opponentName: String,
        persona: String?
    ): LlmMove? {
        val reply = gateway.complete(
            BoardLlmPrompt.buildMoveSystemMessage(state, opponentName, persona),
            BoardLlmPrompt.buildMoveUserMessage(state),
            MOVE_MAX_TOKENS,
            MOVE_TEMPERATURE
        ).getOrNull() ?: return null
        return BoardLlmPrompt.parseMoveReply(reply)
    }

    /** 请求对局内聊天回复；失败返回 null。 */
    suspend fun chatReply(
        state: BoardState,
        opponentName: String,
        persona: String?,
        userText: String
    ): String? {
        return gateway.complete(
            BoardLlmPrompt.buildChatSystemMessage(state, opponentName, persona),
            BoardLlmPrompt.buildChatUserMessage(userText, state),
            CHAT_MAX_TOKENS,
            CHAT_TEMPERATURE
        ).getOrNull()
    }

    private companion object {
        const val MOVE_MAX_TOKENS = 64
        const val MOVE_TEMPERATURE = 0.2
        const val CHAT_MAX_TOKENS = 240
        const val CHAT_TEMPERATURE = 0.8
    }
}
