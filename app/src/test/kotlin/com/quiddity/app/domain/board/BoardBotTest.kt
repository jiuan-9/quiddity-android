package com.quiddity.app.domain.board

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoardBotTest {

    @Test
    fun gomokuBot_returnsLegalMove() {
        val state = BoardState(gameType = BoardGameType.GOMOKU)
        val move = BoardBot.nextMove(state)
        assertNotNull(move)
        assertIs<MoveOutcome.Played>(state.applyMove(move))
    }

    @Test
    fun gomokuBot_takesWinningMoveWhenFourInRow() {
        val grid = MutableList(15 * 15) { 0 }
        for (c in 3..6) grid[7 * 15 + c] = Stone.BLACK.code
        val state = BoardState(gameType = BoardGameType.GOMOKU, grid = grid.toList(), current = Stone.BLACK)
        val move = BoardBot.nextMove(state, Random(1))
        assertNotNull(move)
        assertEquals(7, move.row)
        assertTrue(move.col == 2 || move.col == 7, "应落子在连珠两端补成五连")
    }

    @Test
    fun gomokuBot_blocksOpponentFour() {
        val grid = MutableList(15 * 15) { 0 }
        for (c in 3..6) grid[7 * 15 + c] = Stone.BLACK.code
        val state = BoardState(gameType = BoardGameType.GOMOKU, grid = grid.toList(), current = Stone.WHITE)
        val move = BoardBot.nextMove(state, Random(1))
        assertNotNull(move)
        assertEquals(7, move.row)
        assertTrue(move.col == 2 || move.col == 7, "应堵住黑棋四连")
    }

    @Test
    fun hardDifficulty_takesWinningMoveAndBlocksThreat() {
        val grid = MutableList(15 * 15) { 0 }
        for (c in 3..6) grid[7 * 15 + c] = Stone.BLACK.code
        val toWin = BoardState(gameType = BoardGameType.GOMOKU, grid = grid.toList(), current = Stone.BLACK)
        val winMove = BoardBot.nextMove(toWin, Random(1), difficulty = BoardDifficulty.HARD)
        assertNotNull(winMove)
        assertEquals(7, winMove.row)
        assertTrue(winMove.col == 2 || winMove.col == 7, "困难难度应直接成五")

        val toBlock = BoardState(gameType = BoardGameType.GOMOKU, grid = grid.toList(), current = Stone.WHITE)
        val blockMove = BoardBot.nextMove(toBlock, Random(1), difficulty = BoardDifficulty.HARD)
        assertNotNull(blockMove)
        assertEquals(7, blockMove.row)
        assertTrue(blockMove.col == 2 || blockMove.col == 7, "困难难度应堵住对手四连")
    }

    @Test
    fun easyDifficulty_returnsLegalMove() {
        val grid = MutableList(15 * 15) { 0 }
        for (c in 3..6) grid[7 * 15 + c] = Stone.BLACK.code
        val state = BoardState(gameType = BoardGameType.GOMOKU, grid = grid.toList(), current = Stone.WHITE)
        val move = BoardBot.nextMove(state, Random(7), difficulty = BoardDifficulty.EASY)
        assertNotNull(move)
        assertIs<MoveOutcome.Played>(state.applyMove(move))
    }

    @Test
    fun goBot_returnsLegalMove() {
        val state = BoardState(gameType = BoardGameType.GO)
        val move = BoardBot.nextMove(state, Random(1))
        assertNotNull(move)
        assertIs<MoveOutcome.Played>(state.applyMove(move))
    }

    @Test
    fun goBot_capturesWhenPossible() {
        val grid = MutableList(9 * 9) { 0 }
        grid[1 * 9 + 1] = Stone.WHITE.code
        grid[0 * 9 + 1] = Stone.BLACK.code
        grid[1 * 9 + 0] = Stone.BLACK.code
        grid[2 * 9 + 1] = Stone.BLACK.code
        val state = BoardState(gameType = BoardGameType.GO, size = 9, grid = grid.toList(), current = Stone.BLACK)
        val move = BoardBot.nextMove(state, Random(1))
        assertNotNull(move)
        assertEquals(1 to 2, move.row to move.col)
    }

    @Test
    fun goBot_avoidsSuicide() {
        val grid = MutableList(9 * 9) { 0 }
        grid[0 * 9 + 1] = Stone.WHITE.code
        grid[1 * 9 + 0] = Stone.WHITE.code
        val state = BoardState(gameType = BoardGameType.GO, size = 9, grid = grid.toList(), current = Stone.BLACK)
        val move = BoardBot.nextMove(state, Random(1))
        assertNotNull(move)
        assertIs<MoveOutcome.Played>(state.applyMove(move))
    }

    @Test
    fun goBot_returnsNullWhenBoardFull() {
        val grid = MutableList(9 * 9) { 0 }
        for (r in 0 until 9) for (c in 0 until 9) {
            grid[r * 9 + c] = if ((r + c) % 2 == 0) Stone.BLACK.code else Stone.WHITE.code
        }
        val state = BoardState(gameType = BoardGameType.GO, size = 9, grid = grid.toList(), current = Stone.BLACK)
        assertNull(BoardBot.nextMove(state, Random(1)))
    }
}
