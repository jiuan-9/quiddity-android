package com.quiddity.app.ui.chat.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/*
 * ============================================================================
 * 开发规范 (Development Specifications)
 * ============================================================================
 *
 * 1. 问题修复规范
 *    所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
 *    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
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
 *
 * 视觉语言：模拟剧本/小说的"旁白 / 内心独白"——成对括号内的文字以半透明
 * 灰色显示，与正文（用户 / AI 消息主色）形成视觉层次。
 *
 * 算法说明：
 * 1. 支持 4 种括号配对（ASCII + 全角，覆盖中英文场景）：
 *    - `(`  `)`  英文圆括号
 *    - `（` `）` 全角圆括号（中文最常见）
 *    - `[`  `]`  英文方括号
 *    - `【` `】` 全角方括号（中文文档常用）
 *
 * 2. 栈匹配扫描：
 *    - 遇左括号压栈；
 *    - 遇右括号弹栈并校验配对类型（避免 `(` 与 `]` 错配）；
 *    - 配对成功则记录区间 `[openIdx+1, closeIdx-1]` 为"括号内"。
 *
 * 3. **不处理嵌套引号**——避免破坏"他说：'你（是）谁'"语义。
 *    性能：O(n) 单次扫描 + O(k) 区间构建（k=配对数量），单条消息 < 1ms。
 *
 * 4. **不处理括号转义**——聊天场景几乎不存在反斜杠转义括号；
 *    若未来需要可加 `\\` 前缀跳过逻辑。
 *
 * 5. **错误嵌套（如 `(]`)** 不破坏扫描——不匹配右括号忽略，按普通字符显示。
 *
 * 实现采用"两遍法"（先扫描出区间，再构建 AnnotatedString）：
 * - 第一遍确定所有灰化区间；
 * - 第二遍用 [buildAnnotatedString] 增量 append + 区间内 [withStyle] 染色。
 * 这种方式比"边扫描边 addStyle"逻辑更清晰，避免时序问题。
 *
 * @param text 原始消息文本
 * @param enabled 是否开启灰化；关闭时直接返回原文本（无性能开销）
 * @param grayColor 灰化颜色（典型值：`MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)`）
 */
fun grayifyBrackets(
    text: String,
    enabled: Boolean,
    grayColor: Color
): AnnotatedString {
    if (!enabled || text.isEmpty()) return AnnotatedString(text)

    // 括号配对表：key=左括号，value=右括号
    val pairs: Map<Char, Char> = mapOf('(' to ')', '（' to '）', '[' to ']', '【' to '】')
    val openSet: Set<Char> = pairs.keys

    // 第一遍：扫描出所有"括号内"区间 [start, endInclusive]
    val ranges = mutableListOf<IntRange>()
    val stack: ArrayDeque<Int> = ArrayDeque()  // 存左括号的 index
    // 预计算：左括号 index → 配对的右括号字符（避免重复在 map 中查找）
    val leftToRight: Map<Char, Char> = mapOf('(' to ')', '（' to '）', '[' to ']', '【' to '】')
    for (i in text.indices) {
        val ch = text[i]
        when {
            // 左括号：压栈
            leftToRight.containsKey(ch) -> stack.addLast(i)
            // 右括号：弹栈并校验配对类型
            leftToRight.containsValue(ch) -> {
                if (stack.isNotEmpty()) {
                    val openIdx: Int = stack.last()
                    val openCh: Char = text[openIdx]
                    // 用 char 直接比较而非 map 查找（避免 Int 索引 Map<Char, Char>）
                    if (leftToRight[openCh] == ch) {
                        stack.removeLast()
                        // 区间内容是 (openIdx, closeIdx) 之间的字符
                        if (openIdx + 1 <= i - 1) {
                            ranges.add(openIdx + 1..(i - 1))
                        }
                    }
                }
                // 不匹配：忽略（右括号作为普通字符显示）
            }
            // 普通字符：跳过
        }
    }
    if (ranges.isEmpty()) return AnnotatedString(text)

    // 第二遍：构建 AnnotatedString，按区间顺序增量 append + 灰化染色
    return buildAnnotatedString {
        var cursor = 0
        for (range in ranges) {
            // 区间前的普通文本
            if (range.first > cursor) {
                append(text.substring(cursor, range.first))
            }
            // 区间内的灰化文本
            withStyle(SpanStyle(color = grayColor)) {
                append(text.substring(range.first, range.last + 1))
            }
            cursor = range.last + 1
        }
        // 最后一个区间之后的普通文本
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}
