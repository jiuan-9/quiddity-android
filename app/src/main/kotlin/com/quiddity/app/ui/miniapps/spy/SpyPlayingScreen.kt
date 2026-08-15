package com.quiddity.app.ui.miniapps.spy

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quiddity.app.domain.spy.SpyGameState
import com.quiddity.app.domain.spy.SpyPhase
import com.quiddity.app.domain.spy.SpyPlayer
import com.quiddity.app.domain.spy.SpyPlayerKind
import com.quiddity.app.domain.spy.SpySpeech
import com.quiddity.app.ui.components.AiAvatar
import com.quiddity.app.ui.theme.AiBubbleShape
import com.quiddity.app.ui.theme.Motion
import com.quiddity.app.ui.theme.UserBubbleShape

@Composable
internal fun SpyPlayingScreen(
    game: SpyGameState,
    userIndex: Int,
    lastVoteEvent: SpyVoteEvent?,
    onBack: () -> Unit,
    onSpeakSubmit: (Int, String) -> Unit,
    onVoteSubmit: (Int, Int) -> Unit
) {
    BackHandler(onBack = onBack)
    val user = game.player(userIndex)
    var showWord by remember { mutableStateOf(false) }
    val bannerText = when (game.phase) {
        SpyPhase.SPEAK -> "第 ${game.round} 轮 · 发言开始"
        SpyPhase.VOTE -> "第 ${game.round} 轮 · 投票时间"
        SpyPhase.PK -> "第 ${game.round} 轮 · 平票对决"
        else -> null
    }
    val currentIndex = when (game.phase) {
        SpyPhase.SPEAK -> game.nextSpeaker()?.index
        SpyPhase.VOTE -> game.nextVoter()?.index
        SpyPhase.PK -> game.nextPkSpeaker()?.index ?: game.nextPkVoter()?.index
        else -> null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            SpyTopBar(
                title = "第 ${game.round} 轮",
                subtitle = when (game.phase) {
                    SpyPhase.SPEAK -> "发言阶段"
                    SpyPhase.VOTE -> "投票阶段"
                    SpyPhase.PK -> "平票对决"
                    else -> ""
                },
                onBack = onBack,
                trailing = {
                    IconButton(
                        onClick = { showWord = true },
                        enabled = user != null && user.role != null
                    ) {
                        Icon(Icons.Rounded.Visibility, contentDescription = "查看我的词")
                    }
                }
            )
            PlayerStrip(players = game.players, currentIndex = currentIndex)
            when (game.phase) {
                SpyPhase.SPEAK -> SpeakFlow(game, userIndex, onSpeakSubmit)
                SpyPhase.VOTE -> VoteFlow(game, userIndex, onVoteSubmit)
                SpyPhase.PK -> {
                    if (game.pkSpeakComplete) {
                        VoteFlow(game, userIndex, onVoteSubmit, pk = true)
                    } else {
                        SpeakFlow(game, userIndex, onSpeakSubmit, pk = true)
                    }
                }
                else -> Unit
            }
        }
        if (bannerText != null) {
            SpyRoundBanner(
                key = "${game.round}_${game.phase}",
                text = bannerText,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp)
            )
        }
        if (lastVoteEvent != null) {
            SpyEliminationOverlay(
                event = lastVoteEvent,
                game = game,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }

    if (showWord && user != null && user.role != null) {
        SpyWordReminderDialog(
            role = user.role,
            word = user.word.orEmpty(),
            onDismiss = { showWord = false }
        )
    }
}

@Composable
internal fun PlayerStrip(
    players: List<SpyPlayer>,
    currentIndex: Int? = null
) {
    val reduced = isSystemAnimationsDisabled()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        players.forEach { p ->
            val isCurrent = p.alive && p.index == currentIndex
            val ringColor = when (p.kind) {
                SpyPlayerKind.USER -> MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                SpyPlayerKind.LLM -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.65f)
                SpyPlayerKind.SCRIPT -> MaterialTheme.colorScheme.outlineVariant
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isCurrent && !reduced) {
                        val transition = rememberInfiniteTransition(label = "speaker_pulse")
                        val pulse by transition.animateFloat(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                            label = "pulse"
                        )
                        Box(
                            modifier = Modifier
                                .size(38.dp + 12.dp * pulse)
                                .border(
                                    2.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.75f * (1f - pulse * 0.45f)),
                                    CircleShape
                                )
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .border(
                                if (isCurrent) 2.5.dp else 2.dp,
                                if (isCurrent) MaterialTheme.colorScheme.primary else ringColor,
                                CircleShape
                            )
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        AiAvatar(
                            avatarUri = p.avatarUri,
                            name = p.name,
                            size = 40.dp,
                            modifier = Modifier.graphicsLayer { alpha = if (p.alive) 1f else 0.35f }
                        )
                        if (!p.alive) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "✕",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.size(3.dp))
                Text(
                    text = p.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                    else if (p.alive) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun PhaseProgress(
    label: String,
    current: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = if (total == 0) 0f else current / total.toFloat(),
        animationSpec = tween(Motion.DurationMedium, easing = Motion.EasingStandard),
        label = "phase_progress"
    )
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$label $current/$total",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .width(46.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
internal fun SpeakFlow(
    game: SpyGameState,
    userIndex: Int,
    onSpeakSubmit: (Int, String) -> Unit,
    pk: Boolean = false
) {
    val speaker = if (pk) game.nextPkSpeaker() else game.nextSpeaker()
    val listState = rememberLazyListState()
    val displaySpeeches = if (pk) game.roundSpeeches + game.pkSpeeches else game.roundSpeeches
    val speechCount = displaySpeeches.size
    LaunchedEffect(speechCount) {
        if (speechCount > 0) listState.scrollToItem(speechCount - 1)
    }
    val haptics = spyHaptics()

    Column(Modifier.fillMaxSize()) {
        PhaseProgress(
            label = if (pk) "PK 发言" else "发言",
            current = if (pk) game.pkSpeeches.size else game.roundSpeeches.size,
            total = if (pk) game.pkCandidates.size else game.alivePlayers.size,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(displaySpeeches, key = { if (pk) "pk_${it.round}_${it.playerIndex}" else "${it.round}_${it.playerIndex}" }) { speech ->
                val player = game.player(speech.playerIndex)
                SpeechBubble(
                    speech = speech,
                    isUser = speech.playerIndex == userIndex,
                    playerName = player?.name ?: "玩家",
                    avatarUri = player?.avatarUri,
                    kind = player?.kind ?: SpyPlayerKind.SCRIPT
                )
            }
            if (speaker != null && speaker.index != userIndex) {
                item(key = "thinking_$speechCount") {
                    ThinkingRow(player = speaker)
                }
            }
        }

        if (speaker?.index == userIndex) {
            var text by remember(game.round) { mutableStateOf("") }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text(if (pk) "为你的词辩护一句…" else "用一句话描述你的词…") },
                trailingIcon = {
                    IconButton(
                        enabled = text.trim().isNotBlank(),
                        onClick = {
                            onSpeakSubmit(userIndex, text)
                            text = ""
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (text.trim().isNotBlank()) {
                        onSpeakSubmit(userIndex, text)
                        text = ""
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }),
                maxLines = 3,
                shape = RoundedCornerShape(18.dp)
            )
            Spacer(Modifier.size(8.dp))
        } else {
            Spacer(Modifier.size(46.dp))
        }
    }
}

@Composable
internal fun VoteFlow(
    game: SpyGameState,
    userIndex: Int,
    onVoteSubmit: (Int, Int) -> Unit,
    pk: Boolean = false
) {
    val voter = if (pk) game.nextPkVoter() else game.nextVoter()
    val isUserTurn = voter?.index == userIndex
    val votes = if (pk) game.pkVotes else game.roundVotes
    val displaySpeeches = if (pk) game.roundSpeeches + game.pkSpeeches else game.roundSpeeches
    val candidates = if (pk) {
        game.pkCandidates.mapNotNull { game.player(it) }
    } else {
        game.alivePlayers.filter { it.index != userIndex }
    }
    val totalVoters = if (pk) {
        game.alivePlayers.count { it.index !in game.pkCandidates }
    } else {
        game.alivePlayers.size
    }
    val haptics = spyHaptics()

    Column(Modifier.fillMaxSize()) {
        PhaseProgress(
            label = if (pk) "PK 投票" else "投票",
            current = votes.size,
            total = totalVoters,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(displaySpeeches, key = { if (pk) "pk_${it.round}_${it.playerIndex}" else "${it.round}_${it.playerIndex}" }) { speech ->
                val player = game.player(speech.playerIndex)
                SpeechBubble(
                    speech = speech,
                    isUser = speech.playerIndex == userIndex,
                    playerName = player?.name ?: "玩家",
                    avatarUri = player?.avatarUri,
                    kind = player?.kind ?: SpyPlayerKind.SCRIPT
                )
            }
            if (voter != null && voter.index != userIndex) {
                item(key = "vote_thinking_${votes.size}") {
                    ThinkingRow(player = voter)
                }
            }
        }

        if (isUserTurn) {
            Text(
                text = if (pk) "你支持谁留下？" else "你怀疑谁？",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )
        } else if (voter != null) {
            Text(
                text = "AI 玩家正在投票…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )
        }
        LazyColumn(
            modifier = Modifier
                .weight(if (isUserTurn) 0.62f else 0.5f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(candidates, key = { it.index }) { p ->
                VoteCandidateCard(
                    player = p,
                    game = game,
                    enabled = isUserTurn,
                    pk = pk,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onVoteSubmit(userIndex, p.index)
                    }
                )
            }
        }
    }
}

@Composable
internal fun VoteCandidateCard(
    player: SpyPlayer,
    game: SpyGameState,
    enabled: Boolean,
    pk: Boolean = false,
    onClick: () -> Unit
) {
    val count = (if (pk) game.pkVotes else game.roundVotes).count { it.toIndex == player.index }
    SpyPressable(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (enabled) MaterialTheme.colorScheme.surfaceContainerLow
            else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f),
            border = BorderStroke(
                1.dp,
                if (count > 0) MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AiAvatar(avatarUri = player.avatarUri, name = player.name, size = 40.dp)
                Text(
                    text = player.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                AnimatedContent(
                    targetState = count,
                    transitionSpec = {
                        (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) togetherWith scaleOut(tween(Motion.DurationShort)))
                    },
                    label = "vote_count"
                ) { c ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (c > 0) MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = if (c > 0) "$c 票" else if (enabled) "投 TA" else "等待",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (c > 0) MaterialTheme.colorScheme.error
                            else if (enabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SpeechBubble(
    speech: SpySpeech,
    isUser: Boolean,
    playerName: String,
    avatarUri: String?,
    kind: SpyPlayerKind
) {
    val reduced = isSystemAnimationsDisabled()
    val appear = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(speech) {
        if (!reduced) {
            appear.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
            )
        }
    }
    val ringColor = when (kind) {
        SpyPlayerKind.USER -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        SpyPlayerKind.LLM -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)
        SpyPlayerKind.SCRIPT -> MaterialTheme.colorScheme.outlineVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.value
                translationY = (1f - appear.value) * 14f
            },
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .border(1.5.dp, ringColor, CircleShape)
                .clip(CircleShape)
        ) {
            AiAvatar(avatarUri = avatarUri, name = playerName, size = 28.dp)
        }
        Spacer(Modifier.size(8.dp))
        Surface(
            shape = if (isUser) UserBubbleShape else AiBubbleShape,
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = playerName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = speech.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
internal fun ThinkingRow(player: SpyPlayer) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .border(
                    1.5.dp,
                    if (player.kind == SpyPlayerKind.USER) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f),
                    CircleShape
                )
                .clip(CircleShape)
        ) {
            AiAvatar(avatarUri = player.avatarUri, name = player.name, size = 28.dp)
        }
        Spacer(Modifier.size(8.dp))
        Surface(
            shape = AiBubbleShape,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = player.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(4.dp))
                SpyThinkingDots(text = "正在输入")
            }
        }
    }
}

// ===== 结算页 =====
