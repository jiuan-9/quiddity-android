package com.quiddity.app.ui.agent

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quiddity.app.active.NotificationBridge
import com.quiddity.app.active.ScreenReaderService
import com.quiddity.app.di.ServiceLocator
import kotlinx.coroutines.launch

/*
 * ============================================================================
 * 开发规范 (Development Specifications)
 * ============================================================================
 *
 * 1. 问题修复规范
 *    所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
 *    修复内容需完全融入现有代码架构，保证代码逻辑的连贯性、可维护性和可扩展性。
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
 * Agent 设置页：权限状态中心（P0 骨架）。
 *
 * - 权限状态行：无障碍 / 通知 / 使用情况 / Shizuku，反映真实系统状态；
 * - 工具使用权限：感知 / 读取 / 写入三组开关，写入组在 Shizuku 未授权时锁定；
 * - 等级徽章：基础已解锁，进阶待 Shizuku 授权；
 * - 白名单：增删（写入工具门控）；数据与隐私：审计清空 / 会话清空 / 隐私声明；
 * - 支持：开启教程（按版本路由）。
 */
@Composable
fun AgentSettingsScreen(
    onBack: () -> Unit,
    onOpenGuide: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = ServiceLocator.agentStore
    val settings by store.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showAddWhitelist by rememberSaveable { mutableStateOf(false) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    var showClearSessions by rememberSaveable { mutableStateOf(false) }

    val accessibilityEnabled = remember { ScreenReaderService.isServiceEnabled(context) }
    val notificationEnabled = remember { NotificationBridge.isServiceEnabled(context) }
    val usageEnabled = remember { hasUsageAccess(context) }
    val shizukuInstalled = remember { isShizukuInstalled(context) }
    val shizukuGranted = false

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // ===== 顶栏 =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "Agent 设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.size(48.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ===== 1. 权限状态 =====
            SettingsSection(title = "权限状态") {
                StatusRow(
                    label = "无障碍（读屏）",
                    status = if (accessibilityEnabled) "已开启" else "未开启",
                    granted = accessibilityEnabled,
                    onAction = onOpenGuide
                )
                StatusRow(
                    label = "通知读取",
                    status = if (notificationEnabled) "已开启" else "未开启",
                    granted = notificationEnabled,
                    onAction = onOpenGuide
                )
                StatusRow(
                    label = "使用情况访问",
                    status = if (usageEnabled) "已开启" else "未开启",
                    granted = usageEnabled,
                    onAction = onOpenGuide
                )
                StatusRow(
                    label = "Shizuku（进阶）",
                    status = when {
                        shizukuGranted -> "已授权"
                        shizukuInstalled -> "已安装，未授权"
                        else -> "未安装"
                    },
                    granted = shizukuGranted,
                    onAction = onOpenGuide
                )
            }

            // ===== 2. 工具使用权限 =====
            SettingsSection(title = "工具使用权限") {
                ToolGroupTitle("感知")
                SwitchRow("读屏（read_screen）", settings.toolSwitches.sense_screen) { on ->
                    scope.launch { store.setToolSwitch("sense_screen", on) }
                }
                SwitchRow("通知（read_notifications）", settings.toolSwitches.sense_notifications) { on ->
                    scope.launch { store.setToolSwitch("sense_notifications", on) }
                }
                SwitchRow("用量（usage_stats / foreground_app）", settings.toolSwitches.sense_usage) { on ->
                    scope.launch { store.setToolSwitch("sense_usage", on) }
                }
                ToolGroupTitle("读取")
                SwitchRow("应用列表（list_apps）", settings.toolSwitches.read_apps) { on ->
                    scope.launch { store.setToolSwitch("read_apps", on) }
                }
                SwitchRow("系统信息（read_system）", settings.toolSwitches.read_system) { on ->
                    scope.launch { store.setToolSwitch("read_system", on) }
                }
                ToolGroupTitle("写入（需 Shizuku 授权）")
                LockedSwitchRow("停用/启用应用", settings.toolSwitches.write_disable, shizukuGranted) { on ->
                    scope.launch { store.setToolSwitch("write_disable", on) }
                }
                LockedSwitchRow("权限修改（appops）", settings.toolSwitches.write_appops, shizukuGranted) { on ->
                    scope.launch { store.setToolSwitch("write_appops", on) }
                }
                LockedSwitchRow("强制停止", settings.toolSwitches.write_force_stop, shizukuGranted) { on ->
                    scope.launch { store.setToolSwitch("write_force_stop", on) }
                }
                LockedSwitchRow("卸载应用", settings.toolSwitches.write_uninstall, shizukuGranted) { on ->
                    scope.launch { store.setToolSwitch("write_uninstall", on) }
                }
            }

            // ===== 3. 等级徽章 =====
            SettingsSection(title = "等级徽章") {
                BadgeRow(name = "基础", description = "只读能力（屏幕/通知/用量/应用列表）", unlocked = true)
                BadgeRow(
                    name = "进阶",
                    description = "系统写入（停用/权限/强停/卸载）",
                    unlocked = shizukuGranted
                )
            }

            // ===== 4. 白名单 =====
            SettingsSection(title = "白名单（写入工具门控）") {
                if (settings.whitelist.isEmpty()) {
                    Text(
                        text = "暂无白名单，写入类工具将被拒绝执行。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                settings.whitelist.forEach { pkg ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = pkg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { scope.launch { store.removeWhitelist(pkg) } }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "移除 $pkg",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                TextButton(onClick = { showAddWhitelist = true }) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text("添加包名")
                }
            }

            // ===== 5. 数据与隐私 =====
            SettingsSection(title = "数据与隐私") {
                Text(
                    text = "审计记录：${settings.audit.size} 条（最多保留 500 条）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { scope.launch { store.clearAudit() } }) {
                        Text("清空审计")
                    }
                    TextButton(onClick = { showClearSessions = true }) {
                        Text("清空 Agent 会话")
                    }
                    TextButton(onClick = { showPrivacy = true }) {
                        Text("隐私声明")
                    }
                }
            }

            // ===== 6. 支持 =====
            SettingsSection(title = "支持") {
                TextButton(onClick = onOpenGuide) {
                    Text("开启教程（按系统版本路由）")
                }
                Text(
                    text = "Quiddity Agent · 本地数据不上传",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }

    // ===== 添加白名单弹窗 =====
    if (showAddWhitelist) {
        AddWhitelistDialog(
            onDismiss = { showAddWhitelist = false },
            onAdd = { pkg ->
                scope.launch { store.addWhitelist(pkg) }
                showAddWhitelist = false
            }
        )
    }

    // ===== 隐私声明 =====
    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("隐私声明") },
            text = {
                Text(
                    "屏幕内容、通知内容、应用用量等数据仅在本机处理，用于 Agent 助手回答你的问题，" +
                        "不会上传到网络。工具执行记录保存在本机（agent-settings.json），可随时清空。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacy = false }) { Text("知道了") }
            }
        )
    }

    // ===== 清空 Agent 会话确认 =====
    if (showClearSessions) {
        AlertDialog(
            onDismissRequest = { showClearSessions = false },
            title = { Text("清空 Agent 会话") },
            text = { Text("将删除所有 Agent 类型会话及其消息，不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val agentIds = ServiceLocator.conversationRepository
                                .conversations.value
                                .filter { it.type == com.quiddity.app.data.model.ConversationType.AGENT }
                                .map { it.id }
                            if (agentIds.isNotEmpty()) {
                                ServiceLocator.conversationRepository.deleteConversations(agentIds)
                            }
                        }
                        showClearSessions = false
                    }
                ) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearSessions = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                content = content
            )
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    status: String,
    granted: Boolean,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = status,
            style = MaterialTheme.typography.labelMedium,
            color = if (granted) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.size(8.dp))
        TextButton(onClick = onAction, enabled = !granted) {
            Text(if (granted) "已开启" else "去开启")
        }
    }
}

@Composable
private fun ToolGroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun LockedSwitchRow(
    label: String,
    checked: Boolean,
    unlocked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!unlocked) {
                Text(
                    text = "Shizuku 授权后解锁",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onToggle, enabled = unlocked)
    }
}

@Composable
private fun BadgeRow(name: String, description: String, unlocked: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (unlocked) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.size(10.dp))
        Text(
            text = description + if (unlocked) " · 已解锁" else " · 未解锁",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AddWhitelistDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var input by rememberSaveable { mutableStateOf("") }
    val pkgRegex = Regex("^[a-zA-Z0-9._]+$")
    val valid = input.trim().isNotEmpty() && pkgRegex.matches(input.trim())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加白名单包名") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("例如 com.tencent.mm") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onAdd(input.trim()) }, enabled = valid) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun hasUsageAccess(context: Context): Boolean = runCatching {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    @Suppress("DEPRECATION")
    appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    ) == AppOpsManager.MODE_ALLOWED
}.getOrDefault(false)

private fun isShizukuInstalled(context: Context): Boolean = runCatching {
    context.packageManager.getPackageInfo("moe.shizuku.xyz", 0)
    true
}.getOrDefault(false)
