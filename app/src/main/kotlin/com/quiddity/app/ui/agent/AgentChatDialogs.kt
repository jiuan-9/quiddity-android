package com.quiddity.app.ui.agent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.Message
import com.quiddity.app.domain.agent.AgentToolRegistry
import com.quiddity.app.ui.chat.ChatViewModel
import com.quiddity.app.ui.chat.PendingReedit
import com.quiddity.app.ui.chat.PendingToolConfirm
import com.quiddity.app.ui.chat.WithdrawProposal
import com.quiddity.app.ui.chat.components.RewriteBottomSheet
import com.quiddity.app.ui.theme.Motion
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** 取参数原始字符串（无 JSON 引号）；非原始值回退 JSON 表示。 */
private fun argText(v: JsonElement?): String = when (v) {
    null -> ""
    is JsonPrimitive -> v.content
    else -> v.toString()
}

@Composable
internal fun AgentChatDialogs(
    showCharacterPicker: Boolean,
    conversation: Conversation?,
    messages: List<Message>,
    pendingConfirm: PendingToolConfirm?,
    pendingWithdraw: WithdrawProposal?,
    rewritingMessageId: String?,
    pendingReedit: PendingReedit?,
    reeditSheetOpen: Boolean,
    viewModel: ChatViewModel,
    onCloseCharacterPicker: () -> Unit,
    onDismissRewrite: () -> Unit,
    onDismissReeditSheet: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = showCharacterPicker,
            enter = fadeIn(tween(Motion.DurationMedium)),
            exit = fadeOut(tween(Motion.DurationShort)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .pointerInput(Unit) {
                        detectTapGestures { onCloseCharacterPicker() }
                    }
            )
        }
        AnimatedVisibility(
            visible = showCharacterPicker,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(Motion.DurationPageTransition, easing = Motion.EasingStandard)
            ) + fadeIn(tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(Motion.DurationPageTransition, easing = Motion.EasingStandard)
            ) + fadeOut(tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate)),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(340.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp, 0.dp, 0.dp, 20.dp),
                shadowElevation = 8.dp
            ) {
                AgentCharacterPicker(
                    conversation = conversation,
                    viewModel = viewModel,
                    onDismiss = { onCloseCharacterPicker() }
                )
            }
        }
    }

    // ===== 危险工具确认弹窗：每次执行写入类工具前必须用户确认 =====
    pendingConfirm?.let { pending ->
        AlertDialog(
            onDismissRequest = { viewModel.confirmTool(false) },
            title = { Text("确认执行危险操作") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Agent 请求执行以下 ${pending.items.size} 项操作，是否同意？",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    pending.items.forEach { item ->
                        Text(
                            text = "· ${AgentToolRegistry.displayName(item.toolName)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val detail = buildString {
                            item.args["pkg"]?.let { append("应用包名：${argText(it)}\n") }
                            item.args["path"]?.let { append("路径：${argText(it)}\n") }
                            item.args["src"]?.let { append("源路径：${argText(it)}\n") }
                            item.args["dst"]?.let { append("目标路径：${argText(it)}\n") }
                            item.args["op"]?.let { append("权限操作：${argText(it)}\n") }
                            item.args["mode"]?.let { append("权限模式：${argText(it)}\n") }
                            item.args["x"]?.let { append("横坐标：${argText(it)}\n") }
                            item.args["y"]?.let { append("纵坐标：${argText(it)}\n") }
                            item.args["direction"]?.let { append("方向：${argText(it)}\n") }
                            item.args["distance"]?.let { append("距离：${argText(it)}\n") }
                            item.args["action"]?.let { append("系统动作：${argText(it)}\n") }
                            item.args["text"]?.let { append("文字：${argText(it)}\n") }
                            if (isEmpty()) append(item.args.toString())
                        }.trimEnd()
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "按「过问」模式，这些操作需要你确认；「完全」模式下将按白名单自动执行。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmTool(true) }) { Text("确认执行") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.confirmTool(false) }) { Text("取消") }
            }
        )
    }

    // ===== Agent 撤回确认弹窗：本轮有创建/更改项目时列出清单；无项目时只问「确认撤回？」 =====
    pendingWithdraw?.let { proposal ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelAgentWithdraw() },
            title = { Text("确认撤回？") },
            text = {
                if (proposal.hasEffects) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "本轮有创建/更改项目：",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        proposal.createdPaths.forEach { path ->
                            Text(
                                text = "· 创建 $path",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        proposal.changedItems.forEach { item ->
                            Text(
                                text = "· $item",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "撤回将删除本轮创建的文件并移除消息，不可恢复。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                } else {
                    Text(
                        text = "撤回该消息（及之后的所有消息）？",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmAgentWithdraw() }) { Text("确认撤回") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelAgentWithdraw() }) { Text("取消") }
            }
        )
    }

    // ===== AI 消息改写底部弹出框（与私聊改写同款：编辑后原位替换） =====
    rewritingMessageId?.let { msgId ->
        val targetMsg = messages.firstOrNull { it.id == msgId }
        if (targetMsg != null) {
            RewriteBottomSheet(
                initialText = targetMsg.content,
                onSave = { newContent ->
                    viewModel.rewriteMessage(msgId, newContent)
                    onDismissRewrite()
                },
                onDismiss = { onDismissRewrite() }
            )
        } else {
            onDismissRewrite()
        }
    }

    // ===== 撤回后「重新编辑」底部弹出框（预填被撤回消息原文，保存后作为新消息发出） =====
    if (reeditSheetOpen) {
        val pending = pendingReedit
        if (pending != null) {
            RewriteBottomSheet(
                initialText = pending.content,
                placeholder = "编辑消息…",
                onSave = { newContent ->
                    viewModel.resendReedit(newContent)
                    onDismissReeditSheet()
                },
                onDismiss = { onDismissReeditSheet() }
            )
        } else {
            onDismissReeditSheet()
        }
    }
}
