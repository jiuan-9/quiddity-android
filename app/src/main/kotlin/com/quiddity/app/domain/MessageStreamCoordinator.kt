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
 * [MessageStreamCoordinator] 作为默认实现（按段落 + token 数切分）。
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
 * 切分点信息。
 *
 * @param completedEnd 完成消息的结束索引（exclusive）：buffer[0, completedEnd) 为完成消息内容
 * @param remainingStart 剩余内容的起始索引（inclusive）：buffer[remainingStart, length) 留在 buffer
 * @param trimTrailing 是否 trim 完成消息的尾部空白
 */
private data class SplitPoint(
    val completedEnd: Int,
    val remainingStart: Int,
    val trimTrailing: Boolean
)

/**
 * 流式消息协调器默认实现。
 *
 * ## 切分策略
 *
 * 1. **段落切分（主）**：当 buffer 中出现段落结束标记（`\n\n`）时，在段落边界切分。
 *    每个完整段落作为一条独立消息发送——符合用户选择的「按段落切」粒度。
 * 2. **标点兜底（次）**：当单个段落超过 token 上限但还没有 `\n\n` 时，在最后一个
 *    句末标点（。！？.!?）处切分，避免单条消息过长。
 * 3. **硬上限保护**：当 buffer 累计字符数达到硬上限且找不到任何合适切分点时，
 *    强制在当前位置切分，防止 OOM。
 * 4. **保护区域**：不切割代码块（``` ... ```）、未闭合括号（()（）[]【】{}）
 *    内部的内容——避免破坏 Markdown 格式和语义完整性。
 * 5. **合并短片段**：切分后如果剩余片段过短（< 10 字符），追加到前一条消息末尾。
 */
class MessageStreamCoordinator(
    private val conversationId: String,
    private val runId: String,
    private val singleMessageTokens: Int = 800,
    private val startTimestamp: Long = System.currentTimeMillis()
) : StreamCoordinator {

    private val buffer = StringBuilder()
    private val completed: MutableList<Message> = mutableListOf()
    private val knownIds: MutableSet<String> = LinkedHashSet()
    private var currentIndex = 0
    private var currentStartTs = startTimestamp
    private var droppedOnOverflow = 0L

    override fun accept(delta: String): List<StreamCoordinator.Signal> {
        if (delta.isEmpty()) return emptyList()

        // 硬上限保护：防异常 server 永不切分时无限增长导致 OOM
        val hardCharLimit = (singleMessageTokens.toLong() * HARD_LIMIT_MULTIPLIER * CHARS_PER_TOKEN)
            .coerceAtMost(MAX_HARD_LIMIT_CHARS.toLong())
        if (buffer.length >= hardCharLimit) {
            droppedOnOverflow += delta.length
            return emptyList()
        }
        buffer.append(delta)

        val signals = mutableListOf<StreamCoordinator.Signal>()
        // 循环切分：一次 delta 可能包含多个段落边界，需要全部切分
        var splitPoint = findSplitPoint()
        while (splitPoint != null) {
            // 取出切分点之前的内容作为完成消息
            val completedContent = if (splitPoint.trimTrailing) {
                buffer.substring(0, splitPoint.completedEnd).trimEnd()
            } else {
                buffer.substring(0, splitPoint.completedEnd)
            }
            // 剩余内容：trim 前导空白，避免下一条消息开头出现空行。
            // 代码块受保护不会被切分，因此前导空白只是分割符残留，可安全去除。
            val remaining = if (splitPoint.remainingStart < buffer.length) {
                buffer.substring(splitPoint.remainingStart).trimStart()
            } else {
                ""
            }

            // 清空 buffer，放入剩余内容
            buffer.clear()
            buffer.append(remaining)

            // 跳过空完成内容（如 buffer 以 \n\n 开头时）
            if (completedContent.isNotBlank()) {
                // 如果切分后剩余内容太短（< 10 字符），合并到前一条消息
                if (remaining.length < MIN_TAIL_MERGE_CHARS && completed.isNotEmpty() && remaining.isNotBlank()) {
                    val lastCompleted = completed.last()
                    val mergedContent = lastCompleted.content + remaining
                    val mergedMsg = lastCompleted.copy(
                        content = mergedContent,
                        tokenCount = TokenEstimator.estimate(mergedContent)
                    )
                    completed[completed.lastIndex] = mergedMsg
                    signals += StreamCoordinator.Signal.Update(mergedMsg)
                    buffer.clear()
                    break
                }

                // 构建完成消息
                val completedMsg = buildMessageFromContent(completedContent, streaming = false)
                if (knownIds.add(completedMsg.id)) {
                    signals += StreamCoordinator.Signal.New(completedMsg)
                } else {
                    signals += StreamCoordinator.Signal.Update(completedMsg)
                }
                signals += StreamCoordinator.Signal.Complete(completedMsg)
                completed += completedMsg

                // 开启新消息
                currentIndex++
                currentStartTs = System.currentTimeMillis()
            }

            // 继续检查是否有更多切分点
            splitPoint = findSplitPoint()
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
        val currentId = buildCurrentId()

        if (buffer.isEmpty() && completed.isNotEmpty()) {
            // buffer 已空且已有完成消息——无需额外操作
            return signals
        }

        if (buffer.isEmpty() && completed.isEmpty()) {
            // 极端：从未 accept 过任何 delta
            return signals
        }

        // 把当前的 streaming 消息完整收尾
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

    // ==================== 智能切分核心 ====================

    /**
     * 在 buffer 中寻找切分点。
     *
     * 切分优先级：
     * 2. \n\n 段落边界（主切分方式，不在保护区域内）
     * 3. 句末标点（。！？.!?，当 buffer 超过 token 上限时触发）
     * 4. \n 换行（buffer 远超上限时兜底）
     * 5. 硬上限强制切分
     *
     * @return [SplitPoint] 或 null（无需切分）
     */
    private fun findSplitPoint(): SplitPoint? {
        if (buffer.isBlank()) return null

        // 计算保护区域
        val protectedRanges = findProtectedRanges()

        // 分割标记检测：LLM 可能不严格按系统提示输出 ⫟⫟⫟（3 个），
        // 实测会出现单个 ⫟ 或两个 ⫟⫟ 的情况。这里检测任意 ⫟ 连续串（≥1 个）
        // 作为分割点，避免残留 ⫟ 字符泄漏到用户可见的回复中。
        // 标记串本身在切分时被丢弃，前后内容分别 trim 空白。
        val markerChar = QuiddityConstants.MESSAGE_SPLIT_MARKER.first()
        val markerIdx = buffer.indexOf(markerChar)
        if (markerIdx >= 0) {
            // 计算连续 ⫟ 串的长度（吞掉 1 个以上的连续标记字符）
            var runEnd = markerIdx
            while (runEnd < buffer.length && buffer[runEnd] == markerChar) {
                runEnd++
            }
            return SplitPoint(
                completedEnd = markerIdx,
                remainingStart = runEnd,
                trimTrailing = true
            )
        }

        // 2. 段落边界 \n\n（主切分方式）
        val paragraphSplit = findLastUnprotectedIndex("\n\n", protectedRanges)
        if (paragraphSplit >= 0) {
            // 在 \n\n 之后切分（\n\n 本身作为分隔符被移除）
            val splitAfter = paragraphSplit + 2
            // 确保 \n\n 后面有内容（否则等更多 delta）
            if (splitAfter < buffer.length) {
                return SplitPoint(
                    completedEnd = paragraphSplit,
                    remainingStart = splitAfter,
                    trimTrailing = true
                )
            }
        }

        // 3. 仅在 buffer 超过 token 上限时才按标点切分
        val tokenCount = TokenEstimator.estimate(buffer.toString())
        if (tokenCount >= singleMessageTokens) {
            // 按句末标点切分
            val sentenceSplit = findLastSentenceEnd(protectedRanges)
            if (sentenceSplit >= 0) {
                // 在标点之后切分（标点保留在完成消息中）
                return SplitPoint(
                    completedEnd = sentenceSplit + 1,
                    remainingStart = sentenceSplit + 1,
                    trimTrailing = false
                )
            }

            // 4. \n 换行兜底
            val newlineSplit = findLastUnprotectedIndex("\n", protectedRanges)
            if (newlineSplit >= 0) {
                return SplitPoint(
                    completedEnd = newlineSplit,
                    remainingStart = newlineSplit + 1,
                    trimTrailing = true
                )
            }

            // 5. 硬上限：找不到任何切分点，但 buffer 已达硬上限
            val hardCharLimit = (singleMessageTokens.toLong() * HARD_LIMIT_MULTIPLIER * CHARS_PER_TOKEN)
                .coerceAtMost(MAX_HARD_LIMIT_CHARS.toLong())
            if (buffer.length >= hardCharLimit) {
                // 强制在当前位置切分
                return SplitPoint(
                    completedEnd = buffer.length,
                    remainingStart = buffer.length,
                    trimTrailing = false
                )
            }
        }

        return null
    }

    /**
     * 寻找保护区域范围：代码块、未闭合括号。
     *
     * 保护区域内的索引不应作为切分点——避免破坏 Markdown 格式和语义完整性。
     *
     * @return 保护区域列表，每个 Pair(start, end) 表示 [start, end) 区间受保护
     */
    private fun findProtectedRanges(): List<Pair<Int, Int>> {
        val ranges = mutableListOf<Pair<Int, Int>>()
        val text = buffer.toString()

        // 1. 代码块保护：``` ... ```
        var codeBlockStart = -1
        var i = 0
        while (i <= text.length - 3) {
            if (text.substring(i, i + 3) == "```") {
                if (codeBlockStart >= 0) {
                    // 找到闭合标记
                    ranges.add(Pair(codeBlockStart, i + 3))
                    codeBlockStart = -1
                } else {
                    // 找到开始标记
                    codeBlockStart = i
                }
                i += 3
            } else {
                i++
            }
        }
        // 未闭合的代码块：从开始标记到 buffer 末尾都受保护
        if (codeBlockStart >= 0) {
            ranges.add(Pair(codeBlockStart, text.length))
        }

        // 2. 未闭合括号保护
        // 如果有未闭合的括号，整个 buffer 受保护（无法确定切分点是否在括号内）
        val bracketDepth = countUnclosedBrackets(text)
        if (bracketDepth > 0) {
            ranges.add(Pair(0, text.length))
        }

        return ranges
    }

    /**
     * 统计未闭合的括号深度。
     * 支持：()（）[]【】{}<>
     */
    private fun countUnclosedBrackets(text: String): Int {
        var depth = 0
        for (ch in text) {
            when (ch) {
                '(', '（', '[', '【', '{', '<' -> depth++
                ')', '）', ']', '】', '}', '>' -> depth = (depth - 1).coerceAtLeast(0)
            }
        }
        return depth
    }

    /**
     * 在保护区域之外寻找目标字符串的最后一个出现位置。
     */
    private fun findLastUnprotectedIndex(target: String, protectedRanges: List<Pair<Int, Int>>): Int {
        val text = buffer.toString()
        var searchFrom = 0
        var lastFound = -1
        while (true) {
            val idx = text.indexOf(target, searchFrom)
            if (idx < 0) break
            val isProtected = protectedRanges.any { (start, end) ->
                idx >= start && idx < end
            }
            if (!isProtected) {
                lastFound = idx
            }
            searchFrom = idx + target.length
        }
        return lastFound
    }

    /**
     * 寻找最后一个不受保护的句末标点位置。
     * 句末标点：。！？.!?（后跟换行、空格或字符串末尾时才算句末）
     */
    private fun findLastSentenceEnd(protectedRanges: List<Pair<Int, Int>>): Int {
        val text = buffer.toString()
        val sentenceEnds = charArrayOf('。', '！', '？', '.', '!', '?')
        var lastFound = -1
        for (i in text.indices) {
            if (text[i] in sentenceEnds) {
                val isProtected = protectedRanges.any { (start, end) ->
                    i >= start && i < end
                }
                if (!isProtected) {
                    val afterIdx = i + 1
                    if (afterIdx >= text.length || text[afterIdx] == '\n' || text[afterIdx] == ' ' || text[afterIdx] == '　') {
                        lastFound = i
                    }
                }
            }
        }
        return lastFound
    }

    // ==================== 内部 ====================

    private fun buildCurrentId(): String = "${conversationId}_${runId}_ai_$currentIndex"

    private fun buildMessage(streaming: Boolean): Message = Message(
        id = buildCurrentId(),
        conversationId = conversationId,
        role = com.quiddity.app.data.model.Role.ASSISTANT,
        content = buffer.toString(),
        timestamp = currentStartTs,
        tokenCount = TokenEstimator.estimate(buffer.toString()),
        isStreaming = streaming
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
        isStreaming = streaming
    )

    private companion object {
        const val HARD_LIMIT_MULTIPLIER = QuiddityConstants.SPLITTER_HARD_LIMIT_MULTIPLIER
        const val CHARS_PER_TOKEN = QuiddityConstants.SPLITTER_CHARS_PER_TOKEN
        const val MAX_HARD_LIMIT_CHARS = QuiddityConstants.SPLITTER_MAX_HARD_LIMIT_CHARS

        // 切分后尾部内容过短时合并到前一条消息的阈值（字符数）
        const val MIN_TAIL_MERGE_CHARS = 10
    }
}
