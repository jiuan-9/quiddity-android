package com.quiddity.app.ui.chat.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import kotlin.math.abs
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

