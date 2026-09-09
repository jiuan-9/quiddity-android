package com.quiddity.app.domain

import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.Persona
import com.quiddity.app.data.model.UserPersona
import com.quiddity.app.util.TokenEstimator
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * 系统提示词体积回归测试：每轮对话都会把 system 提示词整份发给 LLM，
 * 固定模板开销过大会挤压上下文与输出预算。本测试锁定"空人设（仅名字）"下的
 * 模板固定开销，防止后续加规则时无节制膨胀。
 * 1.6.x 起对话方式新增「动作描写克制 + 继续说直出台词 + 重说换表达」规则，
 * 预算随规则同步上调（280 → 400 token），仍对后续膨胀保持红线。
 */
class PromptBuilderBudgetTest {

    private fun minimalConv(): Conversation = Conversation(
        id = "conv_1",
        createdAt = 0L,
        updatedAt = 0L,
        persona = Persona(name = "林晚"),
        userPersona = UserPersona(name = "小明")
    )

    @Test
    fun `system prompt fixed overhead stays within token budget`() {
        val privatePrompt = PromptBuilder.buildSystemPrompt(minimalConv())
        val groupPrompt = PromptBuilder.buildGroupSystemPrompt(minimalConv(), PromptBuilder.GROUP_RULES)
        val privateStats = TokenEstimator.analyze(privatePrompt)
        val groupStats = TokenEstimator.analyze(groupPrompt)
        assertTrue(
            privateStats.tokenEstimate <= 400,
            "私聊 system 固定开销应 ≤400 token，当前 ${privateStats.tokenEstimate}"
        )
        assertTrue(
            groupStats.tokenEstimate <= 560,
            "群聊 system 固定开销应 ≤560 token，当前 ${groupStats.tokenEstimate}"
        )
        val rulesStats = TokenEstimator.analyze(PromptBuilder.GROUP_RULES)
        assertTrue(
            rulesStats.tokenEstimate <= 95,
            "群聊规则应 ≤95 token，当前 ${rulesStats.tokenEstimate}"
        )
    }
}
