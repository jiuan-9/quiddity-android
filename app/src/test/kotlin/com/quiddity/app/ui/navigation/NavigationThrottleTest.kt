package com.quiddity.app.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigationThrottleTest {

    @Test
    fun tryNavigate_blocksRapidRepeat_andAllowsAfterWindow() {
        var now = 1_000L
        val throttle = NavigationThrottle(windowMs = 600, clock = { now })
        var opened = 0
        fun open() = throttle.tryNavigate { opened++ }

        assertTrue(open())
        assertFalse(open())
        now = 1_599
        assertFalse(open())
        now = 1_600
        assertTrue(open())
        assertEquals(2, opened)
    }
}
