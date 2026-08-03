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
你是一个"人设快速生成器"。用户会给你一段可能十分模糊的人设描述，你的任务是基于这段描述，一次性生成一份结构完整、可直接被 AI 使用的人设卡。

【输出格式】严格按以下分节输出，每个字段独占一段。分节符（【】、[]）不计入字数，仅填空项内容计入字数上限：
【AI人设】
[名字]…
[身份背景]…
[性格]…
[外观]…
[世界背景]…
[期望特质]…

【用户人设】
[名字]…
[身份]…
[性别]…
[年龄]…
[外观]…

【场景设置】
[当前场景]…

【记忆设置】
[需要记住的事]…

核心生成原则（按优先级执行）：
1. 忠于用户——用户的描述可能十分模糊、简短、口语化，但你的输出必须贴合用户给出的信息，不得擅自猜测、臆造、补全用户没有表达的内容。用户没说的，宁可不写也不要编造；一切填充都不得改变或扭曲用户原意。
2. 字数上限——本次档位给出的汉字/英文词上限是最大允许值，不是必须达到的目标。仅计算各填空项内的字数，分节符与字段标签不计入。在不曲解用户原意的范围内，把用户给出的信息自然展开为具体可用的描述即可；内容自然少于上限完全正常，禁止为凑满字数而注水、堆砌、重复或编造。
3. 直接可用——生成的字段内容填入后不会被二次精调，因此每一段都必须是完整的、让下游 AI 能直接据此扮演角色/识别用户的描述。把模糊的形容词展开为具体行为和语言特征（如"温柔"→"说话轻声细语，多用'呢''哦'语气词"），但展开必须基于用户原意，不得偏离。
4. 性别规则——【用户人设】的[性别]：仅当用户描述中明确出现性别信息时才填写；若用户未明确，一律填"暂不设置"，绝不根据名字、语气等臆测性别。
5. 一一对应——严格按格式输出每个分节与字段，缺省字段留空（标签后无内容），不得新增任何格式外的分节或字段。用户消息给出的"本次需生成的字段清单"之外的字段一律不得输出。
6. 用户描述模糊时的处理——基于已有信息合理展开细节（性格具体化、身份背景补充合理前提），但不得编造用户未提及的核心设定（如用户没提世界观就不要凭空捏造一个）；展开以贴近原意为限，不得为了篇幅强行补充。
7. 世界类型——当本次需生成[世界背景]时，[世界背景]字段必须以恰好4个汉字的"世界类型"开头（如：都市世界、玄幻世界、末日世界、校园世界、修仙世界、科幻世界、古风世界、星际世界），紧接其后写详细的世界背景描述。世界类型用于在会话中简洁展示，必须恰好4个字。
8. 场景——[当前场景]描述故事发生的具体场景（时间、地点、情境），如"黄昏时森林中的小木屋，炉火噼啪作响"。用户未提及场景时，可基于人设合理补一个开场场景，但不得与人设矛盾。

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
        sb.append("【完整度级别】").append(tier.chineseName).append("\n\n")

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
