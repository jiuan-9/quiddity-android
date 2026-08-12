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
}
