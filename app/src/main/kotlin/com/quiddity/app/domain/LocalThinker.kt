package com.quiddity.app.domain

/*
 * ============================================================================
 * 开发规范 (Development Specifications)
 * ============================================================================
 *
 * 1. 问题修复规范
 *    所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
 *    修复内容需完全融入现有代码架构，保证代码逻辑的连贯性、可维护性和可扩展性。
 *
 * 2. 代码注释规范
 *    文件内仅允许保留两类注释：
 *    - 当前规则说明注释（即本注释块）
 *    - 模块划分注释（用于标识代码功能模块边界）
 *    除此之外，禁止出现任何形式的代码注释（包括但不限于单行注释、多行注释、临时调试注释等）。
 *
 * 3. 构建交付要求
 *    在完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK 文件。
 *    APK 文件需满足以下条件：
 *    - 签名有效且符合发布标准
 *    - 包含完整的功能模块
 *    - 经过基础性能测试和兼容性测试
 *    以便在真实设备环境中进行功能验证和性能评估。
 *
 * ============================================================================
 */

/**
 * 应用内本地思考生成器（思考全程在应用内，不依赖厂商服务器）。
 *
 * 客户端在用户发送消息后、正式回复前，按规则快速生成一段简短「思考」，
 * 附着在回复消息上供用户展开查看；内容为本地确定性文本，不发送给模型。
 */
object LocalThinker {

    /** 工具关键词 → 涉及的能力说明（命中即认为需要读取手机信息/执行操作）。 */
    private val TOOL_KEYWORDS = listOf(
        "应用" to "读取应用列表",
        "权限" to "查询应用权限清单",
        "安装" to "查询安装时间与来源",
        "耗电" to "统计后台耗电",
        "流量" to "统计网络流量",
        "屏幕" to "读取屏幕内容",
        "通知" to "读取通知",
        "前台" to "读取前台应用",
        "日志" to "读取全局日志",
        "截图" to "截取屏幕",
        "文件" to "查询文件访问能力",
        "停用" to "停用应用（需确认）",
        "卸载" to "卸载应用（需确认）",
        "强停" to "强制停止（需确认）"
    )

    /** 生成本地思考文本：识别意图 → 是否需要工具 → 回答策略。 */
    fun think(userMessage: String, isAgent: Boolean): String {
        val msg = userMessage.trim()
        val toolHits = TOOL_KEYWORDS
            .filter { (kw, _) -> msg.contains(kw) }
            .map { it.second }
            .distinct()
        return buildString {
            append("用户想：").append(msg.take(50))
            if (toolHits.isNotEmpty()) {
                append("\n这涉及手机信息/操作：").append(toolHits.take(3).joinToString("、"))
                append("\n先查询确认，再如实回答")
            } else if (isAgent) {
                append("\n直接回答即可，语气贴合人设，简洁清晰")
            } else {
                append("\n围绕人设自然回应，无需调用工具")
            }
        }
    }

    /** 工具结果是否判定为异常（权限缺失 / 未找到 / 失败 / 未执行等）。 */
    fun isToolError(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return true
        val markers = listOf(
            "未获得", "未找到", "失败", "无法", "不存在", "尚未", "拒绝",
            "需要用户确认", "执行失败", "读取失败", "未启用", "不可用", "无效",
            "异常", "报错", "未授权", "未开启"
        )
        return markers.any { t.contains(it) }
    }

    /**
     * 工具系列使用后的第一人称思考（人设视角）：评估各工具返回的数据是否正常，
     * 然后给出下一步规划；无论成功失败都会生成，供开启思考时展示。
     */
    fun thinkAfterTools(
        results: List<Pair<String, String>>,
        aiName: String
    ): String {
        if (results.isEmpty()) return ""
        val bad = results.filter { isToolError(it.second) }
        val good = results.filterNot { isToolError(it.second) }
        return buildString {
            append("我是「").append(aiName).append("」，刚调用了一系列工具：")
                .append(results.joinToString("、") { it.first }).append("。")
            if (bad.isEmpty()) {
                append("返回的数据看起来正常，可以据此回答。")
            } else {
                append("其中 ").append(bad.joinToString("、") { it.first })
                    .append(" 没有拿到正常数据（").append(bad.first().second.take(30))
                    .append("）。")
                if (good.isNotEmpty()) {
                    append(good.joinToString("、") { it.first }).append(" 正常。")
                }
            }
            append("下一步：如实告诉用户哪些工具成功、哪些失败及原因，不编造，用已有数据完成回答。")
        }
    }
}
