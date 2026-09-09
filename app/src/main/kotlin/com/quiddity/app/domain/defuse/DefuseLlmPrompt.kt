package com.quiddity.app.domain.defuse

/*
 * 拆弹搭档提示词：LLM 持有手册但看不到面板，玩家负责描述，搭档负责对照手册给出指令。
 */

/** 对话记录中的一条。 */
data class DefuseChatTurn(val sender: String, val text: String)

object DefuseLlmPrompt {

    private const val HISTORY_LIMIT = 8

    private const val MANUAL = "${WireRules.MANUAL}\n\n${KeypadRules.MANUAL}\n\n${ButtonRules.MANUAL}"

    private const val BEHAVIOR = "配合规则：\n" +
        "1. 你看不到炸弹面板，只能依据玩家描述判断，信息不足时先提问，绝不编造面板内容。\n" +
        "2. 判断完毕后直接给明确指令，例如「剪第 3 根」「按顺序：★ ☾ ♥ ◈」「点击」「长按 1 秒」。\n" +
        "3. 像真人搭档一样说话：可以紧张、催促、说句俏皮话或吐槽，但指令必须清晰；一般不超过两句话，不需要解释手册推理过程。\n" +
        "4. 玩家描述自相矛盾时，提醒 TA 重新核对。\n" +
        "5. 全程用口语化、有情绪的口吻（例如「快！先别管那根红的」「稳住，看仔细再报」），别像客服或说明书；不要提及自己是 AI 或模型。"

    /** 构建搭档 system 提示词（含人设与完整手册）。 */
    fun buildSystemPrompt(partnerName: String, persona: String?): String {
        val role = buildString {
            append("你是「").append(partnerName).append("」，一位专业拆弹搭档。")
            if (!persona.isNullOrBlank()) {
                append("你的性格与背景：").append(persona).append('。')
            }
            append("你和玩家正在合作拆弹：玩家能看到炸弹面板，你看不到，你手里只有拆弹手册。")
        }
        return "$role\n\n拆弹手册：\n$MANUAL\n\n$BEHAVIOR"
    }

    /** 构建一次搭档回复请求：携带最近对话记录。 */
    fun buildUserMessage(module: DefuseModule, history: List<DefuseChatTurn>): String {
        val transcript = history.takeLast(HISTORY_LIMIT)
            .joinToString("\n") { "${it.sender}：${it.text}" }
        return "当前模块：${module.type.label}\n\n对话记录：\n$transcript\n\n请给出下一步指令。"
    }

    /** 「汇报面板」一键上报文本：把当前模块状态完整描述给搭档。 */
    fun buildReportText(module: DefuseModule): String = when (module) {
        is DefuseModule.Wires -> {
            val colors = module.wires.joinToString("、") { it.label }
            "面板汇报：5 根线从左到右颜色为：$colors。请告诉我剪哪根。"
        }
        is DefuseModule.Keypad -> {
            "面板汇报：面板上 4 个符号：${module.symbols.joinToString(" ")}。请告诉我按什么顺序点。"
        }
        is DefuseModule.Button -> {
            "面板汇报：按钮颜色「${module.color.label}」，文字「${module.label.text}」，指示灯「${if (module.lightOn) "亮" else "灭"}」。请告诉我应该点击还是长按。"
        }
    }

    /** 当前模块对应的手册摘录（求助时展示给玩家）。 */
    fun manualSection(module: DefuseModule): String = when (module) {
        is DefuseModule.Wires -> WireRules.MANUAL
        is DefuseModule.Keypad -> KeypadRules.MANUAL
        is DefuseModule.Button -> ButtonRules.MANUAL
    }
}
