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
 * - 仅在 设置开启 && 应用不可见 && 有可见内容 时显示窗口；
 * - 气泡展示期间不消费（头部仍保留），工具动作可临时覆盖；动作结束或展示超时后再消费。
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

    private var lastBubbleConversation: Pair<String, ConversationType>? = null

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

    fun clearToolAction(conversationId: String) {
        machine.clearToolAction(conversationId)
        refreshWindow()
    }

    fun enqueueBubble(
        text: String,
        conversationId: String,
        conversationType: ConversationType
    ) {
        machine.enqueueBubble(text, conversationId, conversationType)
        refreshWindow()
    }

    fun onServiceStarted(instance: ReplyOverlayService) {
        service = instance
        service?.applyAvatar(overlayAvatarUri)
        render()
    }

    fun onServiceStopped(instance: ReplyOverlayService) {
        if (service === instance) service = null
    }

    fun dismissWindow() {
        mainHandler.post {
            service?.stopSelf()
        }
    }

    private fun refreshWindow() {
        mainHandler.post {
            if (!enabled || appVisible || !machine.hasVisibleContent) {
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
        val tool = machine.currentToolAction()
        val count = machine.activeCount
        val status = machine.aggregateStatusText()
        val bubble = machine.nextBubble()
        if (tool != null) {
            // 工具动作优先级最高：临时覆盖气泡展示
            showingBubble = false
            svc.render(count, tool, status, null)
            return
        }
        if (bubble != null) {
            if (!showingBubble) {
                showingBubble = true
                lastBubbleConversation = bubble.conversationId to bubble.conversationType
                svc.render(count, null, status, bubble)
                svc.showBubble(
                    bubble = bubble,
                    onDismissed = {
                        onBubbleDismissed()
                    }
                )
            }
            return
        }
        // 仅活跃回复状态（无气泡可展示）
        showingBubble = false
        svc.render(count, null, status, null)
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

    /** 点击气泡 → 打开最近一次展示气泡所属会话。 */
    fun openLastConversation() {
        val pair = lastBubbleConversation
        val context = ServiceLocator.applicationContext
        if (pair == null) {
            openApp()
            return
        }
        context.startActivity(MainActivity.conversationIntent(context, pair.first, pair.second))
    }

    /** 仅供测试读取聚合状态。 */
    fun snapshotForTest(): ReplyOverlayStateMachine = machine
}
