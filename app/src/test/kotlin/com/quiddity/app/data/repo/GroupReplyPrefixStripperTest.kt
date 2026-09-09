package com.quiddity.app.data.repo

import kotlin.test.Test
import kotlin.test.assertEquals

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
 * 群聊回复前缀流式剥离器测试：前缀可能跨 delta 分片到达，剥离必须不误伤正文、
 * 不产生空白消息。
 */
class GroupReplyPrefixStripperTest {

    private fun stripper(vararg names: String) = GroupReplyPrefixStripper(names.toList())

    @Test
    fun `prefix split across deltas is stripped once`() {
        val s = stripper("小A", "小B", "小明")
        assertEquals("", s.accept("小A"))
        assertEquals("", s.accept("："))
        assertEquals("你好呀。", s.accept("你好呀。"))
        assertEquals("继续。", s.accept("继续。"), "匹配后应切换直通，不再处理后续 delta")
    }

    @Test
    fun `full prefix in single delta is stripped`() {
        val s = stripper("小A")
        assertEquals("你好。", s.accept("小A：你好。"))
    }

    @Test
    fun `ascii colon prefix is stripped`() {
        val s = stripper("小明")
        assertEquals("你好", s.accept("小明:你好"))
    }

    @Test
    fun `prefix with leading whitespace is stripped`() {
        val s = stripper("小A")
        assertEquals("你好。", s.accept("\n小A：你好。"))
    }

    @Test
    fun `reply that is only the prefix yields no content`() {
        val s = stripper("小A")
        assertEquals("", s.accept("小A："))
        assertEquals("", s.accept(""), "流结束（无更多内容）时不应产出任何内容")
    }

    @Test
    fun `content without prefix passes through unchanged`() {
        val s = stripper("小A")
        assertEquals("你们好呀。", s.accept("你们好呀。"))
    }

    @Test
    fun `word sharing name prefix is not stripped`() {
        val s = stripper("小明")
        assertEquals("小明白了吗？", s.accept("小明白了吗？"))
    }

    @Test
    fun `longer name wins over shorter prefix`() {
        val s = stripper("小A", "小A同学")
        assertEquals("你好", s.accept("小A同学：你好"))
    }

    @Test
    fun `name without colon releases content once unambiguous`() {
        val s = stripper("小A")
        assertEquals("小A说：你好", s.accept("小A说：你好"), "名字后不是冒号时不剥离")
    }
}
