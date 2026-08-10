package com.quiddity.app.domain.spy

/*
 * 谁是卧底 LLM prompt 构建 + 回复解析。
 *
 * 协议设计（参考 BoardLlmPrompt 的机器可读协议风格）：
 * - 发言：LLM 直接输出自己的描述文本；
 * - 投票：要求只输出 `VOTE(玩家编号)`，解析器兼容中英文括号/空格；
 * - 生成词库：要求只输出 JSON 数组，解析器兼容代码块包裹 / 前后缀文本。
 * - 关键：每个 LLM 只知道自己的词与身份，不知道别人词，不知道谁是卧底，避免串供。
 */
object SpyLlmPrompt {

    /** 构建词库生成 system 消息：要求输出指定数量的相似词对（平民词 + 卧底词）。 */
    fun buildWordGenSystemMessage(): String {
        return buildString {
            appendLine("你是『谁是卧底』桌游的词库生成器。")
            appendLine("你要生成若干组『相似词对』：每组由两个高度相似、容易混淆的词组成（平民词与此同，卧底词与之稍有不同但很难分辨）。")
            appendLine("要求：词语要具体、贴近生活、对比两者都常见；平民词与卧底词属于同一类别、几乎难以分辨；避免生僻词与专有名词。")
            appendLine("只输出一个 JSON 数组，不要输出任何其他解释或文字。数组中每个元素格式如下：")
            appendLine("[{\"civilian\":\"平民词\",\"spy\":\"卧底词\"},{\"civilian\":\"词A\",\"spy\":\"词B\"}]")
        }.trim()
    }

    /** 构建词库生成 user 消息（主题方向 + 需要生成的组数）。 */
    fun buildWordGenUserMessage(count: Int, topic: String = ""): String {
        return buildString {
            if (topic.isNotBlank()) {
                appendLine("请围绕主题「$topic」生成。")
            }
            appendLine("请生成 $count 组相似词对，只输出一组 JSON 数组，不要输出解释、编号或别的文字。")
        }.trim()
    }

    /** 解析词库生成回复；提取 JSON 数组并逐项清洗，失败条目自动丢弃。 */
    fun parseWordGenReply(reply: String): List<SpyWordPair> {
        if (reply.isBlank()) return emptyList()
        val stripped = reply
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val arrayStart = stripped.indexOf('[')
        val arrayEnd = stripped.lastIndexOf(']')
        if (arrayStart < 0 || arrayEnd <= arrayStart) return emptyList()
        val json = stripped.substring(arrayStart, arrayEnd + 1)
        val out = mutableListOf<SpyWordPair>()
        val items = try {
            kotlinx.serialization.json.Json.parseToJsonElement(json) as? kotlinx.serialization.json.JsonArray
        } catch (e: Exception) {
            null
        }
        if (items != null) {
            items.forEachIndexed { i, element ->
                val obj = element as? kotlinx.serialization.json.JsonObject ?: return@forEachIndexed
                val civilian = (obj["civilian"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.trim().orEmpty()
                val spy = (obj["spy"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.trim().orEmpty()
                if (civilian.isNotBlank() && spy.isNotBlank()) {
                    out += SpyWordPair(id = "gen_$i", civilian = civilian, spy = spy, civilianHints = emptyList(), spyHints = emptyList())
                }
            }
        }
        return out
    }

    const val MAX_CHAT_HISTORY = 30

    /** 构建发言 system 消息（身份 + 词 + 规则 + 人设）。 */
    fun buildSpeakSystemMessage(
        playerName: String,
        persona: String?,
        role: SpyRole,
        word: String,
        mode: SpyGameMode = SpyGameMode.CLASSIC
    ): String {
        return buildString {
            appendLine("你正在扮演「$playerName」，参加一桌『谁是卧底』桌游。")
            if (!persona.isNullOrBlank()) {
                appendLine("你的性格与说话风格：$persona")
            }
            when (role) {
                SpyRole.BLANK -> {
                    appendLine("你的身份是：白板。你手上没有词（抽到的是空白牌）。")
                    appendLine("你要假装自己和大家拿到的是同一个词，通过附和前文、模仿别人的说法蒙混过关。")
                    appendLine("绝对不能说自己没有词，也不能说出任何词——因为你根本没有词。")
                }
                else -> {
                    appendLine("你的身份是：${if (role == SpyRole.SPY) "卧底" else "平民"}。")
                    appendLine("你抽到的词是：$word。")
                }
            }
            if (role == SpyRole.SPY && mode == SpyGameMode.DOUBLE_SPY) {
                appendLine("本局是双卧底模式：可能还有另一名卧底与你拿到同一个词，但你不确定是谁。")
            }
            appendLine("规则：每轮轮到你时，用一句话（20~60字左右）描述这个词语，让大家能猜到它，但绝对不能说出词语本身，也不能说得过于直白。")
            if (role == SpyRole.SPY) {
                appendLine("你是卧底：你的词与其他玩家的词不同。描述时要模糊、圆滑、装无辜，听得像在说大家的词，避免暴露。")
            } else if (role == SpyRole.CIVILIAN) {
                appendLine("你是平民：尽量给准确但隐晦的线索，让大家认出你是自己人，同时警惕卧底。")
            }
            appendLine("像真人一样说话，别把发言写成干巴巴的物品说明书：")
            appendLine("- 可以带语气词和情绪（犹豫、自信、开玩笑、假装思考都行）；")
            appendLine("- 可以回应前面玩家的发言（认同、质疑、打趣、起哄都可以）；")
            appendLine("- 结合现场气氛演一演：平民理直气壮，卧底顾左右而言他；")
            appendLine("- 用口语化短句，像朋友聚会聊天，别用书面语。")
            appendLine("只输出你的发言文本本身，不要输出角色名、描述词说明等任何其他内容。")
        }.trim()
    }

    /** 构建 PK 补发言 system 消息（平票对决：自证清白争取留下）。 */
    fun buildPkSpeakSystemMessage(
        playerName: String,
        persona: String?,
        role: SpyRole,
        word: String,
        mode: SpyGameMode = SpyGameMode.CLASSIC
    ): String {
        val base = buildSpeakSystemMessage(playerName, persona, role, word, mode)
        return buildString {
            appendLine(base)
            appendLine("现在进入平票对决（PK）：你和另外的平票者各补一句话自证清白，其余玩家会据此投票决定谁留下。")
            appendLine("发言要显得理直气壮、和大家高度一致，把票导向对方；语气要像真人吵架或自证清白，可以带点委屈、着急或笃定，别干巴巴念稿。")
            appendLine("只输出你的一句话辩护，不要输出其他内容。")
        }.trim()
    }

    /** 构建 PK 补发言 user 消息（候选人 + 已有 PK 发言 + 轮到你）。 */
    fun buildPkSpeakUserMessage(
        round: Int,
        pkCandidates: List<Pair<Int, String>>,
        pkSpeeches: List<SpySpeech>,
        playerName: String
    ): String {
        return buildString {
            appendLine("第 $round 轮投票出现平票，进入 PK 对决。")
            appendLine("平票候选人：")
            for ((index, name) in pkCandidates) {
                appendLine("$index → $name")
            }
            if (pkSpeeches.isNotEmpty()) {
                appendLine("已补发言：")
                for (s in pkSpeeches) {
                    appendLine("${s.text}")
                }
            }
            appendLine("轮到你「$playerName」自证清白，只输出一句话辩护。")
        }.trim()
    }

    /** 构建发言 user 消息（轮次 + 前文发言 + 当前轮到你）。 */
    fun buildSpeakUserMessage(
        round: Int,
        speeches: List<SpySpeech>,
        playerName: String
    ): String {
        return buildString {
            appendLine("当前是第 $round 轮发言。")
            if (speeches.isNotEmpty()) {
                appendLine("前面已经有人说过了：")
                for (s in speeches.takeLast(MAX_CHAT_HISTORY)) {
                    appendLine("${s.playerIndex}. ${s.text}")
                }
            } else {
                appendLine("你是本轮第一个发言的。")
            }
            appendLine("现在轮到你「$playerName」发言。可以顺着前面玩家的发言接话（认同、质疑或打趣都行），但不要复述别人的原话。请只输出你的一句话描述。")
        }.trim()
    }

    /** 构建投票 system 消息（身份 + 词 + 规则 + 人设）。 */
    fun buildVoteSystemMessage(
        playerName: String,
        persona: String?,
        role: SpyRole,
        word: String,
        mode: SpyGameMode = SpyGameMode.CLASSIC
    ): String {
        return buildString {
            appendLine("你在《谁是卧底》中扮演「$playerName」。")
            if (!persona.isNullOrBlank()) {
                appendLine("你的性格与说话风格：$persona")
            }
            when (role) {
                SpyRole.BLANK -> appendLine("你的身份是：白板，你手上没有词。")
                else -> appendLine("你的身份是：${if (role == SpyRole.SPY) "卧底" else "平民"}，你抽到的词是：$word。")
            }
            appendLine("现在是投票环节，你需要根据本轮的发言找出最可疑的玩家。")
            if (role == SpyRole.SPY) {
                appendLine("你是卧底，投票时避免暴露自己，尽量把票投给描述最具体、最有把握的平民。")
            } else if (role == SpyRole.CIVILIAN) {
                appendLine("作为平民，把票投给你觉得发言最含糊、最不像在说同一个词的人（大概率是卧底）。")
            } else {
                appendLine("作为白板，你要掩护卧底、避免暴露自己，把票投给描述最具体、最像平民的人。")
            }
            appendLine("请只输出一行机器可读的投票指令，不要输出其他内容：")
            appendLine("VOTE(玩家编号)")
        }.trim()
    }

    /** 构建 PK 二选一投票 system 消息。 */
    fun buildPkVoteSystemMessage(
        playerName: String,
        persona: String?,
        role: SpyRole,
        word: String,
        mode: SpyGameMode = SpyGameMode.CLASSIC
    ): String {
        val base = buildVoteSystemMessage(playerName, persona, role, word, mode)
        return buildString {
            appendLine(base)
            appendLine("本轮是平票对决（PK）投票：你只能在平票候选人里二选一，投出你认为更应该出局的人。")
            appendLine("只输出一行 VOTE(编号)。")
        }.trim()
    }

    /** 构建投票 user 消息（可投玩家列表 + 本轮发言 + 规则）。 */
    fun buildVoteUserMessage(
        round: Int,
        candidates: List<Pair<Int, String>>,
        speeches: List<SpySpeech>,
        voterName: String
    ): String {
        return buildString {
            appendLine("第 $round 轮投票环节。")
            appendLine("可投票的玩家编号与名字：")
            for ((index, name) in candidates) {
                appendLine("$index → $name")
            }
            appendLine("本轮所有发言：")
            for (s in speeches) {
                appendLine("${s.text}")
            }
            appendLine("你「$voterName」投谁？请只输出一行：VOTE(编号)。")
        }.trim()
    }

    /** 构建 PK 二选一投票 user 消息。 */
    fun buildPkVoteUserMessage(
        round: Int,
        candidates: List<Pair<Int, String>>,
        speeches: List<SpySpeech>,
        voterName: String
    ): String {
        return buildString {
            appendLine("第 $round 轮 PK 对决投票。")
            appendLine("平票候选人（二选一）：")
            for ((index, name) in candidates) {
                appendLine("$index → $name")
            }
            appendLine("本轮发言与 PK 辩护：")
            for (s in speeches) {
                appendLine("${s.text}")
            }
            appendLine("你「$voterName」支持谁留下（投给更可疑的那位）？请只输出一行：VOTE(编号)。")
        }.trim()
    }

    /** 解析投票回复；无法解析或越界返回 null。 */
    fun parseVoteReply(reply: String, validIndexes: Set<Int>): Int? {
        val trimmed = reply.trim()
        if (trimmed.isEmpty()) return null
        val match = VOTE_REGEX.find(trimmed) ?: return null
        val index = match.groupValues[1].toIntOrNull() ?: return null
        return if (index in validIndexes) index else null
    }

    private val VOTE_REGEX = Regex(
        """VOTE\s*[（(]\s*(\d+)\s*[)）]""",
        RegexOption.IGNORE_CASE
    )
}
