package com.quiddity.app.ui.miniapps.defuse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun DefuseResultScreen(
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
internal fun ResultStatRow(label: String, value: String, highlight: Boolean = false) {
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

internal const val HOLD_THRESHOLD_MS = 1000L
