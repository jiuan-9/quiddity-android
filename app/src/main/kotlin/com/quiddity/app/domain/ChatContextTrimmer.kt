package com.quiddity.app.domain

import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role

/**
 * 上下文轮次裁剪：以 USER 消息为锚点，供会话上下文与压缩输入共用。
 */
internal object ChatContextTrimmer {
    /**
     * 取"最后 N 轮"对话（用于上下文裁剪）。
     *
     * 「轮」以 USER 消息为锚点。返回的子列表包含最后 N 个 USER 消息
     * 以及它们之间 / 之后的所有消息，确保不切断任何一轮的上下文。
     *
     * @param messages 全量历史消息（按时间正序）
     * @param rounds 要保留的轮数（按 USER 消息数计）
     * @param buffer 额外向前取的 buffer 消息数（保留上一轮 AI 回复的尾巴）
     * @return 截取后的子列表（顺序不变）；rounds <= 0 或消息为空时返回空列表
     */
    fun takeLastRounds(
        messages: List<Message>,
        rounds: Int,
        buffer: Int = 0
    ): List<Message> {
        if (rounds <= 0 || messages.isEmpty()) return emptyList()
        val userIndices = messages.withIndex()
            .filter { it.value.role == Role.USER }
            .map { it.index }
        if (userIndices.isEmpty()) return emptyList()
        if (rounds >= userIndices.size) return messages
        val startUserIdx = userIndices[userIndices.size - rounds]
        val startIdx = (startUserIdx - buffer).coerceAtLeast(0)
        return messages.subList(startIdx, messages.size)
    }

    /**
     * 取"从第 startRound 轮起"的对话（用于压缩输入裁剪）。
     *
     * @param messages 全量历史消息（按时间正序）
     * @param startRound 起始轮次（0 表示从第一条 USER 消息开始）
     * @return 截取后的子列表（顺序不变）
     */
    fun takeFromRound(
        messages: List<Message>,
        startRound: Int
    ): List<Message> {
        if (messages.isEmpty()) return emptyList()
        if (startRound <= 0) return messages
        val userIndices = messages.withIndex()
            .filter { it.value.role == Role.USER }
            .map { it.index }
        if (startRound >= userIndices.size) return emptyList()
        val startIdx = userIndices[startRound]
        return messages.subList(startIdx, messages.size)
    }
}
