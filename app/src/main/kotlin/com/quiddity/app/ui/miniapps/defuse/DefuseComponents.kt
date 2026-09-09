package com.quiddity.app.ui.miniapps.defuse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quiddity.app.domain.defuse.ButtonColor
import com.quiddity.app.domain.defuse.WireColor

internal val BombBg = Color(0xFF0B0F19)
internal val BombCard = Color(0xFF151C2B)
internal val BombCardHi = Color(0xFF1C2536)
internal val BombPanelInner = Color(0xFF10141F)
internal val BombBorder = Color(0xFF2B3448)
internal val BombText = Color(0xFFF1F4FA)
internal val BombTextDim = Color(0xFFA9B4C8)
internal val BombGood = Color(0xFF69F0AE)
internal val BombBad = Color(0xFFFF5252)
internal val BombAccent = Color(0xFFFFB74D)
internal val BombAccentText = Color(0xFF201505)

internal fun wireColor(color: WireColor): Color = when (color) {
    WireColor.RED -> Color(0xFFFF5252)
    WireColor.BLUE -> Color(0xFF4FC3F7)
    WireColor.YELLOW -> Color(0xFFFFD54F)
    WireColor.WHITE -> Color(0xFFF5F5F5)
    WireColor.GREEN -> Color(0xFF69F0AE)
}

internal fun buttonColor(color: ButtonColor): Color = when (color) {
    ButtonColor.RED -> Color(0xFFD32F2F)
    ButtonColor.BLUE -> Color(0xFF1976D2)
    ButtonColor.YELLOW -> Color(0xFFF9A825)
    ButtonColor.WHITE -> Color(0xFFE0E0E0)
}

internal fun buttonTextColor(color: ButtonColor): Color =
    if (color == ButtonColor.YELLOW || color == ButtonColor.WHITE) Color(0xFF26261F) else Color.White

internal fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(totalSec / 60, totalSec % 60)
}

internal fun DefuseResultKind.icon(): ImageVector = when (this) {
    DefuseResultKind.WIN -> Icons.Rounded.EmojiEvents
    DefuseResultKind.SAVED_LATE -> Icons.Rounded.CheckCircle
    DefuseResultKind.LOST_RACE -> Icons.Rounded.Timer
    DefuseResultKind.EXPLODED -> Icons.Rounded.Whatshot
}

internal fun DefuseResultKind.tint(): Color = when (this) {
    DefuseResultKind.WIN -> Color(0xFFFFC94D)
    DefuseResultKind.SAVED_LATE -> Color(0xFF4FC3F7)
    DefuseResultKind.LOST_RACE -> Color(0xFFB39DDB)
    DefuseResultKind.EXPLODED -> Color(0xFFFF5252)
}

// ===== 通用组件 =====

@Composable
internal fun DefusePrimaryButton(
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
internal fun DefuseSecondaryButton(
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
internal fun DefuseChip(
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
internal fun DefuseTopBar(
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
