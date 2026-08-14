package com.quiddity.app.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [LocalThinker] 单元测试：思考已改为提示词引导模型输出【思考】/【回答】标记，
 * 本地仅保留工具结果异常判定逻辑。
 */
class LocalThinkerTest {

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
    fun isToolError_longDataContentWithKeywordInsideIsNotError() {
        // 回归：长数据内容（应用列表）含 permissioncontroller / null 等关键词属于内容本身，
        // 不得误判为异常（曾因包名 com.android.permissioncontroller 命中 "permission" 误报失败）
        val appList = "com.android.permissioncontroller：权限控制器\n" +
            "com.android.settings：设置\n" +
            "com.android.systemui.navbar.twobutton：2 Button Navigation Bar\n" +
            "com.example.app：示例（空标签条目为 null）"
        assertFalse(LocalThinker.isToolError(appList))
    }
}
