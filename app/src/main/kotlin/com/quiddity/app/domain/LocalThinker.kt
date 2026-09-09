package com.quiddity.app.domain

/**
 * 工具结果判定工具。
 *
 * 思考已改为提示词引导模型输出【思考】/【回答】标记、客户端拆分展示的方案，
 * 本地不再拼接模板思考；本对象仅保留工具结果是否异常的判定，供工具回填标记使用。
 */
object LocalThinker {

    /** 短文本全文判定上限：短结果命中关键词更可能是真实错误。 */
    private const val SHORT_TEXT_LIMIT = 120

    /**
     * 工具结果是否判定为异常（权限缺失 / 未找到 / 失败 / 未执行等）。
     *
     * 判定规则（避免把数据内容误判为错误）：
     * - 任一行以关键词开头（错误句式都整句以关键词开头，如"未找到应用 xx""权限未授权"）；
     * - 或短文本（≤ [SHORT_TEXT_LIMIT]）全文命中关键词。
     * 数据行（应用列表 / 日志）以包名或数据开头（如 com.android.permissioncontroller 的
     * 包名含 "permission" 但不在行首），不会误命中。
     */
    fun isToolError(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return true
        val markers = listOf(
            "未获得", "未找到", "失败", "无法", "不存在", "尚未", "拒绝",
            "需要用户确认", "执行失败", "读取失败", "未启用", "不可用", "无效",
            "异常", "报错", "未授权", "未开启", "超时",
            "error", "failed", "failure", "denied", "permission", "unauthorized",
            "not found", "not granted", "exception", "timeout", "empty", "null"
        )
        if (t.lineSequence().any { line ->
                val head = line.trimStart()
                markers.any { head.startsWith(it, ignoreCase = true) }
            }
        ) {
            return true
        }
        return t.length <= SHORT_TEXT_LIMIT && markers.any { t.contains(it, ignoreCase = true) }
    }

}
