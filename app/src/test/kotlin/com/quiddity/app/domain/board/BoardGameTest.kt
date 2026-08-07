package com.quiddity.app.domain.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoardGameTest {

    private fun gomoku() = BoardState(gameType = BoardGameType.GOMOKU)
    private fun go() = BoardState(gameType = BoardGameType.GO)

    private fun farMoves(state: BoardState, count: Int): List<Pair<Int, Int>> {
        // 返回远离棋盘中心的合法空位，用于让对手"过手"
        val size = state.size
        val moves = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until size) {
            for (c in 0 until size) {
                val far = (r + c) >= size - 2 || (r + c) <= 2
                if (far && state.stoneAt(r, c) == Stone.EMPTY && moves.size < count) {
                    moves += r to c
                }
            }
        }
        return moves
    }

    @Test
    fun gomoku_detectsFiveInRowHorizontal() {
        var state = gomoku()
        val blacks = listOf(7 to 3, 7 to 4, 7 to 5, 7 to 6, 7 to 7)
        val whiteMoves = farMoves(state, 4).toMutableList()
        for ((index, mv) in blacks.withIndex()) {
            val r = state.applyMove(mv.first, mv.second)
            assertIs<MoveOutcome.Played>(r)
            state = r.state
            if (index < blacks.lastIndex) {
                val (wr, wc) = whiteMoves.removeAt(0)
                val wr2 = state.applyMove(wr, wc)
                assertIs<MoveOutcome.Played>(wr2)
                state = wr2.state
            } else {
                assertEquals(Stone.BLACK, r.winner)
                assertTrue(r.gameOver)
                assertTrue(r.state.gameOver)
            }
        }
    }

    @Test
    fun gomoku_fiveWithGapIsNotWin() {
        var state = gomoku()
        // 黑子 (7,3)(7,4)(7,6)(7,7) 中间隔一空位，不成五
        val blacks = listOf(7 to 3, 7 to 4, 7 to 6, 7 to 7)
        val whiteMoves = farMoves(state, blacks.size).toMutableList()
        for (mv in blacks) {
            val r = state.applyMove(mv.first, mv.second)
            assertIs<MoveOutcome.Played>(r)
            assertNull(r.winner)
            val (wr, wc) = whiteMoves.removeAt(0)
            val wr2 = state.applyMove(wr, wc)
            assertIs<MoveOutcome.Played>(wr2)
            state = wr2.state
        }
        assertNull(state.winner)
        assertTrue(!state.gameOver)
    }

    @Test
    fun gomoku_occupiedCellIsRejected() {
        val state = gomoku()
        val first = state.applyMove(7, 7)
        assertIs<MoveOutcome.Played>(first)
        val second = first.state.applyMove(7, 7)
        assertIs<MoveOutcome.Rejected>(second)
    }

    @Test
    fun gomoku_passSwitchesTurn() {
        val state = gomoku()
        val r = state.pass()
        assertIs<PassOutcome.Done>(r)
        assertEquals(Stone.WHITE, r.state.current)
        assertTrue(!r.gameOver)
    }

    @Test
    fun go_capturesSingleStoneWhenLastLibertyFilled() {
        var state = go()
        // 白(1,1)；黑(0,1)(1,0)(2,1)；白远位过手；黑落(1,2)提子
        val whiteFirst = state.applyMove(1, 1)
        assertIs<MoveOutcome.Played>(whiteFirst)
        state = whiteFirst.state

        val sequence = listOf(
            0 to 1 to Stone.BLACK,
            8 to 8 to Stone.WHITE,
            1 to 0 to Stone.BLACK,
            8 to 7 to Stone.WHITE,
            2 to 1 to Stone.BLACK,
            8 to 6 to Stone.WHITE
        )
        for ((mv, expected) in sequence) {
            val r = state.applyMove(mv.first, mv.second)
            assertIs<MoveOutcome.Played>(r)
            state = r.state
        }

        val capturing = state.applyMove(1, 2)
        assertIs<MoveOutcome.Played>(capturing)
        assertEquals(1, capturing.captured)
        assertEquals(Stone.EMPTY, capturing.state.stoneAt(1, 1))
    }

    @Test
    fun go_suicideIsRejected() {
        var state = go()
        val w1 = state.applyMove(0, 1)
        assertIs<MoveOutcome.Played>(w1)
        state = w1.state
        val b1 = state.applyMove(8, 8)
        assertIs<MoveOutcome.Played>(b1)
        state = b1.state
        val w2 = state.applyMove(1, 0)
        assertIs<MoveOutcome.Played>(w2)
        state = w2.state
        val b2 = state.applyMove(8, 7)
        assertIs<MoveOutcome.Played>(b2)
        state = b2.state
        val w3 = state.applyMove(8, 6)
        assertIs<MoveOutcome.Played>(w3)
        state = w3.state

        val suicide = state.applyMove(0, 0)
        assertIs<MoveOutcome.Rejected>(suicide)
    }

    @Test
    fun go_rejectsPositionReplay() {
        val base = go()
        val played = base.applyMove(3, 3)
        assertIs<MoveOutcome.Played>(played)
        // 构造：历史中包含"黑落(3,3)后"的指纹 → 再次形成该局面应判 ko
        val stateWithHistory = base.copy(history = listOf(played.state.fingerprint()))
        val replay = stateWithHistory.applyMove(3, 3)
        assertIs<MoveOutcome.Ko>(replay)
    }

    @Test
    fun go_twoPassesEndsGame() {
        var state = go()
        val p1 = state.pass()
        assertIs<PassOutcome.Done>(p1)
        assertTrue(!p1.gameOver)
        val p2 = p1.state.pass()
        assertIs<PassOutcome.Done>(p2)
        assertTrue(p2.gameOver)
        assertEquals(Stone.WHITE, p2.winner) // 双方都未落子，白 7.5 贴目 → 白胜
    }

    @Test
    fun go_simpleAreaScoringCountsEnclosedTerritory() {
        // 黑子围成 3x3 空心环，中央 (4,4) 为黑空
        val grid = MutableList(81) { 0 }
        val ring = listOf(
            3 to 3, 3 to 4, 3 to 5,
            4 to 3, 4 to 5,
            5 to 3, 5 to 4, 5 to 5
        )
        for ((r, c) in ring) grid[r * 9 + c] = Stone.BLACK.code
        // 远处放几颗白子，让外圈大空域成为公海，不计入任何一方
        for ((r, c) in listOf(0 to 0, 0 to 1, 1 to 0)) grid[r * 9 + c] = Stone.WHITE.code
        val state = BoardState(gameType = BoardGameType.GO, grid = grid.toList(), current = Stone.WHITE, moveCount = 11)
        val score = state.score()
        assertEquals(9.0, score.black)
        assertEquals(10.5, score.white)
        assertEquals(Stone.WHITE, score.winner)
    }

    @Test
    fun go_resignDeclaresOpponentWinner() {
        val state = go()
        val resigned = state.resign()
        assertTrue(resigned.gameOver)
        assertEquals(Stone.WHITE, resigned.winner)
        assertEquals("认输", resigned.endReason)
    }

    @Test
    fun go_capturesMultipleStonesInOneMove() {
        val grid = MutableList(81) { 0 }
        for ((r, c) in listOf(0 to 0, 0 to 1, 1 to 0)) grid[r * 9 + c] = Stone.WHITE.code
        for ((r, c) in listOf(0 to 2, 2 to 0)) grid[r * 9 + c] = Stone.BLACK.code
        val state = BoardState(
            gameType = BoardGameType.GO,
            grid = grid.toList(),
            current = Stone.BLACK
        )
        val result = state.applyMove(1, 1)
        assertIs<MoveOutcome.Played>(result)
        assertEquals(3, result.captured)
        assertEquals(Stone.EMPTY, result.state.stoneAt(0, 0))
        assertEquals(Stone.EMPTY, result.state.stoneAt(0, 1))
        assertEquals(Stone.EMPTY, result.state.stoneAt(1, 0))
    }

    @Test
    fun go_moveAfterPassResetsPassCounter() {
        var state = go()
        val p1 = state.pass()
        assertIs<PassOutcome.Done>(p1)
        assertEquals(1, p1.state.consecutivePasses)
        val move = p1.state.applyMove(4, 4)
        assertIs<MoveOutcome.Played>(move)
        assertEquals(0, move.state.consecutivePasses)
        val p2 = move.state.pass()
        assertIs<PassOutcome.Done>(p2)
        assertTrue(!p2.gameOver)
    }

    @Test
    fun go_scoreEqualAreaPrefersWhiteByKomi() {
        val grid = MutableList(81) { 0 }
        for ((r, c) in listOf(0 to 0, 0 to 1, 1 to 0, 1 to 1)) grid[r * 9 + c] = Stone.BLACK.code
        for ((r, c) in listOf(6 to 6, 6 to 7, 7 to 6, 7 to 7)) grid[r * 9 + c] = Stone.WHITE.code
        val state = BoardState(gameType = BoardGameType.GO, grid = grid.toList(), current = Stone.WHITE)
        val score = state.score()
        assertEquals(4.0, score.black)
        assertEquals(11.5, score.white)
        assertEquals(Stone.WHITE, score.winner)
    }

    @Test
    fun go_applyMoveWhenGameOverIsRejected() {
        val state = go()
        val resigned = state.resign()
        val result = resigned.applyMove(0, 0)
        assertIs<MoveOutcome.Rejected>(result)
    }

    @Test
    fun stone_opponentFlips() {
        assertEquals(Stone.WHITE, Stone.BLACK.opponent())
        assertEquals(Stone.BLACK, Stone.WHITE.opponent())
        assertNotEquals(Stone.BLACK, Stone.EMPTY)
    }
}
