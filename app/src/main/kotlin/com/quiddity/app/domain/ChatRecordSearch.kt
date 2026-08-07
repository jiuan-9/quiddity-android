package com.quiddity.app.domain

import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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


/*
 * 协作说明（临时，交付前删除）：检索链路改造进行中。本文件与 MemorySearch.kt、
 * NgramRecall.kt 由检索任务修改；正在同时修改 UI 的同事请勿改动这三份文件。
 * 本文件 search() 打分内核已切换为 n-gram + IDF（NgramRecall），保留原参数与返回结构。
 */



/**
 * 本地聊天记录检索（search_chat 工具后端）。
 *
 * 目标：让模型能够在**用户本机完整聊天记录**中按关键词查找历史消息并引用，
 * 与 [MemorySearch]（压缩记忆检索）互补——记忆是提炼后的摘要，聊天记录是原始内容。
 *
 * 检索策略与 [MemorySearch] 一致（词元命中打分），但对每条消息逐条评分，
 * 返回命中消息的角色标签 + 内容摘录 + 发送时间，供模型在回复中引用。
 */
object ChatRecordSearch {

    /** 单条消息摘录长度（字符）。 */
    const val EXCERPT_CHARS = 120

    /** 返回的最大命中消息条数。 */
    const val MAX_RESULTS = 5

    /** 结果总长上限（字符）。 */
    const val MAX_TOTAL_CHARS = 3_000

    const val NOT_FOUND_TEXT = "未找到相关的聊天记录"

    data class Result(
        val found: Boolean,
        val content: String
    )

    /**
     * Search excerpt: keyword-centered window with hit ranges.
     *
     * @param text display text (may contain leading/trailing ellipsis)
     * @param highlights hit ranges inside [text], merged and sorted
     */
    data class Excerpt(
        val text: String,
        val highlights: List<IntRange>
    )

    /**
     * Builds a keyword-centered excerpt for search result rows.
     *
     * For long messages the window (up to [maxChars] chars) is centered on the
     * first hit, with ellipses appended around it; all hits inside the window
     * are returned (case-insensitive, overlapping ranges merged). Returns null
     * when the query has no terms or no hit, so callers can fall back.
     */
    fun buildExcerpt(content: String, query: String, maxChars: Int = 80): Excerpt? {
        val normalized = content.replace("\n", " ").trim()
        if (normalized.isEmpty()) return null
        val terms = MemorySearch.extractTerms(query)
        if (terms.isEmpty()) return null
        val lower = normalized.lowercase()

        var firstHit = -1
        var firstTermLen = 0
        for (term in terms) {
            val idx = lower.indexOf(term)
            if (idx >= 0 && (firstHit < 0 || idx < firstHit)) {
                firstHit = idx
                firstTermLen = term.length
            }
        }
        if (firstHit < 0) return null

        val len = normalized.length
        val (start, end) = if (len <= maxChars) {
            0 to len
        } else {
            var s = (firstHit - (maxChars - firstTermLen) / 2).coerceAtLeast(0)
            var e = (s + maxChars).coerceAtMost(len)
            if (e == len) s = (e - maxChars).coerceAtLeast(0)
            s to e
        }

        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < len) "…" else ""
        val text = prefix + normalized.substring(start, end) + suffix

        val offsetBase = prefix.length - start
        val ranges = mutableListOf<IntRange>()
        for (term in terms) {
            var from = start
            while (true) {
                val idx = lower.indexOf(term, from)
                if (idx < 0 || idx >= end) break
                if (idx + term.length <= end) {
                    ranges += (idx + offsetBase) until (idx + term.length + offsetBase)
                }
                from = idx + term.length
            }
        }
        return Excerpt(text, mergeRanges(ranges))
    }

    private fun mergeRanges(ranges: List<IntRange>): List<IntRange> {
        if (ranges.isEmpty()) return emptyList()
        val sorted = ranges.sortedBy { it.first }
        val merged = mutableListOf(sorted.first())
        for (range in sorted.drop(1)) {
            val last = merged.last()
            if (range.first <= last.last) {
                merged[merged.size - 1] = last.first..maxOf(last.last, range.last)
            } else {
                merged += range
            }
        }
        return merged
    }

    /**
     * 在当前会话的 [messages] 中按 [query] 检索历史消息。
     *
     * @param messages 该会话的完整消息列表（调用方已过滤 isNotice 提示气泡）
     * @param query 模型传入的检索关键词
     * @return [Result.found]=true 时返回 `[角色] 内容摘录…（HH:mm）` 列表；否则返回未找到提示
     */
    fun search(messages: List<Message>, query: String): Result {
        if (messages.isEmpty()) {
            return Result(found = false, content = NOT_FOUND_TEXT)
        }
        val terms = MemorySearch.extractTerms(query)
        if (terms.isEmpty()) {
            // 无有效关键词：返回最近几条消息作为上下文
            val recent = messages.takeLast(MAX_RESULTS)
            val content = buildList(recent)
            return Result(found = true, content = content)
        }

        // n-gram + IDF 打分（NgramRecall）：中文口语/词序变化也能命中
        val top = NgramRecall.rank(messages.map { it.content }, query, topK = MAX_RESULTS)
            .mapNotNull { hit -> messages.getOrNull(hit.index) }
        if (top.isEmpty()) {
            return Result(found = false, content = NOT_FOUND_TEXT)
        }
        return Result(found = true, content = buildList(top))
    }

    /**
     * 返回按相关度降序排列的命中消息列表（供聊天记录搜索界面逐条展示）。
     * 无关键词或列表为空时返回空列表。
     */
    fun searchResults(messages: List<Message>, query: String): List<Message> {
        if (messages.isEmpty()) return emptyList()
        val terms = MemorySearch.extractTerms(query)
        if (terms.isEmpty()) return emptyList()
        return messages.mapNotNull { message ->
            val lower = message.content.lowercase()
            val hits = terms.sumOf { term ->
                var count = 0
                var from = 0
                while (true) {
                    val idx = lower.indexOf(term, from)
                    if (idx < 0) break
                    count++
                    from = idx + term.length
                }
                count
            }
            if (hits > 0) message to hits else null
        }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun buildList(messages: List<Message>): String {
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val sb = StringBuilder("【聊天记录检索结果】\n")
        for (message in messages) {
            if (sb.length >= MAX_TOTAL_CHARS) break
            val label = when (message.role) {
                Role.USER -> "用户"
                Role.ASSISTANT -> "AI"
                Role.SYSTEM -> "系统"
            }
            val time = Instant.ofEpochMilli(message.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .format(timeFormatter)
            val excerpt = message.content.replace("\n", " ").trim()
                .let { if (it.length > EXCERPT_CHARS) it.take(EXCERPT_CHARS) + "…" else it }
            val line = "[$label] $excerpt（$time）"
            val remain = MAX_TOTAL_CHARS - sb.length
            if (line.length <= remain) {
                sb.append(line).append("\n")
            } else {
                sb.append(line.take(remain))
                break
            }
        }
        return sb.toString().trim()
    }
}
