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
 * [AI名字]…
 * [身份背景]…
 * [性格]…
 * [外观]…
 * [世界背景]…（必须以恰好4个字的世界类型开头，如 都市世界/玄幻世界/末日世界/校园世界/修仙世界/科幻世界）
 * [期望特质]…
 *
 * 【用户人设】
 * [用户名字]…
 * [用户身份]…
 * [用户性别]…
 * [用户年龄]…
 * [用户外观]…
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

    /** 全部已知字段标签（AI / 用户 / 场景 / 记忆），解析切分点只认这些标签。 */
    private val allFieldLabels = buildSet {
        AiPersonaField.entries.forEach { add(it.label) }
        UserPersonaField.entries.forEach { add(it.label) }
        add("[当前场景]")
        add("[需要记住的事]")
    }

    // ===== 三条开发规范（位于文件中间位置） =====
    // 1. 问题修复规范：所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
    //    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
    // 2. 代码注释规范：文件内仅允许保留两类注释——当前规则说明注释与模块划分注释（// ===== xxx =====）。
    //    除此之外，禁止出现任何形式的代码注释（包括单行 / 多行 / 临时调试注释）。
    // 3. 构建交付要求：完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK。
    //    APK 需满足：签名有效且符合发布标准、包含完整功能模块、经过基础性能与兼容性测试。

    internal val QUICK_SETUP_SYSTEM_PROMPT = """
你是一个"人设剖析师"。用户会给你一段人设描述——可能很长、很乱、很口语化。你的任务不是扩写、拉长或美化它，而是**剖析**：拆解用户原话里的设定信息，归位到对应的字段里，让下游 AI 能直接使用这份结构化的角色卡。

 【输出格式】严格按以下章节输出，每段文字独占一段。分节符不计入字数，仅填空项内容计入字数上限：
 【AI人设】
 [AI名字]……
 [身份背景]……
 [性格]……
 [外观]……
 [世界背景]……
 [期望特质]……
 【用户人设】
 [用户名字]……
 [用户身份]……
 [用户性别]……
 [用户年龄]……
 [用户外观]……
 【场景设置】
 [当前场景]……
 【记忆设置】
 [需要记住的事]……

剖析原则（按优先级执行）：
1. 剖析优先——先通读用户描述，识别其中隐含的结构：这个人是谁（身份背景/性格/外观/世界背景）、用户希望 AI 怎样（期望特质）、用户自己是谁（用户人设）、此刻身处何处（场景）、有什么要记住的（记忆）。把用户**亲口说出的设定**逐字归位到对应字段，关键用词原样保留，不替他改写。
2. 禁止扩写与注水——用户的描述有多长，字段内容就有多实。绝不为了凑满字数上限而添加形容词、设定或背景故事。字数上限只是天花板，不是目标；内容自然少于上限完全正常。
3. 克制推断——只有用户完全没提、但字段又必须存在的项目才做最小推断：[名字]取一个贴合用户原意的普通常见名（2～4 个字的正常人名，禁止"小X"式昵称、网名、ID 或描述性长句）；[世界背景]在用户未提及时按[身份背景]的自然归属给出 4 字世界类型；[当前场景]只写一句、30 字以内（时间 / 地点 / 氛围即可）。其余字段一律以用户原话为准，绝不自行添加用户没说的设定。
4. 具体胜于抽象——若用户原话本身就具体（口头禅、习惯、喜好），原样保留这些细节；若用户只给了抽象词（如"温柔"），可以做克制的展开说明其含义，但展开必须源于原词本义，不编造新设定。
5. 不落俗套——不要套用模板句式和流行词；剖析的产出应保持用户自己的语气与风格。
 6. 视角规则（最高优先级）——所有字段必须以第三人称客观视角书写，禁止出现「你」「我」这类直接称呼；AI 角色用 [AI名字] 或"她/他"指代，用户用【用户人设】的[用户名字]指代。描写的是"这个角色是什么样"，而不是"你要怎么做"。
 7. 性别规则——【用户人设】的[性别]仅在用户描述中明确出现时填写；未明确时一律填"暂不设置"，绝不根据名字或语气猜测。
 8. 字段唯一——AI 字段一律用 [AI名字] 等带「AI」前缀的标签，用户字段一律用 [用户名字] 等带「用户」前缀的标签；禁止在 AI 章节输出 [用户xxx] 字段，禁止在用户章节输出 [AIxxx] 字段。
 9. 期望特质与记忆分离——[期望特质] 只放用户对 AI 行为、性格、说话方式、互动风格的核心期望（如"对我温柔耐心""说话带语气词"）；[需要记住的事] 只放用户明确要求 AI 记住的事实、约定、偏好、背景信息（如"小明喜欢喝拿铁"）。用户描述"我希望/想要 AI 怎样"归期望特质，"记住/别忘/以后都要"归记忆；两者内容不得互换或混填。
 10. 格式严格——只输出上述章节与字段；不得新增任何章节或字段，缺失字段视为剖析失败。

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
        sb.append("【剖析要求】你的任务是把下面的描述剖析、归位到字段里，而不是扩写或拉长它：")
        sb.append("用户亲口说过的设定逐字保留；没说的不要编造；字数上限只是天花板，不是目标。\n\n")
        sb.append("【视角要求】所有字段以第三人称客观视角书写，禁止出现「你」「我」；")
        sb.append("[名字]用 2～4 字正常人名；[当前场景]保持 1～2 句简洁。\n\n")

        sb.append("【本次需生成的字段清单】\n")
        sb.append("AI 人设：")
        sb.append(tier.aiPersonaFields().joinToString("、") { it.label })
        sb.append("\n")
        sb.append("用户人设：[用户名字]、[用户身份]、[用户性别]、[用户年龄]、[用户外观]\n")
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

        val rawMemory = if (tier.includesMemory) {
            extractField(memorySection, "[需要记住的事]").trim()
        } else ""
        // 纠偏：模型偶发把「期望特质」与「记忆」内容填错位，解析后按句式自动归位
        val (finalDesired, finalMemory) = reclassifyMisplacedFields(
            desired = desired,
            memory = rawMemory,
            tier = tier
        )

        return QuickSetupResult(
            persona = Persona(
                name = name,
                desired = finalDesired,
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
            memory = finalMemory
        )
    }

    /**
     * 期望特质 / 记忆错位纠偏：按句式把误填的内容归位。
     *
     * - 「我希望/想要/要对我/说话要…」这类期望表达误填进记忆 → 移回 [期望特质]；
     * - 「记住/别忘了/以后都要/喜欢喝…」这类记忆事实误填进期望特质 → 移回 [需要记住的事]。
     * 长句先按句末标点 / 逗号 / 顿号切成子句再逐句判定；同时含两类句式的子句
     * 归属记忆（「记住」是更强的指令），不强行拆分。
     */
    private fun reclassifyMisplacedFields(
        desired: String,
        memory: String,
        tier: QuickSetupTier
    ): Pair<String, String> {
        if (!tier.includesMemory) return desired to ""
        var desiredOut = desired
        var memoryOut = memory
        val memorySentences = splitSentences(memoryOut)
        if (memorySentences.isNotEmpty()) {
            val (keep, move) = memorySentences.partition { !isDesiredStyleSentence(it) }
            if (move.isNotEmpty()) {
                memoryOut = keep.joinToString("\n")
                desiredOut = joinFields(desiredOut, move)
            }
        }
        val desiredSentences = splitSentences(desiredOut)
        if (desiredSentences.isNotEmpty()) {
            val (keep, move) = desiredSentences.partition { !isMemoryStyleSentence(it) }
            if (move.isNotEmpty()) {
                desiredOut = keep.joinToString("\n")
                memoryOut = joinFields(memoryOut, move)
            }
        }
        return desiredOut to memoryOut
    }

    private fun splitSentences(text: String): List<String> =
        text.split(Regex("(?<=[。！？；;，、])\\s*|\\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

    private fun joinFields(original: String, extra: List<String>): String =
        listOf(original, extra.joinToString("\n"))
            .filter { it.isNotBlank() }
            .joinToString("\n")

    private fun isDesiredStyleSentence(sentence: String): Boolean {
        val hasDesired = DESIRED_STYLE_MARKERS.any { sentence.contains(it) }
        val hasMemory = MEMORY_STYLE_MARKERS.any { sentence.contains(it) }
        return hasDesired && !hasMemory
    }

    private fun isMemoryStyleSentence(sentence: String): Boolean {
        val hasDesired = DESIRED_STYLE_MARKERS.any { sentence.contains(it) }
        val hasMemory = MEMORY_STYLE_MARKERS.any { sentence.contains(it) }
        return hasMemory && !hasDesired
    }

    private val DESIRED_STYLE_MARKERS = listOf(
        "希望", "想要", "想让你", "要对我", "请对我", "对我", "多陪", "多关心", "多宠",
        "多夸", "哄我", "说话方式", "语气词", "称呼我", "叫我", "别凶", "不要凶", "请温柔"
    )

    private val MEMORY_STYLE_MARKERS = listOf(
        "记住", "别忘", "别忘了", "不要忘", "以后都要", "以后都", "习惯", "生日",
        "名字叫", "喜欢喝", "喜欢吃", "最爱", "讨厌", "每周", "每天", "提醒我", "要记得"
    )

    private fun extractSection(text: String, startMarker: String, endMarker: String?): String {
        val startIdx = findSectionIndex(text, startMarker)
        if (startIdx < 0) return ""
        val contentStart = startIdx + startMarker.length
        val endIdx = if (endMarker != null) {
            findSectionIndex(text, endMarker, contentStart)
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
        // 只识别已知字段标签：正文内容里的「[xx]」不再是误切分点，
        // 修复模型在字段内容里写方括号导致内容被截断/错位的问题
        return allFieldLabels
            .mapNotNull { label -> section.indexOf(label, from).takeIf { it >= 0 } }
            .minOrNull()
            ?: -1
    }

    /**
     * 容错定位分节符：允许章节名内部出现空格（如「【AI 人设】」），
     * 也允许旧模型把分节符写成「【AI人设】」。按「去掉空白后相等」匹配。
     */
    private fun findSectionIndex(text: String, marker: String, from: Int = 0): Int {
        val normalizedMarker = marker.filterNot { it.isWhitespace() }
        var idx = from
        while (idx < text.length) {
            val next = text.indexOf('【', idx)
            if (next < 0) return -1
            val end = text.indexOf('】', next)
            if (end < 0) return -1
            val section = text.substring(next, end + 1).filterNot { it.isWhitespace() }
            if (section == normalizedMarker) return next
            idx = end + 1
        }
        return -1
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
