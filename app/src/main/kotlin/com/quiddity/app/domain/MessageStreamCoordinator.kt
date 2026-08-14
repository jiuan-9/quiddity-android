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
    /**
     * 接收 DeepSeek 思考内容增量（reasoning_content）。
     * 思考内容单独成一条 isThinking 消息，普通内容开始或流结束时自动完成。
     */
    fun acceptReasoning(delta: String): List<Signal>
    /** 追加一段应用内思考（如工具系列使用后的评估），附着到下一条新建消息。 */
    fun appendThinking(extra: String)
    /**
     * 设置合并模式：为 true 时后续正文追加到上一条已发消息（工具轮之间正文单条化）。
     * 默认实现为空操作，各实现按需覆盖。
     */
    fun setMergeWithPrevious(merge: Boolean) = Unit
    /**
     * 标记当前合并正文的段边界（工具轮结束处），供 UI 把工具痕迹插入正文流对应位置。
     * 默认实现为空操作，各实现按需覆盖。
     */
    fun markSegmentEnd() = Unit
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
    val emit: Boolean,
    /**
     * 是否为括号段。括号段采用"延迟发出"策略：
     * 先暂存，等后续实质内容到达时再按序发出，避免流结束时留下
     * 只有动作没有下文的悬空气泡；若流直接结束，则合并回上一条消息。
     */
    val isBracket: Boolean = false
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
     * 应用内本地思考内容（客户端生成）：附着在首条回复消息上，
     * 界面在气泡内以可展开/收起的方式展示，不发送给模型。
     */
    private val thinking: String = "",
    /**
     * 发言人会话 id（群聊消息从创建起带发言人，2.0.0 使用）。
     * 私聊为 null（默认值，向后兼容）。
     */
    private val senderId: String? = null
) : StreamCoordinator {

    private val buffer = StringBuilder()
    private val completed: MutableList<Message> = mutableListOf()
    private val knownIds: MutableSet<String> = LinkedHashSet()
    /** 待附着到下一条新建消息上的思考文本（可被工具系列后的思考追加）。 */
    private var pendingThinking: String = thinking
    /** 思考应附着的消息索引（null = 不附着；赋值后该索引消息持续携带）。 */
    private var thinkingForIndex: Int? = if (thinking.isNotBlank()) 0 else null
    /**
     * 已完整闭合但尚未发出的括号段：`(预占索引, 文本)`。
     * 预占索引保证与后续流式消息的 id 不冲突。
     */
    private val pendingBrackets = mutableListOf<Pair<Int, String>>()
    private var currentIndex = 0
    private var currentStartTs = startTimestamp
    /** 流末尾可能独立补发的 "0" 结束标记：先暂存，后续仍有内容则补回正文，流结束则丢弃。 */
    private var pendingZeroArtifact: String? = null
    /** 是否处于【思考】段内（提示词引导的模型思考，客户端按标记拆分展示）。 */
    private var inThinkingMarker = false
    /** 当前思考段已累积的文本（未闭合前不进入正文 buffer）。 */
    private val thinkingChunk = StringBuilder()
    /** 跨 delta 保留的尾部（可能是【思考】/【回答】标记的开头，等待下一个分片补全）。 */
    private var markerTail = ""

    override fun acceptReasoning(delta: String): List<StreamCoordinator.Signal> {
        // 思考统一由提示词引导（【思考】/【回答】标记）产生，客户端按标记拆分展示；
        // 不采用厂商 reasoning_content 兜底，避免出现与角色第一人称无关的原始推理文本。
        return emptyList()
    }

    /** 追加一段思考（如工具系列使用后的第一人称评估），附着到下一条新建消息。 */
    override fun appendThinking(extra: String) {
        if (extra.isBlank()) return
        // 合并模式下没有"下一条新建消息"：思考直接挂到已合并的消息上
        if (mergeWithPrevious && completed.isNotEmpty()) {
            val lastIdx = completed.lastIndex
            val merged = completed[lastIdx].copy(
                thinking = if (completed[lastIdx].thinking.isBlank()) extra
                else completed[lastIdx].thinking + "\n" + extra
            )
            completed[lastIdx] = merged
            return
        }
        pendingThinking = if (pendingThinking.isBlank()) extra else "$pendingThinking\n$extra"
        thinkingForIndex = currentIndex
    }

    /**
     * 合并模式：为 true 时新正文（流式与完成段）追加到上一条已发消息。
     * Agent 多轮工具循环中每轮模型输出的正文合并为一条消息，避免拆成多条。
     */
    private var mergeWithPrevious = false

    /** 设置合并模式（工具轮之后的轮次正文合并到上一条消息）。 */
    override fun setMergeWithPrevious(merge: Boolean) {
        mergeWithPrevious = merge
    }

    /** 记录当前已合并正文的段边界（每个工具轮结束处），供 UI 把工具痕迹插入正文流对应位置。 */
    override fun markSegmentEnd() {
        if (completed.isEmpty()) return
        val lastIdx = completed.lastIndex
        val last = completed[lastIdx]
        val boundary = last.content.length
        if (last.toolSegmentEnds.lastOrNull() == boundary) return
        completed[lastIdx] = last.copy(toolSegmentEnds = last.toolSegmentEnds + boundary)
    }

    override fun accept(delta: String): List<StreamCoordinator.Signal> {
        if (delta.isEmpty()) return emptyList()
        val signals = mutableListOf<StreamCoordinator.Signal>()
        val combined = markerTail + delta
        markerTail = ""
        var cursor = 0
        while (cursor < combined.length) {
            if (!inThinkingMarker) {
                val idx = combined.indexOf(THINK_OPEN, cursor)
                if (idx < 0) {
                    val keep = markerPrefixTailLen(combined, cursor, THINK_OPEN)
                    val end = combined.length - keep
                    if (end > cursor) {
                        signals += acceptContent(combined.substring(cursor, end))
                    }
                    markerTail = combined.substring(end)
                    cursor = combined.length
                } else {
                    if (idx > cursor) {
                        signals += acceptContent(combined.substring(cursor, idx))
                    }
                    cursor = idx + THINK_OPEN.length
                    inThinkingMarker = true
                    thinkingChunk.clear()
                }
            } else {
                val idx = combined.indexOf(THINK_CLOSE, cursor)
                if (idx < 0) {
                    val keep = markerPrefixTailLen(combined, cursor, THINK_CLOSE)
                    val end = combined.length - keep
                    if (end > cursor) {
                        thinkingChunk.append(combined.substring(cursor, end))
                    }
                    markerTail = combined.substring(end)
                    cursor = combined.length
                } else {
                    if (idx > cursor) {
                        thinkingChunk.append(combined.substring(cursor, idx))
                    }
                    cursor = idx + THINK_CLOSE.length
                    inThinkingMarker = false
                    if (thinkingChunk.isNotBlank()) {
                        appendThinking(thinkingChunk.toString().trim())
                        thinkingChunk.clear()
                    }
                }
            }
        }
        return signals
    }

    /**
     * 正文段进入原有切分逻辑（句末标点 + 括号切分，含 "0" 结束标记暂存）。
     * 与旧 [accept] 主体行为完全一致。
     */
    private fun acceptContent(delta: String): List<StreamCoordinator.Signal> {
        // 上一块若是暂存的独立 "0"，说明它并非结束标记（后续还有内容），先补回正文
        pendingZeroArtifact?.let { pending ->
            pendingZeroArtifact = null
            buffer.append(pending)
        }
        val signals = mutableListOf<StreamCoordinator.Signal>()
        // 独立内容块 "0" 且前面已有正文：按"结束标记"暂存，等待后续内容或流结束判定
        if (delta == "0" && buffer.isNotBlank()) {
            pendingZeroArtifact = delta
        } else {
            buffer.append(delta)
        }

        // 循环切分：一次 delta 可能包含多个切分点，全部切出
        while (true) {
            val seg = findNextCompleteSegment()
            if (seg != null) {
                // 从 buffer 移除已消费部分
                consumeFromBuffer(seg.consumeEnd)
                if (seg.emit && seg.text.isNotBlank()) {
                    if (seg.isBracket) {
                        // 括号段延迟发出：预占索引，等后续内容触发时再 flush
                        pendingBrackets += currentIndex to seg.text
                        currentIndex++
                    } else {
                        flushPendingBrackets(signals)
                        emitCompleted(signals, seg.text)
                    }
                }
                continue
            }
            // 无完整切分点：硬上限保护，强制切分防 OOM
            if (splitEnabled && buffer.length >= hardCharLimit() && buffer.isNotBlank()) {
                val forced = buffer.toString().trim()
                buffer.clear()
                if (forced.isNotEmpty()) {
                    flushPendingBrackets(signals)
                    emitCompleted(signals, forced)
                }
                continue
            }
            break
        }

        // 单条更新（当前 buffer 内容）：buffer 为空白时不发出（避免空消息/纯空白气泡）
        if (buffer.isNotBlank()) {
            val current = if (mergeWithPrevious && completed.isNotEmpty()) {
                // 合并模式：已发段落（completed 内容）+ 当前 buffer 拼装为流式内容。
                // 关键：只发 Update 信号、不写回 completed——写回会导致后续 delta
                // 把已含 buffer 的内容再叠加一遍（内容重复累积）。
                // 已发段落由 emitCompleted / finalize 真正追加进 completed。
                val lastIdx = completed.lastIndex
                val mergedContent = completed[lastIdx].content + buffer.toString()
                completed[lastIdx].copy(
                    content = mergedContent,
                    tokenCount = TokenEstimator.estimate(mergedContent),
                    isStreaming = true
                )
            } else {
                buildMessage(streaming = true)
            }
            if (knownIds.add(current.id)) {
                signals += StreamCoordinator.Signal.New(current)
            } else {
                signals += StreamCoordinator.Signal.Update(current)
            }
        }

        return signals
    }

    /**
     * 计算 [text] 从 [start] 到末尾中，与 [marker] 开头匹配的最大保留长度
     * （0 ～ marker.length-1）。用于跨 delta 分片识别【思考】/【回答】标记：
     * 尾部恰好是标记前缀时留到下一个分片，避免把半个标记当正文输出。
     */
    private fun markerPrefixTailLen(text: String, start: Int, marker: String): Int {
        val remain = text.length - start
        if (remain <= 0) return 0
        val maxKeep = minOf(remain, marker.length - 1)
        for (keep in maxKeep downTo 1) {
            val tail = text.substring(text.length - keep)
            if (marker.startsWith(tail)) return keep
        }
        return 0
    }

    override fun finalize(): List<StreamCoordinator.Signal> {
        val signals = mutableListOf<StreamCoordinator.Signal>()
        // 流结束时仍未闭合的思考段：
        // - 已有正文 → 附着到 pendingThinking（思考正常展示）；
        // - 无任何正文 → 视为模型未输出【回答】标记，思考内容转正文，避免整段丢失。
        if (inThinkingMarker && thinkingChunk.isNotBlank()) {
            val leftover = thinkingChunk.toString().trim()
            thinkingChunk.clear()
            if (buffer.isBlank() && completed.isEmpty()) {
                buffer.append(leftover)
            } else {
                appendThinking(leftover)
            }
        }
        // 跨分片保留的标记尾部在流结束时已无后续：按当前模式收尾
        if (markerTail.isNotEmpty()) {
            if (inThinkingMarker) {
                thinkingChunk.append(markerTail)
                val leftover = thinkingChunk.toString().trim()
                thinkingChunk.clear()
                if (buffer.isBlank() && completed.isEmpty()) {
                    buffer.append(leftover)
                } else {
                    appendThinking(leftover)
                }
            } else {
                signals += acceptContent(markerTail)
            }
            markerTail = ""
        }
        // 流结束时仍有滞留括号段：合并进最后一条已发消息（或作为整条回复发出），
        // 根因修复——不再留下"只有动作没有下文"的悬空气泡。
        if (pendingBrackets.isNotEmpty()) {
            // 首个预留索引 = 半截括号流式消息的索引；最终消息必须用该索引，
            // 否则会新建一条重复消息而把半截流式消息留在界面上（内容污染）
            val firstReservedIndex = pendingBrackets.first().first
            val trailing = pendingBrackets.joinToString("") { it.second }
            pendingBrackets.clear()
            when {
                buffer.isNotBlank() -> buffer.append(trailing)
                completed.isNotEmpty() -> {
                    val lastIdx = completed.lastIndex
                    val mergedContent = completed[lastIdx].content + trailing
                    val merged = completed[lastIdx].copy(
                        content = mergedContent,
                        tokenCount = TokenEstimator.estimate(mergedContent)
                    )
                    completed[lastIdx] = merged
                    signals += StreamCoordinator.Signal.Update(merged)
                }
                else -> {
                    // 整条回复只有括号段：用首个预留索引产出最终消息。
                    // 若半截括号曾以流式消息发出（同索引），此处走 Update 补全而非新建。
                    val finalMsg = buildMessageFromContentAt(
                        firstReservedIndex,
                        trailing,
                        streaming = false
                    )
                    if (knownIds.add(finalMsg.id)) {
                        signals += StreamCoordinator.Signal.New(finalMsg)
                    } else {
                        signals += StreamCoordinator.Signal.Update(finalMsg)
                    }
                    signals += StreamCoordinator.Signal.Complete(finalMsg)
                    completed += finalMsg
                }
            }
        }
        // buffer 为空白说明全部内容已在 accept 阶段切分完成（或流本就无内容），无需收尾
        if (buffer.isBlank()) return signals
        // 暂存的独立 "0"：若正文以数字结尾则属于数字的一部分（如 10/100），补回；否则为结束标记，丢弃
        pendingZeroArtifact?.let { pending ->
            pendingZeroArtifact = null
            if (buffer.isNotBlank() && buffer.last().isDigit()) {
                buffer.append(pending)
            }
        }
        // 句末标点切分后残留的独立 "0"（如"想你了。0"拆出"想你了。"后剩余 0）：属于结束标记，丢弃
        if (buffer.trim() == "0" && completed.isNotEmpty()) {
            buffer.clear()
            return signals
        }
        // 清洗流末尾孤立的 "0" 结束标记（部分兼容网关/模型在流结束时补发一个 0），
        // 避免每条 AI 消息末尾多出一个 0；正常数字结尾（10、0.0 等）不受影响。
        val cleaned = stripTrailingArtifactZero(buffer.toString())
        buffer.setLength(0)
        buffer.append(cleaned)
        if (buffer.isBlank()) return signals
        val finalMsg = buildMessage(streaming = false)
        if (mergeWithPrevious && completed.isNotEmpty()) {
            // 合并模式：收尾正文追加到上一条已发消息
            val lastIdx = completed.lastIndex
            val mergedContent = completed[lastIdx].content + finalMsg.content
            completed[lastIdx] = completed[lastIdx].copy(
                content = mergedContent,
                tokenCount = TokenEstimator.estimate(mergedContent),
                isStreaming = false
            )
            signals += StreamCoordinator.Signal.Update(completed[lastIdx])
        } else if (finalMsg.id in knownIds) {
            signals += StreamCoordinator.Signal.Complete(finalMsg)
        } else {
            signals += StreamCoordinator.Signal.New(finalMsg)
            signals += StreamCoordinator.Signal.Complete(finalMsg)
        }
        if (!mergeWithPrevious) {
            completed += finalMsg
            currentIndex++
        }
        buffer.clear()
        return signals
    }

    /**
     * 剥离流末尾孤立的 "0" 结束标记。
     *
     * 仅当 "0" 与前文之间存在换行 / 空白 / 全角句末标点（。！？）分隔时剥离，避免误删正文。
     * "0" 前一个字符是数字时不剥离（保护 10 / 100 / 0.0 等正常数字结尾）。
     */
    private fun stripTrailingArtifactZero(text: String): String {
        if (text.length <= 1 || !text.endsWith("0")) return text
        val before = text[text.length - 2]
        if (before.isDigit()) return text
        val separated = before.isWhitespace() || before in "。！？"
        if (!separated) return text
        return text.dropLast(1).trimEnd()
    }

    override fun snapshot(): List<Message> {
        val out = completed.toMutableList()
        // 尚未被后续内容触发的括号段也计入快照（索引已预占，与最终发出时一致）
        pendingBrackets.forEach { (idx, text) ->
            out += buildMessageFromContentAt(idx, text, streaming = false)
        }
        // 仅展示非空白缓冲；纯空白（切分后的换行等）不产生消息
        if (buffer.isNotBlank()) {
            out += buildMessage(streaming = true)
        }
        return out
    }

    /**
     * 按预占索引发出括号段（索引已预留，id 不与后续流式消息冲突）。
     */
    private fun flushPendingBrackets(signals: MutableList<StreamCoordinator.Signal>) {
        pendingBrackets.forEach { (idx, text) ->
            if (mergeWithPrevious && completed.isNotEmpty()) {
                // 合并模式：括号段同样并入已合并消息
                val lastIdx = completed.lastIndex
                val mergedContent = completed[lastIdx].content + text
                completed[lastIdx] = completed[lastIdx].copy(
                    content = mergedContent,
                    tokenCount = TokenEstimator.estimate(mergedContent)
                )
                signals += StreamCoordinator.Signal.Update(completed[lastIdx])
            } else {
                val completedMsg = buildMessageFromContentAt(idx, text, streaming = false)
                if (knownIds.add(completedMsg.id)) {
                    signals += StreamCoordinator.Signal.New(completedMsg)
                } else {
                    signals += StreamCoordinator.Signal.Update(completedMsg)
                }
                signals += StreamCoordinator.Signal.Complete(completedMsg)
                completed += completedMsg
            }
            currentStartTs = System.currentTimeMillis()
        }
        pendingBrackets.clear()
    }

    /**
     * 用当前索引发出完成消息并推进索引。
     */
    private fun emitCompleted(signals: MutableList<StreamCoordinator.Signal>, text: String) {
        if (mergeWithPrevious && completed.isNotEmpty()) {
            // 合并模式：追加到上一条已发消息（工具轮之间正文单条化，原地 Update）
            val lastIdx = completed.lastIndex
            val mergedContent = completed[lastIdx].content + text
            val merged = completed[lastIdx].copy(
                content = mergedContent,
                tokenCount = TokenEstimator.estimate(mergedContent),
                isStreaming = false
            )
            completed[lastIdx] = merged
            signals += StreamCoordinator.Signal.Update(merged)
            return
        }
        val completedMsg = buildMessageFromContentAt(currentIndex, text, streaming = false)
        currentIndex++
        if (knownIds.add(completedMsg.id)) {
            signals += StreamCoordinator.Signal.New(completedMsg)
        } else {
            signals += StreamCoordinator.Signal.Update(completedMsg)
        }
        signals += StreamCoordinator.Signal.Complete(completedMsg)
        completed += completedMsg
        currentStartTs = System.currentTimeMillis()
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
                    val closeIdx = findMatchingCloseBracket(text, i)
                    // 括号前以冒号结尾（说话人标记，如「小A：」或「小A：\n」）时并入括号段：
                    // 避免「小A：（轻笑）」被拆成「小A：」+「（轻笑）」两条，
                    // 前缀在群聊中被剥离后产生空白消息；未闭合时等待更多 delta，不提前发出前缀。
                    if (endsWithColonAfterWhitespace(text.subSequence(0, i))) {
                        if (closeIdx < 0) return null
                        val prefix = text.subSequence(0, i).toString().trimEnd()
                        val merged = prefix + text.subSequence(i, closeIdx + 1)
                        return Segment(
                            text = merged,
                            consumeEnd = closeIdx + 1,
                            emit = hasBracketInnerContent(merged, prefix.length, merged.length - 1),
                            isBracket = true
                        )
                    }
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
                return Segment(
                    text = bracketContent,
                    consumeEnd = closeIdx + 1,
                    emit = hasBracketInnerContent(bracketContent, 0, bracketContent.length - 1),
                    isBracket = true
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

    /** 判断 [text] 末尾（忽略尾部空白）是否为冒号（全角/半角说话人标记）。 */
    private fun endsWithColonAfterWhitespace(text: CharSequence): Boolean {
        var p = text.length - 1
        while (p >= 0 && text[p].isWhitespace()) p--
        return p >= 0 && (text[p] == '：' || text[p] == ':')
    }

    /** 括号段是否含实质内容（排除括号符号与空白）；仅含空括号（如「（）」）时不发出。 */
    private fun hasBracketInnerContent(text: String, bracketStart: Int, bracketEnd: Int): Boolean =
        text.subSequence(bracketStart, bracketEnd + 1).any { c ->
            !c.isWhitespace() && matchingClose(c) == null && matchingOpen(c) == null
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
        // 本地思考附着到指定索引的消息上（首条或工具系列后追加），随消息一起持久化
        thinking = if (thinkingForIndex == currentIndex) pendingThinking else "",
        senderId = senderId
    )

    /**
     * 用指定索引与内容构建完成消息（括号段使用预占索引，避免 id 冲突）。
     */
    private fun buildMessageFromContentAt(
        index: Int,
        content: String,
        streaming: Boolean
    ): Message = Message(
        id = "${conversationId}_${runId}_ai_$index",
        conversationId = conversationId,
        role = com.quiddity.app.data.model.Role.ASSISTANT,
        content = content,
        timestamp = currentStartTs,
        tokenCount = TokenEstimator.estimate(content),
        isStreaming = streaming,
        // 本地思考附着到指定索引的消息上（首条或工具系列后追加），随消息一起持久化
        thinking = if (thinkingForIndex == index) pendingThinking else "",
        senderId = senderId
    )

    private companion object {
        const val HARD_LIMIT_MULTIPLIER = QuiddityConstants.SPLITTER_HARD_LIMIT_MULTIPLIER
        const val CHARS_PER_TOKEN = QuiddityConstants.SPLITTER_CHARS_PER_TOKEN
        const val MAX_HARD_LIMIT_CHARS = QuiddityConstants.SPLITTER_MAX_HARD_LIMIT_CHARS
        /** 提示词引导的思考开始标记。 */
        const val THINK_OPEN = "【思考】"
        /** 提示词引导的思考结束标记。 */
        const val THINK_CLOSE = "【回答】"
    }
}
