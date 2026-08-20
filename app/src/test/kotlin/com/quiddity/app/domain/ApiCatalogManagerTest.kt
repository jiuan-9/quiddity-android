package com.quiddity.app.domain

import com.quiddity.app.data.remote.ChatApi
import com.quiddity.app.data.model.AppSettings
import com.quiddity.app.data.model.Conversation
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
 * [ApiCatalogManager] 单元测试。
 *
 * 验证模型分级与应用内模型列表的一致性，防止 provider 新增模型后分级表遗漏
 * 或自定义模型等级规则被破坏。
 */
class ApiCatalogManagerTest {

    private val manager = ApiCatalogManager(ChatApi())

    @Test
    fun `buildEntry with blank id generates a fresh id`() {
        val a = manager.buildEntry(
            id = "",
            name = "配置A",
            providerId = "deepseek",
            apiUrl = "https://api.deepseek.com/v1/chat/completions",
            apiModel = "deepseek-chat",
            apiKey = "test-key-a"
        )
        val b = manager.buildEntry(
            id = "",
            name = "配置B",
            providerId = "deepseek",
            apiUrl = "https://api.deepseek.com/v1/chat/completions",
            apiModel = "deepseek-chat",
            apiKey = "test-key-b"
        )
        assertTrue(a.id.isNotBlank(), "空串 id 必须生成独立新 id，否则保存第二条会覆盖第一条")
        assertTrue(b.id.isNotBlank(), "空串 id 必须生成独立新 id")
        assertTrue(a.id != b.id, "两次新建生成的 id 不能相同")
        assertTrue(a.apiKeyEnc.isNotEmpty(), "配置A的密钥应已加密保存")
        assertTrue(b.apiKeyEnc.isNotEmpty(), "配置B的密钥应已加密保存")
    }

    @Test
    fun `buildEntry keeps non blank id`() {
        val entry = manager.buildEntry(
            id = "fixed-id",
            name = "配置",
            providerId = "deepseek",
            apiUrl = "https://api.deepseek.com/v1/chat/completions",
            apiModel = "deepseek-chat",
            apiKey = "test-key"
        )
        assertEquals("fixed-id", entry.id)
    }

    @Test
    fun `decryptKey returns null when key missing or undecryptable`() {
        val noKey = manager.buildEntry(
            id = "e1",
            name = "无密钥",
            providerId = "deepseek",
            apiUrl = "https://api.deepseek.com/v1/chat/completions",
            apiModel = "deepseek-chat",
            apiKey = ""
        )
        assertEquals(false, manager.hasStoredKey(noKey))
        assertEquals(null, manager.decryptKey(noKey))

        val withKey = manager.buildEntry(
            id = "e2",
            name = "有密钥",
            providerId = "deepseek",
            apiUrl = "https://api.deepseek.com/v1/chat/completions",
            apiModel = "deepseek-chat",
            apiKey = "sk-test"
        )
        assertEquals(true, manager.hasStoredKey(withKey))
        assertEquals("sk-test", manager.decryptKey(withKey))

        val corrupt = withKey.copy(apiKeyEnc = "corrupt-!!!")
        assertEquals(null, manager.decryptKey(corrupt))
    }

    @Test
    fun `all provider models have a tier`() {
        val providerModels = manager.providers
            .filter { it.id != "custom" }
            .flatMap { it.models }
            .toSet()

        val tieredModels = manager.tieredModels().values.flatten().toSet()

        assertEquals(
            providerModels,
            tieredModels,
            "provider 模型列表与分级表必须完全一致，差集：" +
                "${(providerModels - tieredModels) + (tieredModels - providerModels)}"
        )
    }

    @Test
    fun `supported model count is exactly 62`() {
        val total = manager.tieredModels().values.sumOf { it.size }
        assertEquals(62, total, "应用内置支持的模型总数应为 62")
    }

    @Test
    fun `deepseek-v4-flash is full tier`() {
        assertEquals(
            ApiCatalogManager.ModelTier.FULL,
            manager.getModelTier("deepseek-v4-flash", "deepseek")
        )
        assertEquals(
            ApiCatalogManager.ModelTier.FULL,
            manager.getModelTier("deepseek-ai/DeepSeek-V4-Flash", "siliconflow")
        )
    }

    @Test
    fun `custom provider models are always full tier`() {
        assertEquals(
            ApiCatalogManager.ModelTier.FULL,
            manager.getModelTier("any-model", "custom")
        )
        assertEquals(
            ApiCatalogManager.ModelTier.FULL,
            manager.getModelTier("", "custom")
        )
    }

    @Test
    fun `unknown model falls back to full tier`() {
        assertEquals(
            ApiCatalogManager.ModelTier.FULL,
            manager.getModelTier("not-listed-model", "openai")
        )
    }

    @Test
    fun `tiered models are grouped correctly`() {
        val tiered = manager.tieredModels()
        assertTrue(tiered.containsKey(ApiCatalogManager.ModelTier.FULL))
        assertTrue(tiered.containsKey(ApiCatalogManager.ModelTier.ADVANCED))
        assertTrue(tiered.containsKey(ApiCatalogManager.ModelTier.BASIC))

        val all = tiered.values.flatten()
        assertEquals(all.size, all.toSet().size, "分级表中不允许重复模型 ID")
    }

    @Test
    fun `server web search is deepseek flash only`() {
        val deepseekFlash = manager.buildEntry(
            id = null, name = "DS", providerId = "deepseek",
            apiUrl = "https://api.deepseek.com/v1/chat/completions",
            apiModel = "deepseek-v4-flash", apiKey = "k"
        )
        val deepseekPro = deepseekFlash.copy(apiModel = "deepseek-v4-pro")
        val aggregated = manager.buildEntry(
            id = null, name = "聚合", providerId = "siliconflow",
            apiUrl = "https://api.siliconflow.cn/v1/chat/completions",
            apiModel = "deepseek-ai/DeepSeek-V4-Flash", apiKey = "k"
        )
        val custom = deepseekFlash.copy(providerId = "custom")
        val customNonOfficial = custom.copy(apiUrl = "https://example.com/v1/chat/completions")
        assertTrue(manager.supportsServerWebSearch(deepseekFlash), "官方 deepseek-v4-flash 应支持服务端搜索")
        assertFalse(manager.supportsServerWebSearch(deepseekPro), "官方 deepseek-v4-pro 暂不支持 Responses API")
        assertFalse(manager.supportsServerWebSearch(aggregated), "聚合平台不具备 DeepSeek 官方服务端搜索")
        assertTrue(
            manager.supportsServerWebSearch(custom),
            "自定义条目但 URL 指向官方 DeepSeek 端点应支持服务端搜索"
        )
        assertFalse(manager.supportsServerWebSearch(customNonOfficial), "非官方地址不具备官方服务端搜索")
    }

    @Test
    fun `responses api url comes from provider preset`() {
        val deepseek = manager.buildEntry(
            id = null, name = "DS", providerId = "deepseek",
            apiUrl = "https://api.deepseek.com/v1/chat/completions",
            apiModel = "deepseek-v4-flash", apiKey = "k"
        )
        val other = manager.buildEntry(
            id = null, name = "Qwen", providerId = "alibaba",
            apiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            apiModel = "qwen-plus", apiKey = "k"
        )
        assertEquals(QuiddityConstants.DEEPSEEK_RESPONSES_URL, manager.responsesApiUrl(deepseek))
        assertNull(manager.responsesApiUrl(other), "无官方 Responses 端点时应返回 null")
    }

    @Test
    fun `resolveEntry follows conversation override then active then first`() {
        val a = manager.buildEntry(
            id = "a", name = "A", providerId = "deepseek",
            apiUrl = "https://api.deepseek.com/v1/chat/completions",
            apiModel = "deepseek-v4-flash", apiKey = "k"
        )
        val b = a.copy(id = "b", apiModel = "deepseek-v4-pro")
        val c = a.copy(id = "c")
        val settings = AppSettings.Default.copy(activeCatalogId = "b", catalog = listOf(a, b, c))
        val base = Conversation(id = "conv", createdAt = 0, updatedAt = 0)
        assertEquals("b", manager.resolveEntry(settings, base)?.id, "无会话覆盖时使用全局激活条目")
        assertEquals(
            "a",
            manager.resolveEntry(settings, base.copy(apiCatalogId = "a"))?.id,
            "会话级覆盖优先于全局激活"
        )
        assertNull(
            manager.resolveEntry(settings.copy(catalog = emptyList()), base),
            "catalog 为空时返回 null"
        )
    }

    @Test
    fun `vision providers preset is not empty and ends with custom`() {
        assertTrue(manager.visionProviders.isNotEmpty(), "视觉 OCR 服务商预置不应为空")
        assertEquals("custom", manager.visionProviders.last().id, "自定义服务商应位于视觉名册末尾")
    }

    @Test
    fun `isVisionModel classifies built-in vision and text models`() {
        assertTrue(manager.isVisionModel("kimi-k3", "moonshot"), "Kimi K3 原生视觉")
        assertTrue(manager.isVisionModel("kimi-k2.6", "moonshot"), "Kimi K2.6 原生视觉")
        assertTrue(manager.isVisionModel("MiniMax-M3", "minimax"), "MiniMax M3 原生多模态")
        assertTrue(manager.isVisionModel("qwen-vl-max", "alibaba"), "通义千问 VL")
        assertTrue(manager.isVisionModel("glm-4.1v-thinking-flash", "zhipu"), "智谱 GLM-4.1V-Thinking-Flash")
        assertTrue(manager.isVisionModel("glm-4.6v-flash", "zhipu"), "智谱 GLM 视觉")
        assertTrue(manager.isVisionModel("gemini-2.5-flash", "google"), "Gemini 全系多模态")
        assertTrue(manager.isVisionModel("gpt-5.4", "openai"), "GPT-5 系列支持视觉")
        assertTrue(manager.isVisionModel("mimo-v2.5", "xiaomi"), "小米 MiMo v2.5 全模态理解")
        assertTrue(manager.isVisionModel("qwen3.8-max", "alibaba"), "Qwen3.8 Max 官方多模态输入")
        assertTrue(
            manager.isVisionModel("doubao-seed-2-1-pro-260628", "bytedance"),
            "豆包 Seed 2.1 Pro 官方多模态理解"
        )

        assertFalse(manager.isVisionModel("deepseek-v4-flash", "deepseek"), "DeepSeek 纯文本")
        assertFalse(manager.isVisionModel("qwen-plus", "alibaba"), "通义千问文本模型")
        assertFalse(manager.isVisionModel("glm-5.2", "zhipu"), "GLM 文本模型")
        assertFalse(manager.isVisionModel("mimo-v2.5-pro", "xiaomi"), "MiMo V2.5 Pro 为纯文本旗舰")
        assertFalse(manager.isVisionModel("kimi-k3", "custom"), "自定义服务商一律按纯文本处理")
    }

    @Test
    fun `resolveOcrEntry prefers built-in vision chat model then active vision entry`() {
        val visionChat = manager.buildEntry(
            id = "chat-vision", name = "Kimi K3", providerId = "moonshot",
            apiUrl = "https://api.moonshot.cn/v1/chat/completions",
            apiModel = "kimi-k3", apiKey = "k"
        )
        val textChat = visionChat.copy(
            id = "chat-text",
            apiModel = "deepseek-v4-flash",
            providerId = "deepseek"
        )
        val ocrA = visionChat.copy(
            id = "ocr-a", name = "GLM",
            providerId = "zhipu", apiModel = "glm-4.6v-flash"
        )
        val ocrB = visionChat.copy(
            id = "ocr-b", name = "Qwen-VL",
            providerId = "alibaba", apiModel = "qwen-vl-max"
        )
        val settings = AppSettings.Default.copy(
            activeVisionCatalogId = "ocr-b",
            visionCatalog = listOf(ocrA, ocrB)
        )
        assertEquals(
            "chat-vision",
            manager.resolveOcrEntry(settings, visionChat)?.id,
            "自带视觉优先使用当前对话模型"
        )
        assertEquals(
            "ocr-b",
            manager.resolveOcrEntry(settings, textChat)?.id,
            "纯文本模型回退到当前选中的视觉 OCR 配置"
        )
        assertEquals(
            "ocr-a",
            manager.resolveOcrEntry(settings.copy(activeVisionCatalogId = null), textChat)?.id,
            "未选中时取视觉名册第一条"
        )
        assertNull(
            manager.resolveOcrEntry(settings.copy(visionCatalog = emptyList()), textChat),
            "无视觉 OCR 配置时返回 null"
        )
    }

    @Test
    fun `defaultMaxTemperature caps known models and leaves others unlimited`() {
        assertEquals(1.0, manager.defaultMaxTemperature("claude-sonnet-4-6"), "Claude 温度上限 1.0")
        assertEquals(1.0, manager.defaultMaxTemperature("claude-opus-4-8"), "Claude 温度上限 1.0")
        assertEquals(1.5, manager.defaultMaxTemperature("mimo-v2.5-pro"), "MiMo V2.5 Pro 温度上限 1.5")
        assertEquals(1.5, manager.defaultMaxTemperature("mimo-v2.5"), "MiMo V2.5 温度上限 1.5")
        assertNull(manager.defaultMaxTemperature("deepseek-v4-flash"), "未知模型不限制")
        assertNull(manager.defaultMaxTemperature("gpt-4o-mini"), "未知模型不限制")
    }

    @Test
    fun `xiaomi mimo models are full tier`() {
        assertEquals(
            ApiCatalogManager.ModelTier.FULL,
            manager.getModelTier("mimo-v2.5-pro", "xiaomi"),
            "MiMo V2.5 Pro 应归完整级"
        )
        assertEquals(
            ApiCatalogManager.ModelTier.FULL,
            manager.getModelTier("mimo-v2.5", "xiaomi"),
            "MiMo V2.5 应归完整级"
        )
    }

    @Test
    fun `newest flagship models are classified by capability`() {
        assertEquals(ApiCatalogManager.ModelTier.FULL, manager.getModelTier("qwen3.8-max", "alibaba"))
        assertEquals(ApiCatalogManager.ModelTier.FULL, manager.getModelTier("glm-5.3", "zhipu"))
        assertEquals(ApiCatalogManager.ModelTier.FULL, manager.getModelTier("hy3", "tencent"))
        assertEquals(ApiCatalogManager.ModelTier.ADVANCED, manager.getModelTier("spark-x2", "iflytek"))
        assertEquals(ApiCatalogManager.ModelTier.BASIC, manager.getModelTier("spark-x2-flash", "iflytek"))
    }

    @Test
    fun `buildEntry applies known max temperature when user leaves it blank`() {
        val entry = manager.buildEntry(
            id = null,
            name = "Claude",
            providerId = "anthropic",
            apiUrl = "https://example.com/v1/chat/completions",
            apiModel = "claude-sonnet-4-5",
            apiKey = "sk-test",
            maxTemperature = null
        )
        assertEquals(1.0, entry.maxTemperature, "预设 Claude 模型应自动套用 1.0 上限")
    }
}
