package com.quiddity.app.ui.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.domain.TimeLibraryEngine
import com.quiddity.app.ui.components.ConfirmDialog


/** 把 "13:30" 转成"下午 1:30"这种用户一看就懂的说法。 */
private fun readableTimeText(time: String): String {
    val parts = time.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: return time
    val minute = parts.getOrNull(1) ?: "00"
    val period = when {
        hour < 6 -> "凌晨"
        hour < 12 -> "上午"
        hour < 14 -> "中午"
        hour < 18 -> "下午"
        else -> "晚上"
    }
    val hour12 = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "$period $hour12:$minute"
}

/** 查看时间库：内容展示弹窗（用户可读的时间说法）。 */
@Composable
internal fun TimeLibraryDetailDialog(
    conversation: Conversation?,
    onDismiss: () -> Unit
) {
    val conv = conversation ?: return
    val today = java.time.LocalDate.now().toString()
    val generatedToday = conv.timeLibraryGeneratedDate == today
    val times = conv.timeLibrary
    val body = buildString {
        if (!generatedToday) {
            appendLine("今天还没有生成时间库。")
            appendLine("请确认模型接口已配置，重新打开本会话会再次尝试生成。")
        } else if (times.isEmpty()) {
            appendLine("今天的时间库是空的，AI 判断今天不需要主动发消息。")
        } else {
            times.forEach { point ->
                val state = if (point.isPending) "待触发" else "已处理"
                appendLine("${readableTimeText(point.time)} · $state")
            }
            appendLine("")
            appendLine("说明：「下午 1:30」就是下午一点半；「待触发」表示还没到时间，「已处理」表示到点已经处理过了。")
        }
    }
    ConfirmDialog(
        title = "今日时间库",
        message = body.trim(),
        confirmText = "知道了",
        cancelText = null,
        onConfirm = onDismiss,
        onDismiss = onDismiss
    )
}
/** 时间库可编辑面板：10 个固定时间框，上下午各 5 个，每个框可上下滚选时间并独立启用。 */
@Composable
internal fun TimeLibraryEditorPanel(
    conversation: Conversation,
    onSave: (List<String>, List<Int>) -> Unit,
    onBack: () -> Unit
) {
    val disabledSet = conversation.disabledTimeSlots.toSet()
    val timesState = remember(conversation.id, conversation.timeLibrary) {
        mutableStateOf(
            List(TimeLibraryEngine.SLOT_COUNT) { index ->
                conversation.timeLibrary.getOrNull(index)?.time ?: defaultSlotTime(index)
            }
        )
    }
    val enabledState = remember(conversation.id, conversation.disabledTimeSlots) {
        mutableStateOf(
            List(TimeLibraryEngine.SLOT_COUNT) { index -> index !in disabledSet }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "时间库",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onBack) { Text("返回") }
        }
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = "上午 / 下午各 5 个时间框，最右侧开关控制是否启用。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.size(8.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(TimeLibraryEngine.SLOT_COUNT) { index ->
                if (index == 5) {
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(
                        text = "下午",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                }
                val current = timesState.value[index]
                val hour = current.substringBefore(":").toInt()
                val minute = current.substringAfter(":").toInt()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (index < 5) "上午 ${index + 1}" else "下午 ${index - 4}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(56.dp)
                    )
                    WheelColumn(
                        values = (0..23).map { it.toString().padStart(2, '0') },
                        selectedIndex = hour,
                        onSelect = { selected ->
                            timesState.value = timesState.value.toMutableList().also {
                                it[index] = "%02d:%02d".format(selected, minute)
                            }
                        }
                    )
                    Text(
                        text = ":",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    WheelColumn(
                        values = (0..59).map { it.toString().padStart(2, '0') },
                        selectedIndex = minute,
                        onSelect = { selected ->
                            timesState.value = timesState.value.toMutableList().also {
                                it[index] = "%02d:%02d".format(hour, selected)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = enabledState.value[index],
                        onCheckedChange = { enabled ->
                            enabledState.value = enabledState.value.toMutableList().also {
                                it[index] = enabled
                            }
                        }
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                )
            }
        }
        Spacer(modifier = Modifier.size(12.dp))
        Button(
            onClick = {
                val times = mutableListOf<String>()
                val disabled = mutableListOf<Int>()
                for (i in 0 until TimeLibraryEngine.SLOT_COUNT) {
                    if (enabledState.value[i]) {
                        times.add(timesState.value[i])
                    } else {
                        disabled.add(i)
                    }
                }
                onSave(times, disabled)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("保存") }
    }
}

@Composable
private fun WheelColumn(
    values: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val density = LocalDensity.current
    val stepPx = with(density) { WHEEL_ITEM_HEIGHT.toPx() }
    var dragAccum by remember { mutableFloatStateOf(0f) }
    val currentSelected by rememberUpdatedState(selectedIndex)
    Box(
        modifier = Modifier
            .width(56.dp)
            .height(WHEEL_HEIGHT)
            .pointerInput(values.size) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    dragAccum += dragAmount
                    while (dragAccum >= stepPx) {
                        dragAccum -= stepPx
                        onSelect((currentSelected - 1).coerceAtLeast(0))
                    }
                    while (dragAccum <= -stepPx) {
                        dragAccum += stepPx
                        onSelect((currentSelected + 1).coerceAtMost(values.size - 1))
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            WheelRow(values, selectedIndex - 1, selectedIndex, onSelect)
            WheelRow(values, selectedIndex, selectedIndex, onSelect)
            WheelRow(values, selectedIndex + 1, selectedIndex, onSelect)
        }
        HorizontalDivider(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
        HorizontalDivider(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 56.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun WheelRow(
    values: List<String>,
    index: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val safeIndex = index.coerceIn(0, values.size - 1)
    val selected = index == selectedIndex
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(WHEEL_ITEM_HEIGHT)
            .clickable { onSelect(safeIndex) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = values[safeIndex],
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            },
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

private fun defaultSlotTime(index: Int): String =
    if (index < 5) "09:00" else "15:00"

private val WHEEL_HEIGHT = 84.dp
private val WHEEL_ITEM_HEIGHT = 28.dp
