package com.quiddity.app.domain

import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NgramRecallSmokeTest {

    private fun msg(id: String, content: String): Message =
        Message(id = id, conversationId = "c1", role = Role.USER, content = content, timestamp = id.toLong() * 1000)

    private val messages = listOf(
        msg("1", "我们家的咪咪是只三花猫，上周把阳台花盆打翻了"),
        msg("2", "在考虑从宠物店领养一只布偶猫，等攒够钱"),
        msg("3", "人民路那家角落咖啡店老板会拉花，拿铁特别好喝"),
        msg("4", "医生说我不该再喝咖啡会失眠，可戒不掉"),
        msg("5", "下周就搬去城东新区了，楼下有个大公园"),
        msg("6", "跟老李学吉他两个月了，手指全是茧"),
        msg("7", "刚看完《三体》，最喜欢第二部的面壁者计划"),
        msg("8", "我姐在常州卖汽车配件，收入还挺好"),
        msg("9", "昨天手机屏摔碎了，换屏花了八百"),
        msg("10", "今天也辛苦了，晚安"),
    )

    @Test
    fun `spoken query without noun no longer hits not-found`() {
        // 旧打分：整句作一个词元去 indexOf → 必然 0 命中、返回 NOT_FOUND。
        // 新打分：字符 bigram 重叠，应能找到含"猫咪/花盆/打翻"的那条。
        val r = ChatRecordSearch.search(messages, "我们家的猫是什么毛色来着？")
        assertTrue(r.found, "口语问句应能召回内容，实际 found=${r.found}")
        assertTrue(
            "猫" in r.content || "阳台" in r.content || "花盆" in r.content,
            "应命中含猫的条目，实际=${r.content}"
        )
    }

    @Test
    fun `spoken recollection about location hits`() {
        // 注意：纯口语"我搬到哪"与原文"搬去城东"无字符重叠，这类同义鸿沟
        // 依赖压缩提示词的别名线索（(搬家/城东)）。带实体部分重叠时 n-gram 即可命中。
        val r = ChatRecordSearch.search(messages, "我说过我要搬到城东是吧？")
        assertTrue(r.found, "搬家口语问应命中，实际=${r.found}")
        assertTrue("城东" in r.content || "公园" in r.content)
    }

    @Test
    fun `keyword in memory hits via ngram`() {
        val r = ChatRecordSearch.search(messages, "吉他 乐器 学")
        assertTrue(r.found)
        assertTrue("吉他" in r.content)
    }

    @Test
    fun `not found still returns NOT_FOUND for unrelated query`() {
        val r = ChatRecordSearch.search(messages, "导弹发射基地坐标")
        assertFalse(r.found)
        assertEquals(ChatRecordSearch.NOT_FOUND_TEXT, r.content)
    }

    @Test
    fun `english term case-insensitive in memory`() {
        val r = MemorySearch.search("用户的项目名称是 Quiddity，8 月发版", "quiddity 发版")
        assertTrue(r.found)
        assertTrue(r.content.lowercase().contains("quiddity"))
    }

    @Test
    fun `many candidates capped by MAX_RESULTS`() {
        val many = (1..30).map { msg(it.toString(), "内容包含旅行计划 $it") }
        val r = ChatRecordSearch.search(many, "旅行")
        assertTrue(r.found)
        val count = Regex("""\[用户\]""").findAll(r.content).count()
        assertTrue(count > 0 && count <= ChatRecordSearch.MAX_RESULTS)
    }
}
