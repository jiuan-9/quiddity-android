package com.quiddity.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 圆角规范（参见 PLAN.md 7.3）。
 * - 小按钮/Tag：8dp
 * - 用户气泡/输入框：20dp
 * - AI 气泡/卡片：16dp
 * - 大模态：24dp
 */
val QuiddityShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),    // 小按钮/Tag
    small = RoundedCornerShape(12.dp),        // 次小
    medium = RoundedCornerShape(16.dp),       // AI 气泡/卡片
    large = RoundedCornerShape(20.dp),        // 用户气泡/输入框
    extraLarge = RoundedCornerShape(24.dp)    // 大模态
)

/** 用户气泡圆角（右下小角，符合"信息发送方向"语义）。 */
val UserBubbleShape = RoundedCornerShape(
    topStart = 16.dp,
    topEnd = 16.dp,
    bottomEnd = 4.dp,
    bottomStart = 16.dp
)

/** AI 气泡圆角（左下小角）。 */
val AiBubbleShape = RoundedCornerShape(
    topStart = 16.dp,
    topEnd = 16.dp,
    bottomEnd = 16.dp,
    bottomStart = 4.dp
)

/** 输入框圆角（参考用户气泡，统一观感）。 */
val InputBarShape = RoundedCornerShape(24.dp)

/** 「让 AI 先发消息」按钮圆角。 */
val AiFirstButtonShape = RoundedCornerShape(24.dp)
