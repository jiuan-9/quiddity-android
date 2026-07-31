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
 * 会话页拖动控制器。
 *
 * 设计原则（最简单、最直接）：
 * - 右滑退出会话：内容层 1:1 跟手，松手超过 30% 屏宽（或速度足够）则 0.4s 滑出屏外并触发返回。
 * - 左滑打开菜单：菜单用 0.4s 淡入淡出（透明度 0→1），聊天内容完全不动。
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
    private val onMenuVisibilityChange: (Boolean) -> Unit
) {
    // ===== 三条开发规范（位于文件中间位置） =====
    // 1. 问题修复规范：所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
    //    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
    // 2. 代码注释规范：文件内仅允许保留两类注释——当前规则说明注释与模块划分注释（// ===== xxx =====）。
    //    除此之外，禁止出现任何形式的代码注释（包括单行 / 多行 / 临时调试注释）。
    // 3. 构建交付要求：完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK。
    //    APK 需满足：签名有效且符合发布标准、包含完整功能模块、经过基础性能与兼容性测试。

    // 暴露 MutableFloatState 供 graphicsLayer draw phase 直接读 .floatValue——
    // 零重组且 state 追踪可靠（禁止在 graphicsLayer 内通过 delegated property / lambda 间接读取，会失效）。
    private val _contentOffsetX = mutableFloatStateOf(0f)
    val contentOffsetXState: MutableFloatState get() = _contentOffsetX
    var contentOffsetX: Float by _contentOffsetX
        private set

    private val _menuAlpha = mutableFloatStateOf(0f)
    val menuAlphaState: MutableFloatState get() = _menuAlpha
    var menuAlpha: Float by _menuAlpha
        private set

    var menuOpen: Boolean by mutableStateOf(false)
        private set

    private val backThresholdPx: Float = screenWidthPx * backThresholdFraction
    private val menuOpenThresholdPx: Float = screenWidthPx * menuOpenThresholdFraction

    // ===== 手势回调 =====
    // 当前规则（menuOpen 状态决定手势语义）：
    // - 菜单关闭时：右滑 1:1 跟手滑出会话内容（返回判定），左滑跟手淡入菜单（开菜单判定）
    // - 菜单打开时：右滑 1:1 跟手淡出菜单（关菜单判定，contentOffsetX 不动），左滑无操作
    //   这样菜单打开后仍可用滑动关闭，不再因 swipeEnabled=false 卡死。

    fun onDrag(totalDx: Float) {
        if (menuOpen) {
            // 菜单已打开：右滑跟手减小 menuAlpha（1→0），左滑无操作（菜单已开）
            if (totalDx > 0f) {
                menuAlpha = (1f - totalDx / screenWidthPx).coerceIn(0f, 1f)
            }
            return
        }
        if (totalDx > 0f) {
            // 右滑：内容层 1:1 跟手，范围 [0, screenWidthPx]
            contentOffsetX = totalDx.coerceIn(0f, screenWidthPx)
        } else if (totalDx < 0f) {
            // 左滑：菜单淡入（透明度跟拖动距离），聊天内容完全不动
            val absDx = (-totalDx).coerceAtMost(screenWidthPx)
            menuAlpha = (absDx / screenWidthPx).coerceIn(0f, 1f)
            // 第一次进入拖动时把菜单设为 open（让 HamburgerMenu 进入组合阶段）
            if (!menuOpen) {
                menuOpen = true
                onMenuVisibilityChange(true)
            }
        }
    }

    fun onDragEnd(totalDx: Float, velocityDx: Float) {
        val absVelocity = abs(velocityDx)
        val velocityCommit = absVelocity > velocityThresholdPxPerSec

        if (menuOpen) {
            // 菜单已打开：右滑判定关菜单，左滑/无位移保持打开
            when {
                totalDx > 0f -> {
                    val shouldClose = totalDx > backThresholdPx ||
                        (velocityCommit && velocityDx > 0f)
                    animateMenuTo(open = !shouldClose)
                }
                else -> animateMenuTo(open = true)
            }
            return
        }

        when {
            // ===== 右滑：返回判定 =====
            totalDx > 0f -> {
                val shouldBack = totalDx > backThresholdPx ||
                    (velocityCommit && velocityDx > 0f)
                if (shouldBack) {
                    // 直接返回：NavHost popExitTransition 接管滑出动画（外层 slideOut 0→fullWidth），
                    // 叠加本控制器保持的 contentOffsetX(=totalDx)，总位移 totalDx→totalDx+fullWidth，
                    // 视觉上"从手势位置继续向右滑出"，无跳跃；同时 HomeScreen 从左视差滑入。
                    onBack()
                } else {
                    animateContentTo(0f)
                }
            }
            // ===== 左滑：菜单判定 =====
            totalDx < 0f -> {
                val absDx = -totalDx
                val shouldOpen = absDx > menuOpenThresholdPx ||
                    (velocityCommit && velocityDx < 0f)
                animateMenuTo(open = shouldOpen)
            }
            else -> {
                // 既没右滑也没左滑，松手时保持菜单当前状态
                if (menuOpen) animateMenuTo(open = true)
            }
        }
    }

    fun onDragCancel() {
        // 取消：恢复到手势开始前的稳定状态
        if (menuOpen) {
            // 菜单打开时取消：恢复 menuAlpha=1（菜单保持打开）
            animateMenuTo(open = true)
        } else if (contentOffsetX != 0f) {
            // 菜单关闭时取消：内容回弹到 0
            animateContentTo(0f)
        }
    }

    // ===== 按钮 / 系统返回键触发的便捷入口 =====

    fun animateBackAndExit() {
        // 按钮触发：直接 onBack，NavHost popExitTransition 接管 0.4s 滑出动画
        onBack()
    }

    fun toggleMenu() {
        animateMenuTo(open = !menuOpen)
    }

    fun closeMenu() {
        if (menuOpen) animateMenuTo(open = false)
    }

    // ===== 内部 settle 工具（统一 0.4s） =====

    private fun animateContentTo(target: Float, onEnd: (() -> Unit)? = null) {
        scope.launch {
            val anim = Animatable(contentOffsetX)
            anim.animateTo(
                targetValue = target,
                animationSpec = tween(Motion.DurationPageTransition, easing = Motion.EasingStandard)
            ) { contentOffsetX = this.value }
            onEnd?.invoke()
        }
    }

    private fun animateMenuTo(open: Boolean) {
        if (open) {
            if (!menuOpen) {
                menuOpen = true
                onMenuVisibilityChange(true)
            }
            scope.launch {
                val anim = Animatable(menuAlpha)
                anim.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(Motion.DurationPageTransition, easing = Motion.EasingStandard)
                ) { menuAlpha = this.value }
            }
        } else {
            scope.launch {
                val anim = Animatable(menuAlpha)
                anim.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(Motion.DurationPageTransition, easing = Motion.EasingStandard)
                ) { menuAlpha = this.value }
                // 淡出动画结束后才让菜单退出组合（避免菜单"突然消失"）
                if (menuOpen) {
                    menuOpen = false
                    onMenuVisibilityChange(false)
                }
            }
        }
    }
}
