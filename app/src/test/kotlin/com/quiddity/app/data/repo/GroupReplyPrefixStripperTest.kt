package com.quiddity.app.data.repo

import kotlin.test.Test
import kotlin.test.assertEquals

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
