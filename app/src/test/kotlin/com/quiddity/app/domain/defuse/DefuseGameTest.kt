package com.quiddity.app.domain.defuse

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefuseGameTest {

    private val random = Random(42)

    @Test
    fun generator_createsExpectedModuleCountPerDifficulty() {
        for (difficulty in DefuseDifficulty.entries) {
            val game = DefuseGenerator.generate(difficulty, random)
            assertEquals(difficulty.moduleCount, game.modules.size)
            assertEquals(difficulty.timeSeconds * 1000L, game.totalTimeMs)
            assertTrue(game.opponentTimeMs >= 30_000L, "对手用时不能低于 30 秒")
            assertTrue(game.modules.all { it.type == DefuseModuleType.WIRES || it.type == DefuseModuleType.KEYPAD || it.type == DefuseModuleType.BUTTON })
        }
    }

    @Test
    fun generator_modulesAreValid() {
        repeat(50) {
            val game = DefuseGenerator.generate(DefuseDifficulty.NORMAL, random)
            for (module in game.modules) {
                when (module) {
                    is DefuseModule.Wires -> {
                        assertEquals(5, module.wires.size)
                        assertTrue(module.answerIndex in 1..5)
                    }
                    is DefuseModule.Keypad -> {
                        assertEquals(4, module.symbols.size)
                        assertEquals(4, module.symbols.toSet().size)
                    }
                    is DefuseModule.Button -> {
                        assertNotNull(module.answerAction)
                    }
                }
            }
        }
    }

    @Test
    fun wireRules_answerMatchesManualBranches() {
        assertEquals(2, WireRules.answer(listOf(WireColor.RED, WireColor.RED, WireColor.RED, WireColor.RED, WireColor.RED)))
        assertEquals(5, WireRules.answer(listOf(WireColor.GREEN, WireColor.YELLOW, WireColor.WHITE, WireColor.BLUE, WireColor.BLUE)))
        assertEquals(4, WireRules.answer(listOf(WireColor.GREEN, WireColor.RED, WireColor.YELLOW, WireColor.WHITE, WireColor.RED)))
        assertEquals(1, WireRules.answer(listOf(WireColor.GREEN, WireColor.YELLOW, WireColor.WHITE, WireColor.BLUE, WireColor.GREEN)))
    }

    @Test
    fun keypadRules_orderFollowsGlobalGlyphOrder() {
        val module = DefuseModule.Keypad(symbols = listOf("♥", "☀", "◈", "☾"))
        assertEquals(listOf("☀", "☾", "♥", "◈"), module.answerOrder)
    }

    @Test
    fun buttonRules_answerMatchesManualBranches() {
        assertEquals(
            ButtonAction.CLICK,
            DefuseModule.Button(ButtonColor.BLUE, ButtonLabel.DETONATE, false).answerAction
        )
        assertEquals(
            ButtonAction.HOLD,
            DefuseModule.Button(ButtonColor.RED, ButtonLabel.ABORT, true).answerAction
        )
        assertEquals(
            ButtonAction.CLICK,
            DefuseModule.Button(ButtonColor.BLUE, ButtonLabel.PRESS, true).answerAction
        )
        assertEquals(
            ButtonAction.HOLD,
            DefuseModule.Button(ButtonColor.WHITE, ButtonLabel.PRESS, false).answerAction
        )
        assertEquals(
            ButtonAction.CLICK,
            DefuseModule.Button(ButtonColor.WHITE, ButtonLabel.PRESS, true).answerAction
        )
    }

    @Test
    fun cutWire_correctDefusesWrongStrikesAndDamages() {
        val game = gameWith(DefuseModule.Wires(
            wires = listOf(WireColor.GREEN, WireColor.YELLOW, WireColor.WHITE, WireColor.BLUE, WireColor.GREEN)
        ))
        val session = DefuseSession(game)

        val wrong = session.cutWire(3)
        val struck = assertIs<DefuseMove.Struck>(wrong).session
        assertEquals(1, struck.strikes)
        assertEquals(game.difficulty.lives - 1, struck.lives)
        assertTrue(3 in (struck.currentModule as DefuseModule.Wires).damaged)
        assertEquals(0, struck.combo)

        val right = struck.cutWire(1)
        val defused = assertIs<DefuseMove.Defused>(right).session
        assertEquals(1, defused.moduleIndex)
        assertEquals(1, defused.strikes)
        assertEquals(1, defused.combo)
        assertEquals(1, defused.maxCombo)
    }

    @Test
    fun cutWire_reCuttingDamagedWireIsInvalid() {
        val game = gameWith(DefuseModule.Wires(
            wires = listOf(WireColor.GREEN, WireColor.YELLOW, WireColor.WHITE, WireColor.BLUE, WireColor.GREEN)
        ))
        val struck = assertIs<DefuseMove.Struck>(DefuseSession(game).cutWire(3)).session
        assertEquals(DefuseMove.Invalid, struck.cutWire(3))
    }

    @Test
    fun strikeOnLastLifeExplodes() {
        val game = DefuseGame(
            id = "test-hard",
            difficulty = DefuseDifficulty.HARD,
            modules = listOf(
                DefuseModule.Wires(
                    wires = listOf(WireColor.GREEN, WireColor.YELLOW, WireColor.WHITE, WireColor.BLUE, WireColor.GREEN)
                )
            ),
            totalTimeMs = 200_000L,
            opponentTimeMs = 172_000L
        )
        var session = DefuseSession(game)
        val module = session.currentModule as DefuseModule.Wires
        val wrongIndex = (1..5).first { it != module.answerIndex }
        val move = session.cutWire(wrongIndex)
        val struck = assertIs<DefuseMove.Struck>(move).session
        assertEquals(1, struck.lives)
        assertFalse(struck.exploded)

        val secondWrong = (1..5).first { it != module.answerIndex && it != wrongIndex }
        val second = struck.cutWire(secondWrong)
        val exploded = assertIs<DefuseMove.Struck>(second).session
        assertTrue(exploded.exploded)
        assertEquals(0, exploded.lives)
    }

    @Test
    fun keypad_fullCorrectSequenceDefuses() {
        val module = DefuseModule.Keypad(symbols = listOf("♥", "☀", "◈", "☾"))
        val game = gameWith(module)
        var session = DefuseSession(game)

        val first = assertIs<DefuseMove.Progressed>(session.pressKeypad("☀")).session
        val second = assertIs<DefuseMove.Progressed>(first.pressKeypad("☾")).session
        assertEquals(listOf("☀", "☾"), (second.currentModule as DefuseModule.Keypad).entered)

        val third = assertIs<DefuseMove.Progressed>(second.pressKeypad("♥")).session
        val defused = assertIs<DefuseMove.Defused>(third.pressKeypad("◈")).session
        assertEquals(1, defused.moduleIndex)
        assertTrue(defused.allCleared)
        assertEquals(1, defused.combo)
    }

    @Test
    fun keypad_wrongSymbolStrikesAndResets() {
        val module = DefuseModule.Keypad(symbols = listOf("♥", "☀", "◈", "☾"))
        val game = gameWith(module)
        var session = DefuseSession(game)
        session = assertIs<DefuseMove.Progressed>(session.pressKeypad("☀")).session

        val struck = assertIs<DefuseMove.Struck>(session.pressKeypad("◈")).session
        assertEquals(1, struck.strikes)
        assertTrue((struck.currentModule as DefuseModule.Keypad).entered.isEmpty())
    }

    @Test
    fun button_wrongActionStrikes() {
        val module = DefuseModule.Button(ButtonColor.BLUE, ButtonLabel.PRESS, true)
        val game = gameWith(module)
        val session = DefuseSession(game)
        assertEquals(ButtonAction.CLICK, module.answerAction)

        val defused = assertIs<DefuseMove.Defused>(session.pressButton(ButtonAction.CLICK)).session
        assertTrue(defused.allCleared)

        val wrongGame = gameWith(DefuseModule.Button(ButtonColor.RED, ButtonLabel.PRESS, true))
        val struck = assertIs<DefuseMove.Struck>(DefuseSession(wrongGame).pressButton(ButtonAction.CLICK)).session
        assertEquals(1, struck.strikes)
    }

    @Test
    fun scoring_rewardsProgressAndPunishesMistakes() {
        val easy = DefuseScoring.score(DefuseDifficulty.EASY, 2, 3, 90, 0, maxCombo = 2, helpUsed = false)
        val withStrikes = DefuseScoring.score(DefuseDifficulty.EASY, 2, 2, 90, 2, maxCombo = 0, helpUsed = false)
        assertTrue(easy > 0)
        assertTrue(easy > withStrikes)
        val withHelp = DefuseScoring.score(DefuseDifficulty.EASY, 2, 3, 90, 0, maxCombo = 2, helpUsed = true)
        assertTrue(easy > withHelp)
        assertEquals(
            0,
            DefuseScoring.score(DefuseDifficulty.EASY, 0, 3, 90, 0, maxCombo = 0, helpUsed = false)
        )
        val comboBonus = DefuseScoring.score(DefuseDifficulty.EASY, 2, 3, 90, 0, maxCombo = 3, helpUsed = false)
        val noCombo = DefuseScoring.score(DefuseDifficulty.EASY, 2, 3, 90, 0, maxCombo = 0, helpUsed = false)
        assertTrue(comboBonus > noCombo)
    }

    @Test
    fun strikeResetsCombo() {
        val game = DefuseGame(
            id = "test-combo",
            difficulty = DefuseDifficulty.EASY,
            modules = listOf(
                DefuseModule.Wires(
                    wires = listOf(WireColor.RED, WireColor.GREEN, WireColor.YELLOW, WireColor.WHITE, WireColor.GREEN)
                ),
                DefuseModule.Keypad(symbols = listOf("☀", "★", "☾", "♥"))
            ),
            totalTimeMs = 150_000L,
            opponentTimeMs = 170_000L
        )
        var session = DefuseSession(game)
        val first = session.currentModule as DefuseModule.Wires
        session = assertIs<DefuseMove.Defused>(session.cutWire(first.answerIndex)).session
        assertEquals(1, session.combo)
        val second = session.currentModule as DefuseModule.Keypad
        session = assertIs<DefuseMove.Struck>(session.pressKeypad(second.answerOrder.last())).session
        assertEquals(0, session.combo)
        assertEquals(1, session.maxCombo)
    }

    private fun gameWith(module: DefuseModule): DefuseGame = DefuseGame(
        id = "test",
        difficulty = DefuseDifficulty.EASY,
        modules = listOf(module),
        totalTimeMs = 150_000L,
        opponentTimeMs = 170_000L
    )
}
