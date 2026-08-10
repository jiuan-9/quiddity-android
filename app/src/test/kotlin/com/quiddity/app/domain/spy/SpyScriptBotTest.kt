package com.quiddity.app.domain.spy

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SpyScriptBotTest {

    private fun dealt(count: Int = 4): SpyGameState {
        val result = SpyGame.deal(
            List(count) { i -> SpyPlayer(i, "P$i", SpyPlayerKind.SCRIPT) },
            SpyWordBank.pairs.first(),
            Random(7)
        )
        require(result is SpyDeal.Done)
        return result.state
    }

    @Test
    fun speak_civilianUsesWordHintAndNeverLeaksWord() {
        val state = dealt()
        val civilian = state.players.first { it.role == SpyRole.CIVILIAN }
        val text = SpyScriptBot.speak(state, civilian.index, Random(7))
        assertFalse(text.isBlank())
        assertFalse(text.contains(civilian.word!!))
    }

    @Test
    fun speak_spyUsesVagueTemplate() {
        val state = dealt()
        val spy = state.players.first { it.role == SpyRole.SPY }
        val text = SpyScriptBot.speak(state, spy.index, Random(0))
        assertFalse(text.isBlank())
        assertFalse(text.contains(spy.word!!))
    }

    @Test
    fun speak_blankPretendsWithoutLeaking() {
        val result = SpyGame.deal(
            List(4) { i -> SpyPlayer(i, "P$i", SpyPlayerKind.SCRIPT) },
            SpyWordBank.pairs.first(),
            Random(7),
            SpyGameMode.BLANK
        )
        require(result is SpyDeal.Done)
        val state = result.state
        val blank = state.player(state.blankIndex!!)!!
        val text = SpyScriptBot.speak(state, blank.index, Random(0))
        assertFalse(text.isBlank())
        assertFalse(text.contains("白板"))
        assertFalse(text.contains("没词"))
    }

    @Test
    fun speak_pkReturnsDefenseText() {
        val result = SpyGame.deal(
            List(4) { i -> SpyPlayer(i, "P$i", SpyPlayerKind.SCRIPT) },
            SpyWordBank.pairs.first(),
            Random(42)
        )
        require(result is SpyDeal.Done)
        var state = SpyGame.startSpeaking(result.state)
        for (p in state.players) {
            state = (SpyGame.speak(state, p.index, "发言") as SpySpeak.Done).state
        }
        state = (SpyGame.vote(state, 0, 1) as SpyVoteOutcome.Done).state
        state = (SpyGame.vote(state, 1, 0) as SpyVoteOutcome.Done).state
        state = (SpyGame.vote(state, 2, 1) as SpyVoteOutcome.Done).state
        state = (SpyGame.vote(state, 3, 0) as SpyVoteOutcome.Done).state
        assertTrue(state.phase == SpyPhase.PK)
        val text = SpyScriptBot.speak(state, state.pkCandidates.first(), Random(3), pk = true)
        assertFalse(text.isBlank())
    }

    @Test
    fun pkVote_targetsOnlyCandidates() {
        val result = SpyGame.deal(
            List(4) { i -> SpyPlayer(i, "P$i", SpyPlayerKind.SCRIPT) },
            SpyWordBank.pairs.first(),
            Random(42)
        )
        require(result is SpyDeal.Done)
        var state = SpyGame.startSpeaking(result.state)
        for (p in state.players) {
            state = (SpyGame.speak(state, p.index, "发言") as SpySpeak.Done).state
        }
        state = (SpyGame.vote(state, 0, 1) as SpyVoteOutcome.Done).state
        state = (SpyGame.vote(state, 1, 0) as SpyVoteOutcome.Done).state
        state = (SpyGame.vote(state, 2, 1) as SpyVoteOutcome.Done).state
        state = (SpyGame.vote(state, 3, 0) as SpyVoteOutcome.Done).state
        for (candidate in state.pkCandidates) {
            state = (SpyGame.pkSpeak(state, candidate, "辩护") as SpyPkSpeak.Done).state
        }
        val target = SpyScriptBot.pkVote(state, 2, Random(0))
        assertNotNull(target)
        assertTrue(target in state.pkCandidates)
    }

    @Test
    fun vote_neverTargetsSelf() {
        val state = dealt(4)
        state.players.forEach { p ->
            val target = SpyScriptBot.vote(state, p.index, Random(p.index))
            assertNotNull(target)
            assertFalse(target == p.index)
        }
    }

    @Test
    fun vote_targetIsAmongAlivePlayers() {
        val state = dealt(4)
        state.players.forEach { p ->
            val target = SpyScriptBot.vote(state, p.index, Random(3))
            assertNotNull(target)
            assertTrue(state.alivePlayers.any { it.index == target })
        }
    }
}
