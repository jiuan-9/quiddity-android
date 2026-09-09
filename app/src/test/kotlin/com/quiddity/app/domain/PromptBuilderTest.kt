package com.quiddity.app.domain

import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import com.quiddity.app.util.QuiddityConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
        senderId: String? = null,
        reasoningContent: String = ""
    ): Message = Message(
        id = id,
        conversationId = "conv_test",
        role = role,
        content = content,
        timestamp = now,
        senderId = senderId,
        reasoningContent = reasoningContent
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
        assertEquals("小A：早上好", labeled[0].content)
        assertEquals("小B：你好呀", labeled[1].content)
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
    fun `toApiMessages drops ccr placeholder content`() {
        val history = listOf(
            msg("m1", Role.USER, "<<ccr:97f81194c97d,string,725B>>"),
            msg("m2", Role.ASSISTANT, "前面的回复内容是<<ccr:abc123,string,100B>>，这部分保留"),
            msg("m3", Role.USER, "正常消息")
        )
        val plain = PromptBuilder.toApiMessages("", history)
        assertEquals(2, plain.size, "整条占位符消息应被跳过")
        assertEquals("前面的回复内容是，这部分保留", plain[0].content, "正文中的占位符应被剥离")
        assertEquals("正常消息", plain[1].content)
    }

    // ============================================================
    // DeepSeek 思考回传（携带 tools 的请求必须回传 reasoning_content，缺失即 400）
    // ============================================================

    @Test
    fun `toApiMessages attaches reasoning for assistant when enabled`() {
        val history = listOf(
            msg("m1", Role.USER, "你好"),
            msg("m2", Role.ASSISTANT, "你好呀", reasoningContent = "思考原文"),
            msg("m3", Role.USER, "再说一遍")
        )
        val result = PromptBuilder.toApiMessages("", history, attachReasoning = true)
        assertEquals("思考原文", result[1].reasoning_content, "私聊无发言人概念，assistant 直接回传落库思考")
        assertNull(result[0].reasoning_content, "user 消息不携带思考字段")
    }

    @Test
    fun `toApiMessages group attaches own reasoning and empty placeholder for others`() {
        val history = listOf(
            msg("m1", Role.ASSISTANT, "我自己说过", senderId = "member_a", reasoningContent = "我的思考"),
            msg("m2", Role.ASSISTANT, "别人说过", senderId = "member_b", reasoningContent = "别人的思考"),
            msg("m3", Role.ASSISTANT, "我旧数据无思考", senderId = "member_a")
        )
        val result = PromptBuilder.toApiMessages(
            systemPrompt = "",
            history = history,
            senderLabels = mapOf("member_a" to "小A", "member_b" to "小B"),
            requesterSenderId = "member_a",
            attachReasoning = true
        )
        assertEquals("我的思考", result[0].reasoning_content, "请求方自己的发言回传落库原文")
        assertEquals("", result[1].reasoning_content, "其他成员发言空串占位，不挂错发言人")
        assertEquals("", result[2].reasoning_content, "旧数据未知思考按空串占位（官方校验字段存在性）")
    }

    @Test
    fun `toApiMessages without attach reasoning omits field`() {
        val history = listOf(
            msg("m1", Role.ASSISTANT, "回复", reasoningContent = "思考原文")
        )
        val result = PromptBuilder.toApiMessages("", history, attachReasoning = false)
        assertNull(result[0].reasoning_content, "不携带工具的请求不挂思考字段（官方无工具时会忽略）")
    }

    @Test
    fun `toResponsesInput emits reasoning item before assistant message`() {
        val apiMessages = listOf(
            com.quiddity.app.data.remote.ChatMessage(role = "system", content = "系统"),
            com.quiddity.app.data.remote.ChatMessage(role = "user", content = "你好"),
            com.quiddity.app.data.remote.ChatMessage(
                role = "assistant", content = "你好呀", reasoning_content = "思考原文"
            ),
            com.quiddity.app.data.remote.ChatMessage(
                role = "assistant", content = "旧轮无思考", reasoning_content = ""
            )
        )
        val items = PromptBuilder.toResponsesInput(apiMessages)
        assertEquals(4, items.size, "system 不进 input；非空思考挂 reasoning item；空思考不挂")
        assertEquals("user", items[0].role)
        assertEquals("reasoning", items[1].type, "非空思考先挂 reasoning item")
        assertEquals("assistant", items[2].role)
        assertEquals("assistant", items[3].role, "空思考不挂 reasoning item，消息本体照常")
    }

    @Test
    fun `toResponsesInput keeps game log system records as user input`() {
        val apiMessages = listOf(
            com.quiddity.app.data.remote.ChatMessage(role = "system", content = "人设提示词"),
            com.quiddity.app.data.remote.ChatMessage(role = "user", content = "你好"),
            com.quiddity.app.data.remote.ChatMessage(role = "system", content = "《棋盘》对局记录：你获胜"),
            com.quiddity.app.data.remote.ChatMessage(role = "assistant", content = "我们再来一局")
        )
        val input = PromptBuilder.toResponsesInput(apiMessages)
        assertEquals(3, input.size)
        assertEquals("user", input[0].role)
        assertEquals(
            "你好",
            (input[0].content as kotlinx.serialization.json.JsonPrimitive).content
        )
        assertEquals("user", input[1].role)
        val secondContent = (input[1].content as kotlinx.serialization.json.JsonPrimitive).content
        assertTrue(secondContent.contains("《棋盘》对局记录"), "对局记录应保留给角色阅读")
        assertTrue(secondContent.startsWith("【系统记录】"), "非首条 system 记录应转为带标记的 user 输入")
        assertEquals("assistant", input[2].role)
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
        assertEquals("小B：第二句\n小A：第三句", transcript)
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

    @Test
    fun `group system prompt contains member persona and group rules`() {
        val member = conv().copy(
            persona = com.quiddity.app.data.model.Persona(
                name = "小A",
                persona = "群聊测试成员"
            ),
            userPersona = com.quiddity.app.data.model.UserPersona(
                name = "小明",
                identity = "程序员"
            )
        )
        val prompt = PromptBuilder.buildGroupSystemPrompt(member, PromptBuilder.GROUP_RULES)
        assertTrue(prompt.contains("小A"), "成员人设名应进入 system 提示词")
        assertTrue(prompt.contains("小明"), "该成员私聊里的用户人设名字应注入 system 提示词")
        assertTrue(prompt.contains("程序员"), "该成员私聊里的用户人设应注入 system 提示词")
        assertTrue(prompt.contains("群聊规则"), "应包含群聊规则节")
        assertTrue(prompt.contains("不替其他成员或用户发言"), "应包含群聊规则内容")
    }

    @Test
    fun `group memory summary prompt contains transcript`() {
        val transcript = PromptBuilder.buildGroupTranscript(
            listOf(msg("m1", content = "第一句", senderId = "conv_a")),
            lastN = 0,
            senderNames = mapOf("conv_a" to "小A")
        )
        val prompt = PromptBuilder.buildGroupMemorySummaryPrompt(transcript)
        assertTrue(prompt.contains("本次需要压缩的群聊对话"), "应包含压缩指令")
        assertTrue(prompt.contains("小A：第一句"), "应包含群聊转述")
    }

    @Test
    fun `group transcript uses member user persona name for user messages`() {
        val messages = listOf(
            msg("m1", content = "你好", senderId = null),
            msg("m2", content = "你们好呀", senderId = "conv_b")
        )
        val transcript = PromptBuilder.buildGroupTranscript(
            messages = messages,
            lastN = 10,
            senderNames = mapOf("conv_b" to "小B"),
            userName = "小明"
        )
        assertEquals("小明：你好\n小B：你们好呀", transcript)
    }

    @Test
    fun `toApiMessages prefixes user messages with member user persona name`() {
        val history = listOf(
            msg("m1", Role.USER, "早上好", senderId = null),
            msg("m2", Role.ASSISTANT, "你好呀", senderId = "conv_b")
        )
        val labeled = PromptBuilder.toApiMessages(
            systemPrompt = "",
            history = history,
            senderLabels = mapOf("conv_b" to "小B"),
            userName = "小明"
        )
        assertEquals("小明：早上好（对全体成员说）", labeled[0].content)
        assertEquals("小B：你好呀", labeled[1].content)
    }

    @Test
    fun `toApiMessages annotates at-mention target in group mode`() {
        val history = listOf(
            msg("m1", Role.ASSISTANT, "@小B 在吗", senderId = "conv_a"),
            msg("m2", Role.ASSISTANT, "@小明 宝宝", senderId = "conv_b")
        )
        val labeled = PromptBuilder.toApiMessages(
            systemPrompt = "",
            history = history,
            senderLabels = mapOf("conv_a" to "小A", "conv_b" to "小B"),
            userName = "小明"
        )
        assertEquals("小A：@小B 在吗（点名@小B）", labeled[0].content)
        assertEquals("小B：@小明 宝宝（点名@小明）", labeled[1].content)
    }

    @Test
    fun `toApiMessages leaves member messages without mention unannotated`() {
        val history = listOf(
            msg("m1", Role.ASSISTANT, "宝宝", senderId = "conv_a")
        )
        val labeled = PromptBuilder.toApiMessages(
            systemPrompt = "",
            history = history,
            senderLabels = mapOf("conv_a" to "小A"),
            userName = "小明"
        )
        assertEquals("小A：宝宝", labeled[0].content)
    }

    // ============================================================
    // 对话纪律（回复认知 + 台词完整性）
    // ============================================================

    @Test
    fun `system prompt follows generic template with clean sections`() {
        val system = PromptBuilder.buildSystemPrompt(
            conv().copy(
                persona = com.quiddity.app.data.model.Persona(
                    name = "林晚",
                    persona = "咖啡店店长",
                    worldBackground = "都市世界，现代都市",
                    desired = "温柔耐心"
                ),
                userPersona = com.quiddity.app.data.model.UserPersona(name = "小明"),
                scene = "傍晚的咖啡店",
                memory = "小明喜欢拿铁",
                compressedMemory = "他们经常在咖啡店见面"
            )
        )
        // 通用模板：身份认知在最前，各节职责单一，无补丁式堆叠
        assertTrue(system.startsWith("【角色与对话双方】"), system.take(40))
        listOf("【角色与对话双方】", "【AI 人设】", "【用户信息】", "【世界与场景】", "【对话方式】").forEach { section ->
            assertEquals(1, section.toRegex().findAll(system).count(), "每节应恰好出现一次：$section")
        }
        assertTrue(system.contains("【历史对话摘要】"), "记忆节应携带压缩摘要")
        assertTrue(system.contains("世界背景：都市世界，现代都市"), "世界背景应常驻")
        assertTrue(system.contains("当前场景：傍晚的咖啡店"), "场景应在首轮注入")
        assertFalse(system.contains("【回复纪律"), "不应再有补丁式回复纪律块")
        // 身份认知：谁是谁 + 只说自己角色的发言
        assertTrue(system.contains("【角色与对话双方】"))
        assertTrue(system.contains("你扮演的角色：林晚"))
        assertTrue(system.contains("对话伙伴：小明"))
        assertTrue(system.contains("只以「林晚」身份发言"))
        assertTrue(system.contains("不替对方说话"))
    }

    @Test
    fun `system prompt marks regenerate request with previous reply`() {
        val system = PromptBuilder.buildSystemPrompt(
            conv().copy(persona = com.quiddity.app.data.model.Persona(name = "林晚")),
            regeneratePreviousReply = "早上好呀，今天想聊点什么？"
        )
        assertTrue(system.contains("「重说」请求"), "重说应在对话方式节给出语义信号")
        assertTrue(system.contains("换一种表达方式"), "重说应要求换一种表达")
        assertTrue(system.contains("早上好呀，今天想聊点什么？"), "上一版回复应注入供对照")
        assertTrue(system.contains("禁止复述"), "应明确禁止复述上一版")
    }

    @Test
    fun `system prompt without regenerate flag stays generic`() {
        val system = PromptBuilder.buildSystemPrompt(
            conv().copy(persona = com.quiddity.app.data.model.Persona(name = "林晚"))
        )
        assertFalse(system.contains("重说"), "普通回复不应携带重说指令")
    }

    @Test
    fun `system prompt includes thinking markers when thinking enabled`() {
        val conv = conv().copy(
            thinkingEnabled = true,
            thinkingDepth = com.quiddity.app.util.QuiddityConstants.THINKING_DEPTH_SHALLOW
        )
        val system = PromptBuilder.buildSystemPrompt(
            conv = conv,
            thinkingDepth = conv.thinkingDepth
        )
        assertTrue(system.contains("【思考】"), "开启思考时应引导模型输出【思考】标记")
        assertTrue(system.contains("【回答】"), "开启思考时应引导模型输出【回答】标记")
        assertTrue(system.contains("第一人称"), "思考应要求第一人称")
    }

    @Test
    fun `system prompt omits thinking markers when thinking disabled`() {
        val system = PromptBuilder.buildSystemPrompt(
            conv().copy(thinkingEnabled = false)
        )
        assertFalse(system.contains("【思考】"), "关闭思考时不应引导【思考】标记")
    }

    @Test
    fun `group rules are generic and minimal`() {
        assertTrue(PromptBuilder.GROUP_RULES.contains("不替其他成员或用户发言"))
        assertFalse(PromptBuilder.GROUP_RULES.contains("必须包含"), "不再强制台词硬规则（由切分器根因修复兜底）")
        assertFalse(PromptBuilder.GROUP_RULES.contains("名字前缀"), "前缀规则已迁入【对话方式】节")
        assertFalse(PromptBuilder.GROUP_RULES.contains("括号"), "括号规则已迁入【对话方式】节")
    }

    @Test
    fun `group system prompt carries dialogue discipline section`() {
        val member = conv().copy(
            persona = com.quiddity.app.data.model.Persona(name = "小A", character = "温柔"),
            userPersona = com.quiddity.app.data.model.UserPersona(name = "小明")
        )
        val prompt = PromptBuilder.buildGroupSystemPrompt(member, PromptBuilder.GROUP_RULES)
        assertTrue(prompt.contains("【对话方式】"), "群聊提示词应包含对话方式节")
        assertTrue(
            prompt.contains("不要刻意添加动作或神态描写"),
            "对话方式应引导自然输出而非强制动作描写：$prompt"
        )
        assertTrue(
            prompt.contains("禁止整条回复只有动作没有台词"),
            "对话方式应保留动作-only 安全兜底：$prompt"
        )
        assertTrue(prompt.contains("不加「名字：」前缀或解释"), "前缀规则应在对话方式节：$prompt")
        assertTrue(prompt.contains("被用户「@」点名时优先回应"), "@点名规则应在对话方式节：$prompt")
        assertTrue(prompt.contains("不提及自己是 AI 或模型"), "AI 身份纪律应在对话方式节：$prompt")
    }

    @Test
    fun `group system prompt includes identity mapping`() {
        val member = conv().copy(
            persona = com.quiddity.app.data.model.Persona(name = "小A"),
            userPersona = com.quiddity.app.data.model.UserPersona(name = "小明")
        )
        val prompt = PromptBuilder.buildGroupSystemPrompt(member, PromptBuilder.GROUP_RULES)
        assertTrue(prompt.contains("你扮演的角色：小A"))
        assertTrue(prompt.contains("对话伙伴：小明"))
        assertTrue(prompt.contains("不替对方说话"))
    }

    @Test
    fun `group system prompt carries address judgement rule`() {
        val member = conv().copy(
            persona = com.quiddity.app.data.model.Persona(name = "小A"),
            userPersona = com.quiddity.app.data.model.UserPersona(name = "小明")
        )
        val prompt = PromptBuilder.buildGroupSystemPrompt(member, PromptBuilder.GROUP_RULES)
        assertTrue(prompt.contains("判断说话对象"), "群聊提示词应包含接话判断规则")
        assertTrue(prompt.contains("不要当成在叫你"), "昵称默认指向用户，不应被其他成员当成在叫自己")
    }

    @Test
    fun `group system prompt marks regenerate request with previous reply`() {
        val member = conv().copy(persona = com.quiddity.app.data.model.Persona(name = "小A"))
        val prompt = PromptBuilder.buildGroupSystemPrompt(
            member,
            PromptBuilder.GROUP_RULES,
            regeneratePreviousReply = "嗯？怎么了"
        )
        assertTrue(prompt.contains("「重说」请求"), "群聊重说应给出语义信号")
        assertTrue(prompt.contains("嗯？怎么了"), "上一版回复应注入供对照")
    }

    @Test
    fun `group system prompt injects group background`() {
        val member = conv().copy(persona = com.quiddity.app.data.model.Persona(name = "小A"))
        val prompt = PromptBuilder.buildGroupSystemPrompt(
            member,
            PromptBuilder.GROUP_RULES,
            groupBackground = "这是大学同学群，关系很熟，说话随意。"
        )
        assertTrue(prompt.contains("【群聊背景】"), "群聊背景应作为独立节注入")
        assertTrue(prompt.contains("这是大学同学群，关系很熟，说话随意。"), "群聊背景内容应原样注入")
    }

    @Test
    fun `group system prompt injects group scene`() {
        val member = conv().copy(persona = com.quiddity.app.data.model.Persona(name = "小A"))
        val prompt = PromptBuilder.buildGroupSystemPrompt(
            member,
            PromptBuilder.GROUP_RULES,
            groupBackground = "你们几个朋友正在一场篝火晚会上，夜空晴朗。",
            groupBackgroundMode = QuiddityConstants.GROUP_BACKGROUND_MODE_SCENE
        )
        assertTrue(prompt.contains("【群聊场景】"), "群聊场景应作为独立节注入")
        assertTrue(prompt.contains("你们几个朋友正在一场篝火晚会上，夜空晴朗。"), "群聊场景内容应原样注入")
    }

    @Test
    fun `group system prompt without background omits both sections`() {
        val member = conv().copy(persona = com.quiddity.app.data.model.Persona(name = "小A"))
        val prompt = PromptBuilder.buildGroupSystemPrompt(member, PromptBuilder.GROUP_RULES)
        assertFalse(prompt.contains("【群聊背景】"), "未设置背景时不应注入该节")
        assertFalse(prompt.contains("【群聊场景】"), "未设置场景时不应注入该节")
    }

    @Test
    fun `group system prompt section title follows background mode`() {
        val member = conv().copy(persona = com.quiddity.app.data.model.Persona(name = "小A"))
        val scenePrompt = PromptBuilder.buildGroupSystemPrompt(
            member,
            PromptBuilder.GROUP_RULES,
            groupBackground = "篝火晚会",
            groupBackgroundMode = QuiddityConstants.GROUP_BACKGROUND_MODE_SCENE
        )
        assertTrue(scenePrompt.contains("【群聊场景】"), "场景模式应注入为【群聊场景】节")
        assertFalse(scenePrompt.contains("【群聊背景】"), "场景模式不应出现【群聊背景】节")

        val backgroundPrompt = PromptBuilder.buildGroupSystemPrompt(
            member,
            PromptBuilder.GROUP_RULES,
            groupBackground = "大学同学群",
            groupBackgroundMode = QuiddityConstants.GROUP_BACKGROUND_MODE_BACKGROUND
        )
        assertTrue(backgroundPrompt.contains("【群聊背景】"), "背景模式应注入为【群聊背景】节")
        assertFalse(backgroundPrompt.contains("【群聊场景】"), "背景模式不应出现【群聊场景】节")
    }

    // ============================================================
    // 人设精调结构化解析
    // ============================================================

    @Test
    fun `parse persona refine result extracts all four sections`() {
        val raw = """
【身份背景】一位深夜电台主播，声音温柔。
【性格】说话轻声细语，多用"呢""哦"等语气词。
【外观】戴着圆框眼镜，常穿深蓝色毛衣。
【期望特质】永远先共情再给建议，不评判用户的选择。
        """.trim()
        val result = PromptBuilder.parsePersonaRefineResult(raw)
        assertEquals("一位深夜电台主播，声音温柔。", result.persona)
        assertEquals("说话轻声细语，多用\"呢\"\"哦\"等语气词。", result.character)
        assertEquals("戴着圆框眼镜，常穿深蓝色毛衣。", result.appearance)
        assertEquals("永远先共情再给建议，不评判用户的选择。", result.desired)
    }

    @Test
    fun `parse persona refine result keeps missing sections blank`() {
        val raw = """
【性格】开朗爱笑，偶尔自嘲。
【期望特质】保持自然，不尬聊。
        """.trim()
        val result = PromptBuilder.parsePersonaRefineResult(raw)
        assertEquals("", result.persona, "缺失章节应返回空串")
        assertEquals("", result.appearance, "缺失章节应返回空串")
        assertEquals("开朗爱笑，偶尔自嘲。", result.character)
        assertEquals("保持自然，不尬聊。", result.desired)
    }

    @Test
    fun `parse persona refine result trims surrounding separators`() {
        val raw = "【身份背景】：\n一位林间木屋的主人。\n\n【期望特质】\n沉稳可靠。"
        val result = PromptBuilder.parsePersonaRefineResult(raw)
        assertEquals("一位林间木屋的主人。", result.persona)
        assertEquals("沉稳可靠。", result.desired)
        assertEquals("", result.character)
    }

    @Test
    fun `persona refine suffix demands strict section format`() {
        val suffix = PromptBuilder.buildPersonaRefineSuffix(1024)
        assertTrue(suffix.contains("【身份背景】"), "精调后缀应要求按节输出")
        assertTrue(suffix.contains("【性格】"), "精调后缀应要求按节输出")
        assertTrue(suffix.contains("【外观】"), "精调后缀应要求按节输出")
        assertTrue(suffix.contains("【期望特质】"), "精调后缀应要求按节输出")
        assertTrue(suffix.contains("不要输出「名字」「世界背景」"), "精调后缀应排除名字与世界背景")
    }

}
