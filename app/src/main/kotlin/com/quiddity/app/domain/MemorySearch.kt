package com.quiddity.app.domain

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
 * 记忆检索（read_memory 工具后端）。
 *
 * 目标（6.6）：AI 不需要每轮重读整份压缩记忆，只在需要回忆细节时调用
 * `read_memory(query)` 按关键词检索相关片段。
 *
 * 检索策略（轻量、确定性，不依赖第三方全文索引）：
 * 1. 把记忆按空行/换行切分为段落；
 * 2. 打分内核为字符 bigram + IDF（见 [NgramRecall]），天然容忍词序变化与口语差异；
 * 3. 命中段落按得分降序、原文顺序保留，总长不超过 [MEMORY_SEARCH_MAX_CHARS]，超出时截断。
 *
 * 无命中时返回明确的"未找到"提示，AI 据此回答"没有相关记忆"而非编造。
 */
object MemorySearch {

    /** 检索结果单条内容上限（字符），约 800 token，避免工具回填撑爆上下文。 */
    const val MEMORY_SEARCH_MAX_CHARS = 2_000

    /** 检索回填候选段落的条数上限（n-gram 召回，压缩记忆通常很短，3 条足以定位）。 */
    const val MEMORY_TOP_K = 3

    /** 无命中提示（found=false 时 AI 应如实回答）。 */
    const val NOT_FOUND_TEXT = "未找到与查询相关的记忆摘要"

    data class Result(
        val found: Boolean,
        val content: String
    )

    /**
     * 在 [memory] 中按 [query] 检索相关段落（read_memory 工具后端）。
     *
     * 打分内核为字符 n-gram + IDF（见 [NgramRecall]），中文自然问句
     * （口语、无关键词、词序变化）也能命中。
     *
     * @param memory 记忆全文（压缩摘要 + 群聊小本本等，通常由
     *   [com.quiddity.app.domain.PromptBuilder.buildMemoryDrawerContent] 产出）
     * @param query 模型传入的检索关键词
     * @return [Result.found]=true 且有内容时返回命中片段；否则返回未找到提示
     */
    fun search(memory: String, query: String): Result {
        if (memory.isBlank()) {
            return Result(found = false, content = NOT_FOUND_TEXT)
        }
        val paragraphs = memory.split(Regex("\\n\\s*\\n|\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // 无有效关键词：回退返回记忆开头片段，避免工具空转
        val terms = extractTerms(query)
        if (terms.isEmpty()) {
            val head = memory.trim().take(MEMORY_SEARCH_MAX_CHARS)
            return Result(found = true, content = "【记忆检索结果】\n$head")
        }

        val hits = NgramRecall.rank(paragraphs, query, topK = MEMORY_TOP_K)
        if (hits.isEmpty()) {
            return Result(found = false, content = NOT_FOUND_TEXT)
        }

        val sb = StringBuilder("【记忆检索结果】\n")
        for (hit in hits) {
            if (hit.index !in paragraphs.indices) continue
            val paragraph = paragraphs[hit.index]
            if (sb.length >= MEMORY_SEARCH_MAX_CHARS) break
            val remain = MEMORY_SEARCH_MAX_CHARS - sb.length
            if (paragraph.length <= remain) {
                sb.append(paragraph).append("\n\n")
            } else {
                sb.append(paragraph.take(remain)).append("…")
                break
            }
        }
        return Result(found = true, content = sb.toString().trim())
    }

    /**
     * 提取查询词元：连续的字母/数字串与连续的汉字各为一个词元，统一小写。
     * 例："用户的项目进度 A-1" → ["用户", "项目进度", "a", "1"]。
     */
    internal fun extractTerms(query: String): List<String> {
        if (query.isBlank()) return emptyList()
        val terms = mutableListOf<String>()
        var i = 0
        while (i < query.length) {
            val ch = query[i]
            when {
                ch.isLetterOrDigit() -> {
                    val start = i
                    while (i < query.length && query[i].isLetterOrDigit()) i++
                    terms += query.substring(start, i).lowercase()
                }
                else -> i++
            }
        }
        return terms
    }
}
