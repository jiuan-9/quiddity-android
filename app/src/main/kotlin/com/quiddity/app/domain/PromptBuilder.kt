package com.quiddity.app.domain

import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import com.quiddity.app.data.remote.ChatMessage
import com.quiddity.app.util.QuiddityConstants
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
     */
    internal val COMPRESSION_SYSTEM_PROMPT = """
你是一个"对话记忆压缩器"。你的任务是从对话历史中提取关键信息，输出一份紧凑、准确、无歧义的记忆摘要，供 AI 在后续对话中参考。

核心原则：
1. 忠实提取——只保留原文出现的信息，严禁添加、猜测、推断或补全。
2. 精确保留——数字、ID、日期、金额、专有名词、人名、地名等必须一字不差。
3. 删除冗余——修饰词、客套话、寒暄、重复解释、语气词全部删掉。
4. 消除指代——把「它」「那个」「这个」「他」等代词替换为具体名词。
5. 紧凑输出——输出一段连续的摘要文本，不要分段，不要使用列表或标题格式。

现在开始压缩下面的对话内容。
""".trim()

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
     * 4. 记忆（压缩摘要 + 固定记忆）
     */
    fun buildSystemPrompt(
        conv: Conversation
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

        // ===== 4. 记忆（压缩摘要优先，固定记忆次之） =====
        if (conv.compressedMemory.isNotBlank()) {
            sb.append("【历史对话摘要】\n").append(conv.compressedMemory).append("\n\n")
        }
        if (conv.memory.isNotBlank()) {
            sb.append("【需要记住的事】\n").append(conv.memory).append("\n\n")
        }

        return sb.toString().trim()
    }

    /** 把消息列表转为 ChatMessage（含 system）。 */
    fun toApiMessages(systemPrompt: String, history: List<Message>): List<ChatMessage> {
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
            result.add(ChatMessage(role = role, content = msg.content))
        }
        return result
    }
}
