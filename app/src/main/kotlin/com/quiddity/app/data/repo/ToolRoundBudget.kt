package com.quiddity.app.data.repo

import com.quiddity.app.domain.agent.AgentToolCategory
import com.quiddity.app.domain.agent.categoryOf

/**
 * 单轮工具调用预算：按工具类别分配额度。
 *
 * - 观察类（READ / ACT / OCR，如截图、识图、读屏）不设单独上限，
 *   屏幕操控这类“边看边操作”的流程可以放开跑；
 * - 写类（MODIFY）与删除类（DELETE）设硬上限，防止危险操作反复尝试；
 * - 总轮数兜底，任何情况下都强制收尾，避免死循环。
 */
internal class ToolRoundBudget(
    val maxTotalRounds: Int = 40,
    val maxModifyRounds: Int = 8,
    val maxDeleteRounds: Int = 6
) {
    var totalRounds = 0
        private set
    var modifyRounds = 0
        private set
    var deleteRounds = 0
        private set

    /**
     * 消耗一轮（含截断/失败重试轮）；[callNames] 为本轮实际发起的工具名，
     * 用于按类别累计写/删配额，观察类不占配额。
     */
    fun consume(callNames: Collection<String>) {
        totalRounds++
        for (name in callNames) {
            when (categoryOf(name)) {
                AgentToolCategory.MODIFY -> modifyRounds++
                AgentToolCategory.DELETE -> deleteRounds++
                else -> Unit
            }
        }
    }

    /** 是否已达任一硬上限（总轮数 / 写类 / 删除类）。 */
    val reachedLimit: Boolean
        get() = totalRounds >= maxTotalRounds ||
            modifyRounds >= maxModifyRounds ||
            deleteRounds >= maxDeleteRounds

    /** 下一轮是否仍允许携带工具。 */
    fun keepTools(): Boolean = !reachedLimit
}
