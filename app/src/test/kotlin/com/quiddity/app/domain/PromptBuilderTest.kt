package com.quiddity.app.domain

import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import com.quiddity.app.util.QuiddityConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
 * [PromptBuilder] 单元测试（6.5.2 两段式压缩解析、6.4/6.5 记忆策略分支、群聊转述）。
 */
class PromptBuilderTest {

    private val now = 1720000000000L

    private fun conv(
        memory: String = "",
        compressedMemory: String = "",
        memoryIndex: String = "",
        memoryStrategy: String? = null,
        groupMemory: String = ""
    ): Conversation = Conversation(
        id = "conv_test",
        createdAt = now,
        updatedAt = now,
        memory = memory,
        compressedMemory = compressedMemory,
        memoryIndex = memoryIndex,
        memoryStrategy = memoryStrategy,
        groupMemory = groupMemory
    )

    private fun msg(
        id: String,
        role: Role = Role.USER,
        content: String,
        senderId: String? = null
    ): Message = Message(
        id = id,
        conversationId = "conv_test",
        role = role,
        content = content,
        timestamp = now,
        senderId = senderId
    )

    // ============================================================
    // 6.5.2 两段式压缩解析
    // ============================================================

    @Test
    fun `parse two section compression output`() {
        val result = PromptBuilder.parseCompressionResult(
            """
            【摘要】
            用户讨论了项目 A 的需求。
            【索引】
            项目A, 需求, API集成
            """.trimIndent()
        )
        assertTrue(result.success)
        assertEquals("用户讨论了项目 A 的需求。", result.summary, "「【摘要】」为节标题，不应进入 compressedMemory")
        assertEquals("项目A, 需求, API集成", result.index)
    }

    @Test
    fun `parse single section output falls back to first 80 chars index`() {
        val longSummary = "关键词".repeat(30)
        val result = PromptBuilder.parseCompressionResult(longSummary)
        assertTrue(result.success)
        assertEquals(longSummary, result.summary)
        assertEquals(longSummary.take(QuiddityConstants.MEMORY_INDEX_FALLBACK_CHARS), result.index)
    }

    @Test
    fun `parse empty summary is failure`() {
        val result = PromptBuilder.parseCompressionResult("【索引】\n项目A")
        assertFalse(result.success, "摘要段为空时压缩应视为失败，两字段保持旧值")
        assertEquals("", result.summary)
        assertEquals("", result.index)
    }

    @Test
    fun `parse with empty index line falls back to summary prefix`() {
        val summary = "项目A需求讨论"
        val result = PromptBuilder.parseCompressionResult("【摘要】\n$summary\n【索引】\n\n")
        assertTrue(result.success)
        assertEquals(summary, result.summary)
        assertEquals(
            summary.take(QuiddityConstants.MEMORY_INDEX_FALLBACK_CHARS),
            result.index,
            "索引缺失/为空时用摘要前 80 字临时顶替"
        )
    }

    // ============================================================
    // 6.4 随身带 / 6.5 小抄 记忆策略分支
    // ============================================================

    @Test
    fun `default strategy keeps carry behavior`() {
        val prompt = PromptBuilder.buildSystemPrompt(
            conv(memory = "固定记忆", compressedMemory = "完整摘要内容")
        )
        assertTrue(prompt.contains("【历史对话摘要】"), "默认随身带应携带完整压缩摘要")
        assertTrue(prompt.contains("【需要记住的事】"))
        assertFalse(prompt.contains("【记忆索引】"), "随身带不输出小抄索引行")
    }

    @Test
    fun `tool strategy outputs cheatsheet index instead of full summary`() {
        val prompt = PromptBuilder.buildSystemPrompt(
            conv(
                memory = "固定记忆",
                compressedMemory = "完整摘要内容",
                memoryIndex = "项目A, 关键词B",
                memoryStrategy = QuiddityConstants.MEMORY_STRATEGY_TOOL
            )
        )
        assertTrue(prompt.contains("【记忆索引】"))
        assertTrue(prompt.contains("历史摘要：项目A, 关键词B"))
        assertTrue(prompt.contains("完整内容可用 read_memory 工具读取"))
        assertFalse(prompt.contains("【历史对话摘要】"), "小抄模式不应携带完整摘要")
    }

    @Test
    fun `tool strategy without compressed memory omits index line`() {
        val prompt = PromptBuilder.buildSystemPrompt(
            conv(memory = "固定记忆", memoryStrategy = QuiddityConstants.MEMORY_STRATEGY_TOOL)
        )
        assertTrue(prompt.contains("【需要记住的事】"))
        assertFalse(prompt.contains("【记忆索引】"), "从未压缩时不生成索引行（等价只带小抄、无抽屉）")
    }

    @Test
    fun `explicit strategy override wins over conversation field`() {
        val prompt = PromptBuilder.buildSystemPrompt(
            conv(
                memory = "固定记忆",
                compressedMemory = "完整摘要内容",
                memoryIndex = "索引A",
                memoryStrategy = QuiddityConstants.MEMORY_STRATEGY_CARRY
            ),
            memoryStrategy = QuiddityConstants.MEMORY_STRATEGY_TOOL
        )
        assertTrue(prompt.contains("【记忆索引】"), "显式覆盖应优先于会话级字段")
    }

    // ============================================================
    // toApiMessages senderId 标签 / 群聊转述
    // ============================================================

    @Test
    fun `toApiMessages prefixes sender label when mapping provided`() {
        val history = listOf(
            msg("m1", Role.USER, "早上好", senderId = "conv_a"),
            msg("m2", Role.ASSISTANT, "你好呀", senderId = "conv_b")
        )
        val labeled = PromptBuilder.toApiMessages(
            systemPrompt = "",
            history = history,
            senderLabels = mapOf("conv_a" to "小A", "conv_b" to "小B")
        )
        assertEquals("[小A] 早上好", labeled[0].content)
        assertEquals("[小B] 你好呀", labeled[1].content)
    }

    @Test
    fun `toApiMessages without labels keeps plain content`() {
        val history = listOf(
            msg("m1", Role.USER, "早上好", senderId = "conv_a")
        )
        val plain = PromptBuilder.toApiMessages("", history)
        assertEquals("早上好", plain[0].content)
    }

    @Test
    fun `buildGroupTranscript takes last N messages with names`() {
        val messages = listOf(
            msg("m1", Role.USER, "第一句", senderId = "conv_a"),
            msg("m2", Role.USER, "第二句", senderId = "conv_b"),
            msg("m3", Role.USER, "第三句", senderId = "conv_a")
        )
        val transcript = PromptBuilder.buildGroupTranscript(
            messages = messages,
            lastN = 2,
            senderNames = mapOf("conv_a" to "小A", "conv_b" to "小B")
        )
        assertEquals("[小B] 第二句\n[小A] 第三句", transcript)
    }

    @Test
    fun `read memory tool requires query parameter`() {
        val tool = PromptBuilder.buildReadMemoryTool()
        assertEquals("read_memory", tool.function.name)
        assertTrue(tool.function.parameters.containsKey("properties"), "工具应带 query 参数定义")
        assertTrue(tool.function.parameters.containsKey("required"), "工具应声明必填参数")
    }

    @Test
    fun `search chat tool requires query parameter`() {
        val tool = PromptBuilder.buildSearchChatTool()
        assertEquals("search_chat", tool.function.name)
        assertTrue(tool.function.parameters.containsKey("properties"), "工具应带 query 参数定义")
        assertTrue(tool.function.parameters.containsKey("required"), "工具应声明必填参数")
    }
}
