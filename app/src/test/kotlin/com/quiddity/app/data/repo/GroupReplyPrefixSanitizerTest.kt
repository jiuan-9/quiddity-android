package com.quiddity.app.data.repo

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 群聊回复前缀剥离测试：模型误输出「名字：」前缀时剥掉（方案五）。
 */
class GroupReplyPrefixSanitizerTest {

    private val sanitizer = GroupReplyPrefixSanitizer(listOf("a", "b", "小明", "用户"))

    @Test
    fun `strips own name prefix with full-width colon`() {
        assertEquals("嗯？你们好", sanitizer.clean("a：嗯？你们好"))
    }

    @Test
    fun `strips other member name prefix`() {
        assertEquals("嗯？你们好", sanitizer.clean("b：嗯？你们好"))
    }

    @Test
    fun `strips ascii colon prefix`() {
        assertEquals("你好", sanitizer.clean("小明:你好"))
    }

    @Test
    fun `strips repeated prefixes`() {
        assertEquals("内容", sanitizer.clean("a：b：内容"))
    }

    @Test
    fun `keeps content without prefix`() {
        assertEquals("你们好呀", sanitizer.clean("你们好呀"))
    }

    @Test
    fun `strips prefix with leading whitespace`() {
        assertEquals("你好", sanitizer.clean("a：你好"))
    }

    @Test
    fun `strips prefix from incremental accumulated content`() {
        assertEquals("嗯", sanitizer.clean("a：嗯"))
        assertEquals("嗯？你们好", sanitizer.clean("a：嗯？你们好"))
    }
}
