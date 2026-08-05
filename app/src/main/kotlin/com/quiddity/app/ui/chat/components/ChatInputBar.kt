package com.quiddity.app.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

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
 * 输入框行数容量：固定尺寸，不做动态测量。
 * - GROUP：群聊输入区固定 2 行；
 * - PRIVATE：私聊输入框容量 = 群聊整体高度（2 行输入 + 成员头像栏）换算出的固定行数。
 */
enum class ChatInputBarLineCapacity { GROUP, PRIVATE }

// 当前规则：圆角 24dp 输入框；群聊固定 2 行、私聊按群聊整体高度换算的固定容量；
// 回车发送策略由 enterToSend 决定；壁纸模式下玻璃质感；
// 发送按钮三态（正常/停止/压缩置灰）；群聊成员头像栏并入输入框容器顶部（方案十一）。
@Composable
fun ChatInputBar(
    enterToSend: Boolean,
    isGenerating: Boolean,
    allowSendWhileGenerating: Boolean = false,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    transparent: Boolean = false,
    onTextChange: ((String) -> Unit)? = null,
    isCompressing: Boolean = false,
    // 输入框容器内的顶部内容（群聊成员头像栏，随输入框一起动）
    header: (@Composable () -> Unit)? = null,
    lineCapacity: ChatInputBarLineCapacity = ChatInputBarLineCapacity.PRIVATE
) {
    var text by rememberSaveable { mutableStateOf("") }

    val density = LocalDensity.current
    // 固定尺寸换算：成员栏 56dp（头像 44dp + 上下 padding 12dp）、输入框内部留白 24dp
    val lineHeightPx = with(density) { MaterialTheme.typography.bodyMedium.lineHeight.roundToPx() }
    val fieldInternalPadPx = with(density) { 24.dp.roundToPx() }
    val memberBarPx = with(density) { 56.dp.roundToPx() }
    val groupTwoLineFieldPx = (lineHeightPx * 2 + fieldInternalPadPx)
        .coerceAtLeast(with(density) { 48.dp.roundToPx() })
    val fieldMaxPx = when (lineCapacity) {
        ChatInputBarLineCapacity.GROUP -> groupTwoLineFieldPx
        ChatInputBarLineCapacity.PRIVATE -> groupTwoLineFieldPx + memberBarPx
    }
    val effectiveMaxLines = when (lineCapacity) {
        ChatInputBarLineCapacity.GROUP -> 2
        ChatInputBarLineCapacity.PRIVATE ->
            ((fieldMaxPx - fieldInternalPadPx) / lineHeightPx).coerceAtLeast(1)
    }

    fun trySend() {
        if (!enabled) return
        val v = text.trim()
        if (v.isNotEmpty() && !isCompressing && (allowSendWhileGenerating || !isGenerating)) {
            onSend(v)
            text = ""
            onTextChange?.invoke("")
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars),
        color = if (transparent) Color.Transparent else MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    if (transparent) {
                        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    }
                )
        ) {
            if (header != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 4.dp)
                ) {
                    header()
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = text,
                    onValueChange = {
                        text = it
                        onTextChange?.invoke(it)
                    },
                    enabled = enabled,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp, max = with(density) { fieldMaxPx.toDp() }),
                    placeholder = {
                        Text(
                            text = "输入消息…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    maxLines = effectiveMaxLines,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = if (enterToSend) ImeAction.Send else ImeAction.Default
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = { trySend() }
                    ),
                    textStyle = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.size(4.dp))

                // ===== 发送按钮三态：isGenerating→停止(红)/isCompressing→置灰不可点/否则→正常发送 =====
                val buttonColor = when {
                    isGenerating -> MaterialTheme.colorScheme.error
                    isCompressing -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    text.isNotBlank() -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(buttonColor)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            when {
                                isGenerating -> onStop()
                                isCompressing -> { /* 压缩中：禁用发送，no-op */ }
                                else -> trySend()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isGenerating) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = "停止",
                            tint = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint = when {
                                isCompressing -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                text.isNotBlank() -> MaterialTheme.colorScheme.onPrimary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
