package com.quiddity.app.domain

import com.quiddity.app.data.model.ApiCatalogEntry
import com.quiddity.app.data.model.AppSettings
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import com.quiddity.app.util.CryptoUtils
import com.quiddity.app.util.QuiddityConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
 * 群聊回复请求规划测试（方案八：成员用各自私聊模型配置；senderId 透传；
 * 方案六.3：完整级带 search_chat 工具）。
 */
class GroupReplyPlannerTest {

    private val now = 1720000000000L

    private fun catalogEntry(id: String, model: String = "deepseek-v4-flash"): ApiCatalogEntry =
        ApiCatalogEntry(
            id = id,
            name = "测试配置",
            providerId = "deepseek",
            apiUrl = "https://api.deepseek.com/v1/chat/completions",
            apiModel = model,
            apiKeyEnc = CryptoUtils.encrypt("test-key")
        )

    private fun member(
        id: String = "member_a",
        apiCatalogId: String? = "cat_a",
        maxTokens: Int? = 2048,
        singleMessageTokens: Int? = 512
    ): Conversation = Conversation(
        id = id,
        createdAt = now,
        updatedAt = now,
        apiCatalogId = apiCatalogId,
        maxTokens = maxTokens,
        singleMessageTokens = singleMessageTokens
    )

    private fun group(): Conversation = Conversation(
        id = "group_1",
        createdAt = now,
        updatedAt = now
    )

    private fun msg(id: String, senderId: String? = null): Message = Message(
        id = id,
        conversationId = "group_1",
        role = Role.USER,
        content = "测试消息 $id",
        timestamp = now,
        senderId = senderId
    )

    @Test
    fun `member uses its own catalog and token config`() {
        val settings = AppSettings.Default.copy(
            activeCatalogId = "cat_global",
            catalog = listOf(catalogEntry("cat_a", model = "member-model"), catalogEntry("cat_global"))
        )
        val plan = GroupReplyPlanner.buildPlan(
            settings = settings,
            member = member(maxTokens = 4096, singleMessageTokens = 1024),
            group = group(),
            transcript = listOf(msg("m1", "member_a")),
            senderId = "member_a",
            tier = ApiCatalogManager.ModelTier.FULL
        ).getOrThrow()
        assertEquals("member-model", plan.request.model)
        assertEquals(4096, plan.request.max_tokens)
        assertEquals(1024, plan.singleMessageTokens)
        assertEquals("member_a", plan.senderId)
        assertEquals("test-key", plan.apiKey)
    }

    @Test
    fun `falls back to active catalog when member has no api config`() {
        val settings = AppSettings.Default.copy(
            activeCatalogId = "cat_global",
            catalog = listOf(catalogEntry("cat_global", model = "global-model"))
        )
        val plan = GroupReplyPlanner.buildPlan(
            settings = settings,
            member = member(apiCatalogId = null),
            group = group(),
            transcript = emptyList(),
            senderId = "member_a",
            tier = ApiCatalogManager.ModelTier.BASIC
        ).getOrThrow()
        assertEquals("global-model", plan.request.model)
    }

    @Test
    fun `full tier members get search chat tool`() {
        val settings = AppSettings.Default.copy(catalog = listOf(catalogEntry("cat_a")))
        val plan = GroupReplyPlanner.buildPlan(
            settings = settings,
            member = member(),
            group = group(),
            transcript = emptyList(),
            senderId = "member_a",
            tier = ApiCatalogManager.ModelTier.FULL
        ).getOrThrow()
        assertTrue(plan.useSearchTool)
        assertEquals("search_chat", plan.request.tools?.first()?.function?.name)
        assertEquals("auto", plan.request.tool_choice)
    }

    @Test
    fun `advanced tier members get search chat tool`() {
        val settings = AppSettings.Default.copy(catalog = listOf(catalogEntry("cat_a")))
        val plan = GroupReplyPlanner.buildPlan(
            settings = settings,
            member = member(),
            group = group(),
            transcript = emptyList(),
            senderId = "member_a",
            tier = ApiCatalogManager.ModelTier.ADVANCED
        ).getOrThrow()
        assertTrue(plan.useSearchTool)
        assertEquals("search_chat", plan.request.tools?.first()?.function?.name)
    }

    @Test
    fun `basic tier members have no tools`() {
        val settings = AppSettings.Default.copy(catalog = listOf(catalogEntry("cat_a")))
        val plan = GroupReplyPlanner.buildPlan(
            settings = settings,
            member = member(),
            group = group(),
            transcript = emptyList(),
            senderId = "member_a",
            tier = ApiCatalogManager.ModelTier.BASIC
        ).getOrThrow()
        assertNull(plan.request.tools)
        assertNull(plan.request.tool_choice)
    }

    @Test
    fun `transcript is prefixed with name and colon`() {
        val settings = AppSettings.Default.copy(catalog = listOf(catalogEntry("cat_a")))
        val transcript = listOf(
            msg("m1", "member_a"),
            msg("m2", "member_b")
        )
        val plan = GroupReplyPlanner.buildPlan(
            settings = settings,
            member = member(),
            group = group(),
            transcript = transcript,
            senderId = "member_a",
            tier = ApiCatalogManager.ModelTier.BASIC,
            senderNames = mapOf("member_a" to "小A", "member_b" to "小B")
        ).getOrThrow()
        val userMessages = plan.request.messages.filter { it.role == "user" }
        assertEquals("小A：测试消息 m1", userMessages[0].content)
        assertEquals("小B：测试消息 m2", userMessages[1].content)
    }

    @Test
    fun `user messages use the replying member's user persona name`() {
        val settings = AppSettings.Default.copy(catalog = listOf(catalogEntry("cat_a")))
        val transcript = listOf(
            Message(
                id = "m1",
                conversationId = "group_1",
                role = Role.USER,
                content = "大家好",
                timestamp = now,
                senderId = null
            ),
            msg("m2", "member_a")
        )
        val plan = GroupReplyPlanner.buildPlan(
            settings = settings,
            member = member().copy(
                userPersona = com.quiddity.app.data.model.UserPersona(name = "小明")
            ),
            group = group(),
            transcript = transcript,
            senderId = "member_a",
            tier = ApiCatalogManager.ModelTier.BASIC,
            senderNames = mapOf("member_a" to "小A"),
            userName = "小明"
        ).getOrThrow()
        val userMessages = plan.request.messages.filter { it.role == "user" }
        assertEquals("小明：大家好（对全体成员说）", userMessages[0].content)
        assertEquals("小A：测试消息 m2", userMessages[1].content)
    }

    @Test
    fun `regenerate previous reply flows into group system prompt`() {
        val settings = AppSettings.Default.copy(catalog = listOf(catalogEntry("cat_a")))
        val plan = GroupReplyPlanner.buildPlan(
            settings = settings,
            member = member().copy(
                persona = com.quiddity.app.data.model.Persona(name = "小A"),
                userPersona = com.quiddity.app.data.model.UserPersona(name = "小明")
            ),
            group = group(),
            transcript = listOf(msg("m1", "member_a")),
            senderId = "member_a",
            tier = ApiCatalogManager.ModelTier.BASIC,
            senderNames = mapOf("member_a" to "小A"),
            userName = "小明",
            regeneratePreviousReply = "嗯？怎么了"
        ).getOrThrow()
        val systemContent = plan.request.messages.first { it.role == "system" }.content.orEmpty()
        assertTrue(systemContent.contains("「重说」请求"), "重说信号应进入群聊系统提示词")
        assertTrue(systemContent.contains("嗯？怎么了"), "上一版回复应注入供对照")
    }

    @Test
    fun `member B sees A endearment without being treated as addressee`() {
        val settings = AppSettings.Default.copy(catalog = listOf(catalogEntry("cat_b")))
        val transcript = listOf(
            Message(
                id = "m1",
                conversationId = "group_1",
                role = Role.ASSISTANT,
                content = "今天的天气挺不错的，你觉得呢宝贝？",
                timestamp = now,
                senderId = "member_a"
            )
        )
        val plan = GroupReplyPlanner.buildPlan(
            settings = settings,
            member = member(id = "member_b").copy(
                persona = com.quiddity.app.data.model.Persona(name = "小B", character = "温柔"),
                userPersona = com.quiddity.app.data.model.UserPersona(name = "小明")
            ),
            group = group(),
            transcript = transcript,
            senderId = "member_b",
            tier = ApiCatalogManager.ModelTier.BASIC,
            senderNames = mapOf("member_a" to "小A"),
            userName = "小明"
        ).getOrThrow()

        val systemContent = plan.request.messages.first { it.role == "system" }.content.orEmpty()
        assertTrue(systemContent.contains("判断说话对象"), "B 的提示词应包含接话判断规则")
        assertTrue(systemContent.contains("默认是在叫用户或对全体说"), "昵称应默认指向用户")
        assertTrue(systemContent.contains("不要当成在叫你"), "B 不应把昵称当成在叫自己")

        val transcriptMessage = plan.request.messages.first { it.role == "assistant" }.content.orEmpty()
        assertEquals(
            "小A：今天的天气挺不错的，你觉得呢宝贝？",
            transcriptMessage,
            "无 @ 点名的成员消息不应被机械标注，交给规则判断"
        )
    }

    @Test
    fun `failure when no catalog configured`() {
        val settings = AppSettings.Default.copy(catalog = emptyList())
        val result = GroupReplyPlanner.buildPlan(
            settings = settings,
            member = member(apiCatalogId = null),
            group = group(),
            transcript = emptyList(),
            senderId = "member_a",
            tier = ApiCatalogManager.ModelTier.BASIC
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `member temperature falls back to global default and override wins`() {
        val settings = AppSettings.Default.copy(
            globalTemperature = 1.3,
            catalog = listOf(catalogEntry("cat_a"))
        )
        val fallback = GroupReplyPlanner.buildPlan(
            settings = settings,
            member = member(),
            group = group(),
            transcript = emptyList(),
            senderId = "member_a",
            tier = ApiCatalogManager.ModelTier.BASIC
        ).getOrThrow()
        assertEquals(1.3, fallback.request.temperature, "未设置会话温度时跟随全局默认")

        val override = GroupReplyPlanner.buildPlan(
            settings = settings,
            member = member().copy(temperature = 0.5),
            group = group(),
            transcript = emptyList(),
            senderId = "member_a",
            tier = ApiCatalogManager.ModelTier.BASIC
        ).getOrThrow()
        assertEquals(0.5, override.request.temperature, "会话级温度覆盖全局默认")
    }

    @Test
    fun `web search member reply plans responses api with server search tool`() {
        val settings = AppSettings.Default.copy(catalog = listOf(catalogEntry("cat_a")))
        val plan = GroupReplyPlanner.buildPlan(
            settings = settings,
            member = member().copy(temperature = 1.3),
            group = group(),
            transcript = listOf(msg("m1", "member_a")),
            senderId = "member_a",
            tier = ApiCatalogManager.ModelTier.FULL,
            senderNames = mapOf("member_a" to "小A"),
            webSearchResponsesUrl = "https://api.deepseek.com/responses"
        ).getOrThrow()
        val responses = plan.responsesRequest
        assertNotNull(responses, "启用联网搜索时应构造 Responses API 请求")
        assertEquals(QuiddityConstants.DEEPSEEK_RESPONSES_URL, plan.responsesApiUrl)
        assertEquals("deepseek-v4-flash", responses.model)
        assertEquals(1.3, responses.temperature)
        assertEquals(true, responses.tools?.any { it.type == "web_search" }, "必须携带服务端 web_search 工具")
        assertEquals(
            true,
            responses.tools?.any { it.type == "function" && it.name == "search_chat" },
            "完整级成员应保留 search_chat 工具"
        )
        assertEquals(
            "小A：测试消息 m1",
            responses.input.firstOrNull { it.role == "user" }?.content,
            "Responses input 应沿用群聊转述格式"
        )
        assertTrue(responses.instructions.isNullOrBlank().not(), "系统提示词应放入 instructions 字段")
    }

    @Test
    fun `web search disabled keeps chat completions plan`() {
        val settings = AppSettings.Default.copy(catalog = listOf(catalogEntry("cat_a")))
        val plan = GroupReplyPlanner.buildPlan(
            settings = settings,
            member = member(),
            group = group(),
            transcript = emptyList(),
            senderId = "member_a",
            tier = ApiCatalogManager.ModelTier.BASIC
        ).getOrThrow()
        assertNull(plan.responsesRequest, "未启用联网搜索时不应构造 Responses 请求")
        assertNull(plan.responsesApiUrl)
    }

    @Test
    fun `memory compression keeps old value when summary empty`() {
        assertEquals("新摘要", GroupReplyPlanner.applyMemoryCompression(
            "【摘要】\n新摘要\n【索引】\n群聊", "旧值"
        ))
        assertEquals("空输出", GroupReplyPlanner.applyMemoryCompression("空输出", "旧值"))
        assertEquals("旧值", GroupReplyPlanner.applyMemoryCompression("", "旧值"))
    }
}
