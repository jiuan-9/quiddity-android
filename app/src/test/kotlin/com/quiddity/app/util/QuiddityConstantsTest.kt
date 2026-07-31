package com.quiddity.app.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [QuiddityConstants] 不变量测试。
 *
 * 集中常量的目的是"单一真相源"——任何修改都应通过测试保证不变量不破。
 */
class QuiddityConstantsTest {

    @Test
    fun `token limits are in correct order`() {
        assertTrue(QuiddityConstants.MIN_MAX_TOKENS < QuiddityConstants.MAX_MAX_TOKENS)
        assertTrue(
            QuiddityConstants.MIN_SINGLE_MESSAGE_TOKENS < QuiddityConstants.MAX_SINGLE_MESSAGE_TOKENS
        )
        assertTrue(QuiddityConstants.MIN_CONTEXT_LIMIT < QuiddityConstants.MAX_CONTEXT_LIMIT)
    }

    @Test
    fun `defaults are within bounds`() {
        // 深层重构：默认值必须在 MIN/MAX 范围内，否则 setter coerceIn 会强制改值
        assertTrue(QuiddityConstants.DEFAULT_MAX_TOKENS in
            QuiddityConstants.MIN_MAX_TOKENS..QuiddityConstants.MAX_MAX_TOKENS)
        assertTrue(QuiddityConstants.DEFAULT_SINGLE_MESSAGE_TOKENS in
            QuiddityConstants.MIN_SINGLE_MESSAGE_TOKENS..QuiddityConstants.MAX_SINGLE_MESSAGE_TOKENS)
        assertTrue(QuiddityConstants.DEFAULT_CONTEXT_LIMIT in
            QuiddityConstants.MIN_CONTEXT_LIMIT..QuiddityConstants.MAX_CONTEXT_LIMIT)
    }

    @Test
    fun `wallpaper darken is in 0 to 1 range`() {
        assertEquals(0.0f, QuiddityConstants.MIN_WALLPAPER_DARKEN)
        assertEquals(1.0f, QuiddityConstants.MAX_WALLPAPER_DARKEN)
        assertTrue(QuiddityConstants.DEFAULT_WALLPAPER_DARKEN in 0.0f..1.0f)
    }

    @Test
    fun `timeouts are positive`() {
        assertTrue(QuiddityConstants.CONNECT_TIMEOUT_SECONDS > 0)
        assertTrue(QuiddityConstants.READ_TIMEOUT_SECONDS > 0)
        assertTrue(QuiddityConstants.WRITE_TIMEOUT_SECONDS > 0)
    }
}
