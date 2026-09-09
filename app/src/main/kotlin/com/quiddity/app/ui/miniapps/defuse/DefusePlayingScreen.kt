package com.quiddity.app.ui.miniapps.defuse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quiddity.app.domain.defuse.ButtonAction
import com.quiddity.app.domain.defuse.DefuseLlmPrompt
import com.quiddity.app.domain.defuse.DefuseSession

@Composable
internal fun DefusePlayingScreen(
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
internal fun StatusRow(session: DefuseSession, remainingMs: Long) {
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
internal fun RaceBars(
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
internal fun RaceBar(
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
