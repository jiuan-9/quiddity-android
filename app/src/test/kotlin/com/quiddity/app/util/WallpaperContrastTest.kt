package com.quiddity.app.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * 壁纸自动对比度纯逻辑测试。
 *
 * 不变量：
 * 1. 亮度计算：白=1.0，黑=0.0，纯红≈0.299（Rec.601 系数）；
 * 2. 自动遮罩强度有方向性：深色主题（浅色文字）对亮壁纸加强压暗，
 *    浅色主题（深色文字）对暗壁纸加强提亮；安全侧保留 0.12 轻基线；
 * 3. 最终强度 = max(用户值, 自动基线)，用户只增强不削弱；
 * 4. 遮罩颜色：深色主题=黑，浅色主题=白。
 */
class WallpaperContrastTest {

    @Test
    fun `pixel brightness follows Rec601 luma`() {
        assertEquals(1.0f, WallpaperContrast.pixelBrightness(0xFFFFFFFF.toInt()), 0.001f)
        assertEquals(0.0f, WallpaperContrast.pixelBrightness(0xFF000000.toInt()), 0.001f)
        assertEquals(0.299f, WallpaperContrast.pixelBrightness(0xFFFF0000.toInt()), 0.001f)
        assertEquals(0.587f, WallpaperContrast.pixelBrightness(0xFF00FF00.toInt()), 0.001f)
        assertEquals(0.114f, WallpaperContrast.pixelBrightness(0xFF0000FF.toInt()), 0.001f)
    }

    @Test
    fun `average brightness handles empty and extremes`() {
        assertEquals(WallpaperContrast.DEFAULT_BRIGHTNESS, WallpaperContrast.averageBrightness(emptyList()))
        assertEquals(0.0f, WallpaperContrast.averageBrightness(listOf(0xFF000000.toInt())), 0.001f)
        assertEquals(1.0f, WallpaperContrast.averageBrightness(listOf(0xFFFFFFFF.toInt())), 0.001f)
        assertEquals(
            0.5f,
            WallpaperContrast.averageBrightness(
                listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
            ),
            0.001f
        )
    }

    @Test
    fun `dark mode strengthens scrim on bright wallpaper`() {
        val darkWallpaper = WallpaperContrast.autoScrimAlpha(0.1f, darkMode = true)
        val midWallpaper = WallpaperContrast.autoScrimAlpha(0.5f, darkMode = true)
        val brightWallpaper = WallpaperContrast.autoScrimAlpha(0.9f, darkMode = true)
        assertTrue(brightWallpaper > midWallpaper, "深色主题下亮壁纸应比中亮壁纸压得更暗")
        assertTrue(midWallpaper >= 0.12f, "中亮壁纸保留安全基线")
        assertTrue(darkWallpaper == 0.12f, "深色主题下暗壁纸只需轻基线")
        assertTrue(brightWallpaper <= 0.38f, "自动强度有上界")
    }

    @Test
    fun `light mode strengthens scrim on dark wallpaper`() {
        val darkWallpaper = WallpaperContrast.autoScrimAlpha(0.1f, darkMode = false)
        val midWallpaper = WallpaperContrast.autoScrimAlpha(0.5f, darkMode = false)
        val brightWallpaper = WallpaperContrast.autoScrimAlpha(0.9f, darkMode = false)
        assertTrue(darkWallpaper > midWallpaper, "浅色主题下暗壁纸应比中亮壁纸提得更亮")
        assertTrue(midWallpaper >= 0.12f, "中亮壁纸保留安全基线")
        assertTrue(brightWallpaper == 0.12f, "浅色主题下亮壁纸只需轻基线")
        assertTrue(darkWallpaper <= 0.38f, "自动强度有上界")
    }

    @Test
    fun `effective scrim is max of user value and auto baseline`() {
        // 用户值 0：自动基线仍生效
        assertEquals(
            WallpaperContrast.autoScrimAlpha(0.9f, darkMode = true),
            WallpaperContrast.effectiveScrimAlpha(0.9f, 0f, darkMode = true),
            0.001f
        )
        // 用户值高于基线：以用户为准
        assertEquals(1f, WallpaperContrast.effectiveScrimAlpha(0.5f, 1f, darkMode = true))
        assertEquals(0.5f, WallpaperContrast.effectiveScrimAlpha(0.5f, 0.5f, darkMode = false))
        // 用户值夹取到 0..1
        assertEquals(1f, WallpaperContrast.effectiveScrimAlpha(0.5f, 2f, darkMode = true))
    }

    @Test
    fun `scrim color follows theme`() {
        assertEquals(0xFF000000.toInt(), WallpaperContrast.scrimColor(darkMode = true))
        assertEquals(0xFFFFFFFF.toInt(), WallpaperContrast.scrimColor(darkMode = false))
    }
}
