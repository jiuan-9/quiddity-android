package com.quiddity.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.quiddity.app.active.ActiveMessageSystem

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
 * 主动消息系统条件卡片（对应算法文档 5.1 触发可靠性引导）。
 *
 * 展示两项影响"到点是否准时触发"的系统条件：
 * - 精确闹钟：Android 12+ 未授权时触发可能延迟数分钟
 * - 电池优化：未放行时国产 ROM 可能强杀后台，闹钟到点不执行
 *
 * 状态实时读取（轻量系统 API），点击"去授权/去设置"跳转对应系统页面；
 * 返回后组合重组时会重新读取状态，无需手动刷新。
 */
@Composable
fun ActiveMessagePermissionCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exactGranted = ActiveMessageSystem.exactAlarmGranted(context)
    val batteryIgnored = ActiveMessageSystem.batteryOptimizationIgnored(context)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        PermissionStatusRow(
            title = "精确闹钟",
            ok = exactGranted,
            okText = "已授权，到点准时触发",
            badText = "未授权，到点触发可能延迟",
            actionText = "去授权",
            onClick = { ActiveMessageSystem.openExactAlarmSettings(context) }
        )
        Spacer(modifier = Modifier.size(10.dp))
        PermissionStatusRow(
            title = "电池优化",
            ok = batteryIgnored,
            okText = "已放行，后台不被拦截",
            badText = "未放行，后台可能被拦截",
            actionText = "去设置",
            onClick = { ActiveMessageSystem.openBatteryOptimizationSettings(context) }
        )
        Spacer(modifier = Modifier.size(10.dp))
        Text(
            text = "提示：国产 ROM 还需允许本应用【自启动】；若到点未收到消息，请检查系统省电与自启动设置。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

/**
 * 单行系统条件状态：左侧标题与状态说明，右侧"已就绪"对勾或跳转按钮。
 */
@Composable
private fun PermissionStatusRow(
    title: String,
    ok: Boolean,
    okText: String,
    badText: String,
    actionText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (ok) okText else badText,
                style = MaterialTheme.typography.labelSmall,
                color = if (ok) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
        if (ok) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "已就绪",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onClick) {
                Text(text = actionText)
            }
        }
    }
}
