package com.quiddity.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/*
 * ============================================================================
 * 开发规范 (Development Specifications)
 * ============================================================================
 *
 * 1. 问题修复规范
 *    所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
 *    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
 *
 * 2. 代码注释规范
 *    文件内仅允许保留两类注释：
 *    - 当前规则说明注释（即本注释块）
 *    - 模块划分注释（用于标识代码功能模块边界）
 *    除此之外，禁止出现任何形式的代码注释（包括但不限于单行注释、多行注释、临时调试注释等）。
 *
 * 3. 构建交付要求
 *    在完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK 文件。
 *    APK 文件需满足以下条件：
 *    - 签名有效且符合发布标准
 *    - 包含完整的功能模块
 *    - 经过基础性能测试和兼容性测试
 *    以便在真实设备环境中进行功能验证和性能评估。
 *
 * ============================================================================
 */



/**
 * 可点击的问号帮助提示组件。
 *
 * 点击问号后在图标附近弹出带箭头的小气泡，显示帮助文本；
 * 点击外部或再次点击问号关闭。
 *
 * 边界自适应：
 * - 默认在图标下方弹出（箭头向上）。
 * - 当图标位于屏幕下半部分、下方空间不足时，自动改为图标上方弹出（箭头向下），
 *   避免气泡被屏幕底部或键盘截断。
 *
 * 定位修正：
 * - 外层 [Box] 强制与图标同尺寸，确保 [Popup] 的 BottomCenter/TopCenter
 *   对齐点就是图标中心，避免父布局宽度影响气泡位置。
 *
 * 样式遵循 Material3 主题并强化可访问性：
 * - 背景：surfaceContainerHighest
 * - 文字：onSurface
 * - 边框：outline 40% 透明度
 * - 阴影：4dp
 */
@Composable
fun HelpTooltip(
    text: String,
    modifier: Modifier = Modifier,
    iconSize: Int = 20,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    horizontalOffset: Dp = 0.dp,
    maxWidth: Dp = 260.dp,
    minWidth: Dp = 160.dp
) {
    var expanded by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    // 图标在窗口中的中心 Y 坐标，用于判断气泡应向上还是向下展开
    var iconCenterY by remember { mutableStateOf(0f) }
    val verticalGap = with(density) { 6.dp.toPx() }
    val horizontalPx = with(density) { horizontalOffset.roundToPx() }
    val iconSizePx = with(density) { iconSize.dp.toPx() }

    // 当图标位于屏幕下半部分时，气泡向上展开，避免被底部导航栏/键盘遮挡
    val showAbove = iconCenterY > screenHeightPx * 0.55f
    val verticalOffset = (iconSizePx / 2f + verticalGap).toInt()

    Box(
        modifier = modifier
            // 强制 Box 与图标同尺寸，Popup 的对齐点即为图标中心
            .size(iconSize.dp)
            .onGloballyPositioned { coordinates ->
                iconCenterY = coordinates.positionInWindow().y + coordinates.size.height / 2f
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
            contentDescription = "帮助",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(iconSize.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { expanded = true }
        )
        if (expanded) {
            Popup(
                alignment = if (showAbove) Alignment.TopCenter else Alignment.BottomCenter,
                offset = IntOffset(
                    horizontalPx,
                    if (showAbove) -verticalOffset else verticalOffset
                ),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (showAbove) {
                        Surface(
                            modifier = Modifier
                                .widthIn(min = minWidth, max = maxWidth)
                                .padding(bottom = (-1).dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            tonalElevation = 2.dp,
                            shadowElevation = 4.dp,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                        ) {
                            Text(
                                text = text,
                                style = style,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                        // 向下的箭头，指向问号图标
                        Box(
                            modifier = Modifier
                                .size(12.dp, 6.dp)
                                .clip(TooltipArrowDownShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        )
                    } else {
                        // 向上的箭头，指向问号图标
                        Box(
                            modifier = Modifier
                                .size(12.dp, 6.dp)
                                .clip(TooltipArrowUpShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        )
                        Surface(
                            modifier = Modifier
                                .widthIn(min = minWidth, max = maxWidth)
                                .padding(top = (-1).dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            tonalElevation = 2.dp,
                            shadowElevation = 4.dp,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                        ) {
                            Text(
                                text = text,
                                style = style,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 向上的等腰三角形箭头形状。
 */
private val TooltipArrowUpShape = GenericShape { size: Size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w / 2f, 0f)
    lineTo(w, h)
    lineTo(0f, h)
    close()
}

/**
 * 向下的等腰三角形箭头形状。
 */
private val TooltipArrowDownShape = GenericShape { size: Size, _ ->
    val w = size.width
    val h = size.height
    moveTo(0f, 0f)
    lineTo(w, 0f)
    lineTo(w / 2f, h)
    close()
}
