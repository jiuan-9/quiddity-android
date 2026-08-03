package com.quiddity.app.domain

import com.quiddity.app.data.model.Message
import com.quiddity.app.util.QuiddityConstants
import com.quiddity.app.util.TokenEstimator

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
 * 流式消息协调器通用接口。
 *
 * 定义"接收 delta -> 派发信号"的核心契约。
 * [MessageStreamCoordinator] 为默认实现（按句末标点 + 括号切分）。
 */
interface StreamCoordinator {
    sealed class Signal {
        data class New(val message: Message) : Signal()
        data class Update(val message: Message) : Signal()
        data class Complete(val message: Message) : Signal()
    }

    fun accept(delta: String): List<Signal>
    fun finalize(): List<Signal>
    fun snapshot(): List<Message>
}

/**
 * 一次切分的结果。
 *
 * @param text 切出的段落文本（已 trim）
 * @param consumeEnd buffer 中被消费的结束索引（exclusive）：buffer[0, consumeEnd) 被移除
 * @param emit 是否作为消息发出。仅含句末标点 / 空括号等无实质内容的段落 emit=false，
 *             仍从 buffer 消费但不产生消息，避免发出"。。。"这类空消息。
 */
private data class Segment(
    val text: String,
    val consumeEnd: Int,
    val emit: Boolean
)

/**
 * 引号栈帧：记录当前未闭合的引号，以及其内容中是否出现过句末标点。
 * 引号内容以句末标点结束时，闭合引号应留在同一条消息内（避免闭合符被拆开）。
 */
private data class QuoteFrame(
    val open: Char,
    var containsSentenceEnder: Boolean = false
)

/**
 * 流式消息协调器默认实现。
 *
 * ## 切分策略（句末标点 + 括号）
 *
 * 全面替代旧的"段落 / token 阈值 / 分割标记"切分方式，改为确定性切分：
 *
 * 1. **句末标点切分**：buffer 中出现句末标点（。！？.!?）即在该处切分，
 *    标点保留在前一条消息末尾。连续的句末标点（如 ?!、！！）合并到同一条消息，
 *    不会切出仅含标点的空片段。
 * 2. **省略号不切分**：ASCII `...`（连续 ≥2 个 `.`）与单字符 `…`（U+2026）视为省略号，
 *    不触发切分，保留在当前消息内——例如"要不歇歇...不要太累。"只在末尾 `。` 切分。
 * 3. **括号内容另起一句**：成对括号 `()` `（）` `[]` `【】` `{}` `<>` 内的内容
 *    作为独立消息发出（含括号本身）。括号前的文本作为前一条消息结束，
 *    括号闭合即视为该段完成。示例"（伸手）你真的还好吗？"切为"（伸手）"+"你真的还好吗？"。
 * 4. **引号内容不切分**：成对引号 `""` `“”` `‘’` `「」` `『』` `《》` `〈〉`
 *    内的句末标点 / 括号不触发切分——`"你好。"` 不会再把闭合引号拆成下一条消息。
 *    引号内容以句末标点结束时，闭合引号一到就整体切出；
 *    否则引号连同后续文本等到下一个句末标点再切分。
 * 5. **孤立闭合符吸收**：句末标点后紧跟的无歧义闭合引号 / 括号
 *    （`”` `’` `」` `』` `》` `〉` `）` `]` `}` `>`）并入同一条消息，
 *    避免模型输出悬空闭合符时产生仅含单个符号的消息。
 * 6. **硬上限保护**：当 buffer 累计字符数达到硬上限且找不到任何切分点时
 *    （如模型输出超长无标点文本），强制在当前位置切分，防止 OOM。
 *
 * 切分开关 [splitEnabled]：关闭时（AI 回复切分 = off）不进行任何切分，
 * 整条回复作为单条消息累计输出，仅在 [finalize] 时完成。
 */
class MessageStreamCoordinator(
    private val conversationId: String,
    private val runId: String,
    private val singleMessageTokens: Int = 800,
    private val splitEnabled: Boolean = true,
    private val startTimestamp: Long = System.currentTimeMillis(),
    /**
     * 发言人会话 id（群聊消息从创建起带发言人，2.0.0 使用）。
     * 私聊为 null（默认值，向后兼容）。
     */
    private val senderId: String? = null
) : StreamCoordinator {

    private val buffer = StringBuilder()
    private val completed: MutableList<Message> = mutableListOf()
    private val knownIds: MutableSet<String> = LinkedHashSet()
    private var currentIndex = 0
    private var currentStartTs = startTimestamp

    override fun accept(delta: String): List<StreamCoordinator.Signal> {
        if (delta.isEmpty()) return emptyList()
        buffer.append(delta)

        val signals = mutableListOf<StreamCoordinator.Signal>()
        // 循环切分：一次 delta 可能包含多个切分点，全部切出
        while (true) {
            val seg = findNextCompleteSegment()
            if (seg != null) {
                // 从 buffer 移除已消费部分
                consumeFromBuffer(seg.consumeEnd)
                if (seg.emit && seg.text.isNotBlank()) {
                    val completedMsg = buildMessageFromContent(seg.text, streaming = false)
                    if (knownIds.add(completedMsg.id)) {
                        signals += StreamCoordinator.Signal.New(completedMsg)
                    } else {
                        signals += StreamCoordinator.Signal.Update(completedMsg)
                    }
                    signals += StreamCoordinator.Signal.Complete(completedMsg)
                    completed += completedMsg
                    currentIndex++
                    currentStartTs = System.currentTimeMillis()
                }
                continue
            }
            // 无完整切分点：硬上限保护，强制切分防 OOM
            if (splitEnabled && buffer.length >= hardCharLimit() && buffer.isNotBlank()) {
                val forced = buffer.toString().trim()
                buffer.clear()
                if (forced.isNotEmpty()) {
                    val completedMsg = buildMessageFromContent(forced, streaming = false)
                    if (knownIds.add(completedMsg.id)) {
                        signals += StreamCoordinator.Signal.New(completedMsg)
                    } else {
                        signals += StreamCoordinator.Signal.Update(completedMsg)
                    }
                    signals += StreamCoordinator.Signal.Complete(completedMsg)
                    completed += completedMsg
                    currentIndex++
                    currentStartTs = System.currentTimeMillis()
                }
                continue
            }
            break
        }

        // 单条更新（当前 buffer 内容）
        if (buffer.isNotEmpty() || signals.isEmpty()) {
            val current = buildMessage(streaming = true)
            if (knownIds.add(current.id)) {
                signals += StreamCoordinator.Signal.New(current)
            } else {
                signals += StreamCoordinator.Signal.Update(current)
            }
        }

        return signals
    }

    override fun finalize(): List<StreamCoordinator.Signal> {
        val signals = mutableListOf<StreamCoordinator.Signal>()
        // buffer 为空说明全部内容已在 accept 阶段切分完成（或流本就无内容），无需收尾
        if (buffer.isEmpty()) return signals
        val finalMsg = buildMessage(streaming = false)
        if (finalMsg.id in knownIds) {
            signals += StreamCoordinator.Signal.Complete(finalMsg)
        } else {
            signals += StreamCoordinator.Signal.New(finalMsg)
            signals += StreamCoordinator.Signal.Complete(finalMsg)
        }
        completed += finalMsg
        buffer.clear()
        return signals
    }

    override fun snapshot(): List<Message> {
        val out = completed.toMutableList()
        if (buffer.isNotEmpty() || completed.isEmpty()) {
            out += buildMessage(streaming = buffer.isNotEmpty())
        }
        return out
    }

    // ==================== 句末标点 + 括号切分核心 ====================

    /**
     * 在 buffer 起始处寻找下一个完整可切分段落。
     *
     * @return [Segment] 或 null（buffer 中尚无完整段落，等待更多 delta）
     */
    private fun findNextCompleteSegment(): Segment? {
        if (!splitEnabled || buffer.isBlank()) return null
        val text = buffer
        val quoteStack = ArrayDeque<QuoteFrame>()
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            // 引号包裹状态：引号未闭合前，句末标点与括号一律不切分，
            // 否则闭合引号（如 "你好。" 的 "）会被拆到下一条消息。
            if (quoteStack.isNotEmpty()) {
                when {
                    // 栈顶引号配对闭合：优先于开引号判断（ASCII " 同时是开/闭引号）
                    isCloseQuote(ch) && matchesQuotePair(quoteStack.last().open, ch) -> {
                        val top = quoteStack.removeLast()
                        if (quoteStack.isEmpty() && top.containsSentenceEnder) {
                            val seg = text.subSequence(0, i + 1).toString().trim()
                            val hasContent = seg.any { c ->
                                !c.isWhitespace() && !isSentenceEnder(c)
                            }
                            return Segment(
                                text = seg,
                                consumeEnd = i + 1,
                                emit = hasContent
                            )
                        }
                        i++
                    }
                    isOpenQuote(ch) -> {
                        quoteStack.addLast(QuoteFrame(ch))
                        i++
                    }
                    isSentenceEnder(ch) -> {
                        // 省略号不算句末标点：不标记引号内容以句号结尾
                        if (ch == '.') {
                            val runLen = countConsecutive(text, i, '.')
                            if (runLen >= 2) {
                                i += runLen
                                continue
                            }
                        }
                        if (ch == '…') {
                            i++
                            continue
                        }
                        quoteStack.forEach { it.containsSentenceEnder = true }
                        i++
                    }
                    else -> i++
                }
                continue
            }
            val openMatch = matchingClose(ch)
            if (openMatch != null) {
                // 括号前的文本作为前一段消息（若非空）
                if (i > 0 && text.subSequence(0, i).isNotBlank()) {
                    return Segment(
                        text = text.subSequence(0, i).toString().trim(),
                        consumeEnd = i,
                        emit = true
                    )
                }
                // 括号在 buffer 起始：寻找匹配的闭合括号
                val closeIdx = findMatchingCloseBracket(text, i)
                if (closeIdx < 0) {
                    // 括号未闭合，等待更多 delta
                    return null
                }
                val bracketContent = text.subSequence(i, closeIdx + 1).toString()
                // 仅含括号 / 空白时不发出（如"（）"），但仍消费
                val hasInnerContent = bracketContent.any { c ->
                    !c.isWhitespace() && matchingClose(c) == null && matchingOpen(c) == null
                }
                return Segment(
                    text = bracketContent,
                    consumeEnd = closeIdx + 1,
                    emit = hasInnerContent
                )
            } else if (isOpenQuote(ch)) {
                quoteStack.addLast(QuoteFrame(ch))
                i++
            } else if (isSentenceEnder(ch)) {
                // 省略号处理：ASCII 连续点（≥2）与单字符 … 不切分
                if (ch == '.') {
                    val runLen = countConsecutive(text, i, '.')
                    if (runLen >= 2) {
                        i += runLen
                        continue
                    }
                    // 单个 '.' 在 buffer 末尾：可能后续还有点组成 '...'，等待更多 delta
                    if (i + 1 >= text.length) return null
                }
                if (ch == '…') {
                    i++
                    continue
                }
                // 消费本标点 + 紧随其后的连续句末标点（如 ?!、！！、。！）
                var end = i + 1
                while (end < text.length) {
                    val nc = text[end]
                    if (nc == '…') break
                    if (nc == '.') {
                        // 连续点视为省略号，停止合并
                        if (end + 1 < text.length && text[end + 1] == '.') break
                    }
                    if (!isSentenceEnder(nc)) break
                    end++
                }
                // 吸收句末标点后紧跟的无歧义闭合引号 / 括号，
                // 避免孤立闭合符被拆成下一条消息。ASCII 单双引号可能同时充当开引号，
                // 不在此吸收，交由上方引号栈配对逻辑处理。
                while (end < text.length && isTrailingCloser(text[end])) end++
                val seg = text.subSequence(0, end).toString().trim()
                // 仅含句末标点 / 空白的片段不发出（如行首"。。。"）
                val hasContent = seg.any { c -> !c.isWhitespace() && !isSentenceEnder(c) }
                return Segment(text = seg, consumeEnd = end, emit = hasContent)
            } else {
                i++
            }
        }
        return null
    }

    /**
     * 从 buffer 头部移除 [consumeEnd] 长度，并 trim 前导空白。
     */
    private fun consumeFromBuffer(consumeEnd: Int) {
        buffer.delete(0, consumeEnd)
        // 移除前导空白，避免下一条消息开头出现空行
        var trimLen = 0
        while (trimLen < buffer.length && buffer[trimLen].isWhitespace()) {
            trimLen++
        }
        if (trimLen > 0) buffer.delete(0, trimLen)
    }

    /**
     * 寻找 [openIdx] 处开括号匹配的闭括号索引（支持同类型嵌套）。
     * @return 闭括号索引，未闭合返回 -1
     */
    private fun findMatchingCloseBracket(text: CharSequence, openIdx: Int): Int {
        val open = text[openIdx]
        val close = matchingClose(open) ?: return -1
        var depth = 1
        var i = openIdx + 1
        while (i < text.length) {
            val c = text[i]
            if (c == open) {
                depth++
            } else if (c == close) {
                depth--
                if (depth == 0) return i
            }
            i++
        }
        return -1
    }

    /** 句末标点：。！？.!?（省略号 … 与连续点另行处理）。 */
    private fun isSentenceEnder(ch: Char): Boolean = when (ch) {
        '。', '！', '？', '.', '!', '?' -> true
        else -> false
    }

    /** 开引号集合（ASCII `"` 与全角成对引号 / 书名号）。 */
    private fun isOpenQuote(ch: Char): Boolean = when (ch) {
        '"', '“', '‘', '「', '『', '《', '〈' -> true
        else -> false
    }

    /** 闭引号集合。 */
    private fun isCloseQuote(ch: Char): Boolean = when (ch) {
        '"', '”', '’', '」', '』', '》', '〉' -> true
        else -> false
    }

    /** 判断开闭引号是否成对。 */
    private fun matchesQuotePair(open: Char, close: Char): Boolean = when (open) {
        '"' -> close == '"'
        '“' -> close == '”'
        '‘' -> close == '’'
        '「' -> close == '」'
        '『' -> close == '』'
        '《' -> close == '》'
        '〈' -> close == '〉'
        else -> false
    }

    /** 无歧义的闭合引号 / 括号：吸收进句末标点所在消息。 */
    private fun isTrailingCloser(ch: Char): Boolean = when (ch) {
        '”', '’', '」', '』', '》', '〉', '）', ']', '}', '>' -> true
        else -> false
    }

    /** 若为开括号，返回对应的闭括号；否则返回 null。 */
    private fun matchingClose(ch: Char): Char? = when (ch) {
        '(' -> ')'
        '（' -> '）'
        '[' -> ']'
        '【' -> '】'
        '{' -> '}'
        '<' -> '>'
        else -> null
    }

    /** 若为闭括号，返回对应的开括号；否则返回 null。 */
    private fun matchingOpen(ch: Char): Char? = when (ch) {
        ')' -> '('
        '）' -> '（'
        ']' -> '['
        '】' -> '【'
        '}' -> '{'
        '>' -> '<'
        else -> null
    }

    /** 从 [start] 起连续等于 [ch] 的字符数。 */
    private fun countConsecutive(text: CharSequence, start: Int, ch: Char): Int {
        var n = 0
        var i = start
        while (i < text.length && text[i] == ch) {
            n++
            i++
        }
        return n
    }

    // ==================== 内部 ====================

    private fun hardCharLimit(): Long {
        return (singleMessageTokens.toLong() * HARD_LIMIT_MULTIPLIER * CHARS_PER_TOKEN)
            .coerceAtMost(MAX_HARD_LIMIT_CHARS.toLong())
    }

    private fun buildCurrentId(): String = "${conversationId}_${runId}_ai_$currentIndex"

    private fun buildMessage(streaming: Boolean): Message = Message(
        id = buildCurrentId(),
        conversationId = conversationId,
        role = com.quiddity.app.data.model.Role.ASSISTANT,
        content = buffer.toString(),
        timestamp = currentStartTs,
        tokenCount = TokenEstimator.estimate(buffer.toString()),
        isStreaming = streaming,
        senderId = senderId
    )

    /**
     * 用指定内容构建消息（用于切分后的完成消息）。
     */
    private fun buildMessageFromContent(content: String, streaming: Boolean): Message = Message(
        id = buildCurrentId(),
        conversationId = conversationId,
        role = com.quiddity.app.data.model.Role.ASSISTANT,
        content = content,
        timestamp = currentStartTs,
        tokenCount = TokenEstimator.estimate(content),
        isStreaming = streaming,
        senderId = senderId
    )

    private companion object {
        const val HARD_LIMIT_MULTIPLIER = QuiddityConstants.SPLITTER_HARD_LIMIT_MULTIPLIER
        const val CHARS_PER_TOKEN = QuiddityConstants.SPLITTER_CHARS_PER_TOKEN
        const val MAX_HARD_LIMIT_CHARS = QuiddityConstants.SPLITTER_MAX_HARD_LIMIT_CHARS
    }
}
