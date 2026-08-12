package com.quiddity.app.domain

import com.quiddity.app.data.model.Persona
import com.quiddity.app.data.model.UserPersona
/*
 * ============================================================================
 * 开发规范 (Development Specifications)
 * ============================================================================
 *
 * 1. 问题修复规范
 *    所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
 *    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
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
 * 快速设定提示词中枢：系统提示词、user 消息拼装、结构化结果解析。
 *
 * 输出格式（分节符不计入字数，仅计填空项内容）：
 * 【AI人设】
 * [名字]…
 * [身份背景]…
 * [性格]…
 * [外观]…
 * [世界背景]…（必须以恰好4个字的世界类型开头，如 都市世界/玄幻世界/末日世界/校园世界/修仙世界/科幻世界）
 * [期望特质]…
 *
 * 【用户人设】
 * [名字]…
 * [身份]…
 * [性别]…
 * [年龄]…
 * [外观]…
 *
 * 【场景设置】
 * [当前场景]…
 *
 * 【记忆设置】
 * [需要记住的事]…
 *
 * 粗略档省略 [外观][世界背景][期望特质] 与整个【记忆设置】节；【场景设置】所有档位均生成。
 */
object QuickSetupPrompt {

    // ===== 三条开发规范（位于文件中间位置） =====
    // 1. 问题修复规范：所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
    //    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
    // 2. 代码注释规范：文件内仅允许保留两类注释——当前规则说明注释与模块划分注释（// ===== xxx =====）。
    //    除此之外，禁止出现任何形式的代码注释（包括单行 / 多行 / 临时调试注释）。
    // 3. 构建交付要求：完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK。
    //    APK 需满足：签名有效且符合发布标准、包含完整功能模块、经过基础性能与兼容性测试。

    internal val QUICK_SETUP_SYSTEM_PROMPT = """
你是一个"角色生成器"。用户会给你一段可能十分模糊的人设描述，你要基于它生成一份结构完整、可直接被 AI 使用的角色卡。

【输出格式】严格按以下章节输出，每段文字独占一段。分节符不计入字数，仅填空项内容计入字数上限：
【AI人设】
[名字]……
[身份背景]……
[性格]……
[外观]……
[世界背景]……
[期望特质]……
【用户人设】
[名字]……
[身份]……
[性别]……
[年龄]……
[外观]……
【场景设置】
[当前场景]……
【记忆设置】
[需要记住的事]……

生成原则（按优先级执行）：
1. 忠实于用户意图——用户明确说过的设定一字不改；描述模糊或缺失的部分允许你合理发散补全，让角色有血有肉、自成一体，但发散必须贴合用户原意，不得违背用户明确给出的设定。
2. 每个字段都必须填充且内容充实——即使档位较低，每个字段也要有具体信息量；禁止空字段，禁止一句话敷衍。
3. 具体胜于抽象——用具体细节说话：口头禅、习惯性小动作、说话节奏、喜好与厌恶、藏在心里的矛盾、坚持的信念、害怕或渴望的东西。禁止只写"温柔""傲娇"这类空洞形容词而不展开。
4. 多样且不落俗套——根据用户描述量身定制，避免千人一面的模板；不要用雷同的开头句式，不要堆砌流行词或烂梗。
5. 密度优先——充分利用字数上限写出有信息量的内容，但拒绝凑字、空话、套话和重复；每个字都要有用，宁可精炼也不注水。
6. 世界类型——[世界背景]字段必须以恰好 4 个汉字的"世界类型"开头（如：都市世界/玄幻世界/末日世界/校园世界/修仙世界/科幻世界/古风世界/星际世界），紧接详细的世界背景描述。
7. 名字规则——[名字]必须是 2～4 个字的正常人名（常见姓氏 + 名字，如 林晚、苏晴、周野）；用户明确指定名字时按用户指定的来；未指定时取一个像真人的普通名字，禁止"小X"式昵称、网名、ID 或描述性长句。
8. 场景规则——[当前场景]只写一句、30 字以内（时间 / 地点 / 氛围即可），如"傍晚的大学图书馆"。禁止展开描写、背景故事或多句描述，越精简越好。
9. 视角规则（最高优先级）——所有字段必须以第三人称客观视角书写，禁止出现「你」「我」这类直接称呼；AI 角色用 [名字] 或"她/他"指代，用户用【用户人设】的[名字]指代。描写的是"这个角色是什么样"，而不是"你要怎么做"。
10. 性别规则——【用户人设】的[性别]仅在用户描述中明确出现时填写；未明确时一律填"暂不设置"，绝不根据名字或语气猜测。
11. 格式严格——只输出上述章节与字段；不得新增任何章节或字段，缺失字段视为生成失败。

现在等待用户的人设描述。
""".trim()

    /**
     * 构造快速设定的 user 消息：档位说明 + 字段清单 + 字数上限 + 用户描述。
     */
    fun buildQuickSetupUserPrompt(userDescription: String, tier: QuickSetupTier): String {
        val sb = StringBuilder()
        sb.append("【本次档位】").append(tier.chineseName).append("\n")
        sb.append("【字数上限】汉字 ").append(tier.maxChars).append(" 字 / 英文 ").append(tier.maxWords)
            .append(" 词（这是最大上限，不是必须达到的目标；仅计填空项内容，分节符与字段标签不计入；内容自然少于上限是正常的，禁止注水凑数）\n")
        sb.append("【完整度级别】").append(tier.chineseName).append("\n")
        sb.append("【本档位充实度要求】").append(tier.densityRequirement).append("\n\n")
        sb.append("【视角要求】所有字段以第三人称客观视角书写，禁止出现「你」「我」；")
        sb.append("[名字]用 2～4 字正常人名；[当前场景]保持 1～2 句简洁。\n\n")

        sb.append("【本次需生成的字段清单】\n")
        sb.append("AI 人设：")
        sb.append(tier.aiPersonaFields().joinToString("、") { it.label })
        sb.append("\n")
        sb.append("用户人设：[名字]、[身份]、[性别]、[年龄]、[外观]\n")
        sb.append("场景设置：[当前场景]\n")
        if (tier.includesMemory) {
            sb.append("记忆设置：[需要记住的事]\n")
        } else {
            sb.append("记忆设置：本档位不生成（省略整个【记忆设置】节）\n")
        }
        sb.append("\n")

        sb.append("请严格按系统提示词的输出格式生成。")
        sb.append("重要：只能生成上述清单中列出的字段，清单之外的字段（如本档位未列出的 AI 人设字段、未列出的分节）一律不得输出。")
        sb.append("生成的每一段内容都要让使用它的 AI 能直接贴合人设。\n\n")

        sb.append("【用户的人设描述】\n").append(userDescription.trim())
        return sb.toString()
    }

    /**
     * 解析 LLM 返回的结构化文本为 [QuickSetupResult]。
     *
     * 容错策略：
     * - 按分节符【AI人设】【用户人设】【场景设置】【记忆设置】切分；
     * - 每节内按字段标签 [xxx] 提取，标签后到下一个标签或节末为内容；
     * - 缺失字段返回空字符串，保证不抛异常；
     * - 性别字段若为空，统一回退为"暂不设置"。
     */
    fun parseQuickSetupResult(raw: String, tier: QuickSetupTier): QuickSetupResult {
        val text = raw.trim()
        val aiSection = extractSection(text, "【AI人设】", "【用户人设】")
        val userSection = extractSection(text, "【用户人设】", "【场景设置】")
        val sceneSection = extractSection(
            text, "【场景设置】",
            if (tier.includesMemory) "【记忆设置】" else null
        )
        val memorySection = if (tier.includesMemory) extractSection(text, "【记忆设置】", null) else ""

        val name = extractField(aiSection, AiPersonaField.NAME.label)
        val persona = extractField(aiSection, AiPersonaField.PERSONA.label)
        val character = extractField(aiSection, AiPersonaField.CHARACTER.label)
        val appearance = extractField(aiSection, AiPersonaField.APPEARANCE.label)
        val worldBackground = extractField(aiSection, AiPersonaField.WORLD_BACKGROUND.label)
        val desired = extractField(aiSection, AiPersonaField.DESIRED.label)

        val userName = extractField(userSection, UserPersonaField.NAME.label)
        val userIdentity = extractField(userSection, UserPersonaField.IDENTITY.label)
        val rawGender = extractField(userSection, UserPersonaField.GENDER.label)
        val userGender = rawGender.ifBlank { "暂不设置" }
        val userAge = extractField(userSection, UserPersonaField.AGE.label)
        val userAppearance = extractField(userSection, UserPersonaField.APPEARANCE.label)

        val scene = extractField(sceneSection, "[当前场景]").trim()

        val memory = if (tier.includesMemory) {
            extractField(memorySection, "[需要记住的事]").trim()
        } else ""

        return QuickSetupResult(
            persona = Persona(
                name = name,
                desired = desired,
                persona = persona,
                character = character,
                appearance = appearance,
                worldBackground = worldBackground,
                compiledPersona = null,
                aiAvatarUri = null
            ),
            userPersona = UserPersona(
                name = userName,
                identity = userIdentity,
                gender = userGender,
                age = userAge,
                appearance = userAppearance
            ),
            scene = scene,
            memory = memory
        )
    }

    private fun extractSection(text: String, startMarker: String, endMarker: String?): String {
        val startIdx = text.indexOf(startMarker)
        if (startIdx < 0) return ""
        val contentStart = startIdx + startMarker.length
        val endIdx = if (endMarker != null) {
            text.indexOf(endMarker, contentStart)
        } else {
            -1
        }
        return if (endIdx >= 0) {
            text.substring(contentStart, endIdx)
        } else {
            text.substring(contentStart)
        }
    }

    private fun extractField(section: String, label: String): String {
        val startIdx = section.indexOf(label)
        if (startIdx < 0) return ""
        val contentStart = startIdx + label.length
        val nextLabelIdx = findNextLabel(section, contentStart)
        val raw = if (nextLabelIdx >= 0) {
            section.substring(contentStart, nextLabelIdx)
        } else {
            section.substring(contentStart)
        }
        return raw.trim().trim('\n', '\r').trim()
    }

    private fun findNextLabel(section: String, from: Int): Int {
        val pattern = Regex("\\[[^\\]]+\\]")
        val match = pattern.find(section, from)
        return match?.range?.first ?: -1
    }
}

/**
 * 必填校验：返回当前档位下缺失字段的 key 集合。
 * 预览弹窗据此禁用「填入」并标红缺失项。
 */
fun QuickSetupResult.missingRequiredFieldKeys(tier: QuickSetupTier): Set<String> {
    val keys = mutableSetOf<String>()
    tier.aiPersonaFields().forEach { field ->
        val value = when (field) {
            AiPersonaField.NAME -> persona.name
            AiPersonaField.PERSONA -> persona.persona
            AiPersonaField.CHARACTER -> persona.character
            AiPersonaField.APPEARANCE -> persona.appearance
            AiPersonaField.WORLD_BACKGROUND -> persona.worldBackground
            AiPersonaField.DESIRED -> persona.desired
        }
        if (value.isBlank()) keys += "ai_${field.name.lowercase()}"
    }
    if (userPersona.name.isBlank()) keys += "user_name"
    if (userPersona.identity.isBlank()) keys += "user_identity"
    if (userPersona.gender.isBlank()) keys += "user_gender"
    if (userPersona.age.isBlank()) keys += "user_age"
    if (userPersona.appearance.isBlank()) keys += "user_appearance"
    if (scene.isBlank()) keys += "scene"
    if (tier.includesMemory && memory.isBlank()) keys += "memory"
    return keys
}
