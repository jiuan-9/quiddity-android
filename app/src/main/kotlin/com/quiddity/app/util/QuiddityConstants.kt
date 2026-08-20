package com.quiddity.app.util

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
 * 项目级魔法值常量。
 *
 * ## 命名规范
 *
 * - `DEFAULT_*`：默认值（用于初始化 / 兜底）
 * - `MIN_*` / `MAX_*`：用户输入的合法范围边界
 * - `TIMEOUT_*`：网络超时
 * - `LIMIT_*`：限制类
 */
object QuiddityConstants {

    // ===== Token 限制 =====
    /** 单次回复最大 Token 上下界。 */
    const val MIN_MAX_TOKENS = 64
    const val MAX_MAX_TOKENS = 32_000
    const val DEFAULT_MAX_TOKENS = 4_096

    // ===== 采样温度 =====
    /** 采样温度上下界（DeepSeek 官方文档：0～2，默认 1.0）。 */
    const val MIN_TEMPERATURE = 0.0
    const val MAX_TEMPERATURE = 2.0
    /** 采样温度默认值（DeepSeek 官方默认 1.0；聊天场景官方建议 1.3，由用户自主调节）。 */
    const val DEFAULT_TEMPERATURE = 1.0

    /**
     * 采样温度钳制：把 [value] 限制在模型支持的范围内。
     * 部分模型最高只支持 1.0（如 Claude 系），超限请求会被服务端拒绝；
     * [max] 由模型配置条目的 [com.quiddity.app.data.model.ApiCatalogEntry.maxTemperature] 提供，
     * 未配置时按全局上限 2.0。
     */
    fun clampTemperature(value: Double, max: Double = MAX_TEMPERATURE): Double =
        value.coerceIn(MIN_TEMPERATURE, max)

    // ===== DeepSeek 官方服务端联网搜索（Responses API） =====
    /** DeepSeek 官方服务商 id（Provider 预设）。 */
    const val DEEPSEEK_PROVIDER_ID = "deepseek"
    /** Responses API 目前唯一支持的模型（官方文档：暂不支持 deepseek-v4-pro）。 */
    const val DEEPSEEK_RESPONSES_MODEL = "deepseek-v4-flash"
    /** DeepSeek 官方 Responses API 端点（服务端执行 web_search）。 */
    const val DEEPSEEK_RESPONSES_URL = "https://api.deepseek.com/responses"

    // ===== api-key 认证端点（自定义条目兜底） =====
    /** 使用 api-key 认证头的官方端点主机（如小米 MiMo；内置名册已移除，自定义条目仍可用）。 */
    const val XIAOMI_MIMO_API_HOST = "api.xiaomimimo.com"

    /** 判定 URL 是否指向小米 MiMo 官方端点（自定义条目填官方 URL 时同样生效）。 */
    fun isXiaomiMimoUrl(url: String): Boolean = url.contains(XIAOMI_MIMO_API_HOST)

    /** 思考深度：浅（默认，reasoning_effort=low）。 */
    const val THINKING_DEPTH_SHALLOW = "SHALLOW"
    /** 思考深度：深（reasoning_effort=high）。 */
    const val THINKING_DEPTH_DEEP = "DEEP"
    /** 思考深度 → API reasoning_effort（浅=low，深=high；null = 不携带，模型默认）。 */
    fun reasoningEffortForDepth(depth: String?): String? = when (depth) {
        THINKING_DEPTH_DEEP -> "high"
        THINKING_DEPTH_SHALLOW -> "low"
        else -> null
    }

    /** 单条消息 Token 上下界。 */
    const val MIN_SINGLE_MESSAGE_TOKENS = 32
    const val MAX_SINGLE_MESSAGE_TOKENS = 8_000
    const val DEFAULT_SINGLE_MESSAGE_TOKENS = 800

    /** 上下文记忆条数上下界。 */
    const val MIN_CONTEXT_LIMIT = 1
    const val MAX_CONTEXT_LIMIT = 200
    const val DEFAULT_CONTEXT_LIMIT = 20

    // ===== 消息预览 =====
    /** 会话列表中显示的最新消息预览最大字符数。 */
    const val MESSAGE_PREVIEW_MAX_CHARS = 60

    // ===== 壁纸 =====
    /** 壁纸暗化默认值（0.0f - 1.0f）。0 = 原始亮度，无默认暗化。 */
    const val DEFAULT_WALLPAPER_DARKEN = 0.0f
    const val MIN_WALLPAPER_DARKEN = 0.0f
    const val MAX_WALLPAPER_DARKEN = 1.0f

    // ===== API 编译 =====
    /** 人设精调编译时的 max_tokens 上限。 */
    const val PERSONA_COMPILE_MAX_TOKENS = 2_000
    const val PERSONA_COMPILE_TEMPERATURE = 0.7

    // ===== 记忆压缩 =====
    /** 记忆压缩时的 max_tokens 上限（摘要输出足够紧凑，2000 token 足够）。 */
    const val COMPRESSION_MAX_TOKENS = 2_000
    /** 记忆压缩采样温度：低温保证忠实提取，抑制发挥与臆造。 */
    const val COMPRESSION_TEMPERATURE = 0.3

    // ===== 记忆调用式（2.0.0 预留；1.3.0 仅接口与字段） =====
    /** 记忆策略：强制随身带（每轮把两份记忆全文塞进系统提示词）。 */
    const val MEMORY_STRATEGY_CARRY = "CARRY"
    /** 记忆策略：强制工具模式（小抄 + read_memory 抽屉按需读取）。 */
    const val MEMORY_STRATEGY_TOOL = "TOOL"
    /** 小抄索引行最大长度（字符，6.5.1）。 */
    const val MEMORY_INDEX_MAX_CHARS = 60
    /** 索引回退长度：无 memoryIndex 时用压缩摘要前 80 字临时顶替（6.5.2 回退规则）。 */
    const val MEMORY_INDEX_FALLBACK_CHARS = 80
    /** 抽屉内容回填预算上限（token，对齐压缩上限，6.6.4）。 */
    const val MEMORY_DRAWER_BUDGET_TOKENS = 2_000
    /** 群聊小本本压缩触发阈值（条数；群聊「轮」= 一条消息，6.7）。 */
    const val GROUP_MEMORY_THRESHOLD = 40
    /** 群聊小本本体积上限（token，6.7）。 */
    const val GROUP_MEMORY_MAX_TOKENS = 1_000
    /** 群聊快速判断 max_tokens 上限（5.2：只输出「0 / 要说的内容」）。 */
    const val GROUP_DECIDE_MAX_TOKENS = 16
    // ===== 群聊 =====
    /** 群聊上下文条数默认值（方案六.2：最近 N 条，默认 50）。 */
    const val GROUP_DEFAULT_CONTEXT_LIMIT = 50
    /** 群聊上下文条数下界。 */
    const val GROUP_MIN_CONTEXT_LIMIT = 1
    /** 群聊上下文条数上界。 */
    const val GROUP_MAX_CONTEXT_LIMIT = 200
    /** 群聊 AI 成员数量上限（方案二.2：最多 3 个 LLM 成员）。 */
    const val GROUP_MAX_MEMBERS = 3
    /** 成员回复失败重试次数（方案十三.1：重试 5 次）。 */
    const val GROUP_RETRY_COUNT = 5
    /** 停止模式 A：只停止当前正在回复的成员（方案四.7）。 */
    const val GROUP_STOP_MODE_A = "A"
    /** 停止模式 B：停止时同时清空整个队列（默认，方案四.7）。 */
    const val GROUP_STOP_MODE_B = "B"
    const val GROUP_DEFAULT_STOP_MODE = GROUP_STOP_MODE_B
    /** 群聊背景/场景合并设置的模式：背景（氛围描述）。 */
    const val GROUP_BACKGROUND_MODE_BACKGROUND = "BACKGROUND"
    /** 群聊背景/场景合并设置的模式：场景（多人情境）。 */
    const val GROUP_BACKGROUND_MODE_SCENE = "SCENE"
    /** 群聊背景/场景的默认模式：背景。 */
    const val GROUP_DEFAULT_BACKGROUND_MODE = GROUP_BACKGROUND_MODE_BACKGROUND
    /** 私聊默认名前缀（新会话 1、2、3…，方案二.4）。 */
    const val SOLO_DEFAULT_TITLE_PREFIX = "新会话"
    /** 群聊默认名前缀（新群聊 1、2、3…，方案二.4）。 */
    const val GROUP_DEFAULT_TITLE_PREFIX = "新群聊"

    // ===== 快速设定 =====
    /** 快速设定的 max_tokens 上限（全面档 5000 汉字，按 ~1.6 token/字 预留余量）。 */
    const val QUICK_SETUP_MAX_TOKENS = 8_000
    /** 快速设定采样温度：略低于精调，兼顾详尽表达与忠实不臆造。 */
    const val QUICK_SETUP_TEMPERATURE = 0.6
    /** 快速设定默认采样温度：偏高保证每次生成的人设发散度足够，避免千篇一律（用户可在面板内调整）。 */
    const val DEFAULT_QUICK_SETUP_TEMPERATURE = 1.2

    // ===== 网络超时 =====
    const val CONNECT_TIMEOUT_SECONDS = 30L
    const val READ_TIMEOUT_SECONDS = 120L
    const val WRITE_TIMEOUT_SECONDS = 30L

    // ===== 人设精调 =====
    /** 期望特质（desired）字段是最高优先级——人设编译时优先处理。 */
    const val PERSONA_DESIRED_PRIORITY = "highest"

    // ===== 默认会话标题 =====
    const val DEFAULT_CONVERSATION_TITLE = "新会话"

    // ===== 流式协调器 =====
    /** 协调器硬上限：单条消息 buffer 字符数达到 token 阈值 × 此系数即强制切分。 */
    const val SPLITTER_HARD_LIMIT_MULTIPLIER = 2L
    /** 估算每 Token 字符数（粗估）。 */
    const val SPLITTER_CHARS_PER_TOKEN = 4L
    /** 绝对硬上限：单条消息 buffer 字符数（防极端配置组合 OOM）。 */
    const val SPLITTER_MAX_HARD_LIMIT_CHARS = 1_000_000

    // ===== 默认 AI 人设 =====
    /**
     * 默认 AI 的身份背景预填值。
     *
     * - 新会话创建时，AI 人设的 `persona`（身份背景）字段预填本常量。
     * - 其他字段（名字 / 性格 / 外观 / 世界背景 / 期望）全部留空，
     *   输入框显示灰色占位提示引导用户设定。
     * - 名字留空让用户自行取名，避免强制赋予 AI 一个用户可能不想要的名字。
     */
    const val DEFAULT_AI_IDENTITY = "用户的AI助手"

    // ===== 上下文记忆轮数 =====
    /** 完全级（FULL）默认上下文记忆轮数（默认压缩轮数同此值）。 */
    const val TIER_FULL_CONTEXT_LIMIT = 40
    /** 进阶级（ADVANCED）默认上下文记忆轮数（默认压缩轮数同此值）。 */
    const val TIER_ADVANCED_CONTEXT_LIMIT = 20
    /** 基础级（BASIC）默认上下文记忆轮数（默认压缩轮数同此值）。 */
    const val TIER_BASIC_CONTEXT_LIMIT = 6

    // ===== 延迟输出（打字机效果） =====
    /** 延迟输出默认开启。 */
    const val DEFAULT_TYPING_DELAY_ENABLED = true
    /** 每个字符延迟毫秒数（营造真人打字感）。 */
    const val DEFAULT_TYPING_DELAY_MS_PER_CHAR = 20
    const val MIN_TYPING_DELAY_MS_PER_CHAR = 0
    const val MAX_TYPING_DELAY_MS_PER_CHAR = 200

    // ===== 发送延迟 =====
    /** 发送延迟默认开启。 */
    const val DEFAULT_SEND_DELAY_ENABLED = true
    /** 发送延迟默认秒数。 */
    const val DEFAULT_SEND_DELAY_SECONDS = 2
    /** 最小 0 秒 = 关闭发送延迟。 */
    const val MIN_SEND_DELAY_SECONDS = 0
    const val MAX_SEND_DELAY_SECONDS = 30

    // ===== 记忆库 =====
    /** 记忆库默认开启。 */
    const val DEFAULT_MEMORY_BANK_ENABLED = true
    /** 记忆库压缩触发轮数（用户可配，默认与上下文记忆轮数一致）。 */
    const val DEFAULT_MEMORY_BANK_ROUNDS = DEFAULT_CONTEXT_LIMIT
    const val MIN_MEMORY_BANK_ROUNDS = 5
    const val MAX_MEMORY_BANK_ROUNDS = 200

    // ===== 字体大小 =====
    /** 应用内字体缩放默认值（1.0 = 设计稿原尺寸，强制默认字体不受系统字号影响）。 */
    const val DEFAULT_FONT_SCALE = 1.0f
    /** 应用内字体缩放下界（小字号）。 */
    const val MIN_FONT_SCALE = 0.8f
    /** 应用内字体缩放上界（大字号）。 */
    const val MAX_FONT_SCALE = 1.4f

    // ===== 主动消息（时间库） =====
    /** 时间库最多时间点数。 */
    const val ACTIVE_MESSAGE_MAX_POINTS = 5
    /** 触发延迟补偿宽容窗口（分钟）：差值 ≤ 5 分钟补发，> 5 分钟放弃。 */
    const val ACTIVE_MESSAGE_LATE_WINDOW_MINUTES = 5
    /** 网络/API 异常时重试 1 次的延迟（毫秒）。 */
    const val ACTIVE_MESSAGE_RETRY_DELAY_MS = 5_000L
    /** App 启动/开机后批量补齐时间库前的延迟（毫秒）：等待网络就绪，避免启动即失败。 */
    const val ACTIVE_MESSAGE_STARTUP_DELAY_MS = 8_000L
    /** 时间库生成 max_tokens 上限（仅时间列表，输出紧凑）。 */
    const val ACTIVE_MESSAGE_GENERATE_MAX_TOKENS = 800
    /** 发送决策 max_tokens 上限（决策输出为实际消息内容，需足够长）。 */
    const val ACTIVE_MESSAGE_DECIDE_MAX_TOKENS = 1_000
    /** 时间库生成采样温度：偏高鼓励 LLM 自主把握时间点。 */
    const val ACTIVE_MESSAGE_GENERATE_TEMPERATURE = 0.7
    /** 发送决策采样温度。 */
    const val ACTIVE_MESSAGE_DECIDE_TEMPERATURE = 0.8
}
