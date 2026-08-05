package com.quiddity.app.domain

/**
 * 发送延迟放行判定（纯函数，便于单测）。
 *
 * 语义：仅当输入框为空，且距用户最后一次编辑（含退格）已静默满 delayMs 时才放行。
 * 任何编辑都会重新计时——修复"退格清空输入框后立即触发加载"的问题。
 */
object SendDelayGate {
    fun shouldFire(text: String, lastEditAt: Long, now: Long, delayMs: Long): Boolean =
        text.isBlank() && now - lastEditAt >= delayMs
}
