package com.quiddity.app.domain

import com.quiddity.app.data.model.Conversation
import com.quiddity.app.ui.chat.CompressionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompressionStateMachineTest {
    private fun conv(
        enabled: Boolean = true,
        rounds: Int = 20,
        lastCompressed: Int = 0
    ) = Conversation(id = "c1", createdAt = 0L, updatedAt = 0L).copy(
        memoryBankEnabled = enabled,
        memoryBankRounds = rounds,
        lastCompressedAtRound = lastCompressed
    )

    @Test
    fun `shouldCompress false when memory bank disabled`() {
        assertFalse(CompressionStateMachine.shouldCompress(conv(enabled = false), userRounds = 50))
    }

    @Test
    fun `shouldCompress false below threshold`() {
        assertFalse(CompressionStateMachine.shouldCompress(conv(rounds = 20, lastCompressed = 10), userRounds = 29))
    }

    @Test
    fun `shouldCompress true at or above threshold`() {
        assertTrue(CompressionStateMachine.shouldCompress(conv(rounds = 20, lastCompressed = 10), userRounds = 30))
        assertTrue(CompressionStateMachine.shouldCompress(conv(rounds = 20, lastCompressed = 10), userRounds = 35))
    }

    @Test
    fun `resultState maps success and failure`() {
        assertEquals(CompressionState.Success, CompressionStateMachine.resultState(success = true))
        assertEquals(CompressionState.Failed, CompressionStateMachine.resultState(success = false))
    }

    @Test
    fun `consume resets transient states to idle`() {
        assertEquals(CompressionState.Idle, CompressionStateMachine.consume(CompressionState.Success))
        assertEquals(CompressionState.Idle, CompressionStateMachine.consume(CompressionState.Failed))
    }

    @Test
    fun `consume keeps idle and compressing unchanged`() {
        assertEquals(CompressionState.Idle, CompressionStateMachine.consume(CompressionState.Idle))
        assertEquals(CompressionState.Compressing, CompressionStateMachine.consume(CompressionState.Compressing))
    }
}
