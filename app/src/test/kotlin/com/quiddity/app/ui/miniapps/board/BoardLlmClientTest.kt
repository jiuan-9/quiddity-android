package com.quiddity.app.ui.miniapps.board

import com.quiddity.app.domain.board.BoardGameType
import com.quiddity.app.domain.board.BoardState
import com.quiddity.app.domain.board.LlmMove
import com.quiddity.app.domain.board.Move
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class BoardLlmClientTest {

    private val state = BoardState(gameType = BoardGameType.GOMOKU)

    @Test
    fun requestMove_parsesValidMoveReply() {
        val client = BoardLlmClient(LlmGateway { _, _, _, _ ->
            Result.success("MOVE(2,3)")
        })
        val move = runBlocking { client.requestMove(state, "棋圣", null) }
        assertIs<LlmMove.Place>(move)
        assertEquals(Move(2, 3), move.move)
    }

    @Test
    fun requestMove_returnsNullOnGatewayFailure() {
        val client = BoardLlmClient(LlmGateway { _, _, _, _ ->
            Result.failure(RuntimeException("boom"))
        })
        assertNull(runBlocking { client.requestMove(state, "棋圣", null) })
    }

    @Test
    fun requestMove_returnsNullOnUnparseableReply() {
        val client = BoardLlmClient(LlmGateway { _, _, _, _ ->
            Result.success("我觉得这里不错")
        })
        assertNull(runBlocking { client.requestMove(state, "棋圣", null) })
    }

    @Test
    fun requestMove_acceptsPassForGo() {
        val goState = BoardState(gameType = BoardGameType.GO)
        val client = BoardLlmClient(LlmGateway { _, _, _, _ ->
            Result.success("PASS")
        })
        val move = runBlocking { client.requestMove(goState, "棋圣", null) }
        assertIs<LlmMove.Pass>(move)
    }

    @Test
    fun chatReply_returnsText() {
        val client = BoardLlmClient(LlmGateway { _, _, _, _ ->
            Result.success("这一步很有魄力")
        })
        assertEquals("这一步很有魄力", runBlocking { client.chatReply(state, "棋圣", null, "这步怎么样？") })
    }

    @Test
    fun chatReply_returnsNullOnFailure() {
        val client = BoardLlmClient(LlmGateway { _, _, _, _ ->
            Result.failure(RuntimeException("boom"))
        })
        assertNull(runBlocking { client.chatReply(state, "棋圣", null, "在吗") })
    }
}
