package com.quiddity.app.domain

import com.quiddity.app.data.model.Persona
import com.quiddity.app.data.model.UserPersona
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [QuickSetupPrompt] 单元测试：结构化解析、档位充实度要求、新生成原则。
 */
class QuickSetupPromptTest {

    @Test
    fun `parse full comprehensive output extracts all fields`() {
        val raw = """
            【AI人设】
            [AI名字]林夕
            [身份背景]温柔学姐，中文系大三
            [性格]说话轻声细语，喜欢用语气词
            [外观]长发，戴圆框眼镜
            [世界背景]都市世界，普通大学校园
            [期望特质]对用户永远温柔耐心
            【用户人设】
            [用户名字]小明
            [用户身份]大一新生
            [用户性别]男
            [用户年龄]18
            [用户外观]运动装
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
        val raw = "【AI人设】\n[AI名字]林夕\n【场景设置】\n[当前场景]图书馆"
        val result = QuickSetupPrompt.parseQuickSetupResult(raw, QuickSetupTier.COMPREHENSIVE)
        assertEquals("", result.persona.character)
        assertEquals("暂不设置", result.userPersona.gender)
        assertEquals("", result.memory)
    }

    @Test
    fun `AI与用户字段标签区分 防止归位错乱`() {
        val raw = """
            【AI人设】
            [AI名字]林夕
            [身份背景]学姐
            【用户人设】
            [用户名字]小明
            [用户身份]大一新生
            [用户性别]男
            [用户年龄]18
            [用户外观]运动装
            【场景设置】
            [当前场景]图书馆
        """.trimIndent()
        val result = QuickSetupPrompt.parseQuickSetupResult(raw, QuickSetupTier.ROUGH)
        assertEquals("林夕", result.persona.name, "AI 名字应从 [AI名字] 提取")
        assertEquals("学姐", result.persona.persona)
        assertEquals("小明", result.userPersona.name, "用户名字应从 [用户名字] 提取")
        assertEquals("大一新生", result.userPersona.identity)
        assertEquals("男", result.userPersona.gender)
        assertEquals("18", result.userPersona.age)
        assertEquals("运动装", result.userPersona.appearance)
    }

    @Test
    fun `字段内容中的方括号不再成为切分点`() {
        val raw = """
            【AI人设】
            [AI名字]林夕
            [身份背景]喜欢[看书]和[写作]
            【用户人设】
            [用户名字]小明
            [用户身份]学生
            [用户性别]暂不设置
            [用户年龄]18
            [用户外观]无
            【场景设置】
            [当前场景]图书馆
        """.trimIndent()
        val result = QuickSetupPrompt.parseQuickSetupResult(raw, QuickSetupTier.ROUGH)
        assertEquals("喜欢[看书]和[写作]", result.persona.persona, "正文中的方括号不应截断内容")
    }

    @Test
    fun `分节符带空格也能解析`() {
        val raw = "【AI 人设】\n[AI名字]林夕\n【用户 人设】\n[用户名字]小明\n【场景 设置】\n[当前场景]图书馆"
        val result = QuickSetupPrompt.parseQuickSetupResult(raw, QuickSetupTier.ROUGH)
        assertEquals("林夕", result.persona.name)
        assertEquals("小明", result.userPersona.name)
        assertEquals("图书馆", result.scene)
    }

    @Test
    fun `user prompt includes tier density requirement`() {
        val rough = QuickSetupPrompt.buildQuickSetupUserPrompt("温柔的学姐", QuickSetupTier.ROUGH)
        val full = QuickSetupPrompt.buildQuickSetupUserPrompt("温柔的学姐", QuickSetupTier.COMPREHENSIVE)
        assertTrue(rough.contains("精炼"))
        assertTrue(full.contains("详尽"))
    }

    @Test
    fun `system prompt emphasizes analysis over expansion`() {
        val sys = QuickSetupPrompt.QUICK_SETUP_SYSTEM_PROMPT
        // 1.7.0：剖析定位 + 名称检索双轨——具体输入剖析归位、禁止注水；仅名称/极模糊才启用知识检索补全
        assertTrue(sys.contains("剖析"))
        assertTrue(sys.contains("名称检索"))
        assertTrue(sys.contains("禁止注水凑数"))
        assertTrue(sys.contains("字段语义与边界"))
        assertTrue(sys.contains("user 消息【本次需生成的字段清单】"))
        assertFalse(sys.contains("合理发散"), "剖析模式不应鼓励发散补全")
    }

    @Test
    fun `system prompt enforces name scene and perspective rules`() {
        val sys = QuickSetupPrompt.QUICK_SETUP_SYSTEM_PROMPT
        assertTrue(sys.contains("绝不擅自改名"), "名字必须尊重用户原意，不得擅改")
        assertTrue(sys.contains("禁止套用烂俗模板"), "检索不到公认指代时原创，不套模板")
        assertTrue(sys.contains("[当前场景]"), "场景字段必须存在")
        assertTrue(sys.contains("30 字以内"), "场景必须限制字数")
        assertTrue(sys.contains("第三人称客观视角"), "必须第三人称客观视角")
        assertTrue(sys.contains("禁止出现「你」「我」"), "禁止你/我")
    }

    @Test
    fun `system prompt separates desired traits from memory`() {
        val sys = QuickSetupPrompt.QUICK_SETUP_SYSTEM_PROMPT
        assertTrue(sys.contains("[期望特质]"), "期望特质字段必须存在")
        assertTrue(sys.contains("用户希望 AI 对"), "期望特质描述用户对 AI 的行为期望")
        assertTrue(sys.contains("[需要记住的事]"), "记忆字段必须存在")
        assertTrue(sys.contains("用户明确要求 AI 记住"), "记忆只放用户明确要求记住的事实")
        assertTrue(sys.contains("与[期望特质]互斥"), "期望特质与记忆必须互斥")
        assertFalse(sys.contains("合理发散"), "剖析模式不应鼓励发散补全")
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
        assertTrue("scene" in roughMissing)
        // 1.7.0：用户人设全部按需填写，不强制用户名（聊天发送时再由应用层提示设置）
        assertFalse("user_name" in roughMissing)
        assertTrue("memory" !in roughMissing)

        val fullMissing = empty.missingRequiredFieldKeys(QuickSetupTier.COMPREHENSIVE)
        assertTrue("ai_appearance" in fullMissing)
        assertTrue("ai_world_background" in fullMissing)
        assertTrue("ai_desired" in fullMissing)
        // 1.7.0：记忆与用户人设按需填写，无记忆不阻塞填入
        assertFalse("memory" in fullMissing)
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

    @Test
    fun `记忆中的期望表达自动归位到期望特质`() {
        val raw = """
            【AI人设】
            [AI名字]林夕
            [身份背景]温柔学姐
            [性格]轻声细语
            [外观]长发
            [世界背景]都市世界
            [期望特质]保持自然
            【用户人设】
            [用户名字]小明
            [用户身份]大一新生
            [用户性别]男
            [用户年龄]18
            [用户外观]运动装
            【场景设置】
            [当前场景]图书馆
            【记忆设置】
            [需要记住的事]小明喜欢喝拿铁。希望她对我温柔耐心，说话带语气词
        """.trimIndent()
        val result = QuickSetupPrompt.parseQuickSetupResult(raw, QuickSetupTier.COMPREHENSIVE)
        assertTrue(result.persona.desired.contains("温柔耐心"), "记忆中的期望表达应移回期望特质")
        assertTrue(result.persona.desired.contains("说话带语气词"), "说话方式期望应移回期望特质")
        assertTrue(result.memory.contains("喜欢喝拿铁"), "记忆事实保留在记忆")
        assertFalse(result.memory.contains("温柔耐心"), "期望表达不应残留在记忆")
    }

    @Test
    fun `期望特质中的记忆事实自动归位到记忆`() {
        val raw = """
            【AI人设】
            [AI名字]林夕
            [身份背景]温柔学姐
            [性格]轻声细语
            [外观]长发
            [世界背景]都市世界
            [期望特质]说话带语气词。记住小明喜欢喝拿铁
            【用户人设】
            [用户名字]小明
            [用户身份]大一新生
            [用户性别]男
            [用户年龄]18
            [用户外观]运动装
            【场景设置】
            [当前场景]图书馆
            【记忆设置】
            [需要记住的事]小明是新生
        """.trimIndent()
        val result = QuickSetupPrompt.parseQuickSetupResult(raw, QuickSetupTier.COMPREHENSIVE)
        assertTrue(result.persona.desired.contains("说话带语气词"), "期望表达保留在期望特质")
        assertTrue(result.memory.contains("喜欢喝拿铁"), "期望特质中的记忆事实应移回记忆")
        assertFalse(result.persona.desired.contains("喜欢喝拿铁"), "记忆事实不应残留在期望特质")
    }

    @Test
    fun `parse strips placeholder values to blanks`() {
        val raw = """
            【AI人设】
            [AI名字]绯雪
            [身份背景]古老的魔法世家继承人
            [性格]冷静坚韧
            [外观]未提供具体描述
            [世界背景]奇幻世界
            [期望特质]立体人物
            【用户人设】
            [用户名字]未提供
            [用户身份]待定
            [用户性别]暂不设置
            [用户年龄]不详
            [用户外观]无
            【场景设置】
            [当前场景]塔楼顶端
            【记忆设置】
            [需要记住的事]无
        """.trimIndent()
        val result = QuickSetupPrompt.parseQuickSetupResult(raw, QuickSetupTier.COMPREHENSIVE)
        // 占位词应被清空，而不是当作真实人设内容
        assertEquals("", result.persona.appearance)
        assertEquals("", result.userPersona.name)
        assertEquals("", result.userPersona.identity)
        assertEquals("", result.userPersona.age)
        assertEquals("", result.userPersona.appearance)
        assertEquals("", result.memory)
        // 合法值保留
        assertEquals("暂不设置", result.userPersona.gender)
        assertEquals("奇幻世界", result.persona.worldBackground)
        assertEquals("塔楼顶端", result.scene)
    }
}
