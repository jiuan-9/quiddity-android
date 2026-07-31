package com.quiddity.app.ui.settings.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quiddity.app.ui.components.QuiddityTextField
import com.quiddity.app.util.QuiddityConstants

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
 * Token 设置编辑器。
 *
 * 显示为"从属设置框"：左侧带竖线，表示从属于"Token 设置"父级。
 *
 * - 上方：当前设置值统计卡片（最大回复 / 单条上限 / 上下文记忆）
 * - 下方：输入框 + 保存按钮
 */
@Composable
fun TokenEditorPanel(
    maxTokens: Int,
    singleTokens: Int,
    onMaxChange: (String) -> Unit,
    onSingleChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var localMax by remember(maxTokens) { mutableStateOf(maxTokens.toString()) }
    var localSingle by remember(singleTokens) { mutableStateOf(singleTokens.toString()) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .height(IntrinsicSize.Min)
    ) {
        // 左侧连接竖线：表示从属关系
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        )
        Spacer(modifier = Modifier.size(12.dp))
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ===== 统计卡片行 =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatsMiniCard(
                        title = "最大回复",
                        value = maxTokens.toString(),
                        suffix = "Token",
                        modifier = Modifier.weight(1f)
                    )
                    StatsMiniCard(
                        title = "单条上限",
                        value = singleTokens.toString(),
                        suffix = "Token",
                        modifier = Modifier.weight(1f)
                    )
                }

                // ===== 输入区 =====
                QuiddityTextField(
                    value = localMax,
                    onValueChange = { localMax = it },
                    label = "最大回复 Token",
                    placeholder = "如 ${QuiddityConstants.DEFAULT_MAX_TOKENS}",
                    singleLine = true
                )
                QuiddityTextField(
                    value = localSingle,
                    onValueChange = { localSingle = it },
                    label = "单条消息 Token 上限",
                    placeholder = "如 ${QuiddityConstants.DEFAULT_SINGLE_MESSAGE_TOKENS}",
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = {
                            if (localMax.isNotEmpty()) onMaxChange(localMax)
                            if (localSingle.isNotEmpty()) onSingleChange(localSingle)
                            Toast.makeText(context, "Token 设置已保存", Toast.LENGTH_SHORT).show()
                        }
                    ) { Text("保存") }
                }

                // ===== Token 估算说明 =====
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Token 估算规则",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = "• 中文字 ≈ 1.5 Token\n" +
                                "• 英文字母 ≈ 0.25 Token（4字母≈1Token）\n" +
                                "• emoji ≈ 3 Token\n" +
                                "• 仅供 UI 显示参考，非精确计数",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 小型统计卡片。
 */
@Composable
private fun StatsMiniCard(
    title: String,
    value: String,
    suffix: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.size(2.dp))
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.size(2.dp))
                Text(
                    text = suffix,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}
