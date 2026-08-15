package com.quiddity.app.ui.miniapps.defuse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quiddity.app.domain.defuse.ButtonAction
import com.quiddity.app.domain.defuse.DefuseModule
import com.quiddity.app.domain.defuse.WireColor
import com.quiddity.app.ui.theme.Motion

@Composable
internal fun DefuseModulePanel(
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
internal fun WiresView(
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
internal fun WireRow(
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
internal fun KeypadView(
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
internal fun KeypadTile(
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
internal fun ButtonModuleView(
    module: DefuseModule.Button,
    onAction: (ButtonAction) -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var pressing by remember { mutableStateOf(false) }
    var pressStart by remember { mutableLongStateOf(0L) }
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
internal fun EventBanner(event: DefuseModuleEvent?, modifier: Modifier = Modifier) {
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
