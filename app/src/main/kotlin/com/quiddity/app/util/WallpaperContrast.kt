package com.quiddity.app.util

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 * 壁纸自动对比度（纯逻辑 + 轻量亮度采样）。
 *
 * 目标：无论用户选择什么壁纸，UI 文字始终可读。设计约束：
 * - **只调整背景遮罩，绝不修改文字颜色 / 字体 / 排版**，杜绝文字渲染异常（乱码）；
 * - 遮罩方向由主题决定：深色主题（浅色文字）→ 黑色遮罩压暗亮壁纸；
 *   浅色主题（深色文字）→ 白色遮罩提亮暗壁纸（修复旧版"浅色主题+暗壁纸无解"的缺口）；
 * - 强度 = max(自动基线, 用户手动值)：自动保证最小可读对比度，用户只做增强微调；
 * - 亮度采样只读像素，不落盘、不改变数据 schema。
 */
object WallpaperContrast {

    /** 无法采样时的默认亮度（中性灰）。 */
    const val DEFAULT_BRIGHTNESS = 0.5f

    /** 单像素相对亮度 0..1（Rec.601 系数，足够用于遮罩判定）。 */
    fun pixelBrightness(argb: Int): Float {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    }

    /** 纯逻辑：像素列表平均亮度（JVM 单测与网格采样共用）。 */
    fun averageBrightness(pixels: List<Int>): Float {
        if (pixels.isEmpty()) return DEFAULT_BRIGHTNESS
        return pixels.map(::pixelBrightness).average().toFloat().coerceIn(0f, 1f)
    }

    /**
     * 自动遮罩强度（0..1）。
     *
     * - 深色主题（浅色文字）：壁纸越亮越需要压暗，亮度 0.5→0.12，1.0→0.38；
     *   暗壁纸只需 0.12 的轻基线（本来就安全）。
     * - 浅色主题（深色文字）：壁纸越暗越需要提亮，亮度 0→0.38，0.5→0.12；
     *   亮壁纸只需 0.12 的轻基线。
     */
    fun autoScrimAlpha(brightness: Float, darkMode: Boolean): Float {
        val b = brightness.coerceIn(0f, 1f)
        return if (darkMode) {
            if (b <= 0.5f) 0.12f
            else (0.12f + (b - 0.5f) * 2f * 0.26f).coerceIn(0.12f, 0.38f)
        } else {
            if (b >= 0.5f) 0.12f
            else (0.12f + (0.5f - b) * 2f * 0.26f).coerceIn(0.12f, 0.38f)
        }
    }

    /**
     * 最终遮罩强度 = max(用户手动值, 自动基线)。
     * 用户值只增强不削弱，保证任何壁纸都有最小可读对比度。
     */
    fun effectiveScrimAlpha(brightness: Float, userDarken: Float, darkMode: Boolean): Float =
        maxOf(autoScrimAlpha(brightness, darkMode), userDarken.coerceIn(0f, 1f)).coerceIn(0f, 1f)

    /** 遮罩颜色（ARGB）：深色主题 → 黑色；浅色主题 → 白色。 */
    fun scrimColor(darkMode: Boolean): Int =
        if (darkMode) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()

    /**
     * 从 Bitmap 采样平均亮度（0..1）。
     * 8×8 网格采样（最多 64 像素），避免全图遍历；getPixel 对采样量级足够快。
     */
    fun sampleBrightness(bitmap: Bitmap): Float {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return DEFAULT_BRIGHTNESS
        val stepX = maxOf(1, w / 8)
        val stepY = maxOf(1, h / 8)
        val sampled = ArrayList<Int>(((w / stepX) + 1) * ((h / stepY) + 1))
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                sampled += bitmap.getPixel(x, y)
                x += stepX
            }
            y += stepY
        }
        return averageBrightness(sampled)
    }
}

@Composable
fun rememberWallpaperBrightness(uri: String?): Float {
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    var brightness by remember(uri) { mutableFloatStateOf(WallpaperContrast.DEFAULT_BRIGHTNESS) }
    LaunchedEffect(uri) {
        if (uri == null) {
            brightness = WallpaperContrast.DEFAULT_BRIGHTNESS
            return@LaunchedEffect
        }
        brightness = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .size(64)
                    .allowHardware(false)
                    .build()
                val drawable = imageLoader.execute(request).drawable
                val bitmap = (drawable as? BitmapDrawable)?.bitmap
                    ?: return@runCatching WallpaperContrast.DEFAULT_BRIGHTNESS
                WallpaperContrast.sampleBrightness(bitmap)
            }.getOrDefault(WallpaperContrast.DEFAULT_BRIGHTNESS)
        }
    }
    return brightness
}
