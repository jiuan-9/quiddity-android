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
    fun buildMoveUserMessage_containsChatHistory() {
        val state = BoardState(gameType = BoardGameType.GOMOKU)
        val history = listOf(
            GameChatTurn(fromUser = true, text = "饶我一命吧"),
            GameChatTurn(fromUser = false, text = "看你这盘表现")
        )
        val msg = BoardLlmPrompt.buildMoveUserMessage(state, history)
        assertTrue(msg.contains("本局聊天记录"))
        assertTrue(msg.contains("饶我一命吧"))
        assertTrue(msg.contains("看你这盘表现"))
        assertTrue(msg.contains("手下留情"))
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
    fun buildChatSystemMessage_containsBoardStones() {
        var state = BoardState(gameType = BoardGameType.GOMOKU)
        val b = state.applyMove(7, 7)
        assertIs<MoveOutcome.Played>(b)
        val w = b.state.applyMove(3, 3)
        assertIs<MoveOutcome.Played>(w)
        state = w.state
        val msg = BoardLlmPrompt.buildChatSystemMessage(state, "小林", null)
        assertTrue(msg.contains("(7,7)"))
        assertTrue(msg.contains("(3,3)"))
    }

    @Test
    fun buildChatSystemMessage_containsChatHistory() {
        val state = BoardState(gameType = BoardGameType.GOMOKU)
        val history = listOf(
            GameChatTurn(fromUser = true, text = "你这步太狠了"),
            GameChatTurn(fromUser = false, text = "那就温柔一点")
        )
        val msg = BoardLlmPrompt.buildChatSystemMessage(state, "小林", null, history)
        assertTrue(msg.contains("本局聊天记录"))
        assertTrue(msg.contains("你这步太狠了"))
        assertTrue(msg.contains("那就温柔一点"))
    }

    @Test
    fun buildChatUserMessage_containsUserText() {
        val state = BoardState(gameType = BoardGameType.GO)
        val msg = BoardLlmPrompt.buildChatUserMessage("你这步下得不错", state)
        assertTrue(msg.contains("你这步下得不错"))
        assertNotNull(msg)
    }

    @Test
    fun parseMoveCommitment_acceptsMoveProtocol() {
        val parsed = BoardLlmPrompt.parseMoveCommitment("好的，我下到 MOVE(1,1)", 15)
        assertIs<MoveCommitmentParse.Place>(parsed)
        assertEquals(Move(1, 1), parsed.move)
    }

    @Test
    fun parseMoveCommitment_acceptsNaturalChineseCommitment() {
        val parsed = BoardLlmPrompt.parseMoveCommitment("没问题，我下到（2，3）", 15)
        assertIs<MoveCommitmentParse.Place>(parsed)
        assertEquals(Move(2, 3), parsed.move)
    }

    @Test
    fun parseMoveCommitment_usesLatestMention() {
        val parsed = BoardLlmPrompt.parseMoveCommitment("先别管(4,4)，我们下到(5,5)", 15)
        assertIs<MoveCommitmentParse.Place>(parsed)
        assertEquals(Move(5, 5), parsed.move)
    }

    @Test
    fun parseMoveCommitment_detectsNegation() {
        val parsed = BoardLlmPrompt.parseMoveCommitment("还是别下(1,1)了", 15)
        assertIs<MoveCommitmentParse.Cancelled>(parsed)
        assertEquals(MoveCommitmentParse.Cancelled, parsed)
    }

    @Test
    fun parseMoveCommitment_detectsCannotCommit() {
        val parsed = BoardLlmPrompt.parseMoveCommitment("我不能下到(1,1)，那里会被吃掉", 15)
        assertIs<MoveCommitmentParse.Cancelled>(parsed)
    }

    @Test
    fun parseMoveCommitment_returnsNoneForOrdinaryChat() {
        val parsed = BoardLlmPrompt.parseMoveCommitment("这盘棋有意思", 15)
        assertIs<MoveCommitmentParse.None>(parsed)
    }

    @Test
    fun parseMoveCommitment_rejectsOutOfRange() {
        val parsed = BoardLlmPrompt.parseMoveCommitment("我下到(99,99)", 15)
        assertIs<MoveCommitmentParse.None>(parsed)
    }
}
