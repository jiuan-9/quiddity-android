package com.quiddity.app.active

import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.MessageToolTrace
import com.quiddity.app.data.model.Role
import com.quiddity.app.data.repo.ChatRepository
import com.quiddity.app.di.ServiceLocator
import com.quiddity.app.util.IdGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 悬浮窗快捷回复桥：从系统悬浮窗直接向会话发送消息并触发 AI 回复。
 *
 * - 私聊 / Agent：追加用户消息后调用 [ChatRepository.streamAssistantReply]，
 *   事件处理器把流式消息落盘并重新入队悬浮窗气泡；
 * - 群聊：只追加用户消息（群聊回复由群内点名机制触发，悬浮窗不代为调度）；
 * - Agent 需要确认的工具调用一律自动拒绝（悬浮窗没有确认 UI，安全兜底）。
 */
object OverlayReplyBridge {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var sending = false

    /** 本次回复累计内容（悬浮窗最终气泡用，不落库）。 */
    private val replyAccumulator = StringBuilder()

    fun send(conversationId: String, type: ConversationType, text: String) {
        val content = text.trim()
        if (content.isEmpty()) return
        if (sending) return
        sending = true
        scope.launch {
            try {
                val convRepo = ServiceLocator.conversationRepository
                val conv = convRepo.getConversation(conversationId) ?: return@launch
                val now = System.currentTimeMillis()
                val userMsg = Message(
                    id = IdGenerator.newId(IdGenerator.Prefix.USER_MESSAGE),
                    conversationId = conv.id,
                    role = Role.USER,
                    content = content,
                    timestamp = now
                )
                convRepo.appendMessage(userMsg)
                // 群聊快捷发送只追加消息（回复靠群内点名触发）：
                // 给用户「已发送」反馈，避免发送后毫无提示
                if (type == ConversationType.GROUP) {
                    ReplyOverlayController.showTransientStatus("已发送")
                    return@launch
                }
                ReplyOverlayController.startReply(conv.id, type)
                val history = convRepo.observeMessages(conv.id).value
                replyAccumulator.setLength(0)
                ServiceLocator.chatRepository.streamAssistantReply(
                    conv = conv,
                    history = history,
                    memoryStrategy = null,
                    thinking = ""
                ) { event ->
                    handleEvent(conv, event)
                }
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                ReplyOverlayController.endReply(conversationId)
            } finally {
                settleInterruptedStreams(conversationId)
                sending = false
            }
        }
    }

    /** 流被中断 / 出错时把残留 streaming 消息标记为完成，避免光标卡死。 */
    private suspend fun settleInterruptedStreams(conversationId: String) {
        val convRepo = ServiceLocator.conversationRepository
        val current = convRepo.observeMessages(conversationId).value
        if (current.none { it.isStreaming }) return
        convRepo.replaceMessages(
            conversationId,
            current.map { msg -> if (msg.isStreaming) msg.copy(isStreaming = false) else msg }
        )
    }

    private suspend fun handleEvent(conv: Conversation, event: ChatRepository.Event) {
        val convRepo = ServiceLocator.conversationRepository
        when (event) {
            is ChatRepository.Event.NewMessage ->
                convRepo.appendMessage(event.message)
            is ChatRepository.Event.UpdateMessage ->
                convRepo.updateMessage(event.message)
            is ChatRepository.Event.CompleteMessage -> {
                convRepo.updateMessage(event.message)
                replyAccumulator.append(event.message.content)
            }
            is ChatRepository.Event.ToolConfirmBatch ->
                // 悬浮窗无确认 UI：一律拒绝需要确认的工具操作（安全兜底）
                event.resume(false)
            is ChatRepository.Event.Done -> {
                // 回复结束：本轮累计的完整回复作为最终气泡展示
                val fullReply = replyAccumulator.toString().trim()
                replyAccumulator.setLength(0)
                if (fullReply.isNotBlank()) {
                    ReplyOverlayController.enqueueBubble(
                        text = fullReply,
                        conversationId = conv.id,
                        conversationType = conv.type
                    )
                }
                ReplyOverlayController.endReply(conv.id)
            }
            is ChatRepository.Event.Error -> {
                replyAccumulator.setLength(0)
                ReplyOverlayController.endReply(conv.id)
            }
            is ChatRepository.Event.Truncated -> {
                replyAccumulator.setLength(0)
                ReplyOverlayController.endReply(conv.id)
            }
            is ChatRepository.Event.Notice -> Unit
            is ChatRepository.Event.ToolUse ->
                attachToolTrace(conv, MessageToolTrace(event.toolName, ok = false, summary = null))
            is ChatRepository.Event.ToolResult ->
                updateToolTrace(conv, event.toolName, event.ok, event.summary)
            is ChatRepository.Event.AgentRoundEffects -> Unit
        }
    }

    /** 工具开始：把「运行中」痕迹实时挂到当前 AI 消息上（悬浮窗桥接路径）。 */
    private suspend fun attachToolTrace(conv: Conversation, trace: MessageToolTrace) {
        val msgs = ServiceLocator.conversationRepository.observeMessages(conv.id).value
        val idx = msgs.indexOfLast {
            it.role == Role.ASSISTANT && !it.isNotice && !it.isThinking
        }
        if (idx < 0) return
        val target = msgs[idx]
        ServiceLocator.conversationRepository.updateMessage(
            target.copy(toolTraces = target.toolTraces + trace)
        )
    }

    /** 工具结束：更新对应痕迹的结果状态。 */
    private suspend fun updateToolTrace(
        conv: Conversation,
        name: String,
        ok: Boolean,
        summary: String?
    ) {
        val msgs = ServiceLocator.conversationRepository.observeMessages(conv.id).value
        val idx = msgs.indexOfLast {
            it.role == Role.ASSISTANT && !it.isNotice && !it.isThinking
        }
        if (idx < 0) return
        val target = msgs[idx]
        val traces = target.toolTraces.toMutableList()
        val traceIdx = traces.indexOfLast { it.name == name }
        if (traceIdx >= 0) {
            traces[traceIdx] = MessageToolTrace(name, ok, summary)
        } else {
            traces += MessageToolTrace(name, ok, summary)
        }
        ServiceLocator.conversationRepository.updateMessage(target.copy(toolTraces = traces))
    }

}
