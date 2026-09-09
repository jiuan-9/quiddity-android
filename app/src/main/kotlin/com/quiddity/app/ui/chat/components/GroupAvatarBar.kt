package com.quiddity.app.ui.chat.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.domain.GroupReplyQueue
import com.quiddity.app.ui.components.AiAvatar

/**
 * 群聊成员头像栏（方案四 / 十一）：显示在输入框上方、向右靠齐。
 *
 * - 只显示 3 个 AI 成员头像，按加入顺序排列，不显示用户头像；
 * - 正在回复：头像变灰 + 中央三点加载动画；
 * - 排队 1 / 2：头像变灰 + 中央数字 1 / 2；
 * - 三点动画在整个逐字淡入期间持续（由消息 isStreaming 驱动，网络快不会提前结束，
 *   方案十八）。
 */
@Composable
fun GroupAvatarBar(
    members: List<Conversation>,
    queue: List<GroupReplyQueue.Item>,
    onTap: (Conversation) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        members.take(3).forEach { member ->
            val position = queue.indexOfFirst { it.memberId == member.id }
            GroupMemberAvatar(
                member = member,
                position = position,
                onClick = { onTap(member) }
            )
            Spacer(modifier = Modifier.size(10.dp))
        }
    }
}

@Composable
private fun GroupMemberAvatar(
    member: Conversation,
    position: Int,
    onClick: () -> Unit
) {
    val isReplying = position == 0
    val isQueued = position > 0
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        AiAvatar(
            avatarUri = member.persona?.aiAvatarUri,
            name = member.persona?.name.orEmpty(),
            size = 44.dp
        )
        when {
            isReplying -> ThreeDotLoading()
            isQueued -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = position.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        }
    }
}

/**
 * 头像中央三点加载动画（方案四.1：正在回复显示三点）。
 * 性能：单帧只更新一个 phase 值并用 Canvas 绘制三个点，避免每帧三个组合动画。
 */
@Composable
private fun ThreeDotLoading() {
    val transition = rememberInfiniteTransition(label = "three_dot")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dot_phase"
    )
    Canvas(
        modifier = Modifier
            .size(44.dp)
            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
    ) {
        val dotRadius = 4.dp.toPx()
        val spacing = 15.dp.toPx()
        val centerY = size.height / 2f
        val startX = (size.width - spacing * 2) / 2f
        for (i in 0 until 3) {
            val t = (phase + i / 3f) % 1f
            val dotAlpha = if (t < 0.5f) t * 2f else (1f - t) * 2f
            drawCircle(
                color = Color.White.copy(alpha = dotAlpha.coerceIn(0.15f, 1f)),
                radius = dotRadius,
                center = Offset(startX + spacing * i, centerY)
            )
        }
    }
}
