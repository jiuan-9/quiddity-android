package com.quiddity.app.data.repo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolRoundBudgetTest {
    @Test
    fun `observation tools consume total rounds but not modify or delete quota`() {
        val budget = ToolRoundBudget()
        budget.consume(listOf("read_screen", "screenshot", "ocr_image"))
        assertEquals(1, budget.totalRounds)
        assertEquals(0, budget.modifyRounds)
        assertEquals(0, budget.deleteRounds)
        assertFalse(budget.reachedLimit)
        assertTrue(budget.keepTools())
    }

    @Test
    fun `modify tools count against modify quota`() {
        val budget = ToolRoundBudget()
        repeat(7) { budget.consume(listOf("write_file")) }
        assertEquals(7, budget.modifyRounds)
        assertFalse(budget.reachedLimit)
        budget.consume(listOf("write_file"))
        assertEquals(8, budget.modifyRounds)
        assertTrue(budget.reachedLimit)
        assertFalse(budget.keepTools())
    }

    @Test
    fun `delete tools count against delete quota`() {
        val budget = ToolRoundBudget()
        repeat(5) { budget.consume(listOf("delete_file")) }
        assertFalse(budget.reachedLimit)
        budget.consume(listOf("delete_file"))
        assertEquals(6, budget.deleteRounds)
        assertTrue(budget.reachedLimit)
    }

    @Test
    fun `total rounds cap triggers limit`() {
        val budget = ToolRoundBudget(maxTotalRounds = 3)
        repeat(2) { budget.consume(emptyList()) }
        assertFalse(budget.reachedLimit)
        budget.consume(emptyList())
        assertTrue(budget.reachedLimit)
        assertEquals(3, budget.totalRounds)
    }

    @Test
    fun `mixed categories accumulate independently`() {
        val budget = ToolRoundBudget()
        budget.consume(listOf("read_screen", "click"))
        budget.consume(listOf("write_file", "delete_file"))
        assertEquals(2, budget.totalRounds)
        assertEquals(1, budget.modifyRounds)
        assertEquals(1, budget.deleteRounds)
    }
}
