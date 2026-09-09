package com.quiddity.app.domain.spy

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpyGameTest {

    private fun players(count: Int): List<SpyPlayer> =
        List(count) { i ->
            SpyPlayer(index = i, name = "玩家$i", kind = if (i == 0) SpyPlayerKind.USER else SpyPlayerKind.SCRIPT)
        }

    private fun dealt(count: Int = 4): SpyGameState {
        return dealt(count, SpyGameMode.CLASSIC)
    }

    private fun dealt(count: Int = 4, mode: SpyGameMode): SpyGameState {
        val result = SpyGame.deal(players(count), SpyWordBank.pairs.first(), Random(42), mode)
        require(result is SpyDeal.Done)
        return result.state
    }

    /** 所有存活玩家依次发言，返回进入投票阶段的局面。 */
    private fun allSpoke(state: SpyGameState): SpyGameState {
        var s = SpyGame.startSpeaking(state)
        for (p in s.alivePlayers) {
            val outcome = SpyGame.speak(s, p.index, "第 ${p.index} 号发言")
            assertIs<SpySpeak.Done>(outcome)
            s = outcome.state
        }
        assertEquals(SpyPhase.VOTE, s.phase)
        return s
    }

    /** 轮转投票：每位玩家都投 targetFor(自己)，若目标是自己则顺延投下一个存活者。 */
    private fun roundVote(state: SpyGameState, targetFor: (Int) -> Int): SpyGameState {
        var s = state
        for (voter in s.alivePlayers) {
            var target = targetFor(voter.index)
            if (target == voter.index) {
                target = s.alivePlayers.first { it.index != voter.index }.index
            }
            val outcome = SpyGame.vote(s, voter.index, target)
            assertIs<SpyVoteOutcome.Done>(outcome)
            s = outcome.state
        }
        return s
    }

    /** 构造第 1 轮 2:2 平票、进入 PK 的局面（0/1 平票，2/3 为 PK 投票人）。 */
    private fun tiedState(): SpyGameState {
        var state = allSpoke(dealt(4))
        val mapping = mapOf(0 to 1, 1 to 0, 2 to 1, 3 to 0)
        var outcome: SpyVoteOutcome.Done? = null
        for (voter in state.players) {
            outcome = SpyGame.vote(state, voter.index, mapping.getValue(voter.index)) as SpyVoteOutcome.Done
            state = outcome.state
        }
        assertNotNull(outcome)
        assertEquals(SpyPhase.PK, state.phase)
        return state
    }

    /** 平票者全部补完 PK 发言。 */
    private fun pkAllSpoke(state: SpyGameState): SpyGameState {
        var s = state
        for (candidate in s.pkCandidates) {
            val outcome = SpyGame.pkSpeak(s, candidate, "候选人 $candidate 辩护")
            assertIs<SpyPkSpeak.Done>(outcome)
            s = outcome.state
        }
        assertTrue(s.pkSpeakComplete)
        return s
    }

    @Test
    fun deal_assignsOneSpyAndWords() {
        val state = dealt(4)
        assertEquals(1, state.players.count { it.role == SpyRole.SPY })
        val pair = state.wordPair!!
        state.players.forEach { p ->
            assertNotNull(p.word)
            assertEquals(if (p.role == SpyRole.SPY) pair.spy else pair.civilian, p.word)
        }
    }

    @Test
    fun deal_doubleSpyAssignsTwoSpiesWithSameWord() {
        val state = dealt(4, SpyGameMode.DOUBLE_SPY)
        assertEquals(2, state.spyIndices.size)
        val spyWords = state.spyIndices.map { state.player(it)!!.word }
        assertEquals(1, spyWords.toSet().size)
        assertEquals(SpyWordBank.pairs.first().spy, spyWords.first())
    }

    @Test
    fun deal_blankAssignsSpyAndBlankWithoutWord() {
        val state = dealt(4, SpyGameMode.BLANK)
        assertEquals(1, state.spyIndices.size)
        assertEquals(1, state.players.count { it.role == SpyRole.BLANK })
        assertEquals("", state.player(state.blankIndex!!)!!.word)
    }

    @Test
    fun deal_roundOrderCoversAllPlayers() {
        val state = dealt(4)
        assertEquals(listOf(0, 1, 2, 3), state.roundOrder.sorted())
    }

    @Test
    fun roundOrder_drivesSpeakingSequence() {
        val state = SpyGame.startSpeaking(dealt(4))
        val expected = state.roundOrder.filter { idx -> state.players[idx].alive }
        var finalState = state
        val actual = buildList {
            var s = state
            while (true) {
                val next = s.nextSpeaker() ?: break
                add(next.index)
                val outcome = SpyGame.speak(s, next.index, "发言")
                assertIs<SpySpeak.Done>(outcome)
                s = outcome.state
            }
            finalState = s
        }
        assertEquals(expected, actual)
        assertNull(finalState.nextSpeaker())
    }

    @Test
    fun nextRound_shufflesOrderForNewRound() {
        val state0 = allSpoke(dealt(4))
        val spy = state0.spyIndex!!
        val civilian = state0.players.first { it.index != spy }.index
        val state = roundVote(state0) { civilian }
        assertEquals(2, state.round)
        assertEquals(3, state.roundOrder.size)
        assertEquals(state.alivePlayers.map { it.index }.toSet(), state.roundOrder.toSet())
    }

    @Test
    fun speak_advancesToVoteAfterAllAliveSpoke() {
        val state = allSpoke(dealt(4))
        assertNull(state.nextSpeaker())
    }

    @Test
    fun speak_duplicateIsRejected() {
        val started = SpyGame.startSpeaking(dealt(3))
        val first = SpyGame.speak(started, 0, "第一次")
        assertIs<SpySpeak.Done>(first)
        val dup = SpyGame.speak(first.state, 0, "第二次")
        assertIs<SpySpeak.Rejected>(dup)
    }

    @Test
    fun vote_civilianEliminatedContinuesToNextRound() {
        val state0 = allSpoke(dealt(4))
        val spy = state0.spyIndex!!
        val civilian = state0.players.first { it.index != spy }.index
        val state = roundVote(state0) { civilian }
        assertEquals(SpyPhase.SPEAK, state.phase)
        assertEquals(2, state.round)
        assertTrue(state.player(civilian)?.alive == false)
    }

    @Test
    fun vote_spyEliminatedEndsWithCivilianWin() {
        val state0 = allSpoke(dealt(4))
        val spy = state0.spyIndex!!
        val state = roundVote(state0) { spy }
        assertEquals(SpyRole.CIVILIAN, state.winner)
        assertEquals(SpyPhase.FINISHED, state.phase)
    }

    @Test
    fun vote_tieEntersPkPhase() {
        val state = tiedState()
        assertEquals(listOf(0, 1), state.pkCandidates)
        assertEquals(0, state.nextPkSpeaker()?.index)
        assertNull(state.nextPkVoter())
        assertTrue(state.players.all { it.alive })
    }

    @Test
    fun pkSpeak_allCandidatesSpeakThenVotingOpens() {
        val state = pkAllSpoke(tiedState())
        assertNull(state.nextPkSpeaker())
        assertEquals(2, state.nextPkVoter()?.index)
    }

    @Test
    fun pkSpeak_nonCandidateOrDuplicateIsRejected() {
        var state = tiedState()
        assertIs<SpyPkSpeak.Rejected>(SpyGame.pkSpeak(state, 2, "我不是平票者"))
        state = (SpyGame.pkSpeak(state, 0, "第一次") as SpyPkSpeak.Done).state
        assertIs<SpyPkSpeak.Rejected>(SpyGame.pkSpeak(state, 0, "第二次"))
    }

    @Test
    fun pkVote_eliminatesCandidateAndContinuesOrEnds() {
        val spy = tiedState().spyIndex
        var state = pkAllSpoke(tiedState())
        var outcome: SpyPkVoteOutcome.Done? = null
        for (voter in listOf(2, 3)) {
            outcome = SpyGame.pkVote(state, voter, 0) as SpyPkVoteOutcome.Done
            state = outcome.state
        }
        assertNotNull(outcome)
        assertEquals(0, outcome!!.eliminatedIndex)
        if (spy == 0) {
            assertEquals(SpyRole.CIVILIAN, state.winner)
            assertEquals(SpyPhase.FINISHED, state.phase)
        } else {
            assertTrue(state.player(0)?.alive == false)
            assertEquals(2, state.round)
            assertEquals(SpyPhase.SPEAK, state.phase)
        }
    }

    @Test
    fun pkVote_tieLeadsToNextRoundNoElimination() {
        var state = pkAllSpoke(tiedState())
        state = (SpyGame.pkVote(state, 2, 0) as SpyPkVoteOutcome.Done).state
        val outcome = SpyGame.pkVote(state, 3, 1) as SpyPkVoteOutcome.Done
        assertTrue(outcome.tie)
        assertEquals(2, outcome.state.round)
        assertEquals(SpyPhase.SPEAK, outcome.state.phase)
        assertTrue(outcome.state.players.all { it.alive })
    }

    @Test
    fun vote_cannotTargetSelf() {
        val state = SpyGame.startSpeaking(dealt(3))
        assertIs<SpyVoteOutcome.Rejected>(SpyGame.vote(state, 0, 0))
    }

    @Test
    fun vote_targetEliminatedIsRejected() {
        val state = SpyGame.startSpeaking(dealt(3))
        assertIs<SpyVoteOutcome.Rejected>(SpyGame.vote(state, 0, 99))
    }

    @Test
    fun spyWinsWhenPlayerCountDropsToTwo() {
        val pair = dealt(3)
        val state0 = allSpoke(pair)
        val spy = state0.spyIndex!!
        val civilian = state0.players.first { it.index != spy }.index
        val state = roundVote(state0) { civilian }
        assertEquals(SpyRole.SPY, state.winner)
        assertEquals(SpyPhase.FINISHED, state.phase)
    }

    @Test
    fun doubleSpy_civilianWinRequiresBothSpiesOut() {
        var state0 = allSpoke(dealt(4, SpyGameMode.DOUBLE_SPY))
        val spies = state0.spyIndices
        var state = roundVote(state0) { spies[0] }
        assertNull(state.winner)
        assertEquals(SpyPhase.SPEAK, state.phase)
        val state1 = allSpoke(state)
        state = roundVote(state1) { spies[1] }
        assertEquals(SpyRole.CIVILIAN, state.winner)
        assertEquals(SpyPhase.FINISHED, state.phase)
    }

    @Test
    fun doubleSpy_spySideWinsWhenOutnumberCivilians() {
        val state0 = allSpoke(dealt(4, SpyGameMode.DOUBLE_SPY))
        val civilians = state0.players.filter { it.role == SpyRole.CIVILIAN }.map { it.index }
        val state = roundVote(state0) { civilians[0] }
        assertEquals(SpyRole.SPY, state.winner)
        assertEquals(SpyPhase.FINISHED, state.phase)
    }

    @Test
    fun blank_civilianWinRequiresSpyAndBlankOut() {
        var state0 = allSpoke(dealt(4, SpyGameMode.BLANK))
        val spy = state0.spyIndex!!
        val blank = state0.blankIndex!!
        var state = roundVote(state0) { blank }
        assertNull(state.winner)
        val state1 = allSpoke(state)
        state = roundVote(state1) { spy }
        assertEquals(SpyRole.CIVILIAN, state.winner)
        assertEquals(SpyPhase.FINISHED, state.phase)
    }

    @Test
    fun blank_spySideWinsWhenOutnumberCivilians() {
        val state0 = allSpoke(dealt(4, SpyGameMode.BLANK))
        val civilians = state0.players.filter { it.role == SpyRole.CIVILIAN }.map { it.index }
        val state = roundVote(state0) { civilians[0] }
        assertEquals(SpyRole.SPY, state.winner)
        assertEquals(SpyPhase.FINISHED, state.phase)
    }
}
