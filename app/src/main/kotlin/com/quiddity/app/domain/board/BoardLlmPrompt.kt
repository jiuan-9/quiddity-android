package com.quiddity.app.domain.board

/*
 * LLM 对弈/聊天 prompt 构建与落子回复解析。
 *
 * 落子协议：要求模型只输出 `MOVE(行,列)` 或 `PASS`；解析器兼容中英文括号、
 * 空格、行尾解释文字与"停一手"。
 */
object BoardLlmPrompt {

    /** 单条对局内聊天记录（供 LLM 参考，跨 UI/领域使用）。 */
    const val MAX_CHAT_HISTORY_TURNS = 20

    private val MOVE_REGEX = Regex(
        """MOVE\s*[（(]\s*(\d+)\s*[,，]\s*(\d+)\s*[)）]""",
        RegexOption.IGNORE_CASE
    )
    private val PASS_REGEX = Regex("""\bPASS\b""", RegexOption.IGNORE_CASE)

    /** 构建落子 system 消息（人设 + 规则 + 格式约束）。 */
    fun buildMoveSystemMessage(
        state: BoardState,
        opponentName: String,
        persona: String?
    ): String {
        val rule = if (state.gameType.isGo) {
            "规则：黑先白后，有气才能落子，禁着点（自杀）不能落子；如果无棋可下或想保留实力可以停一手。"
        } else {
            "规则：黑白轮流落子，先连成五子（横/竖/斜）者获胜。"
        }
        val passHint = if (state.gameType.isGo) "或停一手：PASS" else ""
        return buildString {
            appendLine("你正在扮演「$opponentName」，与用户进行一局${state.gameType.displayName}对局。")
            appendLine("你执${state.current.label}棋。")
            appendLine("棋盘是 ${state.size}x${state.size}，行、列均从 0 开始编号，左上角是 (0,0)。")
            appendLine(rule)
            if (!persona.isNullOrBlank()) {
                appendLine("你的性格与说话方式：$persona")
            }
            appendLine("请只输出一行机器可读的落子指令，不要输出任何其他内容：")
            appendLine("- 落子：MOVE(行,列)，例如 MOVE(7,7)")
            if (passHint.isNotBlank()) appendLine("- $passHint")
        }.trim()
    }

    /** 构建落子 user 消息（当前局面 + 轮次 + 对局聊天记录）。 */
    fun buildMoveUserMessage(
        state: BoardState,
        chatHistory: List<GameChatTurn> = emptyList()
    ): String {
        val passHint = if (state.gameType.isGo) "（围棋可回复 PASS 停一手）" else ""
        return buildString {
            appendLine("当前第 ${state.moveCount + 1} 手，轮到你（${state.current.label}棋）落子$passHint。")
            appendLine(boardStonesText(state))
            state.lastMove?.let {
                appendLine("上一手：${it.row},${it.col}")
            }
            appendChatTranscript(this, chatHistory)
            appendLine("请结合聊天记录里的约定、请求或情绪来决策落子（例如用户求饶时，可以适当手下留情，但不要完全放水）。")
            appendLine("请回复一行指令：MOVE(行,列) 或 PASS。")
        }.trim()
    }

    /** 构建对局内聊天 system 消息（人设 + 当前局面概要）。 */
    fun buildChatSystemMessage(
        state: BoardState,
        opponentName: String,
        persona: String?,
        chatHistory: List<GameChatTurn> = emptyList()
    ): String {
        return buildString {
            appendLine("你正在扮演「$opponentName」，与用户正在下${state.gameType.displayName}。")
            if (!persona.isNullOrBlank()) {
                appendLine("你的性格与说话方式：$persona")
            }
            appendLine("当前第 ${state.moveCount + 1} 手，轮到你（${state.current.label}棋）落子。")
            appendLine(boardStonesText(state))
            appendChatTranscript(this, chatHistory)
            appendLine("请以角色口吻自然回复用户消息，可以谈棋局、聊闲天，但不要替用户决策。")
        }.trim()
    }

    /** 构建对局内聊天 user 消息。 */
    fun buildChatUserMessage(userText: String, state: BoardState): String {
        return buildString {
            appendLine("用户说：$userText")
            appendLine("当前局面：第 ${state.moveCount + 1} 手，轮到你（${state.current.label}棋）。")
        }.trim()
    }

    /** 解析 LLM 落子回复；无法解析返回 null。 */
    fun parseMoveReply(reply: String): LlmMove? {
        val trimmed = reply.trim()
        if (trimmed.isEmpty()) return null
        val moveMatch = MOVE_REGEX.find(trimmed)
        if (moveMatch != null) {
            val row = moveMatch.groupValues[1].toIntOrNull() ?: return null
            val col = moveMatch.groupValues[2].toIntOrNull() ?: return null
            return LlmMove.Place(Move(row, col))
        }
        if (PASS_REGEX.containsMatchIn(trimmed) || trimmed.contains("停一手")) {
            return LlmMove.Pass
        }
        return null
    }

    /** 生成"黑子位置 / 白子位置"的统一棋局描述，供落子与聊天共用。 */
    private fun boardStonesText(state: BoardState): String {
        val blacks = mutableListOf<String>()
        val whites = mutableListOf<String>()
        for (r in 0 until state.size) {
            for (c in 0 until state.size) {
                when (state.stoneAt(r, c)) {
                    Stone.BLACK -> blacks += "($r,$c)"
                    Stone.WHITE -> whites += "($r,$c)"
                    Stone.EMPTY -> Unit
                }
            }
        }
        return buildString {
            appendLine("黑子位置：${if (blacks.isEmpty()) "无" else blacks.joinToString(" ")}")
            append("白子位置：${if (whites.isEmpty()) "无" else whites.joinToString(" ")}")
        }
    }

    /** 把最近一段对局聊天追加进提示词，让 LLM 记住说过的话。 */
    private fun appendChatTranscript(sb: StringBuilder, chatHistory: List<GameChatTurn>) {
        if (chatHistory.isEmpty()) return
        sb.appendLine()
        sb.appendLine("本局聊天记录（最近 ${chatHistory.size} 条）：")
        for (turn in chatHistory.takeLast(MAX_CHAT_HISTORY_TURNS)) {
            sb.appendLine("${if (turn.fromUser) "用户" else "你"}：${turn.text}")
        }
    }
}

/** 对局内的一轮聊天（谁说的 + 内容），供 LLM 记忆对局对话。 */
data class GameChatTurn(
    val fromUser: Boolean,
    val text: String
)

/** LLM 落子指令解析结果。 */
sealed interface LlmMove {
    data class Place(val move: Move) : LlmMove
    data object Pass : LlmMove
}
