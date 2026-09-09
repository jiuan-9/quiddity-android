package com.quiddity.app.data.repo

import com.quiddity.app.data.remote.ChatMessage
import com.quiddity.app.data.remote.ResponsesInputItem

/**
 * 工具轮回传截断：保留最近 N 轮工具调用，超出部分从首个被保留轮起截断。
 */
internal object ChatToolRoundTrimmer {
    /**
     * Chat Completions 输入：以带 tool_calls 的消息为工具轮锚点。
     */
    fun trimToolRounds(messages: List<ChatMessage>, keepRounds: Int): List<ChatMessage> {
        val roundStarts = messages.indices.filter { messages[it].tool_calls?.isNotEmpty() == true }
        if (roundStarts.size <= keepRounds) return messages
        // 任务边界：最后一条 user 消息之后的工具轮全部保留（模型需看到当前任务已走过的路），
        // 更早的历史轮保留最近 keepRounds 个。
        val lastUserIdx = messages.indexOfLast { it.role == "user" }
        if (lastUserIdx < 0) {
            val keepFrom = roundStarts[roundStarts.size - keepRounds]
            return messages.subList(keepFrom, messages.size)
        }
        val keptStarts = roundStarts.filter { it > lastUserIdx } +
            roundStarts.filter { it <= lastUserIdx }.takeLast(keepRounds)
        if (keptStarts.isEmpty()) return messages
        val keepFrom = minOf(keptStarts.min(), lastUserIdx).coerceAtLeast(0)
        return messages.subList(keepFrom, messages.size)
    }

    /**
     * DeepSeek Responses 输入：以 type = function_call 的消息为工具轮锚点。
     */
    fun trimResponsesToolRounds(input: List<ResponsesInputItem>, keepRounds: Int): List<ResponsesInputItem> {
        val roundStarts = input.indices.filter { input[it].type == "function_call" }
        if (roundStarts.size <= keepRounds) return input
        // 任务边界：最后一条 user 消息之后的 function_call 轮全部保留。
        val lastUserIdx = input.indexOfLast { it.type == "message" && it.role == "user" }
        if (lastUserIdx < 0) {
            val keepFrom = roundStarts[roundStarts.size - keepRounds]
            return input.subList(keepFrom, input.size)
        }
        val keptStarts = roundStarts.filter { it > lastUserIdx } +
            roundStarts.filter { it <= lastUserIdx }.takeLast(keepRounds)
        if (keptStarts.isEmpty()) return input
        val keepFrom = minOf(keptStarts.min(), lastUserIdx).coerceAtLeast(0)
        return input.subList(keepFrom, input.size)
    }
}
