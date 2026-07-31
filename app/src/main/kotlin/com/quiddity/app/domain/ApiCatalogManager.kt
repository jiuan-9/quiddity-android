package com.quiddity.app.domain

import com.quiddity.app.data.model.ApiCatalogEntry
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
        val models: List<String>
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
     * 查询指定分级对应的默认上下文轮数。
     *
     * - 完全级：80 轮（[QuiddityConstants.TIER_FULL_CONTEXT_LIMIT]）
     * - 进阶级：40 轮（[QuiddityConstants.TIER_ADVANCED_CONTEXT_LIMIT]）
     * - 基础级：12 轮（[QuiddityConstants.TIER_BASIC_CONTEXT_LIMIT]）
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
            listOf(
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
     * 空字符串快捷路径：[apiKeyEnc] 为空时直接返回空串，不抛 [DecryptFailure.Empty]。
     * 调用方无需自行判空。
     */
    fun decryptKey(entry: ApiCatalogEntry): String {
        if (entry.apiKeyEnc.isEmpty()) return ""
        return CryptoUtils.decrypt(entry.apiKeyEnc)
    }

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
        apiKey: String
    ): ApiCatalogEntry = ApiCatalogEntry(
        id = id ?: generateId(),
        name = name,
        providerId = providerId,
        apiUrl = apiUrl,
        apiModel = apiModel,
        apiKeyEnc = encryptKey(apiKey)
    )
}
