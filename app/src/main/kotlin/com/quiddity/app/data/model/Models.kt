package com.quiddity.app.data.model

import androidx.compose.runtime.Immutable
import com.quiddity.app.util.QuiddityConstants
import kotlinx.serialization.Serializable

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
 * 消息角色。
 */
@Serializable
enum class Role {
    SYSTEM, USER, ASSISTANT
}

/**
 * 会话类型（2.0.0 schema v2 预留字段）。
 *
 * - SOLO = 私聊（默认值，兼容旧数据）
 * - GROUP = 群聊（1.3.0 仅提供接口与字段，群聊实体暂不加入）
 */
@Serializable
enum class ConversationType {
    SOLO, GROUP
}

/**
 * 时间库时间点状态。
 * 仅两种状态：pending（待触发）/ done（已处理）。
 */
@Serializable
enum class TimePointStatus {
    PENDING, DONE
}

/**
 * 时间库时间点。
 * [time] 为 24 小时制 "HH:mm" 字符串，精确到分钟。
 */
@Immutable
@Serializable
data class TimePoint(
    val time: String,
    val status: TimePointStatus = TimePointStatus.PENDING
) {
    val isPending: Boolean get() = status == TimePointStatus.PENDING
    fun copyAsDone(): TimePoint = copy(status = TimePointStatus.DONE)
    fun copyAsPending(): TimePoint = copy(status = TimePointStatus.PENDING)
}

/**
 * AI 人设。
 * [compiledPersona] 为人设编译后的精调结果缓存，若为空则使用原始字段拼接。
 */
@Immutable
@Serializable
data class Persona(
    val name: String = "",
    val desired: String = "",          // "希望 AI 是什么样的"——核心参考
    val persona: String = "",          // 身份背景
    val character: String = "",        // 性格
    val appearance: String = "",       // 外观
    val worldBackground: String = "",  // 世界背景
    val compiledPersona: String? = null,
    val aiAvatarUri: String? = null    // AI 头像（每个会话独立）
) {
    companion object {
        val Empty = Persona()
    }
}

/**
 * 用户人设。
 */
@Immutable
@Serializable
data class UserPersona(
    val name: String = "",
    val identity: String = "",
    val gender: String = "",
    val age: String = "",
    val appearance: String = ""
) {
    companion object {
        val Empty = UserPersona()
    }
}

/**
 * 单条 API 名册条目。
 */
@Immutable
@Serializable
data class ApiCatalogEntry(
    val id: String,
    val name: String,
    val providerId: String = "",
    val apiUrl: String,
    val apiModel: String,
    val apiKeyEnc: String
)

/**
 * 对话实体。每个会话独立人设、用户设定、场景、记忆、API 配置。
 *
 * 壁纸字段：null = 不使用壁纸（应用默认背景），非空 = 持久化到 Conversation，渲染时叠加在消息列表背景。
 */
@Immutable
@Serializable
data class Conversation(
    val id: String,
    val title: String = QuiddityConstants.DEFAULT_CONVERSATION_TITLE,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
    val persona: Persona = Persona.Empty,
    val userPersona: UserPersona = UserPersona.Empty,
    val scene: String = "",
    /**
     * 场景是否已注入过 LLM。
     * - 场景仅在首轮拼入系统提示词发给 LLM，之后靠对话上文延续，避免反复重发静态场景导致场景崩塌。
     * - false = 尚未注入，下次对话会把场景拼入系统提示词。
     * - true = 已注入，后续不再拼入；场景被修改时重置为 false，下次对话重新注入一次。
     */
    val sceneInjected: Boolean = false,
    val memory: String = "",
    val apiCatalogId: String? = null,
    val maxTokens: Int? = null,
    val singleMessageTokens: Int? = null,
    val contextLimit: Int = QuiddityConstants.DEFAULT_CONTEXT_LIMIT,
    val compileEnabled: Boolean = false,
    /**
     * 会话专属壁纸 URI（每个会话独立设置）。
     * - null = 不使用
     * - 持久化路径：直接存储 SAF 返回的 content URI 字符串
     * - 加载策略：Coil 在 LazyColumn 背景层异步解码
     */
    val wallpaperUri: String? = null,
    /**
     * 壁纸暗化程度 0.0f - 1.0f（默认 [QuiddityConstants.DEFAULT_WALLPAPER_DARKEN]，确保文字可读）。
     * 用户在设置壁纸时可调整，避免深色壁纸让文字难以辨识。
     */
    val wallpaperDarken: Float = QuiddityConstants.DEFAULT_WALLPAPER_DARKEN,
    /**
     * 最新一条消息的预览文本（用于会话列表展示，避免列表页加载完整消息）。
     * - 每次追加 / 更新 / 替换消息时由 [com.quiddity.app.data.local.ConversationStore] 同步更新
     * - 空字符串表示尚无消息
     */
    val lastMessagePreview: String = "",
    /**
     * - 仅统计当前 API 的用量，切换 API（[apiCatalogId] 变更）时清零
     * - 持久化在会话中，重启后保留
     * - UI 展示在 Token 统计面板
     */
    val sessionTokenUsed: Int = 0,
    /**
     * - 用于检测 API 是否切换：若 apiCatalogId != tokenCountApiId 则清零
     * - null 表示尚未产生 Token 用量
     */
    val tokenCountApiId: String? = null,
    /**
     * - 用于检测模型是否切换：若当前 apiModel != lastUsedModel 则重置 contextLimit 为分级默认值
     * - null 表示首次使用，初始化时不触发重置
     */
    val lastUsedModel: String? = null,
    /**
     * - true = 达到 [memoryBankRounds] 轮时自动压缩历史对话
     * - false = 不压缩，全部历史消息发送给 API
     * - 默认 [QuiddityConstants.DEFAULT_MEMORY_BANK_ENABLED]
     */
    val memoryBankEnabled: Boolean = QuiddityConstants.DEFAULT_MEMORY_BANK_ENABLED,
    /**
     * - 达到此轮数时触发压缩
     * - 默认 [QuiddityConstants.DEFAULT_MEMORY_BANK_ROUNDS]
     * - 用户可配，范围 [QuiddityConstants.MIN_MEMORY_BANK_ROUNDS] - [QuiddityConstants.MAX_MEMORY_BANK_ROUNDS]
     */
    val memoryBankRounds: Int = QuiddityConstants.DEFAULT_MEMORY_BANK_ROUNDS,
    /**
     * - 每次压缩后更新，包含上一次压缩的信息 + 新一轮压缩的内容
     * - 保留关键词信息，删除修饰词和客套话
     * - 发送给 API 时替代原始历史消息，节省 Token
     * - 空字符串表示尚未压缩
     */
    val compressedMemory: String = "",
    /**
     * - 用于判断何时触发下一次压缩
     * - 0 表示从未压缩
     */
    val lastCompressedAtRound: Int = 0,
    /**
     * 会话级"时间库主动消息"开关（按会话独立，非全局功能）。
     * - true = 开启：当天首次打开该会话时生成时间库，到达 pending 时间点由 LLM 自主决策是否主动发消息
     * - false = 关闭：不生成、不注册任何定时任务，完全静默
     * - 开启后立即触发第一次时间库生成
     */
    val activeMessageEnabled: Boolean = false,
    /**
     * 该会话的时间库（[{time, status}] 结构，24 小时制精确到分钟）。
     * - 最多 5 个时间点，可为空
     * - 每次生成新库时直接覆盖旧库；空结果 / 生成失败时沿用旧库
     */
    val timeLibrary: List<TimePoint> = emptyList(),
    /**
     * 时间库最近一次生成的日期（yyyy-MM-dd）。
     * - 用于"每天仅当天首次打开该会话时触发生成"的判定
     * - 空字符串表示从未生成过
     */
    val timeLibraryGeneratedDate: String = "",
    /**
     * 时间库查看密码（由 AI 在生成时间库时制定，纯数字）。
     * 空字符串表示未设置密码（旧数据可直接查看）。
     * 一旦生成即固定不变，不要求唯一。
     */
    val timeLibraryPassword: String = "",
    /**
     * AI 是否决定把查看密码告知用户。
     * true = 在时间库状态卡上显示；false = 不显示，需向 AI 询问。
     */
    val timeLibraryPasswordRevealed: Boolean = false,
    /**
     * 用户是否成功打开过"查看时间库"。
     * true 后，输密码弹窗会直接显示密码，无需再次输入。
     */
    val timeLibraryPasswordUnlocked: Boolean = false,
    /**
     * 会话类型：SOLO=私聊（默认）/ GROUP=群聊。
     * 1.3.0 仅预留字段与接口，群聊实体不加入。
     */
    val type: ConversationType = ConversationType.SOLO,
    /**
     * 群聊成员会话 id 列表（仅 type=GROUP 使用；1.3.0 仅预留字段）。
     */
    val memberConversationIds: List<String> = emptyList(),
    /**
     * 角色库引用（2.0.0 新建数据填写）。
     * 读取时 resolveCharacter(characterId) 返回角色档案，
     * [persona] 字段作为解析后的缓存副本（代码兼容，不删除）。
     */
    val characterId: String? = null,
    /**
     * 压缩摘要的一行索引（6.5.2 两段式压缩产出，压缩时随摘要一起生成）。
     * 空字符串表示尚未生成。
     */
    val memoryIndex: String = "",
    /**
     * 记忆策略（6.3）：
     * - null = 跟随模型分级默认策略
     * - [com.quiddity.app.util.QuiddityConstants.MEMORY_STRATEGY_CARRY] = 强制随身带
     * - [com.quiddity.app.util.QuiddityConstants.MEMORY_STRATEGY_TOOL] = 强制工具模式（read_memory）
     */
    val memoryStrategy: String? = null,
    /**
     * 群聊小本本（成员视角，6.7；1.3.0 仅预留字段）。
     */
    val groupMemory: String = ""
)

/**
 * 单条消息。
 */
@Immutable
@Serializable
data class Message(
    val id: String,
    val conversationId: String,
    val role: Role,
    val content: String,
    val timestamp: Long,
    val tokenCount: Int = 0,
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
    /**
     * 是否为居中灰色提示气泡（UI 专用，如快速设定后的场景/世界背景提示）。
     * - true = 渲染为居中灰色透明气泡，不显示头像、不可撤回/改写。
     * - 不发送给 LLM、不参与压缩、不导出（各处已过滤）。
     */
    val isNotice: Boolean = false,
    /**
     * 发言人会话 id（2.0.0 群聊消息使用）。
     * - 群聊消息带 senderId（指向成员私聊会话 id）
     * - 私聊消息为 null（默认值，兼容旧数据）
     */
    val senderId: String? = null
)

/**
 * 全局应用设置。
 *
 * 括号灰化：将对话内容中成对括号 `()` `（）` `[]` `【】` 内的文本显示为灰色，
 * 模拟剧本/小说的"旁白 / 内心独白"视觉语言。开关位于"显示"section。
 *
 * - darkMode = true（暗色/灰色主题为默认，与用户偏好一致）
 * - bracketGrayEnabled = true（括号灰化默认开启，营造剧本式视觉层次）
 *
 * 会话列表界面壁纸（全局设置，非会话级）：
 * - 在总设置中配置，应用于 HomeScreen 的背景
 * - 会话条目以毛玻璃质感叠加在壁纸上
 * - 壁纸文件持久化到 filesDir/list_wallpapers/，可通过数据导出/导入迁移
 */
@Immutable
@Serializable
data class AppSettings(
    val darkMode: Boolean = true,
    val userAvatarUri: String? = null,
    val globalMaxTokens: Int = QuiddityConstants.DEFAULT_MAX_TOKENS,
    val globalSingleMessageTokens: Int = QuiddityConstants.DEFAULT_SINGLE_MESSAGE_TOKENS,
    val globalContextLimit: Int = QuiddityConstants.DEFAULT_CONTEXT_LIMIT,
    /**
     * AI 回复多消息切分（UI 叫法"AI 回复切分"）：
     * - true = AI 输出按句末标点 / 括号切成多条消息（像人一样分多条发送）
     * - false = 整条回复作为单条消息
     */
    val multilineAutoSplit: Boolean = true,
    val enterToSend: Boolean = true,
    val activeCatalogId: String? = null,
    val catalog: List<ApiCatalogEntry> = emptyList(),
    /**
     * 括号内容灰化开关。
     * 开启后，AI / 用户消息中成对括号内的文本以 `onSurfaceVariant.copy(alpha = 0.55f)` 颜色显示。
     * 默认 true（开启，营造剧本式旁白视觉）。
     */
    val bracketGrayEnabled: Boolean = true,
    /**
     * 会话列表界面壁纸 URI（全局设置）。
     * - null = 不使用壁纸（应用默认背景）
     * - 非空 = file:// URI 指向 filesDir/list_wallpapers/ 下的持久化文件
     * - 设置入口在总设置 → 显示 → 会话列表壁纸
     * - 导出/导入时由 DataPorter 处理（与对话级壁纸同源方案）
     */
    val listWallpaperUri: String? = null,
    /**
     * 会话列表界面壁纸暗化程度 0.0f - 1.0f。
     * 数值越大壁纸越暗，文字可读性越好。
     */
    val listWallpaperDarken: Float = QuiddityConstants.DEFAULT_WALLPAPER_DARKEN,
    /**
     * - true = 根据 AI 输出字数延迟显示，营造真人打字感
     * - 每个字符延迟 [typingDelayMsPerChar] 毫秒
     * - 默认 [QuiddityConstants.DEFAULT_TYPING_DELAY_ENABLED]
     */
    val typingDelayEnabled: Boolean = QuiddityConstants.DEFAULT_TYPING_DELAY_ENABLED,
    /**
     * - 范围 [QuiddityConstants.MIN_TYPING_DELAY_MS_PER_CHAR] - [QuiddityConstants.MAX_TYPING_DELAY_MS_PER_CHAR]
     * - 默认 [QuiddityConstants.DEFAULT_TYPING_DELAY_MS_PER_CHAR]（20ms）
     */
    val typingDelayMsPerChar: Int = QuiddityConstants.DEFAULT_TYPING_DELAY_MS_PER_CHAR,
    /**
     * - true = 发送消息后等待 [sendDelaySeconds] 秒再发出 API 请求
     * - 若等待期间输入框仍不为空，暂停请求直到输入框清空
     * - 可有效节省 Token 消耗（用户连续输入时合并请求）
     * - 默认 [QuiddityConstants.DEFAULT_SEND_DELAY_ENABLED]
     */
    val sendDelayEnabled: Boolean = QuiddityConstants.DEFAULT_SEND_DELAY_ENABLED,
    /**
     * - 范围 [QuiddityConstants.MIN_SEND_DELAY_SECONDS] - [QuiddityConstants.MAX_SEND_DELAY_SECONDS]
     * - 默认 [QuiddityConstants.DEFAULT_SEND_DELAY_SECONDS]（3秒）
     */
    val sendDelaySeconds: Int = QuiddityConstants.DEFAULT_SEND_DELAY_SECONDS,
    /**
     * 是否跟随系统字体大小。
     * - true = 使用系统字号（用户在系统设置里调整的 fontScale）
     * - false = 强制应用内字号（[fontScale]），不受系统字号影响（默认）
     * - 默认 false：与"整体强制默认字体"约束一致
     */
    val followSystemFont: Boolean = false,
    /**
     * 应用内字体缩放系数（仅在 [followSystemFont]=false 时生效）。
     * - 1.0 = 设计稿原尺寸
     * - 范围 [QuiddityConstants.MIN_FONT_SCALE] - [QuiddityConstants.MAX_FONT_SCALE]
     * - 默认 [QuiddityConstants.DEFAULT_FONT_SCALE]
     */
    val fontScale: Float = QuiddityConstants.DEFAULT_FONT_SCALE,
    /**
     * 主动消息总设置开关。
     * - 仅表示用户已了解该功能（开启时弹窗提示电池优化 / 自启动建议）
     * - 不直接启停任何会话的时间库功能；需在对应会话中单独开启"时间库主动消息"
     */
    val proactiveMessageEnabled: Boolean = false,
    /**
     * 主动消息状态重置日期（yyyy-MM-dd）。
     * - 每日首次启动 App 时检查当前日期与上次重置日期是否不同
     * - 若不同，将所有会话时间库的 done 重置为 pending
     * - 空字符串表示从未重置过
     */
    val proactiveMessageLastResetDate: String = ""
) {
    companion object {
        val Default = AppSettings()
    }
}

/**
 * 壁纸数据（导出/导入用）。
 *
 * 导出时读取壁纸图片文件，Base64 编码后嵌入 JSON——确保导入后壁纸可恢复。
 */
@Serializable
data class WallpaperData(
    val base64: String,       // Base64 编码的图片二进制数据
    val darken: Float         // 壁纸暗化程度 0.0f - 1.0f
)

/**
 * 头像数据（导出/导入用）。
 *
 * 与 [WallpaperData] 同源方案：导出时读取头像图片文件 Base64 编码，
 * 导入时写回 filesDir/avatars/ 并更新 URI 为 FileProvider content:// URI。
 */
@Serializable
data class AvatarData(
    val base64: String        // Base64 编码的图片二进制数据
)

/**
 * 全量数据导出根结构。
 *
 * [wallpapers] 存储每个会话的壁纸图片 Base64 数据，确保导入后壁纸可恢复。
 * 导入时由 DataPorter 写回本地文件，更新 Conversation.wallpaperUri 为 file:// URI。
 *
 * - 与 [wallpapers]（会话级）独立存储，因列表壁纸在 AppSettings 而非 Conversation
 * - 导入时由 DataPorter 写回 filesDir/list_wallpapers/，更新 settings.listWallpaperUri
 *
 * 头像数据：
 * - [userAvatar]：全局用户头像（AppSettings.userAvatarUri 对应的图片文件）
 * - [aiAvatars]：每个会话的 AI 头像，key 为会话 ID（Persona.aiAvatarUri 对应的图片文件）
 * - 导入时写回 filesDir/avatars/，更新对应 URI 为 FileProvider content:// URI
 */
@Serializable
data class ExportPayload(
    val schemaVersion: Int = SCHEMA_VERSION_1,
    val exportedAt: Long,
    /**
     * 导出时应用版本号（schema v2 字段；v1 文件为空字符串）。
     */
    val appVersion: String = "",
    val settings: AppSettings,
    val conversations: List<Conversation>,
    val messages: Map<String, List<Message>>,
    /**
     * 角色库主档（schema v2）：身份类数据（persona / userPersona / 固定记忆 / 头像）。
     */
    val characters: List<Character> = emptyList(),
    /**
     * 私聊列表（schema v2）：conversation 不再内嵌人设（只保留 characterId 引用），
     * 但 persona 字段作为解析后的缓存副本保留。
     */
    val privateChats: List<ConversationBundle> = emptyList(),
    /**
     * 群聊列表（schema v2）：memberConversationIds 引用私聊会话 id，消息带 senderId。
     * 1.3.0 仅提供接口，群聊实体不加入。
     */
    val groupChats: List<ConversationBundle> = emptyList(),
    val wallpapers: Map<String, WallpaperData> = emptyMap(),
    val listWallpaper: WallpaperData? = null,
    val userAvatar: AvatarData? = null,
    val aiAvatars: Map<String, AvatarData> = emptyMap(),
    /**
     * 资产节（schema v2）：壁纸 / 头像 Base64 内嵌。
     * v1 文件为 null（资产平铺在顶层字段）。
     */
    val assets: ExportAssets? = null
) {
    companion object {
        const val SCHEMA_VERSION_1 = 1
        const val SCHEMA_VERSION_2 = 2
    }

    /** 是否为 schema v2 数据（读取端必须同时支持 v1 与 v2）。 */
    val isV2: Boolean get() = schemaVersion >= SCHEMA_VERSION_2
}

/**
 * 人设卡导出（仅 Persona + UserPersona + Scene + Memory）。
 */
@Serializable
data class PersonaCard(
    val schemaVersion: Int = 1,
    val exportedAt: Long,
    val persona: Persona,
    val userPersona: UserPersona,
    val scene: String,
    val memory: String
)
