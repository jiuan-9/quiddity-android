package com.quiddity.app.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [SendDelayGate] 发送延迟放行判定测试（编辑感知防抖）。
 */
class SendDelayGateTest {

    @Test
    fun `blank input and silent for full delay fires`() {
        assertTrue(SendDelayGate.shouldFire("", 1000L, 4000L, 3000L))
    }

    @Test
    fun `recent edit does not fire`() {
        assertFalse(SendDelayGate.shouldFire("", 3500L, 4000L, 3000L))
    }

    @Test
    fun `non blank input never fires`() {
        assertFalse(SendDelayGate.shouldFire("还在打字", 1000L, 4000L, 3000L))
    }

    @Test
    fun `backspace to empty then wait full delay fires`() {
        assertFalse(SendDelayGate.shouldFire("", 3500L, 6000L, 3000L))
        assertTrue(SendDelayGate.shouldFire("", 3500L, 6500L, 3000L))
    }

    @Test
    fun `zero delay means disabled and fires immediately`() {
        assertTrue(SendDelayGate.shouldFire("", 0L, 0L, 0L))
        assertTrue(SendDelayGate.shouldFire("", 1234L, 1234L, 0L))
    }
}
