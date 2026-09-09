package com.quiddity.app.ui.chat

import androidx.lifecycle.viewModelScope
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import com.quiddity.app.data.repo.ChatRepository
import com.quiddity.app.data.repo.ConversationRepository
import com.quiddity.app.domain.GroupReplyQueue
import com.quiddity.app.util.IdGenerator
import com.quiddity.app.util.QuiddityConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class GroupController(
    private val state: ChatViewModelState,
    private val conversationRepository: ConversationRepository,
    private val chatRepository: ChatRepository,
    private val viewModelScope: CoroutineScope,
    private val conversationId: String,
    private val stream: StreamController
) {
    private val _messages get() = state._messages
    private val _isGenerating get() = state._isGenerating
    private val _errorEvent get() = state._errorEvent
    private val _groupQueue get() = state._groupQueue
    private val conversation get() = state.conversation
    private val messages get() = state.messages
    private var groupStreamJob get() = state.groupStreamJob; set(value) { state.groupStreamJob = value }
    private var sendDelayJob get() = state.sendDelayJob; set(value) { state.sendDelayJob = value }
    private val groupQueueEngine get() = state.groupQueueEngine
    fun isGroup(): Boolean = conversation.value?.type == ConversationType.GROUP

    /** 群聊成员会话列表（按加入顺序）。 */
    fun groupMembers(): List<Conversation> =
        conversation.value?.memberConversationIds.orEmpty()
            .mapNotNull { conversationRepository.getConversation(it) }
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
    fun enqueueGroupMemberWithFrozen(memberId: String, frozen: List<Message>) {
        if (!isGroup()) return
        val group = conversation.value ?: return
        if (memberId !in group.memberConversationIds) return
        if (groupStreamJob?.isActive == true && groupQueueEngine.isFull) return
        if (groupQueueEngine.contains(memberId)) return
        if (!groupQueueEngine.enqueue(memberId, frozen)) return
        syncGroupQueue()
        if (groupStreamJob?.isActive != true) startGroupQueueProcessor()
    }
    fun syncGroupQueue() {
        _groupQueue.value = groupQueueEngine.snapshot()
    }
    fun queuePosition(memberId: String): Int = groupQueueEngine.positionOf(memberId)

    fun isQueueFull(): Boolean = groupQueueEngine.isFull

    /**
     * 群聊队列处理器：串行消费队列，每个成员失败重试 5 次并逐次通知；
     * 任一成员 5 次失败后整个队列取消、所有头像恢复（方案十三）。
     */
    fun startGroupQueueProcessor() {
        groupStreamJob = viewModelScope.launch {
            val selfJob = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
            _isGenerating.value = true
            stream.markReplyStarted()
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
                    stream.markReplyEnded()
                }
                stream.settleInterruptedStreams()
                stream.notifyIdleIfNoWork()
            }
            awaitGroupMemoryCompressionIfNeeded()
            stream.notifyIdleIfNoWork()
        }
    }
    suspend fun runGroupMemberReplyWithRetry(item: GroupReplyQueue.Item): Boolean {
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
                        else -> stream.handleStreamEvent(event)
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
    suspend fun rollbackFailedGroupAttempt(memberId: String, beforeIds: Set<String>) {
        val current = conversationRepository.observeMessages(conversationId).value
        val kept = current.filterNot { msg ->
            msg.role == Role.ASSISTANT && msg.senderId == memberId && msg.id !in beforeIds
        }
        if (kept.size != current.size) {
            conversationRepository.replaceMessages(conversationId, kept)
        }
    }
    suspend fun awaitGroupMemoryCompressionIfNeeded() {
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
    fun sendGroupUserMessage(
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
}
