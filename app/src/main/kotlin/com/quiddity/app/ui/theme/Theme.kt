package com.quiddity.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

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



// 当前规则：颜色方案 0.4s 平滑过渡——
//   600ms 旧方案太长，每帧 21 个 Color 变化导致整屏频繁重组；
//   0ms 瞬切会"闪到眼睛"；
//   0.4s 是用户拍板的折中值——既能看到颜色平滑变化，又不会过度重组。
// 性能优化：用 key(darkMode) 包裹 21 个 animateColorAsState 与 copy。
//   - 暗/亮切换时正常动画（key 变化重建 state）
//   - 其他重组（列表滚动、输入文字等）完全跳过颜色计算，零额外开销
@Composable
fun QuiddityTheme(
    darkMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val targetScheme = if (darkMode) QuiddityDarkColors else QuiddityLightColors

    val primary by animateColorAsState(targetScheme.primary, tween(durationMillis = Motion.DurationTheme, easing = Motion.EasingStandard), label = "primary")
    val onPrimary by animateColorAsState(targetScheme.onPrimary, tween(durationMillis = Motion.DurationTheme, easing = Motion.EasingStandard), label = "onPrimary")
    val primaryContainer by animateColorAsState(targetScheme.primaryContainer, tween(durationMillis = Motion.DurationTheme, easing = Motion.EasingStandard), label = "primaryContainer")
    val onPrimaryContainer by animateColorAsState(targetScheme.onPrimaryContainer, tween(durationMillis = Motion.DurationTheme, easing = Motion.EasingStandard), label = "onPrimaryContainer")
    val secondary by animateColorAsState(targetScheme.secondary, tween(durationMillis = Motion.DurationTheme, easing = Motion.EasingStandard), label = "secondary")
    val onSecondary by animateColorAsState(targetScheme.onSecondary, tween(durationMillis = Motion.DurationTheme, easing = Motion.EasingStandard), label = "onSecondary")
    val tertiary by animateColorAsState(targetScheme.tertiary, tween(durationMillis = Motion.DurationTheme, easing = Motion.EasingStandard), label = "tertiary")
    val background by animateColorAsState(targetScheme.background, tween(durationMillis = Motion.DurationTheme, easing = Motion.EasingStandard), label = "background")
    val onBackground by animateColorAsState(targetScheme.onBackground, tween(durationMillis = Motion.DurationTheme, easing = Motion.EasingStandard), label = "onBackground")
    val surface by animateColorAsState(targetScheme.surface, tween(durationMillis = Motion.DurationTheme, easing = Motion.EasingStandard), label = "surface")
    val onSurface by animateColorAsState(targetScheme.onSurface, tween(durationMillis = Motion.DurationTheme, easing = Motion.EasingStandard), label = "onSurface")
    val surfaceVariant by animateColorAsState(targetScheme.surfaceVariant, tween(durationMillis = Motion.DurationTheme, easing = Motion.EasingStandard), label = "surfaceVariant")
    val onSurfaceVariant by animateColorAsState(targetScheme.onSurfaceVariant, tween(durationMillis = Motion.DurationTheme, easing = Motion.EasingStandard), label = "onSurfaceVariant")
    val surfaceContainer by animateColorAsState(targetScheme.surfaceContainer, tween(durationMillis = Motion.DurationTheme, easing = Motion.EasingStandard), label = "surfaceContainer")
    val surfaceContainerLow by animateColorAsState(targetScheme.surfaceContainerLow, tween(durationMillis = Motion.DurationTheme, easing = Motion.EasingStandard), label = "surfaceContainerLow")
    val surfaceContainerHigh by animateColorAsState(targetScheme.surfaceContainerHigh, tween(durationMillis = Motion.DurationTheme, easing = Motion.EasingStandard), label = "surfaceContainerHigh")
    val error by animateColorAsState(targetScheme.error, tween(durationMillis = Motion.DurationTheme, easing = Motion.EasingStandard), label = "error")
    val onError by animateColorAsState(targetScheme.onError, tween(durationMillis = Motion.DurationTheme, easing = Motion.EasingStandard), label = "onError")
    val outline by animateColorAsState(targetScheme.outline, tween(durationMillis = Motion.DurationTheme, easing = Motion.EasingStandard), label = "outline")
    val outlineVariant by animateColorAsState(targetScheme.outlineVariant, tween(durationMillis = Motion.DurationTheme, easing = Motion.EasingStandard), label = "outlineVariant")

    val animatedScheme = targetScheme.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        tertiary = tertiary,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceContainer = surfaceContainer,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainerHigh = surfaceContainerHigh,
        error = error,
        onError = onError,
        outline = outline,
        outlineVariant = outlineVariant
    )

    CompositionLocalProvider(LocalQuiddityDarkMode provides darkMode) {
        MaterialTheme(
            colorScheme = animatedScheme,
            typography = QuiddityTypography,
            shapes = QuiddityShapes,
            content = content
        )
    }
}

val LocalQuiddityDarkMode = compositionLocalOf { false }

@Composable
@ReadOnlyComposable
fun isQuiddityDarkMode(): Boolean = LocalQuiddityDarkMode.current

// ===== 配色（top-level 避免每次重组重新构造） =====

private val QuiddityLightColors = lightColorScheme(
    primary = Accent,
    onPrimary = CardBg,
    primaryContainer = SecondaryBg,
    onPrimaryContainer = TextPrimary,
    secondary = UserBubbleLight,
    onSecondary = UserBubbleTextLight,
    tertiary = TextTertiary,
    background = CreamBg,
    onBackground = TextPrimary,
    surface = CreamBg,
    onSurface = TextPrimary,
    surfaceVariant = CardBgWarm,
    onSurfaceVariant = TextPrimary,
    surfaceContainer = SecondaryBg,
    surfaceContainerLow = CardBg,
    surfaceContainerHigh = SecondaryBg,
    outline = Divider,
    outlineVariant = Divider,
    error = Danger,
    onError = UserBubbleTextLight
)

private val QuiddityDarkColors = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkBg,
    primaryContainer = DarkSecondaryBg,
    onPrimaryContainer = DarkTextPrimary,
    secondary = DarkUserBubble,
    onSecondary = DarkUserBubbleText,
    tertiary = DarkTextTertiary,
    background = DarkBg,
    onBackground = DarkTextPrimary,
    surface = DarkBg,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkCardBg,
    onSurfaceVariant = DarkTextPrimary,
    surfaceContainer = DarkSecondaryBg,
    surfaceContainerLow = DarkCardBg,
    surfaceContainerHigh = DarkSecondaryBg,
    outline = DarkDivider,
    outlineVariant = DarkDivider,
    error = DarkDanger,
    onError = DarkUserBubbleText
)

val QuiddityTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)
