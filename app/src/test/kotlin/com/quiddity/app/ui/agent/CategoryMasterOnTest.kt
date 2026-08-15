package com.quiddity.app.ui.agent

import com.quiddity.app.data.local.AgentToolSwitches
import com.quiddity.app.domain.agent.AgentToolCategory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 分类主开关状态测试（1.6.1）：
 * 主开关只统计「权限已授予」的工具，避免未授权时五分类全部显示开启的误导。
 */
class CategoryMasterOnTest {

    private fun noPermissions(): (String) -> Boolean = { false }

    private fun allPermissions(): (String) -> Boolean = { true }

    private fun switches(vararg enabled: Pair<String, Boolean>): AgentToolSwitches =
        AgentToolSwitches(tools = mapOf(*enabled))

    @Test
    fun noPermissionGranted_allCategoriesShowOff() {
        // 未授予任何权限：所有工具都不可用 → 每类主开关都显示关
        AgentToolCategory.ALL.forEach { category ->
            val tools = AgentToolCategory.toolsOf(category)
            assertFalse(
                categoryMasterOn(tools, AgentToolSwitches(), noPermissions()),
                "分类 ${category.title} 在无权限时应显示关"
            )
        }
    }

    @Test
    fun allPermissions_allToolsOn_showsOn() {
        // 全部权限可用且工具开启 → 主开关显示开
        val tools = AgentToolCategory.toolsOf(AgentToolCategory.OCR)
        val on = switches("ocr_image" to true)
        assertTrue(categoryMasterOn(tools, on, allPermissions()))
    }

    @Test
    fun anyUsableToolOff_masterShowsOff() {
        // 可用工具中有一个关闭 → 主开关显示关
        val tools = setOf("list_apps", "app_permissions", "read_clipboard")
        val s = switches("list_apps" to true, "app_permissions" to true, "read_clipboard" to false)
        assertFalse(categoryMasterOn(tools, s, allPermissions()))
    }

    @Test
    fun unusableToolsIgnored_masterDependsOnUsableOnly() {
        // 权限不可用的工具（无论开关状态）不计入主开关
        val tools = setOf("click", "notify_self")
        // click 依赖无障碍（不可用），notify_self 可用且开启 → 主开关开
        val s = switches("click" to false, "notify_self" to true)
        val usable = { name: String -> name == "notify_self" }
        assertTrue(categoryMasterOn(tools, s, usable))
        // notify_self 关闭 → 主开关关
        val s2 = switches("click" to false, "notify_self" to false)
        assertFalse(categoryMasterOn(tools, s2, usable))
    }

    @Test
    fun actCategory_noAccessibility_masterOff() {
        // 模拟真实场景：无障碍未授权（行为分类的点击类工具全部不可用）
        val tools = AgentToolCategory.toolsOf(AgentToolCategory.ACT)
        val s = switches(
            "screenshot" to true,
            "notify_self" to true,
            "reply_notification" to true,
            "schedule_notify" to true,
            "open_app" to false
        )
        val usable = { name: String -> name in setOf("notify_self", "reply_notification", "schedule_notify", "open_app") }
        // open_app 可用但关闭 → 主开关关
        assertFalse(categoryMasterOn(tools, s, usable))
        val s2 = switches(
            "screenshot" to true,
            "notify_self" to true,
            "reply_notification" to true,
            "schedule_notify" to true,
            "open_app" to true
        )
        assertTrue(categoryMasterOn(tools, s2, usable))
    }
}
