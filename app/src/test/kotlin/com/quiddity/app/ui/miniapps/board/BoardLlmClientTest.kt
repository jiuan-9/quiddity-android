package com.quiddity.app.ui.miniapps.board

import com.quiddity.app.domain.board.BoardGameType
import com.quiddity.app.domain.board.BoardState
import com.quiddity.app.domain.board.GameChatTurn
import com.quiddity.app.domain.board.LlmMove
import com.quiddity.app.domain.board.Move
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun requestMove_passesChatHistoryIntoUserContent() {
        var capturedUser = ""
        val client = BoardLlmClient(LlmGateway { _, user, _, _ ->
            capturedUser = user
            Result.success("MOVE(1,1)")
        })
        val history = listOf(GameChatTurn(fromUser = true, text = "饶我一命"))
        runBlocking { client.requestMove(state, "棋圣", null, history) }
        assertTrue(capturedUser.contains("饶我一命"), "落子请求应包含对局聊天记录")
        assertTrue(capturedUser.contains("本局聊天记录"))
    }

    @Test
    fun chatReply_passesChatHistoryIntoSystemContent() {
        var capturedSystem = ""
        val client = BoardLlmClient(LlmGateway { system, _, _, _ ->
            capturedSystem = system
            Result.success("好的")
        })
        val history = listOf(GameChatTurn(fromUser = false, text = "那我们说好了"))
        runBlocking { client.chatReply(state, "棋圣", null, "嗯", history) }
        assertTrue(capturedSystem.contains("那我们说好了"), "聊天回复应携带对局聊天记录")
    }
}
