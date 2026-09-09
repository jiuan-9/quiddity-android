package com.quiddity.app.ui.chat.gesture

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.quiddity.app.ui.theme.Motion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
/**
 * 会话页拖动控制器。
 *
 * 设计原则（最简单、最直接）：
 * - 右滑退出会话：内容层 1:1 跟手，松手超过 30% 屏宽（或速度足够）则 0.4s 滑出屏外并触发返回。
 * - 左滑打开菜单：菜单用 0.4s 淡入淡出（透明度 0→1），聊天内容完全不动。
 * - 角色卡打开时：右滑只用于关闭角色卡（松手判定），不再触发会话退出。
 * - 拖动期间直接同步赋值（mutableFloatStateOf），graphicsLayer 在 draw phase 读取，零重组。
 * - 多指由 NativeHorizontalSwipeDetector 拦截，controller 不关心。
 */
@Stable
class ChatDragController(
    private val scope: CoroutineScope,
    val screenWidthPx: Float,
    val backThresholdFraction: Float = 0.30f,
    val menuOpenThresholdFraction: Float = 0.50f,
    val velocityThresholdPxPerSec: Float = 400f,
    private val onBack: () -> Unit,
    private val onMenuVisibilityChange: (Boolean) -> Unit,
    private val onCharacterPickerDismiss: (() -> Unit)? = null
) {
