package com.quiddity.app.ui.chat

import androidx.lifecycle.viewModelScope
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Persona
import com.quiddity.app.data.repo.ChatRepository
import com.quiddity.app.data.repo.ConversationRepository
import com.quiddity.app.domain.ChatError
import com.quiddity.app.domain.GroupReplyQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal class ChatViewModelState(
    private val conversationRepository: ConversationRepository,
    private val viewModelScope: CoroutineScope,
    private val conversationId: String
) {

    val conversation: StateFlow<Conversation?> = conversationRepository.conversations
        .map { list -> list.firstOrNull { it.id == conversationId } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = conversationRepository.conversations.value
                .firstOrNull { it.id == conversationId }
        )


    val _messages = MutableStateFlow<List<Message>>(emptyList())

    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    // 加载状态——区分"正在加载"与"加载完成但为空"。
    // isLoading=true 时 UI 显示空白 Loading 态；首次发射后置 false，
    // 此时再根据 messages.isEmpty() 决定显示 Empty/Messages。
    // Loading → Empty/Messages 的切换由 AnimatedContent 平滑过渡。


    // 加载状态——区分"正在加载"与"加载完成但为空"。
    // isLoading=true 时 UI 显示空白 Loading 态；首次发射后置 false，
    // 此时再根据 messages.isEmpty() 决定显示 Empty/Messages。
    // Loading → Empty/Messages 的切换由 AnimatedContent 平滑过渡。
    val _isLoading = MutableStateFlow(true)

    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ===== 图片发送（OCR 识图 → 聊天 API） =====
    /** 待发送图片 URI（file:// 或 content://），null = 当前未挂载图片。 */


    // ===== 图片发送（OCR 识图 → 聊天 API） =====
    /** 待发送图片 URI（file:// 或 content://），null = 当前未挂载图片。 */
    val _pendingImageUri = MutableStateFlow<String?>(null)

    val pendingImageUri: StateFlow<String?> = _pendingImageUri.asStateFlow()

    /** OCR 识图进行中状态，驱动输入栏加载圈与防重复发送。 */


    /** OCR 识图进行中状态，驱动输入栏加载圈与防重复发送。 */
    val _ocrState = MutableStateFlow<OcrState>(OcrState.Idle)

    val ocrState: StateFlow<OcrState> = _ocrState.asStateFlow()


    val _isGenerating = MutableStateFlow(false)

    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    /** 当前轮次 AI 使用的工具名（聊天页工具报告条展示，生成结束后清空）。 */


    /** 当前轮次 AI 使用的工具名（聊天页工具报告条展示，生成结束后清空）。 */
    val _toolTraces = MutableStateFlow<List<ToolTrace>>(emptyList())

    val toolTraces: StateFlow<List<ToolTrace>> = _toolTraces.asStateFlow()

    /** 待用户确认的危险工具调用（弹窗由 Agent 聊天页展示）。 */


    /** 待用户确认的危险工具调用（弹窗由 Agent 聊天页展示）。 */
    val _pendingToolConfirm = MutableStateFlow<PendingToolConfirm?>(null)

    val pendingToolConfirm: StateFlow<PendingToolConfirm?> = _pendingToolConfirm.asStateFlow()

    /** 撤回后的「重新编辑」缓存：撤回时保留原文/图片/OCR 文本，用户可编辑后作为新消息重发。 */


    /** 撤回后的「重新编辑」缓存：撤回时保留原文/图片/OCR 文本，用户可编辑后作为新消息重发。 */
    val _pendingReedit = MutableStateFlow<PendingReedit?>(null)

    val pendingReedit: StateFlow<PendingReedit?> = _pendingReedit.asStateFlow()

    /** 用户对危险工具确认弹窗做出决定：通过 [resume] 回调放行/取消挂起的流。 */


    // ===== 压缩状态机 =====
    // 与 isGenerating 解耦：压缩在 isGenerating 置 false 之后才启动，两者互斥。
    // Compressing 期间 UI 弹 loading 弹窗、发送按钮置灰、横滑禁用；
    // Success/Failed 为瞬态，UI 弹 Toast 后调 consumeCompressionResult 回到 Idle。
    val _compressionState = MutableStateFlow<CompressionState>(CompressionState.Idle)

    val compressionState: StateFlow<CompressionState> = _compressionState.asStateFlow()


    val _errorEvent = MutableStateFlow<String?>(null)

    val errorEvent: StateFlow<String?> = _errorEvent.asStateFlow()

    /** 本次流式最后一条完成消息（工具痕迹固化用，避免 flow 异步延迟取不到）。 */


    /** 本次流式最后一条完成消息（工具痕迹固化用，避免 flow 异步延迟取不到）。 */
    var lastCompletedAiMessage: Message? = null

    /**
     * 当前生成中合并消息的工具轮段边界（实时内存态）：
     * markSegmentEnd 派发的 UpdateMessage 同步更新，UI 段切分优先使用，
     * 让工具痕迹在流式输出过程中实时穿插在正文段间（不依赖持久化 flow 的异步延迟）。
     * Done 后清空，回落到消息持久化的 toolSegmentEnds。
     */


    /**
     * 当前生成中合并消息的工具轮段边界（实时内存态）：
     * markSegmentEnd 派发的 UpdateMessage 同步更新，UI 段切分优先使用，
     * 让工具痕迹在流式输出过程中实时穿插在正文段间（不依赖持久化 flow 的异步延迟）。
     * Done 后清空，回落到消息持久化的 toolSegmentEnds。
     */
    val _activeSegmentEnds = MutableStateFlow<List<Int>>(emptyList())

    val activeSegmentEnds: StateFlow<List<Int>> = _activeSegmentEnds.asStateFlow()

    /**
     * 结构化错误事件。
     * UI 层可基于错误类别（网络 / 鉴权 / 配置 / 业务 / 未知）做差异化处理：
     * - 网络错误：可提示"网络不佳，是否重试？"
     * - 鉴权错误：可提示"请检查接口密钥是否正确"
     * - 配置错误：可提示"请先在模型配置中添加配置"
     */


    /**
     * 结构化错误事件。
     * UI 层可基于错误类别（网络 / 鉴权 / 配置 / 业务 / 未知）做差异化处理：
     * - 网络错误：可提示"网络不佳，是否重试？"
     * - 鉴权错误：可提示"请检查接口密钥是否正确"
     * - 配置错误：可提示"请先在模型配置中添加配置"
     */
    val _chatError = MutableStateFlow<ChatError?>(null)

    val chatError: StateFlow<ChatError?> = _chatError.asStateFlow()

    /** 当前流式会话的根 Job，用于 stopGeneration 整体取消。 */


    /** 当前流式会话的根 Job，用于 stopGeneration 整体取消。 */
    var streamJob: Job? = null

    /**
     * 全量会话的成员名字 / 头像映射（预计算一次，消息气泡直接查表，
     * 避免每条消息渲染时重复扫描仓库并在组合中订阅 conversations 状态）。
     */


    /**
     * 全量会话的成员名字 / 头像映射（预计算一次，消息气泡直接查表，
     * 避免每条消息渲染时重复扫描仓库并在组合中订阅 conversations 状态）。
     */
    val senderNameMap: StateFlow<Map<String, String>> = conversationRepository.conversations
        .map { list -> list.associate { it.id to it.persona.name } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())


    val senderAvatarMap: StateFlow<Map<String, String?>> = conversationRepository.conversations
        .map { list -> list.associate { it.id to it.persona.aiAvatarUri } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // ===== 群聊点名回复队列（方案四：1 个回复 + 2 个排队） =====


    // ===== 群聊点名回复队列（方案四：1 个回复 + 2 个排队） =====
    val groupQueueEngine = GroupReplyQueue()

    val _groupQueue = MutableStateFlow<List<GroupReplyQueue.Item>>(emptyList())

    val groupQueue: StateFlow<List<GroupReplyQueue.Item>> = _groupQueue.asStateFlow()

    var groupStreamJob: Job? = null


    /**
     * 人设精调编译状态（UI 据此显示加载动画 / 错误反馈）。
     *
     * - Idle：未在编译
     * - Compiling：编译进行中（UI 显示 CircularProgressIndicator，保存按钮禁用）
     * - 由 [compilePersona] 维护生命周期，编译结束后自动回到 Idle
     */
    val _isCompiling = MutableStateFlow(false)

    val isCompiling: StateFlow<Boolean> = _isCompiling.asStateFlow()

    /**
     * 触发人设精调编译。
     *
     * 流程：
     * 1. 取当前会话的人设字段；
     * 2. 调用 [ChatRepository.compilePersona] 让 AI 编译为结构化系统提示词；
     * 3. 成功：返回编译后的文本，由 UI 预览后决定是否采用；
     * 4. 失败：抛异常给调用方（UI 据此显示 Toast）。
     *
     * 本方法**不**直接写入 [Persona.compiledPersona]，以便用户在预览不满意时
     * 选择"返回重调"，避免污染已有会话数据。
     *
     * @param persona 待编译的人设（一般为用户在 PersonaPanel 中编辑后的最新值）
     * @param maxOutputTokens 期望模型输出的最大 token 数，写入提示词约束模型输出长度
     * @return 编译后的系统提示词文本
     */


    // ===== 快速设定草稿 =====
    var quickSetupDraftJob: Job? = null

    /**
     * 更新快速设定描述草稿：500ms 防抖落盘，供面板关闭后回看/重新生成。
     */


    /** Agent 撤回确认弹窗状态（null = 未弹窗）。 */
    val _pendingWithdraw = MutableStateFlow<WithdrawProposal?>(null)

    val pendingWithdraw: StateFlow<WithdrawProposal?> = _pendingWithdraw.asStateFlow()

    /**
     * Agent 模式撤回入口：先收集本轮创建/更改项目，弹出确认框。
     *
     * 语义（与私聊撤回一致）：删除目标消息（含）之后的所有消息；
     * 确认后删除本轮创建的文件（createdPaths），更改项仅提示不可自动恢复。
     */


    // ===== 发送延迟 =====

    /** 当前输入框文本（用于发送延迟检测）。 */
    val _inputBarText = MutableStateFlow("")

    val inputBarText: StateFlow<String> = _inputBarText.asStateFlow()

    /** 发送延迟计时器。 */


    /** 发送延迟计时器。 */
    var sendDelayJob: Job? = null

    /** 输入框最后一次编辑时间（含退格，用于发送延迟防抖重计时）。 */


    /** 输入框最后一次编辑时间（含退格，用于发送延迟防抖重计时）。 */
    var lastInputEditAt = 0L

    /**
     * 更新输入框文本状态（由 ChatInputBar 调用）。
     * 用于发送延迟检测：记录每次编辑（含退格），防抖重计时。
     */


    /**
     * 界面整理提示（对应算法文档 3.1"正在整理前一天的记忆！"）。
     * 由 ChatScreen 在会话首次打开时消费后清空。
     */
    val _timeLibraryHint = MutableStateFlow<String?>(null)

    val timeLibraryHint: StateFlow<String?> = _timeLibraryHint.asStateFlow()

    /**
     * 会话首次打开时调用（对应算法文档 3.1 生成时机）：
     * 若会话级开关已开启且当天尚未生成，先给出"正在整理前一天的记忆！"提示，
     * 再委托协调器生成时间库并注册闹钟。当天已生成 / 开关未开启时静默返回。
     */
}
