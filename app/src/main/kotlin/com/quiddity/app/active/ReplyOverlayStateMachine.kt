package com.quiddity.app.active

import com.quiddity.app.data.model.ConversationType

/**
 * 悬浮窗回复状态聚合中心（纯 Kotlin，JVM 可单测）。
 *
 * 负责：
 * - 按会话隔离回复状态，跨会话聚合计数；
 * - Agent 工具动作与完成回复气泡的优先级调度：工具动作 > 回复气泡 > 状态文本；
 * - 气泡按完成顺序排队、逐个消费。
 *
 * 全部状态变更均加锁，保证多会话并发（私聊 / 群聊 / Agent 同时回复）不互相覆盖。
 */
class ReplyOverlayStateMachine {

    data class ActiveReply(
        val conversationId: String,
        val conversationType: ConversationType,
        val startedAt: Long = System.currentTimeMillis()
    )

    data class ReplyBubble(
        val text: String,
        val conversationId: String,
        val conversationType: ConversationType,
        val createdAt: Long = System.currentTimeMillis()
    )

    private val lock = Any()
    private val active = LinkedHashMap<String, ActiveReply>()
    private val toolActions = LinkedHashMap<String, String>()
    /**
     * 当前唯一待展示的回复气泡。
     *
     * 悬浮窗只保留一条消息：新气泡到达时直接替换旧气泡（旧消息不再排队重播），
     * 展示超过 4s 自动消费、点击回复立即消费，均不会再次出现。
     */
    private var bubble: ReplyBubble? = null

    val activeCount: Int
        get() = synchronized(lock) { active.size }

    val hasVisibleContent: Boolean
        get() = synchronized(lock) {
            active.isNotEmpty() || toolActions.isNotEmpty() || bubble != null
        }

    fun startReply(conversationId: String, type: ConversationType) {
        synchronized(lock) {
            if (!active.containsKey(conversationId)) {
                active[conversationId] = ActiveReply(conversationId, type)
            }
        }
    }

    fun endReply(conversationId: String) {
        synchronized(lock) {
            active.remove(conversationId)
            toolActions.remove(conversationId)
        }
    }

    fun showToolAction(conversationId: String, actionText: String) {
        synchronized(lock) {
            if (active.containsKey(conversationId)) {
                toolActions[conversationId] = actionText
            }
        }
    }

    fun clearToolAction(conversationId: String) {
        synchronized(lock) { toolActions.remove(conversationId) }
    }

    fun enqueueBubble(
        text: String,
        conversationId: String,
        conversationType: ConversationType
    ) {
        synchronized(lock) {
            bubble = ReplyBubble(text, conversationId, conversationType)
        }
    }

    fun consumeBubble(): ReplyBubble? = synchronized(lock) {
        val current = bubble
        bubble = null
        current
    }

    fun nextBubble(): ReplyBubble? = synchronized(lock) {
        bubble
    }

    /** 当前需要展示的工具动作文案；多个会话同时操作时取最早开始者。 */
    fun currentToolAction(): String? = synchronized(lock) {
        toolActions.values.firstOrNull()
    }

    fun aggregateStatusText(): String = synchronized(lock) {
        when (active.size) {
            0 -> ""
            1 -> "正在回复…"
            else -> "${active.size} 个对话正在回复…"
        }
    }

    fun clearAll() {
        synchronized(lock) {
            active.clear()
            toolActions.clear()
            bubble = null
        }
    }

    companion object {
        /**
         * 悬浮窗是否应保持显示：设置开启 && 应用不可见 &&（有可见内容 || 输入模式）。
         *
         * 输入模式即使没有待展示内容也必须保留窗口（用户正在输入），
         * 否则关闭输入框会把正在输入的气泡一起带走。
         */
        fun shouldKeepWindow(
            enabled: Boolean,
            appVisible: Boolean,
            hasVisibleContent: Boolean,
            inputModeActive: Boolean
        ): Boolean = enabled && !appVisible && (hasVisibleContent || inputModeActive)
    }
}
