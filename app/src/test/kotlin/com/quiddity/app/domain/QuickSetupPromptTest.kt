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

    @Test
    fun `system prompt enforces name scene and perspective rules`() {
        val sys = QuickSetupPrompt.QUICK_SETUP_SYSTEM_PROMPT
        assertTrue(sys.contains("2～4 个字的正常人名"), "名字必须是正常人名而非昵称/长句")
        assertTrue(sys.contains("禁止\"小X\"式昵称") || sys.contains("禁止“小X”式昵称"), "禁止随意昵称")
        assertTrue(sys.contains("[当前场景]只写 1～2 句必要信息"), "场景必须简洁")
        assertTrue(sys.contains("第三人称客观视角"), "必须第三人称客观视角")
        assertTrue(sys.contains("禁止出现「你」「我」"), "禁止你/我")
    }

    @Test
    fun `user prompt includes perspective requirement`() {
        val userPrompt = QuickSetupPrompt.buildQuickSetupUserPrompt("温柔的学姐", QuickSetupTier.COMPREHENSIVE)
        assertTrue(userPrompt.contains("第三人称客观视角"))
        assertTrue(userPrompt.contains("禁止出现「你」「我」"))
        assertTrue(userPrompt.contains("2～4 字正常人名"))
    }

    @Test
    fun `missing required field keys per tier`() {
        val empty = QuickSetupResult(
            persona = Persona.Empty,
            userPersona = UserPersona.Empty,
            scene = "",
            memory = ""
        )
        val roughMissing = empty.missingRequiredFieldKeys(QuickSetupTier.ROUGH)
        assertTrue("ai_name" in roughMissing)
        assertTrue("ai_persona" in roughMissing)
        assertTrue("ai_character" in roughMissing)
        assertTrue("user_name" in roughMissing)
        assertTrue("scene" in roughMissing)
        assertTrue("memory" !in roughMissing)

        val fullMissing = empty.missingRequiredFieldKeys(QuickSetupTier.COMPREHENSIVE)
        assertTrue("ai_appearance" in fullMissing)
        assertTrue("ai_world_background" in fullMissing)
        assertTrue("ai_desired" in fullMissing)
        assertTrue("memory" in fullMissing)
    }

    @Test
    fun `filled persona has no missing keys`() {
        val filled = QuickSetupResult(
            persona = Persona(
                name = "林夕",
                persona = "学姐",
                character = "温柔",
                appearance = "长发",
                worldBackground = "都市世界，大学",
                desired = "耐心",
                compiledPersona = null,
                aiAvatarUri = null
            ),
            userPersona = UserPersona(
                name = "小明",
                identity = "新生",
                gender = "暂不设置",
                age = "18",
                appearance = "运动装"
            ),
            scene = "图书馆",
            memory = "喜欢拿铁"
        )
        assertEquals(emptySet(), filled.missingRequiredFieldKeys(QuickSetupTier.COMPREHENSIVE))
    }
}
