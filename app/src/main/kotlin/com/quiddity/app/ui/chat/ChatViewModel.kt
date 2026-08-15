package com.quiddity.app.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.MessageToolTrace
import com.quiddity.app.data.model.Persona
import com.quiddity.app.data.model.PersonaCard
import com.quiddity.app.data.model.Role
import com.quiddity.app.data.model.UserPersona
import com.quiddity.app.data.repo.ChatRepository
import com.quiddity.app.data.repo.ConversationRepository
import com.quiddity.app.data.repo.SettingsRepository
import com.quiddity.app.data.repo.TimeLibraryRepository.GenerationOutcome
import com.quiddity.app.di.ServiceLocator
import com.quiddity.app.domain.ApiCatalogManager
import com.quiddity.app.domain.ChatError
import com.quiddity.app.domain.GroupReplyQueue
import com.quiddity.app.domain.TimeLibraryEngine
import com.quiddity.app.domain.VisionOcrService
import com.quiddity.app.util.IdGenerator
import com.quiddity.app.util.ImageUtils
import com.quiddity.app.util.QuiddityConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

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
 * 对话页 ViewModel。
 *
 * 事件回调改为 `suspend (Event) -> Unit`，在 [runStream] 协程内按顺序串行执行，
 * 避免事件并行写入导致消息顺序错乱。
 * [streamJob] 跟踪整条流式链路，stopGeneration 取消整条链路。
 * runStream 统一收尾：isGenerating、errorEvent、streamJob 生命周期集中管理。
 */
class ChatViewModel(
    private val conversationRepository: ConversationRepository,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val apiCatalogManager: ApiCatalogManager,
    private val visionOcrService: VisionOcrService,
    private val conversationId: String,
    /**
     * 任务全部完结（流式 / 压缩 / 发送延迟均空闲）时的回调。
     * 由 [ChatViewModelHost] 注入，用于"退出会话后等任务完结再释放"。
     */
    private val onIdle: ((String) -> Unit)? = null
) : ViewModel() {

    val conversation: StateFlow<Conversation?> = conversationRepository.conversations
        .map { list -> list.firstOrNull { it.id == conversationId } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = conversationRepository.conversations.value
                .firstOrNull { it.id == conversationId }
        )

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    // 加载状态——区分"正在加载"与"加载完成但为空"。
    // isLoading=true 时 UI 显示空白 Loading 态；首次发射后置 false，
    // 此时再根据 messages.isEmpty() 决定显示 Empty/Messages。
    // Loading → Empty/Messages 的切换由 AnimatedContent 平滑过渡。
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ===== 图片发送（OCR 识图 → 聊天 API） =====
    /** 待发送图片 URI（file:// 或 content://），null = 当前未挂载图片。 */
    private val _pendingImageUri = MutableStateFlow<String?>(null)
    val pendingImageUri: StateFlow<String?> = _pendingImageUri.asStateFlow()

    /** OCR 识图进行中状态，驱动输入栏加载圈与防重复发送。 */
    private val _ocrState = MutableStateFlow<OcrState>(OcrState.Idle)
    val ocrState: StateFlow<OcrState> = _ocrState.asStateFlow()

    fun setPendingImage(uri: String?) {
        _pendingImageUri.value = uri
        if (uri == null) {
            _ocrState.value = OcrState.Idle
        }
    }

    fun clearPendingImage() = setPendingImage(null)

    init {
        // 消息流生命周期管理：用单一 launch + collect 自动跟随 viewModelScope 生命周期，
        // suspend observeMessages 在协程内调用，IO 自动调度。
        viewModelScope.launch {
            conversationRepository.observeMessages(conversationId)
                .collect {
                    _messages.value = it
                    // 首次发射后标记加载完成——后续发射不再改变 isLoading（始终 false）
                    _isLoading.value = false
                }
        }

        // 一次性同步：会话首次加载时，若 memoryBankEnabled 且 memoryBankRounds 与
        // contextLimit 不一致（历史数据残留旧默认值 40），自动同步为 contextLimit。
        // 同步后两者相等，不会重复触发；用户后续手动调整 memoryBankRounds 不会被覆盖
        // （仅当 contextLimit 再次变化时才由 updateContextLimit 重新同步）。
        viewModelScope.launch {
            conversation.firstOrNull { conv ->
                conv != null && conv.memoryBankEnabled && conv.memoryBankRounds != conv.contextLimit
            }?.let { conv ->
                val syncRounds = conv.contextLimit.coerceIn(
                    QuiddityConstants.MIN_MEMORY_BANK_ROUNDS,
                    QuiddityConstants.MAX_MEMORY_BANK_ROUNDS
                )
                conversationRepository.updateConversation(
                    conv.copy(memoryBankRounds = syncRounds)
                )
            }
        }
    }

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    /** 当前轮次 AI 使用的工具名（聊天页工具报告条展示，生成结束后清空）。 */
    private val _toolTraces = MutableStateFlow<List<ToolTrace>>(emptyList())
    val toolTraces: StateFlow<List<ToolTrace>> = _toolTraces.asStateFlow()

    /** 待用户确认的危险工具调用（弹窗由 Agent 聊天页展示）。 */
    private val _pendingToolConfirm = MutableStateFlow<PendingToolConfirm?>(null)
    val pendingToolConfirm: StateFlow<PendingToolConfirm?> = _pendingToolConfirm.asStateFlow()

    /** 撤回后的「重新编辑」缓存：撤回时保留原文/图片/OCR 文本，用户可编辑后作为新消息重发。 */
    private val _pendingReedit = MutableStateFlow<PendingReedit?>(null)
    val pendingReedit: StateFlow<PendingReedit?> = _pendingReedit.asStateFlow()

    /** 用户对危险工具确认弹窗做出决定：通过 [resume] 回调放行/取消挂起的流。 */
    fun confirmTool(approved: Boolean) {
        val pending = _pendingToolConfirm.value ?: return
        _pendingToolConfirm.value = null
        pending.resume(approved)
    }

    // ===== 压缩状态机 =====
    // 与 isGenerating 解耦：压缩在 isGenerating 置 false 之后才启动，两者互斥。
    // Compressing 期间 UI 弹 loading 弹窗、发送按钮置灰、横滑禁用；
    // Success/Failed 为瞬态，UI 弹 Toast 后调 consumeCompressionResult 回到 Idle。
    private val _compressionState = MutableStateFlow<CompressionState>(CompressionState.Idle)
    val compressionState: StateFlow<CompressionState> = _compressionState.asStateFlow()

    private val _errorEvent = MutableStateFlow<String?>(null)
    val errorEvent: StateFlow<String?> = _errorEvent.asStateFlow()

    /** 本次流式最后一条完成消息（工具痕迹固化用，避免 flow 异步延迟取不到）。 */
    private var lastCompletedAiMessage: Message? = null

    /**
     * 当前生成中合并消息的工具轮段边界（实时内存态）：
     * markSegmentEnd 派发的 UpdateMessage 同步更新，UI 段切分优先使用，
     * 让工具痕迹在流式输出过程中实时穿插在正文段间（不依赖持久化 flow 的异步延迟）。
     * Done 后清空，回落到消息持久化的 toolSegmentEnds。
     */
    private val _activeSegmentEnds = MutableStateFlow<List<Int>>(emptyList())
    val activeSegmentEnds: StateFlow<List<Int>> = _activeSegmentEnds.asStateFlow()

    /**
     * 结构化错误事件。
     * UI 层可基于错误类别（网络 / 鉴权 / 配置 / 业务 / 未知）做差异化处理：
     * - 网络错误：可提示"网络不佳，是否重试？"
     * - 鉴权错误：可提示"请检查接口密钥是否正确"
     * - 配置错误：可提示"请先在模型配置中添加配置"
     */
    private val _chatError = MutableStateFlow<ChatError?>(null)
    val chatError: StateFlow<ChatError?> = _chatError.asStateFlow()

    /** 当前流式会话的根 Job，用于 stopGeneration 整体取消。 */
    private var streamJob: Job? = null

    /**
     * 1.5.0 延迟输出：加载动画时长 = 回复字数 × 每字毫秒数。
     * [replyRunStart] 当前回复运行开始时间；[replyRunChars] 累计字数（含切分消息）。
     */
    private var replyRunStart = 0L
    private var replyRunChars = 0

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
    private val groupQueueEngine = GroupReplyQueue()
    private val _groupQueue = MutableStateFlow<List<GroupReplyQueue.Item>>(emptyList())
    val groupQueue: StateFlow<List<GroupReplyQueue.Item>> = _groupQueue.asStateFlow()
    private var groupStreamJob: Job? = null

    fun isGroup(): Boolean = conversation.value?.type == ConversationType.GROUP

    /** 群聊成员会话列表（按加入顺序）。 */
    fun groupMembers(): List<Conversation> =
        conversation.value?.memberConversationIds.orEmpty()
            .mapNotNull { conversationRepository.getConversation(it) }

    /** 成员 AI 名字（消息气泡显示用）。 */
    fun memberName(convId: String): String =
        conversationRepository.getConversation(convId)?.persona?.name.orEmpty()

    /** 成员 AI 头像（消息气泡显示用）。 */
    fun memberAvatar(convId: String): String? =
        conversationRepository.getConversation(convId)?.persona?.aiAvatarUri

    /**
     * 群聊成员点名回复（方案三：头像点击发送；方案四.8 上下文在点击那一刻定格）。
     */
    fun enqueueGroupMember(memberId: String) {
        if (!isGroup()) return
        val group = conversation.value ?: return
        if (memberId !in group.memberConversationIds) return
        if (groupStreamJob?.isActive == true && groupQueueEngine.isFull) return
        if (groupQueueEngine.contains(memberId)) return
        // 上下文定格（方案四.8）：只冻结已完成的群聊消息，流式中的半截消息不入上下文
        val frozen = _messages.value.filterNot { it.isNotice || it.isThinking || it.isStreaming }
        enqueueGroupMemberWithFrozen(memberId, frozen)
    }

    /**
     * 入队指定成员（使用调用方提供的冻结快照）。
     *
     * @param frozen 该成员回复时的上下文快照（方案四.8：点击/发送那一刻定格）
     */
    private fun enqueueGroupMemberWithFrozen(memberId: String, frozen: List<Message>) {
        if (!isGroup()) return
        val group = conversation.value ?: return
        if (memberId !in group.memberConversationIds) return
        if (groupStreamJob?.isActive == true && groupQueueEngine.isFull) return
        if (groupQueueEngine.contains(memberId)) return
        if (!groupQueueEngine.enqueue(memberId, frozen)) return
        syncGroupQueue()
        if (groupStreamJob?.isActive != true) startGroupQueueProcessor()
    }

    private fun syncGroupQueue() {
        _groupQueue.value = groupQueueEngine.snapshot()
    }

    /** 队列中某成员的排队序号（0=正在回复，1/2=排队）；不在队返回 -1。 */
    fun queuePosition(memberId: String): Int = groupQueueEngine.positionOf(memberId)

    fun isQueueFull(): Boolean = groupQueueEngine.isFull

    /**
     * 群聊队列处理器：串行消费队列，每个成员失败重试 5 次并逐次通知；
     * 任一成员 5 次失败后整个队列取消、所有头像恢复（方案十三）。
     */
    private fun startGroupQueueProcessor() {
        groupStreamJob = viewModelScope.launch {
            val selfJob = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
            _isGenerating.value = true
            try {
                while (groupQueueEngine.isNotEmpty) {
                    val item = groupQueueEngine.snapshot().firstOrNull()
                        ?: break
                    val ok = runGroupMemberReplyWithRetry(item)
                    groupQueueEngine.dequeue()
                    syncGroupQueue()
                    if (!ok) {
                        groupQueueEngine.clear()
                        syncGroupQueue()
                        _errorEvent.value = "群聊回复失败，队列已取消"
                        break
                    }
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                // 群聊队列异常兜底：清空队列、恢复头像，不让未捕获异常崩掉应用
                groupQueueEngine.clear()
                syncGroupQueue()
                _errorEvent.value = "群聊回复出错：${t.message ?: "未知错误"}，队列已取消"
            } finally {
                // 仅当本 job 仍是当前处理器时才复位生成标志，
                // 避免被 stop/重说取消的旧处理器在新建 job 启动后把它误置 false
                if (groupStreamJob === selfJob) {
                    _isGenerating.value = false
                }
                settleInterruptedStreams()
                notifyIdleIfNoWork()
            }
            awaitGroupMemoryCompressionIfNeeded()
            notifyIdleIfNoWork()
        }
    }

    /**
     * 单成员回复（含 5 次重试）。返回是否最终成功。
     */
    private suspend fun runGroupMemberReplyWithRetry(item: GroupReplyQueue.Item): Boolean {
        val group = conversation.value ?: return false
        val member = conversationRepository.getConversation(item.memberId) ?: return false
        var attempt = 0
        while (attempt < QuiddityConstants.GROUP_RETRY_COUNT) {
            attempt++
            var failed = false
            // 重试起点快照：本次尝试追加的该成员回复 id，失败时回滚，
            // 避免半截流式内容在重试间累积、以及 5 次失败后残留"打字中"气泡
            // 直接从 store 的 StateFlow 读取（同步更新），不依赖 UI 收集协程的调度
            val beforeMemberMsgIds = conversationRepository.observeMessages(conversationId).value
                .filter { it.role == Role.ASSISTANT && it.senderId == item.memberId }
                .map { it.id }
                .toSet()
            replyRunStart = System.currentTimeMillis()
            replyRunChars = 0
            try {
                chatRepository.streamGroupMemberReply(
                    member = member,
                    group = group,
                    transcript = item.frozenMessages,
                    senderId = item.memberId
                ) { event ->
                    when (event) {
                        is ChatRepository.Event.Error -> {
                            failed = true
                            if (attempt < QuiddityConstants.GROUP_RETRY_COUNT) {
                                _errorEvent.value =
                                    "网络错误，重试中 $attempt/${QuiddityConstants.GROUP_RETRY_COUNT}"
                            } else {
                                _errorEvent.value =
                                    "成员 ${member.persona.name.ifBlank { "AI" }} 回复失败"
                            }
                        }
                        else -> handleStreamEvent(event)
                    }
                }
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                failed = true
                _errorEvent.value = "成员回复出错：${t.message ?: "未知错误"}"
            }
            if (failed) {
                rollbackFailedGroupAttempt(item.memberId, beforeMemberMsgIds)
            } else {
                return true
            }
        }
        return false
    }

    /**
     * 群聊成员回复失败时回滚本次尝试追加的该成员消息（保留用户消息与既有内容）。
     */
    private suspend fun rollbackFailedGroupAttempt(memberId: String, beforeIds: Set<String>) {
        val current = conversationRepository.observeMessages(conversationId).value
        val kept = current.filterNot { msg ->
            msg.role == Role.ASSISTANT && msg.senderId == memberId && msg.id !in beforeIds
        }
        if (kept.size != current.size) {
            conversationRepository.replaceMessages(conversationId, kept)
        }
    }

    /**
     * 群聊小本本（方案六.2/十六.3）：队列全部回完后，若群聊消息数达到阈值
     * 则压缩群聊记录写入 groupMemory；失败静默不打断用户。
     */
    private suspend fun awaitGroupMemoryCompressionIfNeeded() {
        val group = conversation.value ?: return
        if (!isGroup()) return
        val messages = _messages.value.filterNot { it.isNotice || it.isThinking }
        if (messages.size < QuiddityConstants.GROUP_MEMORY_THRESHOLD) return
        val summary = runCatching {
            chatRepository.compressGroupMemory(group, messages)
        }.getOrElse { group.groupMemory }
        if (summary.isNotBlank() && summary != group.groupMemory) {
            conversationRepository.updateConversation(group.copy(groupMemory = summary))
        }
    }

    /**
     * 发送用户消息并触发 AI 流式回复。
     *
     * 用户消息 ID 使用 [IdGenerator]，避免 `System.currentTimeMillis()` 在毫秒级连发时产出重复 ID。
     *
     * 发送延迟期间不阻塞 _isGenerating，允许用户连续发送多条消息：
     * - 消息保存使用 NonCancellable 上下文，确保即使延迟被重置也不会丢失消息
     * - 延迟计时器通过 [sendDelayJob] 管理，用户发送新消息时自动重置
     * - 延迟结束后调用 [startApiStream] 发起 API 请求（此时才设置 _isGenerating）
     */
    fun sendMessage(
        text: String,
        ocrText: String? = null,
        imageUri: String? = null
    ) {
        // 发送新消息即放弃上一轮的「重新编辑」入口
        _pendingReedit.value = null
        // 纯图片消息允许内容为空（气泡用图片卡片展示，模型上下文由 ocrText 补充）
        if (text.isBlank() && imageUri.isNullOrBlank()) return
        val conv = conversation.value ?: return
        // 群聊：只追加用户消息，不自动触发回复（回复靠点头像点名）；
        // 队列进行中仍可发送新消息（方案四.8），新消息不影响正在进行的回复
        if (isGroup()) {
            if (_compressionState.value is CompressionState.Compressing) return
            sendGroupUserMessage(text, conv, ocrText, imageUri)
            return
        }
        // 仅在 API 调用 / 压缩期间阻止发送；发送延迟期间允许继续发送
        if (_isGenerating.value || _compressionState.value is CompressionState.Compressing) return
        // 方案九.4/6：私聊必须设置用户名才能发送消息（Agent 模式不要求用户名）
        if (conv.type == ConversationType.SOLO && conv.userPersona.name.isBlank()) {
            _errorEvent.value = "请先设置用户名"
            return
        }

        // 取消已 pending 的发送延迟（用户在延迟期间又发了一条消息 → 重置计时器）
        sendDelayJob?.cancel()

        sendDelayJob = viewModelScope.launch {
            // 发送前检测 API/模型是否切换
            // NonCancellable：确保消息保存不被中途取消导致丢失
            withContext(NonCancellable) {
                checkAndUpdateModelContext()

                val now = System.currentTimeMillis()
                val userMsg = Message(
                    id = IdGenerator.newId(IdGenerator.Prefix.USER_MESSAGE),
                    conversationId = conv.id,
                    role = Role.USER,
                    content = text,
                    ocrText = ocrText?.trim()?.takeIf { it.isNotBlank() },
                    imageUri = imageUri,
                    timestamp = now
                )
                conversationRepository.appendMessage(userMsg)
            }

            // 发送延迟——等待用户停止输入后再发出 API 请求（编辑感知防抖；0 秒 = 关闭）
            val settings = settingsRepository.currentSnapshot()
            if (settings.sendDelayEnabled && settings.sendDelaySeconds > 0) {
                val delayMs = settings.sendDelaySeconds * 1000L
                while (true) {
                    if (com.quiddity.app.domain.SendDelayGate.shouldFire(
                            _inputBarText.value,
                            lastInputEditAt,
                            System.currentTimeMillis(),
                            delayMs
                        )
                    ) {
                        break
                    }
                    kotlinx.coroutines.delay(250)
                }
            }

            // 延迟结束（或未启用延迟）→ 发起 API 请求
            startApiStream()
        }
    }

    /**
     * 发送带图片的消息：先 OCR 识图，再把识别文本交给聊天 API。
     *
     * 完整链路（对应"视觉 OCR 设置"）：
     * 1. 解析识图模型——当前对话模型自带视觉则直接用对话模型，否则用视觉 OCR 名册兜底；
     * 2. 纯文本模型且未开启 OCR 兜底时给出引导提示，不发起请求；
     * 3. 识别成功后把用户消息组装为 `[图片] + OCR 识别结果 + 用户文字`，
     *    走既有的 [sendMessage]（单聊）或 [sendGroupUserMessage]（群聊）流程。
     *
     * 识别失败时保留待发送图片，用户可调整配置后重试。
     */
    fun sendMessageWithImage(context: Context, text: String, imageUri: String) {
        if (imageUri.isBlank()) return
        if (_ocrState.value is OcrState.Recognizing) return
        val conv = conversation.value ?: return
        viewModelScope.launch {
            _ocrState.value = OcrState.Recognizing
            // 发送前置校验：失败时保留待发送图片，避免"OCR 白跑 + 图片被吞"
            if (!isGroup()) {
                if (conv.userPersona.name.isBlank()) {
                    _ocrState.value = OcrState.Idle
                    _errorEvent.value = "请先设置用户名"
                    return@launch
                }
                if (_isGenerating.value || _compressionState.value is CompressionState.Compressing) {
                    _ocrState.value = OcrState.Idle
                    _errorEvent.value = "当前有回复进行中，请稍后再发"
                    return@launch
                }
            }
            val settings = settingsRepository.currentSnapshot()
            val chatEntry = apiCatalogManager.resolveEntry(settings, conv)
            // 纯文本模型 + 未开启 OCR 兜底：直接引导，不发请求
            if (!apiCatalogManager.isVisionModel(
                    chatEntry?.apiModel.orEmpty(),
                    chatEntry?.providerId.orEmpty()
                ) && !settings.ocrEnabled
            ) {
                _ocrState.value = OcrState.Idle
                _errorEvent.value =
                    "当前模型不支持看图，且未开启「视觉 OCR」兜底，请到总设置 → 视觉 OCR 中开启并配置视觉模型"
                return@launch
            }
            val result = visionOcrService.recognizeImage(
                context = context,
                imageUri = Uri.parse(imageUri),
                settings = settings,
                chatEntry = chatEntry
            )
            _ocrState.value = OcrState.Idle
            result.onSuccess { ocrText ->
                // 图片从临时文件复制为持久化文件（msg_ 前缀），随消息长期保存；
                // 临时文件（source_temp_ 前缀）在 pendingImageUri 清空后由界面层回收。
                val persistentUri = runCatching {
                    ImageUtils.copyToInternalStorage(
                        context = context,
                        sourceUri = Uri.parse(imageUri),
                        subdir = "chat_images",
                        fileNamePrefix = "msg_"
                    ).toString()
                }.getOrNull()
                if (persistentUri == null) {
                    _errorEvent.value = "图片保存失败，请重试"
                    return@onSuccess
                }
                _pendingImageUri.value = null
                // 界面可见内容 = 用户输入（图片以卡片形式展示，不再塞 "[图片]" 文本）；
                // OCR 识别结果作为隐藏字段，仅注入发给模型的上下文。
                val visibleContent = text.trim()
                val ocr = ocrText.trim().takeIf { it.isNotBlank() }
                if (isGroup()) {
                    sendGroupUserMessage(visibleContent, conv, ocr, persistentUri)
                } else {
                    sendMessage(visibleContent, ocrText = ocr, imageUri = persistentUri)
                }
            }.onFailure { e ->
                _errorEvent.value = "图片识别失败：${e.message ?: "未知错误"}"
            }
        }
    }

    /**
     * 群聊用户消息：直接追加进群聊记录（方案三.1/四.8），不触发自动回复。
     * 消息中带「@名字」点名时，被点名的成员自动入队回复（受队列容量限制）。
     */
    private fun sendGroupUserMessage(
        text: String,
        conv: Conversation,
        ocrText: String? = null,
        imageUri: String? = null
    ) {
        sendDelayJob?.cancel()
        sendDelayJob = viewModelScope.launch {
            withContext(NonCancellable) {
                val now = System.currentTimeMillis()
                val userMsg = Message(
                    id = IdGenerator.newId(IdGenerator.Prefix.USER_MESSAGE),
                    conversationId = conv.id,
                    role = Role.USER,
                    content = text,
                    ocrText = ocrText?.trim()?.takeIf { it.isNotBlank() },
                    imageUri = imageUri,
                    timestamp = now
                )
                conversationRepository.appendMessage(userMsg)
                // @ 点名：冻结快照必须包含本条刚发送的消息（_messages 异步更新，不能依赖）
                val frozen = (_messages.value.filterNot { it.id == userMsg.id } + userMsg)
                    .filterNot { it.isNotice || it.isThinking || it.isStreaming }
                val members = conversation.value?.memberConversationIds.orEmpty()
                    .mapNotNull { conversationRepository.getConversation(it) }
                com.quiddity.app.domain.GroupChatRules.mentionedMemberIds(text, members)
                    .forEach { memberId ->
                        enqueueGroupMemberWithFrozen(memberId, frozen)
                    }
            }
        }
    }

    /**
     * 发起 API 流式请求（发送延迟结束后调用）。
     *
     * 独立于 [sendMessage] 以便延迟期间不阻塞 _isGenerating，
     * 用户可在延迟期间继续发送消息（消息会被保存，延迟计时器重置）。
     *
     * 两阶段时序（不复用 [runStream]，精细管理 isGenerating 与 compressionState）：
     * 1. 流式阶段：_isGenerating=true → streamAssistantReply；finally _isGenerating=false。
     * 2. 压缩阶段：仅当流式未出错时调用 [awaitCompressionIfNeeded]；_compressionState 驱动 UI 弹窗。
     * 压缩期间 isGenerating=false（UI 不显示停止按钮，用户无法主动取消）；
     * ViewModel 销毁时 streamJob cancel，awaitCompressionIfNeeded 内重置状态为 Idle。
     */
    private fun startApiStream() {
        if (_isGenerating.value || _compressionState.value is CompressionState.Compressing) return
        val conv = conversation.value ?: return
        val sceneAtStart = conv.scene
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            // ===== 流式阶段 =====
            var streamError: Throwable? = null
            val selfJob = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
            cleanupStaleStreamingMessages()
            _isGenerating.value = true
            _toolTraces.value = emptyList()
            _pendingToolConfirm.value = null
            // Agent 模式：回复期间启动前台任务服务，用户切后台时进程不被回收，
            // 通知栏统一显示「潮水无声，静待回荡」（Android 8+ 表现一致）
            val agentTaskStarted = conv.type == com.quiddity.app.data.model.ConversationType.AGENT
            if (agentTaskStarted) {
                com.quiddity.app.active.AgentTaskService.start(
                    com.quiddity.app.di.ServiceLocator.applicationContext
                )
            }
            try {
                replyRunStart = System.currentTimeMillis()
                replyRunChars = 0
                val history = _messages.value
                // 思考由提示词引导模型输出【思考】/【回答】标记，客户端拆分展示；
                // 本地不再拼接模板思考，关闭思考开关时提示词也不带引导，自然无思考内容。
                chatRepository.streamAssistantReply(
                    conv,
                    history,
                    effectiveMemoryStrategy(conv),
                    thinking = ""
                ) { event ->
                    if (event is ChatRepository.Event.Error) {
                        streamError = event.throwable
                        _errorEvent.value = event.throwable.message ?: "未知错误"
                        _chatError.value = chatRepository.classify(event.throwable)
                    } else {
                        handleStreamEvent(event)
                        if (event is ChatRepository.Event.CompleteMessage) {
                            markSceneInjectedIfUnchanged(sceneAtStart)
                        }
                    }
                }
            } catch (c: kotlinx.coroutines.CancellationException) {
                // 用户停止 / 页面销毁：不当作错误，收尾交给 finally
                throw c
            } catch (t: Throwable) {
                streamError = t
                _errorEvent.value = t.message ?: "未知错误"
                _chatError.value = chatRepository.classify(t)
            } finally {
                if (streamJob === selfJob) {
                    _isGenerating.value = false
                    _toolTraces.value = emptyList()
                    _pendingToolConfirm.value = null
                }
                if (agentTaskStarted) {
                    com.quiddity.app.active.AgentTaskService.stop(
                        com.quiddity.app.di.ServiceLocator.applicationContext
                    )
                }
                settleInterruptedStreams()
                notifyIdleIfNoWork()
            }
            // ===== 压缩阶段：仅当流式正常结束时触发 =====
            if (streamError == null) {
                awaitCompressionIfNeeded(conv)
            }
            notifyIdleIfNoWork()
        }
    }

    /** 是否有未完结的任务（流式 / 压缩 / 发送延迟）。供宿主判断退出后能否立即释放。 */
    fun hasActiveWork(): Boolean =
        _isGenerating.value ||
            _compressionState.value is CompressionState.Compressing ||
            (sendDelayJob?.isActive == true) ||
            (groupStreamJob?.isActive == true)

    /** 无未完结任务时通知宿主（仅当宿主已请求释放才生效）。 */
    private fun notifyIdleIfNoWork() {
        if (!hasActiveWork()) onIdle?.invoke(conversationId)
    }

    /**
     * 触发记忆库压缩（若满足条件）。
     *
     * 提取自原 checkMemoryBankCompression：将压缩改为可视化流程——
     * Compressing 状态驱动 UI 弹 loading 弹窗；成功/失败分别为瞬态供 UI 弹 Toast。
     * 失败时不更新 lastCompressedAtRound，下次压缩时 takeFromRound 自动带上未压缩部分。
     *
     * 防呆：CancellationException（ViewModel 销毁 / stopGeneration）重置状态为 Idle 再 rethrow，
     * 避免状态卡在 Compressing。
     */
    private suspend fun awaitCompressionIfNeeded(conv: Conversation) {
        if (!conv.memoryBankEnabled) return
        val messages = _messages.value
        val userRounds = messages.count { it.role == Role.USER }
        val roundsSinceLastCompress = userRounds - conv.lastCompressedAtRound
        if (roundsSinceLastCompress < conv.memoryBankRounds) return

        _compressionState.value = CompressionState.Compressing
        try {
            val result = chatRepository.compressConversationMemory(conv, messages)
            if (result.success) {
                // 6.5.2：摘要写入 compressedMemory，索引写入 memoryIndex（含程序补全的覆盖范围）
                conversationRepository.updateConversation(
                    conv.copy(
                        compressedMemory = result.summary,
                        memoryIndex = result.index,
                        lastCompressedAtRound = userRounds
                    )
                )
                _compressionState.value = CompressionState.Success
            } else {
                // 摘要段为空 → 本次压缩失败，两字段都保持旧值（6.5.2）
                _compressionState.value = CompressionState.Failed
            }
        } catch (c: kotlinx.coroutines.CancellationException) {
            _compressionState.value = CompressionState.Idle
            throw c
        } catch (_: Exception) {
            // 压缩失败：不更新 lastCompressedAtRound，下次自动带上未压缩部分
            _compressionState.value = CompressionState.Failed
        }
    }

    /**
     * 消费压缩结果（Success/Failed → Idle）。
     * UI 弹 Toast 后调用，模式对齐 [consumeError]。
     */
    fun consumeCompressionResult() {
        if (_compressionState.value is CompressionState.Success ||
            _compressionState.value is CompressionState.Failed
        ) {
            _compressionState.value = CompressionState.Idle
            notifyIdleIfNoWork()
        }
    }

    /** 让 AI 主动发消息（空对话开场）。 */
    fun letAiStart() {
        if (isGroup()) return
        if (_isGenerating.value) return
        cancelPendingSend() // 取消 pending 的发送延迟
        val conv = conversation.value ?: return
        val sceneAtStart = conv.scene

        runStream {
            chatRepository.letAiStart(conv, effectiveMemoryStrategy(conv)) { event ->
                handleStreamEvent(event)
                if (event is ChatRepository.Event.CompleteMessage) {
                    markSceneInjectedIfUnchanged(sceneAtStart)
                }
            }
        }
    }

    /**
     * 重新生成当前轮次的 AI 回复（"重说这一轮"）。
     *
     * 语义：重说 = 删除最后一条 USER 消息之后的所有 AI 消息（含"继续说"产生的多条 AI），
     * 然后基于该 USER 消息重新生成一条全新的 AI 回复。这样保证"重说"覆盖整个当前轮次，
     * 而非仅替换最后一条 AI 消息（避免"继续说"堆积的旧 AI 残留干扰新生成）。
     *
     * 完整重做流程：
     * 1. 找到最后一条 USER 消息的位置（当前轮次的起点）；
     * 2. 删除该 USER 消息之后的所有 AI 消息（保留 USER 消息本身用于构造上下文）；
     * 3. 用删除后的 history 重新触发 streamAssistantReply。
     *
     * 约束：
     * - 正在生成中禁止操作（防止并发流相互覆盖）；
     * - 最后一条必须是 ASSISTANT 且 isStreaming=false（UI 仅对末位 AI 显示"重说"按钮）；
     * - 最后一条是 USER 时忽略（无 AI 回复可重说）。
     */
    fun regenerate() {
        if (isGroup()) return
        if (_isGenerating.value) return
        cancelPendingSend() // 取消 pending 的发送延迟
        val conv = conversation.value ?: return
        val sceneAtStart = conv.scene
        val current = _messages.value
        if (current.isEmpty()) return
        val last = current.last()
        // 防御性：UI 上"重说"按钮仅对最后一条 AI 消息可见，但外部代码（如测试、deep link）
        // 可能直接调用本方法时遇到最后一条是 USER 的边界情况——此时无 AI 回复可重说。
        if (last.role != Role.ASSISTANT) return
        if (last.isStreaming) return  // 还在 streaming 中：忽略，等待完成

        // 定位最后一条 USER 消息：保留 0..lastUserIndex（含 USER），删除其后的所有 AI 消息。
        val lastUserIndex = current.indexOfLast { it.role == Role.USER }
        // 上一版回复原文（重说提示词对照用）：常规轮次 = USER 之后的所有 AI 消息；
        // AI 开场轮 = 全部消息。让模型知道上一版说了什么，才能有意识地换一种表达。
        val previousReplies = (if (lastUserIndex >= 0) {
            current.subList(lastUserIndex + 1, current.size)
        } else {
            current
        }).filterNot { it.isNotice || it.isThinking }
            .mapNotNull { it.content.takeIf { c -> c.isNotBlank() } }
            .joinToString("\n")
            .takeIf { it.isNotBlank() }
        if (lastUserIndex >= 0) {
            // 常规轮次：删除最后一条 USER 之后的所有 AI 消息，再重新生成整轮回复
            val newHistory = current.subList(0, lastUserIndex + 1).toList()
            runStream {
                conversationRepository.replaceMessages(conversationId, newHistory)
                chatRepository.streamAssistantReply(
                    conv,
                    newHistory,
                    effectiveMemoryStrategy(conv),
                    previousReplies
                ) { event ->
                    handleStreamEvent(event)
                    if (event is ChatRepository.Event.CompleteMessage) {
                        markSceneInjectedIfUnchanged(sceneAtStart)
                    }
                }
            }
        } else {
            // AI 开场轮（"让 AI 先说"，无 USER 消息）：整轮全部重说。
            // 修复：旧实现只删最后一条并把 assistant 结尾历史直接续写，
            // 导致只重生成最后一句、前面句子全部残留。
            runStream {
                conversationRepository.replaceMessages(conversationId, emptyList())
                chatRepository.letAiStart(
                    conv,
                    effectiveMemoryStrategy(conv),
                    previousReplies
                ) { event ->
                    handleStreamEvent(event)
                    if (event is ChatRepository.Event.CompleteMessage) {
                        markSceneInjectedIfUnchanged(sceneAtStart)
                    }
                }
            }
        }
    }

    /**
     * 继续生成（"继续说"）。
     *
     * 让 AI 接着上一段继续说话——不追加任何用户消息，直接用现有历史
     * （最后一条是 AI 消息）触发 [streamAssistantReply]。API 收到
     * [system, user, ai, ...] 消息序列（结尾是 assistant），模型会自然地
     * 生成一条新的 AI 回复。
     * 用户视角：点击"继续说"→ AI 头像下方出现三点思考动画 → AI 开始说话。
     *
     * 约束：
     * - 正在生成中禁止操作；
     * - 最后一条必须是 ASSISTANT 且 isStreaming=false；
     * - 历史为空时忽略（无上下文可继续）。
     */
    fun continueGeneration() {
        if (isGroup()) return
        if (_isGenerating.value) return
        cancelPendingSend() // 取消 pending 的发送延迟
        val conv = conversation.value ?: return
        val sceneAtStart = conv.scene
        val current = _messages.value
        if (current.isEmpty()) return
        val last = current.last()
        // 防御性：最后一条必须是非 streaming 的 AI 消息（已停止 / 错误 / 完成）
        if (last.role != Role.ASSISTANT) return
        if (last.isStreaming) return

        runStream {
            // 不追加用户消息——直接用现有历史触发流式回复。
            // 模型看到结尾是 assistant 的消息序列，自然生成新的 AI 回复。
            val newHistory = _messages.value
            chatRepository.streamAssistantReply(
                conv,
                newHistory,
                effectiveMemoryStrategy(conv)
            ) { event ->
                handleStreamEvent(event)
                if (event is ChatRepository.Event.CompleteMessage) {
                    markSceneInjectedIfUnchanged(sceneAtStart)
                }
            }
        }
    }

    /**
     * 群聊单条成员消息重新生成（方案十二.5-6：操作只作用于该条消息，不打断当前队列）。
     *
     * 语义：定位该成员消息，删除它及其后的所有消息，再用删除后的群聊历史
     * 以该成员身份重新流式生成一条回复。
     */
    fun regenerateGroupMemberMessage(messageId: String) {
        if (!isGroup()) return
        if (_isGenerating.value) return
        val group = conversation.value ?: return
        val current = _messages.value
        val targetIndex = current.indexOfFirst { it.id == messageId }
        if (targetIndex < 0) return
        val senderId = current[targetIndex].senderId ?: return
        val member = conversationRepository.getConversation(senderId) ?: return
        val newHistory = current.subList(0, targetIndex).toList()
        // 上一版回复原文（含目标消息及之后的消息）：供重说提示词对照，要求换一种表达。
        val previousReplies = current.subList(targetIndex, current.size)
            .filterNot { it.isNotice || it.isThinking }
            .mapNotNull { it.content.takeIf { c -> c.isNotBlank() } }
            .joinToString("\n")
            .takeIf { it.isNotBlank() }
        groupStreamJob?.cancel()
        groupStreamJob = viewModelScope.launch {
            val selfJob = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
            _isGenerating.value = true
            try {
                conversationRepository.replaceMessages(conversationId, newHistory)
                chatRepository.streamGroupMemberReply(
                    member = member,
                    group = group,
                    transcript = newHistory,
                    senderId = senderId,
                    regeneratePreviousReply = previousReplies
                ) { event ->
                    handleStreamEvent(event)
                }
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _errorEvent.value = "重说失败：${t.message ?: "未知错误"}"
            } finally {
                if (groupStreamJob === selfJob) {
                    _isGenerating.value = false
                }
                settleInterruptedStreams()
                notifyIdleIfNoWork()
            }
            notifyIdleIfNoWork()
        }
    }

    /**
     * 启动一次流式会话。集中管理 isGenerating、错误处理、streamJob 生命周期。
     * 入参 block 内部的事件回调（[handleStreamEvent]）在 runStream 协程中
     * 按到达顺序串行执行——天然避免并行写入。
     */
    private fun runStream(block: suspend () -> Unit) {
        // 取消上一轮（如果仍在进行）
        streamJob?.cancel()
        _isGenerating.value = true
        replyRunStart = System.currentTimeMillis()
        replyRunChars = 0
        streamJob = viewModelScope.launch {
            val selfJob = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
            // 防御性：清理可能残留的 streaming 状态
            // 用户停止生成 / 异常退出后，最后一条消息可能仍是 streaming=true，
            // 这种状态会卡住 UI（光标不消失），且与新 run 的消息产生视觉混乱
            cleanupStaleStreamingMessages()
            // Agent 模式（重说 / 继续说）：同样启动前台任务服务保障后台存活
            val agentTaskStarted = conversation.value?.type ==
                com.quiddity.app.data.model.ConversationType.AGENT
            if (agentTaskStarted) {
                com.quiddity.app.active.AgentTaskService.start(
                    com.quiddity.app.di.ServiceLocator.applicationContext
                )
            }
            try {
                block()
            } catch (c: kotlinx.coroutines.CancellationException) {
                // 用户停止 / 页面销毁：不当作错误，收尾交给 finally
                throw c
            } catch (t: Throwable) {
                _errorEvent.value = t.message ?: "未知错误"
                _chatError.value = chatRepository.classify(t)
            } finally {
                if (streamJob === selfJob) {
                    _isGenerating.value = false
                    _toolTraces.value = emptyList()
                    _pendingToolConfirm.value = null
                }
                if (agentTaskStarted) {
                    com.quiddity.app.active.AgentTaskService.stop(
                        com.quiddity.app.di.ServiceLocator.applicationContext
                    )
                }
                settleInterruptedStreams()
                notifyIdleIfNoWork()
            }
        }
    }

    /**
     * 清理残留的 streaming 消息。
     *
     * 每次 run 启动前，把所有 streaming=true 的消息标记为 isStreaming=false
     * （保留内容，不删除），通过单次 [ConversationRepository.replaceMessages] 批量替换，
     * 一次性原子写入——避免对每条残留消息各触发一次磁盘 IO。
     */
    private suspend fun cleanupStaleStreamingMessages() {
        val current = _messages.value
        if (current.none { it.isStreaming }) return
        val cleaned = current.map { msg ->
            if (msg.isStreaming) msg.copy(isStreaming = false) else msg
        }
        // 批量替换：单次 IO，比 N 次 updateMessage 快一个数量级。
        conversationRepository.replaceMessages(conversationId, cleaned)
    }

    /**
     * 收尾残留的流式消息：把 isStreaming=true 的消息落盘为完成态（保留内容），
     * 避免停止生成 / 异常退出后气泡光标永远不消失。
     * 在 NonCancellable 上下文中执行，确保取消过程中也能完成落盘。
     */
    private fun settleInterruptedStreams() {
        viewModelScope.launch {
            withContext(NonCancellable) {
                // 直接读 store 的 StateFlow（同步更新），不依赖 UI 收集协程的调度
                val current = conversationRepository.observeMessages(conversationId).value
                if (current.none { it.isStreaming }) return@withContext
                conversationRepository.replaceMessages(
                    conversationId,
                    current.map { msg ->
                        if (msg.isStreaming) msg.copy(isStreaming = false) else msg
                    }
                )
            }
        }
    }

    /**
     * 串行处理流式事件：每个事件依次 await 持久化完成后再处理下一个，
     * 避免 updateMessage 抢在 appendMessage 之前执行。
     */
    private suspend fun handleStreamEvent(event: ChatRepository.Event) {
        when (event) {
            is ChatRepository.Event.NewMessage -> {
                if (!conversationRepository.appendMessage(event.message)) raiseStorageError()
            }
            is ChatRepository.Event.UpdateMessage -> {
                if (!conversationRepository.updateMessage(event.message)) raiseStorageError()
                // 合并模式（工具轮正文单条化）下，后续轮次正文以 Update 追加到同一条消息，
                // 缓存最新合并结果——Done 固化工具痕迹时必须以最新 content 为准，
                // 否则会用旧对象覆盖持久化消息导致正文丢失（只剩工具痕迹）
                if (event.message.role == Role.ASSISTANT && !event.message.isNotice && !event.message.isThinking) {
                    lastCompletedAiMessage = event.message
                    // 实时段边界：流式过程中工具痕迹立即穿插在正文段间（不依赖 flow 延迟）
                    _activeSegmentEnds.value = event.message.toolSegmentEnds
                }
            }
            is ChatRepository.Event.CompleteMessage -> {
                // 模型偶发在正式回答开头复述思考内容/系统指令：剥离重复前缀，
                // 避免「思考一条 + 回答一条」两条消息内容雷同（防御性去重）
                val target = stripThinkingEcho(event.message)
                // 缓存本次流式最后一条完成消息：Done 事件固化工具痕迹时，
                // _messages 的 flow 更新可能有异步延迟，直接取不到刚完成的消息
                lastCompletedAiMessage = target
                // 1.5.0 延迟输出：加载动画时长 = 累计回复字数 × 每字毫秒数。
                // 流式文字自然显示（MessageBubble 不再逐字停顿），消息保持
                // streaming 状态直到该时长结束（气泡光标 / 群聊头像三点不提前停止）。
                // 思考消息不计入打字延迟（思考单独一条消息，不应拖慢回复动画）
                if (!target.isThinking) {
                    replyRunChars += target.content.length
                }
                val settings = settingsRepository.currentSnapshot()
                if (settings.typingDelayEnabled && settings.typingDelayMsPerChar > 0 &&
                    replyRunStart > 0 && target.content.isNotEmpty()
                ) {
                    val targetDuration = replyRunChars.toLong() * settings.typingDelayMsPerChar
                    val elapsed = System.currentTimeMillis() - replyRunStart
                    val remainder = targetDuration - elapsed
                    if (remainder > 0) {
                        try {
                            kotlinx.coroutines.delay(remainder)
                        } catch (c: kotlinx.coroutines.CancellationException) {
                            // 停止生成：先把消息落盘为完成态，避免加载光标卡住，再继续取消
                            if (!conversationRepository.updateMessage(target)) raiseStorageError()
                            throw c
                        }
                    }
                }
                if (!conversationRepository.updateMessage(target)) raiseStorageError()
                // AI 消息完成时累加 token 用量
                accumulateTokenUsage(target.tokenCount)
            }
            is ChatRepository.Event.ToolUse -> {
                _toolTraces.value = _toolTraces.value + ToolTrace(event.toolName, "running", null)
            }
            is ChatRepository.Event.ToolResult -> {
                val updated = _toolTraces.value.toMutableList()
                val idx = updated.indexOfLast { it.name == event.toolName && it.status == "running" }
                if (idx >= 0) {
                    updated[idx] = ToolTrace(
                        event.toolName,
                        if (event.ok) "done" else "error",
                        event.summary
                    )
                } else {
                    updated += ToolTrace(event.toolName, if (event.ok) "done" else "error", event.summary)
                }
                _toolTraces.value = updated
            }
            is ChatRepository.Event.ToolConfirmBatch -> {
                _pendingToolConfirm.value = PendingToolConfirm(
                    items = event.items,
                    resume = event.resume
                )
            }
            is ChatRepository.Event.AgentRoundEffects -> {
                // 本轮行为追踪结果：固化进最后一条 AI 消息（撤回时按 createdPaths 删除创建物）
                val target = lastCompletedAiMessage?.takeIf {
                    it.role == Role.ASSISTANT && !it.isNotice && !it.isThinking
                } ?: _messages.value.lastOrNull {
                    it.role == Role.ASSISTANT && !it.isNotice && !it.isThinking
                }
                if (target != null) {
                    conversationRepository.updateMessage(
                        target.copy(
                            createdPaths = event.createdPaths,
                            changedItems = event.changedItems
                        )
                    )
                }
            }
            is ChatRepository.Event.Done -> {
                // 工具调用历史固化进最后一条 AI 消息：生成结束后痕迹不再从内存读取，
                // 而是作为消息数据持久化（重新打开会话仍可见，段间渲染与正文分界）
                if (_toolTraces.value.isNotEmpty()) {
                    // 优先从 _messages 按 id 取最新版本（合并模式后续轮次以 Update 追加正文，
                    // 内存缓存可能仍是旧 content——绝不能用旧对象覆盖已持久化的最新正文）
                    val lastId = lastCompletedAiMessage?.id
                    val target = _messages.value.lastOrNull {
                        it.id == lastId && it.role == Role.ASSISTANT && !it.isNotice &&
                            !it.isThinking && it.content.isNotBlank() && it.toolTraces.isEmpty()
                    } ?: lastCompletedAiMessage?.takeIf {
                        it.role == Role.ASSISTANT && !it.isNotice && !it.isThinking &&
                            it.content.isNotBlank() && it.toolTraces.isEmpty()
                    } ?: _messages.value.lastOrNull {
                        it.role == Role.ASSISTANT && !it.isNotice && !it.isThinking &&
                            it.content.isNotBlank() && it.toolTraces.isEmpty()
                    }
                    if (target != null) {
                        conversationRepository.updateMessage(
                            target.copy(
                                toolTraces = _toolTraces.value.map {
                                    MessageToolTrace(it.name, it.status == "done", it.summary)
                                }
                            )
                        )
                    }
                }
                lastCompletedAiMessage = null
                _activeSegmentEnds.value = emptyList()
                _toolTraces.value = emptyList()
                _pendingToolConfirm.value = null
                // 任务结束兜底清理行动通知弹窗（正常流程每一步的完成弹窗会自动消失）
                com.quiddity.app.active.OperationNotifyController.dismiss()
            }
            is ChatRepository.Event.Truncated -> {
                // 回复被截断：不静默吞掉——群聊插入可见提示气泡，私聊弹提示
                if (isGroup()) {
                    val memberName = conversationRepository.observeMessages(conversationId).value
                        .lastOrNull { it.role == Role.ASSISTANT && !it.isNotice && !it.isThinking }
                        ?.senderId
                        ?.let { conversationRepository.getConversation(it)?.persona?.name }
                        ?.takeIf { it.isNotBlank() }
                        ?: "成员"
                    conversationRepository.appendMessage(
                        Message(
                            id = IdGenerator.newId(IdGenerator.Prefix.USER_MESSAGE),
                            conversationId = conversationId,
                            role = Role.SYSTEM,
                            content = "「$memberName」的回复似乎被截断了，可能没说完",
                            timestamp = System.currentTimeMillis(),
                            isNotice = true
                        )
                    )
                } else {
                    _errorEvent.value = "回复可能被截断了，内容没显示完整"
                }
            }
            is ChatRepository.Event.Notice -> {
                // 信息性提示（非错误）：如思考降级 / 未返回思考内容
                _errorEvent.value = event.text
            }
            is ChatRepository.Event.Error -> {
                _toolTraces.value = emptyList()
                _pendingToolConfirm.value = null
                com.quiddity.app.active.OperationNotifyController.dismiss()
                _errorEvent.value = event.throwable.message ?: "未知错误"
                _chatError.value = chatRepository.classify(event.throwable)
            }
        }
    }

    /**
     * 防御性去重：若正式回复开头整段复述了最近一条思考消息的内容
     * （模型回显系统指令/思考文本的常见行为），剥离重复前缀，只保留正式回答。
     */
    private fun stripThinkingEcho(msg: Message): Message {
        if (msg.isThinking || msg.isNotice || msg.content.isBlank()) return msg
        val thinking = _messages.value.asReversed()
            .firstOrNull { it.isThinking }
            ?.content?.trim().orEmpty()
        if (thinking.length < 6) return msg
        val content = msg.content.trimStart()
        if (!content.startsWith(thinking)) return msg
        val rest = content.removePrefix(thinking).trimStart()
        if (rest.isBlank()) return msg
        return msg.copy(
            content = rest,
            tokenCount = com.quiddity.app.util.TokenEstimator.estimate(rest)
        )
    }

    /** 存储写盘失败时上报，避免"显示已发送但实际未落盘"被静默吞掉。 */
    private fun raiseStorageError() {
        _errorEvent.value = "存储写入失败，消息可能未保存"
        _chatError.value = ChatError.Unknown(
            userMessage = "存储写入失败，消息可能未保存",
            cause = null
        )
    }

    /**
     * 流式成功完成一条 AI 消息后，标记场景已注入 LLM。
     *
     * 仅当 [sceneAtStart]（流式开始时的场景）与当前会话场景一致时才标记——
     * 若用户在流式期间修改了场景（updateScene 已重置 sceneInjected=false），
     * 不覆盖其重置，保证新场景在下次对话被重新注入。
     */
    private suspend fun markSceneInjectedIfUnchanged(sceneAtStart: String) {
        val conv = conversation.value ?: return
        if (conv.scene == sceneAtStart && conv.scene.isNotBlank() && !conv.sceneInjected) {
            conversationRepository.updateConversation(conv.copy(sceneInjected = true))
        }
    }

    /** 停止当前生成（取消协程）。 */
    fun stopGeneration() {
        // 群聊停止：模式 A 只停当前成员，排队的顺位递补；模式 B 清空整个队列（方案四.7）
        if (isGroup()) {
            groupStreamJob?.cancel()
            groupStreamJob = null
            val mode = conversation.value?.stopMode ?: QuiddityConstants.GROUP_DEFAULT_STOP_MODE
            if (mode == QuiddityConstants.GROUP_STOP_MODE_B) {
                groupQueueEngine.clear()
            } else {
                groupQueueEngine.removeReplying()
                // 模式 A：只停当前成员，排队的照常顺位递补（方案四.7）
                if (groupQueueEngine.isNotEmpty) {
                    startGroupQueueProcessor()
                }
            }
            syncGroupQueue()
            _isGenerating.value = false
            settleInterruptedStreams()
            notifyIdleIfNoWork()
            return
        }
        streamJob?.cancel()
        streamJob = null
        cancelPendingSend() // 同时取消 pending 的发送延迟
        _toolTraces.value = emptyList()
        _pendingToolConfirm.value = null
        com.quiddity.app.active.OperationNotifyController.dismiss()
        _isGenerating.value = false
        settleInterruptedStreams()
        notifyIdleIfNoWork()
    }

    fun consumeError() {
        _errorEvent.value = null
    }

    /**
     * 消费结构化错误事件。
     * 配合 [chatError] 使用，UI 弹 Toast 后调用此方法清空状态。
     */
    fun consumeChatError() {
        _chatError.value = null
    }

    // ===== 汉堡菜单：会话级设置 =====

    /**
     * 保存 AI 人设到当前会话。
     *
     * 语义：人设字段（name/desired/persona/character/appearance/worldBackground）
     * 任一变化都应清空 `compiledPersona`，由下次开启精调时重新生成。
     * 唯一例外：用户**显式**通过 `compilePersona` 写入时，不应被本方法清空。
     * 通过比较新旧 persona 的关键字段是否变化来判断"用户编辑过"。
     *
     * persona 与 compileEnabled 合并到同一次 [ConversationRepository.updateConversation]
     * 调用中，原子写入，避免分别写入导致 aiAvatarUri 等字段被回滚。
     *
     * @param persona 用户在 PersonaPanel 中编辑后的最新人设
     * @param compileEnabled 是否启用精调（来自 PersonaPanel 开关）
     */
    fun updatePersona(persona: Persona, compileEnabled: Boolean) {
        viewModelScope.launch {
            // 协程内重读会话，避免与并发的其他设置操作互相覆盖（丢失更新竞态）
            val conv = conversation.value ?: return@launch
            // 头部名字框同步规则：标题跟随人设名，除非用户已手动重命名过。
            // - 标题仍是默认"新会话" → 同步为新名
            // - 标题当前等于旧人设名（之前自动同步过）→ 跟随更新为新名
            // - 用户已手动重命名（标题 != 默认 且 != 旧人设名）→ 尊重用户选择，不覆盖
            val newTitle = syncTitleWithPersonaName(
                currentTitle = conv.title,
                oldPersonaName = conv.persona.name,
                newPersonaName = persona.name
            )
            // 人设字段是否已变更（除 compiledPersona 和 aiAvatarUri 外）
            val personaChanged = hasUserEditableFieldsChanged(conv.persona, persona)
            // 已变更 → 清空 compiledPersona 缓存（与 updatePersona 解耦）
            val cleanedPersona = if (personaChanged) {
                persona.copy(compiledPersona = null)
            } else {
                persona
            }
            // persona 与 compileEnabled 原子写入，避免竞态覆盖
            conversationRepository.updateConversation(
                conv.copy(
                    persona = cleanedPersona,
                    title = newTitle,
                    compileEnabled = compileEnabled
                )
            )
        }
    }

    /** 选择角色库角色应用到当前会话：写入 AI 人设 + 用户人设 + 固定记忆 + 角色引用，标题跟随角色名。 */
    fun bindCharacter(character: com.quiddity.app.data.model.Character) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            val newTitle = syncTitleWithPersonaName(
                currentTitle = conv.title,
                oldPersonaName = conv.persona.name,
                newPersonaName = character.persona.name
            )
            conversationRepository.updateConversation(
                conv.copy(
                    persona = character.persona.copy(
                        compiledPersona = null,
                        aiAvatarUri = character.aiAvatarUri ?: character.persona.aiAvatarUri
                    ),
                    userPersona = character.userPersona,
                    memory = character.memory,
                    characterId = character.id,
                    title = newTitle
                )
            )
        }
    }

    /** 清除当前会话的角色引用（保留已写入的人设副本）。 */
    fun clearCharacter() {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            conversationRepository.updateConversation(conv.copy(characterId = null))
        }
    }

    /** 应用非角色库来源的人设（如从私聊会话合成）：只写入 AI 人设 + 用户人设 + 记忆，不写角色引用。 */
    fun bindPersona(
        persona: Persona,
        userPersona: com.quiddity.app.data.model.UserPersona,
        memory: String
    ) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            val newTitle = syncTitleWithPersonaName(
                currentTitle = conv.title,
                oldPersonaName = conv.persona.name,
                newPersonaName = persona.name
            )
            conversationRepository.updateConversation(
                conv.copy(
                    persona = persona.copy(compiledPersona = null),
                    userPersona = userPersona,
                    memory = memory,
                    title = newTitle
                )
            )
        }
    }

    /**
     * 计算头部名字框（会话标题）与人设名的同步结果。
     *
     * 系统性修复"会话内头部名字不随人设名设置而改变"的问题：
     * 旧实现仅在标题为默认"新会话"时同步，首次同步后标题已变为人设名，
     * 后续再改人设名时因标题 != 默认值而不再同步，导致头部名字框停留在旧名。
     *
     * 现规则：只要标题当前等于旧人设名（即此前由自动同步产生）或仍是默认值，
     * 就跟随新名更新；若用户曾手动重命名（标题与旧人设名、默认值都不一致）则保留用户命名。
     *
     * @param currentTitle 当前会话标题
     * @param oldPersonaName 修改前的 AI 人设名
     * @param newPersonaName 修改后的 AI 人设名
     * @return 应写入的标题
     */
    private fun syncTitleWithPersonaName(
        currentTitle: String,
        oldPersonaName: String,
        newPersonaName: String
    ): String {
        // 新名为空：保持原标题（不因清空名字而清空标题）
        if (newPersonaName.isBlank()) return currentTitle
        return when {
            // 旧默认「新会话」或 1.5.0 带编号默认名（新会话 1、2、3…）都可被 AI 名字覆盖
            isDefaultSoloTitle(currentTitle) -> newPersonaName
            currentTitle == oldPersonaName -> newPersonaName
            else -> currentTitle
        }
    }

    /**
     * 1.5.0 私聊默认名判定（方案二.4）：旧默认「新会话」或带编号的「新会话 N」。
     */
    private fun isDefaultSoloTitle(title: String): Boolean =
        title == QuiddityConstants.DEFAULT_CONVERSATION_TITLE ||
            title.startsWith(QuiddityConstants.SOLO_DEFAULT_TITLE_PREFIX + " ")

    /**
     * 判断用户可编辑的人设字段是否发生变化（用于 [updatePersona] 决定是否清空编译缓存）。
     *
     * 不比较 [Persona.name] 和 [Persona.aiAvatarUri]：
     * - name 是用户给 AI 取的称呼，不影响精调结果
     * - aiAvatarUri 由 [setAiAvatarUri] 独立更新
     * - compiledPersona 是缓存字段，由 [compilePersona] 写入
     *
     * 结论：仅更改头像、名字这两项时，无需重新精调（前提是已精调过的）。
     */
    private fun hasUserEditableFieldsChanged(old: Persona, new: Persona): Boolean {
        return old.desired != new.desired ||
            old.persona != new.persona ||
            old.character != new.character ||
            old.appearance != new.appearance ||
            old.worldBackground != new.worldBackground
    }

    /**
     * 解析当前会话使用的模型分级（用于 UI 功能权限控制）。
     *
     * 解析顺序与会话级 API 配置一致：
     * `conv.apiCatalogId` → `settings.activeCatalogId` → catalog 第一条。
     * 未找到配置时默认返回完整级，避免空配置误禁用功能。
     */
    fun resolveCurrentTier(): ApiCatalogManager.ModelTier {
        val conv = conversation.value ?: return ApiCatalogManager.ModelTier.FULL
        val settings = settingsRepository.currentSnapshot()
        val entry = settings.catalog
            .firstOrNull { it.id == conv.apiCatalogId }
            ?: settings.catalog.firstOrNull { it.id == settings.activeCatalogId }
            ?: settings.catalog.firstOrNull()
            ?: return ApiCatalogManager.ModelTier.FULL
        return apiCatalogManager.getModelTier(entry.apiModel, entry.providerId)
    }

    /**
     * 计算当前会话的默认记忆策略：
     * - 会话级显式覆盖（[Conversation.memoryStrategy]）优先；
     * - 完整级模型且已有压缩记忆 → 工具模式（read_memory 按需检索，不再每轮重读压缩摘要）；
     * - 其余 → 随身带。
     */
    private fun effectiveMemoryStrategy(conv: Conversation): String? {
        conv.memoryStrategy?.let { return it }
        return if (conv.compressedMemory.isNotBlank() &&
            resolveCurrentTier() == ApiCatalogManager.ModelTier.FULL
        ) {
            QuiddityConstants.MEMORY_STRATEGY_TOOL
        } else {
            QuiddityConstants.MEMORY_STRATEGY_CARRY
        }
    }

    fun updateUserPersona(userPersona: UserPersona) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            conversationRepository.updateConversation(conv.copy(userPersona = userPersona))
        }
    }

    fun updateScene(scene: String) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            // 场景修改后重置 sceneInjected=false，下次对话将新场景注入系统提示词
            conversationRepository.updateConversation(
                conv.copy(scene = scene, sceneInjected = false)
            )
        }
    }

    fun updateMemory(memory: String) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            conversationRepository.updateConversation(conv.copy(memory = memory))
        }
    }

    /**
     * 人设精调编译状态（UI 据此显示加载动画 / 错误反馈）。
     *
     * - Idle：未在编译
     * - Compiling：编译进行中（UI 显示 CircularProgressIndicator，保存按钮禁用）
     * - 由 [compilePersona] 维护生命周期，编译结束后自动回到 Idle
     */
    private val _isCompiling = MutableStateFlow(false)
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
    suspend fun compilePersona(persona: Persona, maxOutputTokens: Int): String {
        _isCompiling.value = true
        try {
            val conv = conversation.value ?: throw IllegalStateException("会话不存在")
            // 先把最新 persona 写入会话，确保 repository 读到的是最新字段
            val newTitle = syncTitleWithPersonaName(
                currentTitle = conv.title,
                oldPersonaName = conv.persona.name,
                newPersonaName = persona.name
            )
            val updatedConv = conv.copy(persona = persona, title = newTitle)
            conversationRepository.updateConversation(updatedConv)
            // 调用 AI 编译，传入 token 上限约束
            return chatRepository.compilePersona(updatedConv, maxOutputTokens)
        } finally {
            _isCompiling.value = false
        }
    }

    /**
     * 快速设定：基于用户描述一次性生成 AI 人设 / 用户人设 / 场景设置 / 记忆设置。
     *
     * 流程：
     * 1. 接收 UI 选定的档位（[tier]），由 UI 控制；
     * 2. 调用 [ChatRepository.quickSetup] 让 AI 生成结构化角色卡文本；
     * 3. 成功：返回原始文本，由 UI 展示给用户预览/编辑；
     * 4. 失败：抛异常给调用方（UI 据此显示「生成失败 / API 未配置」）。
     *
     * 本方法不直接写入人设字段——用户在预览弹窗确认后才由 [applyQuickSetupResult] 落盘。
     *
     * @param userDescription 用户的人设描述（可能十分模糊）
     * @param tier UI 选定的档位（受当前模型等级层级解锁）
     * @return LLM 返回的结构化文本（UI 可编辑后传给 [applyQuickSetupResult]）
     */
    suspend fun quickSetupGenerate(
        userDescription: String,
        tier: com.quiddity.app.domain.QuickSetupTier
    ): String {
        val conv = conversation.value ?: throw IllegalStateException("会话不存在")
        return chatRepository.quickSetup(conv, userDescription, tier)
    }

    /**
     * 将快速设定结果（用户可能已编辑）解析并写入会话的人设/用户人设/场景/记忆字段。
     *
     * - 解析由 [QuickSetupPrompt.parseQuickSetupResult] 完成，缺失字段为空串；
     * - 性别字段为空时回退为「暂不设置」；
     * - 直接覆盖现有 persona / userPersona / scene / memory（由 UI 在调用前确认）；
     * - 场景被覆盖后重置 sceneInjected=false，下次对话把新场景注入系统提示词；
     * - 场景 / 世界类型提示由 ChatScreen 根据会话数据实时派生渲染（不落库为消息，
     *   不发给 LLM、不参与压缩、不导出），用户直观看到快速设定生效后的场景状态。
     */
    fun applyQuickSetupResult(
        rawText: String,
        tier: com.quiddity.app.domain.QuickSetupTier
    ) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                val conv = conversation.value ?: return@withContext
                val result = com.quiddity.app.domain.QuickSetupPrompt
                    .parseQuickSetupResult(rawText, tier)
                val newTitle = syncTitleWithPersonaName(
                    currentTitle = conv.title,
                    oldPersonaName = conv.persona.name,
                    newPersonaName = result.persona.name
                )
                conversationRepository.updateConversation(
                    conv.copy(
                        persona = result.persona,
                        userPersona = result.userPersona,
                        scene = result.scene,
                        memory = result.memory,
                        title = newTitle,
                        sceneInjected = false
                    )
                )
            }
        }
    }

    // ===== 快速设定草稿 =====
    private var quickSetupDraftJob: Job? = null

    /**
     * 更新快速设定描述草稿：500ms 防抖落盘，供面板关闭后回看/重新生成。
     */
    fun updateQuickSetupDraft(draft: String) {
        quickSetupDraftJob?.cancel()
        quickSetupDraftJob = viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            persistQuickSetupDraft(draft)
        }
    }

    private fun persistQuickSetupDraft(draft: String) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                val conv = conversation.value ?: return@withContext
                if (conv.quickSetupDraft == draft) return@withContext
                conversationRepository.updateConversation(conv.copy(quickSetupDraft = draft))
            }
        }
    }

    /**
     * 设置快速设定的独立采样温度（全局设置，0～2）。
     * 温度越高，每次生成的人设发散性越强，避免用户总是拿到同一个人设。
     */
    fun updateQuickSetupTemperature(value: Double) {
        viewModelScope.launch {
            settingsRepository.setQuickSetupTemperature(value)
        }
    }

    fun renameConversation(newTitle: String) {
        viewModelScope.launch {
            conversationRepository.renameConversation(conversationId, newTitle)
        }
    }

    // 清空当前会话消息入口已下线——删除会话/消息的唯一入口是主页长按多选模式。

    /**
     * 撤回指定消息。
     *
     * 语义：
     * - 用户点击任意 USER 气泡触发"撤回"操作时，按被点击消息的位置区分行为：
     *   1. **被点击的消息是会话最后一条** → 仅删除该条消息本身；
     *   2. **被点击的消息非最后一条** → 删除该条及其后所有消息（含 AI 回复、
     *      "重说"产生的新 AI、后续轮次等）。
     * - 统一实现：删除 [messageId] 所在 index（含）及之后所有消息。
     *   - 若 target 是最后一条：subList(0, size-1) → 仅删除该条；
     *   - 若 target 非最后一条：subList(0, targetIndex) → 删除该条及其后所有。
     *
     * 不撤回的场景：
     * - 找不到对应 messageId（已被删除或参数错误）；
     * - 正在生成中（避免破坏流式状态；让用户先 stop 再撤回）。
     *
     * UI 协调：父组件（ChatScreen）维护 `withdrawTargetId` 状态确保一次只显示一个
     * 撤回提示；点击第二个气泡的"撤回"时，第一个气泡的撤回 UI 由 AnimatedVisibility
     * 的 exit 过渡平滑淡出（详见 MessageBubble.kt 的 isWithdrawing 参数说明）。
     *
     * @param messageId 被点击撤回的消息 ID
     */
    fun withdrawMessage(messageId: String) {
        if (_isGenerating.value) return
        val current = _messages.value
        if (current.isEmpty()) return
        val targetIndex = current.indexOfFirst { it.id == messageId }
        if (targetIndex < 0) return
        val target = current[targetIndex]
        val newHistory = current.subList(0, targetIndex).toList()
        viewModelScope.launch {
            conversationRepository.replaceMessages(conversationId, newHistory)
        }
        // 撤回后提供「重新编辑」入口：缓存原文/图片/OCR 文本，编辑后作为新消息重发
        if (target.role == Role.USER && (target.content.isNotBlank() || target.imageUri != null)) {
            _pendingReedit.value = PendingReedit(target.content, target.ocrText, target.imageUri)
        }
    }

    // ===== Agent 模式撤回：确认弹窗 + 创建物删除（1.6.0 架构重整） =====

    /**
     * Agent 撤回提案：目标消息 + 本轮创建/更改项目清单。
     * [createdPaths] 为本轮创建的文件（确认后删除）；[changedItems] 为更改项摘要（仅提示）。
     */
    data class WithdrawProposal(
        val targetId: String,
        val createdPaths: List<String>,
        val changedItems: List<String>
    ) {
        /** 本轮是否有创建/更改项目（决定弹窗文案是否带项目清单）。 */
        val hasEffects: Boolean get() = createdPaths.isNotEmpty() || changedItems.isNotEmpty()
    }

    /** Agent 撤回确认弹窗状态（null = 未弹窗）。 */
    private val _pendingWithdraw = MutableStateFlow<WithdrawProposal?>(null)
    val pendingWithdraw: StateFlow<WithdrawProposal?> = _pendingWithdraw.asStateFlow()

    /**
     * Agent 模式撤回入口：先收集本轮创建/更改项目，弹出确认框。
     *
     * 语义（与私聊撤回一致）：删除目标消息（含）之后的所有消息；
     * 确认后删除本轮创建的文件（createdPaths），更改项仅提示不可自动恢复。
     */
    fun requestAgentWithdraw(messageId: String) {
        if (_isGenerating.value) return
        val current = _messages.value
        if (current.isEmpty()) return
        val targetIndex = current.indexOfFirst { it.id == messageId }
        if (targetIndex < 0) return
        // 收集目标消息（含）之后所有 AI 消息上固化的本轮创建/更改清单
        val created = current.subList(targetIndex, current.size)
            .flatMap { it.createdPaths }
            .distinct()
        val changed = current.subList(targetIndex, current.size)
            .flatMap { it.changedItems }
            .distinct()
        _pendingWithdraw.value = WithdrawProposal(
            targetId = messageId,
            createdPaths = created,
            changedItems = changed
        )
    }

    /** Agent 撤回确认：删除创建物 + 删除消息，随后提供「重新编辑」入口。 */
    fun confirmAgentWithdraw() {
        val proposal = _pendingWithdraw.value ?: return
        _pendingWithdraw.value = null
        val current = _messages.value
        val targetIndex = current.indexOfFirst { it.id == proposal.targetId }
        if (targetIndex < 0) return
        val target = current[targetIndex]
        val newHistory = current.subList(0, targetIndex).toList()
        viewModelScope.launch {
            val executors = com.quiddity.app.di.ServiceLocator.agentExecutors
            // 1. 删除本轮创建的文件（rm -rf，仅删确实存在的路径；失败不阻塞消息撤回）
            if (proposal.createdPaths.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    executors.deleteFilesForWithdraw(proposal.createdPaths)
                }
            }
            // 2. 恢复可逆的应用状态（停用 → 启用 / 启用 → 停用；不可逆项仅提示）
            proposal.changedItems.forEach { item ->
                val enable = item.startsWith("停用 ")
                val disable = item.startsWith("启用 ")
                if (enable || disable) {
                    val pkg = item.substringAfter(if (enable) "停用 " else "启用 ")
                        .substringBefore("（")
                        .trim()
                    if (pkg.isNotBlank()) {
                        withContext(Dispatchers.IO) {
                            executors.revertAppEnabledState(pkg, enable)
                        }
                    }
                }
            }
            // 3. 删除消息（含目标）及其后所有
            conversationRepository.replaceMessages(conversationId, newHistory)
        }
        // 撤回后提供「重新编辑」入口（与私聊一致）
        if (target.role == Role.USER && (target.content.isNotBlank() || target.imageUri != null)) {
            _pendingReedit.value = PendingReedit(target.content, target.ocrText, target.imageUri)
        }
    }

    /** Agent 撤回取消：关闭确认弹窗。 */
    fun cancelAgentWithdraw() {
        _pendingWithdraw.value = null
    }

    /**
     * 「重新编辑」保存：以撤回前的原文为基础，把编辑后的内容作为新消息发出。
     *
     * - 保留被撤回消息的图片与 OCR 文本（纯文本编辑不受影响）；
     * - 复用 [sendMessage] 完整发送链路：私聊/Agent 触发 AI 流式回复，群聊只追加用户消息；
     * - 成功后清空「重新编辑」入口。
     */
    fun resendReedit(newContent: String) {
        val pending = _pendingReedit.value ?: return
        if (newContent.isBlank() && pending.imageUri.isNullOrBlank()) return
        _pendingReedit.value = null
        sendMessage(newContent, ocrText = pending.ocrText, imageUri = pending.imageUri)
    }

    /** 关闭「重新编辑」入口（用户不打算重新编辑被撤回的消息）。 */
    fun clearPendingReedit() {
        _pendingReedit.value = null
    }

    /**
     * 批量删除指定消息（多选模式下使用）。
     *
     * - 仅删除 [messageIds] 中的消息，不影响其他消息；
     * - 不触发重说/继续说等连锁逻辑；
     * - 删除后直接替换消息列表。
     */
    fun deleteMessages(messageIds: Set<String>) {
        if (messageIds.isEmpty()) return
        val current = _messages.value
        if (current.isEmpty()) return
        val newMessages = current.filterNot { it.id in messageIds }
        if (newMessages.size == current.size) return
        viewModelScope.launch {
            conversationRepository.replaceMessages(conversationId, newMessages)
        }
    }

    /** 清空当前会话的人设、用户、场景、记忆设置（保留会话与消息记录）。 */
    fun clearConversationSettings() {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            conversationRepository.updateConversation(
                conv.copy(
                    persona = Persona.Empty,
                    userPersona = UserPersona.Empty,
                    scene = "",
                    sceneInjected = false,
                    memory = "",
                    compileEnabled = false
                )
            )
        }
    }

    /**
     * 清空当前会话的所有消息（保留会话与所有设置）。
     *
     * 行为：
     * - 替换当前会话的消息列表为空列表
     * - 重置 compressedMemory / memoryIndex 与 lastCompressedAtRound，下次对话重新开始
     * - 保留 AI 人设、用户人设、场景、记忆、壁纸、API 配置等所有会话级设置
     *
     * 不可恢复，需在 UI 层做二次确认。
     */
    fun clearConversationMessages() {
        if (_isGenerating.value) return
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            val isGroupConv = conv.type == ConversationType.GROUP
            conversationRepository.replaceMessages(conversationId, emptyList())
            conversationRepository.updateConversation(
                if (isGroupConv) {
                    // 群聊：清除聊天记录并重置群聊小本本
                    conv.copy(groupMemory = "")
                } else {
                    conv.copy(
                        compressedMemory = "",
                        memoryIndex = "",
                        lastCompressedAtRound = 0
                    )
                }
            )
        }
    }

    /** 删除当前会话（含全部消息与设置），供群聊菜单「删除该会话」使用。 */
    fun deleteCurrentConversation() {
        val id = conversationId
        groupStreamJob?.cancel()
        groupStreamJob = null
        streamJob?.cancel()
        streamJob = null
        cancelPendingSend()
        viewModelScope.launch {
            // NonCancellable：页面即将返回销毁 ViewModel，删除必须完整落盘后再释放
            withContext(NonCancellable) {
                conversationRepository.deleteConversation(id)
            }
        }
    }

    /** 导出当前会话的角色卡（覆盖「人设」一栏全部设置，含快速设定内容）。 */
    fun exportPersonaCard(): PersonaCard? {
        val conv = conversation.value ?: return null
        return PersonaCard(
            schemaVersion = 1,
            exportedAt = System.currentTimeMillis(),
            persona = conv.persona,
            userPersona = conv.userPersona,
            scene = conv.scene,
            memory = conv.memory,
            quickSetupDraft = conv.quickSetupDraft
        )
    }

    /** 导入角色卡到当前会话（覆盖「人设」一栏全部设置，含快速设定内容）。 */
    fun importPersonaCard(card: PersonaCard) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            // 场景可能随角色卡变更，重置 sceneInjected 让下次对话重新注入
            conversationRepository.updateConversation(
                conv.copy(
                    persona = card.persona,
                    userPersona = card.userPersona,
                    scene = card.scene,
                    sceneInjected = false,
                    memory = card.memory,
                    quickSetupDraft = card.quickSetupDraft
                )
            )
        }
    }

    /** 设置 AI 头像（会话级）。 */
    fun setAiAvatarUri(uri: String?) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            conversationRepository.updateConversation(
                conv.copy(persona = conv.persona.copy(aiAvatarUri = uri))
            )
        }
    }

    /**
     * 设置会话专属壁纸 URI。
     *
     * - 持久化到 conversation.wallpaperUri（DataStore 之外，存于 conversations.json）
     * - 仅当前会话生效——其他会话的 wallpaperUri 独立存储
     * - 传 null 清除壁纸
     *
     * 注：URI 来自 SAF PickVisualMedia 返回的 content URI；本类不缓存，确保重启后
     * Coil 能直接重新加载。
     */
    fun setWallpaperUri(uri: String?) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            conversationRepository.updateConversation(conv.copy(wallpaperUri = uri))
        }
    }

    /**
     * 设置壁纸暗化程度 0.0f - 1.0f。
     * 数值越大壁纸越暗，文字可读性越好但壁纸本身被遮挡。
     */
    fun setWallpaperDarken(value: Float) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            val clamped = value.coerceIn(
                QuiddityConstants.MIN_WALLPAPER_DARKEN,
                QuiddityConstants.MAX_WALLPAPER_DARKEN
            )
            conversationRepository.updateConversation(conv.copy(wallpaperDarken = clamped))
        }
    }

    /**
     * 从 Markdown / 纯文本导入对话记录到当前会话。
     *
     * 行为：
     * - 解析 [text] 为 [com.quiddity.app.util.ConversationCodec.ImportResult]
     * - 替换当前会话的所有消息为新解析的 messages（覆盖式导入）
     * - 若解析到角色卡信息，更新当前会话的 persona / userPersona / scene / memory / quickSetupDraft
     * - 若解析到会话标题且当前会话仍是默认标题，更新会话标题
     *
     * 不影响：
     * - 当前会话的壁纸、API 配置、compileEnabled 等会话级设置
     * - 其他会话
     *
     * @param text Markdown 或纯文本内容
     * @return Result.success(Unit) 或 Result.failure(Throwable)
     */
    fun importConversationFromText(text: String): Result<Unit> {
        return runCatching {
            val result = com.quiddity.app.util.ConversationCodec.importConversation(
                content = text,
                targetConversationId = conversationId
            )
            viewModelScope.launch {
                // 协程内重读会话，避免覆盖并发修改（丢失更新竞态）
                val conv = conversation.value ?: return@launch
                // 1. 替换消息列表（覆盖式导入）
                conversationRepository.replaceMessages(conversationId, result.messages)

                // 2. 更新角色卡信息（仅在解析到非空内容时更新对应字段）
                val updatedPersona = if (
                    result.persona != Persona.Empty ||
                    result.userPersona != UserPersona.Empty
                ) {
                    conv.persona.copy(
                        // 仅在解析到非空字段时覆盖，避免清空用户已设置的人设
                        name = result.persona.name.ifBlank { conv.persona.name },
                        persona = result.persona.persona.ifBlank { conv.persona.persona },
                        character = result.persona.character.ifBlank { conv.persona.character },
                        appearance = result.persona.appearance.ifBlank { conv.persona.appearance },
                        worldBackground = result.persona.worldBackground.ifBlank { conv.persona.worldBackground },
                        desired = result.persona.desired.ifBlank { conv.persona.desired }
                    )
                } else {
                    conv.persona
                }
                val updatedUserPersona = if (result.userPersona != UserPersona.Empty) {
                    conv.userPersona.copy(
                        name = result.userPersona.name.ifBlank { conv.userPersona.name },
                        identity = result.userPersona.identity.ifBlank { conv.userPersona.identity },
                        gender = result.userPersona.gender.ifBlank { conv.userPersona.gender },
                        age = result.userPersona.age.ifBlank { conv.userPersona.age },
                        appearance = result.userPersona.appearance.ifBlank { conv.userPersona.appearance }
                    )
                } else {
                    conv.userPersona
                }
                val updatedScene = result.scene.ifBlank { conv.scene }
                val updatedMemory = result.memory.ifBlank { conv.memory }
                // 场景变更时重置 sceneInjected，下次对话重新注入新场景
                val sceneChanged = updatedScene != conv.scene

                // 3. 标题更新：仅当当前是默认标题且解析到非默认标题时
                val updatedTitle = if (
                    isDefaultSoloTitle(conv.title) &&
                    result.title.isNotBlank() &&
                    result.title != QuiddityConstants.DEFAULT_CONVERSATION_TITLE
                ) {
                    result.title
                } else {
                    conv.title
                }

                conversationRepository.updateConversation(
                    conv.copy(
                        title = updatedTitle,
                        persona = updatedPersona,
                        userPersona = updatedUserPersona,
                        scene = updatedScene,
                        sceneInjected = if (sceneChanged) false else conv.sceneInjected,
                        memory = updatedMemory
                    )
                )
            }
        }
    }

    /**
     * 导出当前会话为 Markdown / 纯文本格式。
     *
     * 与 [exportPersonaCard] 的差异：
     * - 本方法导出消息记录（角色卡 + 所有消息）
     * - exportPersonaCard 仅导出角色卡（无消息）
     *
     * @param format 目标格式（MARKDOWN 或 TEXT）；JSON 格式请使用 ExportPayload + DataPorter
     * @return 格式化后的字符串
     */
    fun exportConversationAsText(
        format: com.quiddity.app.util.ConversationCodec.Format
    ): String? {
        val conv = conversation.value ?: return null
        val msgs = _messages.value
        return com.quiddity.app.util.ConversationCodec.exportConversation(
            conversation = conv,
            messages = msgs,
            format = format
        )
    }

    // ===== Per-API Token 计数 + 模型切换检测 =====

    /**
     * 检测 API/模型是否切换，若切换则：
     * - 重置 sessionTokenUsed 为 0
     * - 更新 tokenCountApiId 为当前 API
     * - 重置 contextLimit 为新模型分级的默认值
     * - 更新 lastUsedModel
     *
     * 应在每次发送消息前调用。
     */
    private suspend fun checkAndUpdateModelContext() {
        val conv = conversation.value ?: return
        val settings = settingsRepository.currentSnapshot()
        val entry = settings.catalog
            .firstOrNull { it.id == conv.apiCatalogId }
            ?: settings.catalog.firstOrNull { it.id == settings.activeCatalogId }
            ?: return
        applyEntrySelection(conv, entry, explicit = false)
    }

    /**
     * 累加 Token 用量到会话。
     * 在 AI 消息完成时调用，将消息的 tokenCount 加到 sessionTokenUsed。
     */
    private suspend fun accumulateTokenUsage(tokenCount: Int) {
        if (tokenCount <= 0) return
        val conv = conversation.value ?: return
        conversationRepository.updateConversation(
            conv.copy(sessionTokenUsed = conv.sessionTokenUsed + tokenCount)
        )
    }

    // ===== 会话级上下文记忆轮数设置 =====

    /**
     * 设置当前会话的上下文记忆轮数（仅影响当前会话）。
     *
     * 当 memoryBankEnabled 时，压缩轮数自动跟随上下文记忆轮数同步调整，
     * 满足"默认与当前模型的上下文记忆轮数一样，跟上下文记忆轮数一起变"的需求。
     * 用户可单独调整压缩轮数（[updateMemoryBankRounds]），但下次调整记忆轮数时
     * 仍会再次同步。
     */
    fun updateContextLimit(limit: Int) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            val clamped = limit.coerceIn(
                QuiddityConstants.MIN_CONTEXT_LIMIT,
                QuiddityConstants.MAX_CONTEXT_LIMIT
            )
            val newConv = if (conv.memoryBankEnabled) {
                // 同步压缩轮数：跟随上下文记忆轮数，但限制在合法范围内
                val syncRounds = clamped.coerceIn(
                    QuiddityConstants.MIN_MEMORY_BANK_ROUNDS,
                    QuiddityConstants.MAX_MEMORY_BANK_ROUNDS
                )
                conv.copy(contextLimit = clamped, memoryBankRounds = syncRounds)
            } else {
                conv.copy(contextLimit = clamped)
            }
            conversationRepository.updateConversation(newConv)
        }
    }

    // ===== 会话级采样温度 / 官方联网搜索（模型配置） =====

    /**
     * 设置当前会话的采样温度覆盖（0～2；null = 跟随全局默认）。
     * 官方文档：DeepSeek 思考模式下 temperature 不生效。
     */
    fun updateTemperature(value: Double?) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            val clamped = value?.coerceIn(
                QuiddityConstants.MIN_TEMPERATURE,
                QuiddityConstants.MAX_TEMPERATURE
            )
            conversationRepository.updateConversation(conv.copy(temperature = clamped))
        }
    }

    /**
     * 设置当前会话的 DeepSeek 官方服务端联网搜索开关。
     * 开启仅代表用户意愿；实际路由由 ChatRepository 按当前 API 配置能力判定。
     */
    fun updateWebSearchEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            conversationRepository.updateConversation(conv.copy(webSearchEnabled = enabled))
        }
    }

    /** 设置当前会话的 DeepSeek 思考开关。 */
    fun updateThinkingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            conversationRepository.updateConversation(conv.copy(thinkingEnabled = enabled))
        }
    }

    /** 设置当前会话的思考深度（浅 / 深）。 */
    fun updateThinkingDepth(depth: String) {
        val safe = when (depth) {
            QuiddityConstants.THINKING_DEPTH_DEEP -> depth
            else -> QuiddityConstants.THINKING_DEPTH_SHALLOW
        }
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            if (conv.thinkingDepth == safe) return@launch
            conversationRepository.updateConversation(conv.copy(thinkingDepth = safe))
        }
    }

    // ===== 群聊设置（方案十：群名称 / 上下文条数 N / 成员管理 / 停止模式） =====

    /** 设置群聊上下文条数 N（范围 1～200）。 */
    fun updateGroupContextLimit(limit: Int) {
        viewModelScope.launch {
            val group = conversation.value ?: return@launch
            if (group.type != ConversationType.GROUP) return@launch
            val clamped = limit.coerceIn(
                QuiddityConstants.GROUP_MIN_CONTEXT_LIMIT,
                QuiddityConstants.GROUP_MAX_CONTEXT_LIMIT
            )
            conversationRepository.updateConversation(group.copy(groupContextLimit = clamped))
        }
    }

    /** 切换群聊停止模式（A=只停当前 / B=清空队列）。 */
    fun updateGroupStopMode(mode: String) {
        viewModelScope.launch {
            val group = conversation.value ?: return@launch
            if (group.type != ConversationType.GROUP) return@launch
            conversationRepository.updateConversation(group.copy(stopMode = mode))
        }
    }

    /**
     * 设置群聊背景 / 场景（合并设置，注入所有成员的回复提示词）。
     *
     * @param mode [com.quiddity.app.util.QuiddityConstants.GROUP_BACKGROUND_MODE_*] 之一，
     *   决定文本注入为【群聊背景】还是【群聊场景】节。
     */
    fun updateGroupBackground(text: String, mode: String) {
        viewModelScope.launch {
            val group = conversation.value ?: return@launch
            if (group.type != ConversationType.GROUP) return@launch
            conversationRepository.updateConversation(
                group.copy(groupBackground = text.trim(), groupBackgroundMode = mode)
            )
        }
    }

    /**
     * 添加群聊成员（方案十.5 + 需求）：逐个校验（用户名 / AI 名 / API 测试），
     * 通过的角色加入（头像栏/成员列表显示），未通过的返回 (成员id, 原因) 列表，
     * 供弹窗按成员重试或配置 API；最多 3 个。
     */
    fun addGroupMembers(ids: List<String>, onDone: (Result<Unit>, List<Pair<String, String>>) -> Unit) {
        viewModelScope.launch {
            val group = conversation.value ?: return@launch
            if (group.type != ConversationType.GROUP) return@launch
            val current = group.memberConversationIds
            val newIds = ids.filter { it !in current }
            val failures = mutableListOf<Pair<String, String>>()
            val passed = mutableListOf<String>()
            for (id in newIds) {
                val member = conversationRepository.getConversation(id) ?: continue
                val result = conversationRepository.validateGroupMember(member)
                if (result.isFailure) {
                    val name = member.persona.name.ifBlank { member.title }
                    failures += id to "${result.exceptionOrNull()?.message ?: "校验失败"}（$name）"
                } else {
                    passed += id
                }
            }
            if (passed.isEmpty()) {
                onDone(
                    Result.failure(IllegalStateException(
                        failures.joinToString("\n") { (_, reason) -> reason }
                    )),
                    failures
                )
                return@launch
            }
            val target = (current + passed).distinct()
            if (target.size > QuiddityConstants.GROUP_MAX_MEMBERS) {
                onDone(
                    Result.failure(IllegalStateException("群聊成员最多 ${QuiddityConstants.GROUP_MAX_MEMBERS} 个")),
                    failures
                )
                return@launch
            }
            conversationRepository.updateConversation(group.copy(memberConversationIds = target))
            onDone(Result.success(Unit), failures)
        }
    }

    /**
     * 为群聊成员（私聊会话）设置模型配置条目（null = 使用全局激活配置）。
     */
    fun setMemberApi(memberId: String, catalogId: String?) {
        viewModelScope.launch {
            val member = conversationRepository.getConversation(memberId) ?: return@launch
            conversationRepository.updateConversation(member.copy(apiCatalogId = catalogId))
        }
    }

    /** 移除群聊成员（踢出，方案十.4：最少 1 个；历史消息气泡保留）。 */
    fun removeGroupMember(memberId: String) {
        viewModelScope.launch {
            val group = conversation.value ?: return@launch
            if (group.type != ConversationType.GROUP) return@launch
            if (group.memberConversationIds.size <= 1) {
                _errorEvent.value = "群聊至少保留 1 个成员"
                return@launch
            }
            conversationRepository.updateConversation(
                group.copy(memberConversationIds = group.memberConversationIds - memberId)
            )
        }
    }

    /**
     * 重置上下文记忆轮数为当前模型分级的默认值。
     *
     * 当 memoryBankEnabled 时，压缩轮数同步跟随重置后的上下文记忆轮数。
     */
    fun resetContextLimitToTierDefault() {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            val tier = resolveCurrentTier()
            val defaultLimit = apiCatalogManager.defaultContextLimitForTier(tier)
            val newConv = if (conv.memoryBankEnabled) {
                val syncRounds = defaultLimit.coerceIn(
                    QuiddityConstants.MIN_MEMORY_BANK_ROUNDS,
                    QuiddityConstants.MAX_MEMORY_BANK_ROUNDS
                )
                conv.copy(contextLimit = defaultLimit, memoryBankRounds = syncRounds)
            } else {
                conv.copy(contextLimit = defaultLimit)
            }
            conversationRepository.updateConversation(newConv)
        }
    }

    // ===== 记忆库设置 =====

    /**
     * 开启/关闭记忆库（会话级设置，仅影响当前会话）。
     *
     * 开启时自动把压缩轮数同步为当前上下文记忆轮数（满足"默认与上下文记忆轮数一样"）；
     * 关闭时保留原值，便于下次开启时恢复用户偏好。
     */
    fun updateMemoryBankEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            val newConv = if (enabled) {
                val syncRounds = conv.contextLimit.coerceIn(
                    QuiddityConstants.MIN_MEMORY_BANK_ROUNDS,
                    QuiddityConstants.MAX_MEMORY_BANK_ROUNDS
                )
                conv.copy(memoryBankEnabled = true, memoryBankRounds = syncRounds)
            } else {
                conv.copy(memoryBankEnabled = false)
            }
            conversationRepository.updateConversation(newConv)
        }
    }

    /**
     * 设置记忆库压缩触发轮数（会话级设置）。
     */
    fun updateMemoryBankRounds(rounds: Int) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            val clamped = rounds.coerceIn(
                QuiddityConstants.MIN_MEMORY_BANK_ROUNDS,
                QuiddityConstants.MAX_MEMORY_BANK_ROUNDS
            )
            conversationRepository.updateConversation(conv.copy(memoryBankRounds = clamped))
        }
    }

    // ===== 发送延迟 =====

    /** 当前输入框文本（用于发送延迟检测）。 */
    private val _inputBarText = MutableStateFlow("")
    val inputBarText: StateFlow<String> = _inputBarText.asStateFlow()

    /** 发送延迟计时器。 */
    private var sendDelayJob: Job? = null

    /** 输入框最后一次编辑时间（含退格，用于发送延迟防抖重计时）。 */
    private var lastInputEditAt = 0L

    /**
     * 更新输入框文本状态（由 ChatInputBar 调用）。
     * 用于发送延迟检测：记录每次编辑（含退格），防抖重计时。
     */
    fun updateInputText(text: String) {
        if (_inputBarText.value == text) return
        _inputBarText.value = text
        lastInputEditAt = System.currentTimeMillis()
    }

    /**
     * 取消待发送的延迟请求（用户在等待期间继续输入时调用）。
     */
    fun cancelPendingSend() {
        sendDelayJob?.cancel()
        sendDelayJob = null
    }

    // ===== 消息改写 =====

    /**
     * 改写最后一条 AI 消息。
     *
     * 用户单击 AI 最后一条消息 → 弹出改写输入框 → 输入新内容 → 保存
     * - 直接替换原消息内容（不保留历史）
     * - 不触发重新生成（仅替换文本）
     *
     * @param messageId 要改写的消息 ID
     * @param newContent 新内容
     */
    fun rewriteMessage(messageId: String, newContent: String) {
        if (newContent.isBlank()) return
        viewModelScope.launch {
            val msg = _messages.value.firstOrNull { it.id == messageId } ?: return@launch
            conversationRepository.updateMessage(
                msg.copy(content = newContent, isStreaming = false)
            )
        }
    }

    // ===== 设置会话 API（会话级，仅影响当前会话） =====

    /**
     * 设置当前会话使用的 API 条目 ID（会话级设置）。
     * 切换 API 时自动重置上下文记忆轮数为新模型分级的默认值。
     */
    fun setConversationApi(catalogId: String) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            val settings = settingsRepository.currentSnapshot()
            val entry = settings.catalog.firstOrNull { it.id == catalogId } ?: return@launch
            applyEntrySelection(conv, entry, explicit = true)
        }
    }

    /**
     * 统一"切换 API/模型后的会话重置"规则（发送前自动检测与用户显式切换共用）：
     * - token 用量统计：任何切换（或首次使用）都清零并记录新 API id；
     * - 上下文记忆轮数：用户显式切换，或模型发生变更（含首次使用）→ 重置为模型分级默认值；
     *   仅 API 条目变更但模型相同 → 保留用户手动调整过的轮数；
     * - 记忆库压缩轮数：开启记忆库时随上下文记忆轮数同步。
     *
     * @param explicit true = 用户在会话内显式选择模型配置（无条件重置上下文轮数）
     */
    private suspend fun applyEntrySelection(
        conv: Conversation,
        entry: com.quiddity.app.data.model.ApiCatalogEntry,
        explicit: Boolean
    ) {
        val tier = apiCatalogManager.getModelTier(entry.apiModel, entry.providerId)
        val tierDefaultContext = apiCatalogManager.defaultContextLimitForTier(tier)
        val modelChanged = conv.lastUsedModel != null && conv.lastUsedModel != entry.apiModel
        val apiChanged = conv.tokenCountApiId != null && conv.tokenCountApiId != entry.id

        if (!explicit && !apiChanged && !modelChanged && conv.tokenCountApiId != null) return

        val shouldResetContext = explicit || modelChanged || conv.lastUsedModel == null
        val newContextLimit = if (shouldResetContext) tierDefaultContext else conv.contextLimit
        val syncRounds = if (conv.memoryBankEnabled && shouldResetContext) {
            newContextLimit.coerceIn(
                QuiddityConstants.MIN_MEMORY_BANK_ROUNDS,
                QuiddityConstants.MAX_MEMORY_BANK_ROUNDS
            )
        } else {
            conv.memoryBankRounds
        }
        // 切换到不支持服务端搜索的模型时自动关闭官方联网搜索，避免开关状态与实际能力不一致
        val newWebSearch = if (conv.webSearchEnabled) {
            apiCatalogManager.supportsServerWebSearch(entry)
        } else {
            false
        }
        conversationRepository.updateConversation(
            conv.copy(
                apiCatalogId = if (explicit) entry.id else conv.apiCatalogId,
                sessionTokenUsed = 0,
                tokenCountApiId = entry.id,
                lastUsedModel = entry.apiModel,
                contextLimit = newContextLimit,
                memoryBankRounds = syncRounds,
                webSearchEnabled = newWebSearch
            )
        )
    }

    // ===== 主动消息（时间库） =====

    /**
     * 会话级"时间库主动消息"开关（对应算法文档 2.2）。
     * - 开启：立即触发第一次时间库生成（或当天已生成过则直接注册闹钟）
     * - 关闭：注销该会话所有定时闹钟
     */
    fun setActiveMessageEnabled(enabled: Boolean) {
        if (enabled) {
            _timeLibraryHint.value = "正在生成今日时间库…"
        }
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            val outcome = ServiceLocator.timeLibraryRepository.setConversationEnabled(conv, enabled)
            if (enabled) {
                val times = conversation.value?.timeLibrary.orEmpty()
                _timeLibraryHint.value = when (outcome) {
                    GenerationOutcome.Triggered ->
                        "时间库已生成：${times.joinToString("、") { it.time }}"
                    GenerationOutcome.TriggeredSilent ->
                        "时间库为空：AI 判断今天不需要主动发消息"
                    GenerationOutcome.Failed ->
                        "时间库生成失败：请检查模型接口配置后重试"
                    GenerationOutcome.UpToDate ->
                        "今日时间库已就绪"
                    GenerationOutcome.Generating ->
                        "时间库生成中，请稍候"
                    GenerationOutcome.NotEnabled -> ""
                }
            }
        }
    }

    /** 用户成功输入时间库查看密码后调用：记住已解锁，之后输密码弹窗直接显示密码。 */
    fun markTimeLibraryUnlocked() {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            if (!conv.timeLibraryPasswordUnlocked) {
                conversationRepository.updateConversation(conv.copy(timeLibraryPasswordUnlocked = true))
            }
        }
    }

    /** 用户手动保存时间库（时间点 + 禁用框），随后重新注册闹钟。 */
    fun updateTimeLibrary(times: List<String>, disabledSlots: List<Int>) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            ServiceLocator.timeLibraryRepository.saveTimeLibrary(conv, times, disabledSlots)
        }
    }

    /**
     * 界面整理提示（对应算法文档 3.1"正在整理前一天的记忆！"）。
     * 由 ChatScreen 在会话首次打开时消费后清空。
     */
    private val _timeLibraryHint = MutableStateFlow<String?>(null)
    val timeLibraryHint: StateFlow<String?> = _timeLibraryHint.asStateFlow()

    /**
     * 会话首次打开时调用（对应算法文档 3.1 生成时机）：
     * 若会话级开关已开启且当天尚未生成，先给出"正在整理前一天的记忆！"提示，
     * 再委托协调器生成时间库并注册闹钟。当天已生成 / 开关未开启时静默返回。
     */
    fun ensureTimeLibraryGenerated() {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            // 全局总开关关闭：静默跳过（不生成、不提示）
            if (!settingsRepository.currentSnapshot().proactiveMessageEnabled) return@launch
            if (!conv.activeMessageEnabled) return@launch
            val today = java.time.LocalDate.now().toString()
            if (!TimeLibraryEngine.shouldGenerate(true, conv.timeLibraryGeneratedDate, today)) return@launch
            _timeLibraryHint.value = "正在整理前一天的记忆！"
            val outcome = ServiceLocator.timeLibraryRepository.ensureLibraryGeneratedToday(conv.id)
            val times = conversation.value?.timeLibrary.orEmpty()
            _timeLibraryHint.value = when (outcome) {
                GenerationOutcome.Triggered ->
                    "时间库已生成：${times.joinToString("、") { it.time }}"
                GenerationOutcome.TriggeredSilent ->
                    "时间库为空：AI 判断今天不需要主动发消息"
                GenerationOutcome.Failed ->
                    "时间库生成失败：请检查模型接口配置后重试"
                else -> null
            }
        }
    }

    fun consumeTimeLibraryHint() {
        _timeLibraryHint.value = null
    }
}

class ChatViewModelFactory(
    private val conversationRepository: ConversationRepository,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val apiCatalogManager: ApiCatalogManager,
    private val visionOcrService: VisionOcrService,
    private val conversationId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatViewModel(
            conversationRepository,
            chatRepository,
            settingsRepository,
            apiCatalogManager,
            visionOcrService,
            conversationId
        ) as T
    }
}

/**
 * 待确认的危险工具调用：聊天页弹窗展示 [items]，
 * 用户点击后通过 [resume] 把决定交还给挂起的工具执行流。
 */
data class PendingToolConfirm(
    val items: List<ChatRepository.ToolConfirmItem>,
    val resume: (Boolean) -> Unit
)

/**
 * 流式输出过程中的工具痕迹：记录工具名、运行状态与结果摘要。
 */
data class ToolTrace(
    val name: String,
    val status: String,
    val summary: String?
)

/**
 * 撤回消息后的「重新编辑」缓存：保留被撤回消息的原文、图片与 OCR 文本，
 * 用户点击「重新编辑」后以编辑结果作为新消息重新发出。
 */
data class PendingReedit(
    val content: String,
    val ocrText: String?,
    val imageUri: String?
)

// 当前规则：压缩状态与 isGenerating 解耦；Compressing 驱动 UI 弹窗与发送置灰，Success/Failed 为瞬态供 Toast 后 consume 回 Idle。
sealed interface CompressionState {
    data object Idle : CompressionState
    data object Compressing : CompressionState
    data object Success : CompressionState
    data object Failed : CompressionState
}

/**
 * 图片 OCR 识图状态：Idle = 空闲；Recognizing = 正在识图（输入栏显示加载圈）。
 */
sealed interface OcrState {
    data object Idle : OcrState
    data object Recognizing : OcrState
}
