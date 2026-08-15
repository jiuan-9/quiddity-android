package com.quiddity.app.domain.agent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Agent 任务编排器单元测试（方案 B'）：去重、失败重试、参数规范化、零介入保证。
 */
class AgentWorkflowControllerTest {

    private fun obj(vararg pairs: Pair<String, String>): JsonObject =
        JsonObject(pairs.associate { (k, v) -> k to JsonPrimitive(v) })

    @Test
    fun firstCall_neverDuplicated_zeroIntervention() {
        // 0~1 次调用场景：历史为空，任何调用都不去重（零介入）
        val c = AgentWorkflowController()
        assertNull(c.findDuplicate("read_screen", obj("maxChars" to "100")))
        assertNull(c.findDuplicate("list_apps", JsonObject(emptyMap())))
        assertEquals(0, c.executedCount())
    }

    @Test
    fun duplicateCall_reusesPreviousSuccess() {
        val c = AgentWorkflowController()
        val args = obj("pkg" to "com.tencent.mm")
        c.record("app_battery", args, ok = true, result = "耗电 12%")
        // 同工具同参数再次调用 → 命中缓存
        val hit = c.findDuplicate("app_battery", obj("pkg" to "com.tencent.mm"))
        assertNotNull(hit)
        assertEquals("耗电 12%", hit!!.result)
        // 不同参数 → 不命中
        assertNull(c.findDuplicate("app_battery", obj("pkg" to "com.example")))
        // 不同工具 → 不命中
        assertNull(c.findDuplicate("app_permissions", args))
    }

    @Test
    fun failedCall_notDeduplicated() {
        // 失败记录不可用于去重（避免把失败结果当成功复用）
        val c = AgentWorkflowController()
        c.record("read_file", obj("path" to "/sdcard/a.txt"), ok = false, result = "读取失败")
        assertNull(c.findDuplicate("read_file", obj("path" to "/sdcard/a.txt")))
    }

    @Test
    fun normalizeArgs_ignoresKeyOrder() {
        val c = AgentWorkflowController()
        val a = obj("pkg" to "com.a", "mode" to "allow")
        val b = obj("mode" to "allow", "pkg" to "com.a")
        assertEquals(c.normalizeArgs(a), c.normalizeArgs(b))
    }

    @Test
    fun shouldRetry_onlyRetryableFailures() {
        val c = AgentWorkflowController()
        val args = obj("path" to "/sdcard/x")
        // 成功 → 不重试
        assertFalse(c.shouldRetry("read_file", args, ok = true, retryable = true))
        // 可重试失败（执行报错）→ 重试
        assertTrue(c.shouldRetry("read_file", args, ok = false, retryable = true))
        // 用户取消/未确认导致的失败（retryable=false）→ 绝不重试
        assertFalse(c.shouldRetry("read_file", args, ok = false, retryable = false))
    }

    @Test
    fun retryOnlyOncePerCall() {
        val c = AgentWorkflowController()
        val args = obj("path" to "/sdcard/x")
        assertTrue(c.shouldRetry("read_file", args, ok = false, retryable = true))
        c.markRetried("read_file", args)
        // 重试后再次失败 → 不再重试（防止死循环）
        assertFalse(c.shouldRetry("read_file", args, ok = false, retryable = true))
    }

    @Test
    fun executedCount_tracksRealExecutions() {
        val c = AgentWorkflowController()
        c.record("list_apps", JsonObject(emptyMap()), ok = true, result = "ok")
        c.record("list_apps", JsonObject(emptyMap()), ok = true, result = "ok")
        assertEquals(2, c.executedCount())
    }
}
