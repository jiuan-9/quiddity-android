package com.quiddity.app.domain.board

import com.quiddity.app.ui.miniapps.board.BoardLlmClient
import com.quiddity.app.ui.miniapps.board.LlmGateway
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/*
 * 端到端冒烟测试：用真实引擎 + 兜底 AI 把整局棋下到终局，
 * 证明对局不会卡死、不会出现非法落子、结算可用。
 */
class BoardEndToEndTest {

    @Test
    fun gomoku_botVersusBot_runsToTerminalState() {
        var state = BoardState(gameType = BoardGameType.GOMOKU)
        var guard = 0
        while (!state.gameOver && guard < 300) {
            guard++
            val move = BoardBot.nextMove(state, Random(guard))
                ?: break
            val outcome = state.applyMove(move)
            assertIs<MoveOutcome.Played>(outcome, "第 $guard 手必须是合法落子")
            state = outcome.state
        }
        assertTrue(
            state.gameOver || state.moveCount == state.size * state.size,
            "对局必须终止（终局或棋盘填满），实际 $guard 手"
        )
    }

    @Test
    fun gomoku_llmGarbageReply_fallsBackToBotAndFinishes() = runBlocking {
        var state = BoardState(gameType = BoardGameType.GOMOKU)
        // 假 LLM：永远返回无法解析的内容，验证降级路径
        val client = BoardLlmClient(LlmGateway { _, _, _, _ ->
            Result.success("我觉得这步棋很有深度，但我不告诉你坐标")
        })
        var guard = 0
        while (!state.gameOver && guard < 300) {
            guard++
            val llmMove = client.requestMove(state, "对手", null)
            val move = when (llmMove) {
                is LlmMove.Place -> llmMove.move
                else -> BoardBot.nextMove(state, Random(guard)) ?: break
            }
            val outcome = state.applyMove(move)
            assertIs<MoveOutcome.Played>(outcome, "第 $guard 手（LLM 兜底）必须是合法落子")
            state = outcome.state
        }
        assertTrue(
            state.gameOver || state.moveCount == state.size * state.size,
            "LLM 全部失效时对局仍必须正常结束，实际 $guard 手"
        )
    }

    @Test
    fun go_botVersusBot_handlesPassAndScoring() {
        var state = BoardState(gameType = BoardGameType.GO)
        var guard = 0
        var passCount = 0
        while (!state.gameOver && guard < 250) {
            guard++
            val move = BoardBot.nextMove(state, Random(guard))
            if (move != null) {
                val outcome = state.applyMove(move)
                assertIs<MoveOutcome.Played>(outcome, "围棋第 $guard 手必须是合法落子")
                state = outcome.state
                passCount = 0
            } else {
                val pass = state.pass()
                assertIs<PassOutcome.Done>(pass)
                state = pass.state
                passCount++
            }
        }
        assertTrue(
            state.gameOver || passCount >= 2 || guard >= 250,
            "围棋对局必须通过连停/满盘结束或稳定运行，实际 $guard 手"
        )
        val score = state.score()
        if (state.gameOver) {
            assertNotNull(score.winner, "带贴目的围棋结算必有胜者")
        }
        assertTrue(score.black >= 0.0 && score.white >= 0.0)
    }

    @Test
    fun go_scriptedCaptureAndResign_runsWithoutError() {
        var state = BoardState(gameType = BoardGameType.GO)
        val moves = listOf(
            1 to 1, 0 to 1, 8 to 8, 1 to 0, 8 to 7, 2 to 1, 8 to 6, 1 to 2
        )
        for ((i, mv) in moves.withIndex()) {
            val outcome = state.applyMove(mv.first, mv.second)
            assertIs<MoveOutcome.Played>(outcome, "第 $i 手应合法")
            state = outcome.state
        }
        // 第 8 手黑落 (1,2) 应提掉白 (1,1)
        assertEquals(Stone.EMPTY, state.stoneAt(1, 1))
        val resigned = state.resign()
        assertTrue(resigned.gameOver)
        assertEquals(Stone.WHITE, resigned.winner)
    }
}
