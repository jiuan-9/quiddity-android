package com.quiddity.app.ui.chat

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.Message
import com.quiddity.app.ui.chat.ChatViewModel
import com.quiddity.app.ui.chat.components.HamburgerMenu
import com.quiddity.app.ui.chat.components.CompressionProgressDialog
import com.quiddity.app.ui.chat.components.RewriteBottomSheet

@Composable
internal fun ChatScreenDialogs(
    showHamburger: Boolean,
    menuAlphaState: MutableFloatState,
    isCompressing: Boolean,
    showUserNameDialog: Boolean,
    userNameInput: String,
    conversation: Conversation?,
    messages: List<Message>,
    rewritingMessageId: String?,
    pendingReedit: PendingReedit?,
    reeditSheetOpen: Boolean,
    viewModel: ChatViewModel,
    settingsViewModel: com.quiddity.app.ui.settings.SettingsViewModel,
    onBack: () -> Unit,
    onCloseMenu: () -> Unit,
    onJumpToMessage: (String) -> Unit,
    onUserNameInputChange: (String) -> Unit,
    onUserNameConfirm: () -> Unit,
    onDismissUserNameDialog: () -> Unit,
    onDismissRewrite: () -> Unit,
    onDismissReeditSheet: () -> Unit
) {
        HamburgerMenu(
            visible = showHamburger,
            menuAlphaState = menuAlphaState,
            viewModel = viewModel,
            settingsViewModel = settingsViewModel,
            onDismiss = onCloseMenu,
            onDeleteConversation = {
                viewModel.deleteCurrentConversation()
                onBack()
            },
            onJumpToMessage = { id -> onJumpToMessage(id) }
        )

    // ===== 压缩进度弹窗 =====
    CompressionProgressDialog(visible = isCompressing)

    // ===== 私聊用户名强制弹窗（方案九.6） =====
    if (showUserNameDialog) {
        AlertDialog(
            onDismissRequest = onDismissUserNameDialog,
            title = { Text("设置用户名") },
            text = {
                OutlinedTextField(
                    value = userNameInput,
                    onValueChange = onUserNameInputChange,
                    label = { Text("用户名（未填写不能发送消息）") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = userNameInput.isNotBlank(),
                    onClick = {
onUserNameConfirm()
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissUserNameDialog) {
                    Text("暂不设置")
                }
            }
        )
    }

    // ===== 消息改写底部弹出框 =====
    rewritingMessageId?.let { msgId ->
        val targetMsg = messages.firstOrNull { it.id == msgId }
        if (targetMsg != null) {
            RewriteBottomSheet(
                initialText = targetMsg.content,
                onSave = { newContent ->
                    viewModel.rewriteMessage(msgId, newContent)
                    onDismissRewrite()
                },
                onDismiss = onDismissRewrite
            )
        } else {
            // 目标消息不存在（已删除等）：延迟到组合结束后再关闭，避免组合期副作用写状态
            LaunchedEffect(rewritingMessageId) { onDismissRewrite() }
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
                onDismiss = onDismissReeditSheet
            )
        } else {
            // 待重编辑内容已不存在：延迟到组合结束后再关闭，避免组合期副作用写状态
            LaunchedEffect(pendingReedit) { onDismissReeditSheet() }
        }
    }
}
