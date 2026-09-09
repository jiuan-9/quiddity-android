package com.quiddity.app.domain.defuse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DefuseScriptPartnerTest {

    @Test
    fun reportTriggersCorrectInstructionForWires() {
        val module = DefuseModule.Wires(
            wires = listOf(WireColor.GREEN, WireColor.YELLOW, WireColor.WHITE, WireColor.BLUE, WireColor.GREEN)
        )
        val text = DefuseScriptPartner.reply(module, "面板汇报：…", reported = false)
        assertTrue(text.contains("剪第 ${module.answerIndex} 根"), text)
    }

    @Test
    fun reportTriggersCorrectInstructionForKeypad() {
        val module = DefuseModule.Keypad(symbols = listOf("♥", "☀", "◈", "☾"))
        val text = DefuseScriptPartner.reply(module, "面板汇报", reported = false)
        assertTrue(text.contains(module.answerOrder.joinToString(" ")), text)
    }

    @Test
    fun reportTriggersCorrectInstructionForButton() {
        val module = DefuseModule.Button(ButtonColor.BLUE, ButtonLabel.PRESS, true)
        val text = DefuseScriptPartner.reply(module, "面板汇报", reported = false)
        assertTrue(text.contains(module.answerAction.label), text)
    }

    @Test
    fun nonReportAsksForPanelDescription() {
        val module = DefuseModule.Button(ButtonColor.RED, ButtonLabel.ABORT, true)
        val text = DefuseScriptPartner.reply(module, "你好", reported = false)
        assertTrue(text.contains("汇报面板"), text)
    }

    @Test
    fun explainRepeatsInstruction() {
        val module = DefuseModule.Button(ButtonColor.RED, ButtonLabel.ABORT, true)
        val text = DefuseScriptPartner.reply(module, "再解释一遍", reported = false)
        assertEquals(DefuseScriptPartner.instruction(module), text)
    }
}
