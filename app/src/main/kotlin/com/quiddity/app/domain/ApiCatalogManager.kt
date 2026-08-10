package com.quiddity.app.domain

import com.quiddity.app.data.model.ApiCatalogEntry
import com.quiddity.app.data.model.AppSettings
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.remote.ChatApi
import com.quiddity.app.util.CryptoUtils
import com.quiddity.app.util.IdGenerator
import com.quiddity.app.util.QuiddityConstants

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
 * API 名册管理器。
 *
 * 把 Provider 预设、ID 生成、Key 加解密、连接测试等逻辑集中到一处，对外暴露统一的：
 *   - [providers] 服务商预设
 *   - [generateId] id 生成
 *   - [encryptKey]/[decryptKey] 加解密
 *   - [testConnection] 连接测试
 *
 * 所有上层（ViewModel / UI）均通过本类访问 API 名册领域能力，避免重复实现。
 */
class ApiCatalogManager(
    private val chatApi: ChatApi
) {

    // ==================== 服务商预设（唯一数据源） ====================

    /**
     * API 服务商预设数据类。
     *
     * 包括 id / 显示名 / 默认 URL / 模型 id 列表。
     * 模型 id（用于 API 请求的 model 字段）严格沿用桌面版定义，确保两端
     * 导出的 API 名册可互通。
     */
    data class Provider(
        val id: String,
        val name: String,
        val defaultUrl: String,
        val keyUrl: String,
        val models: List<String>,
        /**
         * 该服务商官方支持的 Responses API 端点（服务端 web_search）。
         * null = 该服务商无服务端搜索能力。
         */
        val responsesUrl: String? = null
    )

    /**
     * 模型能力分级。
     *
     * 综合 `d:\桌面\最终报告.txt` 的实测得分与成本/主流度划分：
     * - [FULL] 完整级：实测得分高，或便宜且为对应厂商主流模型，可编辑全部人设字段。
     * - [ADVANCED] 进阶级：得分中等，禁用「你希望ta是什么样的」编辑，自动注入 standard 指令。
     * - [BASIC] 基础级：得分较低或入门级轻量模型，仅开放名字/身份/性格，自动注入 basic 指令。
     */
    enum class ModelTier { FULL, ADVANCED, BASIC }

    /**
     * 内置模型能力分级表。
     *
     * 以 provider 模型 ID 为键，覆盖 [providers] 中全部 54 个内置模型。
     * 单表维护可避免三个独立 set 出现遗漏或重复，并确保“应用内支持的模型
     * 与分级表完全一致”。
     *
     * 分级原则（综合实测得分与成本/主流度）：
     * - [ModelTier.FULL] 完整级：实测得分高，或便宜且为对应厂商主流模型，
     *   可编辑全部人设字段。
     * - [ModelTier.ADVANCED] 进阶级：得分中等，禁用「你希望ta是什么样的」编辑。
     * - [ModelTier.BASIC] 基础级：得分较低或入门级轻量模型，仅开放名字/身份/性格。
     *
     * 自定义服务商（providerId == "custom"）的模型在 [getModelTier] 中强制归为
     * 完整级，与“如果您确定要自己决定模型的等级，请使用自定义添加模型”的说明一致。
     */
    private val MODEL_TIER_MAP: Map<String, ModelTier> = mapOf(
        // ==================== 完整级（总分 ≥ 36.1 或便宜主流） ====================
        "doubao-seed-evolving" to ModelTier.FULL,
        "doubao-seed-2-1-pro-260628" to ModelTier.FULL,
        "moonshotai/Kimi-K3" to ModelTier.FULL,
        "deepseek-ai/DeepSeek-V4-Pro" to ModelTier.FULL,
        "deepseek-ai/DeepSeek-V4-Flash" to ModelTier.FULL, // 便宜且主流
        "zai-org/GLM-5.2" to ModelTier.FULL,
        "kimi-k3" to ModelTier.FULL,
        "deepseek-v4-pro" to ModelTier.FULL,
        "deepseek-v4-flash" to ModelTier.FULL, // 便宜且主流
        "doubao-seed-2-1-turbo-260628" to ModelTier.FULL,
        "ernie-5.1" to ModelTier.FULL,
        "glm-5.2" to ModelTier.FULL,
        "qwen-plus" to ModelTier.FULL, // 阿里云主流平价模型
        "hunyuan-role-latest" to ModelTier.FULL, // 腾讯混元角色模型，成本友好
        "doubao-seed-2-0-lite-260428" to ModelTier.FULL, // 豆包轻量主流模型
        "ByteDance-Seed/Seed-OSS-36B-Instruct" to ModelTier.FULL, // 开源低成本

        // ==================== 进阶级（总分 31.0 - 35.6） ====================
        "qwen3.7-max" to ModelTier.ADVANCED,
        "Qwen/Qwen3.5-397B-A17B" to ModelTier.ADVANCED,
        "zai-org/GLM-5.1" to ModelTier.ADVANCED,
        "ernie-x1.1" to ModelTier.ADVANCED,
        "MiniMax-M3" to ModelTier.ADVANCED,
        "glm-5.1" to ModelTier.ADVANCED,
        "moonshotai/Kimi-K2.6" to ModelTier.ADVANCED,
        "ernie-5.0" to ModelTier.ADVANCED,
        "kimi-k2.6" to ModelTier.ADVANCED,
        "tencent/Hy3" to ModelTier.ADVANCED,
        "qwen3.7-plus" to ModelTier.ADVANCED,
        "zai-org/GLM-5" to ModelTier.ADVANCED,
        "4.0Ultra" to ModelTier.ADVANCED,
        "MiniMax-M2.7" to ModelTier.ADVANCED,
        "hy3-preview" to ModelTier.ADVANCED,
        "ernie-4.5-turbo-128k" to ModelTier.ADVANCED,
        "glm-5" to ModelTier.ADVANCED,
        "glm-4-plus" to ModelTier.ADVANCED,
        "Qwen/Qwen3.6-35B-A3B" to ModelTier.ADVANCED,
        "doubao-seed-2-0-code-preview-260215" to ModelTier.ADVANCED,
        "MiniMaxAI/MiniMax-M2.5" to ModelTier.ADVANCED,
        "kimi-k2.7-code" to ModelTier.ADVANCED,
        "qwen3-coder-plus" to ModelTier.ADVANCED,
        "MiniMax-M2.7-highspeed" to ModelTier.ADVANCED,
        "qwen3.6-35b-a3b" to ModelTier.ADVANCED,
        "MiniMax-M2.5" to ModelTier.ADVANCED,

        // ==================== 基础级（总分 ≤ 30.9） ====================
        "kimi-k2.7-code-highspeed" to ModelTier.BASIC,
        "qwen3.6-flash" to ModelTier.BASIC,
        "spark-x" to ModelTier.BASIC,
        "step-3.7-flash" to ModelTier.BASIC,
        "glm-4-air" to ModelTier.BASIC,
        "step-3.5-flash" to ModelTier.BASIC,
        "doubao-seed-2-0-mini-260428" to ModelTier.BASIC,
        "generalv3.5" to ModelTier.BASIC,
        "qwen-flash" to ModelTier.BASIC,
        "glm-4-flash" to ModelTier.BASIC,
        "pro-128k" to ModelTier.BASIC,
        "lite" to ModelTier.BASIC
    )

    /**
     * 自带视觉能力的模型清单（图片理解 / 多模态）。
     *
     * 覆盖两类来源：
     * - 聊天名册中"模型本身就支持图片输入"的模型（如 Kimi K3 / K2.6、MiniMax M3），
     *   发送图片时直接使用当前对话 API 做识图，无需再走 OCR 兜底；
     * - 视觉 OCR 名册中的预置视觉模型 ID。
     *
     * 匹配规则（[isVisionModel]）：
     * 1. 精确命中本清单（不区分大小写）；
     * 2. 模型 ID 包含常见视觉关键词（vision / vl / omni / ocr），或命中全系多模态前缀
     *    （gemini / gpt-5 / gpt-4o / claude），以兼容清单未覆盖的新模型；
     * 3. 自定义服务商一律视为纯文本，交给用户配置的 OCR 兜底。
     */
    private val VISION_MODEL_MAP: Set<String> = setOf(
        // OpenAI
        "gpt-5.5", "gpt-5.4", "gpt-4o",
        // Google Gemini（全系多模态）
        "gemini-3.5-flash", "gemini-3.5-flash-lite", "gemini-2.5-flash", "gemini-2.5-pro",
        // Anthropic Claude（全系支持视觉）
        "claude-sonnet-4-6", "claude-sonnet-4-5", "claude-opus-4-8",
        // 阿里云通义千问 VL / Omni
        "qwen-vl-max", "qwen-vl-plus", "qwen3.5-omni-plus",
        "qwen2.5-vl-72b-instruct", "qwen2.5-vl-7b-instruct",
        "Qwen/Qwen2.5-VL-72B-Instruct", "Qwen/Qwen2.5-VL-7B-Instruct",
        "Qwen/Qwen3-Omni-30B-A3B-Instruct",
        // 智谱 GLM 视觉
        "glm-4.1v-thinking-flash",
        "glm-4.6v-flash", "glm-4.6v", "glm-4v-plus", "glm-4v-flash",
        "zai-org/GLM-5V-Turbo",
        // 月之暗面 Kimi（K3 / K2.6 / K2.5 原生视觉）
        "kimi-k3", "kimi-k2.6", "kimi-k2.5", "moonshot-v1-32k-vision-preview",
        "moonshotai/Kimi-K3", "moonshotai/Kimi-K2.6",
        // 字节豆包视觉
        "doubao-seed-1-6-vision-250815", "doubao-1.5-thinking-vision-pro",
        "doubao-1.5-vision-pro", "doubao-1.5-vision-lite",
        // 百度文心 ERNIE-VL
        "ernie-4.5-turbo-vl", "ernie-4.5-turbo-vl-32k",
        // MiniMax 多模态
        "MiniMax-M3", "MiniMaxAI/MiniMax-M3",
        // 硅基流动 OCR / 视觉专用模型
        "deepseek-ai/DeepSeek-OCR", "PaddlePaddle/PaddleOCR-VL"
    )

    private val VISION_MODEL_MAP_NORMALIZED: Set<String> =
        VISION_MODEL_MAP.mapTo(mutableSetOf()) { it.lowercase() }

    /**
     * 视觉能力关键词 / 全系多模态前缀。
     * 命中即认为模型支持图片输入，便于覆盖各平台后续新增的视觉模型。
     */
    private val VISION_MODEL_KEYWORDS: List<String> = listOf(
        "vision", "-vl", "omni", "ocr", "gemini", "gpt-5", "gpt-4o", "claude"
    )

    /**
     * 判断模型是否自带视觉能力（可直接接收图片）。
     *
     * - 自定义服务商无法确认能力，一律按纯文本处理，交由 OCR 兜底；
     * - 其余模型按 [VISION_MODEL_MAP] 精确匹配 + 关键词兜底。
     */
    fun isVisionModel(apiModel: String, providerId: String): Boolean {
        val m = apiModel.trim().lowercase()
        if (m.isEmpty()) return false
        if (providerId == "custom") return false
        if (m in VISION_MODEL_MAP_NORMALIZED) return true
        return VISION_MODEL_KEYWORDS.any { m.contains(it) }
    }

    /**
     * 解析图片识图实际使用的模型配置（优先级）：
     *
     * 1. 当前对话模型自带视觉 → 直接使用当前对话 API（URL / Key / 模型名均一致）；
     * 2. 否则回退用户配置的视觉 OCR 模型（先当前选中项，再名册第一条）。
     *
     * 返回 null 表示无可用识图配置，由调用方给出引导提示。
     */
    fun resolveOcrEntry(settings: AppSettings, chatEntry: ApiCatalogEntry?): ApiCatalogEntry? {
        if (chatEntry != null && isVisionModel(chatEntry.apiModel, chatEntry.providerId)) {
            return chatEntry
        }
        return settings.visionCatalog.firstOrNull { it.id == settings.activeVisionCatalogId }
            ?: settings.visionCatalog.firstOrNull()
    }

    /**
     * 支持服务端联网搜索（Responses API web_search）的模型清单。
     *
     * 官方文档：Responses API 目前仅支持 deepseek-v4-flash，暂不支持 deepseek-v4-pro。
     * 随官方开放范围维护，新增模型时在此追加。
     */
    private val RESPONSES_SUPPORTED_MODELS: Set<String> = setOf(
        QuiddityConstants.DEEPSEEK_RESPONSES_MODEL
    )

    /**
     * 查询模型所属分级。
     *
     * - 自定义服务商（[providerId] == "custom"）自动归为完整级。
     * - 名单外的模型默认按完整级处理，避免名单更新滞后导致功能误禁用。
     */
    fun getModelTier(apiModel: String, providerId: String): ModelTier {
        if (providerId == "custom") return ModelTier.FULL
        return MODEL_TIER_MAP[apiModel] ?: ModelTier.FULL
    }

    /**
     * 解析会话实际使用的 catalog 条目（与会话级覆盖 → 全局激活 → 第一条的顺序一致）。
     */
    fun resolveEntry(settings: AppSettings, conv: Conversation): ApiCatalogEntry? =
        settings.catalog
            .firstOrNull { it.id == conv.apiCatalogId }
            ?: settings.catalog.firstOrNull { it.id == settings.activeCatalogId }
            ?: settings.catalog.firstOrNull()

    /**
     * 该条目所属服务商官方提供的 Responses API 端点；不支持时返回 null。
     */
    fun responsesApiUrl(entry: ApiCatalogEntry): String? =
        findProvider(entry.providerId).responsesUrl
            // 自定义条目但 URL 指向官方 DeepSeek 端点：按官方能力处理，避免"官方 URL 却灰色不可用"
            ?: if (isOfficialDeepSeekEntry(entry)) QuiddityConstants.DEEPSEEK_RESPONSES_URL else null

    /**
     * 该条目是否支持 DeepSeek 官方服务端联网搜索（Responses API web_search）。
     *
     * 要求官方服务商（providerId=deepseek）+ 官方支持的模型（[RESPONSES_SUPPORTED_MODELS]）。
     */
    fun supportsServerWebSearch(entry: ApiCatalogEntry): Boolean =
        entry.apiModel in RESPONSES_SUPPORTED_MODELS &&
            isOfficialDeepSeekEntry(entry)

    /**
     * 判定条目是否为 DeepSeek 官方服务（服务商预设或 URL 指向官方端点）。
     * 用户用自定义条目填官方 URL 时同样视为官方服务，联网搜索等官方能力可用。
     */
    private fun isOfficialDeepSeekEntry(entry: ApiCatalogEntry): Boolean {
        if (entry.providerId == QuiddityConstants.DEEPSEEK_PROVIDER_ID) return true
        val url = entry.apiUrl.trim()
        return url.startsWith("https://api.deepseek.com")
    }

    /**
     * 已知模型的采样温度上限（未列出 = 不限制，按全局 2.0）。
     *
     * Anthropic Claude 官方 API 温度仅支持 0～1.0，超限请求会被服务端拒绝；
     * 选中这些预设模型时自动套用上限，用户也可在模型配置里手动覆盖。
     */
    private val knownTemperatureCaps: Map<String, Double> = mapOf(
        "claude-sonnet-4-6" to 1.0,
        "claude-sonnet-4-5" to 1.0,
        "claude-opus-4-8" to 1.0
    )

    /** 返回已知模型的温度上限；未知模型返回 null（按 2.0 不限制）。 */
    fun defaultMaxTemperature(apiModel: String): Double? = knownTemperatureCaps[apiModel]

    /**
     * 查询指定分级对应的默认上下文轮数。
     *
     * - 完全级：40 轮（[QuiddityConstants.TIER_FULL_CONTEXT_LIMIT]）
     * - 进阶级：20 轮（[QuiddityConstants.TIER_ADVANCED_CONTEXT_LIMIT]）
     * - 基础级：6 轮（[QuiddityConstants.TIER_BASIC_CONTEXT_LIMIT]）
     *
     * 模型切换时自动重置为此默认值，用户可手动覆盖。
     */
    fun defaultContextLimitForTier(tier: ModelTier): Int = when (tier) {
        ModelTier.FULL -> QuiddityConstants.TIER_FULL_CONTEXT_LIMIT
        ModelTier.ADVANCED -> QuiddityConstants.TIER_ADVANCED_CONTEXT_LIMIT
        ModelTier.BASIC -> QuiddityConstants.TIER_BASIC_CONTEXT_LIMIT
    }

    /**
     * 获取按分级归类的模型名单（用于《模型分配方案》弹窗展示）。
     */
    fun tieredModels(): Map<ModelTier, List<String>> =
        MODEL_TIER_MAP.entries
            .groupBy({ it.value }, { it.key })
            .mapValues { (_, models) -> models.sorted() }

    /**
     * 内置服务商预设（与桌面版 Quiddity-Chat settings.js AI_PROVIDERS 对齐）。
     *
     * 维护要点：
     * - 修改模型时尽量同步桌面版 settings.js，保持两端可互通
     * - 服务商顺序按中文首字母排序
     * - "自定义"始终位于末尾，作为兜底
     *
     * 模型 ID 核对原则（2026-07-23）：
     * - 仅保留官方文档/公告中明确列出、且高可信度为最新可用的模型 ID
     * - 已确认下线/弃用/无法核实的模型已移除
     * - 聚合平台（SiliconFlow）模型名采用其官方模型库 slug
     */
    val providers: List<Provider> = listOf(
        Provider(
            "alibaba", "阿里云（通义千问）\nAlibaba Qwen",
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            "https://dashscope.aliyun.com",
            listOf(
                "qwen3.7-max",
                "qwen3.7-plus",
                "qwen3.6-flash",
                "qwen3.6-35b-a3b",
                "qwen-plus",
                "qwen-flash",
                "qwen3-coder-plus"
            )
        ),
        Provider(
            "baidu", "百度（文心一言）\nBaidu ERNIE",
            "https://qianfan.baidubce.com/v2/chat/completions",
            "https://qianfan.cloud.baidu.com",
            listOf(
                "ernie-5.1",
                "ernie-5.0",
                "ernie-x1.1",
                "ernie-4.5-turbo-128k"
            )
        ),
        Provider(
            "siliconflow", "硅基流动（聚合平台）\nSiliconFlow",
            "https://api.siliconflow.cn/v1/chat/completions",
            "https://cloud.siliconflow.cn",
            listOf(
                "deepseek-ai/DeepSeek-V4-Pro",
                "deepseek-ai/DeepSeek-V4-Flash",
                "zai-org/GLM-5.2",
                "zai-org/GLM-5.1",
                "zai-org/GLM-5",
                "Qwen/Qwen3.5-397B-A17B",
                "Qwen/Qwen3.6-35B-A3B",
                "moonshotai/Kimi-K3",
                "moonshotai/Kimi-K2.6",
                "MiniMaxAI/MiniMax-M2.5",
                "tencent/Hy3",
                "ByteDance-Seed/Seed-OSS-36B-Instruct"
            )
        ),
        Provider(
            "stepfun", "阶跃星辰\nStepFun",
            "https://api.stepfun.com/v1/chat/completions",
            "https://platform.stepfun.com",
            listOf(
                "step-3.7-flash",
                "step-3.5-flash"
            )
        ),
        Provider(
            "iflytek", "科大讯飞（星火）\niFlytek Spark",
            "https://spark-api-open.xf-yun.com/v1/chat/completions",
            "https://xinghuo.xfyun.cn",
            listOf(
                "4.0Ultra",
                "spark-x",
                "generalv3.5",
                "pro-128k",
                "lite"
            )
        ),
        Provider(
            "minimax", "MiniMax（海螺AI）",
            "https://api.minimax.chat/v1/openai/chat/completions",
            "https://platform.minimaxi.com",
            listOf(
                "MiniMax-M3",
                "MiniMax-M2.7",
                "MiniMax-M2.7-highspeed",
                "MiniMax-M2.5"
            )
        ),
        Provider(
            "deepseek", "深度求索\nDeepSeek",
            "https://api.deepseek.com/v1/chat/completions",
            "https://platform.deepseek.com",
            responsesUrl = QuiddityConstants.DEEPSEEK_RESPONSES_URL,
            models = listOf(
                "deepseek-v4-flash",
                "deepseek-v4-pro"
            )
        ),
        Provider(
            "tencent", "腾讯（混元）\nTencent Hunyuan",
            "https://api.hunyuan.cloud.tencent.com/v1/chat/completions",
            "https://console.cloud.tencent.com/hunyuan",
            listOf(
                "hy3-preview",
                "hunyuan-role-latest"
            )
        ),
        Provider(
            "moonshot", "月之暗面\nMoonshot Kimi",
            "https://api.moonshot.cn/v1/chat/completions",
            "https://platform.moonshot.cn",
            listOf(
                "kimi-k3",
                "kimi-k2.6",
                "kimi-k2.7-code",
                "kimi-k2.7-code-highspeed"
            )
        ),
        Provider(
            "bytedance", "字节跳动（豆包）\nByteDance Doubao",
            "https://ark.cn-beijing.volces.com/api/v3/chat/completions",
            "https://console.volcengine.com/ark",
            listOf(
                "doubao-seed-evolving",
                "doubao-seed-2-1-pro-260628",
                "doubao-seed-2-1-turbo-260628",
                "doubao-seed-2-0-lite-260428",
                "doubao-seed-2-0-mini-260428",
                "doubao-seed-2-0-code-preview-260215"
            )
        ),
        Provider(
            "zhipu", "智谱\nZhipu GLM",
            "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            "https://open.bigmodel.cn",
            listOf(
                "glm-5.2",
                "glm-5.1",
                "glm-5",
                "glm-4-plus",
                "glm-4-air",
                "glm-4-flash"
            )
        ),
        Provider("custom", "自定义", "", "", emptyList())
    )

    /** 兜底自定义服务商。 */
    val customProvider: Provider get() = providers.last()

    fun findProvider(id: String?): Provider =
        providers.firstOrNull { it.id == id } ?: customProvider

    fun displayNameOf(providerId: String): String =
        providers.firstOrNull { it.id == providerId }?.name ?: "自定义"

    // ==================== 视觉 OCR 服务商预置（独立名册） ====================

    /**
     * 视觉 OCR 服务商预置数据（2026-08 核对各厂商官方文档）。
     *
     * 与聊天名册共用 [Provider] 结构：默认 URL 均为 OpenAI 兼容的
     * `/chat/completions` 接口，模型 ID 采用各平台官方文档中的实际模型名。
     *
     * 覆盖厂商：
     * - OpenAI（gpt-5.5 / gpt-5.4 / gpt-4o，全系支持视觉）
     * - Google Gemini（OpenAI 兼容端点，全系多模态）
     * - Anthropic Claude（OpenAI 兼容端点）
     * - 阿里云百炼（qwen-vl-max / qwen2.5-vl / qwen3.5-omni-plus）
     * - 智谱开放平台（glm-4.6v-flash / glm-4.1v-thinking-flash 免费 / glm-4.6v / glm-4v 系列）
     * - 月之暗面（kimi-k3 / kimi-k2.6 原生视觉，moonshot-v1 视觉预览版）
     * - 字节火山方舟（doubao-seed-1.6-vision / doubao-1.5 视觉系列）
     * - 百度千帆（ernie-4.5-turbo-vl 系列）
     * - MiniMax（M3 原生多模态）
     * - 硅基流动聚合平台（Qwen-VL / GLM-5V / DeepSeek-OCR / PaddleOCR-VL）
     */
    val visionProviders: List<Provider> = listOf(
        Provider(
            "openai", "OpenAI\nOpenAI",
            "https://api.openai.com/v1/chat/completions",
            "https://platform.openai.com/api-keys",
            listOf("gpt-5.5", "gpt-5.4", "gpt-4o")
        ),
        Provider(
            "google", "Google Gemini\nGoogle Gemini",
            "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
            "https://aistudio.google.com/apikey",
            listOf("gemini-3.5-flash", "gemini-2.5-flash", "gemini-2.5-pro")
        ),
        Provider(
            "anthropic", "Anthropic Claude\nAnthropic Claude",
            "https://api.anthropic.com/v1/chat/completions",
            "https://console.anthropic.com/settings/keys",
            listOf("claude-sonnet-4-6", "claude-sonnet-4-5", "claude-opus-4-8")
        ),
        Provider(
            "alibaba", "阿里云（通义千问 VL）\nAlibaba Qwen-VL",
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            "https://dashscope.aliyun.com",
            listOf(
                "qwen-vl-max",
                "qwen-vl-plus",
                "qwen3.5-omni-plus",
                "qwen2.5-vl-72b-instruct",
                "qwen2.5-vl-7b-instruct"
            )
        ),
        Provider(
            "zhipu", "智谱（GLM 视觉）\nZhipu GLM-Vision",
            "https://open.bigmodel.cn/api/paas/v4/chat/completions",
            "https://open.bigmodel.cn",
            listOf(
                "glm-4.1v-thinking-flash",
                "glm-4.6v-flash",
                "glm-4.6v",
                "glm-4v-plus",
                "glm-4v-flash"
            )
        ),
        Provider(
            "moonshot", "月之暗面（Kimi 视觉）\nMoonshot Kimi-Vision",
            "https://api.moonshot.cn/v1/chat/completions",
            "https://platform.moonshot.cn",
            listOf(
                "kimi-k3",
                "kimi-k2.6",
                "kimi-k2.5",
                "moonshot-v1-32k-vision-preview"
            )
        ),
        Provider(
            "bytedance", "字节跳动（豆包视觉）\nByteDance Doubao-Vision",
            "https://ark.cn-beijing.volces.com/api/v3/chat/completions",
            "https://console.volcengine.com/ark",
            listOf(
                "doubao-seed-1-6-vision-250815",
                "doubao-1.5-thinking-vision-pro",
                "doubao-1.5-vision-pro",
                "doubao-1.5-vision-lite"
            )
        ),
        Provider(
            "baidu", "百度（文心 ERNIE-VL）\nBaidu ERNIE-VL",
            "https://qianfan.baidubce.com/v2/chat/completions",
            "https://qianfan.cloud.baidu.com",
            listOf(
                "ernie-4.5-turbo-vl",
                "ernie-4.5-turbo-vl-32k"
            )
        ),
        Provider(
            "minimax", "MiniMax（M3 多模态）\nMiniMax Multimodal",
            "https://api.minimax.chat/v1/openai/chat/completions",
            "https://platform.minimaxi.com",
            listOf("MiniMax-M3")
        ),
        Provider(
            "siliconflow", "硅基流动（视觉/OCR）\nSiliconFlow Vision",
            "https://api.siliconflow.cn/v1/chat/completions",
            "https://cloud.siliconflow.cn",
            listOf(
                "Qwen/Qwen2.5-VL-72B-Instruct",
                "Qwen/Qwen2.5-VL-7B-Instruct",
                "Qwen/Qwen3-Omni-30B-A3B-Instruct",
                "zai-org/GLM-5V-Turbo",
                "deepseek-ai/DeepSeek-OCR",
                "PaddlePaddle/PaddleOCR-VL"
            )
        ),
        Provider("custom", "自定义", "", "", emptyList())
    )

    fun findVisionProvider(id: String?): Provider =
        visionProviders.firstOrNull { it.id == id } ?: customProvider

    fun visionDisplayNameOf(providerId: String): String =
        visionProviders.firstOrNull { it.id == providerId }?.name ?: "自定义"

    /**
     * 获取指定服务商的官方 API-KEY 控制台地址。
     * 自定义或未知服务商返回空字符串。
     */
    fun keyUrlFor(providerId: String): String =
        providers.firstOrNull { it.id == providerId }?.keyUrl ?: ""

    // ==================== ID 生成（统一格式） ====================

    /**
     * 生成一个新的名册条目 id。
     */
    fun generateId(): String = IdGenerator.newId(IdGenerator.Prefix.CATALOG_ENTRY)

    // ==================== Key 加解密 ====================

    /**
     * 加密 API Key（空字符串返回空字符串）。
     */
    fun encryptKey(plain: String): String =
        if (plain.isEmpty()) "" else CryptoUtils.encrypt(plain)

    /**
     * 解密 API Key。
     *
     * 空安全：密文为空或解不开（更换设备 / 重装 / 数据被篡改）时返回 null。
     * UI 层据此显示"已保存密钥"或"密钥不可用需重新输入"，不再抛异常。
     */
    fun decryptKey(entry: ApiCatalogEntry): String? =
        CryptoUtils.decryptOrNull(entry.apiKeyEnc)

    /** 该条目是否存有密钥（用于编辑时提示"已保存，可留空保持不变"）。 */
    fun hasStoredKey(entry: ApiCatalogEntry): Boolean = entry.apiKeyEnc.isNotEmpty()

    // ==================== 连接测试 ====================

    /**
     * 测试 API 连接是否可用。
     * 委托给 [ChatApi.testConnection]，封装为 Result 便于 UI 层错误处理。
     */
    suspend fun testConnection(
        apiUrl: String,
        apiKey: String,
        model: String
    ): Result<String> = chatApi.testConnection(apiUrl, apiKey, model)

    // ==================== 条目构造工厂 ====================

    /**
     * 从字段构造一个 [ApiCatalogEntry]。
     * - [id] 为 null 时自动生成；为非空时直接使用（用于更新现有条目）。
     * - [apiKey] 通过 [encryptKey] 统一加密。
     */
    fun buildEntry(
        id: String?,
        name: String,
        providerId: String,
        apiUrl: String,
        apiModel: String,
        apiKey: String,
        maxTemperature: Double? = null
    ): ApiCatalogEntry = ApiCatalogEntry(
        // 关键修复：表单新增时 id 传空字符串 ""（ApiCatalogEditFormState 语义：空 = 新增）。
        // 空串不能当真实 id 用，否则多个新条目 id 相同会互相覆盖（"密钥不保存"根因）
        id = id?.takeIf { it.isNotBlank() } ?: generateId(),
        name = name,
        providerId = providerId,
        apiUrl = apiUrl,
        apiModel = apiModel,
        apiKeyEnc = encryptKey(apiKey),
        // 用户未填写时按已知模型默认上限套用（如 Claude 1.0）
        maxTemperature = maxTemperature ?: defaultMaxTemperature(apiModel)
    )
}
