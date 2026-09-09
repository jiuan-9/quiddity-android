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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.quiddity.app.active.ActiveMessageSystem

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
    // 系统 API 查询（binder 调用）较慢，故用 remember 缓存避免每次重组重复查询；
    // 但 remember 不会随重组重新求值，授权页返回后仍是旧值。
    // 因此在每次 Lifecycle.Resume 时重读，保证从系统授权页返回后状态立即刷新。
    var exactGranted by remember { mutableStateOf(ActiveMessageSystem.exactAlarmGranted(context)) }
    var batteryIgnored by remember { mutableStateOf(ActiveMessageSystem.batteryOptimizationIgnored(context)) }
    LifecycleResumeEffect(Unit) {
        // 每次回到前台（含从系统授权页返回）重读权限状态，避免 remember 缓存旧值
        exactGranted = ActiveMessageSystem.exactAlarmGranted(context)
        batteryIgnored = ActiveMessageSystem.batteryOptimizationIgnored(context)
        onPauseOrDispose { }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
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
