package com.quiddity.app.util

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
 * Token 估算工具（启发式）。
 *
 * 规则：
 * - 中文 1.5 token/字
 * - 英文字母 0.25 token/字母（即 4 个字母 ≈ 1 token）
 * - emoji 3 token
 * - 数字/标点 0.5 token
 *
 * 仅供 UI 显示参考，非精确计数。
 *
 * 对齐 PC 端 persona-translator.js 的字量统计逻辑。
 */
object TokenEstimator {

    private const val CJK_RANGE_START = 0x4E00
    private const val CJK_RANGE_END = 0x9FFF
    private const val EMOJI_RANGE_START = 0x1F000
    private const val EMOJI_RANGE_END = 0x1FFFF

    /**
     * 估算文本的 token 数。
     *
     * 规则：
     * - 中文字符（CJK统一表意文字）= 1.5 token
     * - emoji = 3 token
     * - ASCII 字母 = 0.25 token（4字母≈1token）
     * - 数字 = 0.5 token
     * - 空白 = 0 token
     * - 其他（标点/符号）= 0.5 token
     */
    fun estimate(text: String): Int {
        if (text.isEmpty()) return 0
        var total = 0.0
        text.forEach { ch ->
            val code = ch.code
            total += when {
                code in CJK_RANGE_START..CJK_RANGE_END -> 1.5
                code in EMOJI_RANGE_START..EMOJI_RANGE_END -> 3.0
                ch.isLetter() && code < 0x80 -> 0.25
                ch.isDigit() -> 0.5
                ch.isWhitespace() -> 0.0
                else -> 0.5
            }
        }
        return total.toInt()
    }

    /**
     * 统计文本的"字量"（可见字符数，不含空白）。
     *
     * 用于人设卡字量统计——用户关心的是"我写了多少字"，而非 token 数。
     * 中文字符每个计 1，英文字母每个计 1，数字每个计 1，标点每个计 1，空白不计。
     *
     * @param text 待统计文本
     * @return 可见字符数
     */
    fun countChars(text: String): Int {
        if (text.isEmpty()) return 0
        return text.count { !it.isWhitespace() }
    }

    /**
     * 统计文本的详细信息（字量 + token 估算）。
     *
     * 一次性返回字数和 token 数，避免对同一文本遍历两次。
     */
    data class TextStats(val charCount: Int, val tokenEstimate: Int)

    fun analyze(text: String): TextStats {
        if (text.isEmpty()) return TextStats(0, 0)
        var chars = 0
        var tokens = 0.0
        text.forEach { ch ->
            val code = ch.code
            if (!ch.isWhitespace()) chars++
            tokens += when {
                code in CJK_RANGE_START..CJK_RANGE_END -> 1.5
                code in EMOJI_RANGE_START..EMOJI_RANGE_END -> 3.0
                ch.isLetter() && code < 0x80 -> 0.25
                ch.isDigit() -> 0.5
                ch.isWhitespace() -> 0.0
                else -> 0.5
            }
        }
        return TextStats(chars, tokens.toInt())
    }
}
