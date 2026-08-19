package com.quiddity.app.active

import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.data.model.Message
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
                val bubbleText = event.message.content.trim().takeIf { it.isNotBlank() }
                // 仅动作/神态描写（无实际台词）的消息对悬浮窗不可读，不展示为气泡
                if (bubbleText != null && !event.message.isNotice && !event.message.isThinking &&
                    !com.quiddity.app.data.repo.isActionOnlyReply(bubbleText)
                ) {
                    ReplyOverlayController.enqueueBubble(
                        text = bubbleText,
                        conversationId = conv.id,
                        conversationType = conv.type
                    )
                }
            }
            is ChatRepository.Event.ToolConfirmBatch ->
                // 悬浮窗无确认 UI：一律拒绝需要确认的工具操作（安全兜底）
                event.resume(false)
            is ChatRepository.Event.Done ->
                ReplyOverlayController.endReply(conv.id)
            is ChatRepository.Event.Error ->
                ReplyOverlayController.endReply(conv.id)
            is ChatRepository.Event.Truncated ->
                ReplyOverlayController.endReply(conv.id)
            is ChatRepository.Event.Notice -> Unit
            is ChatRepository.Event.ToolUse -> Unit
            is ChatRepository.Event.ToolResult -> Unit
            is ChatRepository.Event.AgentRoundEffects -> Unit
        }
    }

}
