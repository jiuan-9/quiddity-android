package com.quiddity.app.ui.miniapps.defuse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.quiddity.app.ui.components.AiAvatar
import com.quiddity.app.ui.theme.AiBubbleShape
import com.quiddity.app.ui.theme.UserBubbleShape

@Composable
internal fun DefuseChatPanel(
    messages: List<DefuseChatMessage>,
    thinking: Boolean,
    partnerName: String,
    partnerAvatar: String?,
    showManual: Boolean,
    manualText: String,
    helpUsed: Boolean,
    onSend: (String) -> Unit,
    onReport: () -> Unit,
    onToggleHelp: () -> Unit,
    modifier: Modifier = Modifier
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, thinking) {
        val count = listState.layoutInfo.totalItemsCount
        if (count > 0) {
            listState.animateScrollToItem(count - 1)
        }
    }
    Column(modifier) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(messages) { message ->
                DefuseChatBubble(
                    message = message,
                    partnerName = partnerName,
                    partnerAvatar = partnerAvatar
                )
            }
            if (thinking) {
                item {
                    DefuseThinkingBubble(partnerName = partnerName, partnerAvatar = partnerAvatar)
                }
            }
        }
        if (showManual) {
            ManualCard(manualText = manualText, helpUsed = helpUsed)
            Spacer(Modifier.size(6.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DefuseChip(text = "汇报面板", selected = false, onClick = onReport)
            DefuseChip(text = "下一步", selected = false, onClick = { onSend("下一步？") })
            DefuseChip(text = "再解释一遍", selected = false, onClick = { onSend("再解释一遍") })
            DefuseChip(text = if (showManual) "收起手册" else "求助手册", selected = showManual, onClick = onToggleHelp)
        }
        Spacer(Modifier.size(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("描述你看到的面板…") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (input.isNotBlank()) {
                        onSend(input)
                        input = ""
                    }
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = BombText,
                    unfocusedTextColor = BombText,
                    cursorColor = BombAccent,
                    focusedBorderColor = BombAccent.copy(alpha = 0.7f),
                    unfocusedBorderColor = BombBorder,
                    focusedContainerColor = BombCard,
                    unfocusedContainerColor = BombCard,
                    focusedPlaceholderColor = BombTextDim,
                    unfocusedPlaceholderColor = BombTextDim
                )
            )
            Spacer(Modifier.size(6.dp))
            IconButton(
                onClick = {
                    if (input.isNotBlank()) {
                        onSend(input)
                        input = ""
                    }
                },
                enabled = input.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint = if (input.isNotBlank()) BombAccent else BombTextDim
                )
            }
        }
    }
}

@Composable
internal fun DefuseChatBubble(
    message: DefuseChatMessage,
    partnerName: String,
    partnerAvatar: String?
) {
    if (message.sender == DefuseChatSender.USER) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                color = BombAccent,
                shape = UserBubbleShape
            ) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = BombAccentText
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            AiAvatar(avatarUri = partnerAvatar, name = partnerName, size = 30.dp)
            Spacer(Modifier.size(6.dp))
            Surface(
                color = BombCard,
                shape = AiBubbleShape,
                border = BorderStroke(1.dp, BombBorder)
            ) {
                Column {
                    Text(
                        text = partnerName,
                        modifier = Modifier.padding(start = 14.dp, top = 8.dp, end = 14.dp),
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = BombAccent
                    )
                    Text(
                        text = message.text,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color = BombText
                    )
                }
            }
        }
    }
}

@Composable
internal fun DefuseThinkingBubble(partnerName: String, partnerAvatar: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        AiAvatar(avatarUri = partnerAvatar, name = partnerName, size = 30.dp)
        Spacer(Modifier.size(6.dp))
        Surface(
            color = BombCard,
            shape = AiBubbleShape,
            border = BorderStroke(1.dp, BombBorder)
        ) {
            Text(
                text = "翻手册中…",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = BombTextDim
            )
        }
    }
}

@Composable
internal fun ManualCard(manualText: String, helpUsed: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BombCardHi,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BombBorder)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = BombAccent
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = "手册摘录",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = BombText
                )
                Spacer(Modifier.weight(1f))
                if (!helpUsed) {
                    Text(
                        text = "首次求助后对手队伍加速 8 秒",
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        color = BombBad.copy(alpha = 0.9f)
                    )
                }
            }
            Spacer(Modifier.size(6.dp))
            Text(
                text = manualText,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = BombText
            )
        }
    }
}

// ===== 结算页 =====
