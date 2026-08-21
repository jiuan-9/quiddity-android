package com.quiddity.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quiddity.app.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

// 进软件开始动画：应用名淡入+轻微放大、分隔线、slogan 依次浮现；达到最小时长后淡出reveal主界面。
@Composable
fun AppLaunchIntro(
    darkMode: Boolean,
    visible: Boolean,
    onFinished: () -> Unit
) {
    val bg = if (darkMode) Color.Black else Color.White
    val fg = if (darkMode) Color.White else Color.Black
    val nameAlpha = remember { Animatable(0f) }
    val sloganAlpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.96f) }
    val fadeOut = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        launch { nameAlpha.animateTo(1f, tween(650, easing = Motion.EasingStandard)) }
        launch { scale.animateTo(1f, tween(650, easing = Motion.EasingStandard)) }
        delay(180)
        sloganAlpha.animateTo(1f, tween(650, easing = Motion.EasingStandard))
    }
    LaunchedEffect(visible) {
        if (visible) {
            fadeOut.animateTo(0f, tween(260, easing = Motion.EasingStandard))
            onFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .graphicsLayer { alpha = fadeOut.value },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.graphicsLayer {
                    alpha = nameAlpha.value
                    scaleX = scale.value
                    scaleY = scale.value
                }
            ) {
                androidx.compose.material3.Text(
                    text = "Quiddity",
                    color = fg,
                    fontSize = 40.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp
                )
            }
            Spacer(Modifier.height(22.dp))
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(2.dp)
                    .background(fg)
                    .graphicsLayer { alpha = sloganAlpha.value }
            )
            Spacer(Modifier.height(22.dp))
            Box(
                modifier = Modifier.graphicsLayer {
                    alpha = sloganAlpha.value
                }
            ) {
                androidx.compose.material3.Text(
                    text = "知所不尽、往复不止",
                    color = fg,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 6.sp
                )
            }
        }
    }
}
