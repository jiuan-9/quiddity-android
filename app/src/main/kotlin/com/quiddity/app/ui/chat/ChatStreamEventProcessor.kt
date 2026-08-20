package com.quiddity.app.ui.chat

import androidx.lifecycle.viewModelScope
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.MessageToolTrace
import com.quiddity.app.data.model.Role
import com.quiddity.app.data.repo.ChatRepository
import com.quiddity.app.data.repo.ConversationRepository
import com.quiddity.app.data.repo.SettingsRepository
import com.quiddity.app.domain.ChatError
import com.quiddity.app.util.IdGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class StreamEventProcessor(
    private val state: ChatViewModelState,
    private val conversationRepository: ConversationRepository,
    private val settingsRepository: SettingsRepository,
    private val chatRepository: ChatRepository,
    private val conversationId: String,
    private val viewModelScope: CoroutineScope,
    private val group: GroupController,
    private val settingsController: SettingsController
) {
    private val _messages get() = state._messages
    private val _toolTraces get() = state._toolTraces
    private val _pendingToolConfirm get() = state._pendingToolConfirm
    private val _errorEvent get() = state._errorEvent
    private val _activeSegmentEnds get() = state._activeSegmentEnds
    private val _chatError get() = state._chatError
    private val conversation get() = state.conversation
    private val toolTraces get() = state.toolTraces
    private var lastCompletedAiMessage get() = state.lastCompletedAiMessage; set(value) { state.lastCompletedAiMessage = value }
    /** 本次回复累计内容（仅用于悬浮窗最终气泡，不落库）。 */
    private val replyRunAccumulator = StringBuilder()

    /** 新一轮回复开始：清空累计器（悬浮窗气泡只展示本轮内容）。 */
    fun startReplyRun() {
        replyRunAccumulator.setLength(0)
    }
    suspend fun handle(event: ChatRepository.Event) {
        when (event) {
            is ChatRepository.Event.NewMessage -> {
                if (!conversationRepository.appendMessage(event.message)) raiseStorageError()
            }
            is ChatRepository.Event.UpdateMessage -> {
                if (!conversationRepository.updateMessage(event.message)) raiseStorageError()
                // 合并模式（工具轮正文单条化）下，后续轮次正文以 Update 追加到同一条消息，
                // 缓存最新合并结果——Done 固化工具痕迹时必须以最新 content 为准，
                // 否则会用旧对象覆盖持久化消息导致正文丢失（只剩工具痕迹）。
                // 空内容 Update 是协调器的删除标记（半截残留消息回收），不得污染缓存。
                if (event.message.role == Role.ASSISTANT && !event.message.isNotice &&
                    !event.message.isThinking && event.message.content.isNotBlank()
                ) {
                    lastCompletedAiMessage = event.message
                    // 实时段边界：流式过程中工具痕迹立即穿插在正文段间（不依赖 flow 延迟）
                    _activeSegmentEnds.value = event.message.toolSegmentEnds
                }
            }
            is ChatRepository.Event.CompleteMessage -> {
                // 协调器近重复去重：跨多分片流式到达的重复台词在收尾时派发
                // 「空内容 Complete」删除标记，这里直接移除该消息，不落盘、不入队气泡
                if (event.message.content.isBlank() && !event.message.isNotice && !event.message.isThinking) {
                    conversationRepository.deleteMessage(conversationId, event.message.id)
                    return
                }
                // 模型偶发在正式回答开头复述思考内容/系统指令：剥离重复前缀，
                // 避免「思考一条 + 回答一条」两条消息内容雷同（防御性去重）
                val target = stripThinkingEcho(event.message)
                // 缓存本次流式最后一条完成消息：Done 事件固化工具痕迹时，
                // _messages 的 flow 更新可能有异步延迟，直接取不到刚完成的消息
                lastCompletedAiMessage = target
                // 消息文本流式完毕即落盘为完成态，不再按字数保持 streaming 假状态：
                // 旧的「打字延迟」保持会让光标在文本已显示完后继续闪烁数秒，
                // 并阻塞流式消费造成突发渲染（气泡闪动主因）。
                if (!conversationRepository.updateMessage(target)) raiseStorageError()
                // AI 消息完成时累加 token 用量
                settingsController.accumulateTokenUsage(target.tokenCount)
                // 累计本轮回复内容：悬浮窗最终气泡只展示本轮，不把历史对话堆进去
                replyRunAccumulator.append(target.content)
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
                // 悬浮窗：回复结束，把本轮累计的完整回复作为最终气泡展示
                val fullReply = replyRunAccumulator.toString().trim()
                replyRunAccumulator.setLength(0)
                if (fullReply.isNotBlank()) {
                    com.quiddity.app.active.ReplyOverlayController.enqueueBubble(
                        text = fullReply,
                        conversationId = conversationId,
                        conversationType = conversation.value?.type
                            ?: com.quiddity.app.data.model.ConversationType.SOLO
                    )
                }
                com.quiddity.app.active.ReplyOverlayController.endReply(conversationId)
            }
            is ChatRepository.Event.Truncated -> {
                replyRunAccumulator.setLength(0)
                // 回复被截断：不静默吞掉——群聊插入可见提示气泡，私聊弹提示
                if (group.isGroup()) {
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
                replyRunAccumulator.setLength(0)
                _toolTraces.value = emptyList()
                _pendingToolConfirm.value = null
                com.quiddity.app.active.OperationNotifyController.dismiss()
                com.quiddity.app.active.ReplyOverlayController.endReply(conversationId)
                _errorEvent.value = event.throwable.message ?: "未知错误"
                _chatError.value = chatRepository.classify(event.throwable)
            }
        }
    }

    fun stripThinkingEcho(msg: Message): Message {
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
    fun raiseStorageError() {
        _errorEvent.value = "存储写入失败，消息可能未保存"
        _chatError.value = ChatError.Unknown(
            userMessage = "存储写入失败，消息可能未保存",
            cause = null
        )
    }
    suspend fun markSceneInjectedIfUnchanged(sceneAtStart: String) {
        val conv = conversation.value ?: return
        if (conv.scene == sceneAtStart && conv.scene.isNotBlank() && !conv.sceneInjected) {
            conversationRepository.updateConversation(conv.copy(sceneInjected = true))
        }
    }
    suspend fun cleanupStaleStreamingMessages() {
        val current = _messages.value
        if (current.none { it.isStreaming }) return
        val cleaned = current.map { msg ->
            if (msg.isStreaming) msg.copy(isStreaming = false) else msg
        }
        // 批量替换：单次 IO，比 N 次 updateMessage 快一个数量级。
        conversationRepository.replaceMessages(conversationId, cleaned)
    }
    fun settleInterruptedStreams() {
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
}
