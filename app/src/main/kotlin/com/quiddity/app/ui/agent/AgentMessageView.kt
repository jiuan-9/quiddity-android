package com.quiddity.app.ui.agent

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import com.quiddity.app.domain.agent.AgentToolRegistry
import com.quiddity.app.ui.chat.ToolTrace
import com.quiddity.app.ui.components.AiAvatar
import com.quiddity.app.ui.theme.Motion
import com.quiddity.app.util.DateUtils
import kotlinx.coroutines.launch

@Composable
internal fun AgentMessageLine(
    message: Message,
    markdownEnabled: Boolean,
    userAvatarUri: String?,
    aiAvatarUri: String?,
    aiName: String,
    isLatestAi: Boolean = false,
    inMultiSelect: Boolean = false,
    isSelected: Boolean = false,
    onEnterMultiSelect: () -> Unit = {},
    onToggleSelect: () -> Unit = {},
    onCopy: (String) -> Unit = {},
    onRegenerate: () -> Unit = {},
    onContinue: () -> Unit = {},
    onRewrite: () -> Unit = {},
    onWithdraw: (() -> Unit)? = null,
    toolTraces: List<ToolTrace> = emptyList(),
    /** 工具轮段边界（生成中用实时内存边界，历史消息用持久化边界）。 */
    segmentEnds: List<Int> = emptyList(),
    animateEntry: Boolean = true
) {
    val colorScheme = MaterialTheme.colorScheme
    if (message.isNotice) {
        Text(
            text = message.content,
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        return
    }

    // ===== 新消息入场动画（与气泡一致：淡入 + 上浮 + 轻微放大） =====
    val entryAlpha = remember(message.id, animateEntry) {
        Animatable(if (animateEntry) 0f else 1f)
    }
    val entryOffsetY = remember(message.id, animateEntry) { Animatable(0f) }
    val entryScale = remember(message.id, animateEntry) {
        Animatable(if (animateEntry) 0.97f else 1f)
    }
    val entryOffsetPx = with(LocalDensity.current) { 10.dp.toPx() }
    LaunchedEffect(message.id, animateEntry) {
        if (animateEntry) {
            entryAlpha.snapTo(0f)
            entryOffsetY.snapTo(entryOffsetPx)
            entryScale.snapTo(0.97f)
            val spec: FiniteAnimationSpec<Float> =
                tween(Motion.DurationXLong, easing = Motion.EasingEmphasizedDecelerate)
            launch { entryAlpha.animateTo(1f, spec) }
            launch { entryOffsetY.animateTo(0f, spec) }
            launch { entryScale.animateTo(1f, spec) }
        }
    }

    val isUser = message.role == Role.USER
    // ===== 工具痕迹合成：生成中取内存状态（含"工具使用中…"高亮滑动），
    // 生成结束/历史消息取持久化到消息的工具调用记录（重新打开会话仍可见） =====
    val uiTraces = remember(message.toolTraces, toolTraces) {
        if (toolTraces.isNotEmpty()) {
            toolTraces.map { UiTrace(it.name, it.status, it.summary) }
        } else {
            message.toolTraces.map { UiTrace(it.name, if (it.ok) "done" else "error", it.summary) }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(
                onClick = { if (inMultiSelect) onToggleSelect() },
                onLongClick = { if (!inMultiSelect) onEnterMultiSelect() }
            )
            .graphicsLayer {
                alpha = entryAlpha.value
                translationY = entryOffsetY.value
                scaleX = entryScale.value
                scaleY = entryScale.value
            },
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        // 头像与第一行文字顶部齐平（无气泡直排风格）
        verticalAlignment = Alignment.Top
    ) {
        // ===== 多选：行首选择圈 =====
        if (inMultiSelect) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "已选中",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.size(8.dp))
        }
        if (!isUser) {
            AiAvatar(
                avatarUri = aiAvatarUri,
                name = aiName,
                size = 40.dp
            )
            Spacer(modifier = Modifier.size(10.dp))
        }
        // weight(1f, fill=false)：长消息时正文列最多占满剩余空间（行宽 - 头像区），
        // 头像永远留在屏幕内，不会被挤出（短消息仍按内容自适应宽度）
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .widthIn(max = 460.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            if (message.isThinking) {
                Text(
                    text = if (message.isStreaming) "思考中" else "思考",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            // 图片消息：Agent 无气泡直排，使用与私聊一致的固定图片卡片，避免纯图片消息显示为空
            if (message.imageUri?.isNotBlank() == true) {
                com.quiddity.app.ui.chat.components.ImageMessageCard(
                    imageUri = message.imageUri,
                    modifier = Modifier.padding(bottom = if (message.content.isNotBlank()) 6.dp else 0.dp)
                )
            }
            // ===== 应用内本地思考：气泡内可展开/收起（思考中为高亮滑块） =====
            if (message.thinking.isNotBlank()) {
                com.quiddity.app.ui.components.ThinkingBlock(
                    thinking = message.thinking,
                    isStreaming = message.isStreaming,
                    contentEmpty = message.content.isBlank()
                )
            }
            // ===== 正文按工具轮段边界拆分渲染：工具痕迹插入段间（与流式输出一致：
            // 正文段 → 空行 → 工具痕迹 → 空行 → 正文段），痕迹与正文空行分隔；
            // 单段正文（无轮间正文）时痕迹显示在正文后，同样空行分隔 =====
            val segments = remember(message.content, segmentEnds) {
                splitToolSegments(message.content, segmentEnds)
            }
            if (segments.size > 1) {
                segments.forEachIndexed { idx, seg ->
                    AgentMarkdownText(
                        content = seg,
                        isStreaming = message.isStreaming && idx == segments.lastIndex,
                        markdownEnabled = markdownEnabled,
                        color = if (isUser) colorScheme.onSurface else colorScheme.onSurfaceVariant
                    )
                    // 段间：空行分隔 + 对应工具痕迹（第 i 段后 = 第 i 条痕迹）
                    if (idx < segments.lastIndex) {
                        Spacer(modifier = Modifier.size(10.dp))
                        if (!isUser) {
                            uiTraces.getOrNull(idx)?.let { trace ->
                                ToolTraceLine(trace)
                                Spacer(modifier = Modifier.size(10.dp))
                            }
                        }
                    }
                }
                // 段之后的剩余痕迹（工具轮正文段缺失时连续显示）
                if (!isUser && uiTraces.size > segments.size) {
                    Spacer(modifier = Modifier.size(10.dp))
                    uiTraces.drop(segments.size).forEach { trace ->
                        ToolTraceLine(trace)
                        Spacer(modifier = Modifier.size(4.dp))
                    }
                }
            } else {
                // 单段正文（模型直接调工具、无轮间开场白）：工具痕迹显示在正文之前
                // （工具先执行，正文为最终汇报——穿插顺序：工具 → 汇报正文）
                if (!isUser && uiTraces.isNotEmpty()) {
                    uiTraces.forEach { trace ->
                        ToolTraceLine(trace)
                        Spacer(modifier = Modifier.size(10.dp))
                    }
                }
                AgentMarkdownText(
                    content = message.content,
                    isStreaming = message.isStreaming,
                    markdownEnabled = markdownEnabled,
                    color = if (isUser) colorScheme.onSurface else colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.size(3.dp))
            Text(
                text = DateUtils.formatTime(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            // 用户消息操作：撤回（仅图标；只出现在最后一条用户消息；流式中 / 多选时不显示）
            if (isUser && !message.isStreaming && !inMultiSelect && onWithdraw != null) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    AgentActionButton(
                        icon = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "撤回",
                        onClick = { onWithdraw?.invoke() }
                    )
                }
            }
            // AI 消息操作：复制 / 改写 / 重说 / 继续说（仅图标；重说与继续说只出现在最新一条 AI 消息）
            if (!isUser && !message.isThinking && !message.isStreaming) {
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    AgentActionButton(
                        icon = Icons.Filled.ContentCopy,
                        contentDescription = "复制",
                        onClick = { onCopy(message.content) }
                    )
                    AgentActionButton(
                        icon = Icons.Filled.Edit,
                        contentDescription = "改写",
                        onClick = onRewrite
                    )
                    if (isLatestAi) {
                        AgentActionButton(
                            icon = Icons.Filled.Refresh,
                            contentDescription = "重说",
                            onClick = onRegenerate
                        )
                        AgentActionButton(
                            icon = Icons.Filled.PlayArrow,
                            contentDescription = "继续说",
                            onClick = onContinue
                        )
                    }
                }
            }
        }
        if (isUser) {
            Spacer(modifier = Modifier.size(10.dp))
            AiAvatar(
                avatarUri = userAvatarUri,
                name = "",
                size = 40.dp
            )
        }
    }
}

@Composable
internal fun AgentActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(50))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            modifier = Modifier.size(17.dp)
        )
    }
}

internal data class UiTrace(
    val name: String,
    val status: String,
    val summary: String?
)

@Composable
internal fun ToolTraceLine(trace: UiTrace) {
    val colorScheme = MaterialTheme.colorScheme
    if (trace.status == "running") {
        // 加载/调用中：工具名滑动高亮（shimmer），随流式输出出现在正文内
        com.quiddity.app.ui.components.ShimmerHighlightText(
            text = AgentToolRegistry.displayName(trace.name),
            icon = Icons.Filled.Build
        )
        return
    }
    var expanded by remember(trace.name, trace.summary) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        // 点击工具名文字展开/收起详情（拉开 / 收起动画）
        Text(
            text = AgentToolRegistry.displayName(trace.name),
            style = MaterialTheme.typography.labelMedium,
            // 固定灰色（Material 灰 500）：暗色/亮色主题下都是明显的灰色，不会近似白色
            color = Color(0xFF9E9E9E),
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { expanded = !expanded }
                .padding(vertical = 2.dp, horizontal = 2.dp)
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = tween(Motion.DurationShort, easing = Motion.EasingEmphasizedDecelerate)
            ) + fadeIn(tween(Motion.DurationShort, easing = Motion.EasingEmphasizedDecelerate)),
            exit = shrinkVertically(
                animationSpec = tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate)
            ) + fadeOut(tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate))
        ) {
            Column {
                Text(
                    text = if (trace.status == "done") "✓ 成功" else "✕ 失败",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (trace.status == "done") colorScheme.primary else colorScheme.error,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                )
                trace.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                    )
                }
            }
        }
    }
}

internal fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("消息", text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}
