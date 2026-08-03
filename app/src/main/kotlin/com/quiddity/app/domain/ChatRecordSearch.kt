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

        data class Scored(val message: Message, val score: Int, val order: Int)
        val scored = messages.mapIndexedNotNull { index, message ->
            var score = 0
            val lower = message.content.lowercase()
            for (term in terms) {
                var from = 0
                while (true) {
                    val hit = lower.indexOf(term, from)
                    if (hit < 0) break
                    score++
                    from = hit + term.length
                }
            }
            if (score > 0) Scored(message, score, index) else null
        }

        if (scored.isEmpty()) {
            return Result(found = false, content = NOT_FOUND_TEXT)
        }

        val top = scored
            .sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.order })
            .take(MAX_RESULTS)
            .map { it.message }
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
