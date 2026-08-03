package com.quiddity.app.domain

import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.MemoryCompressionResult
import com.quiddity.app.data.model.Role
import com.quiddity.app.data.remote.ChatMessage
import com.quiddity.app.data.remote.ToolDefinition
import com.quiddity.app.data.remote.ToolFunction
import com.quiddity.app.util.QuiddityConstants
import com.quiddity.app.util.TokenEstimator
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
 * 提示词中枢：统一管理所有发给 LLM 的提示词，分为两大类。
 *
 * 一、功能类——与具体人设无关的工具型提示词：记忆压缩 / 人设精调。
 * 二、人设类——聊天 system 提示词：AI 人设 / 用户人设 / 场景 / 记忆。
 *
 * 字段命名规范（全文件统一，跨提示词一致，对准应用内设置填空项）：
 * - AI 人设：【名字】【身份背景】【性格】【外观】【世界背景】【期望特质】
 * - 用户人设（【对话伙伴信息】下）：名字 / 身份 / 性别 / 年龄 / 外观
 * - 其他：【当前场景】【历史对话摘要】【需要记住的事】
 *
 * 多消息切分不再由提示词驱动：[MessageStreamCoordinator] 在流式输出阶段按句末标点 +
 * 括号确定性切分，无需告知 LLM 任何分割标记或规则。
 */
object PromptBuilder {

    // ===== 三条开发规范（位于文件中间位置） =====
    // 1. 问题修复规范：所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
    //    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
    // 2. 代码注释规范：文件内仅允许保留两类注释——当前规则说明注释与模块划分注释（// ===== xxx =====）。
    //    除此之外，禁止出现任何形式的代码注释（包括单行 / 多行 / 临时调试注释）。
    // 3. 构建交付要求：完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK。
    //    APK 需满足：签名有效且符合发布标准、包含完整功能模块、经过基础性能与兼容性测试。

    // ============================================================
    // 一、功能类提示词（记忆压缩 / 人设精调）
    // ============================================================

    // 当前规则：压缩与精调各自拥有独立 system 提示词，互不复用——压缩要忠实提取，精调要化模糊为精确，二者指令语义冲突，混用会导致压缩时 AI 误发挥。

    /**
     * 记忆压缩器 system 提示词。
     *
     * 定位为"对话记忆压缩器"：只做忠实提取与去冗余，禁止任何发挥、补全或猜测。
     * 与 [PERSONA_REFINE_SYSTEM_PROMPT] 完全分离，修复了过去压缩复用"人设编译器"指令的语义错配。
     *
     * 6.5.2 两段化：输出「【摘要】 + 【索引】」两段，解析后分别写入
     * [Conversation.compressedMemory] 与 [Conversation.memoryIndex]。
     */
    internal val COMPRESSION_SYSTEM_PROMPT = """
你是一个"对话记忆压缩器"。你的任务是从对话历史中提取关键信息，输出一份紧凑、准确、无歧义的记忆摘要，供 AI 在后续对话中参考。

核心原则：
1. 忠实提取——只保留原文出现的信息，严禁添加、猜测、推断或补全。
2. 精确保留——数字、ID、日期、金额、专有名词、人名、地名等必须一字不差。
3. 删除冗余——修饰词、客套话、寒暄、重复解释、语气词全部删掉。
4. 消除指代——把「它」「那个」「这个」「他」等代词替换为具体名词。
5. 紧凑输出——摘要是一段连续文本，不使用列表或标题格式。

输出格式（严格遵守，只输出以下两段）：
【摘要】
<一段连续的完整摘要>
【索引】
<一行索引：20 字以内的主题 + 逗号分隔的关键词，不要包含轮次、日期等时间范围信息>
""".trim()

    /**
     * 解析两段式压缩结果（6.5.2 解析规则）。
     *
     * 1. 按行扫描，取**行首为 `【索引】`** 的那一行作为分隔：其前的所有行为摘要段，其后紧跟的一行为索引段。
     * 2. 摘要段 → [MemoryCompressionResult.summary]；索引段 → [MemoryCompressionResult.index]。
     * 3. 找不到 `【索引】` 分隔（模型只输出了一段）→ 整段视为摘要；索引回退为「摘要前 80 字」。
     * 4. 摘要段为空 → 本次压缩视为失败（[MemoryCompressionResult.success] = false），两字段都保持旧值。
     */
    fun parseCompressionResult(raw: String): MemoryCompressionResult {
        val text = raw.trim()
        if (text.isBlank()) {
            return MemoryCompressionResult(summary = "", index = "", success = false)
        }
        val lines = text.lines()
        val separatorIndex = lines.indexOfFirst { it.trim().startsWith("【索引】") }
        return if (separatorIndex >= 0) {
            val rawSummary = lines.subList(0, separatorIndex).joinToString("\n").trim()
            // 输出格式中的「【摘要】」是节标题而非内容：剥离首行标题，摘要段只保留正文
            // （compressedMemory 行为与今天一致）
            val summary = if (rawSummary.lineSequence().firstOrNull()?.trim() == "【摘要】") {
                rawSummary.lineSequence().drop(1).joinToString("\n").trim()
            } else {
                rawSummary
            }
            val modelIndex = lines.drop(separatorIndex + 1).firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            if (summary.isBlank()) {
                MemoryCompressionResult(summary = "", index = "", success = false)
            } else {
                // 回退规则：索引缺失/为空时用摘要前 80 字临时顶替，下次压缩补齐
                val index = modelIndex.ifBlank {
                    summary.take(QuiddityConstants.MEMORY_INDEX_FALLBACK_CHARS)
                }
                MemoryCompressionResult(summary = summary, index = index, success = true)
            }
        } else {
            MemoryCompressionResult(
                summary = text,
                index = text.take(QuiddityConstants.MEMORY_INDEX_FALLBACK_CHARS),
                success = true
            )
        }
    }

    /**
     * 构造记忆压缩的 user 消息：上次压缩摘要（若有）+ 本次待压缩对话。
     *
     * @param previousSummary 上一次的压缩摘要（[Conversation.compressedMemory]），为空则不附加
     * @param newMessages 本次需要压缩的消息（调用方负责按 [Conversation.lastCompressedAtRound] 过滤）
     */
    fun buildCompressionUserPrompt(previousSummary: String, newMessages: List<Message>): String {
        val sb = StringBuilder()
        if (previousSummary.isNotBlank()) {
            sb.append("【上一次的压缩摘要】\n").append(previousSummary).append("\n\n")
        }
        sb.append("【本次需要压缩的对话】\n")
        newMessages.forEach { msg ->
            val label = when (msg.role) {
                Role.USER -> "用户"
                Role.ASSISTANT -> "AI"
                Role.SYSTEM -> "系统"
            }
            sb.append("[").append(label).append("] ").append(msg.content).append("\n")
        }
        return sb.toString().trim()
    }

    /**
     * 人设精调器 system 提示词。
     *
     * 精调项严格对准应用内设置填空项：身份背景 / 性格 / 外观 / 期望特质。
     * 「名字」「世界背景」不参与精调——由 [buildSystemPrompt] 在精调结果之外直接透传；
     * 不输出"行为/规则/准则"等任何填空项之外的项目。
     */
    internal val PERSONA_REFINE_SYSTEM_PROMPT = """
你是一个"人设精调器"。你的任务是理解用户对 AI 角色的描述，将其精调为一份让下游 AI 能精准扮演目标角色的结构化系统指令。

精调项严格对准应用内的设置填空项，只输出以下四节（用户未填的字段省略对应章节）。不要输出"名字""世界背景"（由系统单独处理），也不要输出"行为""规则""准则"等任何填空项之外的项目：

【身份背景】概述角色的身份定位和背景设定。

【性格】描述说话的语气、情感温度、用词习惯和节奏。重点是将模糊的形容词转化为具体的行为和语言描述。例如"温柔"应写成"说话轻声细语，像春日暖风，多用'呢''哦''呀'等语气词，语速不紧不慢，从不急躁或咄咄逼人"。例如"幽默"应写成"偶尔插入轻松自嘲和调侃，但注意分寸，不说冒犯性的笑话"。

【外观】角色的外观与穿着描述。

【期望特质】把用户对 AI 最核心的期望落实为具体、可执行的角色表现指引。

核心精调原则（按优先级执行）：
1. **期望特质至高无上**——用户填写的"期望特质"是最高指令，一切输出都围绕它展开；其他字段与其冲突时，无条件以期望特质为准修正。
2. **化模糊为精确**——绝不允许输出中出现模糊词，"温柔""活泼""可爱"等形容词必须展开为具体的行为和语言描述。
3. **去冗余提纯**——删掉重复、无意义的表述，每个信息点只出现一次。
4. **忠于填空项**——只精调上述四项用户填写的内容，不得凭空补全用户未填的字段，不得新增任何填空项之外的项目。
5. **冲突调和**——字段间存在矛盾时，按 期望特质 > 性格 > 身份背景 > 外观 的优先级解决，保持自洽。

输出纯文本，不要任何解释性文字。现在开始精调用户的人设输入。
""".trim()

    /**
     * 构造人设精调的 user 消息。
     *
     * 参与精调的字段（与 system 提示词输出四节一一对应）：期望特质 / 身份背景 / 性格 / 外观。
     * 不参与精调的字段：名字、世界背景（由 [buildSystemPrompt] 直接透传，不发送给精调器）。
     * 缺省字段自动跳过，整体为空时返回空串（调用方据此判定"无需精调"）。
     */
    fun buildPersonaRefineInput(persona: com.quiddity.app.data.model.Persona): String {
        val parts = mutableListOf<String>()
        if (persona.desired.isNotBlank()) {
            parts.add(
                "【期望特质】（最高优先级，以此为基础精调，不得曲解原意）" +
                    "这是用户对 AI 最核心的期望：" + persona.desired
            )
        }
        if (persona.persona.isNotBlank()) parts.add("【身份背景】" + persona.persona)
        if (persona.character.isNotBlank()) parts.add("【性格】" + persona.character)
        if (persona.appearance.isNotBlank()) parts.add("【外观】" + persona.appearance)
        return parts.joinToString("\n").trim()
    }

    /**
     * 精调 user 消息的尾部约束：要求按结构输出并限制长度。
     */
    fun buildPersonaRefineSuffix(maxOutputTokens: Int): String {
        return "\n\n请在以上信息的基础上进行人设精调，输出一段结构清晰、不曲解原意的系统提示词。" +
            "输出字数限制在 " + maxOutputTokens + " token 以内。"
    }

    // ============================================================
    // 二、人设类提示词（聊天 system + 开场引导）
    // ============================================================

    /**
     * AI 开场引导消息（空对话时作为 user 消息，仅提示 AI 主动说出第一句）。
     */
    const val LET_AI_START_GUIDE = "（请主动开启第一句）"

    /**
     * 组装聊天 system 提示词。
     *
     * 组装顺序：
     * 1. AI 人设（精调结果优先；精调结果不含名字/世界背景，需单独透传，修复过去精调后两字段丢失的问题）
     * 2. 用户人设
     * 3. 场景
     * 4. 记忆（6.4 随身带 / 6.5 小抄两种组装，按记忆策略分支）
     *
     * @param memoryStrategy 记忆策略覆盖值（null = 跟随 [Conversation.memoryStrategy]，
     *   仍为 null 时回退为随身带现状）。完整级默认策略由 2.0.0 运行时按模型分级解析后传入。
     */
    fun buildSystemPrompt(
        conv: Conversation,
        memoryStrategy: String? = null
    ): String {
        val sb = StringBuilder()

        // ===== 1. AI 人设 =====
        val persona = conv.persona
        if (conv.compileEnabled && !persona.compiledPersona.isNullOrBlank()) {
            // 精调结果：身份背景 / 性格 / 外观 / 期望特质
            sb.append(persona.compiledPersona).append("\n\n")
            // 名字、世界背景不参与精调，直接透传
            if (persona.name.isNotBlank()) {
                sb.append("【名字】").append(persona.name).append("\n\n")
            }
            if (persona.worldBackground.isNotBlank()) {
                sb.append("【世界背景】").append(persona.worldBackground).append("\n\n")
            }
        } else {
            // 未精调：原始字段直接拼接（身份背景为空时回退默认身份，确保 AI 有角色定位）
            if (persona.name.isNotBlank()) {
                sb.append("【名字】").append(persona.name).append("\n\n")
            }
            val effectivePersona = persona.persona.ifBlank { QuiddityConstants.DEFAULT_AI_IDENTITY }
            if (effectivePersona.isNotBlank()) {
                sb.append("【身份背景】\n").append(effectivePersona).append("\n\n")
            }
            if (persona.character.isNotBlank()) {
                sb.append("【性格】").append(persona.character).append("\n\n")
            }
            if (persona.appearance.isNotBlank()) {
                sb.append("【外观】").append(persona.appearance).append("\n\n")
            }
            if (persona.worldBackground.isNotBlank()) {
                sb.append("【世界背景】").append(persona.worldBackground).append("\n\n")
            }
            if (persona.desired.isNotBlank()) {
                sb.append("【期望特质】\n").append(persona.desired).append("\n\n")
            }
        }

        // ===== 2. 用户人设 =====
        val user = conv.userPersona
        if (user.name.isNotBlank() || user.identity.isNotBlank() || user.gender.isNotBlank()
            || user.age.isNotBlank() || user.appearance.isNotBlank()) {
            sb.append("【对话伙伴信息】\n")
            if (user.name.isNotBlank()) sb.append("- 名字：").append(user.name).append("\n")
            if (user.identity.isNotBlank()) sb.append("- 身份：").append(user.identity).append("\n")
            if (user.gender.isNotBlank()) sb.append("- 性别：").append(user.gender).append("\n")
            if (user.age.isNotBlank()) sb.append("- 年龄：").append(user.age).append("\n")
            if (user.appearance.isNotBlank()) sb.append("- 外观：").append(user.appearance).append("\n")
            sb.append("\n")
        }

        // ===== 3. 场景 =====
        // 当前规则：场景仅在首轮注入（sceneInjected=false）。注入一次后由对话上文延续场景，
        // 避免反复重发静态场景导致 LLM 跑回最开始场景（场景崩塌）。场景被修改时 sceneInjected 重置为 false。
        if (conv.scene.isNotBlank() && !conv.sceneInjected) {
            sb.append("【当前场景】\n").append(conv.scene).append("\n\n")
        }

        // ===== 4. 记忆（6.4 随身带 / 6.5 小抄两种组装） =====
        val effectiveStrategy = memoryStrategy
            ?: conv.memoryStrategy
            ?: QuiddityConstants.MEMORY_STRATEGY_CARRY
        if (effectiveStrategy == QuiddityConstants.MEMORY_STRATEGY_TOOL && conv.compressedMemory.isNotBlank()) {
            // 6.5.1 小抄：固定记忆全文 + 一行索引（完整内容可用 read_memory 工具读取）
            if (conv.memory.isNotBlank()) {
                sb.append("【需要记住的事】\n").append(conv.memory).append("\n\n")
            }
            val historyIndex = conv.memoryIndex.ifBlank {
                conv.compressedMemory.take(QuiddityConstants.MEMORY_INDEX_FALLBACK_CHARS)
            }
            sb.append("【记忆索引】\n")
            sb.append("历史摘要：").append(historyIndex).append("\n")
            if (conv.groupMemory.isNotBlank()) {
                sb.append("群聊记忆：").append(conv.groupMemory.take(QuiddityConstants.MEMORY_INDEX_FALLBACK_CHARS)).append("\n")
            }
            sb.append("（完整内容可用 read_memory 工具读取；查找原始聊天记录可用 search_chat 工具）\n\n")
        } else {
            // 6.4 随身带（现状）：压缩摘要全文 + 固定记忆全文
            if (conv.compressedMemory.isNotBlank()) {
                sb.append("【历史对话摘要】\n").append(conv.compressedMemory).append("\n\n")
            }
            if (conv.memory.isNotBlank()) {
                sb.append("【需要记住的事】\n").append(conv.memory).append("\n\n")
            }
        }

        // ===== 5. 时间库说明（主动消息开启且有查看密码时） =====
        // 让 AI 确切知道时间库查看密码与告知状态，避免在对话中编造错误密码
        if (conv.activeMessageEnabled && conv.timeLibraryPassword.isNotBlank()) {
            sb.append("【时间库查看密码】\n")
            sb.append("本会话今日时间库的查看密码是：").append(conv.timeLibraryPassword).append("\n")
            if (conv.timeLibraryPasswordRevealed) {
                sb.append("你已告知用户该密码；用户问起时可以如实回答。\n")
            } else {
            sb.append("你尚未主动告知用户该密码；不要主动提起，但用户直接询问时可以如实回答。\n")
            }
            sb.append("\n")
        }

        return sb.toString().trim()
    }

    /**
     * 把消息列表转为 ChatMessage（含 system）。
     *
     * @param senderLabels 群聊发言人标签映射（senderId → 名字，6.5.3 群聊转述可区分说话人）。
     *   仅当映射非空且消息带 senderId 时给内容加 `[名字] ` 前缀；私聊/空映射行为不变。
     */
    fun toApiMessages(
        systemPrompt: String,
        history: List<Message>,
        senderLabels: Map<String, String> = emptyMap()
    ): List<ChatMessage> {
        val result = mutableListOf<ChatMessage>()
        if (systemPrompt.isNotBlank()) {
            result.add(ChatMessage(role = "system", content = systemPrompt))
        }
        history.forEach { msg ->
            val role = when (msg.role) {
                Role.USER -> "user"
                Role.ASSISTANT -> "assistant"
                Role.SYSTEM -> "system"
            }
            val content = if (msg.senderId != null && senderLabels.isNotEmpty()) {
                val label = senderLabels[msg.senderId] ?: msg.senderId
                "[$label] ${msg.content}"
            } else {
                msg.content
            }
            result.add(ChatMessage(role = role, content = content))
        }
        return result
    }

    // ============================================================
    // 三、主动消息提示词（时间库生成 + 发送决策）
    // ============================================================

    /**
     * 时间库生成器 system 提示词。
     *
     * 定位为"时间库制定者"：只输出 24 小时制时间列表，最多 5 个，允许为空。
     * 对应算法文档 3.2 生成规则——由 LLM 根据人设和上下文自主把握时间点。
     */
    internal val TIME_LIBRARY_SYSTEM_PROMPT = """
    你是一个"时间库制定者"。
    你的任务：根据 AI 的人设和过往对话记录，制定当天 AI 可能主动向用户发消息的时间列表。
    规则：
    1. 仅输出时间列表，24 小时制，精确到分钟，一行一个，格式如 13:30。
    2. 最多输出 5 个时间点；可以少于 5 个；若认为当天不需要主动发消息，输出空列表。
    3. 时间点之间无强制间隔限制，由你根据人设和上下文自行把握。
    4. 时间列表结束后，另起一行输出【查看密码】和一组 4~6 位纯数字密码（仅数字，如 520131，
       不要包含冒号或其它字符），再另起一行输出【是否告知】后跟"是"或"否"。
查看密码仅用于查看当天时间库；如果选择"是"，App 会用这条密码替你在会话里告知用户，
因此密码必须严格按你输出的这一组数字记录，不能随意更改。密码不需要唯一，可以是任意数字组合。
    5. 除时间列表、查看密码、是否告知外，不要输出任何解释、标点或其它内容。
""".trim()

    /**
     * 构造时间库生成的 user 消息。
     * 输入依据（对应算法文档 3.2）：该会话的人设 + 该会话的压缩聊天记录。
     */
    fun buildTimeLibraryUserPrompt(conv: Conversation, compressedHistory: String): String {
        val sb = StringBuilder()
        sb.append("【AI 人设】\n").append(buildPersonaSnippet(conv)).append("\n\n")
        if (compressedHistory.isNotBlank()) {
            sb.append("【压缩聊天记录】\n").append(compressedHistory).append("\n\n")
        }
        sb.append("请输出今天 AI 可能主动发消息的时间列表：")
        return sb.toString().trim()
    }

    /**
     * 发送决策 system 提示词：人设 + 决策规则。
     *
     * 对应算法文档 5.2——LLM 根据人设和上下文自主决定是否发送。
     * 返回值约定：严格等于独立数字 0 → 拦截不发送；包含任何其他内容 → 需要发送（该内容即消息）。
     */
    internal fun buildDecisionSystemPrompt(conv: Conversation): String {
        val persona = buildPersonaSnippet(conv).ifBlank { QuiddityConstants.DEFAULT_AI_IDENTITY }
        return """
你现在扮演以下人设的 AI 角色：
$persona

此刻是主动消息触发时间点。请根据你的人设和与用户的聊天记录，自主决定是否主动向用户发一条消息。
规则：
1. 若你认为应该主动发消息：直接输出想要发送的消息内容，以你的身份口吻，自然贴合人设与聊天上下文，不要任何前缀、解释或引号。
2. 若你认为此刻不应主动发消息（如无话可说、时机不合适）：严格只输出数字 0（仅一个 0，无任何其它字符）。
""".trim()
    }

    /**
     * 构造发送决策的 user 消息。
     * 输入依据（对应算法文档 5.2）：该会话的人设（已放入 system）+ 未压缩的聊天记录 + 当前触发的时间点。
     */
    fun buildDecisionUserPrompt(history: List<Message>, timePoint: String): String {
        val sb = StringBuilder()
        sb.append("当前触发时间点：").append(timePoint).append("\n\n")
        sb.append("【聊天记录】\n")
        if (history.isEmpty()) {
            sb.append("（无聊天记录）")
        } else {
            history.forEach { msg ->
                val label = when (msg.role) {
                    Role.USER -> "用户"
                    Role.ASSISTANT -> "AI"
                    Role.SYSTEM -> "系统"
                }
                sb.append("[").append(label).append("] ").append(msg.content).append("\n")
            }
        }
        sb.append("\n请决定此刻是否主动发消息。")
        return sb.toString().trim()
    }

    /**
     * 提取人设要点文本（主动消息共用）。
     * - 已精调时使用精调结果 + 名字 / 世界背景透传
     * - 未精调时拼接原始字段（身份背景为空回退默认身份）
     */
    internal fun buildPersonaSnippet(conv: Conversation): String {
        val p = conv.persona
        val sb = StringBuilder()
        if (conv.compileEnabled && !p.compiledPersona.isNullOrBlank()) {
            sb.append(p.compiledPersona)
            if (p.name.isNotBlank()) sb.append("\n【名字】").append(p.name)
            if (p.worldBackground.isNotBlank()) sb.append("\n【世界背景】").append(p.worldBackground)
        } else {
            if (p.name.isNotBlank()) sb.append("【名字】").append(p.name).append("\n")
            sb.append("【身份背景】").append(p.persona.ifBlank { QuiddityConstants.DEFAULT_AI_IDENTITY }).append("\n")
            if (p.character.isNotBlank()) sb.append("【性格】").append(p.character).append("\n")
            if (p.appearance.isNotBlank()) sb.append("【外观】").append(p.appearance).append("\n")
            if (p.worldBackground.isNotBlank()) sb.append("【世界背景】").append(p.worldBackground).append("\n")
            if (p.desired.isNotBlank()) sb.append("【期望特质】").append(p.desired).append("\n")
        }
        return sb.toString().trim()
    }

    // ============================================================
    // 四、群聊提示词 + 记忆调用式工具（2.0.0 接口预留；1.3.0 群聊实体不加入）
    // ============================================================

    /**
     * 群聊小本本压缩器 system 提示词（6.7）。
     *
     * 复用 6.5.2 的两段输出格式：摘要 → [Conversation.groupMemory]，
     * 索引 → 小抄里的「群聊记忆」一行。摘要体积上限对齐 [QuiddityConstants.GROUP_MEMORY_MAX_TOKENS]。
     */
    internal val GROUP_MEMORY_SYSTEM_PROMPT = """
你是一个"群聊记忆记录员"。你的任务是从群聊对话中提取关键信息，输出一份紧凑、准确、无歧义的群聊小本本，供 AI 成员在后续群聊中参考。

核心原则：
1. 忠实提取——只保留原文出现的信息，严禁添加、猜测、推断或补全。
2. 精确保留——数字、ID、日期、金额、专有名词、人名、地名等必须一字不差。
3. 删除冗余——修饰词、客套话、寒暄、重复解释、语气词全部删掉。
4. 消除指代——把「它」「那个」「这个」「他」等代词替换为具体名词。
5. 紧凑输出——摘要是一段连续文本，不使用列表或标题格式，全文控制在 1000 token 以内。

输出格式（严格遵守，只输出以下两段）：
【摘要】
<一段连续的完整摘要>
【索引】
<一行索引：20 字以内的主题 + 逗号分隔的关键词，不要包含轮次、日期等时间范围信息>
""".trim()

    /**
     * 构造群聊成员 system 提示词（4.2）：成员人设 + 群规则。
     */
    fun buildGroupSystemPrompt(member: Conversation, groupRules: String): String {
        val sb = StringBuilder()
        sb.append(buildPersonaSnippet(member)).append("\n\n")
        if (groupRules.isNotBlank()) {
            sb.append("【群聊规则】\n").append(groupRules)
        }
        return sb.toString().trim()
    }

    /**
     * 构造群聊转述文本（4.2）：`[名字] 内容` 格式，只给最近 [lastN] 条（5.2）。
     *
     * @param senderNames 成员会话 id → 名字映射；未映射的 senderId 直接显示 id，无 senderId 显示「未知成员」（3.3）。
     * @param lastN 保留最近 N 条；<= 0 表示全部。
     */
    fun buildGroupTranscript(
        messages: List<Message>,
        lastN: Int,
        senderNames: Map<String, String> = emptyMap()
    ): String {
        val effective = if (lastN > 0 && messages.size > lastN) messages.takeLast(lastN) else messages
        return effective.joinToString("\n") { msg ->
            val name = msg.senderId?.let { senderNames[it] ?: it } ?: "未知成员"
            "[$name] ${msg.content}"
        }
    }

    /**
     * 构造"该不该我接话"的判断指令（4.2）：成员人设 + 群聊转述 + 输出约束。
     *
     * 返回值约定（5.2）：严格等于独立数字 0 → 不接话；包含任何其他内容 → 该内容即要说的消息。
     * 调用方用 [QuiddityConstants.GROUP_DECIDE_MAX_TOKENS] 限制输出。
     */
    fun buildGroupDecisionPrompt(member: Conversation, transcript: String): String {
        val persona = buildPersonaSnippet(member).ifBlank { QuiddityConstants.DEFAULT_AI_IDENTITY }
        return """
你现在扮演以下人设的 AI 角色：
$persona

你正在参与一场群聊。请在阅读群聊转述后判断是否该由你自然接话。
规则：
1. 若你应当接话：直接输出你想说的内容，以你的身份口吻，贴合人设与群聊上下文，不要任何前缀、解释或引号。
2. 若你不应接话：严格只输出数字 0（仅一个 0，无任何其它字符）。

【群聊转述】
$transcript
""".trim()
    }

    /**
     * 构造群聊小本本压缩的 user 消息（6.7）：待压缩的群聊转述。
     * system 提示词为 [GROUP_MEMORY_SYSTEM_PROMPT]。
     */
    fun buildGroupMemorySummaryPrompt(transcript: String): String {
        val sb = StringBuilder()
        sb.append("【本次需要压缩的群聊对话】\n")
        sb.append(transcript)
        return sb.toString().trim()
    }

    /**
     * 记忆抽屉工具定义（6.6.1 + 检索版）：`read_memory(query)`。
     *
     * - 带 query 参数：模型按当前话题的关键词/问题检索相关记忆片段，
     *   不再要求模型每轮重读整份压缩摘要；
     * - 仅在需要回忆更早的对话细节时调用；
     * - 检索结果由 [com.quiddity.app.domain.MemorySearch] 在工具回填阶段产出。
     */
    fun buildReadMemoryTool(): ToolDefinition = ToolDefinition(
        type = "function",
        function = ToolFunction(
            name = "read_memory",
            description = "按关键词检索历史记忆摘要。仅当需要回忆更早的对话细节时才调用；传入具体的问题或关键词，返回相关记忆片段。",
            parameters = JsonObject(
                mapOf(
                    "type" to JsonPrimitive("object"),
                    "properties" to JsonObject(
                        mapOf(
                            "query" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "description" to JsonPrimitive("需要检索的关键词或问题，例如：用户的项目进度")
                                )
                            )
                        )
                    ),
                    "required" to JsonArray(listOf(JsonPrimitive("query")))
                )
            )
        )
    )

    /**
     * 本地聊天记录检索工具（search_chat）：按关键词在用户本机完整聊天记录中
     * 查找历史消息并引用（含角色标签、内容摘录与发送时间）。
     * 检索结果由 [com.quiddity.app.domain.ChatRecordSearch] 在工具回填阶段产出。
     */
    fun buildSearchChatTool(): ToolDefinition = ToolDefinition(
        type = "function",
        function = ToolFunction(
            name = "search_chat",
            description = "在用户本机的聊天记录中按关键词搜索历史消息。当需要回忆/引用某条具体聊天内容（谁在什么时候说过什么）时调用；传入具体的关键词或问题。",
            parameters = JsonObject(
                mapOf(
                    "type" to JsonPrimitive("object"),
                    "properties" to JsonObject(
                        mapOf(
                            "query" to JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("string"),
                                    "description" to JsonPrimitive("需要检索的关键词或问题，例如：上次提到的旅行计划")
                                )
                            )
                        )
                    ),
                    "required" to JsonArray(listOf(JsonPrimitive("query")))
                )
            )
        )
    )

    /**
     * 构造抽屉回填内容（6.6.4）：完整压缩摘要 +（群聊场景）群聊小本本合并文本。
     *
     * 回填前做体积保护：合计超过 [QuiddityConstants.MEMORY_DRAWER_BUDGET_TOKENS] 时截断
     * （预算 = 2000 token，对齐压缩上限）。
     */
    fun buildMemoryDrawerContent(conv: Conversation): String {
        val parts = mutableListOf<String>()
        if (conv.compressedMemory.isNotBlank()) parts.add(conv.compressedMemory)
        if (conv.groupMemory.isNotBlank()) parts.add(conv.groupMemory)
        var text = parts.joinToString("\n\n")
        while (text.isNotEmpty() &&
            TokenEstimator.estimate(text) > QuiddityConstants.MEMORY_DRAWER_BUDGET_TOKENS
        ) {
            text = text.take((text.length * 3) / 4).trim()
        }
        return text
    }
}
