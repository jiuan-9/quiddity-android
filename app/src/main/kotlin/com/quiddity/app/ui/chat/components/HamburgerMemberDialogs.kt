package com.quiddity.app.ui.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.ui.components.AiAvatar
import com.quiddity.app.util.QuiddityConstants


@Composable
internal fun MemberAddResultDialog(
    passedCount: Int,
    failed: List<Pair<Conversation, String>>,
    onRetry: () -> Unit,
    onConfig: (Conversation) -> Unit,
    onDone: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("添加成员结果") },
        text = {
            Column {
                Text(
                    text = if (passedCount > 0) "已加入 $passedCount 个成员（头像已显示）" else "没有成员通过校验",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (failed.isNotEmpty()) {
                    Spacer(modifier = Modifier.size(10.dp))
                    Text(
                        text = "以下 ${failed.size} 个未通过：",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(failed, key = { it.first.id }) { (member, reason) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = member.persona.name.ifBlank { member.title },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = reason,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                TextButton(onClick = { onConfig(member) }) {
                                    Text("配置", color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDone) { Text("完成") }
        },
        dismissButton = {
            if (failed.isNotEmpty()) {
                TextButton(onClick = onRetry) { Text("重试") }
            }
        }
    )
}

/**
 * 成员模型配置弹窗（需求：未配置 API 的成员可直接在此页面配置）。
 * 选择已有配置 / 使用默认（跟随全局）/ 新增配置；配置后自动重新校验。
 */
@Composable
internal fun MemberApiConfigDialog(
    member: Conversation,
    catalog: List<com.quiddity.app.data.model.ApiCatalogEntry>,
    currentId: String?,
    onSelect: (String?) -> Unit,
    onAddNew: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("配置 ${member.persona.name.ifBlank { "成员" }} 的模型") },
        text = {
            Column {
                Text(
                    text = "成员回复使用各自私聊的模型配置，选择后自动重新校验：",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.size(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(catalog, key = { it.id }) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onSelect(entry.id) }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = entry.id == currentId,
                                onClick = { onSelect(entry.id) }
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = entry.apiModel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    item(key = "use_default") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onSelect(null) }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = currentId == null,
                                onClick = { onSelect(null) }
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "使用默认（跟随全局激活配置）",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    item(key = "add_new") {
                        TextButton(
                            onClick = onAddNew,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("+ 新增模型配置")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onBack) { Text("返回") }
        },
        dismissButton = {
            TextButton(onClick = onRetry) { Text("重试") }
        }
    )
}

/**
 * 添加群聊成员弹窗（方案十.5）：私聊列表勾选，最多选到 3 个，
 * 确定后执行 API 测试，通过的角色加入，未通过的返回通知可重试。
 */
@Composable
internal fun AddGroupMembersDialog(
    soloList: List<Conversation>,
    currentIds: List<String>,
    hasApiConfig: Boolean,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    val candidates = soloList.filter { it.id !in currentIds }
    fun toggle(id: String) {
        selected = if (id in selected) selected - id else selected + id
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加成员") },
        text = {
            if (candidates.isEmpty()) {
                Text(
                    text = "没有可添加的私聊会话",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(candidates, key = { it.id }) { conv ->
                        val checked = conv.id in selected
                        val status = when {
                            !hasApiConfig -> "API 未配置"
                            conv.userPersona.name.isBlank() -> "用户名未设置"
                            conv.persona.name.isBlank() -> "AI 名未设置"
                            else -> "可加入"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { toggle(conv.id) }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { toggle(conv.id) })
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                            ) {
                                AiAvatar(
                                    avatarUri = conv.persona.aiAvatarUri,
                                    name = conv.persona.name,
                                    size = 36.dp
                                )
                            }
                            Spacer(modifier = Modifier.size(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = conv.title.ifBlank { "未命名会话" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "AI：${conv.persona.name.ifBlank { "未设置" }} · $status",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (status == "可加入") MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                if (selected.size + currentIds.size > QuiddityConstants.GROUP_MAX_MEMBERS) {
                    Text(
                        text = "最多 ${QuiddityConstants.GROUP_MAX_MEMBERS} 个成员",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.isNotEmpty() &&
                    (currentIds.size + selected.size) <= QuiddityConstants.GROUP_MAX_MEMBERS,
                onClick = { onConfirm(selected.toList()) }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
