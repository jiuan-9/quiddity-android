package com.quiddity.app.domain

import com.quiddity.app.data.model.Conversation
import com.quiddity.app.ui.chat.CompressionState

/**
 * 会话压缩状态机：触发判定与状态迁移（Idle / Compressing / Success / Failed）。
 */
internal object CompressionStateMachine {
    /**
     * 是否达到压缩触发条件：启用记忆库且自上次压缩以来的 USER 轮数达到阈值。
     */
    fun shouldCompress(conv: Conversation, userRounds: Int): Boolean {
        if (!conv.memoryBankEnabled) return false
        val roundsSinceLastCompress = userRounds - conv.lastCompressedAtRound
        return roundsSinceLastCompress >= conv.memoryBankRounds
    }

    /**
     * 压缩结果映射：成功 -> Success，失败 -> Failed。
     */
    fun resultState(success: Boolean): CompressionState =
        if (success) CompressionState.Success else CompressionState.Failed

    /**
     * 消费瞬态结果：Success / Failed -> Idle，其余保持不变。
     */
    fun consume(state: CompressionState): CompressionState =
        if (state is CompressionState.Success || state is CompressionState.Failed) {
            CompressionState.Idle
        } else {
            state
        }
}
