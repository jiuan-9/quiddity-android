package com.quiddity.app.data.repo

import com.quiddity.app.data.remote.ChatMessage
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.quiddity.app.util.QuiddityConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        assertTrue(looksTruncated("然后他（"), "以未闭合括号结尾视为截断")
        assertTrue(looksTruncated("他说“"), "以未闭合引号结尾视为截断")
        assertTrue(looksTruncated("接着他说「"), "以未闭合直角引号结尾视为截断")
    }

    @Test
    fun `looksTruncated does not flag complete endings`() {
        assertFalse(looksTruncated("好的呀"), "完整短句不应误判")
        assertFalse(looksTruncated("今晚月色真美。"), "以句号结尾不应误判")
        assertFalse(looksTruncated("声音有点颤抖的说："), "以全角冒号结尾是自然结尾，不应误判为截断")
        assertFalse(looksTruncated("她轻轻地说:"), "以半角冒号结尾是自然结尾，不应误判为截断")
        assertFalse(looksTruncated("话还没说完，"), "以逗号结尾是自然结尾，不应误判为截断")
        assertFalse(
            looksTruncated("好，我跳到微信，一步步点进去。先打开微信:"),
            "以「先打开微信:」这类预告式冒号结尾不应触发自动续写循环"
        )
        assertFalse(looksTruncated(""), "空内容不应误判")
        assertFalse(looksTruncated("   "), "纯空白不应误判")
    }

    @Test
    fun `isActionOnlyReply flags action-only replies`() {
        assertTrue(isActionOnlyReply("（轻笑）"), "单括号动作应判定为仅动作")
        assertTrue(isActionOnlyReply("（点头）（微笑）"), "多个连续动作应判定为仅动作")
        assertTrue(isActionOnlyReply("（深吸一口气，又缓缓吐出）"), "长动作描写应判定为仅动作")
        assertTrue(isActionOnlyReply("（轻笑）\n（小声嘟囔）"), "换行分隔的多个动作应判定为仅动作")
        assertFalse(isActionOnlyReply(""), "空内容不判定")
        assertFalse(isActionOnlyReply("   "), "纯空白不判定")
    }

    @Test
    fun `isActionOnlyReply does not flag speech or mixed replies`() {
        assertFalse(isActionOnlyReply("（轻笑）你好呀。"), "动作+台词不判定为仅动作")
        assertFalse(isActionOnlyReply("你好呀"), "纯台词不判定为仅动作")
        assertFalse(isActionOnlyReply("（心跳加速）怎么办，我好像有点喜欢你。"), "动作+台词不判定为仅动作")
        assertFalse(isActionOnlyReply("【系统】这是一条系统记录"), "方括号内容可能是系统记录，不判定为仅动作")
        assertFalse(isActionOnlyReply("我回来了"), "普通文本不判定为仅动作")
    }

    @Test
    fun `replySimilarityRatio distinguishes same from different replies`() {
        assertTrue(
            replySimilarityRatio("她轻轻笑了下，说：今天天气真好。", "她轻轻笑了下，说：今天天气真好。") >=
                REGENERATE_SIMILARITY_THRESHOLD,
            "完全相同的回复应判定为高度相似"
        )
        assertTrue(
            replySimilarityRatio("（轻笑）今天天气真好，我们出去走走吧。", "今天天气真好，我们出去走走吧。") >=
                REGENERATE_SIMILARITY_THRESHOLD,
            "仅动作括号不同、台词相同的回复应判定为高度相似"
        )
        assertTrue(
            replySimilarityRatio("我有点紧张，但还是鼓起勇气开口。", "我有点紧张，但还是鼓起勇气开口了。") >=
                REGENERATE_SIMILARITY_THRESHOLD,
            "仅个别字词不同的回复应判定为高度相似"
        )
        assertTrue(
            replySimilarityRatio("今天天气真好，我们出去走走吧。", "刚才那件事我仔细想了想，还是先不打扰你了。") <
                REGENERATE_SIMILARITY_THRESHOLD,
            "完全不同话题的回复不应判定为高度相似"
        )
        assertEquals(0f, replySimilarityRatio("", "内容"), "空内容相似度为 0")
    }
}
