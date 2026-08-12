package com.quiddity.app.ui.agent

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.quiddity.app.active.NotificationBridge
import com.quiddity.app.active.ScreenReaderService
import com.quiddity.app.active.ShizukuStatus
import com.quiddity.app.di.ServiceLocator
import com.quiddity.app.ui.settings.ClickableRow
import com.quiddity.app.ui.settings.SettingsBottomSheet
import com.quiddity.app.ui.settings.SettingsSectionCard
import com.quiddity.app.ui.settings.SettingsViewModel
import com.quiddity.app.ui.settings.ToggleRow
import com.quiddity.app.ui.theme.Motion
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/** Shizuku 直装下载页（Quiddity 官网，全中文）。 */
private const val SHIZUKU_DOWNLOAD_URL =
    "https://jiuan-9.github.io/Quiddity-website/downloads/shizuku.apk"
private const val SHIZUKU_REQUEST_CODE = 1101

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
 * Agent 设置（会话外设置）：与总设置完全同款的底部弹层。
 *
 * - 排版/滑动/动画/行样式与 [SettingsBottomSheet] 一致（毛玻璃面板 + 顶部抓手拖拽 + 分组卡片行）；
 * - 内容仅 Agent 专属：权限状态 / 工具使用开关 / 等级徽章 / 白名单 / 数据与隐私 / 支持；
 * - 全局设置（主题 / 字体 / Markdown 等）通过「总设置」入口进入，受总设置管控。
 */
@Composable
fun AgentSettingsScreen(
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val store = ServiceLocator.agentStore
    val settings by store.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showAddWhitelist by rememberSaveable { mutableStateOf(false) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    var showClearSessions by rememberSaveable { mutableStateOf(false) }
    var showGlobalSettings by rememberSaveable { mutableStateOf(false) }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    var helpText by remember { mutableStateOf<String?>(null) }

    // 从系统设置返回后刷新权限状态（ON_RESUME）
    var refreshTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val accessibilityEnabled = remember(refreshTick) { ScreenReaderService.isServiceEnabled(context) }
    val notificationEnabled = remember(refreshTick) { NotificationBridge.isServiceEnabled(context) }
    val usageEnabled = remember(refreshTick) { hasUsageAccess(context) }
    val shizukuClient = ServiceLocator.shizukuClient
    var shizukuStatus by remember(refreshTick) { mutableStateOf(shizukuClient.status()) }
    // Shizuku 已授权并配对：写入类与全局日志等工具的开关才可操作
    val shizukuReady = shizukuStatus == ShizukuStatus.GRANTED
    var visible by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val screenHeightPx = with(LocalDensity.current) { screenHeight.toPx() }
    val dragOffsetYState = remember { mutableFloatStateOf(0f) }
    val dismissThreshold = screenHeightPx * 0.2f
    LaunchedEffect(Unit) { visible = true }

    fun refreshShizuku() {
        shizukuStatus = ServiceLocator.shizukuClient.status()
    }

    DisposableEffect(Unit) {
        val received = Shizuku.OnBinderReceivedListener { refreshShizuku() }
        val dead = Shizuku.OnBinderDeadListener { refreshShizuku() }
        Shizuku.addBinderReceivedListener(received)
        Shizuku.addBinderDeadListener(dead)
        onDispose {
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
        }
    }

    fun dismissSheet() {
        visible = false
        scope.launch {
            kotlinx.coroutines.delay(Motion.DurationShort.toLong())
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(Motion.DurationMedium)),
            exit = fadeOut(tween(Motion.DurationShort)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { dismissSheet() }
                    )
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(Motion.DurationXLong, easing = Motion.EasingEmphasizedDecelerate)
            ) + fadeIn(tween(Motion.DurationLong)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedAccelerate)
            ) + fadeOut(tween(Motion.DurationShort)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = screenHeight * 0.8f)
                    .fillMaxHeight()
                    .graphicsLayer {
                        translationY = dragOffsetYState.floatValue.coerceAtLeast(0f)
                    },
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                ),
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    AgentSheetGrabBar(
                        dragOffsetYState = dragOffsetYState,
                        dismissThreshold = dismissThreshold,
                        onClose = { dismissSheet() }
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)
                    ) {
                        item(key = "permissions", contentType = { "section" }) {
                            ExpandableSettingsSection(
                                title = "权限状态",
                                defaultExpanded = false
                            ) {
                                ClickableRow(
                                    icon = Icons.Filled.Visibility,
                                    title = "无障碍（读屏）",
                                    subtitle = if (accessibilityEnabled) "已开启" else "未开启",
                                    onClick = { openSystemSettings(context, Settings.ACTION_ACCESSIBILITY_SETTINGS) },
                                    helpText = "允许 Agent 读取屏幕上的文字，才能帮你「看」当前页面（只读，不上传）。",
                                    onHelpClick = { helpText = "允许 Agent 读取屏幕上的文字，才能帮你「看」当前页面（只读，不上传）。" },
                                    trailingContent = {
                                        if (accessibilityEnabled) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "已开启",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else {
                                            Text(
                                                text = "去开启",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                )
                                ClickableRow(
                                    icon = Icons.Filled.Notifications,
                                    title = "通知读取",
                                    subtitle = if (notificationEnabled) "已开启" else "未开启",
                                    onClick = { openSystemSettings(context, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
                                    helpText = "允许 Agent 读取手机通知（如验证码、消息提醒），只读、不上传。",
                                    onHelpClick = { helpText = "允许 Agent 读取手机通知（如验证码、消息提醒），只读、不上传。" },
                                    trailingContent = {
                                        if (notificationEnabled) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "已开启",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else {
                                            Text(
                                                text = "去开启",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                )
                                ClickableRow(
                                    icon = Icons.Filled.Speed,
                                    title = "使用情况访问",
                                    subtitle = if (usageEnabled) "已开启" else "未开启",
                                    onClick = { openSystemSettings(context, Settings.ACTION_USAGE_ACCESS_SETTINGS) },
                                    helpText = "允许 Agent 查看你用了哪些应用、用了多久（用量统计、前台应用）。",
                                    onHelpClick = { helpText = "允许 Agent 查看你用了哪些应用、用了多久（用量统计、前台应用）。" },
                                    trailingContent = {
                                        if (usageEnabled) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "已开启",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else {
                                            Text(
                                                text = "去开启",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                )
                                ClickableRow(
                                    icon = Icons.Filled.Settings,
                                    title = "Shizuku（进阶）",
                                    subtitle = when {
                                        shizukuStatus == ShizukuStatus.GRANTED -> "已授权"
                                        shizukuStatus == ShizukuStatus.RUNNING_NOT_GRANTED -> "已安装，未授权"
                                        shizukuStatus == ShizukuStatus.INSTALLED_NOT_RUNNING -> "已安装，未运行"
                                        else -> "未安装"
                                    },
                                    onClick = {
                                        when (shizukuStatus) {
                                            ShizukuStatus.GRANTED -> launchShizukuApp(context)
                                            ShizukuStatus.RUNNING_NOT_GRANTED -> {
                                                val started = shizukuClient.requestPermission(
                                                    SHIZUKU_REQUEST_CODE
                                                ) { refreshShizuku() }
                                                if (!started) {
                                                    Toast.makeText(
                                                        context,
                                                        "Shizuku 未运行，请先启动",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    launchShizukuApp(context)
                                                }
                                            }
                                            ShizukuStatus.INSTALLED_NOT_RUNNING -> launchShizukuApp(context)
                                            ShizukuStatus.NOT_INSTALLED ->
                                                openUrl(context, SHIZUKU_DOWNLOAD_URL)
                                        }
                                    },
                                    helpText = "进阶能力（停用、卸载应用，改权限，强制停止）需要 Shizuku 授权；不开启只能用只读功能。",
                                    onHelpClick = { helpText = "进阶能力（停用、卸载应用，改权限，强制停止）需要 Shizuku 授权；不开启只能用只读功能。" },
                                    trailingContent = {
                                        when {
                                            shizukuStatus == ShizukuStatus.GRANTED -> Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "已授权",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            shizukuStatus == ShizukuStatus.RUNNING_NOT_GRANTED -> Text(
                                                text = "去开启",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            shizukuStatus == ShizukuStatus.INSTALLED_NOT_RUNNING -> Text(
                                                text = "去启动",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            else -> Text(
                                                text = "去下载",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                )
                            }
                        }

                        item(key = "tools", contentType = { "section" }) {
                            ExpandableSettingsSection(
                                title = "工具使用开关",
                                defaultExpanded = false
                            ) {
                                // ===== 感知类：依赖系统权限，未授权时开关置灰 =====
                                ToggleRow(
                                    icon = Icons.Filled.Visibility,
                                    title = "读屏",
                                    subtitle = toolStatusSubtitle("读取屏幕文本", "无障碍服务", accessibilityEnabled),
                                    checked = settings.toolSwitches.sense_screen,
                                    onCheckedChange = { on ->
                                        scope.launch { store.setToolSwitch("sense_screen", on) }
                                    },
                                    helpText = "打开后，Agent 才能使用「读取屏幕」工具（需要无障碍权限）。",
                                    onHelpClick = { helpText = "打开后，Agent 才能使用「读取屏幕」工具（需要无障碍权限）。" },
                                    enabled = accessibilityEnabled
                                )
                                ToggleRow(
                                    icon = Icons.Filled.Notifications,
                                    title = "通知",
                                    subtitle = toolStatusSubtitle("读取最近的通知", "通知使用权", notificationEnabled),
                                    checked = settings.toolSwitches.sense_notifications,
                                    onCheckedChange = { on ->
                                        scope.launch { store.setToolSwitch("sense_notifications", on) }
                                    },
                                    helpText = "打开后，Agent 才能使用「读取通知」工具（需要通知使用权）。",
                                    onHelpClick = { helpText = "打开后，Agent 才能使用「读取通知」工具（需要通知使用权）。" },
                                    enabled = notificationEnabled
                                )
                                ToggleRow(
                                    icon = Icons.Filled.Speed,
                                    title = "用量与前台应用",
                                    subtitle = toolStatusSubtitle("统计应用使用情况", "使用情况访问", usageEnabled),
                                    checked = settings.toolSwitches.sense_usage,
                                    onCheckedChange = { on ->
                                        scope.launch { store.setToolSwitch("sense_usage", on) }
                                    },
                                    helpText = "打开后，Agent 才能使用「用量统计」「前台应用」工具。",
                                    onHelpClick = { helpText = "打开后，Agent 才能使用「用量统计」「前台应用」工具。" },
                                    enabled = usageEnabled
                                )
                                // ===== 读取类：无需额外权限 =====
                                ToggleRow(
                                    icon = Icons.Filled.Apps,
                                    title = "应用列表",
                                    subtitle = "列出已安装应用",
                                    checked = settings.toolSwitches.read_apps,
                                    onCheckedChange = { on ->
                                        scope.launch { store.setToolSwitch("read_apps", on) }
                                    },
                                    helpText = "打开后，Agent 才能列出你手机上的应用。",
                                    onHelpClick = { helpText = "打开后，Agent 才能列出你手机上的应用。" }
                                )
                                ToggleRow(
                                    icon = Icons.Filled.ManageSearch,
                                    title = "应用信息",
                                    subtitle = "权限清单 / 安装时间来源 / 文件访问能力",
                                    checked = settings.toolSwitches.read_app_info,
                                    onCheckedChange = { on ->
                                        scope.launch { store.setToolSwitch("read_app_info", on) }
                                    },
                                    helpText = "打开后，Agent 才能查询应用的权限清单、安装时间与来源、文件访问能力。",
                                    onHelpClick = { helpText = "打开后，Agent 才能查询应用的权限清单、安装时间与来源、文件访问能力。" }
                                )
                                ToggleRow(
                                    icon = Icons.Filled.BatteryFull,
                                    title = "后台耗电",
                                    subtitle = toolStatusSubtitle("查询应用后台耗电统计", "Shizuku 授权", shizukuReady),
                                    checked = settings.toolSwitches.read_battery,
                                    onCheckedChange = { on ->
                                        scope.launch { store.setToolSwitch("read_battery", on) }
                                    },
                                    helpText = "打开后，Agent 才能查询指定应用的后台耗电（需要 Shizuku 授权）。",
                                    onHelpClick = { helpText = "打开后，Agent 才能查询指定应用的后台耗电（需要 Shizuku 授权）。" },
                                    enabled = shizukuReady
                                )
                                ToggleRow(
                                    icon = Icons.Filled.DataUsage,
                                    title = "流量排行",
                                    subtitle = toolStatusSubtitle("按流量统计已安装应用", "使用情况访问", usageEnabled),
                                    checked = settings.toolSwitches.read_traffic,
                                    onCheckedChange = { on ->
                                        scope.launch { store.setToolSwitch("read_traffic", on) }
                                    },
                                    helpText = "打开后，Agent 才能按接收/发送流量排行应用（需要使用情况访问权限）。",
                                    onHelpClick = { helpText = "打开后，Agent 才能按接收/发送流量排行应用（需要使用情况访问权限）。" },
                                    enabled = usageEnabled
                                )
                                ToggleRow(
                                    icon = Icons.Filled.PhotoCamera,
                                    title = "截图",
                                    subtitle = toolStatusSubtitle("截取当前屏幕并保存", "无障碍服务", accessibilityEnabled),
                                    checked = settings.toolSwitches.read_screenshot,
                                    onCheckedChange = { on ->
                                        scope.launch { store.setToolSwitch("read_screenshot", on) }
                                    },
                                    helpText = "打开后，Agent 才能截取当前屏幕（需要无障碍权限与 Android 11+）。",
                                    onHelpClick = { helpText = "打开后，Agent 才能截取当前屏幕（需要无障碍权限与 Android 11+）。" },
                                    enabled = accessibilityEnabled
                                )
                                ToggleRow(
                                    icon = Icons.Filled.Article,
                                    title = "全局日志",
                                    subtitle = toolStatusSubtitle("查看所有应用的日志报告", "Shizuku 授权", shizukuReady),
                                    checked = settings.toolSwitches.read_logs,
                                    onCheckedChange = { on ->
                                        scope.launch { store.setToolSwitch("read_logs", on) }
                                    },
                                    helpText = "打开后，Agent 才能读取全局系统日志（logcat，覆盖所有应用；需要 Shizuku 授权）。",
                                    onHelpClick = { helpText = "打开后，Agent 才能读取全局系统日志（logcat，覆盖所有应用；需要 Shizuku 授权）。" },
                                    enabled = shizukuReady
                                )
                                // ===== 写入类：无论是否授权都有开关；Shizuku 未授权/未配对时置灰 =====
                                ToggleRow(
                                    icon = Icons.Filled.Block,
                                    title = "停用与启用应用",
                                    subtitle = toolStatusSubtitle("停用/启用指定应用", "Shizuku 授权", shizukuReady),
                                    checked = settings.toolSwitches.write_disable,
                                    onCheckedChange = { on ->
                                        scope.launch { store.setToolSwitch("write_disable", on) }
                                    },
                                    helpText = "停用后应用图标消失、无法运行；需 Shizuku 授权，且目标应用必须在白名单内。",
                                    onHelpClick = { helpText = "停用后应用图标消失、无法运行；需 Shizuku 授权，且目标应用必须在白名单内。" },
                                    enabled = shizukuReady
                                )
                                ToggleRow(
                                    icon = Icons.Filled.Lock,
                                    title = "权限修改",
                                    subtitle = toolStatusSubtitle("修改应用的权限模式", "Shizuku 授权", shizukuReady),
                                    checked = settings.toolSwitches.write_appops,
                                    onCheckedChange = { on ->
                                        scope.launch { store.setToolSwitch("write_appops", on) }
                                    },
                                    helpText = "修改应用的权限模式（如拒绝震动、定位等）；需 Shizuku 授权 + 白名单。",
                                    onHelpClick = { helpText = "修改应用的权限模式（如拒绝震动、定位等）；需 Shizuku 授权 + 白名单。" },
                                    enabled = shizukuReady
                                )
                                ToggleRow(
                                    icon = Icons.Filled.Stop,
                                    title = "强制停止",
                                    subtitle = toolStatusSubtitle("立即停止应用的后台运行", "Shizuku 授权", shizukuReady),
                                    checked = settings.toolSwitches.write_force_stop,
                                    onCheckedChange = { on ->
                                        scope.launch { store.setToolSwitch("write_force_stop", on) }
                                    },
                                    helpText = "立即停止应用的后台运行；需 Shizuku 授权 + 白名单。",
                                    onHelpClick = { helpText = "立即停止应用的后台运行；需 Shizuku 授权 + 白名单。" },
                                    enabled = shizukuReady
                                )
                                ToggleRow(
                                    icon = Icons.Filled.Delete,
                                    title = "卸载应用",
                                    subtitle = toolStatusSubtitle("卸载指定应用", "Shizuku 授权", shizukuReady),
                                    checked = settings.toolSwitches.write_uninstall,
                                    onCheckedChange = { on ->
                                        scope.launch { store.setToolSwitch("write_uninstall", on) }
                                    },
                                    helpText = "卸载指定应用；需 Shizuku 授权 + 白名单，卸载后数据不可恢复。",
                                    onHelpClick = { helpText = "卸载指定应用；需 Shizuku 授权 + 白名单，卸载后数据不可恢复。" },
                                    enabled = shizukuReady
                                )
                            }
                        }

                        item(key = "whitelist", contentType = { "section" }) {
                            SettingsSectionCard(title = "白名单（写入工具门控）") {
                                if (settings.whitelist.isEmpty()) {
                                    ClickableRow(
                                        icon = Icons.Filled.Info,
                                        title = "暂无白名单",
                                        subtitle = "写入类工具将被拒绝执行",
                                        onClick = { showAddWhitelist = true },
                                        helpText = "只有加入白名单的应用，Agent 才能对它执行停用、卸载等写入操作。",
                                        onHelpClick = { helpText = "只有加入白名单的应用，Agent 才能对它执行停用、卸载等写入操作。" }
                                    )
                                }
                                settings.whitelist.forEach { pkg ->
                                    ClickableRow(
                                        icon = Icons.Filled.Lock,
                                        title = pkg,
                                        subtitle = "已加入白名单",
                                        onClick = {},
                                        trailingContent = {
                                            IconButton(onClick = { scope.launch { store.removeWhitelist(pkg) } }) {
                                                Icon(
                                                    imageVector = Icons.Filled.Close,
                                                    contentDescription = "移除 $pkg",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    )
                                }
                                ClickableRow(
                                    icon = Icons.Filled.Add,
                                    title = "添加包名",
                                    subtitle = "加入后写入类工具才可执行",
                                    onClick = { showAddWhitelist = true },
                                    helpText = "输入应用包名（如 com.tencent.mm）加入白名单，写入工具才能对该应用生效。",
                                    onHelpClick = { helpText = "输入应用包名（如 com.tencent.mm）加入白名单，写入工具才能对该应用生效。" }
                                )
                            }
                        }

                        item(key = "privacy", contentType = { "section" }) {
                            SettingsSectionCard(title = "数据与隐私") {
                                ClickableRow(
                                    icon = Icons.Filled.Description,
                                    title = "审计记录",
                                    subtitle = "${settings.audit.size} 条（最多保留 500 条）",
                                    onClick = { scope.launch { store.clearAudit() } },
                                    helpText = "记录每次工具执行的明细（时间、工具、参数、是否确认）；点击可清空。",
                                    onHelpClick = { helpText = "记录每次工具执行的明细（时间、工具、参数、是否确认）；点击可清空。" }
                                )
                                ClickableRow(
                                    icon = Icons.Filled.Delete,
                                    title = "清空 Agent 会话",
                                    subtitle = "删除全部 Agent 会话与消息",
                                    onClick = { showClearSessions = true },
                                    helpText = "删除所有 Agent 会话及其消息，不可恢复。",
                                    onHelpClick = { helpText = "删除所有 Agent 会话及其消息，不可恢复。" }
                                )
                                ClickableRow(
                                    icon = Icons.Filled.PrivacyTip,
                                    title = "隐私声明",
                                    subtitle = "数据仅在本机处理，不上传",
                                    onClick = { showPrivacy = true },
                                    helpText = "说明屏幕、通知、用量等数据的本地处理规则，不会上传网络。",
                                    onHelpClick = { helpText = "说明屏幕、通知、用量等数据的本地处理规则，不会上传网络。" }
                                )
                            }
                        }

                        item(key = "support", contentType = { "section" }) {
                            SettingsSectionCard(title = "支持") {
                                ClickableRow(
                                    icon = Icons.Filled.Settings,
                                    title = "总设置",
                                    subtitle = "全局：主题、字体、Markdown 等",
                                    onClick = { showGlobalSettings = true },
                                    helpText = "全局设置（主题、字体、Markdown 等），所有模式共用，Agent 也受它管控。",
                                    onHelpClick = { helpText = "全局设置（主题、字体、Markdown 等），所有模式共用，Agent 也受它管控。" }
                                )
                                ClickableRow(
                                    icon = Icons.Filled.HelpOutline,
                                    title = "教程",
                                    subtitle = "按系统分类，一步步开启",
                                    onClick = { showGuide = true },
                                    helpText = "按你的手机系统版本，一步步教你开启权限与 Shizuku。",
                                    onHelpClick = { helpText = "按你的手机系统版本，一步步教你开启权限与 Shizuku。" }
                                )
                            }
                        }
                    }
                }
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

    // ===== 总设置（与私聊/群聊同款全局设置弹层） =====
    if (showGlobalSettings) {
        SettingsBottomSheet(
            viewModel = settingsViewModel,
            onDismiss = { showGlobalSettings = false }
        )
    }

    // ===== 教程：底部弹出子设置页（与总设置的子页面一致） =====
    if (showGuide) {
        AgentSetupGuideSheet(onDismiss = { showGuide = false })
    }

    // ===== 设置项说明（问号）：独立浮层卡片，不再与底色同层 =====
    if (helpText != null) {
        Dialog(
            onDismissRequest = { helpText = null },
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                ),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = helpText.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { helpText = null }) { Text("知道了") }
                    }
                }
            }
        }
    }
}

/** 工具行副标题：所需权限未授权时附加「需XX（未开启）」，开关置灰原因一目了然。 */
private fun toolStatusSubtitle(base: String, need: String?, granted: Boolean): String =
    if (need == null || granted) base else "$base · 需$need（未开启）"

/** 可展开设置分组：点头部展开/收起（用于权限状态、工具使用开关）。 */
@Composable
private fun ExpandableSettingsSection(
    title: String,
    defaultExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(defaultExpanded) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(com.quiddity.app.ui.components.glassCardColor())
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { expanded = !expanded }
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
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
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer {
                        rotationZ = if (expanded) 180f else 0f
                    }
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(Motion.DurationShort)) +
                expandVertically(tween(Motion.DurationShort, easing = Motion.EasingEmphasizedDecelerate)),
            exit = fadeOut(tween(Motion.DurationShort)) +
                shrinkVertically(tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate))
        ) {
            Column(content = content)
        }
    }
}

/** 底部弹层顶部抓手：拖拽面板 1:1 跟随，超阈值关闭，否则回弹。 */
@Composable
private fun AgentSheetGrabBar(
    dragOffsetYState: MutableFloatState,
    dismissThreshold: Float,
    onClose: () -> Unit
) {
    val scope = rememberCoroutineScope()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetYState.floatValue =
                            (dragOffsetYState.floatValue + dragAmount).coerceAtLeast(0f)
                    },
                    onDragEnd = {
                        if (dragOffsetYState.floatValue > dismissThreshold) {
                            onClose()
                        } else {
                            scope.launch {
                                val anim = Animatable(dragOffsetYState.floatValue)
                                anim.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(
                                        Motion.DurationShort,
                                        easing = Motion.EasingEmphasizedDecelerate
                                    )
                                ) { dragOffsetYState.floatValue = this.value }
                            }
                        }
                    }
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Agent 设置",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "关闭",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
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

/** 直接跳转系统对应设置页（无障碍 / 通知使用权 / 使用情况访问）。 */
private fun openSystemSettings(context: Context, action: String) {
    runCatching {
        context.startActivity(Intent(action))
    }.onFailure {
        Toast.makeText(context, "无法打开系统设置", Toast.LENGTH_SHORT).show()
    }
}

/** 已安装 Shizuku 时直接拉起 Shizuku 应用。 */
private fun launchShizukuApp(context: Context) {
    runCatching {
        val intent = listOf("moe.shizuku.privileged.api", "moe.shizuku.xyz")
            .mapNotNull { context.packageManager.getLaunchIntentForPackage(it) }
            .firstOrNull()
        if (intent != null) {
            context.startActivity(intent)
        } else {
            openSystemSettings(context, Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        }
    }.onFailure {
        Toast.makeText(context, "无法打开 Shizuku", Toast.LENGTH_SHORT).show()
    }
}

/** 打开 Shizuku 直装下载页（官网，全中文）。 */
private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }.onFailure {
        Toast.makeText(context, "无法打开下载页面", Toast.LENGTH_SHORT).show()
    }
}
