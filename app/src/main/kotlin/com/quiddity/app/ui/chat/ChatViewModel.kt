package com.quiddity.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Persona
import com.quiddity.app.data.model.PersonaCard
import com.quiddity.app.data.model.Role
import com.quiddity.app.data.model.UserPersona
import com.quiddity.app.data.repo.ChatRepository
import com.quiddity.app.data.repo.ConversationRepository
import com.quiddity.app.data.repo.SettingsRepository
import com.quiddity.app.domain.ApiCatalogManager
import com.quiddity.app.domain.ChatError
import com.quiddity.app.util.IdGenerator
import com.quiddity.app.util.QuiddityConstants
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
    private val conversationId: String
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

    // ===== 压缩状态机 =====
    // 与 isGenerating 解耦：压缩在 isGenerating 置 false 之后才启动，两者互斥。
    // Compressing 期间 UI 弹 loading 弹窗、发送按钮置灰、横滑禁用；
    // Success/Failed 为瞬态，UI 弹 Toast 后调 consumeCompressionResult 回到 Idle。
    private val _compressionState = MutableStateFlow<CompressionState>(CompressionState.Idle)
    val compressionState: StateFlow<CompressionState> = _compressionState.asStateFlow()

    private val _errorEvent = MutableStateFlow<String?>(null)
    val errorEvent: StateFlow<String?> = _errorEvent.asStateFlow()

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
     * 发送用户消息并触发 AI 流式回复。
     *
     * 用户消息 ID 使用 [IdGenerator]，避免 `System.currentTimeMillis()` 在毫秒级连发时产出重复 ID。
     *
     * 发送延迟期间不阻塞 _isGenerating，允许用户连续发送多条消息：
     * - 消息保存使用 NonCancellable 上下文，确保即使延迟被重置也不会丢失消息
     * - 延迟计时器通过 [sendDelayJob] 管理，用户发送新消息时自动重置
     * - 延迟结束后调用 [startApiStream] 发起 API 请求（此时才设置 _isGenerating）
     */
    fun sendMessage(text: String) {
        if (text.isBlank()) return
        // 仅在 API 调用 / 压缩期间阻止发送；发送延迟期间允许继续发送
        if (_isGenerating.value || _compressionState.value is CompressionState.Compressing) return
        val conv = conversation.value ?: return

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
                    timestamp = now
                )
                conversationRepository.appendMessage(userMsg)
            }

            // 发送延迟——等待用户停止输入后再发出 API 请求
            val settings = settingsRepository.currentSnapshot()
            if (settings.sendDelayEnabled) {
                val delayMs = settings.sendDelaySeconds * 1000L
                kotlinx.coroutines.delay(delayMs)
                // 等待输入框为空（用户停止输入）
                // 如果用户在等待期间继续输入并发送，sendDelayJob 会被外层 cancel 并重启
                while (_inputBarText.value.isNotBlank()) {
                    kotlinx.coroutines.delay(500)
                }
            }

            // 延迟结束（或未启用延迟）→ 发起 API 请求
            startApiStream()
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
            cleanupStaleStreamingMessages()
            _isGenerating.value = true
            try {
                val history = _messages.value
                chatRepository.streamAssistantReply(conv, history) { event ->
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
            } catch (t: Throwable) {
                streamError = t
                _errorEvent.value = t.message ?: "未知错误"
                _chatError.value = chatRepository.classify(t)
            } finally {
                _isGenerating.value = false
            }
            // ===== 压缩阶段：仅当流式正常结束时触发 =====
            if (streamError == null) {
                awaitCompressionIfNeeded(conv)
            }
        }
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
            val compressed = chatRepository.compressConversationMemory(conv, messages)
            conversationRepository.updateConversation(
                conv.copy(
                    compressedMemory = compressed,
                    lastCompressedAtRound = userRounds
                )
            )
            _compressionState.value = CompressionState.Success
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
        }
    }

    /** 让 AI 主动发消息（空对话开场）。 */
    fun letAiStart() {
        if (_isGenerating.value) return
        cancelPendingSend() // 取消 pending 的发送延迟
        val conv = conversation.value ?: return
        val sceneAtStart = conv.scene

        runStream {
            chatRepository.letAiStart(conv) { event ->
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
        val newHistory = if (lastUserIndex >= 0) {
            current.subList(0, lastUserIndex + 1).toList()
        } else {
            // 无 USER 消息（如 letAiStart 开场）：退化为仅删除最后一条 AI
            current.dropLast(1)
        }

        runStream {
            // 1. 移除当前轮次的所有 AI 消息（原子替换整张表）
            conversationRepository.replaceMessages(conversationId, newHistory)
            // 2. 用删除后的 history 重新触发 AI 回复
            chatRepository.streamAssistantReply(conv, newHistory) { event ->
                handleStreamEvent(event)
                if (event is ChatRepository.Event.CompleteMessage) {
                    markSceneInjectedIfUnchanged(sceneAtStart)
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
            chatRepository.streamAssistantReply(conv, newHistory) { event ->
                handleStreamEvent(event)
                if (event is ChatRepository.Event.CompleteMessage) {
                    markSceneInjectedIfUnchanged(sceneAtStart)
                }
            }
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
        streamJob = viewModelScope.launch {
            // 防御性：清理可能残留的 streaming 状态
            // 用户停止生成 / 异常退出后，最后一条消息可能仍是 streaming=true，
            // 这种状态会卡住 UI（光标不消失），且与新 run 的消息产生视觉混乱
            cleanupStaleStreamingMessages()
            try {
                block()
            } catch (t: Throwable) {
                _errorEvent.value = t.message ?: "未知错误"
                _chatError.value = chatRepository.classify(t)
            } finally {
                _isGenerating.value = false
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
     * 串行处理流式事件：每个事件依次 await 持久化完成后再处理下一个，
     * 避免 updateMessage 抢在 appendMessage 之前执行。
     */
    private suspend fun handleStreamEvent(event: ChatRepository.Event) {
        when (event) {
            is ChatRepository.Event.NewMessage -> {
                conversationRepository.appendMessage(event.message)
            }
            is ChatRepository.Event.UpdateMessage -> {
                conversationRepository.updateMessage(event.message)
            }
            is ChatRepository.Event.CompleteMessage -> {
                conversationRepository.updateMessage(event.message)
                // AI 消息完成时累加 token 用量
                accumulateTokenUsage(event.message.tokenCount)
            }
            is ChatRepository.Event.Done -> Unit
            is ChatRepository.Event.Error -> {
                _errorEvent.value = event.throwable.message ?: "未知错误"
                _chatError.value = chatRepository.classify(event.throwable)
            }
        }
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
        streamJob?.cancel()
        streamJob = null
        cancelPendingSend() // 同时取消 pending 的发送延迟
        _isGenerating.value = false
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
        val conv = conversation.value ?: return
        viewModelScope.launch {
            // 如果会话名还是默认的"新会话"，且人设名不为空，则自动将会话名改为人设名
            val newTitle = if (conv.title == QuiddityConstants.DEFAULT_CONVERSATION_TITLE && persona.name.isNotBlank()) {
                persona.name
            } else {
                conv.title
            }
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

    fun updateUserPersona(userPersona: UserPersona) {
        val conv = conversation.value ?: return
        viewModelScope.launch {
            conversationRepository.updateConversation(conv.copy(userPersona = userPersona))
        }
    }

    fun updateScene(scene: String) {
        val conv = conversation.value ?: return
        viewModelScope.launch {
            // 场景修改后重置 sceneInjected=false，下次对话将新场景注入系统提示词
            conversationRepository.updateConversation(
                conv.copy(scene = scene, sceneInjected = false)
            )
        }
    }

    fun updateMemory(memory: String) {
        val conv = conversation.value ?: return
        viewModelScope.launch {
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
        val conv = conversation.value ?: throw IllegalStateException("会话不存在")
        _isCompiling.value = true
        try {
            // 先把最新 persona 写入会话，确保 repository 读到的是最新字段
            val newTitle = if (conv.title == QuiddityConstants.DEFAULT_CONVERSATION_TITLE && persona.name.isNotBlank()) {
                persona.name
            } else {
                conv.title
            }
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
     * 2. 调用 [ChatRepository.quickSetup] 让 AI 生成结构化人设卡文本；
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
     * - 写入后在会话内插入一条居中灰色提示气泡（isNotice=true），展示当前场景与世界类型，
     *   让用户直观了解快速设定生效后的场景状态。提示气泡不发给 LLM、不参与压缩、不导出。
     */
    fun applyQuickSetupResult(
        rawText: String,
        tier: com.quiddity.app.domain.QuickSetupTier
    ) {
        val conv = conversation.value ?: return
        val result = com.quiddity.app.domain.QuickSetupPrompt
            .parseQuickSetupResult(rawText, tier)
        val newTitle = if (conv.title == QuiddityConstants.DEFAULT_CONVERSATION_TITLE &&
            result.persona.name.isNotBlank()
        ) {
            result.persona.name
        } else {
            conv.title
        }
        // 构造提示气泡内容：世界类型（世界背景前4个字）+ 当前场景
        val worldType = result.persona.worldBackground.take(4)
        val noticeContent = buildNoticeContent(worldType, result.scene)
        viewModelScope.launch {
            withContext(NonCancellable) {
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
                if (noticeContent.isNotBlank()) {
                    val noticeMsg = Message(
                        id = IdGenerator.newId(IdGenerator.Prefix.USER_MESSAGE),
                        conversationId = conv.id,
                        role = Role.SYSTEM,
                        content = noticeContent,
                        timestamp = System.currentTimeMillis(),
                        isNotice = true
                    )
                    conversationRepository.appendMessage(noticeMsg)
                }
            }
        }
    }

    /**
     * 构造快速设定提示气泡内容：世界类型 + 场景。
     * - 世界类型为世界背景前4个汉字（LLM 按规则在 [世界背景] 字段首写4字世界类型）；
     * - 场景为 [当前场景] 内容；
     * - 两者皆有 → "世界类型 · 场景"；仅一项 → 该项；皆空 → 返回空串（不插气泡）。
     */
    private fun buildNoticeContent(worldType: String, scene: String): String {
        val wt = worldType.trim()
        val sc = scene.trim()
        return when {
            wt.isNotBlank() && sc.isNotBlank() -> "$wt · $sc"
            wt.isNotBlank() -> wt
            sc.isNotBlank() -> sc
            else -> ""
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
        val newHistory = current.subList(0, targetIndex).toList()
        viewModelScope.launch {
            conversationRepository.replaceMessages(conversationId, newHistory)
        }
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
        val conv = conversation.value ?: return
        viewModelScope.launch {
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
     * - 重置 compressedMemory 与 lastCompressedAtRound，下次对话重新开始
     * - 保留 AI 人设、用户人设、场景、记忆、壁纸、API 配置等所有会话级设置
     *
     * 不可恢复，需在 UI 层做二次确认。
     */
    fun clearConversationMessages() {
        if (_isGenerating.value) return
        val conv = conversation.value ?: return
        viewModelScope.launch {
            conversationRepository.replaceMessages(conversationId, emptyList())
            conversationRepository.updateConversation(
                conv.copy(
                    compressedMemory = "",
                    lastCompressedAtRound = 0
                )
            )
        }
    }

    /** 导出当前会话的人设卡。 */
    fun exportPersonaCard(): PersonaCard? {
        val conv = conversation.value ?: return null
        return PersonaCard(
            schemaVersion = 1,
            exportedAt = System.currentTimeMillis(),
            persona = conv.persona,
            userPersona = conv.userPersona,
            scene = conv.scene,
            memory = conv.memory
        )
    }

    /** 导入人设卡到当前会话。 */
    fun importPersonaCard(card: PersonaCard) {
        val conv = conversation.value ?: return
        viewModelScope.launch {
            // 场景可能随人设卡变更，重置 sceneInjected 让下次对话重新注入
            conversationRepository.updateConversation(
                conv.copy(
                    persona = card.persona,
                    userPersona = card.userPersona,
                    scene = card.scene,
                    sceneInjected = false,
                    memory = card.memory
                )
            )
        }
    }

    /** 设置 AI 头像（会话级）。 */
    fun setAiAvatarUri(uri: String?) {
        val conv = conversation.value ?: return
        viewModelScope.launch {
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
        val conv = conversation.value ?: return
        viewModelScope.launch {
            conversationRepository.updateConversation(conv.copy(wallpaperUri = uri))
        }
    }

    /**
     * 设置壁纸暗化程度 0.0f - 1.0f。
     * 数值越大壁纸越暗，文字可读性越好但壁纸本身被遮挡。
     */
    fun setWallpaperDarken(value: Float) {
        val conv = conversation.value ?: return
        val clamped = value.coerceIn(
            QuiddityConstants.MIN_WALLPAPER_DARKEN,
            QuiddityConstants.MAX_WALLPAPER_DARKEN
        )
        viewModelScope.launch {
            conversationRepository.updateConversation(conv.copy(wallpaperDarken = clamped))
        }
    }

    /**
     * 从 Markdown / 纯文本导入对话记录到当前会话。
     *
     * 行为：
     * - 解析 [text] 为 [com.quiddity.app.util.ConversationCodec.ImportResult]
     * - 替换当前会话的所有消息为新解析的 messages（覆盖式导入）
     * - 若解析到人设卡信息，更新当前会话的 persona / userPersona / scene / memory
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
        val conv = conversation.value ?: return Result.failure(
            IllegalStateException("会话未加载")
        )
        return runCatching {
            val result = com.quiddity.app.util.ConversationCodec.importConversation(
                content = text,
                targetConversationId = conv.id
            )
            viewModelScope.launch {
                // 1. 替换消息列表（覆盖式导入）
                conversationRepository.replaceMessages(conv.id, result.messages)

                // 2. 更新人设卡信息（仅在解析到非空内容时更新对应字段）
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
                    conv.title == QuiddityConstants.DEFAULT_CONVERSATION_TITLE &&
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
     * - 本方法导出消息记录（人设卡 + 所有消息）
     * - exportPersonaCard 仅导出人设卡（无消息）
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

        val currentModel = entry.apiModel
        val currentApiId = entry.id
        val tier = apiCatalogManager.getModelTier(currentModel, entry.providerId)
        val tierDefaultContext = apiCatalogManager.defaultContextLimitForTier(tier)

        val apiChanged = conv.tokenCountApiId != null && conv.tokenCountApiId != currentApiId
        val modelChanged = conv.lastUsedModel != null && conv.lastUsedModel != currentModel

        if (apiChanged || modelChanged || conv.tokenCountApiId == null) {
            // API 或模型已切换（或首次使用）：重置 token 计数 + 更新上下文默认值
            val newContextLimit = if (modelChanged || conv.lastUsedModel == null) {
                tierDefaultContext
            } else {
                conv.contextLimit
            }
            // 模型切换时压缩轮数跟随上下文记忆轮数同步，满足"压缩轮数随模型变化"需求
            val syncRounds = if (conv.memoryBankEnabled && (modelChanged || conv.lastUsedModel == null)) {
                newContextLimit.coerceIn(
                    QuiddityConstants.MIN_MEMORY_BANK_ROUNDS,
                    QuiddityConstants.MAX_MEMORY_BANK_ROUNDS
                )
            } else {
                conv.memoryBankRounds
            }
            conversationRepository.updateConversation(
                conv.copy(
                    sessionTokenUsed = 0,
                    tokenCountApiId = currentApiId,
                    lastUsedModel = currentModel,
                    contextLimit = newContextLimit,
                    memoryBankRounds = syncRounds
                )
            )
        }
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
        val conv = conversation.value ?: return
        val clamped = limit.coerceIn(
            QuiddityConstants.MIN_CONTEXT_LIMIT,
            QuiddityConstants.MAX_CONTEXT_LIMIT
        )
        viewModelScope.launch {
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

    /**
     * 重置上下文记忆轮数为当前模型分级的默认值。
     *
     * 当 memoryBankEnabled 时，压缩轮数同步跟随重置后的上下文记忆轮数。
     */
    fun resetContextLimitToTierDefault() {
        val conv = conversation.value ?: return
        val tier = resolveCurrentTier()
        val defaultLimit = apiCatalogManager.defaultContextLimitForTier(tier)
        viewModelScope.launch {
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
        val conv = conversation.value ?: return
        viewModelScope.launch {
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
        val conv = conversation.value ?: return
        val clamped = rounds.coerceIn(
            QuiddityConstants.MIN_MEMORY_BANK_ROUNDS,
            QuiddityConstants.MAX_MEMORY_BANK_ROUNDS
        )
        viewModelScope.launch {
            conversationRepository.updateConversation(conv.copy(memoryBankRounds = clamped))
        }
    }

    // ===== 发送延迟 =====

    /** 当前输入框文本（用于发送延迟检测）。 */
    private val _inputBarText = MutableStateFlow("")
    val inputBarText: StateFlow<String> = _inputBarText.asStateFlow()

    /** 发送延迟计时器。 */
    private var sendDelayJob: Job? = null

    /**
     * 更新输入框文本状态（由 ChatInputBar 调用）。
     * 用于发送延迟检测：当输入框为空时才真正发出 API 请求。
     */
    fun updateInputText(text: String) {
        _inputBarText.value = text
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
        val conv = conversation.value ?: return
        val settings = settingsRepository.currentSnapshot()
        val entry = settings.catalog.firstOrNull { it.id == catalogId } ?: return
        val tier = apiCatalogManager.getModelTier(entry.apiModel, entry.providerId)
        val defaultContext = apiCatalogManager.defaultContextLimitForTier(tier)
        // 切换 API 时压缩轮数跟随上下文记忆轮数同步
        val syncRounds = if (conv.memoryBankEnabled) {
            defaultContext.coerceIn(
                QuiddityConstants.MIN_MEMORY_BANK_ROUNDS,
                QuiddityConstants.MAX_MEMORY_BANK_ROUNDS
            )
        } else {
            conv.memoryBankRounds
        }
        viewModelScope.launch {
            conversationRepository.updateConversation(
                conv.copy(
                    apiCatalogId = catalogId,
                    contextLimit = defaultContext,
                    memoryBankRounds = syncRounds,
                    sessionTokenUsed = 0,
                    tokenCountApiId = catalogId,
                    lastUsedModel = entry.apiModel
                )
            )
        }
    }
}

class ChatViewModelFactory(
    private val conversationRepository: ConversationRepository,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val apiCatalogManager: ApiCatalogManager,
    private val conversationId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatViewModel(
            conversationRepository,
            chatRepository,
            settingsRepository,
            apiCatalogManager,
            conversationId
        ) as T
    }
}

// 当前规则：压缩状态与 isGenerating 解耦；Compressing 驱动 UI 弹窗与发送置灰，Success/Failed 为瞬态供 Toast 后 consume 回 Idle。
sealed interface CompressionState {
    data object Idle : CompressionState
    data object Compressing : CompressionState
    data object Success : CompressionState
    data object Failed : CompressionState
}
