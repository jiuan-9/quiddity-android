package com.quiddity.app.ui.miniapps.board

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quiddity.app.domain.board.BoardState
import com.quiddity.app.domain.board.Stone
import com.quiddity.app.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
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
    var showExitConfirm by remember { mutableStateOf(false) }
    // 结算弹窗可点掉：关掉后留在对局页复盘棋盘，需要时再点"查看战报"重新打开
    var showResult by remember(session.id) { mutableStateOf(true) }
    // 开局"分先"动画：随机黑白归属，简单展示后淡出（朴素）
    var showColorDraw by remember(session.id) { mutableStateOf(false) }
    val drawAlpha = remember(session.id) { Animatable(0f) }
    val drawScale = remember(session.id) { Animatable(0.92f) }
    LaunchedEffect(session.id) {
        if (session.board.moveCount == 0 && session.status == BoardStatus.Playing) {
            showColorDraw = true
            drawAlpha.snapTo(0f)
            drawScale.snapTo(0.92f)
            drawAlpha.animateTo(1f, tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate))
            drawScale.animateTo(1f, tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate))
            delay(900)
            drawAlpha.animateTo(0f, tween(Motion.DurationShort + 60, easing = Motion.EasingEmphasizedAccelerate))
            showColorDraw = false
        }
    }
    BackHandler {
        if (session.status == BoardStatus.Playing) {
            showExitConfirm = true
        } else {
            onBack()
        }
    }
    val finished = session.status as? BoardStatus.Finished

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding()
    ) {
        BoardTopBar(
            title = "${session.gameType.displayName} ${session.board.size}×${session.board.size} · ${session.opponentName}",
            onBack = {
                if (session.status == BoardStatus.Playing) showExitConfirm = true else onBack()
            },
            horizontalPadding = 12.dp,
            trailing = {
                Text(
                    text = if (session.llmEnabled) {
                        "LLM"
                    } else {
                        "本地电脑 · ${session.difficulty.label}"
                    },
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
            AnimatedContent(
                targetState = buildTurnText(session),
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val enter = fadeIn(tween(Motion.DurationShort, easing = Motion.EasingEmphasizedDecelerate)) +
                        slideInVertically(tween(Motion.DurationShort, easing = Motion.EasingStandard)) { -it / 3 }
                    val exit = fadeOut(tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate)) +
                        slideOutVertically(tween(Motion.DurationShort, easing = Motion.EasingStandard)) { it / 3 }
                    enter togetherWith exit
                },
                label = "turn_text"
            ) { text ->
                if (session.thinking) {
                    val pulse by rememberInfiniteTransition(label = "thinking").animateFloat(
                        initialValue = 0.55f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            tween(600, easing = Motion.EasingStandard),
                            RepeatMode.Reverse
                        ),
                        label = "thinking_alpha"
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .graphicsLayer { alpha = pulse }
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (session.gameType.isGo && finished == null) {
                PressableTextButton(onClick = onPass, enabled = !session.thinking) {
                    Text("停一手")
                }
                PressableTextButton(onClick = onResign) {
                    Text("认输", color = MaterialTheme.colorScheme.error)
                }
            } else if (finished == null) {
                PressableTextButton(onClick = onResign) {
                    Text("认输", color = MaterialTheme.colorScheme.error)
                }
            } else if (!showResult) {
                PressableTextButton(onClick = { showResult = true }) {
                    Text("查看战报", color = MaterialTheme.colorScheme.primary)
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
        if (showResult) {
            BoardResultDialog(
                result = result,
                gameName = "${session.gameType.displayName} ${session.board.size}×${session.board.size}",
                onDismiss = { showResult = false },
                onRematch = onRematch,
                onExit = onBack,
                onOpenChat = onOpenChat
            )
        }
    }

    if (showColorDraw) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.graphicsLayer {
                    alpha = drawAlpha.value
                    scaleX = drawScale.value
                    scaleY = drawScale.value
                },
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 36.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "分先",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DrawDot(color = Color(0xFF1B1B1F), highlighted = session.userStone == Stone.BLACK)
                        DrawDot(color = Color(0xFFF2EEE6), highlighted = session.userStone == Stone.WHITE)
                    }
                    Text(
                        text = "你执${session.userStone.label}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (session.userStone == Stone.BLACK) "黑方先手" else "对方执黑先行",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text("退出对局？", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "对局还在进行中，退出后本局不会保留。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    onBack()
                }) {
                    Text("退出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) {
                    Text("继续对局")
                }
            }
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
    val boardColor = Color(0xFFE2C189)
    val boardShade = Color(0xFFC69A5E)
    val lineColor = Color(0xFF6E4C26)
    val markerColor = Color(0xFFE0453A)

    val scope = rememberCoroutineScope()
    val appearAnims = remember { mutableStateMapOf<Pair<Int, Int>, Animatable<Float, AnimationVector1D>>() }
    val dropAnims = remember { mutableStateMapOf<Pair<Int, Int>, Animatable<Float, AnimationVector1D>>() }
    val vanishAnims = remember { mutableStateMapOf<Pair<Int, Int>, VanishAnim>() }
    val prevGrid = remember { mutableStateOf(state.grid) }
    val markerAlpha = remember { Animatable(0f) }
    var hoverCell by remember(state.size, interactive) { mutableStateOf<Pair<Int, Int>?>(null) }

    fun snapCell(offset: Offset, canvasWidth: Int, canvasHeight: Int, boardSize: Int): Pair<Int, Int> {
        val minDim = min(canvasWidth, canvasHeight).toFloat()
        val pad = minDim * 0.06f
        val cell = (minDim - pad * 2f) / (boardSize - 1)
        val row = ((offset.y - pad) / cell).roundToInt().coerceIn(0, boardSize - 1)
        val col = ((offset.x - pad) / cell).roundToInt().coerceIn(0, boardSize - 1)
        return row to col
    }

    LaunchedEffect(state.grid) {
        val prev = prevGrid.value
        val cur = state.grid
        val size = state.size
        for (idx in cur.indices) {
            if (Stone.fromCode(cur[idx]) != Stone.EMPTY &&
                Stone.fromCode(prev.getOrElse(idx) { Stone.EMPTY.code }) == Stone.EMPTY
            ) {
                val key = idx / size to idx % size
                if (key !in appearAnims) {
                    val anim = Animatable(0f)
                    val drop = Animatable(1f)
                    appearAnims[key] = anim
                    dropAnims[key] = drop
                    scope.launch {
                        drop.animateTo(0f, tween(150, easing = Motion.EasingEmphasizedDecelerate))
                        anim.animateTo(
                            1f,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                        )
                    }
                }
            }
        }
        for (idx in prev.indices) {
            val stone = Stone.fromCode(prev[idx])
            if (stone != Stone.EMPTY &&
                Stone.fromCode(cur.getOrElse(idx) { Stone.EMPTY.code }) == Stone.EMPTY
            ) {
                val key = idx / size to idx % size
                if (key !in vanishAnims) {
                    val anim = Animatable(1f)
                    vanishAnims[key] = VanishAnim(stone, anim)
                    scope.launch {
                        anim.animateTo(0f, tween(260, easing = Motion.EasingEmphasizedAccelerate))
                        vanishAnims.remove(key)
                    }
                }
            }
        }
        prevGrid.value = cur
    }

    LaunchedEffect(state.lastMove) {
        if (state.lastMove != null) {
            markerAlpha.snapTo(0f)
            markerAlpha.animateTo(1f, tween(200, easing = Motion.EasingEmphasizedDecelerate))
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .pointerInput(state.size, interactive) {
                if (!interactive) {
                    hoverCell = null
                    return@pointerInput
                }
                detectTapGestures(
                    onPress = {
                        hoverCell = snapCell(it, size.width, size.height, state.size)
                        tryAwaitRelease()
                        hoverCell = null
                    },
                    onTap = { offset ->
                        val (row, col) = snapCell(offset, size.width, size.height, state.size)
                        onCellTap(row, col)
                    }
                )
            }
    ) {
        val minDim = minOf(size.width, size.height)
        val pad = minDim * 0.06f
        val inner = minDim - pad * 2f
        val cell = inner / (state.size - 1)

        fun drawStoneAt(center: Offset, radius: Float, stone: Stone, alpha: Float, scale: Float) {
            if (alpha <= 0f || scale <= 0f) return
            val r = radius * scale
            val shadow = if (stone == Stone.BLACK) Color(0x55100A05) else Color(0x3360452B)
            drawCircle(
                color = shadow,
                radius = r,
                center = center + Offset(0f, radius * 0.14f),
                alpha = alpha
            )
            val brush = if (stone == Stone.BLACK) {
                Brush.radialGradient(
                    colors = listOf(Color(0xFF4A4A52), Color(0xFF232327), Color(0xFF0D0D10)),
                    center = center - Offset(radius * 0.35f, radius * 0.35f),
                    radius = radius * 1.6f
                )
            } else {
                Brush.radialGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFF4EFE4), Color(0xFFD8D0C0)),
                    center = center - Offset(radius * 0.35f, radius * 0.35f),
                    radius = radius * 1.6f
                )
            }
            drawCircle(brush = brush, radius = r, center = center, alpha = alpha)
            if (stone == Stone.BLACK) {
                drawCircle(
                    color = Color(0xFF6A6A74).copy(alpha = 0.3f * alpha),
                    radius = r * 0.72f,
                    center = center - Offset(r * 0.32f, r * 0.36f),
                    style = Stroke(width = maxOf(1.4f, r * 0.08f))
                )
            } else {
                drawCircle(
                    color = Color(0xFF9A8F7C).copy(alpha = 0.55f * alpha),
                    radius = r,
                    center = center,
                    style = Stroke(width = maxOf(1.2f, r * 0.05f))
                )
            }
        }

        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(boardColor, boardShade),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            ),
            cornerRadius = CornerRadius(16f, 16f),
            size = Size(size.width, size.height)
        )
        for (i in 0..10) {
            val y = minDim * (0.08f + i * 0.085f)
            drawLine(
                color = Color(0xFF8A6234).copy(alpha = 0.05f + (i % 3) * 0.012f),
                start = Offset(minDim * 0.02f, y),
                end = Offset(minDim * 0.98f, y + minDim * 0.02f),
                strokeWidth = 1.6f
            )
        }
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color(0x22000000)),
                center = Offset(minDim / 2f, minDim / 2f),
                radius = minDim * 0.72f
            ),
            cornerRadius = CornerRadius(16f, 16f),
            size = Size(minDim, minDim)
        )
        drawRoundRect(
            color = Color(0x337A5A32),
            cornerRadius = CornerRadius(16f, 16f),
            size = Size(size.width, size.height),
            style = Stroke(width = 1.2f)
        )

        for (i in 0 until state.size) {
            val p = pad + i * cell
            drawLine(
                color = lineColor.copy(alpha = 0.82f),
                start = Offset(pad, p),
                end = Offset(pad + inner, p),
                strokeWidth = 1.4f
            )
            drawLine(
                color = lineColor.copy(alpha = 0.82f),
                start = Offset(p, pad),
                end = Offset(p, pad + inner),
                strokeWidth = 1.4f
            )
        }

        if (state.gameType.isGo) {
            val star = state.size / 2
            for (r in listOf(2, star, state.size - 3)) {
                for (c in listOf(2, star, state.size - 3)) {
                    drawCircle(
                        color = lineColor.copy(alpha = 0.28f),
                        radius = cell * 0.16f,
                        center = Offset(pad + c * cell, pad + r * cell)
                    )
                    drawCircle(
                        color = lineColor,
                        radius = cell * 0.085f,
                        center = Offset(pad + c * cell, pad + r * cell)
                    )
                }
            }
        }

        if (interactive && hoverCell != null &&
            state.stoneAt(hoverCell!!.first, hoverCell!!.second) == Stone.EMPTY
        ) {
            val (hr, hc) = hoverCell!!
            val ghost = if (state.current == Stone.BLACK) Color(0x551B1B1F) else Color(0x66F2EEE6)
            val ghostRing = if (state.current == Stone.BLACK) Color(0xFFFFFFFF) else Color(0xFF6E4C26)
            drawCircle(
                color = ghost,
                radius = cell * 0.46f,
                center = Offset(pad + hc * cell, pad + hr * cell)
            )
            drawCircle(
                color = ghostRing.copy(alpha = 0.35f),
                radius = cell * 0.13f,
                center = Offset(pad + hc * cell, pad + hr * cell),
                style = Stroke(width = 1.2f)
            )
        }

        for ((key, vanish) in vanishAnims) {
            drawStoneAt(
                center = Offset(pad + key.second * cell, pad + key.first * cell),
                radius = cell * 0.46f,
                stone = vanish.stone,
                alpha = vanish.anim.value,
                scale = 1f
            )
        }
        for (r in 0 until state.size) {
            for (c in 0 until state.size) {
                val stone = state.stoneAt(r, c)
                if (stone == Stone.EMPTY) continue
                val drop = dropAnims[r to c]?.value ?: 0f
                drawStoneAt(
                    center = Offset(pad + c * cell, pad + r * cell + cell * 0.9f * drop),
                    radius = cell * 0.46f,
                    stone = stone,
                    alpha = 1f,
                    scale = appearAnims[r to c]?.value ?: 1f
                )
            }
        }

        state.lastMove?.let { move ->
            drawCircle(
                color = markerColor.copy(alpha = markerAlpha.value * 0.22f),
                radius = cell * 0.32f,
                center = Offset(pad + move.col * cell, pad + move.row * cell)
            )
            drawCircle(
                color = markerColor.copy(alpha = markerAlpha.value),
                radius = cell * 0.12f,
                center = Offset(pad + move.col * cell, pad + move.row * cell)
            )
        }
    }
}

/** 提子淡出动画：记录被移除棋子的颜色与透明度动画。 */
private class VanishAnim(
    val stone: Stone,
    val anim: Animatable<Float, AnimationVector1D>
)

@Composable
private fun StoneDot(color: Color) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(color, if (color == Color(0xFF1B1B1F)) Color(0xFF050506) else Color(0xFFD8D0C0)),
                    center = Offset(5f, 5f),
                    radius = 12f
                )
            )
            .border(1.dp, Color(0x55807A70), CircleShape)
    )
}

@Composable
private fun DrawDot(color: Color, highlighted: Boolean) {
    val outline = if (highlighted) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Gray.copy(alpha = 0.45f)
    }
    Box(
        modifier = Modifier
            .size(if (highlighted) 34.dp else 30.dp)
            .clip(CircleShape)
            .background(color)
            .border(2.dp, outline, CircleShape)
    )
}

@Composable
private fun PressableTextButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.92f else 1f,
        animationSpec = tween(90, easing = Motion.EasingEmphasizedAccelerate),
        label = "press_scale"
    )
    TextButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = Modifier.scale(scale)
    ) {
        content()
    }
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
    val canSend = input.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(0.46f)
            .padding(start = 16.dp, end = 16.dp, bottom = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Text(
                text = "对局聊天",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Spacer(Modifier.size(8.dp))
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
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        ChatBubble(message)
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("说点什么…", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                shape = RoundedCornerShape(18.dp),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (canSend) {
                        onSend(input)
                        input = ""
                    }
                })
            )
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = if (canSend) {
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                )
                            } else {
                                listOf(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                )
                            }
                        )
                    )
                    .clickable(enabled = canSend) {
                        onSend(input)
                        input = ""
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "发送",
                    tint = if (canSend) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(message: BoardChatMessage) {
    val isUser = message.fromUser
    // 新气泡淡入 + 轻微放大，表达"消息出现"的质感
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.value
                val s = 0.92f + 0.08f * appear.value
                scaleX = s
                scaleY = s
            },
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
    onDismiss: () -> Unit,
    onRematch: () -> Unit,
    onExit: () -> Unit,
    onOpenChat: (() -> Unit)?
) {
    val win = result.winnerName == "你"
    val draw = result.winnerName == null
    val bannerColor = when {
        win -> Color(0xFF2E7D32)
        draw -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        else -> Color(0xFFC62828)
    }
    val bannerText = when {
        win -> "你赢了"
        draw -> "平局"
        else -> "惜败"
    }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text("对局结束", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.graphicsLayer {
                    alpha = appear.value
                    val s = 0.94f + 0.06f * appear.value
                    scaleX = s
                    scaleY = s
                },
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(bannerColor.copy(alpha = 0.12f))
                        .padding(vertical = 10.dp, horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = bannerText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = bannerColor
                        )
                        Text(
                            text = "${result.winnerName ?: "双方"} · $gameName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    text = result.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                result.score?.let { score ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ScoreColumn(label = "黑", value = score.black, color = Color(0xFF1B1B1F))
                            Text(
                                text = ":",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            ScoreColumn(label = "白", value = score.white, color = Color(0xFFF2EEE6))
                        }
                    }
                    Text(
                        text = "白贴 7.5 · 简化数子计分",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
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

@Composable
private fun ScoreColumn(label: String, value: Double, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, Color(0x557A7368), CircleShape)
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
