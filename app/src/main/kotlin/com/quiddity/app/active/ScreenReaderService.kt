package com.quiddity.app.active

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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
 * 屏幕文本读取无障碍服务（只读，V1 不做模拟点击）：
 * 窗口切换/内容变化时采集当前活动窗口文本，写入 [AgentSensorState.screenText]。
 * 文本仅保存在进程内，供 Agent 只读工具使用。
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
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> collectScreenText()
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        isConnected = false
        super.onDestroy()
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

    companion object {
        private const val MAX_TEXT_NODES = 200
        private const val MAX_SCREEN_CHARS = 8000

        @Volatile
        private var instance: ScreenReaderService? = null

        @Volatile
        var isConnected: Boolean = false
            private set

        /**
         * 截取当前屏幕并保存 PNG 到应用私有目录，返回保存路径文本；
         * 失败时返回以「截图」开头的中文错误描述。
         */
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
