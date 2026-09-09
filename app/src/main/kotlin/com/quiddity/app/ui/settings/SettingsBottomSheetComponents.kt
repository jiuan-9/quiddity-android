package com.quiddity.app.ui.settings

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quiddity.app.ui.components.ExpandableText
import com.quiddity.app.ui.components.QuiddityToggleSwitch
import com.quiddity.app.ui.theme.Motion
import com.quiddity.app.util.QuiddityConstants
import kotlin.math.roundToInt
import kotlinx.coroutines.launch


@Composable
internal fun CenterGrabBar(
    dragOffsetYState: androidx.compose.runtime.MutableFloatState,
    dismissThreshold: Float,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // 顶部抓手：支持向下拖动关闭面板。
        // 增大可拖动区域到 48dp 高，视觉指示器仍保持 4dp，避免用户很难命中。
        // 拖动偏移由外部 MutableFloatState 持有，整个面板 graphicsLayer.translationY 跟随。
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .fillMaxWidth()
                .height(48.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount ->
                            // 直接同步赋值，零协程，draw phase 读取
                            dragOffsetYState.floatValue = (dragOffsetYState.floatValue + dragAmount).coerceAtLeast(0f)
                        },
                        onDragEnd = {
                            if (dragOffsetYState.floatValue > dismissThreshold) {
                                onClose()
                            } else {
                                // 回弹：使用 Animatable 做 0.4s 动画
                                scope.launch {
                                    val anim = androidx.compose.animation.core.Animatable(dragOffsetYState.floatValue)
                                    anim.animateTo(
                                        targetValue = 0f,
                                        animationSpec = tween(
                                            Motion.DurationPageTransition,
                                            easing = Motion.EasingStandard
                                        )
                                    ) { dragOffsetYState.floatValue = this.value }
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                val anim = androidx.compose.animation.core.Animatable(dragOffsetYState.floatValue)
                                anim.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(
                                        Motion.DurationPageTransition,
                                        easing = Motion.EasingStandard
                                    )
                                ) { dragOffsetYState.floatValue = this.value }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = "关闭",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
internal fun SettingsSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    /**
     * 大分区是否默认展开。
     * - false（默认）：收起，点头部展开；
     * - true：进入页面即展开（Agent 设置-支持 等需要用户第一时间看到入口的场景）。
     */
    defaultExpanded: Boolean = false,
    /**
     * 是否使用统一加高的头部（60dp，约常规 2 倍）。
     * Agent 设置面板的所有大项都传 true，保证未展开时每张卡片等高、触控面积更大。
     */
    tallHeader: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    // 大分区默认收起（可指定默认展开），点头部展开/收起
    var expanded by rememberSaveable(title) { mutableStateOf(defaultExpanded) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(com.quiddity.app.ui.components.glassCardColor())
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { expanded = !expanded }
                // 统一加高头部（Agent 设置大项）：固定 60dp 等高；常规面板保持原内边距
                .then(if (tallHeader) Modifier.height(60.dp) else Modifier)
                .then(
                    if (tallHeader) {
                        Modifier.padding(horizontal = 12.dp)
                    } else {
                        Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 8.dp)
                    }
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                content()
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
internal fun FontSizeRow(
    fontScale: Float,
    enabled: Boolean,
    onValueChangeFinished: (Float) -> Unit
) {
    // 本地拖动状态：拖动时即时跟随，松手才落盘
    var sliderValue by remember(fontScale) { mutableFloatStateOf(fontScale) }
    val percent = (sliderValue * 100).roundToInt()
    val sizeLabel = when {
        sliderValue < 0.95f -> "小"
        sliderValue <= 1.05f -> "标准"
        sliderValue <= 1.2f -> "大"
        else -> "特大"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.FormatSize,
                        contentDescription = null,
                        tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.size(14.dp))
                    Text(
                        text = "字体大小",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
                Text(
                    text = "$sizeLabel · $percent%",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
            Spacer(modifier = Modifier.size(8.dp))
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                onValueChangeFinished = {
                    // 离散化到 0.1 一档，落盘
                    val stepped = (sliderValue * 10f).roundToInt() / 10f
                    sliderValue = stepped
                    onValueChangeFinished(stepped)
                },
                valueRange = QuiddityConstants.MIN_FONT_SCALE..QuiddityConstants.MAX_FONT_SCALE,
                steps = 5,
                enabled = enabled
            )
        }
    }
}

@Composable
internal fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    helpText: String? = null,
    onHelpClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    showSavedToast: Boolean = true
) {
    val context = LocalContext.current
    // Box 替代 Surface：行内无 elevation 需求，Box+background+clip 跳过 Surface 的 CompositionLocalProvider 开销
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .graphicsLayer {
                    alpha = if (enabled) 1f else 0.5f
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            if (helpText != null) {
                IconButton(onClick = { onHelpClick?.invoke() }) {
                    Icon(
                        imageVector = Icons.Filled.HelpOutline,
                        contentDescription = "设置说明",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            // 切换时显示"已保存"Toast 反馈
            QuiddityToggleSwitch(
                checked = checked,
                onCheckedChange = {
                    onCheckedChange(it)
                    if (showSavedToast) Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                },
                enabled = enabled
            )
        }
    }
}

@Composable
internal fun ExpandableSettingGroup(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    expanded: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                // 行与子面板之间的连接线：强调两者同属一个设置框
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(com.quiddity.app.ui.components.glassCardBorderColor())
                )
                Spacer(modifier = Modifier.size(10.dp))
                content()
            }
        }
    }
}

@Composable
internal fun ClickableRow(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    onClick: () -> Unit,
    expandableSubtitle: Boolean = false,
    trailingContent: @Composable (() -> Unit)? = null,
    helpText: String? = null,
    onHelpClick: (() -> Unit)? = null
) {
    // Box 替代 Surface：clickable 移到 Box，避免 Surface 包裹的额外开销
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle.isNotEmpty()) {
                    if (expandableSubtitle) {
                        ExpandableText(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            maxCollapsedLines = 1
                        )
                    } else {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            if (helpText != null) {
                IconButton(onClick = { onHelpClick?.invoke() }) {
                    Icon(
                        imageVector = Icons.Filled.HelpOutline,
                        contentDescription = "设置说明",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            // 自定义尾部内容（如加载指示器）；默认显示右箭头
            trailingContent?.invoke() ?: Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
