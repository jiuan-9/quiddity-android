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
        "应用", "权限", "安装", "耗电", "流量", "屏幕", "通知", "前台",
        "日志", "截图", "文件", "停用", "卸载", "强停", "电量", "内存", "网速",
        "install", "app", "battery", "permission", "traffic", "screen",
        "notification", "foreground", "log", "screenshot", "file", "stop",
        "uninstall", "kill", "apps"
    )

    private val QUESTION_MARKERS = listOf("吗", "呢", "怎么", "什么", "如何", "为啥", "为什么", "多少", "？", "?")

    /**
     * 生成本地思考：按意图生成自然的第一人称内心独白（打招呼 / 提问 / 涉及手机信息 / 闲聊）。
     * 不使用固定模板前缀，随消息内容自然展开。
     */
    fun think(userMessage: String, isAgent: Boolean, aiName: String): String {
        val msg = userMessage.trim()
        val needsTool = TOOL_KEYWORDS.any { msg.contains(it) }
        val isQuestion = QUESTION_MARKERS.any { msg.contains(it) } || msg.endsWith("？") || msg.endsWith("?")
        val isGreeting = msg.length <= 12 && (
            msg.contains("嗨") || msg.contains("你好") || msg.contains("在吗") ||
                msg.contains("哈喽") || msg.contains("hello", true) || msg.contains("hi", true)
            )
        return when {
            needsTool -> {
                "TA 提到的是手机上的信息（${msg.take(24)}）。" +
                    "我得先实际查一下再回答，不能凭印象说。查不到的就直说，别让 TA 以为我糊弄。"
            }
            isGreeting -> {
                if (isAgent) "TA 主动打招呼了，先自然应一声，再看 TA 接下来想让我做什么。" else {
                    "TA 来打招呼了，用「$aiName」的语气轻松回应一下，看看 TA 今天想聊什么。"
                }
            }
            isQuestion -> {
                if (isAgent) "TA 问了个问题。先把问题想清楚：能直接答的就答，要查手机信息的就查了再说。" else {
                    "TA 在问我。按「$aiName」的性子想想怎么回最自然，别答得干巴巴的。"
                }
            }
            else -> {
                if (isAgent) "TA 在跟我说话，顺着 TA 的意思接，简洁清楚，不绕弯。" else {
                    "TA 随口说了句，我顺着话题接住，保持「$aiName」一贯的感觉就好。"
                }
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
            append("我查了 ").append(results.joinToString("、") { it.first }).append("。")
            if (bad.isEmpty()) {
                append("拿到的数据都挺正常，按这个来回答。")
            } else {
                append(bad.joinToString("、") { it.first })
                    .append(" 这边没查成（").append(bad.first().second.take(24))
                    .append("）。")
                if (good.isNotEmpty()) append(good.joinToString("、") { it.first }).append(" 没问题。")
                append("那就如实告诉 TA 哪个没查成、为什么，别糊弄。")
            }
        }
    }
}
