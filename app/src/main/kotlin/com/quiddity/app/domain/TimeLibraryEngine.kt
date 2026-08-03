package com.quiddity.app.domain

import com.quiddity.app.data.model.TimePoint
import com.quiddity.app.data.model.TimePointStatus
import com.quiddity.app.util.QuiddityConstants
import java.util.Locale

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
 * 主动消息（时间库）核心纯逻辑引擎。
 *
 * 所有时间解析 / 校验 / 补偿计算 / 重置判定 / 返回值解析均集中于此，
 * 不依赖 Android 框架，可直接单元测试。
 *
 * 对应算法文档规则：
 * - 3.2 生成规则（LLM 输出解析、最多 5 个时间点）
 * - 3.3 时间库存储结构（status 仅 pending / done）
 * - 四、时间库更新与兜底（空结果 / 失败沿用旧库）
 * - 5.2 触发执行流程（返回值严格等于 0 拦截，否则发送）
 * - 6.1 触发延迟补偿（差值 ≤ 5 分钟补发，> 5 分钟放弃）
 * - 七、状态重置（每日首次启动 App 时 done → pending）
 */
object TimeLibraryEngine {

    private val TIME_PATTERN = Regex("""^([01]\d|2[0-3]):([0-5]\d)$""")
    private val TIME_EXTRACT_PATTERN = Regex("""([01]\d|2[0-3]):([0-5]\d)""")

    // ===== 时间解析 =====

    /**
     * 解析 "HH:mm" 为当日分钟数（0 - 1439）。
     * 非法格式（如 25:00、13:5、空串）返回 null。
     */
    fun parseMinutes(time: String): Int? {
        val match = TIME_PATTERN.matchEntire(time.trim()) ?: return null
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        return hour * 60 + minute
    }

    // ===== 时间库生成时机 =====

    /**
     * 是否触发生成：会话级开关开启，且当天尚未生成过（[generatedDate] != [today]）。
     * 每天仅当天首次打开该会话时触发生成；若当天已生成过，再次打开不重复生成。
     */
    fun shouldGenerate(enabled: Boolean, generatedDate: String, today: String): Boolean =
        enabled && generatedDate != today

    // ===== LLM 时间列表输出解析 =====

    /**
     * 解析时间库生成结果：从 LLM 返回文本中提取 24 小时制 "HH:mm" 时间点。
     * - 最多不超过 5 个，按出现顺序、去重
     * - 允许为空列表（LLM 认为当天无需主动发消息）
     */
    fun parseGeneratedTimes(raw: String): List<String> {
        val result = mutableListOf<String>()
        TIME_EXTRACT_PATTERN.findAll(raw).forEach { match ->
            val time = match.value
            if (result.none { it == time }) result.add(time)
        }
        return result.take(QuiddityConstants.ACTIVE_MESSAGE_MAX_POINTS)
    }

    /**
     * 从生成结果中提取 4~6 位数字查看密码；未输出返回空串。
     * 纯数字无冒号，不会被 [parseGeneratedTimes] 误识别为时间。
     */
    fun parseGeneratedPassword(raw: String): String {
        val match = Regex("""【查看密码】\s*(\d{4,6})""").find(raw) ?: return ""
        return match.groupValues[1]
    }

    /**
     * 校验并清洗 AI 输出的密码：只保留数字并取前 6 位；
     * 不足 4 位视为无效返回空串（调用方改用兜底密码）。
     */
    fun sanitizePassword(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.length >= 4) digits.take(6) else ""
    }

    /**
     * 兜底密码：AI 未输出有效密码时使用，按会话 id + 日期稳定生成 6 位数字，
     * 保证"查看时间库"功能任何时候都可用。
     */
    fun fallbackPassword(seed: String): String =
        String.format(Locale.US, "%06d", (seed.hashCode() and 0x7fffffff) % 1_000_000)

    /**
     * 从生成结果中提取"是否告知查看密码"（是/否/true/false/1/0）。
     * 未明确输出"否"时默认告知（保证用户能查看时间库，功能可用）。
     */
    fun parsePasswordRevealed(raw: String): Boolean {
        val match = Regex("""【是否告知】\s*(是|否|true|false|1|0)""").find(raw) ?: return true
        return match.groupValues[1] in setOf("是", "true", "1")
    }

    /**
     * 时间库更新与兜底：
     * - 本次生成结果非空 → 直接覆盖旧时间库（全部初始化为 pending）
     * - 本次生成结果为空列表 → 不采用本次结果，沿用上一次的时间库
     */
    fun mergeGenerated(oldLibrary: List<TimePoint>, generatedTimes: List<String>): List<TimePoint> {
        if (generatedTimes.isEmpty()) return oldLibrary
        return generatedTimes.map { TimePoint(time = it, status = TimePointStatus.PENDING) }
    }

    // ===== 发送决策返回值解析 =====

    /**
     * 解析发送决策返回值：
     * - 返回值严格等于独立数字 0（仅 0，无任何其他字符）→ 拦截，不发送（返回 null）
     * - 返回值包含任何其他内容（文字、标点、0 带句号等）→ 视为"需要发送"，返回该内容
     * - 空白 / 空内容视为不发送（防御性兜底）
     */
    fun parseDecisionResult(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed == "0") return null
        return trimmed
    }

    // ===== 触发延迟补偿 =====

    /**
     * 触发延迟补偿判定：
     * - 当前时间比时间点晚 0~5 分钟 → 立即补发（true）
     * - 晚超过 5 分钟 → 放弃该时间点（false）
     * - 早于时间点（含跨天残留闹钟，如昨日 23:58 在今日 00:01 触发，
     *   差值约 -1437）→ 视为过期，放弃（false）；仅允许 1 分钟提前量吸收时钟抖动
     */
    fun withinLateWindow(timePointMinutes: Int, nowMinutes: Int): Boolean =
        (nowMinutes - timePointMinutes) in -1..QuiddityConstants.ACTIVE_MESSAGE_LATE_WINDOW_MINUTES

    // ===== 每日状态重置 =====

    /**
     * 是否需要执行每日重置：当前日期与上次重置日期不同。
     */
    fun shouldReset(lastResetDate: String, today: String): Boolean = lastResetDate != today

    /**
     * 将时间库中所有 done 重置为 pending。
     */
    fun resetDoneToPending(library: List<TimePoint>): List<TimePoint> =
        library.map { it.copyAsPending() }

    // ===== 时间点状态管理 =====

    /**
     * 是否全部为 done（空库视为全部完成，按"全部 done 则注销闹钟"处理）。
     */
    fun allDone(library: List<TimePoint>): Boolean =
        library.all { it.status == TimePointStatus.DONE }

    /**
     * 将指定时间点标记为 done（无论发送与否，执行后立即改 done）。
     */
    fun markDone(library: List<TimePoint>, time: String): List<TimePoint> =
        library.map { if (it.time == time) it.copyAsDone() else it }

    /**
     * 下一个待调度的 pending 时间点（时间严格晚于当前时间）。
     * 用于闹钟注册：仅注册未来时间点，已过去的时间点留待次日重置或再次打开时重新生成。
     * 返回 null 表示无需调度（无未来 pending 时间点）。
     */
    fun nextSchedulable(library: List<TimePoint>, nowMinutes: Int): TimePoint? =
        library.asSequence()
            .filter { it.isPending }
            .mapNotNull { tp -> parseMinutes(tp.time)?.let { minutes -> tp to minutes } }
            .filter { (_, minutes) -> minutes > nowMinutes }
            .minByOrNull { (_, minutes) -> minutes }
            ?.first
}
