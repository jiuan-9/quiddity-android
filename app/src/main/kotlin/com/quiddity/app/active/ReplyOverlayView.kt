package com.quiddity.app.active

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.graphics.Outline
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import com.quiddity.app.R

/**
 * 悬浮窗视图（头像 + 气泡）。
 *
 * 结构：LinearLayout [头像(含角标)][气泡]，贴右边缘时整体镜像（scaleX=-1）并反向
 * 镜像头像/气泡/角标，保证文字可读；气泡滑出动画按局部坐标 +气泡宽度 → 0，
 * 在镜像与未镜像两种状态下都表现为「从头像一侧滑向屏幕内侧」。
 */
internal class ReplyOverlayView(
    context: Context,
    private val listener: Listener
) : LinearLayout(context) {

    interface Listener {
        /** 拖动中更新窗口位置（由 Service 调用 WindowManager.updateViewLayout）。 */
        fun onPositionChanged(x: Int, y: Int)
        /** 拖动结束，由 Service 计算贴边位置并回调 [onSnapped]。 */
        fun onDragEnd()
        /** 拖动开始（超过阈值后触发，用于显示关闭按钮）。 */
        fun onDragStart()
        /** 松手吸附边缘后重新布局（左右翻转）。 */
        fun onSnapped(left: Boolean, x: Int, y: Int)
        fun onAvatarClicked()
        fun onBubbleClicked()
        /** 任意触摸交互（驱动 2s 空闲淡化复位）。 */
        fun onUserInteraction()
        /** 输入气泡发送。 */
        fun onInputSubmit(text: String)
        /** 输入气泡取消（返回键 / 点击 ✕）。 */
        fun onInputCancel()
        /** 输入模式状态变化（active=false 表示已关闭）。 */
        fun onInputModeChanged(active: Boolean)
        /** 气泡展示完成（自动回收）。 */
        fun onBubbleDismissed()
    }

    private val density = resources.displayMetrics.density

    private val avatarFrame = FrameLayout(context)

    private val avatarView = ImageView(context).apply {
        setImageResource(R.mipmap.ic_launcher)
        scaleType = ImageView.ScaleType.CENTER_CROP
        // 正圆裁剪：默认图标与自定义头像统一显示为圆形
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
    }

    private val badgeView = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        textSize = 9f
        visibility = View.GONE
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFFE53935.toInt())
        }
    }

    private val bubbleView = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 12f
        setPadding(dp(10), dp(6), dp(10), dp(6))
        maxWidth = dp(280)
        maxHeight = dp(360)
        minHeight = dp(32)
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(0xE6202020.toInt())
        }
        alpha = 0f
    }

    /** 输入气泡：EditText + 发送按钮（默认隐藏，点击消息气泡时弹出）。 */
    private val inputView = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(0xE61A1A1A.toInt())
        }
        visibility = View.GONE
    }

    private val inputEditText = EditText(context).apply {
        setTextColor(Color.WHITE)
        setHintTextColor(
            android.content.res.ColorStateList.valueOf(HINT_COLOR)
        )
        textSize = 12f
        isSingleLine = true
        imeOptions = EditorInfo.IME_ACTION_SEND
        inputType = EditorInfo.TYPE_CLASS_TEXT
        hint = "回复…"
        background = null
        setPadding(dp(10), 0, dp(6), 0)
        setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitInput()
                true
            } else {
                false
            }
        }
        setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                listener.onInputCancel()
                true
            } else {
                false
            }
        }
    }

    private val cancelButton = TextView(context).apply {
        text = "✕"
        setTextColor(0x99FFFFFF.toInt())
        textSize = 14f
        setPadding(dp(8), dp(8), dp(6), dp(8))
        gravity = Gravity.CENTER
    }

    private val sendButton = TextView(context).apply {
        text = "发送"
        setTextColor(0xFF80D8FF.toInt())
        textSize = 12f
        setPadding(dp(8), dp(8), dp(10), dp(8))
        gravity = Gravity.CENTER
    }

    private var snappedLeft = true

    private var downRawX = 0f
    private var downRawY = 0f
    /** 触点在窗口内的偏移（跟手锚点）：拖动时保持该偏移不变，窗口始终贴着手。 */
    private var downOffsetX = 0f
    private var downOffsetY = 0f
    private var dragging = false

    private var currentBubbleText: String? = null
    private var inputModeActive = false
    private var replyPulseActive = false

    /** 回复中头像轻微脉动（人性化反馈：AI 正在思考/输出）。 */
    private val replyPulseAnimator = ValueAnimator.ofFloat(1f, 1.06f).apply {
        duration = 700L
        interpolator = AccelerateDecelerateInterpolator()
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            val scale = it.animatedValue as Float
            avatarView.scaleX = scale
            avatarView.scaleY = scale
        }
    }

    /** 是否正在拖动（Service 据此跳过尺寸变化触发的自动吸附）。 */
    val isDragging: Boolean
        get() = dragging

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL

        // 常规无障碍悬浮窗尺寸：48dp 圆形头像
        val avatarSize = dp(48)
        avatarFrame.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xE61A1A1A.toInt())
            setStroke(dp(2), 0xCCFFFFFF.toInt())
        }
        val avatarFrameLayout = LayoutParams(avatarSize, avatarSize)
        addView(avatarFrame, avatarFrameLayout)
        avatarView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        avatarFrame.addView(avatarView)

        val badgeSize = dp(18)
        val badgeLayout = FrameLayout.LayoutParams(badgeSize, badgeSize)
        badgeLayout.gravity = Gravity.TOP or Gravity.END
        avatarFrame.addView(badgeView, badgeLayout)

        val bubbleLayout = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        )
        bubbleLayout.leftMargin = dp(8)
        addView(bubbleView, bubbleLayout)

        inputEditText.layoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        )
        inputEditText.minWidth = dp(160)
        inputEditText.maxWidth = dp(240)
        inputView.addView(inputEditText)
        inputView.addView(cancelButton)
        inputView.addView(sendButton)
        val inputLayout = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        )
        inputLayout.leftMargin = dp(8)
        addView(inputView, inputLayout)

        setOnTouchListener { v, e -> handleTouch(v, e, onTap = { listener.onAvatarClicked() }) }
        avatarFrame.setOnTouchListener { v, e -> handleTouch(v, e, onTap = { listener.onAvatarClicked() }) }
        bubbleView.setOnTouchListener { v, e -> handleTouch(v, e, onTap = { listener.onBubbleClicked() }) }
        cancelButton.setOnClickListener { listener.onInputCancel() }
        sendButton.setOnClickListener { submitInput() }
        inputView.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_DOWN) listener.onUserInteraction()
            false
        }
        inputEditText.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_DOWN) listener.onUserInteraction()
            false
        }
    }

    /** 窗口透明度平滑过渡（淡化/恢复都走动画，不做瞬间跳变）。 */
    fun animateWindowAlpha(target: Float, durationMs: Long) {
        animate()
            .alpha(target)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /** 窗口出现动画：整体从透明淡入。 */
    fun playEntrance() {
        alpha = 0f
        animate()
            .alpha(1f)
            .setDuration(300L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /** 回复进行中：头像轻脉动；停止时复位。 */
    fun setReplying(active: Boolean) {
        if (replyPulseActive == active) return
        replyPulseActive = active
        if (active) {
            replyPulseAnimator.start()
        } else {
            replyPulseAnimator.cancel()
            avatarView.scaleX = 1f
            avatarView.scaleY = 1f
        }
    }

    /** 打开输入模式：显示输入气泡并请求焦点。 */
    fun startInputMode() {
        if (inputModeActive) return
        inputModeActive = true
        setReplying(false)
        bubbleView.visibility = View.INVISIBLE
        inputView.visibility = View.VISIBLE
        inputView.alpha = 0f
        inputView.animate()
            .alpha(1f)
            .setDuration(160L)
            .setInterpolator(DecelerateInterpolator())
            .start()
        inputEditText.setText("")
        listener.onInputModeChanged(true)
    }

    /** 关闭输入模式：隐藏输入气泡，恢复消息气泡展示。 */
    fun closeInputMode() {
        if (!inputModeActive) return
        inputModeActive = false
        inputView.visibility = View.GONE
        bubbleView.visibility = View.VISIBLE
        inputEditText.clearFocus()
        listener.onInputModeChanged(false)
    }

    /** 输入框获取焦点（配合输入法弹出）。 */
    fun requestInputFocus() {
        if (inputModeActive) {
            inputEditText.requestFocus()
        }
    }

    /** 输入框视图（Service 弹出输入法用）。 */
    fun inputEditText(): EditText = inputEditText

    private fun submitInput() {
        val text = inputEditText.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) return
        listener.onInputSubmit(text)
    }

    /** 更新头像（null = 默认应用图标）。 */
    fun setAvatar(uri: String?) {
        if (uri.isNullOrBlank()) {
            avatarView.setImageResource(R.mipmap.ic_launcher)
            return
        }
        runCatching {
            val bitmap = context.contentResolver.openInputStream(Uri.parse(uri))?.use {
                BitmapFactory.decodeStream(it)
            } ?: return
            val circular = RoundedBitmapDrawableFactory.create(resources, bitmap)
            circular.isCircular = true
            avatarView.setImageDrawable(circular)
        }.onFailure {
            avatarView.setImageResource(R.mipmap.ic_launcher)
        }
    }

    /** 设置多会话角标（<=1 隐藏）。 */
    fun setBadgeCount(count: Int) {
        val visible = count > 1
        badgeView.text = if (count > 99) "99+" else count.toString()
        if (visible && badgeView.visibility != View.VISIBLE) {
            badgeView.visibility = View.VISIBLE
            // 角标弹出动画（贴右时保持镜像方向）
            val flipSign = if (snappedLeft) 1f else -1f
            badgeView.scaleX = 0.5f * flipSign
            badgeView.scaleY = 0.5f
            badgeView.animate()
                .scaleX(flipSign)
                .scaleY(1f)
                .setDuration(200L)
                .setInterpolator(OvershootInterpolator(1.8f))
                .start()
        } else {
            badgeView.visibility = if (visible) View.VISIBLE else View.GONE
        }
    }

    /** 展示工具动作 / 状态文本（非气泡，无滑出动画）。 */
    fun showStatusText(text: String) {
        currentBubbleText = null
        bubbleView.animate().cancel()
        bubbleView.apply {
            this.text = text
            alpha = 1f
            translationX = 0f
        }
    }

    /** 展示回复气泡：从头像一侧滑出（局部坐标 +宽 → 0，镜像后自动朝向屏幕内侧）。 */
    fun showReplyBubble(text: String) {
        showBubble(text, toolStyle = false)
    }

    /** 展示 Agent 工具动作气泡（蓝色调，与普通回复区分）。 */
    fun showToolActionBubble(text: String) {
        showBubble(text, toolStyle = true)
    }

    private fun showBubble(text: String, toolStyle: Boolean) {
        bubbleView.backgroundTintList = if (toolStyle) {
            ColorStateList.valueOf(TOOL_BUBBLE_COLOR)
        } else {
            null
        }
        if (currentBubbleText == text && bubbleView.alpha > 0.5f) return
        currentBubbleText = text
        bubbleView.apply {
            this.text = text
            alpha = 1f
            val startX = (if (width > 0) width else dp(110)).toFloat()
            translationX = startX
            animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(260L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    /** 隐藏气泡（工具动作覆盖或应用回前台时）。 */
    fun hideBubble() {
        currentBubbleText = null
        bubbleView.animate().cancel()
        bubbleView.apply {
            alpha = 0f
            translationX = 0f
        }
    }

    /** 气泡滑回并淡出（4s 展示结束），动画完成后回调 [onEnd]。 */
    fun dismissBubble(onEnd: () -> Unit) {
        if (currentBubbleText == null || bubbleView.alpha <= 0.1f) {
            onEnd()
            return
        }
        bubbleView.animate()
            .translationX((if (width > 0) width else dp(110)).toFloat())
            .alpha(0f)
            .setDuration(200L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction(onEnd)
            .start()
    }

    /** 吸附到指定边缘：贴左不镜像，贴右整体镜像并反向镜像子视图保证可读。 */
    fun snapToEdge(left: Boolean, x: Int, y: Int) {
        snappedLeft = left
        val flip = !left
        scaleX = if (flip) -1f else 1f
        avatarFrame.scaleX = if (flip) -1f else 1f
        bubbleView.scaleX = if (flip) -1f else 1f
        badgeView.scaleX = if (flip) -1f else 1f
        inputView.scaleX = if (flip) -1f else 1f
        listener.onSnapped(left, x, y)
    }

    private fun handleTouch(
        v: View,
        event: MotionEvent,
        onTap: () -> Unit
    ): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                listener.onUserInteraction()
                downRawX = event.rawX
                downRawY = event.rawY
                // 抓取点 = 触点在窗口内的坐标：
                // 根视图直接用 event.x/y；子视图要加上子视图在根布局里的偏移。
                // 拖动时窗口左上角 = 手指屏幕坐标 - 抓取点偏移，实现 1:1 跟手无跳变。
                downOffsetX = if (v === this) event.x else (v.left + event.x).toFloat()
                downOffsetY = if (v === this) event.y else (v.top + event.y).toFloat()
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && (kotlin.math.abs(dx) > dp(6) || kotlin.math.abs(dy) > dp(6))) {
                    dragging = true
                    hideBubble()
                    listener.onDragStart()
                }
                if (dragging) {
                    listener.onPositionChanged(
                        (downRawX + dx - downOffsetX).toInt(),
                        (downRawY + dy - downOffsetY).toInt()
                    )
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    listener.onDragEnd()
                    return true
                }
                onTap()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    private companion object {
        const val HINT_COLOR = 0x80FFFFFF.toInt()
        const val TOOL_BUBBLE_COLOR = 0xE61F3B66.toInt()
    }
}
