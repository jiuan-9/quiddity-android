package com.quiddity.app.active

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.quiddity.app.MainActivity
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.di.ServiceLocator
import com.quiddity.app.util.CrashLogger

/**
 * 回复悬浮窗全局控制器（进程内单例）。
 *
 * 状态聚合在 [ReplyOverlayStateMachine]；本类负责：
 * 1) 桥接聊天流 / Agent 工具执行挂点；
 * 2) 应用可见性（QuiddityApp 的 ActivityLifecycleCallbacks 驱动）；
 * 3) 设置开关与头像（SettingsViewModel 驱动）；
 * 4) 决定何时启动 / 停止 [ReplyOverlayService]，并把聚合状态渲染到窗口。
 *
 * 显示规则：
 * - 仅当 设置开启 && 应用不可见 && 有可见内容（进行中回复 / 工具动作 / 待展示气泡）时
 *   显示窗口；没有进行中会话时**不显示**悬浮窗，避免常驻头像打扰；
 * - 气泡只保留最新一条：新气泡替换旧气泡，展示超过 4s / 点击回复后消费且不再重播；
 * - 点击气泡直接进入输入模式（弹出输入气泡 + 自动滑出输入法键盘），
 *   发送后由 [OverlayReplyBridge] 在后台追加用户消息并触发 AI 回复；
 * - Agent 工具动作以气泡样式实时展示。
 */
object ReplyOverlayController {

    private val machine = ReplyOverlayStateMachine()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var service: ReplyOverlayService? = null

    @Volatile
    private var appVisible = true

    @Volatile
    private var enabled = false

    @Volatile
    var overlayAvatarUri: String? = null
        private set

    /** 当前正在展示的气泡（未消费；展示结束 / 被工具动作覆盖时复位）。 */
    @Volatile
    private var showingBubble = false

    /** 悬浮窗是否处于输入模式（输入气泡打开，渲染层不得覆盖输入框）。 */
    @Volatile
    private var inputModeActive = false

    private var lastBubbleConversation: Pair<String, ConversationType>? = null

    /** 当前展示气泡的文本（用于新气泡到达时立即替换旧气泡）。 */
    @Volatile
    private var lastBubbleText: String? = null

    /** 输入模式对应的会话（点击气泡时记录，发送时使用）。 */
    @Volatile
    private var inputConversation: Pair<String, ConversationType>? = null

    /** 瞬时状态文案（如「已发送」），到期自动清除；仅在没有进行中回复时展示。 */
    @Volatile
    private var transientStatus: String? = null

    private var transientStatusUntil = 0L

    private val transientRunnable = Runnable {
        if (System.currentTimeMillis() >= transientStatusUntil) {
            transientStatus = null
            refreshWindow()
        }
    }

    /** QuiddityApp 在 ActivityLifecycleCallbacks 中驱动。 */
    fun setAppVisible(visible: Boolean) {
        if (appVisible == visible) return
        appVisible = visible
        refreshWindow()
    }

    /** 设置变更（SettingsViewModel 或启动时读取后调用）。 */
    fun updateEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (!value) {
            machine.clearAll()
            showingBubble = false
            inputModeActive = false
            dismissWindow()
        } else {
            refreshWindow()
        }
    }

    fun updateAvatarUri(uri: String?) {
        if (overlayAvatarUri == uri) return
        overlayAvatarUri = uri
        service?.applyAvatar(uri)
    }

    fun startReply(conversationId: String, type: ConversationType) {
        machine.startReply(conversationId, type)
        refreshWindow()
    }

    fun endReply(conversationId: String) {
        machine.endReply(conversationId)
        refreshWindow()
    }

    fun showToolAction(conversationId: String, actionText: String) {
        machine.showToolAction(conversationId, actionText)
        refreshWindow()
    }

    /** 展示瞬时状态（发送反馈等），到期自动消失。 */
    fun showTransientStatus(text: String, durationMs: Long = TRANSIENT_STATUS_MS) {
        mainHandler.removeCallbacks(transientRunnable)
        transientStatus = text
        transientStatusUntil = System.currentTimeMillis() + durationMs
        mainHandler.postDelayed(transientRunnable, durationMs)
        refreshWindow()
    }

    fun clearToolAction(conversationId: String) {
        machine.clearToolAction(conversationId)
        refreshWindow()
    }

    fun enqueueBubble(
        text: String,
        conversationId: String,
        conversationType: ConversationType
    ) {
        // 截断集中在这里：悬浮窗只展示一条短气泡，超长内容统一补「…」
        val display = text.trim().let {
            if (it.length > MAX_BUBBLE_CHARS) it.take(MAX_BUBBLE_CHARS).trimEnd() + "…" else it
        }
        machine.enqueueBubble(display, conversationId, conversationType)
        refreshWindow()
    }

    fun onServiceStarted(instance: ReplyOverlayService) {
        service = instance
        service?.applyAvatar(overlayAvatarUri)
        render()
    }

    fun onServiceStopped(instance: ReplyOverlayService) {
        if (service === instance) service = null
        inputModeActive = false
        if (showingBubble) {
            // 气泡正在展示时窗口被停止（应用回前台 / 系统回收）：用户已看到该消息，
            // 消费并清空展示记录，避免同一消息下次离屏时重播
            showingBubble = false
            lastBubbleText = null
            lastBubbleConversation = null
            machine.consumeBubble()
        } else {
            // 气泡尚未展示（应用在前台时回复完成入队）：保留气泡，
            // 下次离屏时继续展示
            showingBubble = false
            lastBubbleText = null
            lastBubbleConversation = null
        }
    }

    fun dismissWindow() {
        mainHandler.post {
            service?.stopSelf()
        }
    }

    /** 悬浮窗拖动结束：重置气泡展示记录，让当前气泡重新滑出（拖动期间气泡被隐藏）。 */
    fun onOverlayDragEnd() {
        mainHandler.post {
            showingBubble = false
            lastBubbleText = null
            lastBubbleConversation = null
            render()
        }
    }

    private fun refreshWindow() {
        mainHandler.post {
            val keep = ReplyOverlayStateMachine.shouldKeepWindow(
                    enabled = enabled,
                    appVisible = appVisible,
                    hasVisibleContent = machine.hasVisibleContent,
                    inputModeActive = inputModeActive
                ) || transientStatus != null
            if (!keep) {
                service?.stopSelf()
                return@post
            }
            if (service == null) {
                val context = ServiceLocator.applicationContext
                runCatching {
                    context.startService(Intent(context, ReplyOverlayService::class.java))
                }.onFailure {
                    CrashLogger.logException(context, it, "ReplyOverlayController.startService")
                }
            } else {
                render()
            }
        }
    }

    private fun render() {
        val svc = service ?: return
        // 输入模式打开期间不渲染气泡/状态，避免覆盖输入框
        if (inputModeActive) return
        val tool = machine.currentToolAction()
        val count = machine.activeCount
        val status = buildStatusText(count).ifBlank { transientStatus.orEmpty() }
        val bubble = machine.nextBubble()
        if (tool != null) {
            // 工具动作优先级最高：以气泡样式展示，动作结束（clearToolAction）后恢复
            showingBubble = false
            // 清空上次气泡记录：工具动作结束后下一轮渲染会重新展示回复气泡并重启 4s 计时
            lastBubbleText = null
            lastBubbleConversation = null
            svc.showToolBubble(count, tool)
            return
        }
        if (bubble != null) {
            val bubbleChanged = !showingBubble ||
                bubble.text != lastBubbleText ||
                bubble.conversationId != lastBubbleConversation?.first
            if (bubbleChanged) {
                showingBubble = true
                lastBubbleText = bubble.text
                lastBubbleConversation = bubble.conversationId to bubble.conversationType
                svc.render(count, status, bubble)
                svc.showBubble(
                    bubble = bubble,
                    onDismissed = {
                        onBubbleDismissed()
                    }
                )
            }
            return
        }
        // 无气泡可展示：仅头像 + 状态文本（无会话进行时保持空状态）
        showingBubble = false
        lastBubbleText = null
        svc.render(count, status, null)
    }

    /** 人性化状态文案：单个会话显示「名字」正在回复，多个会话聚合计数。 */
    private fun buildStatusText(count: Int): String {
        if (count == 0) return ""
        if (count == 1) {
            val id = machine.activeConversationIds().firstOrNull() ?: return "正在回复…"
            val name = ServiceLocator.conversationRepository.getConversation(id)
                ?.persona?.name?.takeIf { it.isNotBlank() }
            return if (name != null) "「$name」正在回复…" else "正在回复…"
        }
        return "$count 个对话正在回复…"
    }

    /** 气泡展示完成（Service 回调）→ 消费队首并渲染下一条。 */
    fun onBubbleDismissed() {
        mainHandler.post {
            showingBubble = false
            machine.consumeBubble()
            refreshWindow()
        }
    }

    /** 点击悬浮窗头像 → 打开应用。 */
    fun openApp() {
        val context = ServiceLocator.applicationContext
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    /** 点击气泡 → 消费该消息并进入输入模式（有会话上下文时）。 */
    fun onBubbleClicked() {
        mainHandler.post {
            showingBubble = false
            val pair = lastBubbleConversation
            // 点击即消费：该消息不再重播
            machine.consumeBubble()
            if (pair == null) {
                openApp()
                return@post
            }
            inputConversation = pair
            inputModeActive = true
            service?.startInputMode()
            refreshWindow()
        }
    }

    /** 输入模式关闭（发送完成 / 取消 / 服务回收）。 */
    fun onInputModeClosed() {
        mainHandler.post {
            inputModeActive = false
            service?.closeInputMode()
            refreshWindow()
        }
    }

    /** 悬浮窗输入框发送：交给 [OverlayReplyBridge] 追加消息并触发 AI 回复。 */
    fun onOverlayInputSent(text: String) {
        val pair = inputConversation ?: return
        inputConversation = null
        inputModeActive = false
        mainHandler.post {
            service?.closeInputMode()
            OverlayReplyBridge.send(pair.first, pair.second, text)
        }
    }

    /** 供 Service 在输入模式关闭后强制重渲染（服务线程即主线程）。 */
    fun renderNow() {
        mainHandler.post { render() }
    }

    /** 悬浮窗是否应当保持显示（设置开启 && 应用不可见）。Service 自启/重建时调用。 */
    fun keepWindowVisible(): Boolean = ReplyOverlayStateMachine.shouldKeepWindow(
        enabled = enabled,
        appVisible = appVisible,
        hasVisibleContent = machine.hasVisibleContent,
        inputModeActive = inputModeActive
    ) || transientStatus != null

    private const val MAX_BUBBLE_CHARS = 80
    private const val TRANSIENT_STATUS_MS = 2_500L
}
