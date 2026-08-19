package com.quiddity.app.active

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.quiddity.app.domain.agent.AgentToast
import com.quiddity.app.domain.agent.AgentSensorState
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/*
 * ============================================================================
 * 开发规范 (Development Specifications)
 * ============================================================================
 *
 * 1. 问题修复规范
 *    所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
 *    修复内容需完全融入现有代码架构，保证代码逻辑的连贯性、可维护性和可扩展性。
 *
 * 2. 代码注释规范
 *    文件内仅允许保留两类注释：
 *    - 当前规则说明注释（即本注释块）
 *    - 模块划分注释（用于标识代码功能模块边界）
 *    除此之外，禁止出现任何形式的代码注释（包括但不限于单行注释、多行注释、临时调试注释等）。
 *
 * 3. 构建交付要求
 *    在完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK 文件。
 *    APK 文件需满足以下条件：
 *    - 签名有效且符合发布标准
 *    - 包含完整的功能模块
 *    - 经过基础性能测试和兼容性测试
 *    以便在真实设备环境中进行功能验证和性能评估。
 *
 * ============================================================================
 */

/**
 * 屏幕无障碍服务：采集当前窗口文本与 Toast，并执行模拟点击 / 长按 / 滑动 / 系统动作。
 * 文本与 Toast 写入 [AgentSensorState]，供 Agent 工具使用；手势由工具显式触发。
 */
class ScreenReaderService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isConnected = true
        collectScreenText()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            // 仅窗口切换时被动采集；滚动/内容变化不采集（read_screen 工具会主动强制刷新），
            // 避免主线程全量遍历节点树导致滚动/动画间歇掉帧
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> collectScreenText()
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> captureToast(event)
            else -> Unit
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        isConnected = false
        super.onDestroy()
    }

    /** 在主线程主动采集一次当前屏幕文本（供 read_screen 工具调用，不依赖窗口事件）。 */
    internal fun refreshScreenTextNow() {
        runCatching { collectScreenText() }
    }

    private fun collectScreenText() {
        val root = rootInActiveWindow ?: return
        val sb = StringBuilder()
        val visited = mutableSetOf<Int>()
        var collected = 0

        fun walk(node: AccessibilityNodeInfo) {
            if (collected >= MAX_TEXT_NODES) return
            val key = System.identityHashCode(node)
            if (!visited.add(key)) return
            node.text?.takeIf { it.isNotBlank() }?.let {
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(it)
                collected++
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                walk(child)
            }
        }

        runCatching { walk(root) }
        AgentSensorState.screenText = sb.toString().take(MAX_SCREEN_CHARS)
    }

    /** 捕获瞬时 Toast（两秒即消失的系统提示），写入 [AgentSensorState] 供 toast_monitor 读取。 */
    private fun captureToast(event: AccessibilityEvent) {
        val className = event.className?.toString()
        if (className != "android.widget.Toast") return
        val text = event.text?.joinToString(" ")?.trim().orEmpty()
        if (text.isEmpty()) return
        AgentSensorState.appendToast(
            AgentToast(
                packageName = event.packageName?.toString() ?: "unknown",
                text = text,
                timestamp = event.eventTime.takeIf { it > 0L } ?: System.currentTimeMillis()
            )
        )
    }

    companion object {
        /** ???????API 28 ???????? getMainExecutor??? null ???????? */
        private fun serviceExecutor(service: ScreenReaderService): java.util.concurrent.Executor? =
            if (Build.VERSION.SDK_INT >= 28) service.mainExecutor else null

        private const val MAX_TEXT_NODES = 200
        private const val MAX_SCREEN_CHARS = 8000
        private const val LONG_PRESS_DURATION_MS = 800L
        private const val SCROLL_DURATION_MS = 400L
        private const val GESTURE_TIMEOUT_MS = 3_000L

        @Volatile
        private var instance: ScreenReaderService? = null

        @Volatile
        var isConnected: Boolean = false
            private set

        /**
         * 无障碍手势注入中标志：模拟手势期间 Quiddity 自身的手势检测（左右滑返回等）
         * 必须忽略，避免 AI 在应用内滑动时触发"划退"退出会话。
         */
        @Volatile
        var injecting: Boolean = false
            private set

        /**
         * 主动采集一次当前屏幕文本并等待完成。
         * 界面静止时无障碍事件不触发，screenText 会停留在旧值/空值；
         * read_screen 工具调用本方法确保拿到的是当前屏幕内容。
         */
        suspend fun refreshAndReadScreenText(timeoutMs: Long = 800): String {
            val service = instance ?: return AgentSensorState.screenText
            val executor = runCatching { serviceExecutor(service) }.getOrNull()
                ?: return AgentSensorState.screenText
            return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    runCatching {
                        executor.execute {
                            service.refreshScreenTextNow()
                            if (!cont.isCancelled) cont.resume(AgentSensorState.screenText)
                        }
                    }.onFailure {
                        if (!cont.isCancelled) cont.resume(AgentSensorState.screenText)
                    }
                }
            } ?: AgentSensorState.screenText
        }

        /**
         * 截取当前屏幕并保存 PNG 到应用私有目录，返回保存路径文本；
         * 失败时返回以「截图」开头的中文错误描述。
         */
        @android.annotation.SuppressLint("NewApi")
        suspend fun captureScreenshot(context: Context): String {
            if (Build.VERSION.SDK_INT < 30) return "截图需要 Android 11 及以上系统"
            val service = instance ?: return "截图需要先开启无障碍服务（屏幕读取权限）"
            return suspendCancellableCoroutine { cont ->
                runCatching {
                    service.takeScreenshot(
                        Display.DEFAULT_DISPLAY,
                        service.mainExecutor,
                        object : AccessibilityService.TakeScreenshotCallback {
                            override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                                val hardwareBuffer = screenshot.hardwareBuffer
                                val bitmap = runCatching {
                                    Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)
                                        ?.copy(Bitmap.Config.ARGB_8888, false)
                                }.getOrNull()
                                hardwareBuffer.close()
                                if (bitmap == null) {
                                    if (!cont.isCancelled) cont.resume("截图生成失败")
                                    return
                                }
                                val dir = File(context.filesDir, "quiddity-data/screenshots")
                                    .apply { mkdirs() }
                                val file = File(dir, "shot_${System.currentTimeMillis()}.png")
                                val ok = runCatching {
                                    FileOutputStream(file).use { out ->
                                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                    }
                                    bitmap.recycle()
                                    true
                                }.getOrDefault(false)
                                if (!cont.isCancelled) {
                                    cont.resume(
                                        if (ok) "已保存截图：${file.absolutePath}"
                                        else "截图保存失败"
                                    )
                                }
                            }

                            override fun onFailure(errorCode: Int) {
                                if (!cont.isCancelled) cont.resume("截图失败（错误码 $errorCode）")
                            }
                        }
                    )
                }.onFailure {
                    if (!cont.isCancelled) cont.resume("截图失败：${it.message ?: "未知错误"}")
                }
            }
        }

        /** 模拟点击指定坐标（durationMs 控制长按）。 */
        suspend fun performTap(x: Float, y: Float, durationMs: Long = 100L): String {
            val service = instance ?: return "模拟点击需要先开启无障碍服务"
            val gesture = buildTapGesture(x, y, durationMs)
            return dispatchGesture(service, gesture, "点击")
        }

        /** 长按指定坐标。 */
        suspend fun performLongPress(x: Float, y: Float): String =
            performTap(x, y, LONG_PRESS_DURATION_MS)

        /** 按方向滑动屏幕（up / down / left / right）。 */
        suspend fun performScroll(direction: String, distance: Float): String {
            val service = instance ?: return "模拟滑动需要先开启无障碍服务"
            val gesture = buildScrollGesture(service, direction, distance)
                ?: return "不支持的滑动方向：$direction"
            return dispatchGesture(service, gesture, "滑动")
        }

        /** 点击屏幕上包含指定文字的控件。 */
        suspend fun performClickByText(text: String): String {
            val service = instance ?: return "模拟点击需要先开启无障碍服务"
            val executor = runCatching { serviceExecutor(service) }.getOrNull()
                ?: return "模拟点击服务未就绪"
            return kotlinx.coroutines.withTimeoutOrNull(GESTURE_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    runCatching {
                        executor.execute {
                            val clicked = runCatching {
                                val root = service.rootInActiveWindow
                                val nodes = root?.findAccessibilityNodeInfosByText(text).orEmpty()
                                nodes.any { node ->
                                    runCatching { node.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
                                        .getOrDefault(false)
                                }
                            }.getOrDefault(false)
                            val message = if (clicked) {
                                "已点击包含「$text」的控件"
                            } else {
                                "未找到可点击的「$text」控件"
                            }
                            if (!cont.isCancelled) cont.resume(message)
                        }
                    }.onFailure {
                        if (!cont.isCancelled) cont.resume("点击文字控件失败：${it.message ?: "未知错误"}")
                    }
                }
            } ?: "点击文字控件超时"
        }

        /** 向当前聚焦的输入框写入文本。 */
        suspend fun performInputText(text: String): String {
            val service = instance ?: return "输入文本需要先开启无障碍服务"
            val executor = runCatching { serviceExecutor(service) }.getOrNull()
                ?: return "输入文本服务未就绪"
            return kotlinx.coroutines.withTimeoutOrNull(GESTURE_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    runCatching {
                        executor.execute {
                            val done = runCatching {
                                val root = service.rootInActiveWindow
                                val target = findEditableNode(root)
                                target?.performAction(
                                    AccessibilityNodeInfo.ACTION_SET_TEXT,
                                    Bundle().apply {
                                        putCharSequence(
                                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                            text
                                        )
                                    }
                                ) == true
                            }.getOrDefault(false)
                            val message = if (done) "已输入文本" else "未找到可输入的文本框"
                            if (!cont.isCancelled) cont.resume(message)
                        }
                    }.onFailure {
                        if (!cont.isCancelled) cont.resume("输入文本失败：${it.message ?: "未知错误"}")
                    }
                }
            } ?: "输入文本超时"
        }

        /** 按 resource-id 或 contentDescription 点击控件。 */
        suspend fun performClickBy(id: String?, desc: String?): String {
            val service = instance ?: return "模拟点击需要先开启无障碍服务"
            val executor = runCatching { serviceExecutor(service) }.getOrNull()
                ?: return "模拟点击服务未就绪"
            return kotlinx.coroutines.withTimeoutOrNull(GESTURE_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    runCatching {
                        executor.execute {
                            val clicked = runCatching {
                                val root = service.rootInActiveWindow
                                val node = findNode(root) { n ->
                                    val matchedId = id != null && (
                                        n.viewIdResourceName == id ||
                                            n.viewIdResourceName?.substringAfterLast('/') == id
                                        )
                                    val matchedDesc = desc != null &&
                                        n.contentDescription?.toString()?.contains(desc, ignoreCase = true) == true
                                    matchedId || matchedDesc
                                }
                                node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                            }.getOrDefault(false)
                            val message = if (clicked) "已点击匹配控件" else "未找到匹配控件"
                            if (!cont.isCancelled) cont.resume(message)
                        }
                    }.onFailure {
                        if (!cont.isCancelled) cont.resume("点击控件失败：${it.message ?: "未知错误"}")
                    }
                }
            } ?: "点击控件超时"
        }

        /** 在两点之间拖拽。 */
        suspend fun performDrag(x1: Float, y1: Float, x2: Float, y2: Float): String {
            val service = instance ?: return "拖拽需要先开启无障碍服务"
            val path = Path().apply {
                moveTo(x1, y1)
                lineTo(x2, y2)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0L, SCROLL_DURATION_MS)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return dispatchGesture(service, gesture, "拖拽")
        }

        /** 向下滚动查找并点击目标文字，最多尝试 [maxScrolls] 次。 */
        suspend fun performScrollToText(text: String, maxScrolls: Int): String {
            val service = instance ?: return "滚动查找需要先开启无障碍服务"
            repeat(maxScrolls.coerceIn(1, 10)) {
                val clicked = clickTextNow(service, text)
                if (clicked) return "已滚动并点击「$text」"
                val scrolled = performScroll("up", 700f)
                if (scrolled.startsWith("模拟滑动需要") || scrolled.startsWith("不支持的滑动方向")) {
                    return "未找到「$text」"
                }
            }
            return "滚动 $maxScrolls 次后仍未找到「$text」"
        }

        private fun findEditableNode(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? =
            findNode(root) { it.isEditable }

        private fun findNode(
            root: AccessibilityNodeInfo?,
            predicate: (AccessibilityNodeInfo) -> Boolean
        ): AccessibilityNodeInfo? {
            if (root == null) return null
            if (predicate(root)) return root
            for (i in 0 until root.childCount) {
                val child = root.getChild(i) ?: continue
                val found = findNode(child, predicate)
                if (found != null) return found
            }
            return null
        }

        private fun clickTextNow(service: ScreenReaderService, text: String): Boolean =
            runCatching {
                val root = service.rootInActiveWindow
                val nodes = root?.findAccessibilityNodeInfosByText(text).orEmpty()
                nodes.any { node ->
                    runCatching { node.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
                }
            }.getOrDefault(false)

        /** 执行系统全局动作（返回 / 首页 / 最近任务 / 通知栏 / 快捷设置）。 */
        suspend fun performGlobalAction(action: String): String {
            val service = instance ?: return "系统动作需要先开启无障碍服务"
            val code = when (action) {
                "back" -> AccessibilityService.GLOBAL_ACTION_BACK
                "home" -> AccessibilityService.GLOBAL_ACTION_HOME
                "recents" -> AccessibilityService.GLOBAL_ACTION_RECENTS
                "notifications" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
                "quick_settings" -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
                "lock" -> if (Build.VERSION.SDK_INT >= 28) {
                    AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
                } else {
                    return "仅支持 Android 9 及以上"
                }
                else -> null
            } ?: return "不支持的系统动作：$action"
            return if (runCatching { service.performGlobalAction(code) }.getOrDefault(false)) {
                "已执行系统动作：$action"
            } else {
                "系统动作执行失败：$action"
            }
        }

        private fun buildTapGesture(x: Float, y: Float, durationMs: Long): GestureDescription {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
            return GestureDescription.Builder().addStroke(stroke).build()
        }

        private fun buildScrollGesture(
            service: ScreenReaderService,
            direction: String,
            distance: Float
        ): GestureDescription? {
            val metrics = runCatching { service.resources.displayMetrics }.getOrNull() ?: return null
            val width = metrics.widthPixels.toFloat()
            val height = metrics.heightPixels.toFloat()
            val cx = width / 2f
            val cy = height / 2f
            val path = Path().apply {
                when (direction) {
                    "up" -> { moveTo(cx, cy + distance / 2f); lineTo(cx, cy - distance / 2f) }
                    "down" -> { moveTo(cx, cy - distance / 2f); lineTo(cx, cy + distance / 2f) }
                    "left" -> { moveTo(cx + distance / 2f, cy); lineTo(cx - distance / 2f, cy) }
                    "right" -> { moveTo(cx - distance / 2f, cy); lineTo(cx + distance / 2f, cy) }
                    else -> return null
                }
            }
            val stroke = GestureDescription.StrokeDescription(path, 0L, SCROLL_DURATION_MS)
            return GestureDescription.Builder().addStroke(stroke).build()
        }

        private suspend fun dispatchGesture(
            service: ScreenReaderService,
            gesture: GestureDescription,
            label: String
        ): String {
            val executor = runCatching { serviceExecutor(service) }.getOrNull()
                ?: return "模拟${label}服务未就绪"
            return kotlinx.coroutines.withTimeoutOrNull(GESTURE_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    runCatching {
                        executor.execute {
                            // 注入标志：模拟手势期间 Quiddity 自身的手势检测（左右滑返回等）
                            // 必须忽略，避免 AI 在应用内滑动时触发"划退"退出会话。
                            injecting = true
                            val dispatched = runCatching {
                                service.dispatchGesture(
                                    gesture,
                                    object : AccessibilityService.GestureResultCallback() {
                                        override fun onCompleted(g: GestureDescription?) {
                                            injecting = false
                                            if (!cont.isCancelled) cont.resume("已执行模拟$label")
                                        }

                                        override fun onCancelled(g: GestureDescription?) {
                                            injecting = false
                                            if (!cont.isCancelled) cont.resume("模拟${label}被取消")
                                        }
                                    },
                                    null
                                )
                            }.getOrDefault(false)
                            if (!dispatched) {
                                injecting = false
                                if (!cont.isCancelled) cont.resume("模拟${label}手势发送失败")
                            }
                        }
                    }.onFailure {
                        injecting = false
                        if (!cont.isCancelled) cont.resume("模拟${label}失败：${it.message ?: "未知错误"}")
                    }
                }
            }?.also { injecting = false } ?: run {
                injecting = false
                "模拟${label}超时"
            }
        }

        /** 系统无障碍设置中是否已启用本服务。 */
        fun isServiceEnabled(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
                as? android.view.accessibility.AccessibilityManager
                ?: return false
            val expected = ComponentName(context, ScreenReaderService::class.java)
            return manager
                .getEnabledAccessibilityServiceList(
                    android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
                )
                .any { info ->
                    val serviceInfo = info.resolveInfo?.serviceInfo
                    serviceInfo != null &&
                        serviceInfo.packageName == expected.packageName &&
                        serviceInfo.name == expected.className
                }
        }
    }
}
