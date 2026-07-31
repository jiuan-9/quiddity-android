package com.quiddity.app.ui.chat.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs
/*
 * ============================================================================
 * 开发规范 (Development Specifications)
 * ============================================================================
 *
 * 1. 问题修复规范
 *    所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
 *    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
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
 * 全屏原生横向滑动检测器（最简版）。
 *
 * 设计原则（恢复最原始的交互）：
 * - pointerInput 挂在最外层 Box 上，覆盖整片屏幕区域
 * - PointerEventPass.Initial：在 LazyColumn 之前先消费横向事件，避免列表滚动抢走手势
 * - 状态机：DECIDING → HORIZONTAL / VERTICAL
 *   - 累计 |dx| / |dy|，绝对值超过 touchSlop 后判定方向
 *   - 横向：|dx| >= |dy| * 1.2（倾向横向，避免误触纵向滚动）
 *   - 纵向：|dy| > |dx| * 1.2（让位给 LazyColumn）
 * - 多指防呆：检测到第二根手指立即 cancel（只判单指）
 * - 首帧立即回调 onDrag(0f, 0f)，确保 DECIDING 阶段 UI 立即响应
 * - enabled 仅在 down 时刻检查一次：手势一旦开始就完整跑完，
 *   不会因中途 enabled 变化（如左滑打开菜单导致 showHamburger 翻转）而重启被打断。
 *   pointerInput 调用方应用稳定 key（Unit）+ rememberUpdatedState 传入 enabled，
 *   避免 key 变化重启协程导致 onDragEnd/onDragCancel 丢失（左滑卡死的根因）。
 */
suspend fun PointerInputScope.detectNativeHorizontalSwipe(
    enabled: () -> Boolean,
    onDrag: (totalDx: Float, velocityDx: Float) -> Unit,
    onDragEnd: (totalDx: Float, velocityDx: Float) -> Unit,
    onDragCancel: () -> Unit
) {
    awaitEachGesture {
        // Initial pass：父元素先于子元素（遮罩 detectTapGestures）收到 down。
        // 菜单打开时遮罩存在，若用 Main pass 遮罩会先拿到 down 并启动 tap 检测，
        // 可能消费事件导致外层手势丢失；Initial pass 保证外层始终优先。
        // 横向判定后才 consume，tap（未移动）不 consume，Main pass 遮罩仍能识别 tap 关菜单。
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)

        // down 时刻检查一次 enabled：false 则放手势给子元素（如菜单遮罩），不消费 down
        if (!enabled()) return@awaitEachGesture

        // ===== 三条开发规范（位于文件中间位置） =====
        // 1. 问题修复规范：所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
        //    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
        // 2. 代码注释规范：文件内仅允许保留两类注释——当前规则说明注释与模块划分注释（// ===== xxx =====）。
        //    除此之外，禁止出现任何形式的代码注释（包括单行 / 多行 / 临时调试注释）。
        // 3. 构建交付要求：完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK。
        //    APK 需满足：签名有效且符合发布标准、包含完整功能模块、经过基础性能与兼容性测试。

        var decidedHorizontal = false
        var abandonedToVertical = false
        var multiTouchDetected = false
        var accumulatedDx = 0f
        var accumulatedDy = 0f
        var lastEventTime = down.uptimeMillis
        var lastDx = 0f
        var velocityDx = 0f
        val touchSlop = viewConfiguration.touchSlop

        // 首帧 baseline，确保 DECIDING 阶段 UI 立即响应
        onDrag(0f, 0f)

        while (true) {
            if (abandonedToVertical || multiTouchDetected) break
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }

            // ===== 多指防呆：屏幕上有 ≥ 2 根手指时,本次手势立即 cancel,只判单指 =====
            if (event.changes.count { it.pressed } > 1) {
                multiTouchDetected = true
                onDragCancel()
                break
            }

            // 首指已抬起(change==null),视为取消
            if (change == null) {
                if (decidedHorizontal) onDragCancel()
                break
            }

            // 首指抬起：发布 dragEnd
            if (event.changes.all { it.changedToUp() } || change.changedToUp()) {
                if (decidedHorizontal) {
                    val dtMs = (change.uptimeMillis - lastEventTime).coerceAtLeast(1L)
                    val finalVel = if (dtMs > 0) lastDx * 1000f / dtMs else velocityDx
                    onDragEnd(accumulatedDx, finalVel)
                }
                break
            }

            if (change.positionChanged()) {
                val dx = change.positionChange().x
                val dy = change.positionChange().y
                val dtMs = (change.uptimeMillis - lastEventTime).coerceAtLeast(1L)
                lastEventTime = change.uptimeMillis
                lastDx = dx
                val instantVel = if (dtMs > 0) dx * 1000f / dtMs else 0f
                velocityDx = velocityDx * 0.5f + instantVel * 0.5f

                if (!decidedHorizontal) {
                    accumulatedDx += dx
                    accumulatedDy += dy
                    val absAccX = abs(accumulatedDx)
                    val absAccY = abs(accumulatedDy)
                    // 横向：累计 dx 超过 touchSlop **且** |dx| >= |dy| * 1.2
                    val isHorizontalDominant = absAccX > touchSlop && absAccX >= absAccY * 1.2f
                    // 纵向：累计 dy 超过 touchSlop **且** |dy| > |dx| * 1.2（让位给 LazyColumn）
                    val isVerticalDominant = absAccY > touchSlop && absAccY > absAccX * 1.2f
                    when {
                        isHorizontalDominant -> {
                            decidedHorizontal = true
                            onDrag(accumulatedDx, velocityDx)
                            change.consume()
                        }
                        isVerticalDominant -> {
                            abandonedToVertical = true
                        }
                    }
                } else {
                    accumulatedDx += dx
                    onDrag(accumulatedDx, velocityDx)
                    change.consume()
                }
            }
        }
    }
}
