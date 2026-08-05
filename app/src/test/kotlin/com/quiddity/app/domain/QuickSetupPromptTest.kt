package com.quiddity.app.domain

import com.quiddity.app.data.model.Persona
import com.quiddity.app.data.model.UserPersona
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [QuickSetupPrompt] 单元测试：结构化解析、档位充实度要求、新生成原则。
 */
class QuickSetupPromptTest {

    @Test
    fun `parse full comprehensive output extracts all fields`() {
        val raw = """
            【AI人设】
            [名字]林夕
            [身份背景]温柔学姐，中文系大三
            [性格]说话轻声细语，喜欢用语气词
            [外观]长发，戴圆框眼镜
            [世界背景]都市世界，普通大学校园
            [期望特质]对用户永远温柔耐心
            【用户人设】
            [名字]小明
            [身份]大一新生
            [性别]男
            [年龄]18
            [外观]运动装
            【场景设置】
            [当前场景]黄昏时的图书馆，两人靠窗而坐
            【记忆设置】
            [需要记住的事]小明喜欢喝拿铁
        """.trimIndent()
        val result = QuickSetupPrompt.parseQuickSetupResult(raw, QuickSetupTier.COMPREHENSIVE)
        assertEquals("林夕", result.persona.name)
        assertEquals("温柔学姐，中文系大三", result.persona.persona)
        assertEquals("对用户永远温柔耐心", result.persona.desired)
        assertEquals("小明", result.userPersona.name)
        assertEquals("男", result.userPersona.gender)
        assertEquals("黄昏时的图书馆，两人靠窗而坐", result.scene)
        assertEquals("小明喜欢喝拿铁", result.memory)
    }

    @Test
    fun `parse missing fields returns blanks and gender fallback`() {
        val raw = "【AI人设】\n[名字]林夕\n【场景设置】\n[当前场景]图书馆"
        val result = QuickSetupPrompt.parseQuickSetupResult(raw, QuickSetupTier.COMPREHENSIVE)
        assertEquals("", result.persona.character)
        assertEquals("暂不设置", result.userPersona.gender)
        assertEquals("", result.memory)
    }

    @Test
    fun `user prompt includes tier density requirement`() {
        val rough = QuickSetupPrompt.buildQuickSetupUserPrompt("温柔的学姐", QuickSetupTier.ROUGH)
        val full = QuickSetupPrompt.buildQuickSetupUserPrompt("温柔的学姐", QuickSetupTier.COMPREHENSIVE)
        assertTrue(rough.contains("精炼"))
        assertTrue(full.contains("详尽"))
    }

    @Test
    fun `system prompt emphasizes divergence and density`() {
        val sys = QuickSetupPrompt.QUICK_SETUP_SYSTEM_PROMPT
        assertTrue(sys.contains("合理发散"))
        assertTrue(sys.contains("具体胜于抽象"))
        assertTrue(sys.contains("密度优先"))
        assertTrue(sys.contains("每个字段都必须填充"))
    }
}
