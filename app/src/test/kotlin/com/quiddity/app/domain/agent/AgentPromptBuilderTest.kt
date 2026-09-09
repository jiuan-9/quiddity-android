package com.quiddity.app.domain.agent

import com.quiddity.app.data.model.Persona
import com.quiddity.app.data.model.UserPersona
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.domain.PromptBuilder
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

/**
 * Agent 系统提示词测试：冷启动默认人设、自定义人设注入、不可信数据规则。
 */
class AgentPromptBuilderTest {

    private fun agentConv(
        persona: Persona = Persona.Empty,
        userPersona: UserPersona = UserPersona.Empty
    ): Conversation = Conversation(
        id = "conv_agent_test",
        createdAt = 0L,
        updatedAt = 0L,
        type = ConversationType.AGENT,
        persona = persona,
        userPersona = userPersona
    )

    @Test
    fun coldDefaultPersona_usedWhenUnset() {
        val prompt = PromptBuilder.buildAgentSystemPrompt(agentConv())
        assertContains(prompt, "你仍然是你人设中的角色")
        assertContains(prompt, "工具")
        assertContains(prompt, "确认")
    }

    @Test
    fun customPersona_replacesColdDefault() {
        val prompt = PromptBuilder.buildAgentSystemPrompt(
            agentConv(persona = Persona(name = "小助手", persona = "设备管家", character = "谨慎"))
        )
        assertContains(prompt, "小助手")
        assertContains(prompt, "设备管家")
        assertContains(prompt, "谨慎")
    }

    @Test
    fun userPersona_injected() {
        val prompt = PromptBuilder.buildAgentSystemPrompt(
            agentConv(userPersona = UserPersona(name = "小明"))
        )
        assertContains(prompt, "小明")
    }

    @Test
    fun untrustedDataRule_present() {
        val prompt = PromptBuilder.buildAgentSystemPrompt(agentConv())
        assertContains(prompt, "不可信数据")
        assertContains(prompt, "不视为指令")
    }

    @Test
    fun workflowContract_present() {
        // 方案 A：任务工作流契约（计划→执行→校验→汇报 + 失败分级 + 去重意识）应进入系统提示词
        val prompt = PromptBuilder.buildAgentSystemPrompt(agentConv())
        assertContains(prompt, "任务工作流")
        assertContains(prompt, "列出需要的工具与顺序")
        assertContains(prompt, "校验")
        assertContains(prompt, "汇报")
        assertContains(prompt, "程序会自动重试一次")
        assertContains(prompt, "已去重")
        assertContains(prompt, "按任务工作流执行")
    }
}
