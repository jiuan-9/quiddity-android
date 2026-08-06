package com.quiddity.app.domain.board

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoardLlmPromptTest {

    @Test
    fun parseMoveReply_acceptsCanonicalMove() {
        val parsed = BoardLlmPrompt.parseMoveReply("MOVE(3,4)")
        assertIs<LlmMove.Place>(parsed)
        assertEquals(Move(3, 4), parsed.move)
    }

    @Test
    fun parseMoveReply_acceptsWhitespaceAndChineseParens() {
        val parsed = BoardLlmPrompt.parseMoveReply("我选择落子：MOVE（7， 7）")
        assertIs<LlmMove.Place>(parsed)
        assertEquals(Move(7, 7), parsed.move)
    }

    @Test
    fun parseMoveReply_acceptsPass() {
        val parsed = BoardLlmPrompt.parseMoveReply("PASS")
        assertIs<LlmMove.Pass>(parsed)
    }

    @Test
    fun parseMoveReply_acceptsChinesePassWord() {
        val parsed = BoardLlmPrompt.parseMoveReply("停一手")
        assertIs<LlmMove.Pass>(parsed)
    }

    @Test
    fun parseMoveReply_rejectsGarbage() {
        assertNull(BoardLlmPrompt.parseMoveReply("你好呀"))
        assertNull(BoardLlmPrompt.parseMoveReply(""))
    }

    @Test
    fun parseMoveReply_ignoresTrailingExplanations() {
        val parsed = BoardLlmPrompt.parseMoveReply("MOVE(1,1) 这是因为我看到了好机会")
        assertIs<LlmMove.Place>(parsed)
        assertEquals(Move(1, 1), parsed.move)
    }

    @Test
    fun buildMoveSystemMessage_containsPersonaAndFormat() {
        val state = BoardState(gameType = BoardGameType.GO)
        val msg = BoardLlmPrompt.buildMoveSystemMessage(state, "棋圣", "你性格沉稳")
        assertTrue(msg.contains("棋圣"))
        assertTrue(msg.contains("你性格沉稳"))
        assertTrue(msg.contains("MOVE"))
        assertTrue(msg.contains("PASS"))
    }

    @Test
    fun buildMoveUserMessage_containsTurnAndStones() {
        var state = BoardState(gameType = BoardGameType.GOMOKU)
        val b = state.applyMove(7, 7)
        assertIs<MoveOutcome.Played>(b)
        val w = b.state.applyMove(3, 3)
        assertIs<MoveOutcome.Played>(w)
        state = w.state
        val msg = BoardLlmPrompt.buildMoveUserMessage(state)
        assertTrue(msg.contains("黑"))
        assertTrue(msg.contains("(7,7)"))
        assertTrue(msg.contains("(3,3)"))
    }

    @Test
    fun buildChatSystemMessage_containsPersonaAndGame() {
        val state = BoardState(gameType = BoardGameType.GOMOKU)
        val msg = BoardLlmPrompt.buildChatSystemMessage(state, "小林", "你是一名棋手")
        assertTrue(msg.contains("小林"))
        assertTrue(msg.contains("你是一名棋手"))
        assertTrue(msg.contains("五子棋"))
    }

    @Test
    fun buildChatUserMessage_containsUserText() {
        val state = BoardState(gameType = BoardGameType.GO)
        val msg = BoardLlmPrompt.buildChatUserMessage("你这步下得不错", state)
        assertTrue(msg.contains("你这步下得不错"))
        assertNotNull(msg)
    }
}
