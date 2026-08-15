package com.quiddity.app.ui.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.util.QuiddityConstants


// ============================================================
// 群聊 1.5.0：群设置（群名称 / 上下文条数 N / 成员管理 / 停止模式）
// ============================================================

/**
 * 群聊主菜单（方案十：群名称、上下文条数 N、成员管理、停止模式 A/B；
 * 不含共享用户人设与时间库入口）。
 */
@Composable
internal fun GroupMenuContent(
    conversation: Conversation?,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onContextLimit: () -> Unit,
    onStopModeChange: (String) -> Unit,
    onGroupBackground: () -> Unit,
    onWallpaper: () -> Unit,
    onManageMembers: () -> Unit,
    onSearchChat: () -> Unit,
    onClearMessages: () -> Unit,
    onDeleteConversation: () -> Unit,
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "群聊设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Close, "关闭",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.size(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            MenuSectionCard(title = "群聊") {
                MenuRow(
                    title = "查找聊天记录",
                    subtitle = "搜索本群历史消息",
                    onClick = onSearchChat,
                    trailingIcon = Icons.Filled.ChevronRight
                )
                MenuRow(
                    title = "群聊背景 / 场景",
                    subtitle = conversation?.let { conv ->
                        if (conv.groupBackground.isNotBlank()) {
                            val label = if (conv.groupBackgroundMode == QuiddityConstants.GROUP_BACKGROUND_MODE_SCENE)
                                "场景" else "背景"
                            "$label：${conv.groupBackground.trim()}"
                        } else {
                            "未设置（可选）"
                        }
                    } ?: "未设置（可选）",
                    onClick = onGroupBackground,
                    expandableSubtitle = true
                )
                MenuRow(
                    title = "群名称",
                    subtitle = conversation?.title?.ifBlank { "新群聊" } ?: "新群聊",
                    onClick = onRename
                )
                MenuRow(
                    title = "上下文条数 N",
                    subtitle = "最近 ${conversation?.groupContextLimit ?: QuiddityConstants.GROUP_DEFAULT_CONTEXT_LIMIT} 条",
                    onClick = onContextLimit
                )
                val stopMode = conversation?.stopMode ?: QuiddityConstants.GROUP_DEFAULT_STOP_MODE
                ToggleMenuRow(
                    title = "停止模式",
                    subtitle = if (stopMode == QuiddityConstants.GROUP_STOP_MODE_A)
                        "A：只停止当前成员，排队的递补" else "B：停止时清空整个队列（默认）",
                    checked = stopMode == QuiddityConstants.GROUP_STOP_MODE_B,
                    onCheckedChange = { checked ->
                        onStopModeChange(
                            if (checked) QuiddityConstants.GROUP_STOP_MODE_B
                            else QuiddityConstants.GROUP_STOP_MODE_A
                        )
                    }
                )
            }
            MenuSectionCard(title = "外观") {
                ToggleMenuRow(
                    title = "深色模式",
                    subtitle = if (darkMode) "当前：暗色" else "当前：亮色",
                    checked = darkMode,
                    onCheckedChange = onDarkModeChange
                )
                MenuRow(
                    title = "群聊壁纸",
                    subtitle = if (conversation?.wallpaperUri != null) "已设置" else "未设置",
                    onClick = onWallpaper
                )
            }
            MenuSectionCard(title = "成员管理") {
                MenuRow(
                    title = "查看 / 添加 / 移除成员",
                    subtitle = "当前 ${conversation?.memberConversationIds?.size ?: 0}/3 个",
                    onClick = onManageMembers,
                    trailingIcon = Icons.Filled.ChevronRight
                )
            }
            MenuSectionCard(title = "危险操作") {
                MenuRow(
                    title = "清除聊天记录",
                    subtitle = "删除本群所有消息，不可恢复",
                    onClick = onClearMessages
                )
                MenuRow(
                    title = "删除该会话",
                    subtitle = "删除群聊及其全部消息，成员私聊不受影响",
                    onClick = onDeleteConversation,
                    titleColor = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * 群名称编辑面板（方案十.1：群名称可随时改名）。
 */
@Composable
internal fun GroupNamePanel(
    currentName: String,
    onBack: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(currentName) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "群名称",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.size(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("群名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.size(16.dp))
        TextButton(
            enabled = name.isNotBlank(),
            onClick = { onSave(name.trim()) }
        ) {
            Text("保存")
        }
    }
}

/**
 * 群聊上下文条数 N 编辑面板（方案六.2：默认 50，范围 1～200）。
 */
@Composable
internal fun GroupContextLimitPanel(
    currentLimit: Int,
    onBack: () -> Unit,
    onSave: (Int) -> Unit
) {
    var limit by rememberSaveable { mutableIntStateOf(currentLimit) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "上下文条数 N",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = "群聊记录只取最近 N 条（1～200）",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.size(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                enabled = limit > QuiddityConstants.GROUP_MIN_CONTEXT_LIMIT,
                onClick = { limit = (limit - 5).coerceAtLeast(QuiddityConstants.GROUP_MIN_CONTEXT_LIMIT) }
            ) { Text("−5") }
            TextButton(
                enabled = limit > QuiddityConstants.GROUP_MIN_CONTEXT_LIMIT,
                onClick = { limit = (limit - 1).coerceAtLeast(QuiddityConstants.GROUP_MIN_CONTEXT_LIMIT) }
            ) { Text("−1") }
            Text(
                text = limit.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            TextButton(
                enabled = limit < QuiddityConstants.GROUP_MAX_CONTEXT_LIMIT,
                onClick = { limit = (limit + 1).coerceAtMost(QuiddityConstants.GROUP_MAX_CONTEXT_LIMIT) }
            ) { Text("+1") }
            TextButton(
                enabled = limit < QuiddityConstants.GROUP_MAX_CONTEXT_LIMIT,
                onClick = { limit = (limit + 5).coerceAtMost(QuiddityConstants.GROUP_MAX_CONTEXT_LIMIT) }
            ) { Text("+5") }
        }
        Spacer(modifier = Modifier.size(16.dp))
        TextButton(onClick = { onSave(limit) }) { Text("保存") }
    }
}

/**
 * 群聊背景 / 场景编辑面板：背景（氛围描述）与场景（多人情境）合并为一个设置项，
 * 单选其一开启；文本注入所有成员的回复提示词。清空输入并保存 = 移除。
 */
@Composable
internal fun GroupBackgroundPanel(
    currentText: String,
    currentMode: String,
    onBack: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var text by rememberSaveable { mutableStateOf(currentText) }
    var mode by rememberSaveable { mutableStateOf(currentMode) }
    val isScene = mode == QuiddityConstants.GROUP_BACKGROUND_MODE_SCENE
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "群聊背景 / 场景",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.size(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { mode = QuiddityConstants.GROUP_BACKGROUND_MODE_BACKGROUND }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.RadioButton(
                    selected = !isScene,
                    onClick = { mode = QuiddityConstants.GROUP_BACKGROUND_MODE_BACKGROUND }
                )
                Text(
                    text = "背景",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { mode = QuiddityConstants.GROUP_BACKGROUND_MODE_SCENE }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.RadioButton(
                    selected = isScene,
                    onClick = { mode = QuiddityConstants.GROUP_BACKGROUND_MODE_SCENE }
                )
                Text(
                    text = "场景",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Spacer(modifier = Modifier.size(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(if (isScene) "场景描述" else "背景描述") },
            placeholder = {
                Text(
                    if (isScene) {
                        "例如：用户和小A、小B正在一场篝火晚会上，夜空晴朗，周围是树林"
                    } else {
                        "例如：大学同学群，成员有用户、小A、小B，关系很熟，说话随意，偶尔互怼"
                    }
                )
            },
            minLines = 4,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = if (isScene) {
                "多人场景的情境描述，会注入到每个成员回复时的提示词里；提到成员时请写大名（如「用户」「小A」），不要用「你」「我」等称呼；清空后保存即移除。"
            } else {
                "氛围与群规描述，会注入到每个成员回复时的提示词里；提到成员时请写大名（如「用户」「小A」），不要用「你」「我」等称呼；清空后保存即移除。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.size(16.dp))
        TextButton(onClick = { onSave(text, mode) }) {
            Text("保存")
        }
    }
}
