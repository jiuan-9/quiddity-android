package com.quiddity.app.active

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.inputmethod.InputMethodManager
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import com.quiddity.app.util.CrashLogger

/**
 * 回复悬浮窗服务：承载 [ReplyOverlayView] 系统窗口。
 *
 * 普通 started service（不占前台通知）；由 [ReplyOverlayController] 在
 * 设置开启 + 应用不可见 + 有可见内容时启动，无内容时自动回收。
 * - 无交互 2s 后自动淡化窗口（alpha → 0.35），任意交互恢复全透明；
 * - 点击气泡进入输入模式（输入气泡 + 自动滑出输入法键盘）；
 * - 消息气泡展示 4s 后滑回并自动消费（仅保留最新一条）；
 * - Agent 工具调用实时以气泡样式展示「正在做什么」。
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

    /** 空闲淡化定时器：无交互 2s 后淡化为半透明，交互后恢复全透明。 */
    private var idleFadeRunnable: Runnable? = null

    /** 输入模式是否激活（窗口可聚焦以弹出输入法）。 */
    private var inputModeActive = false

    /** 当前吸附边（true = 屏幕左侧），窗口伸缩时以头像为锚点保持不动。 */
    private var snappedLeft = true

    /** 松手吸附位置动画（拖动中取消）。 */
    private var snapAnimator: ValueAnimator? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = ReplyOverlayView(this, this)
        // 记住用户上次拖动的位置：服务随内容回收重建时不再跳回默认点
        posX = if (lastPosX >= 0) lastPosX else screenWidth() - dp(56)
        posY = if (lastPosY >= 0) lastPosY else screenHeight() / 2 - dp(24)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureWindowAdded()
        snapToEdge()
        ReplyOverlayController.onServiceStarted(this)
        // 进程被系统回收后由 START_STICKY 重建窗口；应用可见 / 开关关闭时立即自停
        if (!ReplyOverlayController.keepWindowVisible()) {
            stopSelf()
            return START_NOT_STICKY
        }
        resetIdleFade()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** 渲染当前聚合状态：回复气泡 > 状态文本（工具动作由 [showToolBubble] 独立渲染）。 */
    fun render(
        count: Int,
        statusText: String,
        bubble: ReplyOverlayStateMachine.ReplyBubble?
    ) {
        if (!windowAdded) ensureWindowAdded()
        if (inputModeActive) return
        overlayView.setReplying(count > 0)
        overlayView.setBadgeCount(count)
        when {
            bubble != null -> {
                overlayView.showReplyBubble(bubble.text)
                layoutWindow()
            }
            else -> {
                if (statusText.isBlank()) {
                    // 无进行中会话 / 无状态文本：隐藏气泡，避免出现空白药丸
                    overlayView.hideBubble()
                } else {
                    overlayView.showStatusText(statusText)
                }
                layoutWindow()
            }
        }
        resetIdleFade()
    }

    /** 展示回复气泡并启动自动回收计时（4 秒）。 */
    fun showBubble(
        bubble: ReplyOverlayStateMachine.ReplyBubble,
        onDismissed: () -> Unit
    ) {
        cancelBubbleTimer()
        bubbleDismissRunnable = Runnable {
            bubbleDismissRunnable = null
            // 人性化：展示结束先让气泡滑回再消费，避免瞬间消失
            overlayView.dismissBubble(onEnd = onDismissed)
        }.also { mainHandler.postDelayed(it, BUBBLE_DISPLAY_MS) }
        resetIdleFade()
    }

    /** Agent 工具动作：以气泡样式实时展示（动作结束由 clearToolAction 恢复）。 */
    fun showToolBubble(count: Int, text: String) {
        if (!windowAdded) ensureWindowAdded()
        if (inputModeActive) return
        cancelBubbleTimer()
        overlayView.setBadgeCount(count)
        overlayView.showToolActionBubble(text)
        layoutWindow()
        resetIdleFade()
    }

    /** 工具动作覆盖时取消气泡计时，避免气泡被误消费。 */
    fun cancelBubbleTimer() {
        bubbleDismissRunnable?.let { mainHandler.removeCallbacks(it) }
        bubbleDismissRunnable = null
    }

    /** 打开输入模式：显示输入气泡、窗口可聚焦、自动弹出输入法键盘。 */
    fun startInputMode() {
        if (inputModeActive) return
        inputModeActive = true
        if (!windowAdded) ensureWindowAdded()
        overlayView.startInputMode()
        updateFocusFlags(focusable = true)
        layoutWindow()
        mainHandler.postDelayed({
            overlayView.requestInputFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(overlayView.inputEditText(), InputMethodManager.SHOW_IMPLICIT)
        }, 120L)
        resetIdleFade()
    }

    /** 关闭输入模式：恢复窗口不可聚焦，回到气泡/状态渲染。 */
    fun closeInputMode() {
        if (!inputModeActive) return
        inputModeActive = false
        overlayView.closeInputMode()
        updateFocusFlags(focusable = false)
        layoutWindow()
        ReplyOverlayController.renderNow()
        resetIdleFade()
    }

    private fun updateFocusFlags(focusable: Boolean) {
        val layoutParams = params ?: return
        if (focusable) {
            layoutParams.flags = layoutParams.flags and
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            layoutParams.softInputMode =
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        } else {
            layoutParams.flags = layoutParams.flags or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            layoutParams.softInputMode =
                WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
        }
        runCatching { windowManager.updateViewLayout(overlayView, layoutParams) }
    }

    /** 空闲淡化：无交互 2s 后把窗口淡化，任何交互/展示恢复全透明。 */
    private fun resetIdleFade() {
        idleFadeRunnable?.let { mainHandler.removeCallbacks(it) }
        overlayView.animateWindowAlpha(1f, RESTORE_ANIM_MS)
        idleFadeRunnable = Runnable {
            idleFadeRunnable = null
            if (!inputModeActive) overlayView.animateWindowAlpha(IDLE_ALPHA, IDLE_FADE_ANIM_MS)
        }.also { mainHandler.postDelayed(it, IDLE_FADE_MS) }
    }

    fun applyAvatar(uri: String?) {
        overlayView.setAvatar(uri)
    }

    override fun onPositionChanged(x: Int, y: Int) {
        snapAnimator?.cancel()
        snapAnimator = null
        posX = x
        posY = y
        updateWindowPosition()
    }

    override fun onDragEnd() {
        snapToEdge()
        // 拖动结束后恢复气泡展示（拖动期间气泡被隐藏）；
        // 延迟到吸附动画完成后再恢复，避免布局抢先跳到贴边点打断回弹动画
        mainHandler.postDelayed({
            ReplyOverlayController.onOverlayDragEnd()
        }, SNAP_ANIM_MS + 30L)
    }

    override fun onSnapped(left: Boolean, x: Int, y: Int) {
        // 位置由 animateWindowTo 平滑过渡，这里只接收吸附方向翻转，不直接落点
    }

    override fun onAvatarClicked() {
        ReplyOverlayController.openApp()
    }

    override fun onBubbleClicked() {
        cancelBubbleTimer()
        ReplyOverlayController.onBubbleClicked()
    }

    override fun onBubbleDismissed() {
        ReplyOverlayController.onBubbleDismissed()
    }

    override fun onUserInteraction() {
        resetIdleFade()
    }

    override fun onInputSubmit(text: String) {
        ReplyOverlayController.onOverlayInputSent(text)
    }

    override fun onInputCancel() {
        ReplyOverlayController.onInputModeClosed()
    }

    override fun onInputModeChanged(active: Boolean) {
        if (!active) ReplyOverlayController.onInputModeClosed()
    }

    override fun onDestroy() {
        cancelBubbleTimer()
        idleFadeRunnable?.let { mainHandler.removeCallbacks(it) }
        idleFadeRunnable = null
        snapAnimator?.cancel()
        snapAnimator = null
        overlayView.setReplying(false)
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
            overlayView.playEntrance()
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
        lastPosX = posX
        lastPosY = posY
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
        overlayView.snapToEdge(left, snappedX, snappedY)
        animateWindowTo(snappedX, snappedY)
    }

    /** 松手吸附回弹：位置从当前点平滑过渡到贴边点（约 220ms）。 */
    private fun animateWindowTo(targetX: Int, targetY: Int) {
        val fromX = posX
        val fromY = posY
        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SNAP_ANIM_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                posX = fromX + ((targetX - fromX) * fraction).toInt()
                posY = fromY + ((targetY - fromY) * fraction).toInt()
                updateWindowPosition()
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }
                override fun onAnimationEnd(animation: Animator) {
                    snapAnimator = null
                    if (!cancelled) {
                        posX = targetX
                        posY = targetY
                        updateWindowPosition()
                    }
                }
            })
            start()
        }
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
        // 输入模式下气泡较宽：限制在屏幕内（头像 + 输入框都可见），避免被屏幕边缘裁切
        val effectiveWidth = if (inputModeActive) {
            width.coerceAtMost(screenWidth() - dp(72))
        } else {
            width
        }
        layoutParams.width = effectiveWidth
        layoutParams.height = height
        // 头像锚定：贴左 → 头像左缘在 dp(8)；贴右 → 头像右缘距屏幕右缘 dp(8)
        layoutParams.x = if (snappedLeft) dp(8) else screenWidth() - effectiveWidth - dp(8)
        layoutParams.y = posY.coerceIn(0, (screenHeight() - height - dp(8)).coerceAtLeast(0))
        posX = layoutParams.x
        posY = layoutParams.y
        runCatching { windowManager.updateViewLayout(overlayView, layoutParams) }
        lastPosX = layoutParams.x
        lastPosY = layoutParams.y
    }

    private fun screenWidth(): Int = resources.displayMetrics.widthPixels

    private fun screenHeight(): Int = resources.displayMetrics.heightPixels

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        @Volatile
        var lastPosX = -1
        @Volatile
        var lastPosY = -1

        const val BUBBLE_DISPLAY_MS = 4_000L
        const val IDLE_FADE_MS = 2_000L
        const val IDLE_FADE_ANIM_MS = 500L
        const val RESTORE_ANIM_MS = 180L
        const val SNAP_ANIM_MS = 220L
        const val IDLE_ALPHA = 0.35f
    }
}
