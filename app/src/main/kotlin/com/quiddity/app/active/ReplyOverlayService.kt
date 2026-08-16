package com.quiddity.app.active

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.quiddity.app.util.CrashLogger

/**
 * 回复悬浮窗服务：承载 [ReplyOverlayView] 系统窗口。
 *
 * 普通 started service（不占前台通知）；窗口仅在「设置开启 + 应用不可见 + 有可见内容」
 * 时由 [ReplyOverlayController] 启动。进程存活期间由控制器驱动渲染与回收。
 */
class ReplyOverlayService : Service(), ReplyOverlayView.Listener {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: ReplyOverlayView

    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowAdded = false
    private var params: WindowManager.LayoutParams? = null

    private var posX = 0
    private var posY = 0

    private var bubbleDismissRunnable: Runnable? = null

    /** 当前吸附边（true = 屏幕左侧），窗口伸缩时以头像为锚点保持不动。 */
    private var snappedLeft = true

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = ReplyOverlayView(this, this)
        posX = screenWidth() - dp(56)
        posY = screenHeight() / 2 - dp(24)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureWindowAdded()
        snapToEdge()
        ReplyOverlayController.onServiceStarted(this)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 渲染当前聚合状态：工具动作 > 回复气泡 > 状态文本。 */
    fun render(
        count: Int,
        toolAction: String?,
        statusText: String,
        bubble: ReplyOverlayStateMachine.ReplyBubble?
    ) {
        if (!windowAdded) ensureWindowAdded()
        overlayView.setBadgeCount(count)
        when {
            toolAction != null -> {
                cancelBubbleTimer()
                overlayView.showStatusText(toolAction)
                layoutWindow()
            }
            bubble != null -> {
                overlayView.showReplyBubble(bubble.text)
                layoutWindow()
            }
            else -> {
                overlayView.showStatusText(statusText)
                layoutWindow()
            }
        }
    }

    /** 展示回复气泡并启动自动回收计时（4 秒）。 */
    fun showBubble(
        bubble: ReplyOverlayStateMachine.ReplyBubble,
        onDismissed: () -> Unit
    ) {
        cancelBubbleTimer()
        bubbleDismissRunnable = Runnable {
            bubbleDismissRunnable = null
            onDismissed()
        }.also { mainHandler.postDelayed(it, BUBBLE_DISPLAY_MS) }
    }

    /** 工具动作覆盖时取消气泡计时，避免气泡被误消费。 */
    fun cancelBubbleTimer() {
        bubbleDismissRunnable?.let { mainHandler.removeCallbacks(it) }
        bubbleDismissRunnable = null
    }

    fun applyAvatar(uri: String?) {
        overlayView.setAvatar(uri)
    }

    override fun onPositionChanged(x: Int, y: Int) {
        posX = x
        posY = y
        updateWindowPosition()
    }

    override fun onDragEnd() {
        snapToEdge()
    }

    override fun onSnapped(left: Boolean, x: Int, y: Int) {
        posX = x
        posY = y
        updateWindowPosition()
    }

    override fun onAvatarClicked() {
        ReplyOverlayController.openApp()
    }

    override fun onBubbleClicked() {
        cancelBubbleTimer()
        ReplyOverlayController.openLastConversation()
    }

    override fun onBubbleDismissed() {
        ReplyOverlayController.onBubbleDismissed()
    }

    override fun onDestroy() {
        cancelBubbleTimer()
        ReplyOverlayController.onServiceStopped(this)
        if (windowAdded) {
            runCatching { windowManager.removeView(overlayView) }
            windowAdded = false
        }
        super.onDestroy()
    }

    private fun ensureWindowAdded() {
        if (windowAdded) return
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = posX
            y = posY
        }
        runCatching {
            windowManager.addView(overlayView, layoutParams)
            params = layoutParams
            windowAdded = true
        }.onFailure {
            CrashLogger.logException(this, it, "ReplyOverlayService.addView")
            stopSelf()
        }
    }

    private fun updateWindowPosition() {
        val layoutParams = params ?: return
        if (!windowAdded) return
        layoutParams.x = posX
        layoutParams.y = posY
        runCatching { windowManager.updateViewLayout(overlayView, layoutParams) }
    }

    /** 松手吸附：按窗口中心判定贴左 / 贴右，并夹紧 Y。 */
    private fun snapToEdge() {
        if (overlayView.isDragging || !windowAdded) return
        val windowWidth = overlayView.measuredWidth.takeIf { it > 0 } ?: dp(48)
        val windowHeight = overlayView.measuredHeight.takeIf { it > 0 } ?: dp(48)
        val left = posX + windowWidth / 2 < screenWidth() / 2
        snappedLeft = left
        val snappedX = if (left) dp(8) else screenWidth() - windowWidth - dp(8)
        val snappedY = posY.coerceIn(0, (screenHeight() - windowHeight - dp(8)).coerceAtLeast(0))
        posX = snappedX
        posY = snappedY
        overlayView.snapToEdge(left, snappedX, snappedY)
        layoutWindow()
    }

    /**
     * 同步测量并按当前内容重设窗口尺寸 / 位置。
     *
     * 关键：尺寸与位置在同一帧内一起提交（updateViewLayout），
     * 气泡出现/收起导致的窗口伸缩不会产生「旧位置 + 新尺寸」的中间帧，
     * 从而保证头像位置不跳动。贴边侧以头像为锚点：左贴 → x 固定；右贴 → 头像右缘贴屏幕右缘。
     */
    private fun layoutWindow() {
        val layoutParams = params ?: return
        if (!windowAdded || overlayView.isDragging) return
        overlayView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val width = overlayView.measuredWidth.takeIf { it > 0 } ?: dp(48)
        val height = overlayView.measuredHeight.takeIf { it > 0 } ?: dp(48)
        layoutParams.width = width
        layoutParams.height = height
        // 头像锚定：贴左 → 头像左缘在 dp(8)；贴右 → 头像右缘距屏幕右缘 dp(8)
        layoutParams.x = if (snappedLeft) dp(8) else screenWidth() - width - dp(8)
        layoutParams.y = posY.coerceIn(0, (screenHeight() - height - dp(8)).coerceAtLeast(0))
        posX = layoutParams.x
        posY = layoutParams.y
        runCatching { windowManager.updateViewLayout(overlayView, layoutParams) }
    }

    private fun screenWidth(): Int = resources.displayMetrics.widthPixels

    private fun screenHeight(): Int = resources.displayMetrics.heightPixels

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val BUBBLE_DISPLAY_MS = 4_000L
    }
}
