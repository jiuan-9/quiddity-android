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
     * 生成本地思考：按意图生成角色第一人称的自然内心独白（打招呼 / 提问 / 涉及手机信息 / 闲聊）。
     * 称呼用户时优先使用 [userCallName]（如「宝宝」）并放在句首，未设置时回退为 [userName]；
     * 不使用固定模板，句式随消息内容与长度自然变化。
     */
    fun think(
        userMessage: String,
        isAgent: Boolean,
        aiName: String,
        userName: String,
        userCallName: String = ""
    ): String {
        val msg = userMessage.trim()
        val needsTool = TOOL_KEYWORDS.any { msg.contains(it) }
        val isQuestion = QUESTION_MARKERS.any { msg.contains(it) } || msg.endsWith("？") || msg.endsWith("?")
        val isGreeting = msg.length <= 12 && (
            msg.contains("嗨") || msg.contains("你好") || msg.contains("在吗") ||
                msg.contains("哈喽") || msg.contains("hello", true) || msg.contains("hi", true)
            )
        val call = userCallName.ifBlank { userName }.ifBlank { "用户" }
        val topic = msg.take(24)
        val variant = (msg.hashCode() and Int.MAX_VALUE) % 3
        return when {
            needsTool -> {
                when (variant) {
                    0 -> "$call，这事涉及手机里的真实数据，我先查一下再回你，不凭印象说。查到什么就报什么，查不到也直说。"
                    1 -> "$call，这个（$topic）我得先实际查一遍，数据正常就照报，异常就告诉你哪一步没查成。"
                    else -> "$call，光凭印象说不准，我先查手机。查得到就上数据，查不到就明说原因，不糊弄。"
                }
            }
            isGreeting -> {
                if (isAgent) {
                    when (variant) {
                        0 -> "$call 来找我了，先应一声，再看看 $call 接下来想让我做什么。"
                        1 -> "$call 主动打了个招呼，我自然接上，别显得生硬。"
                        else -> "$call 来了，先回一句，然后等着看 $call 今天要我帮什么忙。"
                    }
                } else {
                    when (variant) {
                        0 -> "$call 来打招呼了，用「$aiName」的性子轻松回一句，看看 $call 今天想聊什么。"
                        1 -> "$call 主动找我，先应下这声招呼，再顺着往下聊。"
                        else -> "$call 啊，先自然应一声，气氛松快点，再听 $call 想说什么。"
                    }
                }
            }
            isQuestion -> {
                if (isAgent) {
                    when (variant) {
                        0 -> "$call 问了个问题，先把问题想清楚：能直接答就直接答，要查手机的就查了再说。"
                        1 -> "$call 在问我（$topic），我心里先过一遍怎么答，该查的先查。"
                        else -> "$call 这个问题得想清楚再回，别答得含含糊糊的。"
                    }
                } else {
                    when (variant) {
                        0 -> "$call 在问我，按「$aiName」的性子想想怎么回最自然，别答得干巴巴的。"
                        1 -> "$call 问的是（$topic），我先琢磨一下语气，再顺着人设回。"
                        else -> "$call 问的这事，我得想想怎么回才像「$aiName」会说的话，自然点，别端着。"
                    }
                }
            }
            else -> {
                if (isAgent) {
                    when (variant) {
                        0 -> "$call 在跟我说话，顺着 $call 的意思接，简洁清楚，不绕弯。"
                        1 -> "$call 随口说了句（$topic），我接住话题，看看 $call 真正想要什么。"
                        else -> "$call 这句话我听完再决定怎么接，别抢话。"
                    }
                } else {
                    when (variant) {
                        0 -> "$call 随口说了句，我顺着话题接住，保持「$aiName」一贯的感觉就好。"
                        1 -> "$call 说（$topic），我按「$aiName」的习惯接一句，自然往下聊。"
                        else -> "$call 这话题我顺着走，用「$aiName」的口吻回，别让气氛冷下来。"
                    }
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
        aiName: String,
        userName: String,
        userCallName: String = ""
    ): String {
        if (results.isEmpty()) return ""
        val bad = results.filter { isToolError(it.second) }
        val good = results.filterNot { isToolError(it.second) }
        val call = userCallName.ifBlank { userName }.ifBlank { "用户" }
        return buildString {
            append("我查了 ").append(results.joinToString("、") { it.first }).append("。")
            if (bad.isEmpty()) {
                append("拿到的数据都挺正常，按这个来回答。")
            } else {
                append(bad.joinToString("、") { it.first })
                    .append(" 这边没查成（").append(bad.first().second.take(24))
                    .append("）。")
                if (good.isNotEmpty()) append(good.joinToString("、") { it.first }).append(" 没问题。")
                append("那就如实告诉 $call 哪个没查成、为什么，别糊弄。")
            }
        }
    }
}
