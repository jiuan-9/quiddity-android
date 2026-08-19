package com.quiddity.app.data.repo

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 轮次完整性契约测试：兜底恢复的唯一判定来源是
 * [StreamRoundResult.isCompleteReply]（有正文且未截断）。
 *
 * 回归保护：工具执行成功后模型已给出完整正文时，工具轮循环直接收尾、
 * 兜底函数立即返回 Completed——均不产生任何额外请求。否则会在正常回复
 * 后面追加一条「AI 回复不完整或为空（已自动重试 3 次）」的错误消息。
 */
class ChatToolRoundRecoveryTest {

    private fun round(hasContent: Boolean, truncated: Boolean) =
        StreamRoundResult(
            toolCalls = emptyList(),
            reasoningText = "",
            truncated = truncated,
            hasContent = hasContent
        )

    @Test
    fun `complete reply is final and never needs recovery`() {
        assertTrue(round(hasContent = true, truncated = false).isCompleteReply)
    }

    @Test
    fun `empty reply is incomplete and needs recovery`() {
        assertFalse(round(hasContent = false, truncated = false).isCompleteReply)
    }

    @Test
    fun `truncated reply is incomplete even with partial content`() {
        assertFalse(round(hasContent = true, truncated = true).isCompleteReply)
    }

    @Test
    fun `truncated empty reply is incomplete`() {
        assertFalse(round(hasContent = false, truncated = true).isCompleteReply)
    }
}
