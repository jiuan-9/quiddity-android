package com.quiddity.app.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalThinkerTest {

    @Test
    fun think_noFixedTemplatePrefix() {
        val out = LocalThinker.think("帮我看看手机上有哪些应用", true, "Agent")
        assertFalse(out.contains("用户想："), "思考不应带固定模板前缀")
        assertFalse(out.contains("直接回答即可"), "思考不应带模板结尾")
    }

    @Test
    fun think_toolIntent_mentionsQueryingFirst() {
        val out = LocalThinker.think("看看哪个应用耗电", true, "Agent")
        assertTrue(out.contains("查"), "涉及手机信息应提到先查证")
        assertFalse(out.contains("用户想："))
    }

    @Test
    fun think_greeting_isNaturalFirstPerson() {
        val out = LocalThinker.think("嗨", false, "小美")
        assertTrue(out.contains("应") || out.contains("回"), "打招呼思考应自然")
        assertFalse(out.contains("TA"), "思考不应出现 TA")
        assertFalse(out.contains("被设定"), "思考不应依赖被设定的称呼")
    }

    @Test
    fun think_question_variesFromToolIntent() {
        val question = LocalThinker.think("你怎么看待这件事", false, "小美")
        val tool = LocalThinker.think("查一下流量排行", true, "Agent")
        assertTrue(question != tool)
        assertFalse(question.contains("查一下"))
        assertTrue(tool.contains("查"))
        assertFalse(question.contains("TA"), "思考不应出现 TA")
        assertFalse(tool.contains("TA"), "思考不应出现 TA")
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
            "Agent"
        )
        assertTrue(out.contains("我查了 app_install_info、app_battery"))
        assertTrue(out.contains("app_battery 这边没查成"))
        assertTrue(out.contains("别糊弄"))
        assertFalse(out.contains("用户想："))
    }
}
