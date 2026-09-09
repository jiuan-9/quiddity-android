package com.quiddity.app.ui.miniapps.spy

import com.quiddity.app.data.remote.ChatApi
import com.quiddity.app.data.repo.ApiAccess
import com.quiddity.app.domain.spy.SpyLlmPrompt
import com.quiddity.app.domain.spy.SpyPlayer
import com.quiddity.app.domain.spy.SpySpeech
import com.quiddity.app.domain.spy.SpyWordPair
import com.quiddity.app.domain.spy.SpyRole
import com.quiddity.app.domain.spy.SpyGameMode
import kotlinx.coroutines.delay

/*
 * 谁是卧底 LLM 网关：把 ChatApi 包装成可测试的 [SpyGateway]，
 * [SpyLlmClient] 只关心 prompt 与解析（与棋盘 BoardLlmClient 同构）。
 */
fun interface SpyGateway {
    suspend fun complete(
        systemPrompt: String,
        userContent: String,
        maxTokens: Int,
        temperature: Double
    ): Result<String>
}

fun ApiAccess.Resolved.toSpyGateway(api: ChatApi): SpyGateway =
    SpyGateway { system, user, maxTokens, temperature ->
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

class SpyLlmClient(
    private val gateway: SpyGateway,
    private val speechMaxTokens: Int = 120,
    private val voteMaxTokens: Int = 16
) {

    /** 请求生成一批相似词对（平民词 + 卧底词）；失败返回空列表。 */
    suspend fun generateWordPairs(
        count: Int,
        topic: String = ""
    ): List<SpyWordPair> {
        for (attempt in 1..RETRY_COUNT) {
            val reply = gateway.complete(
                SpyLlmPrompt.buildWordGenSystemMessage(),
                SpyLlmPrompt.buildWordGenUserMessage(count, topic),
                WORD_GEN_MAX_TOKENS,
                WORD_GEN_TEMPERATURE
            ).getOrNull()
            if (reply != null) {
                val pairs = SpyLlmPrompt.parseWordGenReply(reply)
                if (pairs.isNotEmpty()) return pairs
            }
            if (attempt < RETRY_COUNT) delay(RETRY_DELAY_MS)
        }
        return emptyList()
    }

    /** 请求一次发言文本；失败返回 null（调用方用脚本兜底）。 */
    suspend fun requestSpeech(
        player: SpyPlayer,
        round: Int,
        speeches: List<SpySpeech>,
        playerName: String,
        mode: SpyGameMode = SpyGameMode.CLASSIC
    ): String? {
        val role = player.role ?: return null
        val word = player.word ?: return null
        for (attempt in 1..RETRY_COUNT) {
            val reply = gateway.complete(
                SpyLlmPrompt.buildSpeakSystemMessage(player.name, player.persona, role, word, mode),
                SpyLlmPrompt.buildSpeakUserMessage(round, speeches, playerName),
                speechMaxTokens,
                SPEECH_TEMPERATURE
            ).getOrNull()
            if (reply != null && reply.isNotBlank()) return reply.trim()
            if (attempt < RETRY_COUNT) delay(RETRY_DELAY_MS)
        }
        return null
    }

    /** 请求一次 PK 补发言；失败返回 null（调用方用脚本兜底）。 */
    suspend fun requestPkSpeech(
        player: SpyPlayer,
        round: Int,
        pkCandidates: List<Pair<Int, String>>,
        pkSpeeches: List<SpySpeech>,
        playerName: String,
        mode: SpyGameMode = SpyGameMode.CLASSIC
    ): String? {
        val role = player.role ?: return null
        val word = player.word ?: return null
        for (attempt in 1..RETRY_COUNT) {
            val reply = gateway.complete(
                SpyLlmPrompt.buildPkSpeakSystemMessage(player.name, player.persona, role, word, mode),
                SpyLlmPrompt.buildPkSpeakUserMessage(round, pkCandidates, pkSpeeches, playerName),
                speechMaxTokens,
                SPEECH_TEMPERATURE
            ).getOrNull()
            if (reply != null && reply.isNotBlank()) return reply.trim()
            if (attempt < RETRY_COUNT) delay(RETRY_DELAY_MS)
        }
        return null
    }

    /** 请求一次投票；解析失败返回 null（调用方用脚本兜底）。 */
    suspend fun requestVote(
        player: SpyPlayer,
        round: Int,
        candidates: List<Pair<Int, String>>,
        speeches: List<SpySpeech>,
        voterName: String,
        mode: SpyGameMode = SpyGameMode.CLASSIC
    ): Int? {
        val role = player.role ?: return null
        val word = player.word ?: return null
        val valid = candidates.map { it.first }.toSet()
        for (attempt in 1..RETRY_COUNT) {
            val reply = gateway.complete(
                SpyLlmPrompt.buildVoteSystemMessage(player.name, player.persona, role, word, mode),
                SpyLlmPrompt.buildVoteUserMessage(round, candidates, speeches, voterName),
                voteMaxTokens,
                VOTE_TEMPERATURE
            ).getOrNull()
            if (reply != null) {
                val parsed = SpyLlmPrompt.parseVoteReply(reply, valid)
                if (parsed != null) return parsed
            }
            if (attempt < RETRY_COUNT) delay(RETRY_DELAY_MS)
        }
        return null
    }

    /** 请求一次 PK 二选一投票；解析失败返回 null（调用方用脚本兜底）。 */
    suspend fun requestPkVote(
        player: SpyPlayer,
        round: Int,
        candidates: List<Pair<Int, String>>,
        speeches: List<SpySpeech>,
        voterName: String,
        mode: SpyGameMode = SpyGameMode.CLASSIC
    ): Int? {
        val role = player.role ?: return null
        val word = player.word ?: return null
        val valid = candidates.map { it.first }.toSet()
        for (attempt in 1..RETRY_COUNT) {
            val reply = gateway.complete(
                SpyLlmPrompt.buildPkVoteSystemMessage(player.name, player.persona, role, word, mode),
                SpyLlmPrompt.buildPkVoteUserMessage(round, candidates, speeches, voterName),
                voteMaxTokens,
                VOTE_TEMPERATURE
            ).getOrNull()
            if (reply != null) {
                val parsed = SpyLlmPrompt.parseVoteReply(reply, valid)
                if (parsed != null) return parsed
            }
            if (attempt < RETRY_COUNT) delay(RETRY_DELAY_MS)
        }
        return null
    }

    private companion object {
        const val RETRY_COUNT = 5
        const val RETRY_DELAY_MS = 1200L
        const val SPEECH_TEMPERATURE = 0.8
        const val VOTE_TEMPERATURE = 0.5
        const val WORD_GEN_MAX_TOKENS = 1000
        const val WORD_GEN_TEMPERATURE = 1.0
    }
}
