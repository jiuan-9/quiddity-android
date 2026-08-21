package com.quiddity.app.ui.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ExportPayload
import com.quiddity.app.ui.components.ConfirmDialog
import com.quiddity.app.util.ConversationCodec

/**
 * 汉堡菜单确认对话框组：清空设置 / 清空消息 / 删除会话 / 密钥重填提示。
 */
@Composable
internal fun HamburgerConfirmDialogs(
    pendingClearSettings: Boolean,
    pendingClearMessages: Boolean,
    pendingDeleteConversation: Boolean,
    pendingKeyRefill: List<String>?,
    isGroupConversation: Boolean,
    onClearSettings: () -> Unit,
    onClearMessages: () -> Unit,
    onDeleteConversation: () -> Unit,
    onDismiss: () -> Unit
) {
    if (pendingClearSettings) {
        ConfirmDialog(
            title = "清空会话设置",
            message = "将重置当前会话的 AI 人设、用户人设、场景、记忆（不影响消息记录与会话记录）。此操作不可撤销。",
            confirmText = "清空",
            onConfirm = onClearSettings,
            onDismiss = onDismiss
        )
    }

    if (pendingClearMessages) {
        ConfirmDialog(
            title = if (isGroupConversation) "清除群聊记录" else "清空会话记录（含压缩对话）",
            message = if (isGroupConversation) {
                "将删除本群聊的所有消息，并重置群聊小本本（groupMemory）。群聊设置与成员保持不变。此操作不可撤销。"
            } else {
                "将删除当前会话的所有消息，并重置压缩记忆（compressedMemory / lastCompressedAtRound）。AI 人设 / 用户 / 场景 / 记忆 / 壁纸保留。此操作不可撤销。"
            },
            confirmText = "清空",
            onConfirm = onClearMessages,
            onDismiss = onDismiss
        )
    }

    if (pendingDeleteConversation) {
        ConfirmDialog(
            title = "删除该会话",
            message = if (isGroupConversation) {
                "将删除当前群聊及其全部消息记录，成员私聊不受影响。此操作不可撤销。"
            } else {
                "将删除该会话及其全部消息记录。此操作不可撤销。"
            },
            confirmText = "删除",
            onConfirm = onDeleteConversation,
            onDismiss = onDismiss
        )
    }

    // 导入后密钥重填提示
    pendingKeyRefill?.let { names ->
        ConfirmDialog(
            title = "部分接口密钥需重新填写",
            message = "导入的备份中以下模型配置的密钥无法解密（可能来自其他设备），" +
                "请到「模型配置」中重新填写：\n\n" + names.joinToString("\n"),
            confirmText = "知道了",
            cancelText = null,
            onConfirm = onDismiss,
            onDismiss = onDismiss
        )
    }
}

/** 查看时间库流程：直接展示内容（无密码机制）。 */
@Composable
internal fun TimeLibraryViewFlow(
    step: Int,
    conversation: Conversation?,
    onStepChange: (Int) -> Unit
) {
    if (step >= 1) {
        TimeLibraryDetailDialog(
            conversation = conversation,
            onDismiss = { onStepChange(0) }
        )
    }
}

/**
 * 导出格式选择对话框触发器。
 */
@Composable
internal fun ExportFormatDialog(
    pending: ExportFormatPicker?,
    onDismiss: () -> Unit,
    onSelect: (ConversationCodec.Format) -> Unit
) {
    pending?.let {
        ExportFormatPickerDialog(
            onDismiss = onDismiss,
            onSelect = onSelect
        )
    }
}

/**
 * JSON 全量导入抉择弹窗：已有数据时让用户选择替换/合并/取消。
 */
@Composable
internal fun ImportMergeDialog(
    payload: ExportPayload?,
    onDismiss: () -> Unit,
    onMerge: (ExportPayload) -> Unit,
    onCharactersOnly: (ExportPayload) -> Unit,
    onReplace: (ExportPayload) -> Unit
) {
    payload?.let { p ->
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "导入数据",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(
                        text = "检测到应用已有对话数据，请选择导入方式：",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = com.quiddity.app.ui.components.glassCardColor()
                    ) {
                        Text(
                            text = "提示：你也可以在此菜单中单独导入人设卡或对话记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.size(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("取消")
                        }
                        Spacer(modifier = Modifier.size(4.dp))
                        TextButton(onClick = {
                            onDismiss()
                            onMerge(p)
                        }) { Text("合并") }
                        Spacer(modifier = Modifier.size(4.dp))
                        TextButton(onClick = {
                            onDismiss()
                            onCharactersOnly(p)
                        }) { Text("仅导入角色库") }
                        Spacer(modifier = Modifier.size(4.dp))
                        TextButton(onClick = {
                            onDismiss()
                            onReplace(p)
                        }) { Text("替换", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}
