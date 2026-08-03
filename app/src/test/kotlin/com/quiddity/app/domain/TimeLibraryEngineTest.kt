package com.quiddity.app.domain

import com.quiddity.app.data.model.TimePoint
import com.quiddity.app.data.model.TimePointStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * ============================================================================
 * 开发规范 (Development Specifications)
 * ============================================================================
 *
 * 1. 问题修复规范
 *    所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
 *    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
 *
 * 2. 代码注释规范
 *    文件内仅允许保留两类注释：
 *    - 当前规则说明注释（即本注释块）
 *    - 模块划分注释（用于标识代码功能模块边界）
 *    除此之外，禁止出现任何形式的代码注释（包括但不限于单行注释、多行注释、临时调试注释等）。
 *
 * 3. 构建交付要求
 *    在完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK 文件。
 *    APK 文件需满足以下条件：
 *    - 签名有效且符合发布标准
 *    - 包含完整的功能模块
 *    - 经过基础性能测试和兼容性测试
 *    以便在真实设备环境中进行功能验证和性能评估。
 *
 * ============================================================================
 */



/**
 * [TimeLibraryEngine] 单元测试。
 *
 * 覆盖算法文档中全部可纯逻辑验证的规则：
 * - 3.2 生成规则（时间解析、最多 5 个、去重、允许空）
 * - 3.3 存储结构（status 仅 pending / done）
 * - 四、更新与兜底（非空覆盖 / 空结果沿用旧库）
 * - 5.2 返回值解析（严格等于 0 拦截，否则发送）
 * - 6.1 延迟补偿（≤5 分钟补发，>5 分钟放弃）
 * - 七、状态重置（done → pending）
 * - 八、任务注销（全 done 判定）
 */
class TimeLibraryEngineTest {

    // ============================================================
    // 一、时间解析 parseMinutes
    // ============================================================

    @Test
    fun `parseMinutes - valid times`() {
        assertEquals(0, TimeLibraryEngine.parseMinutes("00:00"))
        assertEquals(810, TimeLibraryEngine.parseMinutes("13:30"))
        assertEquals(1439, TimeLibraryEngine.parseMinutes("23:59"))
        assertEquals(810, TimeLibraryEngine.parseMinutes(" 13:30 "))
    }

    @Test
    fun `parseMinutes - invalid times return null`() {
        assertNull(TimeLibraryEngine.parseMinutes("25:00"))
        assertNull(TimeLibraryEngine.parseMinutes("13:5"))
        assertNull(TimeLibraryEngine.parseMinutes(""))
        assertNull(TimeLibraryEngine.parseMinutes("abc"))
        assertNull(TimeLibraryEngine.parseMinutes("1:30"))
    }

    // ============================================================
    // 二、生成时机 shouldGenerate
    // ============================================================

    @Test
    fun `shouldGenerate - only when enabled and not generated today`() {
        assertTrue(TimeLibraryEngine.shouldGenerate(true, "2026-08-01", "2026-08-02"))
        assertFalse(TimeLibraryEngine.shouldGenerate(true, "2026-08-02", "2026-08-02"))
        assertFalse(TimeLibraryEngine.shouldGenerate(false, "2026-08-01", "2026-08-02"))
        // 从未生成过（空日期）：开启后应触发生成
        assertTrue(TimeLibraryEngine.shouldGenerate(true, "", "2026-08-02"))
    }

    // ============================================================
    // 三、LLM 时间列表解析 parseGeneratedTimes
    // ============================================================

    @Test
    fun `parseGeneratedTimes - extracts times from free text`() {
        val raw = "建议在 13:30 和 12:32 联系，晚上 15:45 再说。"
        assertEquals(
            listOf("13:30", "12:32", "15:45"),
            TimeLibraryEngine.parseGeneratedTimes(raw)
        )
    }

    @Test
    fun `parseGeneratedTimes - deduplicates and caps at 5`() {
        val raw = "13:30\n13:30\n14:00\n15:00\n16:00\n17:00\n18:00"
        val result = TimeLibraryEngine.parseGeneratedTimes(raw)
        assertEquals(5, result.size)
        assertEquals(result.size, result.toSet().size, "结果必须去重")
        assertEquals("13:30", result.first())
    }

    @Test
    fun `parseGeneratedTimes - empty result allowed`() {
        assertTrue(TimeLibraryEngine.parseGeneratedTimes("今天不需要主动联系").isEmpty())
        assertTrue(TimeLibraryEngine.parseGeneratedTimes("").isEmpty())
    }

    @Test
    fun `parseGeneratedPassword extracts digits only`() {
        val raw = "13:30\n14:00\n【查看密码】 520131\n【是否告知】是"
        assertEquals("520131", TimeLibraryEngine.parseGeneratedPassword(raw))
        assertTrue(TimeLibraryEngine.parsePasswordRevealed(raw))
    }

    @Test
    fun `parseGeneratedPassword returns empty when absent`() {
        assertEquals("", TimeLibraryEngine.parseGeneratedPassword("13:30"))
        assertTrue(TimeLibraryEngine.parsePasswordRevealed("13:30"), "未明确说「否」时默认告知，保证功能可用")
    }

    @Test
    fun `password digits are not parsed as times`() {
        val raw = "13:30\n【查看密码】520131\n【是否告知】否"
        assertEquals(listOf("13:30"), TimeLibraryEngine.parseGeneratedTimes(raw))
        assertFalse(TimeLibraryEngine.parsePasswordRevealed(raw))
    }

    @Test
    fun `sanitizePassword keeps digits and validates length`() {
        assertEquals("520131", TimeLibraryEngine.sanitizePassword("520131"))
        assertEquals("123456", TimeLibraryEngine.sanitizePassword("密码123456abc"))
        assertEquals("", TimeLibraryEngine.sanitizePassword("12"))
        assertEquals("", TimeLibraryEngine.sanitizePassword("abcd"))
    }

    @Test
    fun `fallbackPassword is deterministic and six digits`() {
        val a = TimeLibraryEngine.fallbackPassword("conv_1|2026-08-03")
        val b = TimeLibraryEngine.fallbackPassword("conv_1|2026-08-03")
        assertEquals(a, b)
        assertEquals(6, a.length)
        assertTrue(a.all { it.isDigit() })
    }

    // ============================================================
    // 四、时间库更新与兜底 mergeGenerated
    // ============================================================

    @Test
    fun `mergeGenerated - non-empty result overwrites old library as pending`() {
        val old = listOf(TimePoint("09:00"), TimePoint("20:00"))
        val merged = TimeLibraryEngine.mergeGenerated(old, listOf("13:30", "14:00"))
        assertEquals(listOf("13:30", "14:00"), merged.map { it.time })
        assertTrue(merged.all { it.status == TimePointStatus.PENDING }, "覆盖后的新库必须全部为 pending")
    }

    @Test
    fun `mergeGenerated - empty result keeps old library`() {
        val old = listOf(TimePoint("09:00"), TimePoint("20:00"))
        val merged = TimeLibraryEngine.mergeGenerated(old, emptyList())
        assertEquals(old, merged, "空生成结果应沿用上一次的时间库")
    }

    @Test
    fun `mergeGenerated - empty result and no old library stays empty`() {
        assertTrue(TimeLibraryEngine.mergeGenerated(emptyList(), emptyList()).isEmpty())
    }

    // ============================================================
    // 五、发送决策返回值解析 parseDecisionResult
    // ============================================================

    @Test
    fun `parseDecisionResult - strict zero blocks sending`() {
        assertNull(TimeLibraryEngine.parseDecisionResult("0"))
        assertNull(TimeLibraryEngine.parseDecisionResult("  0  "))
    }

    @Test
    fun `parseDecisionResult - blank blocks sending`() {
        assertNull(TimeLibraryEngine.parseDecisionResult(""))
        assertNull(TimeLibraryEngine.parseDecisionResult("   "))
    }

    @Test
    fun `parseDecisionResult - any other content means send`() {
        assertEquals("1", TimeLibraryEngine.parseDecisionResult("1"))
        assertEquals("0.0", TimeLibraryEngine.parseDecisionResult("0.0"))
        assertEquals("0。", TimeLibraryEngine.parseDecisionResult("0。"))
        assertEquals("好的，我现在发消息", TimeLibraryEngine.parseDecisionResult("好的，我现在发消息"))
        assertEquals("10", TimeLibraryEngine.parseDecisionResult("10"))
    }

    // ============================================================
    // 六、触发延迟补偿 withinLateWindow
    // ============================================================

    @Test
    fun `withinLateWindow - within 5 minutes sends`() {
        // 13:30 触发点，13:35 唤醒：差值 5 分钟 ≤ 5 → 补发
        assertTrue(TimeLibraryEngine.withinLateWindow(13 * 60 + 30, 13 * 60 + 35))
        // 准时触发：差值 0
        assertTrue(TimeLibraryEngine.withinLateWindow(13 * 60 + 30, 13 * 60 + 30))
        // 提前唤醒（差值 < 0）：按立即处理
        assertTrue(TimeLibraryEngine.withinLateWindow(13 * 60 + 30, 13 * 60 + 29))
    }

    @Test
    fun `withinLateWindow - beyond 5 minutes abandons`() {
        // 13:30 触发点，13:36 唤醒：差值 6 分钟 > 5 → 放弃
        assertFalse(TimeLibraryEngine.withinLateWindow(13 * 60 + 30, 13 * 60 + 36))
    }

    // ============================================================
    // 七、每日状态重置 shouldReset / resetDoneToPending
    // ============================================================

    @Test
    fun `shouldReset - different date resets, same date skips`() {
        assertTrue(TimeLibraryEngine.shouldReset("2026-08-01", "2026-08-02"))
        assertFalse(TimeLibraryEngine.shouldReset("2026-08-02", "2026-08-02"))
        // 从未重置过：应执行首次重置
        assertTrue(TimeLibraryEngine.shouldReset("", "2026-08-02"))
    }

    @Test
    fun `resetDoneToPending - all done become pending`() {
        val library = listOf(
            TimePoint("13:30", TimePointStatus.DONE),
            TimePoint("14:00", TimePointStatus.PENDING)
        )
        val reset = TimeLibraryEngine.resetDoneToPending(library)
        assertTrue(reset.all { it.status == TimePointStatus.PENDING }, "重置后所有时间点必须为 pending")
        assertEquals(listOf("13:30", "14:00"), reset.map { it.time }, "重置不改变时间点内容")
    }

    // ============================================================
    // 八、任务注销 allDone / markDone / nextSchedulable
    // ============================================================

    @Test
    fun `allDone - true when every point done or empty`() {
        assertTrue(TimeLibraryEngine.allDone(emptyList()))
        assertTrue(TimeLibraryEngine.allDone(listOf(TimePoint("13:30", TimePointStatus.DONE))))
        assertFalse(
            TimeLibraryEngine.allDone(
                listOf(TimePoint("13:30"), TimePoint("14:00", TimePointStatus.DONE))
            )
        )
    }

    @Test
    fun `markDone - only the target point becomes done`() {
        val library = listOf(TimePoint("13:30"), TimePoint("14:00"))
        val marked = TimeLibraryEngine.markDone(library, "13:30")
        assertEquals(TimePointStatus.DONE, marked[0].status)
        assertEquals(TimePointStatus.PENDING, marked[1].status, "其他时间点状态不受影响")
    }

    @Test
    fun `markDone - unknown time keeps library unchanged`() {
        val library = listOf(TimePoint("13:30"))
        assertEquals(library, TimeLibraryEngine.markDone(library, "99:99"))
    }

    @Test
    fun `nextSchedulable - returns nearest future pending`() {
        val library = listOf(
            TimePoint("09:00"),   // 已过去
            TimePoint("13:30"),   // 未来最近
            TimePoint("14:00"),
            TimePoint("20:00", TimePointStatus.DONE) // done 不参与调度
        )
        val next = TimeLibraryEngine.nextSchedulable(library, 12 * 60)
        assertEquals("13:30", next?.time)
    }

    @Test
    fun `nextSchedulable - null when no future pending`() {
        val library = listOf(
            TimePoint("09:00"),
            TimePoint("10:00", TimePointStatus.DONE)
        )
        assertNull(TimeLibraryEngine.nextSchedulable(library, 12 * 60))
        assertNull(TimeLibraryEngine.nextSchedulable(emptyList(), 12 * 60))
    }

    @Test
    fun `nextSchedulable - exact now time is not schedulable`() {
        val library = listOf(TimePoint("12:00"))
        assertNull(TimeLibraryEngine.nextSchedulable(library, 12 * 60), "严格晚于当前时间才注册")
    }
}
