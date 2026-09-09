package com.quiddity.app.domain.spy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SpyLlmPromptTest {

    @Test
    fun speakSystem_containsRoleAndWordButNoOthers() {
        val sys = SpyLlmPrompt.buildSpeakSystemMessage("阿哲", "性格冷静", SpyRole.CIVILIAN, "苹果")
        assertEquals(true, sys.contains("平民"))
        assertEquals(true, sys.contains("苹果"))
        assertEquals(true, sys.contains("不能"))
    }

    @Test
    fun speakSystem_spyRoleMentionsDisguise() {
        val sys = SpyLlmPrompt.buildSpeakSystemMessage("阿哲", null, SpyRole.SPY, "梨")
        assertEquals(true, sys.contains("卧底"))
        assertEquals(true, sys.contains("圆滑"))
    }

    @Test
    fun speakSystem_blankRoleMentionsNoWord() {
        val sys = SpyLlmPrompt.buildSpeakSystemMessage("阿哲", null, SpyRole.BLANK, "", SpyGameMode.BLANK)
        assertEquals(true, sys.contains("白板"))
        assertEquals(true, sys.contains("没有词"))
        assertEquals(false, sys.contains("你抽到的词是"))
    }

    @Test
    fun speakSystem_doubleSpyMentionsCompanion() {
        val sys = SpyLlmPrompt.buildSpeakSystemMessage("阿哲", null, SpyRole.SPY, "梨", SpyGameMode.DOUBLE_SPY)
        assertEquals(true, sys.contains("双卧底"))
        assertEquals(true, sys.contains("另一名卧底"))
    }

    @Test
    fun pkSpeakUser_listsCandidatesAndRound() {
        val user = SpyLlmPrompt.buildPkSpeakUserMessage(
            round = 2,
            pkCandidates = listOf(0 to "小美", 1 to "阿哲"),
            pkSpeeches = listOf(SpySpeech(2, 0, "我是自己人")),
            playerName = "阿哲"
        )
        assertEquals(true, user.contains("第 2 轮"))
        assertEquals(true, user.contains("0 → 小美"))
        assertEquals(true, user.contains("阿哲"))
    }

    @Test
    fun speakUser_listsPriorSpeeches() {
        val user = SpyLlmPrompt.buildSpeakUserMessage(
            round = 2,
            speeches = listOf(
                SpySpeech(1, 3, "我的是水果"),
                SpySpeech(1, 0, "我也感觉是水果")
            ),
            playerName = "阿哲"
        )
        assertEquals(true, user.contains("第 2 轮"))
        assertEquals(true, user.contains("我的是水果"))
        assertEquals(true, user.contains("阿哲"))
    }

    @Test
    fun voteSystem_requiresVoteInstruction() {
        val sys = SpyLlmPrompt.buildVoteSystemMessage("小美", null, SpyRole.CIVILIAN, "苹果")
        assertEquals(true, sys.contains("VOTE"))
    }

    @Test
    fun pkVoteSystem_requiresVoteInstruction() {
        val sys = SpyLlmPrompt.buildPkVoteSystemMessage("小美", null, SpyRole.CIVILIAN, "苹果")
        assertEquals(true, sys.contains("VOTE"))
        assertEquals(true, sys.contains("二选一"))
    }

@Test
    fun voteUser_listsCandidatesIncludingIndexAndName() {
        val user = SpyLlmPrompt.buildVoteUserMessage(
            round = 2,
            candidates = listOf(1 to "阿哲", 2 to "路人甲", 3 to "路人丙"),
            speeches = listOf(SpySpeech(2, 1, "我是水果")),
            voterName = "小美"
        )
        assertEquals(true, user.contains("1 → 阿哲"))
        assertEquals(true, user.contains("2 → 路人甲"))
        assertEquals(true, user.contains("小美"))
    }

    @Test
    fun pkVoteUser_listsOnlyCandidates() {
        val user = SpyLlmPrompt.buildPkVoteUserMessage(
            round = 2,
            candidates = listOf(0 to "小美", 1 to "阿哲"),
            speeches = listOf(SpySpeech(2, 0, "我是自己人")),
            voterName = "路人甲"
        )
        assertEquals(true, user.contains("0 → 小美"))
        assertEquals(true, user.contains("1 → 阿哲"))
        assertEquals(true, user.contains("VOTE"))
    }

    @Test
    fun parseVoteReply_acceptsChineseAndEnglishParens() {
        assertEquals(2, SpyLlmPrompt.parseVoteReply("VOTE(2)", setOf(0, 1, 2, 3)))
        assertEquals(1, SpyLlmPrompt.parseVoteReply("VOTE（1）", setOf(0, 1, 2, 3)))
        assertEquals(3, SpyLlmPrompt.parseVoteReply("我选 VOTE( 3 )", setOf(0, 1, 2, 3)))
    }

    @Test
    fun parseVoteReply_rejectsOutOfRangeAndNoMatch() {
        assertNull(SpyLlmPrompt.parseVoteReply("VOTE(9)", setOf(0, 1, 2)))
        assertNull(SpyLlmPrompt.parseVoteReply("随便", setOf(0, 1, 2)))
        assertNull(SpyLlmPrompt.parseVoteReply("", setOf(0, 1, 2)))
    }

    @Test
    fun parseVoteReply_rejectsSelfVoteElligibility() {
        // valid 集合里不含自己，越界即拒绝
        assertNull(SpyLlmPrompt.parseVoteReply("VOTE(0)", setOf(1, 2, 3)))
        assertNotNull(SpyLlmPrompt.parseVoteReply("VOTE(2)", setOf(1, 2, 3)))
    }

    @Test
    fun wordGenSystem_demandsJsonArray() {
        val sys = SpyLlmPrompt.buildWordGenSystemMessage()
        assertEquals(true, sys.contains("JSON 数组"))
        assertEquals(true, sys.contains("civilian"))
        assertEquals(true, sys.contains("spy"))
    }

    @Test
    fun wordGenUser_mentionsTopicAndCount() {
        val user = SpyLlmPrompt.buildWordGenUserMessage(count = 10, topic = "水果")
        assertEquals(true, user.contains("水果"))
        assertEquals(true, user.contains("10"))
    }

    @Test
    fun parseWordGenReply_extractsPairsFromPlainJson() {
        val pairs = SpyLlmPrompt.parseWordGenReply(
            """[{"civilian":"苹果","spy":"梨"},{"civilian":"咖啡","spy":"奶茶"}]"""
        )
        assertEquals(2, pairs.size)
        assertEquals("苹果", pairs[0].civilian)
        assertEquals("梨", pairs[0].spy)
        assertEquals("咖啡", pairs[1].civilian)
    }

    @Test
    fun parseWordGenReply_stripsCodeBlockAndNoise() {
        val pairs = SpyLlmPrompt.parseWordGenReply(
            "```json\n好的，这是生成的词对：\n[{\"civilian\":\"图书馆\",\"spy\":\"书店\"}]\n```"
        )
        assertEquals(1, pairs.size)
        assertEquals("图书馆", pairs[0].civilian)
        assertEquals("书店", pairs[0].spy)
    }

    @Test
    fun parseWordGenReply_returnsEmptyOnGarbage() {
        assertEquals(0, SpyLlmPrompt.parseWordGenReply("").size)
        assertEquals(0, SpyLlmPrompt.parseWordGenReply("随便说点什么").size)
        assertEquals(0, SpyLlmPrompt.parseWordGenReply("[{\"civilian\":\"\",\"spy\":\"\"}]").size)
    }
}
