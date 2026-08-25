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
    SOLO, GROUP, AGENT
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
    val apiKeyEnc: String,
    /**
     * 该模型支持的最高采样温度（null = 不限制，按全局 [QuiddityConstants.MAX_TEMPERATURE]）。
     * 部分模型（如 Claude 系）仅支持 0～1.0，超范围请求会被服务端拒绝；
     * 发送请求前按此值钳制，UI 设置过高时不会报错。
     */
    val maxTemperature: Double? = null
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
    /**
     * 快速设定面板的用户描述草稿（持久化以便回看/重新生成）。
     * 每次输入防抖写入；空字符串表示从未填写。
     */
    val quickSetupDraft: String = "",
    val apiCatalogId: String? = null,
    val maxTokens: Int? = null,
    val singleMessageTokens: Int? = null,
    /**
     * 会话级采样温度覆盖（0～2，[QuiddityConstants.MIN_TEMPERATURE]～[QuiddityConstants.MAX_TEMPERATURE]）。
     * - null = 跟随全局默认 [AppSettings.globalTemperature]
     * - 官方文档：DeepSeek 思考模式下 temperature 不生效
     */
    val temperature: Double? = null,
    /**
     * 会话级"DeepSeek 官方服务端联网搜索"开关。
     * - true = 当前 API 配置支持时走 Responses API（tools 携带 web_search，服务端执行搜索）
     * - 能力依赖官方平台：providerId=deepseek 且模型为 [QuiddityConstants.DEEPSEEK_RESPONSES_MODEL]
     */
    val webSearchEnabled: Boolean = false,
    /**
     * 会话级"DeepSeek 思考"开关（仅 DeepSeek 官方 flash / pro 模型生效，默认关闭）。
     * - true = 请求携带 reasoning 能力，模型的思考内容单独成一条消息展示；
     * - 非 DeepSeek 官方模型时忽略（不发送不识别字段）。
     */
    val thinkingEnabled: Boolean = false,
    /**
     * 思考深度（仅 [thinkingEnabled] 时生效）：
     * - [QuiddityConstants.THINKING_DEPTH_SHALLOW] = 浅（默认，reasoning_effort=low）
     * - [QuiddityConstants.THINKING_DEPTH_DEEP] = 深（reasoning_effort=high）
     */
    val thinkingDepth: String = QuiddityConstants.THINKING_DEPTH_SHALLOW,
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
     * 被永久禁用的时间框下标（0-9，共 10 个框，上午 0-4、下午 5-9）。
     * 禁用后该框不参与、也不计入可用数量；可用最大数量 = 10 - 禁用框数量。
     */
    val disabledTimeSlots: List<Int> = emptyList(),
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
    val groupMemory: String = "",
    /**
     * 群聊上下文条数 N（仅 type=GROUP 使用；默认 50，范围 1～200）。
     */
    val groupContextLimit: Int = QuiddityConstants.GROUP_DEFAULT_CONTEXT_LIMIT,
    /**
     * 群聊停止模式（仅 type=GROUP 使用）：A=只停止当前、B=清空整个队列（默认）。
     */
    val stopMode: String = QuiddityConstants.GROUP_DEFAULT_STOP_MODE,
    /**
     * 群聊背景（仅 type=GROUP 使用）：用户自定义文本，
     * 注入每个成员回复时的 system 提示词【群聊背景】节，塑造群聊整体氛围；
     * 空字符串 = 不注入（不影响默认群聊规则）。
     */
    val groupBackground: String = "",
    /**
     * 群聊背景/场景的模式（仅 type=GROUP 使用，与 [groupBackground] 合并为一个设置项）：
     * - [com.quiddity.app.util.QuiddityConstants.GROUP_BACKGROUND_MODE_BACKGROUND] = 背景（氛围描述），
     *   注入为【群聊背景】节
     * - [com.quiddity.app.util.QuiddityConstants.GROUP_BACKGROUND_MODE_SCENE] = 场景（多人情境），
     *   注入为【群聊场景】节
     * 两者可选其一开启；旧数据缺省回退为背景模式。
     */
    val groupBackgroundMode: String = QuiddityConstants.GROUP_DEFAULT_BACKGROUND_MODE
)

/**
 * 会话是否拥有角色卡内容（AI 人设 / 用户人设 / 记忆任一非空；场景单独设置不构成角色卡）。
 *
 * 无角色卡的会话（新建后未做任何设定）不弹用户名弹窗，也不被群聊 / Agent
 * 角色选择等「按内容检测」的入口拾取。
 */
val Conversation.hasPersonaContent: Boolean
    get() {
        val p = persona
        val u = userPersona
        return p.name.isNotBlank() || p.desired.isNotBlank() || p.persona.isNotBlank() ||
            p.character.isNotBlank() || p.appearance.isNotBlank() || p.worldBackground.isNotBlank() ||
            u.name.isNotBlank() || u.identity.isNotBlank() || u.gender.isNotBlank() ||
            u.age.isNotBlank() || u.appearance.isNotBlank() || memory.isNotBlank()
    }

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
     * 是否为 DeepSeek 思考内容消息（单独占一条消息展示）。
     * - true = 渲染为思考气泡（带"思考"标签，内容为模型 reasoning_content）；
     * - 不发送给 LLM、不参与压缩（避免污染上下文），但保留在本地供回看。
     */
    val isThinking: Boolean = false,
    /**
     * 应用内本地思考内容（客户端生成，不依赖厂商服务器）。
     *
     * - 非空 = 回复消息头部展示可展开/收起的思考块（默认收起，箭头展开）；
     * - 思考中（isStreaming 且 content 为空）时以高亮滑块样式提示；
     * - 不发送给 LLM、不参与压缩。
     */
    val thinking: String = "",
    /**
     * 发言人会话 id（2.0.0 群聊消息使用）。
     * - 群聊消息带 senderId（指向成员私聊会话 id）
     * - 私聊消息为 null（默认值，兼容旧数据）
     */
    val senderId: String? = null,
    /**
     * 小应用邀请卡片：仅 isNotice=true 且 miniAppId 非空时，
     * 在私聊中渲染为可点击的"邀请卡片"（点击跳回对应小应用）。
     * - 不发送给 LLM、不参与压缩（isNotice 已在各处过滤）
     */
    val miniAppId: String? = null,
    val miniAppTitle: String? = null,
    /**
     * 小应用对局记录气泡：以居中卡片形式展示在私聊里（如"你们刚玩完一局五子棋"）。
     * - true = 渲染为居中卡片气泡（不显示头像、不可撤回/改写）；
     * - 与 isNotice 不同：**会发送给 LLM 参与上下文**，让角色知道刚刚和用户一起玩过；
     * - false = 普通消息。
     */
    val isGameLog: Boolean = false,
    /**
     * 图片消息的 OCR 识别结果（隐藏字段）。
     *
     * - 仅用于构造发送给聊天 API 的上下文（见 [com.quiddity.app.domain.PromptBuilder]），
     *   界面上不展示、[content] 保持用户可见的干净文本（如 `[图片]` + 用户输入）；
     * - null = 普通文本消息。
     */
    val ocrText: String? = null,
    /**
     * 图片消息的本地图片文件 URI（file://，持久化在 filesDir/chat_images/）。
     *
     * - 非空 = 该消息附带一张图片，气泡以固定卡片样式展示缩略图（参考 DeepSeek 网页版）；
     * - 导出/换机后文件可能不存在，渲染时自动降级为占位样式；
     * - null = 普通文本消息。
     */
    val imageUri: String? = null,
    /**
     * 工具轮正文段边界（合并模式消息专用）：合并模式把多轮工具循环的正文拼成一条消息，
     * 本字段记录每个工具轮结束时的正文长度（字符偏移），供 UI 把工具痕迹插入正文流
     * 的对应位置（正文段 → 工具痕迹 → 正文段，与流式输出一致）。
     * - 空列表 = 普通消息（不拆分渲染）；
     * - 只写入合并消息，旧数据无此字段自动兼容。
     */
    val toolSegmentEnds: List<Int> = emptyList(),
    /**
     * DeepSeek 思考原文（reasoning_content，流式期间累积后固化）。
     *
     * DeepSeek 官方约束：携带 tools 的思考模式请求，历史中 assistant 消息必须在
     * 后续所有轮次原样回传 reasoning_content（字段缺失即 400「思考内容需要回传」）。
     * - 不展示（思考展示由提示词引导的【思考】标记负责）、不参与压缩、不导出；
     * - 仅在「请求携带 tools + DeepSeek 模型 + 本消息为请求方自己的发言」时回传，
     *   避免把字段挂在群聊里其他成员的发言上引发新的 400；
     * - 旧数据无此字段自动兼容（空串 = 未知，回传时按空串占位，官方校验字段存在性）。
     */
    val reasoningContent: String = "",
    /**
     * 本轮会话创建/更改的文件路径（Agent 模式撤回追踪，1.6.0）。
     * - 记录该条 AI 消息对应的一轮用户指令中，AI 通过工具创建的文件/目录路径
     *   （create_file / mkdir / copy_file / move_file / rename_file 的目标）；
     * - 撤回该轮时按此清单删除创建物；更改项（写入既有文件、应用状态）见 [changedItems]；
     * - 导出时不携带（文件在设备存储上，路径不可迁移），导入后撤回只问「确认撤回？」。
     */
    val createdPaths: List<String> = emptyList(),
    /**
     * 本轮会话更改的项目摘要（Agent 模式撤回提示，1.6.0）。
     * - 如「写入 /sdcard/x.txt」「修改 com.xxx 权限（不可自动恢复）」；
     * - 撤回确认弹窗中与 [createdPaths] 一起展示，提示用户本轮影响范围；
     * - 仅展示提示，不参与删除。
     */
    val changedItems: List<String> = emptyList(),
    /**
     * 工具调用历史（持久化到消息）：生成结束后把本轮的 [com.quiddity.app.ui.chat.ToolTrace]
     * 固化进消息，之后重新打开会话仍能看到工具调用记录（段间渲染，与正文分界）。
     * - 空列表 = 无工具调用；
     * - 旧数据无此字段自动兼容。
     */
    val toolTraces: List<MessageToolTrace> = emptyList()
)

/**
 * 消息内持久化的工具调用记录（与 [Message.toolTraces] 配套）。
 */
@Serializable
data class MessageToolTrace(
    val name: String,
    val ok: Boolean,
    val summary: String?
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
    /**
     * 全局默认采样温度（0～2，官方默认 1.0）。
     * 会话未单独设置温度时使用该值。
     */
    val globalTemperature: Double = QuiddityConstants.DEFAULT_TEMPERATURE,
    /**
     * 快速设定的采样温度（独立于聊天温度）：
     * - 温度越高，每次生成的人设发散性越强，避免用户总是拿到同一个人设；
     * - 在快速设定面板内调整，范围 0～2，默认 [QuiddityConstants.DEFAULT_QUICK_SETUP_TEMPERATURE]。
     */
    val quickSetupTemperature: Double = QuiddityConstants.DEFAULT_QUICK_SETUP_TEMPERATURE,
    val globalContextLimit: Int = QuiddityConstants.DEFAULT_CONTEXT_LIMIT,
    /**
     * AI 回复多消息切分（UI 叫法"AI 回复切分"）：
     * - true = AI 输出按句末标点 / 括号切成多条消息（像人一样分多条发送）
     * - false = 整条回复作为单条消息
     */
    val multilineAutoSplit: Boolean = false,
    val enterToSend: Boolean = true,
    val activeCatalogId: String? = null,
    val catalog: List<ApiCatalogEntry> = emptyList(),
    /**
     * 视觉 OCR 兜底总开关。
     * - true = 当前对话模型不支持图片时，允许用 [visionCatalog] 里的视觉模型做 OCR 识图后转发给聊天 API
     * - false = 关闭兜底，仅当聊天模型本身支持视觉时才可识图
     */
    val ocrEnabled: Boolean = false,
    /**
     * 视觉 OCR 模型配置列表（独立于 [catalog]，条目结构与普通模型配置一致）。
     * 用于图片识别的兜底模型，服务商可选视觉模型预置项或自定义 OpenAI 兼容地址。
     */
    val visionCatalog: List<ApiCatalogEntry> = emptyList(),
    /**
     * 当前启用的视觉 OCR 模型配置 id（对应 [visionCatalog]）。
     * null = 自动取 [visionCatalog] 第一条。
     */
    val activeVisionCatalogId: String? = null,
    /**
     * 括号内容灰化开关。
     * 开启后，AI / 用户消息中成对括号内的文本以 `onSurfaceVariant.copy(alpha = 0.55f)` 颜色显示。
     * 默认 true（开启，营造剧本式旁白视觉）。
     */
    val bracketGrayEnabled: Boolean = true,
    /**
     * Markdown 渲染开关。
     * 开启后 AI / 用户消息中的标题、加粗、斜体、删除线、行内代码、链接、
     * 引用、列表标记会以 Markdown 样式显示；关闭后一律按纯文本显示。
     * 围栏代码块（```）不受此开关影响，始终按代码卡片渲染。
     */
    val markdownEnabled: Boolean = true,
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
     * - true = 发送消息后等待 [sendDelaySeconds] 秒再发出 API 请求
     * - 若等待期间输入框仍不为空，暂停请求直到输入框清空
     * - 可有效节省 Token 消耗（用户连续输入时合并请求）
     * - 默认 [QuiddityConstants.DEFAULT_SEND_DELAY_ENABLED]
     */
    val sendDelayEnabled: Boolean = QuiddityConstants.DEFAULT_SEND_DELAY_ENABLED,
    /**
     * - 范围 [QuiddityConstants.MIN_SEND_DELAY_SECONDS] - [QuiddityConstants.MAX_SEND_DELAY_SECONDS]
     * - 默认 [QuiddityConstants.DEFAULT_SEND_DELAY_SECONDS]（2秒）
     * - 0 秒 = 关闭发送延迟
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
    val proactiveMessageLastResetDate: String = "",
    /**
     * 群聊教程弹窗是否已看过（首次进入群聊模式列表页弹一次，方案十.9）。
     */
    val groupTutorialSeen: Boolean = false,
    /**
     * Agent 教程弹窗是否已看过（首次进入 Agent 模式列表页弹一次）。
     */
    val agentTutorialSeen: Boolean = false,
    /**
     * 私聊默认名计数器：新会话 1、2、3…，删除不补号（方案二.4）。
     */
    val soloChatCounter: Int = 0,
    /**
     * 群聊默认名计数器：新群聊 1、2、3…，删除不补号（方案二.4）。
     */
    val groupChatCounter: Int = 0,
    /**
     * 回复悬浮窗总开关（需系统悬浮窗权限）。
     * - true = 应用不可见且 AI 回复时显示悬浮窗头像/气泡
     * - false = 关闭（默认）
     */
    val overlayEnabled: Boolean = false,
    /**
     * 悬浮窗头像 URI（null = 默认应用图标）。
     */
    val overlayAvatarUri: String? = null
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
    val assets: ExportAssets? = null,
    /**
     * Agent 模式设置（1.6.0 架构重整）：工具开关、黑名单、权限管控、审计日志。
     * - 旧备份文件为 null（不导入 Agent 设置，保持本机现状）；
     * - 审计日志随备份导出/导入（执行结果、日期、时间、工具名称）；
     * - 注意：消息中的撤回追踪字段（createdPaths / changedItems）**不随导出携带**——
     *   创建物位于设备存储上，无法随备份迁移；导入后撤回只问「确认撤回？」。
     */
    val agentSettings: com.quiddity.app.data.local.AgentSettings? = null
) {
    companion object {
        const val SCHEMA_VERSION_1 = 1
        const val SCHEMA_VERSION_2 = 2
    }

    /** 是否为 schema v2 数据（读取端必须同时支持 v1 与 v2）。 */
    val isV2: Boolean get() = schemaVersion >= SCHEMA_VERSION_2
}

/**
 * 角色卡导出（覆盖「人设」一栏全部设置：Persona + UserPersona + Scene + Memory + 快速设定内容）。
 */
@Serializable
data class PersonaCard(
    val schemaVersion: Int = 1,
    val exportedAt: Long,
    val persona: Persona,
    val userPersona: UserPersona,
    val scene: String,
    val memory: String,
    val quickSetupDraft: String = ""
)
