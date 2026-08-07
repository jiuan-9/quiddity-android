package com.quiddity.app.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NgramRecallTest {

    @Test
    fun singleCharQueryHitsBigramContainingIt() {
        val docs = listOf(
            "我们家有只猫，很可爱",
            "今天天气不错"
        )
        val hits = NgramRecall.rank(docs, "猫", topK = 5)
        assertTrue(hits.isNotEmpty(), "单字查询应命中包含该字的双字组合")
        assertEquals(0, hits.first().index)
    }

    @Test
    fun allStopCharsQueryReturnsEmpty() {
        val docs = listOf("我们计划下个月去成都旅行")
        val hits = NgramRecall.rank(docs, "的了", topK = 5)
        assertTrue(hits.isEmpty(), "纯停用字查询无有效 gram，应返回空")
    }

    @Test
    fun englishQueryIsCaseInsensitive() {
        val docs = listOf(
            "项目名称是 Quiddity",
            "其他无关内容"
        )
        val hits = NgramRecall.rank(docs, "quiddity", topK = 5)
        assertTrue(hits.isNotEmpty())
        assertEquals(0, hits.first().index)
    }

    @Test
    fun emojiAndPunctuationIgnored() {
        val docs = listOf(
            "今天天气不错😊，去公园吧！",
            "完全无关的内容 abc"
        )
        val hits = NgramRecall.rank(docs, "公园", topK = 5)
        assertTrue(hits.isNotEmpty())
        assertEquals(0, hits.first().index)
    }

    @Test
    fun spokenQueryRecallsRelevantDocument() {
        val docs = listOf(
            "我们家的猫是只三花猫，上周把阳台花盆打翻了",
            "在考虑从宠物店领养一只布偶猫，等攒够钱"
        )
        val hits = NgramRecall.rank(docs, "我们家的猫是什么毛色来着？", topK = 5)
        assertTrue(hits.isNotEmpty(), "口语查询应通过字符 bigram 重叠召回相关文档")
        assertEquals(0, hits.first().index)
    }
}
