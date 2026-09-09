package com.quiddity.app.ui.miniapps.defuse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quiddity.app.domain.defuse.DefuseDifficulty
import com.quiddity.app.ui.components.AiAvatar
import com.quiddity.app.ui.miniapps.board.BoardInvitee

@Composable
internal fun DefuseSetupScreen(
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
internal fun DefuseHeroHeader() {
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
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = BombText,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
internal fun PartnerRow(
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
internal fun DifficultyCard(
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
