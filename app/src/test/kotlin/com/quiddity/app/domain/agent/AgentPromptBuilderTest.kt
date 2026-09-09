package com.quiddity.app.domain.agent

import com.quiddity.app.data.model.Persona
import com.quiddity.app.data.model.UserPersona
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.domain.PromptBuilder
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

/*
 * ============================================================================
 * 开发规范 (Development Specifications)
 * ============================================================================
 *
 * 1. 问题修复规范
 *    所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
 *    修复内容需完全融入现有代码架构，保证代码逻辑的连贯性、可维护性和可扩展性。
 *
 * 2. 代码注释规范
 *    文件内仅允许保留两类注释：
 *    - 当前规则说明注释（即本注释块）
 *    - 模块划分注释（用于标识代码功能模块边界）
 *    除此之外，禁止出现任何形式的代码注释（包括但不限于单行注释、多行注释、临时调试注释等）。
 *
 * 3. 构建交付要求
 *    在完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK 文件。
 *    APK 文件需满足以下条件：
 *    - 签名有效且符合发布标准
 *    - 包含完整的功能模块
 *    - 经过基础性能测试和兼容性测试
 *    以便在真实设备环境中进行功能验证和性能评估。
 *
 * ============================================================================
 */

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
