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

    @Test
    fun `group chat constants follow the 1_5_0 plan`() {
        assertEquals(50, QuiddityConstants.GROUP_DEFAULT_CONTEXT_LIMIT)
        assertEquals(1, QuiddityConstants.GROUP_MIN_CONTEXT_LIMIT)
        assertEquals(200, QuiddityConstants.GROUP_MAX_CONTEXT_LIMIT)
        assertEquals(3, QuiddityConstants.GROUP_MAX_MEMBERS)
        assertEquals(5, QuiddityConstants.GROUP_RETRY_COUNT)
        assertEquals("B", QuiddityConstants.GROUP_DEFAULT_STOP_MODE)
        assertTrue(QuiddityConstants.GROUP_DEFAULT_STOP_MODE in
            listOf(QuiddityConstants.GROUP_STOP_MODE_A, QuiddityConstants.GROUP_STOP_MODE_B))
        assertTrue(QuiddityConstants.GROUP_DEFAULT_CONTEXT_LIMIT in
            QuiddityConstants.GROUP_MIN_CONTEXT_LIMIT..QuiddityConstants.GROUP_MAX_CONTEXT_LIMIT)
    }

    @Test
    fun `clampTemperature respects model specific max`() {
        assertEquals(1.0, QuiddityConstants.clampTemperature(1.5, max = 1.0), "超过模型上限应钳制到上限")
        assertEquals(0.8, QuiddityConstants.clampTemperature(0.8, max = 1.0), "范围内原样返回")
        assertEquals(2.0, QuiddityConstants.clampTemperature(2.5), "未指定上限时按全局 2.0 钳制")
        assertEquals(0.0, QuiddityConstants.clampTemperature(-0.5, max = 1.0), "低于下限应钳制到 0")
    }
}
