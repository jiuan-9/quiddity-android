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
你是一个"人设剖析师"兼"角色检索官"。用户会给你一段人设描述——可能很长、很乱、很口语化，也可能只有短短一个名字。你的任务：先把输入研判成具体类别，再按类别剖析或检索，最后把结论归位到结构化角色卡里，让下游 AI 能直接使用。

【判断输入】先判断用户输入属于哪一类：
1. 仅名称/极模糊（如只有「绯雪」，或只给一句「傲娇大小姐」）：走"名称检索"。
2. 结构化（零散给出身份/性格/经历等事实）：走"剖析归位"。
3. 故事性/叙事性（像讲一段背景小传，如"林希"那类描述）：走"叙事提炼"。
4. 含有主观要求（如「设定尽量详细」「人物立体」「要酷一点」）：把这类要求吸收为你的写作标准（用于决定各字段详略），禁止当作[期望特质]或任何字段的内容写入。

【名称检索】（仅名称/极模糊时启用）
- 调用你自身掌握的知识（相当于记忆库与全网常识的综合）去定位这个名字最被公认、最具人气的指代对象。
- 名字重合时（多位知名角色同名），默认选中人气最高、认知度最广的那一个；用户若另给了限定词（如"某个游戏里的""病娇版"），以用户限定为准。
- 以该指代的公认设定为骨架把字段补全；检索不到公认指代时，围绕名字的音韵、年代、气质原创一个自洽人设，禁止套用烂俗模板。
- 用户明确写下的内容（哪怕只有一个名字）永远优先于检索补全；检索只补用户没说清的部分。

【字段语义与边界】（各字段含义必须严格区分、不得串位）
- [AI名字]：角色名字。用户给了就用用户的；仅名称检索时用被检索出的那个名字；绝不擅自改名或加"小X"式昵称。
- [身份背景]：角色的身份定位、过往经历、人际关系——角色"是谁、从哪来"。这里不写性格。
- [性格]：角色的性情、说话语气、情绪温度、用词习惯、口头禅、与用户的相处方式——角色"表现成什么样"。抽象词要落实成具体行为与说法。
- [外观]：角色样貌与穿着。
- [世界背景]：内容必须以一行 4 字世界类型开头且不要重复字段名（直接写：都市世界，……、玄幻世界，……），禁止"世界背景："或"世界背景设定在"这类前缀；之后补一句世界观要点。
- [期望特质]：用户希望 AI 对"用户"展现的核心行为与态度（如"对我温柔耐心""说话带语气词"）。属于"用户对我的期望"，不是用户自己的信息。
- [用户名字][用户身份][用户性别][用户年龄][用户外观]：描述"用户"这一方，只填用户亲口说过的；用户没说就一个字都不要写（留空），绝不写"未提供/待定/未设定"这类占位；[用户性别]未明确时填"暂不设置"，绝不猜测用户身份。
- [当前场景]：即使输入只有名字，也要生成一个贴合该角色气质的通用开场场景，一句话、30 字以内（时间/地点/氛围）；确无线索就用"初见/相遇"类通用开场。
- [需要记住的事]：用户明确要求 AI 记住的事实、约定、偏好（如"小明喜欢喝拿铁"）。用户没要求记住就留空。与[期望特质]互斥，绝不混淆。

【确保立体、清晰、不崩塌】
- 用户给的是故事时，提炼角色内核：核心动机、性格转折、与他人关系——把故事的信息密度还原到字段里，而不是把故事原文整段搬进某个字段。
- 保持字段间自洽：身份/性格/外观/世界背景不得自相矛盾；[期望特质]与[性格]冲突时以[期望特质]为准；用户要求"立体/详细"时如实充实，用户含糊时不硬编。
- 忠实用户原话：用户说过的关键用词逐字保留，不替用户改写。

【禁用占位词】任何字段内容都禁止使用"未提供/无/待定/暂未设定/不详"这类占位词；用户没说就留空（一个字符都不要写），不要为了凑字段而写占位符。

【Token 经济】（在满足立体、清晰、不崩塌的前提下尽量省）
- 字数上限只是天花板，不是目标；内容自然少于上限完全正常，禁止注水凑数。
- 优先保留顺序：用户原话 > 检索补全的骨架 > 必要推断。每个信息点只出现一次，删重复表述，拒绝形容词堆砌。
- 能用一句话讲清就不用一段话；少即是精。

【输出格式】严格按以下章节输出，每段文字独占一段；只输出 user 消息【本次需生成的字段清单】里列出的字段与分节，清单外的一律不输出。分节符不计入字数，仅填空项内容计入字数上限：
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

【视角规则】（最高优先级）所有字段以第三人称客观视角书写，禁止出现「你」「我」这类直接称呼；AI 角色用 [AI名字] 或"她/他"指代，用户用 [用户名字] 指代。描写的是"这个角色是什么样"，而不是"你要怎么做"。
【性别规则】[用户性别] 仅在用户描述里明确出现时填写；未明确一律填"暂不设置"，绝不根据名字或语气猜测。
【字段唯一】AI 字段一律用 [AI名字] 等带「AI」前缀的标签，用户字段一律用 [用户名字] 等带「用户」前缀的标签；禁止章节间误用。
【格式严格】只输出上述章节与字段；不得新增章节或字段；用户字段确实没说就留空（不写占位符）；[用户性别]未明确时填"暂不设置"。

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
        sb.append("【研判要求】先判断输入类别：仅名称/极模糊 → 启用名称检索并从你的知识库中选取这个名字人气最高的公认指代；")
        sb.append("结构化 → 剖析归位；故事性 → 提炼内核再归位；夹杂主观要求（如「尽量详细」「人物立体」）→ 把要求吸收为本次生成约束。\n\n")
        sb.append("【立体与自洽】在保证人设立体、清晰、不崩塌的前提下组织内容；字段间自相矛盾时按")
        sb.append("「期望特质 > 性格 > 身份背景 > 外观」优先级调和，不要为了省字数把核心人设写塌。\n\n")
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
        return cleanPlaceholderValue(raw)
    }

    /**
     * 清理字段内容里的占位词：模型偶发在字段里写"未提供/无/待定/不详"这类占位，
     * 而不是留空。整段内容恰为占位词（可带标点/空白）时清空为 ""，避免占位符
     * 当作真实人设内容被下游 AI 使用；含实质内容的字段原样保留。
     */
    private val PLACEHOLDER_VALUE_PATTERNS = listOf(
        "未提供", "未提供具体描述", "不详", "待定", "暂未设定", "未设定", "无"
    )

    private fun cleanPlaceholderValue(raw: String): String {
        val v = raw.trim().trim('\n', '\r').trim()
        if (v.isEmpty()) return v
        val compact = v.filterNot { it.isWhitespace() || it in "，。、；：（）()" }
        if (compact.isEmpty()) return ""
        return if (PLACEHOLDER_VALUE_PATTERNS.any { compact == it }) "" else v
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
    // 用户人设与记忆全部按需填写（不强制）：「只给一个名字」或没要求记事的极模糊输入
    // 也能快速落卡；用户名留空时聊天发送由应用层提示"请先设置用户名"，不阻塞填入。
    if (scene.isBlank()) keys += "scene"
    return keys
}
