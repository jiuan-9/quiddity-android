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
