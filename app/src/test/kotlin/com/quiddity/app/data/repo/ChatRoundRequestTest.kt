package com.quiddity.app.data.repo

import com.quiddity.app.data.remote.ChatMessage
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.quiddity.app.util.QuiddityConstants
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
 * 聊天请求载体测试：Responses（联网搜索）与 Completions 各自携带正确端点。
 *
 * 回归保护：私聊联网曾把 Responses 请求体发到 chat/completions 端点导致 400，
 * 根因是 URL 在调用点各自传参。现在 URL 由请求载体自身携带，
 * 本测试锁定"Responses 必须带 /responses 端点、Completions 必须带 chat/completions 端点"。
 */
class ChatRoundRequestTest {

    private val access = ApiAccess.Resolved(
        apiUrl = "https://api.deepseek.com/v1/chat/completions",
        apiKey = "test-key",
        model = QuiddityConstants.DEEPSEEK_RESPONSES_MODEL
    )

    private val apiMessages = listOf(
        ChatMessage(role = "system", content = "系统提示"),
        ChatMessage(role = "user", content = "你好")
    )

    @Test
    fun `responses request carries responses endpoint url`() {
        val request = buildChatRound(
            access = access,
            systemPrompt = "系统提示",
            apiMessages = apiMessages,
            maxTokens = 512,
            temperature = 1.0,
            responsesUrl = QuiddityConstants.DEEPSEEK_RESPONSES_URL,
            reasoningEffort = null,
            tools = null,
            tool_choice = null
        )
        assertTrue(request is ChatRoundRequest.Responses, "启用联网搜索时应构造 Responses 请求")
        request as ChatRoundRequest.Responses
        assertEquals(QuiddityConstants.DEEPSEEK_RESPONSES_URL, request.apiUrl, "Responses 请求必须携带 /responses 端点")
        assertEquals("deepseek-v4-flash", request.request.model)
        assertTrue(
            request.request.tools?.any { it.type == "web_search" } == true,
            "联网搜索必须携带服务端 web_search 工具"
        )
    }

    @Test
    fun `completions request carries chat completions endpoint url`() {
        val request = buildChatRound(
            access = access,
            systemPrompt = "系统提示",
            apiMessages = apiMessages,
            maxTokens = 512,
            temperature = 1.0,
            responsesUrl = null,
            reasoningEffort = null,
            tools = null,
            tool_choice = null
        )
        assertTrue(request is ChatRoundRequest.Completions, "未启用联网搜索时应构造 Completions 请求")
        request as ChatRoundRequest.Completions
        assertEquals("https://api.deepseek.com/v1/chat/completions", request.apiUrl)
    }

    @Test
    fun `looksTruncated flags obvious continuation endings`() {
        assertTrue(looksTruncated("声音有点颤抖的说："), "以全角冒号结尾视为截断")
        assertTrue(looksTruncated("她轻轻地说:"), "以半角冒号结尾视为截断")
        assertTrue(looksTruncated("然后他（"), "以未闭合括号结尾视为截断")
        assertTrue(looksTruncated("他说“"), "以未闭合引号结尾视为截断")
        assertTrue(looksTruncated("话还没说完，"), "以逗号结尾视为截断")
    }

    @Test
    fun `looksTruncated does not flag complete endings`() {
        assertFalse(looksTruncated("好的呀"), "完整短句不应误判")
        assertFalse(looksTruncated("今晚月色真美。"), "以句号结尾不应误判")
        assertFalse(looksTruncated(""), "空内容不应误判")
        assertFalse(looksTruncated("   "), "纯空白不应误判")
    }
}
