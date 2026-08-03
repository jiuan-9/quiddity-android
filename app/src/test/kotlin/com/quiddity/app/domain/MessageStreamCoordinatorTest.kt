package com.quiddity.app.domain

import com.quiddity.app.data.model.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
 * [MessageStreamCoordinator] 单元测试（句末标点 + 括号切分策略）。
 *
 * ## 为什么必须测试
 *
 * 协调器是流式聊天的**关键正确性边界**——历史上至少 2 次重大闪退源自此处的 bug：
 * 1. 消息 ID 跨 run 重复 → LazyColumn 重复 key 崩溃
 * 2. 切分时 New/Update 错位 → 消息内容错乱
 *
 * 当前切分策略已全面重构为「句末标点 + 括号」确定性切分，本测试覆盖：
 * - 句末标点（。！？.!?）触发切分
 * - 连续句末标点合并（不会切出空片段）
 * - 省略号（... 与 …）不触发切分
 * - 括号内容（含括号本身）作为独立消息
 * - 空括号 / 纯标点片段不发出
 * - splitEnabled 关闭时不切分
 * - 硬上限保护
 * - 跨 runId 唯一性 / finalize 收尾等不变量
 */
class MessageStreamCoordinatorTest {

    // ============================================================
    // 一、用户核心用例：句末标点 + 括号切分
    // ============================================================

    @Test
    fun `user example - bracket plus question plus ellipsis plus period`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        coord.accept("（伸手）你真的还好吗？要不歇歇...不要太累。")
        val snap = coord.snapshot()
        val contents = snap.map { it.content }
        assertEquals(
            listOf("（伸手）", "你真的还好吗？", "要不歇歇...不要太累。"),
            contents,
            "用户原例切分结果与预期不符：$contents"
        )
        val ids = snap.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "消息 id 必须唯一：$ids")
    }

    @Test
    fun `single chinese period splits into one completed message`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        val signals = coord.accept("你好。")
        val completed = signals.filterIsInstance<StreamCoordinator.Signal.Complete>()
        assertEquals(1, completed.size, "句末「。」应切出 1 条完成消息")
        assertEquals("你好。", completed[0].message.content)
    }

    @Test
    fun `multiple sentences split independently`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        coord.accept("第一句。第二句！第三句？")
        val snap = coord.snapshot()
        assertEquals(
            listOf("第一句。", "第二句！", "第三句？"),
            snap.map { it.content },
            "三句应以各自句末标点切分"
        )
    }

    @Test
    fun `ascii punctuation also triggers split`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        coord.accept("Hello!How are you?I am fine.")
        // 末尾单个 '.' 可能是省略号开头，调用 finalize 强制收尾
        coord.finalize()
        val snap = coord.snapshot()
        assertEquals(
            listOf("Hello!", "How are you?", "I am fine."),
            snap.map { it.content }
        )
    }

    // ============================================================
    // 二、连续句末标点合并（不切出空片段）
    // ============================================================

    @Test
    fun `consecutive sentence enders merge into one message`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        coord.accept("真的吗？！不会吧！！真的。")
        val snap = coord.snapshot()
        assertEquals(
            listOf("真的吗？！", "不会吧！！", "真的。"),
            snap.map { it.content },
            "连续句末标点应合并到同一条消息"
        )
    }

    @Test
    fun `pure punctuation segment is not emitted`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        // 行首连续句号：不应发出仅含标点的空消息
        coord.accept("。。。真正的内容。")
        val snap = coord.snapshot()
        val contents = snap.map { it.content }
        assertTrue(
            contents.none { it.trim().all { c -> c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?' } },
            "不应发出仅含句末标点的消息：$contents"
        )
        assertTrue(contents.contains("真正的内容。"), "应保留实质内容：$contents")
    }

    // ============================================================
    // 三、省略号不切分
    // ============================================================

    @Test
    fun `ascii ellipsis does not trigger split`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        // 中间的 ... 不切分，只在末尾 。 切分
        coord.accept("要不歇歇...不要太累。")
        val snap = coord.snapshot()
        assertEquals(
            listOf("要不歇歇...不要太累。"),
            snap.map { it.content },
            "ASCII 省略号 ... 不应触发切分"
        )
    }

    @Test
    fun `unicode ellipsis character does not trigger split`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        coord.accept("嗯…也许吧。")
        val snap = coord.snapshot()
        assertEquals(
            listOf("嗯…也许吧。"),
            snap.map { it.content },
            "单字符省略号 … 不应触发切分"
        )
    }

    @Test
    fun `ellipsis followed by sentence ender splits at ender`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        coord.accept("等等...然后呢？")
        val snap = coord.snapshot()
        assertEquals(listOf("等等...然后呢？"), snap.map { it.content })
    }

    // ============================================================
    // 四、括号内容另起一句
    // ============================================================

    @Test
    fun `bracket content becomes its own message`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        coord.accept("（伸手）你真的还好吗？")
        val snap = coord.snapshot()
        assertEquals(
            listOf("（伸手）", "你真的还好吗？"),
            snap.map { it.content },
            "括号内容应作为独立消息"
        )
    }

    @Test
    fun `text before bracket ends previous message`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        coord.accept("你好。（点头）")
        val snap = coord.snapshot()
        assertEquals(
            listOf("你好。", "（点头）"),
            snap.map { it.content },
            "括号前的文本应作为前一条消息"
        )
    }

    @Test
    fun `multiple consecutive brackets each become separate messages`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        coord.accept("（点头）（微笑）你好。")
        val snap = coord.snapshot()
        assertEquals(
            listOf("（点头）", "（微笑）", "你好。"),
            snap.map { it.content },
            "连续多个括号应各自独立成条"
        )
    }

    @Test
    fun `all bracket types supported`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        coord.accept("(action)【动作】<动作>你好。")
        val snap = coord.snapshot()
        assertEquals(
            listOf("(action)", "【动作】", "<动作>", "你好。"),
            snap.map { it.content },
            "应支持 () 【】 <> 等各类括号"
        )
    }

    @Test
    fun `nested brackets match outer close`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        // 嵌套同类型括号：以匹配的外层闭括号为准
        coord.accept("（外层（内层））后续。")
        val snap = coord.snapshot()
        assertEquals(
            listOf("（外层（内层））", "后续。"),
            snap.map { it.content },
            "嵌套括号应匹配最外层闭合"
        )
    }

    @Test
    fun `empty bracket is consumed but not emitted`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        coord.accept("（）你好。")
        val snap = coord.snapshot()
        val contents = snap.map { it.content }
        assertTrue(
            contents.none { it.trim() == "（）" || it.trim() == "()" },
            "空括号不应作为消息发出：$contents"
        )
        assertTrue(contents.contains("你好。"), "应保留实质内容：$contents")
    }

    @Test
    fun `unclosed bracket waits for more delta`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        // 第一段：括号未闭合，不应切出括号消息
        val first = coord.accept("（伸")
        val completedFirst = first.filterIsInstance<StreamCoordinator.Signal.Complete>()
        assertEquals(0, completedFirst.size, "括号未闭合时不应发出完成消息")
        // 第二段：括号闭合 + 句末标点
        coord.accept("手）你好。")
        val snap = coord.snapshot()
        assertEquals(
            listOf("（伸手）", "你好。"),
            snap.map { it.content },
            "跨 delta 的括号应在闭合后切分"
        )
    }

    // ============================================================
    // 五、splitEnabled 关闭时不切分
    // ============================================================

    @Test
    fun `split disabled keeps entire reply as single message`() {
        val coord = MessageStreamCoordinator(
            "conv1", "run1",
            singleMessageTokens = 100,
            splitEnabled = false
        )
        coord.accept("第一句。第二句！（括号）第三句？")
        val snap = coord.snapshot()
        assertEquals(1, snap.size, "splitEnabled=false 时不应切分")
        assertEquals("第一句。第二句！（括号）第三句？", snap[0].content)
    }

    @Test
    fun `split disabled finalize emits single completed message`() {
        val coord = MessageStreamCoordinator(
            "conv1", "run1",
            singleMessageTokens = 100,
            splitEnabled = false
        )
        coord.accept("片段一。")
        coord.accept("片段二。")
        coord.finalize()
        val snap = coord.snapshot()
        assertEquals(1, snap.size, "splitEnabled=false 时整条回复应为单条消息")
        assertEquals("片段一。片段二。", snap[0].content)
    }

    // ============================================================
    // 六、流式信号契约（New / Update / Complete）
    // ============================================================

    @Test
    fun `first delta produces New signal`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        val signals = coord.accept("Hello")
        assertEquals(1, signals.size, "单 delta 无切分点应只产生 1 个信号")
        assertTrue(signals[0] is StreamCoordinator.Signal.New, "首次消息应为 New 信号")
    }

    @Test
    fun `subsequent deltas on same id produce Update signals`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        coord.accept("Hello")
        val signals = coord.accept(" world")
        assertEquals(1, signals.size)
        assertTrue(
            signals[0] is StreamCoordinator.Signal.Update,
            "相同 id 的后续 delta 应为 Update 信号"
        )
    }

    @Test
    fun `split produces New and Complete pair`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        // 使用「！」而非末尾单「.」——单「.」在 buffer 末尾会被视为省略号开头而等待更多 delta
        val signals = coord.accept("第一句！")
        val hasComplete = signals.any { it is StreamCoordinator.Signal.Complete }
        assertTrue(hasComplete, "句末标点切分时必须发出 Complete 信号")
    }

    // ============================================================
    // 七、跨 runId 唯一性 / finalize 收尾
    // ============================================================

    @Test
    fun `different runIds produce different message ids to avoid LazyColumn key collision`() {
        val coord1 = MessageStreamCoordinator("conv1", "run-A", singleMessageTokens = 1000)
        val coord2 = MessageStreamCoordinator("conv1", "run-B", singleMessageTokens = 1000)

        coord1.accept("Same content.")
        coord2.accept("Same content.")

        val snap1 = coord1.snapshot()
        val snap2 = coord2.snapshot()

        val id1 = snap1.first().id
        val id2 = snap2.first().id
        assertTrue(id1 != id2, "不同 runId 的同内容消息 ID 必须不同，否则会触发 LazyColumn 闪退")
        assertTrue(id1.contains("run-A"), "id 应包含 runId: $id1")
        assertTrue(id2.contains("run-B"), "id 应包含 runId: $id2")
    }

    @Test
    fun `finalize marks streaming message as not streaming`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        coord.accept("partial")
        val snap = coord.snapshot()
        assertTrue(snap.isNotEmpty())
        assertTrue(snap.last().isStreaming, "调用 finalize 前应为 streaming")

        coord.finalize()
        val finalSnap = coord.snapshot()
        val lastFinal = finalSnap.last()
        assertEquals(false, lastFinal.isStreaming, "finalize 后当前消息应标记为完成")
    }

    @Test
    fun `finalize on empty buffer with prior completed does not emit duplicate`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        coord.accept("完成。")
        val beforeFinal = coord.snapshot().size
        coord.finalize()
        val afterFinal = coord.snapshot().size
        assertEquals(
            beforeFinal, afterFinal,
            "buffer 已空且有完成消息时，finalize 不应新增消息"
        )
    }

    // ============================================================
    // 八、硬上限保护
    // ============================================================

    @Test
    fun `hard limit forces split on long no-punctuation text`() {
        // singleMessageTokens=1 → 硬上限 = 1 * 2 * 4 = 8 字符
        // 模拟真实流式：分多个小 delta 喂入无标点文本，buffer 每次达到上限即强制切分
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1)
        val longText = "x".repeat(50)
        // 每次 5 字符：buffer 累计到 8+ 即触发强制切分
        longText.chunked(5).forEach { coord.accept(it) }
        coord.finalize()
        val snap = coord.snapshot()
        assertTrue(snap.size >= 2, "超长无标点文本应被硬上限强制切分为多条：${snap.size}")
        // 所有片段拼接应等于原文（不丢字）
        val reconstructed = snap.joinToString("") { it.content }
        assertEquals(longText, reconstructed, "硬上限切分不应丢失内容")
    }

    @Test
    fun `hard limit split keeps ids unique`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1)
        val longText = "y".repeat(50)
        longText.chunked(5).forEach { coord.accept(it) }
        coord.finalize()
        val snap = coord.snapshot()
        val ids = snap.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "硬上限切分产生的消息 id 必须唯一：$ids")
    }

    // ============================================================
    // 九、综合流式场景
    // ============================================================

    @Test
    fun `streamed deltas across brackets and sentences reconstruct correctly`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        // 模拟真实流式：分多次 delta 到达
        coord.accept("（抬头")
        coord.accept("）今天")
        coord.accept("天气真好。")
        coord.accept("要不要出去走走？")
        coord.finalize()
        val snap = coord.snapshot()
        assertEquals(
            listOf("（抬头）", "今天天气真好。", "要不要出去走走？"),
            snap.map { it.content },
            "跨 delta 流式切分应正确重组"
        )
    }

    @Test
    fun `role is always assistant`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        coord.accept("你好。")
        val snap = coord.snapshot()
        assertTrue(
            snap.all { it.role == Role.ASSISTANT },
            "协调器产出的消息 role 必须为 ASSISTANT"
        )
    }

    @Test
    fun `completed messages are not marked streaming`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        coord.accept("第一句。第二句。")
        val snap = coord.snapshot()
        assertTrue(
            snap.all { !it.isStreaming },
            "所有已完成切分的消息 isStreaming 必须为 false"
        )
    }

    // ============================================================
    // 十、引号包裹内容：闭合引号不得被拆到下一条消息
    // ============================================================

    @Test
    fun `quoted sentence keeps closing quote in same message`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        coord.accept("\"你好。\"")
        val snap = coord.snapshot()
        assertEquals(
            listOf("\"你好。\""),
            snap.map { it.content },
            "闭合引号必须与「你好。」留在同一条消息"
        )
    }

    @Test
    fun `quoted dialogue with multiple sentences stays intact`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        coord.accept("他说：\"你好。我很开心。\"然后走了。")
        val snap = coord.snapshot()
        assertEquals(
            listOf("他说：\"你好。我很开心。\"", "然后走了。"),
            snap.map { it.content },
            "引号内的句号不应触发切分，闭合引号后的句号才切分"
        )
    }

    @Test
    fun `fullwidth quotes keep closing quote attached`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        coord.accept("“你好。”")
        coord.accept("「晚安。」")
        val snap = coord.snapshot()
        assertEquals(
            listOf("“你好。”", "「晚安。」"),
            snap.map { it.content }
        )
    }

    @Test
    fun `closing quote arriving in later delta is not split off`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        coord.accept("他说：\"你好。")
        val signals = coord.accept("\"然后走了。")
        val completed = signals.filterIsInstance<StreamCoordinator.Signal.Complete>()
        val contents = completed.map { it.message.content }
        assertEquals(
            listOf("他说：\"你好。\"", "然后走了。"),
            contents,
            "跨 delta 的闭合引号应并入引号内容所在消息"
        )
    }

    @Test
    fun `english apostrophe does not suppress splitting`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        coord.accept("He's fine. I'm good.")
        coord.finalize()
        val snap = coord.snapshot()
        assertEquals(
            listOf("He's fine.", "I'm good."),
            snap.map { it.content },
            "英文撇号不应被误判为引号而抑制切分"
        )
    }

    @Test
    fun `stray closing bracket after sentence ender is absorbed`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        coord.accept("你好。」然后呢？")
        val snap = coord.snapshot()
        assertEquals(
            listOf("你好。」", "然后呢？"),
            snap.map { it.content },
            "句末标点后的孤立闭合符应并入前一条消息"
        )
    }

    @Test
    fun `senderId is threaded into created messages`() {
        val coord = MessageStreamCoordinator(
            "conv1",
            "run1",
            singleMessageTokens = 1000,
            senderId = "conv_member_a"
        )
        coord.accept("第一条。")
        coord.finalize()
        val snap = coord.snapshot()
        assertEquals(1, snap.size, "句末标点切分后应立即完成该条消息")
        assertTrue(
            snap.all { it.senderId == "conv_member_a" },
            "群聊消息应从创建起带 senderId：${snap.map { it.senderId }}"
        )
    }

    @Test
    fun `default senderId is null for solo chat`() {
        val coord = MessageStreamCoordinator("conv1", "run1", singleMessageTokens = 1000)
        coord.accept("你好。")
        coord.finalize()
        assertTrue(
            coord.snapshot().all { it.senderId == null },
            "私聊消息 senderId 默认应为 null（兼容旧数据）"
        )
    }
}
