package com.quiddity.app.domain

/*
 * ============================================================================
 * 协作说明（临时，交付前删除）：本文件由 OpenAI-codex（UI 侧）与
 * Quiddity 检索链路改造同步工作期间新增。请不要修改与此无关的检索相关文件
 * （MemorySearch.kt / ChatRecordSearch.kt / 本文件），如需触碰请先沟通。
 * 本文件实现"字符 n-gram + IDF"本地召回，作为 read_memory / search_chat 的新打分核心。
 * ============================================================================
 */

/**
 * 轻量本地召回器（n-gram + IDF），纯 Kotlin、零 Android 依赖。
 *
 * 定位：取代 [MemorySearch] / [ChatRecordSearch] 里"整句作为一个词元、
 * 再做子串匹配"的打分方式——那种方式对自然中文句完全失效（整句 indexOf 永远找不到）。
 * 本器改为字符 bigram 匹配 + IDF 加权，天然容忍词序变化与口语差异，
 * 并能利用压缩提示词里新增的"别名括号"（如 三花猫（毛色/花色））。
 *
 * 算法：
 * 1. 文本小写 → 去语气/助词字 → 取字符 bigram 集合；
 * 2. 对候选段落在全语料上计算每个 gram 的 df → idf = ln((N+1)/(df+0.5))；
 * 3. 命中分 = 候选文本里属于 query grams 的 idf 之和 / query 总权重（归一化）；
 * 4. 按得分降序、原文顺序升序截断 [topK]。
 */
object NgramRecall {

    /** 语气/助词字符（只删无信息量的；猫/毛/咖/啡 等信息字一律保留）。 */
    private val stopChars: Set<Char> = setOf(
        '的', '了', '吗', '呢', '吧', '啊', '呀', '哦', '嗯', '嘛', '么', '是', '在',
        '什', '啥', '这', '那', '个', '就', '都', '也', '很', '还', '有', '不', '没',
        '会', '能', '要', '去', '来', '下', '种', '事', '问', '着', '着'
    )

    /** 单个候选（index = 在传入文档列表中的下标）。 */
    data class Scored(val index: Int, val score: Double) {
        override fun toString(): String = "#$index(score=${"%.2f".format(score)})"
    }

    /**
     * 在 [documents] 中按 [query] 召回 top [topK]（得分>0）。
     * @return 按得分降序（同分按原顺序）；query 为空或无 gram → 空列表
     */
    fun rank(documents: List<String>, query: String, topK: Int, minScore: Double = 0.0): List<Scored> {
        if (documents.isEmpty() || query.isBlank()) return emptyList()
        val qg = grams(query)
        if (qg.isEmpty()) return emptyList()

        // 1. 计算语料 df（同一文档内去重）
        val df = HashMap<String, Int>()
        val seen = HashSet<String>()
        for (doc in documents) {
            seen.clear()
            for (g in grams(doc)) {
                if (!seen.contains(g)) { seen.add(g); df[g] = (df[g] ?: 0) + 1 }
            }
        }
        val n = documents.size.coerceAtLeast(1)
        fun weight(g: String): Double = Math.log((n + 1.0) / ((df[g] ?: 0) + 0.5))

        // 2. query 权重
        val qWeight = qg.sumOf { weight(it) }
        if (qWeight <= 0) return emptyList()

        // 3. 逐文档打分并排序
        val result = ArrayList<Scored>()
        documents.forEachIndexed { i, doc ->
            var score = 0.0
            val docGrams = grams(doc)
            for (g in docGrams) {
                val matched = qg.firstOrNull { it == g || (it.length == 1 && g.contains(it)) }
                if (matched != null) score += weight(matched)
            }
            val norm = score / qWeight
            if (norm > minScore) result.add(Scored(i, norm))
        }
        result.sortWith(compareByDescending<Scored> { it.score }.thenBy { it.index })
        return if (topK > 0) result.take(topK) else result
    }

    /**
     * 提取字符 bigram 集（小写归一，去停用字）。长度为 1 时退回以单字成集，避免短查询落空。
     */
    fun grams(text: String): Set<String> {
        val lower = text.lowercase()
        val cleaned = buildString {
            for (ch in lower) {
                if (ch.isLetterOrDigit() && ch !in stopChars) append(ch)
            }
        }
        return buildSet {
            if (cleaned.length >= 2) {
                for (i in 0..cleaned.length - 2) add(cleaned.substring(i, i + 2))
            } else if (cleaned.isNotEmpty()) {
                add(cleaned)
            }
        }
    }
}
