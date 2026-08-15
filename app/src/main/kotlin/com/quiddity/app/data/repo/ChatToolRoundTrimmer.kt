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
        val keepFrom = roundStarts[roundStarts.size - keepRounds]
        return messages.subList(0, keepFrom) + messages.subList(keepFrom, messages.size)
    }

    /**
     * DeepSeek Responses 输入：以 type = function_call 的消息为工具轮锚点。
     */
    fun trimResponsesToolRounds(input: List<ResponsesInputItem>, keepRounds: Int): List<ResponsesInputItem> {
        val roundStarts = input.indices.filter { input[it].type == "function_call" }
        if (roundStarts.size <= keepRounds) return input
        val keepFrom = roundStarts[roundStarts.size - keepRounds]
        return input.subList(0, keepFrom) + input.subList(keepFrom, input.size)
    }
}
