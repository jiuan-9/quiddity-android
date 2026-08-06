package com.quiddity.app.ui.miniapps.board

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quiddity.app.domain.board.BoardState
import com.quiddity.app.domain.board.Stone
import kotlin.math.roundToInt

/*
 * 对局页：棋盘（Canvas）+ 状态栏 + 对局内聊天 + 操作（停一手/认输）+ 结算弹窗。
 */

@Composable
fun BoardGameScreen(
    session: BoardSession,
    onBack: () -> Unit,
    onCellTap: (Int, Int) -> Unit,
    onPass: () -> Unit,
    onResign: () -> Unit,
    onSendChat: (String) -> Unit,
    onRematch: () -> Unit,
    onOpenChat: (() -> Unit)?
) {
    BackHandler(onBack = onBack)
    val finished = session.status as? BoardStatus.Finished

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        BoardTopBar(
            title = "${session.gameType.displayName} · ${session.opponentName}",
            onBack = onBack,
            horizontalPadding = 12.dp,
            trailing = {
                Text(
                    text = if (session.llmEnabled) "LLM" else "本地电脑",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (session.llmEnabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            (if (session.llmEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant).copy(alpha = 0.12f)
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StoneDot(color = if (session.userStone == Stone.BLACK) Color(0xFF1B1B1F) else Color(0xFFF2EEE6))
            Text(
                text = buildTurnText(session),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (session.gameType.isGo && finished == null) {
                TextButton(onClick = onPass, enabled = !session.thinking) {
                    Text("停一手")
                }
                TextButton(onClick = onResign) {
                    Text("认输", color = MaterialTheme.colorScheme.error)
                }
            } else if (finished == null) {
                TextButton(onClick = onResign) {
                    Text("认输", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        session.notice?.let { notice ->
            Text(
                text = notice,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 24.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.54f)
                .padding(horizontal = 18.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            GameBoardView(
                state = session.board,
                interactive = finished == null && !session.thinking && session.board.current == session.userStone,
                onCellTap = onCellTap
            )
        }

        ChatPanel(session = session, onSend = onSendChat)
    }

    finished?.let { result ->
        BoardResultDialog(
            result = result,
            gameName = session.gameType.displayName,
            onRematch = onRematch,
            onExit = onBack,
            onOpenChat = onOpenChat
        )
    }
}

private fun buildTurnText(session: BoardSession): String {
    val finished = session.status as? BoardStatus.Finished
    if (finished != null) return "对局结束：${finished.winnerName ?: "平局"}"
    val yourTurn = session.board.current == session.userStone
    return if (session.thinking) {
        "「${session.opponentName}」思考中…"
    } else if (yourTurn) {
        "轮到你落子（${session.userStone.label}）"
    } else {
        "等待对方落子…"
    }
}

@Composable
fun GameBoardView(
    state: BoardState,
    interactive: Boolean,
    onCellTap: (Int, Int) -> Unit
) {
    val boardColor = Color(0xFFD9B98A)
    val lineColor = Color(0xFF7A5A32)
    val markerColor = Color(0xFFE53935)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .pointerInput(state.size, interactive) {
                if (!interactive) return@pointerInput
                detectTapGestures { offset ->
                    val minDim = minOf(size.width, size.height).toFloat()
                    val pad = minDim * 0.06f
                    val cell = (minDim - pad * 2f) / (state.size - 1)
                    val row = ((offset.y - pad) / cell).roundToInt().coerceIn(0, state.size - 1)
                    val col = ((offset.x - pad) / cell).roundToInt().coerceIn(0, state.size - 1)
                    onCellTap(row, col)
                }
            }
    ) {
        val minDim = minOf(size.width, size.height)
        val pad = minDim * 0.06f
        val inner = minDim - pad * 2f
        val cell = inner / (state.size - 1)

        drawRoundRect(
            color = boardColor,
            cornerRadius = CornerRadius(16f, 16f),
            size = Size(size.width, size.height)
        )

        for (i in 0 until state.size) {
            val p = pad + i * cell
            drawLine(
                color = lineColor.copy(alpha = 0.9f),
                start = Offset(pad, p),
                end = Offset(pad + inner, p),
                strokeWidth = 1.5f
            )
            drawLine(
                color = lineColor.copy(alpha = 0.9f),
                start = Offset(p, pad),
                end = Offset(p, pad + inner),
                strokeWidth = 1.5f
            )
        }

        if (state.gameType.isGo) {
            val star = state.size / 2
            for (r in listOf(2, star, state.size - 3)) {
                for (c in listOf(2, star, state.size - 3)) {
                    drawCircle(
                        color = lineColor,
                        radius = cell * 0.09f,
                        center = Offset(pad + c * cell, pad + r * cell)
                    )
                }
            }
        }

        for (r in 0 until state.size) {
            for (c in 0 until state.size) {
                val stone = state.stoneAt(r, c)
                if (stone == Stone.EMPTY) continue
                val center = Offset(pad + c * cell, pad + r * cell)
                val radius = cell * 0.46f
                val brush = if (stone == Stone.BLACK) {
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF43434B), Color(0xFF101013)),
                        center = center - Offset(radius * 0.35f, radius * 0.35f),
                        radius = radius * 1.6f
                    )
                } else {
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFFFFFFF), Color(0xFFD8D2C6)),
                        center = center - Offset(radius * 0.35f, radius * 0.35f),
                        radius = radius * 1.6f
                    )
                }
                drawCircle(brush = brush, radius = radius, center = center)
                if (stone == Stone.WHITE) {
                    drawCircle(
                        color = Color(0xFF8A8377).copy(alpha = 0.6f),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 1.2f)
                    )
                }
            }
        }

        state.lastMove?.let { move ->
            drawCircle(
                color = markerColor,
                radius = cell * 0.13f,
                center = Offset(pad + move.col * cell, pad + move.row * cell)
            )
        }
    }
}

@Composable
private fun StoneDot(color: Color) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, Color.Gray.copy(alpha = 0.5f), CircleShape)
    )
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ChatPanel(
    session: BoardSession,
    onSend: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val messages = session.chat
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
    var input by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(0.46f)
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "对局聊天",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (session.llmEnabled) {
                        "下棋间隙可以和「${session.opponentName}」聊聊天"
                    } else {
                        "对方是本地电脑，不参与聊天"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatBubble(message)
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("说点什么…", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                shape = RoundedCornerShape(18.dp),
                maxLines = 3
            )
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable {
                        if (input.isNotBlank()) {
                            onSend(input)
                            input = ""
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(message: BoardChatMessage) {
    val isUser = message.fromUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isUser) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp, 16.dp, if (isUser) 4.dp else 16.dp, if (isUser) 16.dp else 4.dp))
                .background(
                    if (isUser) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun BoardResultDialog(
    result: BoardStatus.Finished,
    gameName: String,
    onRematch: () -> Unit,
    onExit: () -> Unit,
    onOpenChat: (() -> Unit)?
) {
    AlertDialog(
        onDismissRequest = onExit,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text("对局结束", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "${result.winnerName ?: "平局"} · $gameName",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = result.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                result.score?.let { score ->
                    Text(
                        text = "黑 ${score.black} : 白 ${score.white}（白贴 7.5，简化计分）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRematch) {
                Text("再来一局")
            }
        },
        dismissButton = {
            Row {
                onOpenChat?.let {
                    TextButton(onClick = it) {
                        Text("去私聊", color = MaterialTheme.colorScheme.primary)
                    }
                }
                TextButton(onClick = onExit) {
                    Text("返回")
                }
            }
        }
    )
}
