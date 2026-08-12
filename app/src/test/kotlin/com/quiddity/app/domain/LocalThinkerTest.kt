package com.quiddity.app.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalThinkerTest {

    @Test
    fun think_noFixedTemplatePrefix() {
        val out = LocalThinker.think("帮我看看手机上有哪些应用", true, "Agent", "小明")
        assertFalse(out.contains("用户想："), "思考不应带固定模板前缀")
        assertFalse(out.contains("直接回答即可"), "思考不应带模板结尾")
    }

    @Test
    fun think_toolIntent_mentionsQueryingFirst() {
        val out = LocalThinker.think("看看哪个应用耗电", true, "Agent", "小明")
        assertTrue(out.contains("查"), "涉及手机信息应提到先查证")
        assertFalse(out.contains("用户想："))
    }

    @Test
    fun think_greeting_startsWithCallNameAndIsNatural() {
        val out = LocalThinker.think("嗨", false, "小美", "小明", "宝宝")
        assertTrue(out.startsWith("宝宝"), "打招呼思考应以称呼开头")
        assertTrue(out.contains("应") || out.contains("回"), "打招呼思考应自然")
        assertFalse(out.contains("TA"), "思考不应出现 TA")
    }

    @Test
    fun think_question_variesFromToolIntent() {
        val question = LocalThinker.think("你怎么看待这件事", false, "小美", "小明")
        val tool = LocalThinker.think("查一下流量排行", true, "Agent", "小明")
        assertTrue(question != tool)
        assertFalse(question.contains("查一下"), "提问思考不应是工具思考")
        assertTrue(tool.contains("查"), "工具思考应提到查证")
    }

    @Test
    fun think_usesCallNameFirstAndNeverTa() {
        val out = LocalThinker.think("看看哪个应用耗电", true, "Agent", "小明", "宝宝")
        assertTrue(out.contains("宝宝"), "设置了称呼时应优先使用称呼")
        assertTrue(out.startsWith("宝宝"), "称呼应放在句首")
        assertFalse(out.contains("TA"), "思考不应出现 TA")
        assertFalse(out.contains("ta"), "思考不应出现 ta")
    }

    @Test
    fun think_fallsBackToUserName() {
        val out = LocalThinker.think("今天天气怎么样", false, "小美", "小明")
        assertTrue(out.contains("小明"), "未设置称呼时应回退到用户名")
        assertFalse(out.contains("TA"), "思考不应出现 TA")
    }

    @Test
    fun isToolError_detectsCommonFailures() {
        assertTrue(LocalThinker.isToolError("未获得 Shizuku 授权"))
        assertTrue(LocalThinker.isToolError("未找到应用 com.x"))
        assertTrue(LocalThinker.isToolError("工具未启用"))
        assertTrue(LocalThinker.isToolError("读取失败"))
        assertFalse(LocalThinker.isToolError("微信：com.tencent.mm"))
        assertFalse(LocalThinker.isToolError("首次安装：2024-07-11 18:43"))
    }

    @Test
    fun thinkAfterTools_reportsBadAndGoodNaturally() {
        val out = LocalThinker.thinkAfterTools(
            listOf(
                "app_install_info" to "首次安装：2024-07-11",
                "app_battery" to "未获得 Shizuku 授权"
            ),
            "Agent",
            "小明",
            "宝宝"
        )
        assertTrue(out.contains("我查了 app_install_info、app_battery"))
        assertTrue(out.contains("app_battery 这边没查成"))
        assertTrue(out.contains("别糊弄"))
        assertTrue(out.contains("宝宝"), "工具后思考应使用称呼")
        assertFalse(out.contains("TA"), "思考不应出现 TA")
        assertFalse(out.contains("用户想："))
    }
}
