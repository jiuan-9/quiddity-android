package com.quiddity.app.ui.miniapps.spy

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quiddity.app.data.model.Character
import com.quiddity.app.domain.spy.SpyGameState
import com.quiddity.app.domain.spy.SpyPhase
import com.quiddity.app.domain.spy.SpyGameMode
import com.quiddity.app.domain.spy.SpyPlayer
import com.quiddity.app.domain.spy.SpyPlayerKind
import com.quiddity.app.domain.spy.SpyRole
import com.quiddity.app.domain.spy.SpySpeech
import com.quiddity.app.domain.spy.SpyWordBank
import com.quiddity.app.ui.components.AiAvatar
import com.quiddity.app.ui.components.QuiddityPrimaryButton
import com.quiddity.app.ui.components.QuidditySecondaryButton
import com.quiddity.app.ui.theme.AiBubbleShape
import com.quiddity.app.ui.theme.Motion
import com.quiddity.app.ui.theme.UserBubbleShape

// ===== 配置页 =====

@Composable
fun SpySetupScreen(
    characters: List<Character>,
    setup: SpySetupState,
    checking: Boolean,
    userAvatarUri: String?,
    onBack: () -> Unit,
    onToggleLlm: (Character) -> Unit,
    onRemoveLlm: (String) -> Unit,
    onCategorySelect: (String?) -> Unit,
    onModeSelect: (SpyGameMode) -> Unit,
    onStart: () -> Unit
) {
    BackHandler(onBack = onBack)
    var showRules by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .imePadding()
            .padding(horizontal = 20.dp)
    ) {
        SpyTopBar(
            title = "谁是卧底",
            onBack = onBack,
            trailing = {
                IconButton(onClick = { showRules = true }) {
                    Icon(Icons.Rounded.HelpOutline, contentDescription = "游戏规则")
                }
            }
        )
        SpyEntrance(0) { SpyHeroHeader() }
        Spacer(Modifier.size(14.dp))
        SpyEntrance(1) {
            SpySetupSeatRow(userAvatarUri = userAvatarUri, llmCharacters = setup.llmCharacters)
        }
        Spacer(Modifier.size(14.dp))
        SpyEntrance(2) {
            SpyCategoryRow(selected = setup.category, onSelect = onCategorySelect)
        }
        Spacer(Modifier.size(10.dp))
        SpyEntrance(3) {
            SpyModeRow(selected = setup.mode, onSelect = onModeSelect)
        }
        Spacer(Modifier.size(10.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SpySectionTitle("选择 3 个 LLM 角色参与")
                Spacer(Modifier.size(6.dp))
                SelectionCountRow(selected = setup.llmCharacters.size)
            }
            if (characters.isEmpty()) {
                item {
                    SpyEntrance(3) {
                        EmptyCharactersCard()
                    }
                }
            } else {
                itemsIndexed(characters, key = { _, c -> c.id }) { index, character ->
                    val name = character.persona.name.ifBlank { "未命名角色" }
                    val selected = setup.llmCharacters.any { it.id == character.id }
                    SpyEntrance(index + 3) {
                        SpySelectCard(
                            title = name,
                            subtitle = character.persona.character.ifBlank { "点击加入牌局" },
                            avatarUri = character.aiAvatarUri ?: character.persona.aiAvatarUri,
                            selected = selected,
                            enabled = !checking,
                            onClick = {
                                if (selected) onRemoveLlm(character.id) else onToggleLlm(character)
                            }
                        )
                    }
                }
            }
            item {
                SpyEntrance(9) {
                    Local("开局会随机挑一名角色，用它的 API 生成本局词库；连不上就自动用内置词库。")
                }
            }
            item { Spacer(Modifier.size(12.dp)) }
        }

        SpyEntrance(10) {
            Button(
                onClick = onStart,
                enabled = setup.llmCharacters.size == MAX_LLM && !checking,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Text(
                    text = if (checking) "正在生成词库…" else "开局（共 4 人）",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.size(16.dp))
    }

    if (checking) {
        CheckingOverlay()
    }
    if (showRules) {
        SpyRulesDialog(onDismiss = { showRules = false })
    }
}

@Composable
private fun SpyHeroHeader() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
                    )
                )
            )
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Casino,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column {
                Text(
                    text = "组一桌牌局",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.size(3.dp))
                Text(
                    text = "1 个真人 + 3 个 LLM 角色 · 经典 / 双卧底 / 白板多玩法",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f)
                )
            }
        }
    }
}

@Composable
private fun SpyCategoryRow(
    selected: String?,
    onSelect: (String?) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("全部") }
            )
        }
        items(SpyWordBank.categories) { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(category) },
                label = { Text(category) }
            )
        }
    }
}

@Composable
private fun SpyModeRow(
    selected: SpyGameMode,
    onSelect: (SpyGameMode) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(SpyGameMode.entries) { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                label = { Text(mode.label) }
            )
        }
    }
}

@Composable
private fun SelectionCountRow(selected: Int) {
    val progress by animateFloatAsState(
        targetValue = selected / MAX_LLM.toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "selection_progress"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "已选 $selected/$MAX_LLM",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = if (selected == MAX_LLM) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
private fun EmptyCharactersCard() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Groups,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = "角色库为空：请先去主应用创建 3 个角色再来开局。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CheckingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
                Column {
                    Text("正在生成词库…", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        text = "稍等片刻，马上发牌",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ===== 发牌页 =====

@Composable
fun SpyDealScreen(
    game: SpyGameState,
    userIndex: Int,
    notice: String?,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val user = game.player(userIndex) ?: return
    val role = user.role ?: return
    val word = user.word ?: return
    val haptics = spyHaptics()
    val reduced = isSystemAnimationsDisabled()
    val density = LocalDensity.current.density

    var flipped by remember(game.id) { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(Motion.DurationLong + 140, easing = Motion.EasingEmphasizedDecelerate),
        label = "deal_flip"
    )
    val borderColor by animateColorAsState(
        targetValue = if (flipped) {
            (when (role) {
                SpyRole.SPY -> MaterialTheme.colorScheme.error
                SpyRole.BLANK -> MaterialTheme.colorScheme.tertiary
                SpyRole.CIVILIAN -> MaterialTheme.colorScheme.primary
            })
                .copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        },
        animationSpec = tween(Motion.DurationLong, easing = Motion.EasingStandard),
        label = "deal_border"
    )
    val cardScale = remember { Animatable(if (reduced) 1f else 0.86f) }
    val cardAlpha = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) {
            cardAlpha.animateTo(1f, tween(Motion.DurationShort + 80, easing = Motion.EasingStandard))
            cardScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SpyTopBar(title = "发牌", onBack = onBack)
        SpyEntrance(1) {
            Text(
                text = "悄悄记住你的词，别让同桌看出破绽",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .size(width = 230.dp, height = 310.dp)
                    .graphicsLayer {
                        rotationY = rotation
                        cameraDistance = 14f * density
                        alpha = cardAlpha.value
                        scaleX = cardScale.value
                        scaleY = cardScale.value
                    }
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(enabled = !flipped) {
                        flipped = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 8.dp,
                border = BorderStroke(1.5.dp, borderColor)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (rotation < 90f) {
                        CardBack()
                    } else {
                        Box(Modifier.graphicsLayer { rotationY = 180f }) {
                            CardFront(role = role, word = word)
                        }
                    }
                }
            }
        }
        SpyEntrance(3) {
            PlayerStrip(players = game.players)
        }
        if (!notice.isNullOrBlank()) {
            Spacer(Modifier.size(10.dp))
            SpyEntrance(4) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                ) {
                    Text(
                        text = notice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }
        Spacer(Modifier.size(12.dp))
        Button(
            onClick = onContinue,
            enabled = flipped,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            Text(
                text = if (flipped) "记住了，开始" else "点击卡片查看你的词",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.size(20.dp))
    }
}

@Composable
private fun CardBack() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(42.dp)
                )
            }
            Spacer(Modifier.size(18.dp))
            Text(
                text = "点击翻开你的词",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.size(6.dp))
            Text(
                text = "反扣在桌上，只有你能看",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CardFront(role: SpyRole, word: String) {
    val roleColor = when (role) {
        SpyRole.SPY -> MaterialTheme.colorScheme.error
        SpyRole.BLANK -> MaterialTheme.colorScheme.tertiary
        SpyRole.CIVILIAN -> MaterialTheme.colorScheme.primary
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = roleColor.copy(alpha = 0.12f)
        ) {
            Text(
                text = when (role) {
                    SpyRole.SPY -> "卧底"
                    SpyRole.BLANK -> "白板"
                    SpyRole.CIVILIAN -> "平民"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = roleColor,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
            )
        }
        Spacer(Modifier.weight(0.5f))
        Text(
            text = if (role == SpyRole.BLANK) "（空白牌）" else word,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (role == SpyRole.BLANK) roleColor else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.weight(0.5f))
        Text(
            text = when (role) {
                SpyRole.SPY -> "别让别人看出你与他人不同"
                SpyRole.BLANK -> "你没有词，装成和大家一样，别被识破"
                SpyRole.CIVILIAN -> "描述词让大家都猜到，但别说出口"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ===== 对局页 =====

@Composable
fun SpyPlayingScreen(
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
private fun PlayerStrip(
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
private fun PhaseProgress(
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
private fun SpeakFlow(
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
private fun VoteFlow(
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
private fun VoteCandidateCard(
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
private fun SpeechBubble(
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
private fun ThinkingRow(player: SpyPlayer) {
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

@Composable
fun SpyResultScreen(
    game: SpyGameState,
    onRematch: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val civilianWin = game.winner == SpyRole.CIVILIAN
    val winColor = if (civilianWin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val winText = when {
        civilianWin -> "平民胜利"
        game.mode == SpyGameMode.CLASSIC -> "卧底获胜"
        else -> "卧底方获胜"
    }
    val revealPlayers = buildList {
        game.spyIndices.forEach { idx -> game.player(idx)?.let { add(it to SpyRole.SPY) } }
        game.blankIndex?.let { idx -> game.player(idx)?.let { add(it to SpyRole.BLANK) } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp)
    ) {
        SpyTopBar(title = "结算", onBack = onBack)
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(112.dp),
                contentAlignment = Alignment.Center
            ) {
                SpySparkleBurst(Modifier.fillMaxSize())
                SpyEntrance(0) {
                    Icon(
                        imageVector = if (civilianWin) Icons.Rounded.EmojiEvents else Icons.Rounded.Visibility,
                        contentDescription = null,
                        tint = winColor,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
            SpyEntrance(1) {
                Text(
                    text = winText,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = winColor
                )
            }
            SpyEntrance(2) {
                Text(
                    text = "共 ${game.round} 轮 · ${game.players.size} 名玩家",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.size(14.dp))
            SpyEntrance(3) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (game.mode == SpyGameMode.BLANK) "🎭 卧底 · 白板揭晓" else "🎭 卧底揭晓",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.size(6.dp))
                        revealPlayers.forEach { (player, role) ->
                            Text(
                                text = "${if (role == SpyRole.BLANK) "白板" else "卧底"}：「${player.name}」 · ${player.word.orEmpty().ifBlank { "空白牌" }}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                game.players.forEachIndexed { i, p ->
                    SpyEntrance(4 + i) {
                        ResultPlayerRow(player = p)
                    }
                }
            }
        }
        SpyEntrance(10) {
            QuiddityPrimaryButton(
                text = "再来一局",
                onClick = onRematch,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            )
        }
        Spacer(Modifier.size(8.dp))
        SpyEntrance(11) {
            QuidditySecondaryButton(
                text = "返回",
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            )
        }
        Spacer(Modifier.size(16.dp))
    }
}

@Composable
private fun ResultPlayerRow(player: SpyPlayer) {
    val isSpecial = player.role == SpyRole.SPY || player.role == SpyRole.BLANK
    val roleColor = when (player.role) {
        SpyRole.SPY -> MaterialTheme.colorScheme.error
        SpyRole.BLANK -> MaterialTheme.colorScheme.tertiary
        SpyRole.CIVILIAN, null -> MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AiAvatar(
            avatarUri = player.avatarUri,
            name = player.name,
            size = 36.dp,
            modifier = Modifier.graphicsLayer { alpha = if (player.alive) 1f else 0.4f }
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = player.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (player.alive) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            if (!player.alive) {
                Text(
                    text = "已出局",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = roleColor.copy(alpha = 0.1f)
        ) {
            Text(
                text = when (player.role) {
                    SpyRole.SPY -> "卧底"
                    SpyRole.BLANK -> "白板"
                    SpyRole.CIVILIAN, null -> "平民"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = roleColor,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.size(2.dp))
        Text(
            text = player.word ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ===== 公共组件 =====

@Composable
private fun SpySectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun SpySelectCard(
    title: String,
    subtitle: String,
    avatarUri: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    SpyPressable(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
            else MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .border(
                            1.5.dp,
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            CircleShape
                        )
                        .clip(CircleShape)
                ) {
                    AiAvatar(avatarUri = avatarUri, name = title, size = 40.dp)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AnimatedContent(
                    targetState = selected,
                    transitionSpec = {
                        (scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) togetherWith scaleOut(tween(Motion.DurationShort)))
                    },
                    label = "select_state"
                ) { sel ->
                    if (sel) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "已加入",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Text(
                            text = "加入",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Local(text: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "💡",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SpyTopBar(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

private const val MAX_LLM = 3
