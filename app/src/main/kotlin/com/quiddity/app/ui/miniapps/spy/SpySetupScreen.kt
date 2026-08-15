package com.quiddity.app.ui.miniapps.spy

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.quiddity.app.data.model.Character
import com.quiddity.app.domain.spy.SpyGameMode
import com.quiddity.app.domain.spy.SpyWordBank

@Composable
internal fun SpySetupScreen(
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
    var showCharacterPicker by remember { mutableStateOf(false) }

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
            SpySetupSeatRow(
                userAvatarUri = userAvatarUri,
                llmCharacters = setup.llmCharacters,
                onAddClick = { showCharacterPicker = true }
            )
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
    if (showCharacterPicker) {
        SpyCharacterPickerDialog(
            characters = characters,
            selectedIds = setup.llmCharacters.mapTo(mutableSetOf()) { it.id },
            onPick = { character ->
                showCharacterPicker = false
                onToggleLlm(character)
            },
            onDismiss = { showCharacterPicker = false }
        )
    }
}

@Composable
internal fun SpyCharacterPickerDialog(
    characters: List<Character>,
    selectedIds: Set<String>,
    onPick: (Character) -> Unit,
    onDismiss: () -> Unit
) {
    val available = characters.filter { it.id !in selectedIds }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Text(
                    text = "选择 LLM 角色",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = if (available.isEmpty()) {
                        if (characters.isEmpty()) "角色库为空，请先到主应用创建角色"
                        else "可选角色已全部加入（$MAX_LLM/$MAX_LLM）"
                    } else {
                        "点击角色卡即可加入牌局（已选 ${selectedIds.size}/$MAX_LLM）"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(14.dp))
                if (available.isEmpty()) {
                    EmptyCharactersCard()
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(available, key = { it.id }) { character ->
                            SpySelectCard(
                                title = character.persona.name.ifBlank { "未命名角色" },
                                subtitle = character.persona.character.ifBlank { "点击加入牌局" },
                                avatarUri = character.aiAvatarUri ?: character.persona.aiAvatarUri,
                                selected = false,
                                enabled = true,
                                onClick = { onPick(character) }
                            )
                        }
                    }
                }
                Spacer(Modifier.size(14.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("取消")
                }
            }
        }
    }
}

@Composable
internal fun SpyHeroHeader() {
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
internal fun SpyCategoryRow(
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
internal fun SpyModeRow(
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
internal fun SelectionCountRow(selected: Int) {
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
internal fun EmptyCharactersCard() {
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
internal fun CheckingOverlay() {
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
