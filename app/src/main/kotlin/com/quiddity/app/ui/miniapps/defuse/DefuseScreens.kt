package com.quiddity.app.ui.miniapps.defuse

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quiddity.app.domain.defuse.ButtonAction
import com.quiddity.app.domain.defuse.ButtonColor
import com.quiddity.app.domain.defuse.DefuseDifficulty
import com.quiddity.app.domain.defuse.DefuseLlmPrompt
import com.quiddity.app.domain.defuse.DefuseModule
import com.quiddity.app.domain.defuse.DefuseSession
import com.quiddity.app.domain.defuse.WireColor
import com.quiddity.app.ui.components.AiAvatar
import com.quiddity.app.ui.miniapps.board.BoardInvitee
import com.quiddity.app.ui.theme.AiBubbleShape
import com.quiddity.app.ui.theme.Motion
import com.quiddity.app.ui.theme.UserBubbleShape

// ===== 拆弹小队固定深色配色（不跟随系统明暗，杜绝纯黑字） =====

private val BombBg = Color(0xFF0B0F19)
private val BombCard = Color(0xFF151C2B)
private val BombCardHi = Color(0xFF1C2536)
private val BombPanelInner = Color(0xFF10141F)
private val BombBorder = Color(0xFF2B3448)
private val BombText = Color(0xFFF1F4FA)
private val BombTextDim = Color(0xFFA9B4C8)
private val BombAccent = Color(0xFFFFB74D)
private val BombAccentText = Color(0xFF201505)
private val BombGood = Color(0xFF69F0AE)
private val BombBad = Color(0xFFFF5252)

private fun wireColor(color: WireColor): Color = when (color) {
    WireColor.RED -> Color(0xFFFF5252)
    WireColor.BLUE -> Color(0xFF4FC3F7)
    WireColor.YELLOW -> Color(0xFFFFD54F)
    WireColor.WHITE -> Color(0xFFF5F5F5)
    WireColor.GREEN -> Color(0xFF69F0AE)
}

private fun buttonColor(color: ButtonColor): Color = when (color) {
    ButtonColor.RED -> Color(0xFFD32F2F)
    ButtonColor.BLUE -> Color(0xFF1976D2)
    ButtonColor.YELLOW -> Color(0xFFF9A825)
    ButtonColor.WHITE -> Color(0xFFE0E0E0)
}

private fun buttonTextColor(color: ButtonColor): Color =
    if (color == ButtonColor.YELLOW || color == ButtonColor.WHITE) Color(0xFF26261F) else Color.White

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(totalSec / 60, totalSec % 60)
}

private fun DefuseResultKind.icon(): ImageVector = when (this) {
    DefuseResultKind.WIN -> Icons.Rounded.EmojiEvents
    DefuseResultKind.SAVED_LATE -> Icons.Rounded.CheckCircle
    DefuseResultKind.LOST_RACE -> Icons.Rounded.Timer
    DefuseResultKind.EXPLODED -> Icons.Rounded.Whatshot
}

private fun DefuseResultKind.tint(): Color = when (this) {
    DefuseResultKind.WIN -> Color(0xFFFFC94D)
    DefuseResultKind.SAVED_LATE -> Color(0xFF4FC3F7)
    DefuseResultKind.LOST_RACE -> Color(0xFFB39DDB)
    DefuseResultKind.EXPLODED -> Color(0xFFFF5252)
}

// ===== 通用组件 =====

@Composable
private fun DefusePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = BombAccent,
            contentColor = BombAccentText,
            disabledContainerColor = BombAccent.copy(alpha = 0.4f),
            disabledContentColor = BombAccentText.copy(alpha = 0.6f)
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 13.dp)
    ) {
        Text(text = text, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DefuseSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = BombText),
        border = BorderStroke(1.dp, BombBorder),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 13.dp)
    ) {
        Text(text = text, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DefuseChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) BombAccent.copy(alpha = 0.18f) else BombCard)
            .border(
                width = 1.dp,
                color = if (selected) BombAccent.copy(alpha = 0.8f) else BombBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = if (selected) BombAccent else BombText
        )
    }
}

@Composable
private fun DefuseTopBar(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = BombText
            )
        }
        Text(
            text = title,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = BombText,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

// ===== 设置页 =====

@Composable
fun DefuseSetupScreen(
    invitees: List<BoardInvitee>,
    state: DefuseUiState,
    onBack: () -> Unit,
    onSelectPartner: (BoardInvitee) -> Unit,
    onSelectScript: () -> Unit,
    onSelectDifficulty: (DefuseDifficulty) -> Unit,
    onStart: () -> Unit
) {
    BackHandler(onBack = onBack)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BombBg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { DefuseTopBar(title = "拆弹小队", onBack = onBack) }
        item { DefuseHeroHeader() }
        item { SectionTitle("选择搭档") }
        item {
            PartnerRow(
                name = "内置搭档",
                subtitle = "脚本搭档 · 无需 API，离线可玩",
                avatarUri = null,
                selected = state.partner == null,
                onClick = onSelectScript
            )
        }
        items(invitees, key = { it.key }) { invitee ->
            PartnerRow(
                name = invitee.displayName,
                subtitle = invitee.subtitle,
                avatarUri = invitee.avatarUri,
                selected = state.partner?.key == invitee.key,
                onClick = { onSelectPartner(invitee) }
            )
        }
        item { SectionTitle("选择难度") }
        items(DefuseDifficulty.entries) { difficulty ->
            DifficultyCard(
                difficulty = difficulty,
                selected = state.difficulty == difficulty,
                onClick = { onSelectDifficulty(difficulty) }
            )
        }
        item {
            state.notice?.let { notice ->
                Text(
                    text = notice,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = BombBad,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            DefusePrimaryButton(
                text = if (state.checking) "准备中…" else "开始拆弹",
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.checking
            )
            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun DefuseHeroHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF1A2333), Color(0xFF10141F))))
            .border(1.dp, BombBorder, RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(BombAccent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = BombAccent,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.size(12.dp))
            Column {
                Text(
                    text = "拆弹小队",
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = BombText
                )
                Text(
                    text = "你看面板，搭档翻手册 —— 和 AI 组队，赢过对手队伍",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = BombTextDim
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = BombText,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun PartnerRow(
    name: String,
    subtitle: String,
    avatarUri: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) BombAccent.copy(alpha = 0.14f) else BombCard)
            .border(
                width = 1.5.dp,
                color = if (selected) BombAccent.copy(alpha = 0.9f) else BombBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (avatarUri != null) {
            AiAvatar(avatarUri = avatarUri, name = name, size = 42.dp)
        } else {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(BombCardHi),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SmartToy,
                    contentDescription = null,
                    tint = BombAccent
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = BombText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = BombTextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = BombAccent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DifficultyCard(
    difficulty: DefuseDifficulty,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) BombAccent.copy(alpha = 0.14f) else BombCard)
            .border(
                width = 1.5.dp,
                color = if (selected) BombAccent.copy(alpha = 0.9f) else BombBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = difficulty.label,
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = BombText
            )
            Text(
                text = difficulty.description,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = BombTextDim
            )
        }
        Text(
            text = "${difficulty.timeSeconds} 秒",
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            color = if (selected) BombAccent else BombTextDim
        )
    }
}

// ===== 拆弹页 =====

@Composable
fun DefusePlayingScreen(
    state: DefuseUiState,
    onBack: () -> Unit,
    onCutWire: (Int) -> Unit,
    onKeypadPress: (String) -> Unit,
    onButtonAction: (ButtonAction) -> Unit,
    onSendText: (String) -> Unit,
    onReportPanel: () -> Unit,
    onToggleHelp: () -> Unit
) {
    val session = state.session ?: return
    val game = state.game ?: return
    val module = session.currentModule ?: return
    val elapsed = (state.nowMs - state.startedAtMs).coerceAtLeast(0L)
    val remainingMs = (game.totalTimeMs - elapsed).coerceAtLeast(0L)
    val myProgress = (elapsed.toFloat() / game.totalTimeMs.toFloat()).coerceIn(0f, 1f)
    val opponentProgress = ((elapsed + state.opponentBoostMs).toFloat() / game.opponentTimeMs.toFloat())
        .coerceIn(0f, 1f)
    val opponentModules = ((opponentProgress * game.modules.size).toInt()).coerceIn(0, game.modules.size)
    val manualText = DefuseLlmPrompt.manualSection(module)
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state.lastEvent?.atMs) {
        when (state.lastEvent?.kind) {
            DefuseModuleEventKind.STRIKE,
            DefuseModuleEventKind.EXPLODE -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            DefuseModuleEventKind.DEFUSED -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            null -> Unit
        }
    }

    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BombBg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding()
            .padding(horizontal = 16.dp)
    ) {
        DefuseTopBar(
            title = "模块 ${session.moduleIndex + 1}/${game.modules.size}",
            onBack = onBack
        )
        StatusRow(session = session, remainingMs = remainingMs)
        Spacer(Modifier.size(8.dp))
        RaceBars(
            myProgress = myProgress,
            opponentProgress = opponentProgress,
            opponentModulesDone = opponentModules,
            totalModules = game.modules.size
        )
        Spacer(Modifier.size(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            DefuseModulePanel(
                module = module,
                onCutWire = onCutWire,
                onKeypadPress = onKeypadPress,
                onButtonAction = onButtonAction
            )
            EventBanner(
                event = state.lastEvent,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
            )
        }
        Spacer(Modifier.size(10.dp))
        DefuseChatPanel(
            messages = state.messages,
            thinking = state.thinking,
            partnerName = state.partnerName,
            partnerAvatar = state.partnerAvatarUri,
            showManual = state.showManual,
            manualText = manualText,
            helpUsed = state.helpUsed,
            onSend = onSendText,
            onReport = onReportPanel,
            onToggleHelp = onToggleHelp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

@Composable
private fun StatusRow(session: DefuseSession, remainingMs: Long) {
    val urgent = remainingMs < 30_000
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(session.game.difficulty.lives) { index ->
                val alive = index < session.lives
                Text(
                    text = if (alive) "♥" else "♡",
                    fontSize = 18.sp,
                    color = if (alive) BombBad else BombBorder
                )
                if (index < session.game.difficulty.lives - 1) Spacer(Modifier.size(2.dp))
            }
            if (session.combo >= 2) {
                Spacer(Modifier.size(8.dp))
                Surface(
                    color = BombAccent.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, BombAccent.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = "连击 ×${session.combo}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = BombAccent
                    )
                }
            }
        }
        Text(
            text = formatTime(remainingMs),
            style = androidx.compose.material3.MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = if (urgent) BombBad else BombText
        )
        Text(
            text = session.currentModule?.type?.label ?: "",
            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = BombTextDim
        )
    }
}

@Composable
private fun RaceBars(
    myProgress: Float,
    opponentProgress: Float,
    opponentModulesDone: Int,
    totalModules: Int
) {
    Column {
        RaceBar(
            label = "我方拆弹组",
            progress = myProgress,
            color = BombGood
        )
        Spacer(Modifier.size(6.dp))
        RaceBar(
            label = "对手队伍",
            progress = opponentProgress,
            color = BombBad,
            trailing = "已拆 $opponentModulesDone/$totalModules 个模块"
        )
    }
}

@Composable
private fun RaceBar(
    label: String,
    progress: Float,
    color: Color,
    trailing: String = ""
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = BombTextDim
            )
            Text(
                text = if (trailing.isBlank()) "进度 ${(progress * 100).toInt()}%" else trailing,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = BombTextDim
            )
        }
        Spacer(Modifier.size(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = BombCardHi
        )
    }
}

@Composable
private fun DefuseModulePanel(
    module: DefuseModule,
    onCutWire: (Int) -> Unit,
    onKeypadPress: (String) -> Unit,
    onButtonAction: (ButtonAction) -> Unit
) {
    val pulse by rememberInfiniteTransition(label = "bomb_pulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1100, easing = Motion.EasingStandard),
            RepeatMode.Reverse
        ),
        label = "bomb_pulse_alpha"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(20.dp))
            .background(BombPanelInner)
            .border(1.dp, BombAccent.copy(alpha = pulse), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(BombBad.copy(alpha = pulse))
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = "危险装置 · 已激活",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = BombTextDim
                )
            }
            Spacer(Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = module.type.label,
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = BombAccent
                )
                Spacer(Modifier.size(10.dp))
                Text(
                    text = when (module) {
                        is DefuseModule.Wires -> "5 根线 · 选一根剪"
                        is DefuseModule.Keypad -> "按顺序点符号"
                        is DefuseModule.Button -> "按搭档指令操作"
                    },
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = BombTextDim
                )
            }
            Spacer(Modifier.size(16.dp))
            when (module) {
                is DefuseModule.Wires -> WiresView(
                    module = module,
                    onCut = onCutWire
                )
                is DefuseModule.Keypad -> KeypadView(
                    module = module,
                    onPress = onKeypadPress
                )
                is DefuseModule.Button -> ButtonModuleView(
                    module = module,
                    onAction = onButtonAction
                )
            }
        }
    }
}

@Composable
private fun WiresView(
    module: DefuseModule.Wires,
    onCut: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        module.wires.forEachIndexed { index, color ->
            val wireIndex = index + 1
            WireRow(
                index = wireIndex,
                color = color,
                damaged = wireIndex in module.damaged,
                onClick = { onCut(wireIndex) }
            )
        }
    }
}

@Composable
private fun WireRow(
    index: Int,
    color: WireColor,
    damaged: Boolean,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(Motion.DurationShort, easing = Motion.EasingStandard),
        label = "wire_scale"
    )
    val base = wireColor(color)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = !damaged,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$index",
            modifier = Modifier.width(24.dp),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = BombTextDim
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(26.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            base.copy(alpha = if (damaged) 0.18f else 0.95f),
                            base.copy(alpha = if (damaged) 0.08f else 0.35f),
                            base.copy(alpha = if (damaged) 0.18f else 0.9f)
                        )
                    )
                )
                .border(1.dp, base.copy(alpha = if (damaged) 0.15f else 0.45f), RoundedCornerShape(13.dp))
        ) {
            if (damaged) {
                Canvas(Modifier.fillMaxSize()) {
                    drawLine(
                        color = BombBad,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, 0f),
                        strokeWidth = 4.dp.toPx()
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadView(
    module: DefuseModule.Keypad,
    onPress: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        module.symbols.chunked(2).forEach { rowSymbols ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowSymbols.forEach { symbol ->
                    KeypadTile(
                        symbol = symbol,
                        enteredIndex = module.entered.indexOf(symbol).takeIf { it >= 0 },
                        onPress = onPress
                    )
                }
            }
        }
    }
}

@Composable
private fun KeypadTile(
    symbol: String,
    enteredIndex: Int?,
    onPress: (String) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = tween(Motion.DurationShort, easing = Motion.EasingStandard),
        label = "keypad_scale"
    )
    Box(
        modifier = Modifier
            .size(84.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (enteredIndex != null) BombAccent.copy(alpha = 0.22f)
                else BombCardHi
            )
            .border(
                width = 1.dp,
                color = if (enteredIndex != null) BombAccent else BombBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enteredIndex == null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPress(symbol)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            fontSize = 38.sp,
            color = if (enteredIndex != null) BombAccent else BombText
        )
        if (enteredIndex != null) {
            Text(
                text = "${enteredIndex + 1}",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = BombAccent
            )
        }
    }
}

@Composable
private fun ButtonModuleView(
    module: DefuseModule.Button,
    onAction: (ButtonAction) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var pressing by remember { mutableStateOf(false) }
    var pressStart by remember { mutableStateOf(0L) }
    val holdProgress by animateFloatAsState(
        targetValue = if (pressing) 1f else 0f,
        animationSpec = tween(1000, easing = Motion.EasingStandard),
        label = "hold_progress"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "指示灯",
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = BombTextDim
            )
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (module.lightOn) Color(0xFFFFF176) else Color(0xFF4A5468))
            )
            Text(
                text = if (module.lightOn) "亮" else "灭",
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = BombTextDim
            )
        }
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { holdProgress },
                modifier = Modifier.size(104.dp),
                color = BombAccent,
                trackColor = BombBorder.copy(alpha = 0.4f),
                strokeWidth = 4.dp
            )
            Box(
                modifier = Modifier
                    .size(width = 184.dp, height = 68.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(buttonColor(module.color))
                    .border(2.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                pressStart = System.currentTimeMillis()
                                pressing = true
                                try {
                                    awaitRelease()
                                } finally {
                                    val held = System.currentTimeMillis() - pressStart
                                    pressing = false
                                    if (held > 0L) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onAction(if (held >= HOLD_THRESHOLD_MS) ButtonAction.HOLD else ButtonAction.CLICK)
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = module.label.text,
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = buttonTextColor(module.color)
                )
            }
        }
        Text(
            text = "点一下 = 点击 ｜ 按住 1 秒 = 长按",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = BombTextDim
        )
    }
}

@Composable
private fun EventBanner(event: DefuseModuleEvent?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = event != null,
        modifier = modifier,
        enter = fadeIn(tween(Motion.DurationShort)) + slideInVertically(tween(Motion.DurationShort)),
        exit = fadeOut(tween(Motion.DurationShort)) + slideOutVertically(tween(Motion.DurationShort))
    ) {
        val kind = event?.kind ?: return@AnimatedVisibility
        val (text, color) = when (kind) {
            DefuseModuleEventKind.STRIKE -> "操作失误！生命 -1" to BombBad
            DefuseModuleEventKind.DEFUSED -> "模块拆除！" to BombGood
            DefuseModuleEventKind.EXPLODE -> "轰——" to BombBad
        }
        Surface(
            color = color.copy(alpha = 0.16f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, color.copy(alpha = 0.6f))
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

// ===== 聊天面板 =====

@Composable
private fun DefuseChatPanel(
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
private fun DefuseChatBubble(
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
private fun DefuseThinkingBubble(partnerName: String, partnerAvatar: String?) {
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
private fun ManualCard(manualText: String, helpUsed: Boolean) {
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

@Composable
fun DefuseResultScreen(
    state: DefuseUiState,
    onRematch: () -> Unit,
    onBack: () -> Unit
) {
    val kind = state.resultKind ?: return
    val session = state.session ?: return
    val game = state.game ?: return
    val elapsed = state.finishedAtMs ?: 0L
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BombBg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = kind.icon(),
            contentDescription = null,
            tint = kind.tint(),
            modifier = Modifier.size(84.dp)
        )
        Spacer(Modifier.size(16.dp))
        Text(
            text = kind.title,
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = BombText
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = kind.subtitle,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = BombTextDim
        )
        Spacer(Modifier.size(24.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = BombCard,
            border = BorderStroke(1.dp, BombBorder)
        ) {
            Column(Modifier.padding(18.dp)) {
                ResultStatRow("用时", formatTime(elapsed))
                ResultStatRow("拆除模块", "${session.clearedCount}/${game.modules.size}")
                ResultStatRow("失误次数", "${session.strikes}")
                ResultStatRow("剩余生命", "${session.lives}/${game.difficulty.lives}")
                ResultStatRow("最高连击", "×${session.maxCombo}")
                ResultStatRow("得分", "${state.score}", highlight = true)
            }
        }
        Spacer(Modifier.size(24.dp))
        DefusePrimaryButton(
            text = "再来一局",
            onClick = onRematch,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.size(10.dp))
        DefuseSecondaryButton(
            text = "返回设置",
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.size(16.dp))
    }
}

@Composable
private fun ResultStatRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = BombTextDim
        )
        Text(
            text = value,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (highlight) BombAccent else BombText
        )
    }
}

private const val HOLD_THRESHOLD_MS = 1000L
