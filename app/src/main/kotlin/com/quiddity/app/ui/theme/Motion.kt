package com.quiddity.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

object Motion {

    // ===== 动画时长（毫秒） =====
    const val DurationShort = 160
    const val DurationMedium = 320
    const val DurationLong = 360
    const val DurationXLong = 400
    // 路由切换：进入/退出会话、滑入/滑出/淡入/淡出、主题过渡——统一 0.4s
    const val DurationPageTransition = 400
    // 主题颜色过渡：0.4s 平滑过渡，避免瞬切闪眼
    const val DurationTheme = 400

    // ===== 三条开发规范（位于文件中间位置） =====
    // 1. 问题修复规范：所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
    //    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
    // 2. 代码注释规范：文件内仅允许保留两类注释——当前规则说明注释与模块划分注释（// ===== xxx =====）。
    //    除此之外，禁止出现任何形式的代码注释（包括单行 / 多行 / 临时调试注释）。
    // 3. 构建交付要求：完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK。
    //    APK 需满足：签名有效且符合发布标准、包含完整功能模块、经过基础性能与兼容性测试。

    // ===== 缓动函数 =====
    val EasingStandard: Easing = FastOutSlowInEasing
    val EasingEmphasizedDecelerate: Easing = CubicBezierEasing(0.1f, 0.8f, 0.2f, 1.0f)
    val EasingEmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 0.7f, 0.2f)
    val EasingEmphasized: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    // ===== Spring 配置 =====
    val SpringSoft = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val SpringStandard = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    // ===== 循环 =====
    const val TypingCycleMs = 1000
    const val CursorBlinkMs = 500
}
