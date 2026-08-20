package com.quiddity.app.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.MessageToolTrace
import com.quiddity.app.data.model.Role
import com.quiddity.app.data.repo.ChatRepository
import com.quiddity.app.data.repo.ConversationRepository
import com.quiddity.app.data.repo.SettingsRepository
import com.quiddity.app.di.ServiceLocator
import com.quiddity.app.domain.ApiCatalogManager
import com.quiddity.app.domain.CompressionStateMachine
import com.quiddity.app.domain.ChatError
import com.quiddity.app.domain.VisionOcrService
import com.quiddity.app.util.IdGenerator
import com.quiddity.app.util.ImageUtils
import com.quiddity.app.util.QuiddityConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class StreamController(
    private val state: ChatViewModelState,
    private val conversationRepository: ConversationRepository,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val apiCatalogManager: ApiCatalogManager,
    private val visionOcrService: VisionOcrService,
    private val viewModelScope: CoroutineScope,
    private val conversationId: String,
    private val persona: PersonaController,
    private val settingsController: SettingsController,
    private val onIdle: ((String) -> Unit)? = null
) {
    internal lateinit var group: GroupController
    private val _messages get() = state._messages
    private val _pendingImageUri get() = state._pendingImageUri
    private val _ocrState get() = state._ocrState
    private val _isGenerating get() = state._isGenerating
    private val _toolTraces get() = state._toolTraces
    private val _pendingToolConfirm get() = state._pendingToolConfirm
    private val _pendingReedit get() = state._pendingReedit
    private val _compressionState get() = state._compressionState
    private val _errorEvent get() = state._errorEvent
    private val _activeSegmentEnds get() = state._activeSegmentEnds
    private val _chatError get() = state._chatError
    private val _inputBarText get() = state._inputBarText
    private val conversation get() = state.conversation
    private val messages get() = state.messages
    private val pendingImageUri get() = state.pendingImageUri
    private val toolTraces get() = state.toolTraces
    private var lastCompletedAiMessage get() = state.lastCompletedAiMessage; set(value) { state.lastCompletedAiMessage = value }
    private var streamJob get() = state.streamJob; set(value) { state.streamJob = value }
    private var replyRunStart get() = state.replyRunStart; set(value) { state.replyRunStart = value }
    private var replyRunChars get() = state.replyRunChars; set(value) { state.replyRunChars = value }
    private var groupStreamJob get() = state.groupStreamJob; set(value) { state.groupStreamJob = value }
    private var sendDelayJob get() = state.sendDelayJob; set(value) { state.sendDelayJob = value }
    private var lastInputEditAt get() = state.lastInputEditAt; set(value) { state.lastInputEditAt = value }
    private val groupQueueEngine get() = state.groupQueueEngine
    private lateinit var eventProcessor: StreamEventProcessor

    fun attachGroup(controller: GroupController) {
        group = controller
        eventProcessor = StreamEventProcessor(
            state = state,
            conversationRepository = conversationRepository,
            settingsRepository = settingsRepository,
            chatRepository = chatRepository,
            conversationId = conversationId,
            viewModelScope = viewModelScope,
            group = group,
            settingsController = settingsController
        )
    }

    /** 悬浮窗：标记本会话回复开始（应用不可见时由控制器决定是否显示窗口）。 */
    internal fun markReplyStarted() {
        val conv = conversation.value ?: return
        if (::eventProcessor.isInitialized) {
            eventProcessor.startReplyRun()
        }
        com.quiddity.app.active.ReplyOverlayController.startReply(conv.id, conv.type)
    }

    /** 悬浮窗：标记本会话回复结束。 */
    internal fun markReplyEnded() {
        val conv = conversation.value ?: return
        com.quiddity.app.active.ReplyOverlayController.endReply(conv.id)
    }

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
        if (group.isGroup()) {
            if (_compressionState.value is CompressionState.Compressing) return
            group.sendGroupUserMessage(text, conv, ocrText, imageUri)
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
                settingsController.checkAndUpdateModelContext()

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
    fun sendMessageWithImage(context: Context, text: String, imageUri: String) {
        if (imageUri.isBlank()) return
        if (_ocrState.value is OcrState.Recognizing) return
        val conv = conversation.value ?: return
        viewModelScope.launch {
            _ocrState.value = OcrState.Recognizing
            // 发送前置校验：失败时保留待发送图片，避免"OCR 白跑 + 图片被吞"
            if (!group.isGroup()) {
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
                if (group.isGroup()) {
                    group.sendGroupUserMessage(visibleContent, conv, ocr, persistentUri)
                } else {
                    sendMessage(visibleContent, ocrText = ocr, imageUri = persistentUri)
                }
            }.onFailure { e ->
                _errorEvent.value = "图片识别失败：${e.message ?: "未知错误"}"
            }
        }
    }
    fun startApiStream() {
        if (_isGenerating.value || _compressionState.value is CompressionState.Compressing) return
        val conv = conversation.value ?: return
        val sceneAtStart = conv.scene
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            // ===== 流式阶段 =====
            var streamError: Throwable? = null
            val selfJob = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
            eventProcessor.cleanupStaleStreamingMessages()
            _isGenerating.value = true
            markReplyStarted()
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
                    persona.effectiveMemoryStrategy(conv),
                    thinking = ""
                ) { event ->
                    if (event is ChatRepository.Event.Error) {
                        streamError = event.throwable
                        _errorEvent.value = event.throwable.message ?: "未知错误"
                        _chatError.value = chatRepository.classify(event.throwable)
                    } else {
                        eventProcessor.handle(event)
                        if (event is ChatRepository.Event.CompleteMessage) {
                            eventProcessor.markSceneInjectedIfUnchanged(sceneAtStart)
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
                    markReplyEnded()
                    _toolTraces.value = emptyList()
                    _pendingToolConfirm.value = null
                }
                if (agentTaskStarted) {
                    com.quiddity.app.active.AgentTaskService.stop(
                        com.quiddity.app.di.ServiceLocator.applicationContext
                    )
                }
                eventProcessor.settleInterruptedStreams()
                notifyIdleIfNoWork()
            }
            // ===== 压缩阶段：仅当流式正常结束时触发 =====
            if (streamError == null) {
                awaitCompressionIfNeeded(conv)
            }
            notifyIdleIfNoWork()
        }
    }
    fun letAiStart() {
        if (group.isGroup()) return
        if (_isGenerating.value) return
        settingsController.cancelPendingSend() // 取消 pending 的发送延迟
        val conv = conversation.value ?: return
        val sceneAtStart = conv.scene

        runStream {
            chatRepository.letAiStart(conv, persona.effectiveMemoryStrategy(conv)) { event ->
                eventProcessor.handle(event)
                if (event is ChatRepository.Event.CompleteMessage) {
                    eventProcessor.markSceneInjectedIfUnchanged(sceneAtStart)
                }
            }
        }
    }
    fun regenerate() {
        if (group.isGroup()) return
        if (_isGenerating.value) return
        settingsController.cancelPendingSend() // 取消 pending 的发送延迟
        val conv = conversation.value ?: return
        val sceneAtStart = conv.scene
        val current = _messages.value
        if (current.isEmpty()) return
        val last = current.last()
        // 防御性：UI 上"重说"按钮仅对最后一条 AI 消息可见，但外部代码（如测试、deep link）
        // 可能直接调用本方法时遇到最后一条是 USER 的边界情况——此时无 AI 回复可重说。
        if (last.role != Role.ASSISTANT) return
        if (last.isStreaming) return  // 还在 streaming 中：忽略，等待完成
        // 重说基准：最后一条非思考、非提示的实际 AI 内容（思考消息不参与去同判定）
        val replyReference = current.lastOrNull {
            it.role == Role.ASSISTANT && !it.isNotice && !it.isThinking &&
                it.content.isNotBlank() && !it.isError
        } ?: return

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
        // 去同基准：用户点「重说」的那条回复。新回复与它高度相似时由算法自动换表达重写，
        // 不把「别写得太像」完全交给模型自觉。
        val similarityReference = replyReference.content.trim().takeIf { it.isNotBlank() }
        val newHistory = if (lastUserIndex >= 0) {
            current.subList(0, lastUserIndex + 1).toList()
        } else {
            emptyList()
        }
        val isOpeningRound = lastUserIndex < 0

        runStream {
            var attempt = 0
            var hint = previousReplies
            while (true) {
                attempt++
                // 常规轮次：删除最后一条 USER 之后的所有 AI 消息，再重新生成整轮回复；
                // AI 开场轮（"让 AI 先说"，无 USER 消息）：整轮全部重说。
                // 修复：旧实现只删最后一条并把 assistant 结尾历史直接续写，
                // 导致只重生成最后一句、前面句子全部残留。
                conversationRepository.replaceMessages(conversationId, newHistory)
                if (isOpeningRound) {
                    chatRepository.letAiStart(
                        conv,
                        persona.effectiveMemoryStrategy(conv),
                        hint
                    ) { event ->
                        eventProcessor.handle(event)
                        if (event is ChatRepository.Event.CompleteMessage) {
                            eventProcessor.markSceneInjectedIfUnchanged(sceneAtStart)
                        }
                    }
                } else {
                    chatRepository.streamAssistantReply(
                        conv,
                        newHistory,
                        persona.effectiveMemoryStrategy(conv),
                        hint
                    ) { event ->
                        eventProcessor.handle(event)
                        if (event is ChatRepository.Event.CompleteMessage) {
                            eventProcessor.markSceneInjectedIfUnchanged(sceneAtStart)
                        }
                    }
                }
                // 去同判定：新回复与上一版核心内容高度相似（bigram 相似度 ≥ 阈值）时，
                // 擦掉本次结果并用更强约束重写一次；上限 REGENERATE_MAX_ATTEMPTS。
                if (similarityReference == null || attempt >= REGENERATE_MAX_ATTEMPTS) break
                val produced = _messages.value.lastOrNull {
                    it.role == Role.ASSISTANT && !it.isNotice && !it.isThinking &&
                        it.content.isNotBlank() && !it.isError
                }?.content.orEmpty()
                if (produced.isBlank()) break
                if (com.quiddity.app.data.repo.replySimilarityRatio(
                        similarityReference, produced
                    ) < com.quiddity.app.data.repo.REGENERATE_SIMILARITY_THRESHOLD
                ) {
                    break
                }
                hint = buildRegenerateRetryHint(previousReplies ?: similarityReference)
            }
        }
    }

    /** 重说去同重试：算法检测到与上一版高度相似后，用数据驱动的更强约束重写一次。 */
    private fun buildRegenerateRetryHint(previous: String): String =
        "（再次重说：刚才新生成的回复与上一版过于相似。请完全换一种开场、角度、句式和叙述顺序重新写，" +
            "第一句不要沿用上一版的开头；保持同样的人设与语境，不要复述上一版原句。）\n" +
            "上一版回复（仅作对照，禁止复述）：${previous.take(600)}"

    fun continueGeneration() {
        if (group.isGroup()) return
        if (_isGenerating.value) return
        settingsController.cancelPendingSend() // 取消 pending 的发送延迟
        val conv = conversation.value ?: return
        val sceneAtStart = conv.scene
        val current = _messages.value
        if (current.isEmpty()) return
        val last = current.last()
        // 防御性：最后一条必须是非 streaming 的 AI 消息（已停止 / 错误 / 完成）
        if (last.role != Role.ASSISTANT) return
        if (last.isStreaming) return
        // 继续说判定基准：最后一条非思考、非提示的实际 AI 内容
        val replyReference = current.lastOrNull {
            it.role == Role.ASSISTANT && !it.isNotice && !it.isThinking &&
                it.content.isNotBlank()
        }

        runStream {
            // 不追加真实用户消息——直接用现有历史触发流式回复。
            // 追加一条「继续说」引导（只进 API 请求、不进 UI 消息列表）：
            // 算法按上一条回复的形态选择引导语——上一条只有动作时要求「补全台词」，
            // 有内容时才走「接着上一句继续说」，让模型自然输出内容而不是复述或描写动作。
            val guide = if (replyReference != null &&
                com.quiddity.app.data.repo.isActionOnlyReply(replyReference.content)
            ) {
                "（继续说：请直接说出一句完整的台词继续对话，不要复述上一句。）"
            } else {
                "（继续说：请直接接着上一句继续输出后续内容，不要复述上一句。）"
            }
            val newHistory = _messages.value + Message(
                id = IdGenerator.newId(IdGenerator.Prefix.USER_MESSAGE),
                conversationId = conv.id,
                role = Role.USER,
                content = guide,
                timestamp = System.currentTimeMillis()
            )
            chatRepository.streamAssistantReply(
                conv,
                newHistory,
                persona.effectiveMemoryStrategy(conv)
            ) { event ->
                eventProcessor.handle(event)
                if (event is ChatRepository.Event.CompleteMessage) {
                    eventProcessor.markSceneInjectedIfUnchanged(sceneAtStart)
                }
            }
        }
    }
    fun regenerateGroupMemberMessage(messageId: String) {
        if (!group.isGroup()) return
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
            markReplyStarted()
            try {
                conversationRepository.replaceMessages(conversationId, newHistory)
                chatRepository.streamGroupMemberReply(
                    member = member,
                    group = group,
                    transcript = newHistory,
                    senderId = senderId,
                    regeneratePreviousReply = previousReplies
                ) { event ->
                    eventProcessor.handle(event)
                }
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _errorEvent.value = "重说失败：${t.message ?: "未知错误"}"
            } finally {
                if (groupStreamJob === selfJob) {
                    _isGenerating.value = false
                    markReplyEnded()
                }
                eventProcessor.settleInterruptedStreams()
                notifyIdleIfNoWork()
            }
            notifyIdleIfNoWork()
        }
    }
    fun runStream(block: suspend () -> Unit) {
        // 取消上一轮（如果仍在进行）
        streamJob?.cancel()
        _isGenerating.value = true
        markReplyStarted()
        replyRunStart = System.currentTimeMillis()
        replyRunChars = 0
        streamJob = viewModelScope.launch {
            val selfJob = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
            // 防御性：清理可能残留的 streaming 状态
            // 用户停止生成 / 异常退出后，最后一条消息可能仍是 streaming=true，
            // 这种状态会卡住 UI（光标不消失），且与新 run 的消息产生视觉混乱
            eventProcessor.cleanupStaleStreamingMessages()
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
                    markReplyEnded()
                    _toolTraces.value = emptyList()
                    _pendingToolConfirm.value = null
                }
                if (agentTaskStarted) {
                    com.quiddity.app.active.AgentTaskService.stop(
                        com.quiddity.app.di.ServiceLocator.applicationContext
                    )
                }
                eventProcessor.settleInterruptedStreams()
                notifyIdleIfNoWork()
            }
        }
    }
    fun stopGeneration() {
        // 群聊停止：模式 A 只停当前成员，排队的顺位递补；模式 B 清空整个队列（方案四.7）
        if (group.isGroup()) {
            groupStreamJob?.cancel()
            groupStreamJob = null
            val mode = conversation.value?.stopMode ?: QuiddityConstants.GROUP_DEFAULT_STOP_MODE
            if (mode == QuiddityConstants.GROUP_STOP_MODE_B) {
                groupQueueEngine.clear()
            } else {
                groupQueueEngine.removeReplying()
                // 模式 A：只停当前成员，排队的照常顺位递补（方案四.7）
                if (groupQueueEngine.isNotEmpty) {
                    group.startGroupQueueProcessor()
                }
            }
            group.syncGroupQueue()
            _isGenerating.value = false
            markReplyEnded()
            eventProcessor.settleInterruptedStreams()
            notifyIdleIfNoWork()
            return
        }
        streamJob?.cancel()
        streamJob = null
        settingsController.cancelPendingSend() // 同时取消 pending 的发送延迟
        _toolTraces.value = emptyList()
        _pendingToolConfirm.value = null
        com.quiddity.app.active.OperationNotifyController.dismiss()
        _isGenerating.value = false
        markReplyEnded()
        eventProcessor.settleInterruptedStreams()
        notifyIdleIfNoWork()
    }
    fun hasActiveWork(): Boolean =
        _isGenerating.value ||
            _compressionState.value is CompressionState.Compressing ||
            (sendDelayJob?.isActive == true) ||
            (groupStreamJob?.isActive == true)

    /** 无未完结任务时通知宿主（仅当宿主已请求释放才生效）。 */
    fun notifyIdleIfNoWork() {
        if (!hasActiveWork()) onIdle?.invoke(conversationId)
    }
    suspend fun awaitCompressionIfNeeded(conv: Conversation) {
        val messages = _messages.value
        val userRounds = messages.count { it.role == Role.USER }
        if (!CompressionStateMachine.shouldCompress(conv, userRounds)) return

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
    fun consumeCompressionResult() {
        val next = CompressionStateMachine.consume(_compressionState.value)
        if (next != _compressionState.value) {
            _compressionState.value = next
            notifyIdleIfNoWork()
        }
    }
    fun consumeError() {
        _errorEvent.value = null
    }
    fun consumeChatError() {
        _chatError.value = null
    }

    internal suspend fun handleStreamEvent(event: ChatRepository.Event) = eventProcessor.handle(event)

    internal suspend fun settleInterruptedStreams() = eventProcessor.settleInterruptedStreams()

    private companion object {
        /** 重说去同自动重试上限（首次 + 最多 1 次换表达重写）。 */
        const val REGENERATE_MAX_ATTEMPTS = 2
    }
}
