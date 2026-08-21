package com.quiddity.app.ui.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.ui.text.font.FontFamily
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
import com.quiddity.app.data.local.AgentAuditEntry
import com.quiddity.app.data.local.AgentPermissionControl
import com.quiddity.app.data.local.AgentToolSwitches
import com.quiddity.app.di.ServiceLocator
import com.quiddity.app.domain.agent.AgentExecutors
import com.quiddity.app.domain.agent.AgentToolCategory
import com.quiddity.app.domain.agent.AgentToolRegistry
import com.quiddity.app.ui.settings.ClickableRow
import com.quiddity.app.ui.settings.SettingsBottomSheet
import com.quiddity.app.ui.settings.SettingsSectionCard
import com.quiddity.app.ui.settings.SettingsViewModel
import com.quiddity.app.ui.components.QuiddityToggleSwitch
import com.quiddity.app.ui.theme.Motion
import com.quiddity.app.util.DateUtils
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
    var showAddBlacklist by rememberSaveable { mutableStateOf(false) }
    var showAuditDetail by rememberSaveable { mutableStateOf(false) }
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
    val usageEnabled = remember(refreshTick) { AgentExecutors.hasUsageAccess(context) }
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

    // 系统返回键：无子弹窗打开时关闭设置面板（子弹窗是独立窗口，会优先消费返回键）
    BackHandler(enabled = true) { dismissSheet() }

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
                                // ===== 五分类（只读 / 更改 / 删除 / 行为 / 识图）：
                                // 每类一张卡片：主开关一键全开/全关（任一工具关闭即呈现关态），
                                // 默认收起工具明细，展开后每个工具独立启停 + 问号查看作用 =====
                                AgentToolCategory.ALL.forEach { category ->
                                    ToolCategoryCard(
                                        category = category,
                                        switches = settings.toolSwitches,
                                        accessibilityEnabled = accessibilityEnabled,
                                        notificationEnabled = notificationEnabled,
                                        usageEnabled = usageEnabled,
                                        shizukuReady = shizukuReady,
                                        onToolSwitch = { name, on ->
                                            scope.launch { store.setToolSwitch(name, on) }
                                        },
                                        onCategorySwitch = { on ->
                                            scope.launch {
                                                store.setCategorySwitch(
                                                    AgentToolCategory.toolsOf(category),
                                                    on
                                                )
                                            }
                                        },
                                        onHelp = { text -> helpText = text }
                                    )
                                }
                            }
                        }

                        item(key = "permissionControl", contentType = { "section" }) {
                            SettingsSectionCard(title = "权限管控", tallHeader = true) {
                                ClickableRow(
                                    icon = Icons.Filled.Lock,
                                    title = "过问",
                                    subtitle = "危险 / 写入类工具执行前一次列出，由你确认",
                                    onClick = { scope.launch { store.setPermissionControl(AgentPermissionControl.ASK) } },
                                    helpText = "模型会先说明要执行哪些工具，一次列出后等你确认，再继续执行并给出答案。",
                                    onHelpClick = { helpText = "模型会先说明要执行哪些工具，一次列出后等你确认，再继续执行并给出答案。" },
                                    trailingContent = {
                                        if (settings.permissionControl == AgentPermissionControl.ASK) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "已选择过问",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                )
                                ClickableRow(
                                    icon = Icons.Filled.CheckCircle,
                                    title = "完全",
                                    subtitle = "信任开关与黑名单，不再逐次确认，自动执行",
                                    onClick = { scope.launch { store.setPermissionControl(AgentPermissionControl.FULL) } },
                                    helpText = "危险 / 写入类工具将自动执行（仍受工具开关与黑名单约束），不再弹确认框。",
                                    onHelpClick = { helpText = "危险 / 写入类工具将自动执行（仍受工具开关与黑名单约束），不再弹确认框。" },
                                    trailingContent = {
                                        if (settings.permissionControl == AgentPermissionControl.FULL) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "已选择完全",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                )
                                // ===== 黑名单（v1 白名单语义反转）：默认全应用权限；
                                // 跟随「过问」权限模式：过问开启时展开显示，切到「完全」自动收起；
                                // 黑名单列表与添加入口合并为一个整体区块 =====
                                AnimatedVisibility(
                                    visible = settings.permissionControl == AgentPermissionControl.ASK,
                                    enter = fadeIn(tween(Motion.DurationShort)) +
                                        expandVertically(tween(Motion.DurationShort, easing = Motion.EasingEmphasizedDecelerate)),
                                    exit = fadeOut(tween(Motion.DurationShort)) +
                                        shrinkVertically(tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate))
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f))
                                    ) {
                                        // 头部：黑名单标题 + 说明 + 添加按钮（合为一体）
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Block,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.size(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "黑名单",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "默认全应用权限；命中的应用/文件 AI 无权查看、更改、删除",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                            }
                                            TextButton(onClick = { showAddBlacklist = true }) {
                                                Icon(
                                                    imageVector = Icons.Filled.Add,
                                                    contentDescription = "添加黑名单",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.size(2.dp))
                                                Text(
                                                    text = "添加",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        // 条目列表
                                        if (settings.blacklist.isEmpty()) {
                                            Text(
                                                text = "暂无黑名单条目",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.padding(start = 44.dp, end = 14.dp, bottom = 12.dp)
                                            )
                                        } else {
                                            settings.blacklist.forEach { entry ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 44.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = entry,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    IconButton(onClick = { scope.launch { store.removeBlacklist(entry) } }) {
                                                        Icon(
                                                            imageVector = Icons.Filled.Close,
                                                            contentDescription = "移出黑名单 $entry",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.size(4.dp))
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "privacy", contentType = { "section" }) {
                            SettingsSectionCard(title = "数据与隐私", tallHeader = true) {
                                ClickableRow(
                                    icon = Icons.Filled.Description,
                                    title = "审计记录",
                                    subtitle = "${settings.audit.size} 条（最多保留 500 条）",
                                    onClick = { showAuditDetail = true },
                                    helpText = "记录每次工具执行的明细（执行结果、日期、时间、工具名称）；点进查看详情。",
                                    onHelpClick = { helpText = "记录每次工具执行的明细（执行结果、日期、时间、工具名称）；点进查看详情。" },
                                    trailingContent = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "查看详情",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
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
                            SettingsSectionCard(title = "支持", defaultExpanded = true, tallHeader = true) {
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

    // ===== 添加黑名单弹窗 =====
    if (showAddBlacklist) {
        AddBlacklistDialog(
            onDismiss = { showAddBlacklist = false },
            onAdd = { entry ->
                scope.launch { store.addBlacklist(entry) }
                showAddBlacklist = false
            }
        )
    }

    // ===== 审计记录详情弹窗：只读展示 AI 工具使用日志 =====
    if (showAuditDetail) {
        AuditDetailDialog(
            audit = settings.audit,
            onDismiss = { showAuditDetail = false }
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

/** 可展开设置分组：点头部展开/收起（用于权限状态、工具使用开关）。 */
@Composable
private fun ExpandableSettingsSection(
    title: String,
    subtitle: String = "",
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
                // 设置大项统一头部高度（60dp，约原 2 倍）：未展开时所有大项等高
                .height(60.dp)
                .padding(horizontal = 12.dp),
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
/** 工具分类卡片：主开关（一键全开/全关）+ 展开后的每工具独立开关（中文名 + 问号说明）。 */
@Composable
private fun ToolCategoryCard(
    category: AgentToolCategory,
    switches: AgentToolSwitches,
    accessibilityEnabled: Boolean,
    notificationEnabled: Boolean,
    usageEnabled: Boolean,
    shizukuReady: Boolean,
    onToolSwitch: (String, Boolean) -> Unit,
    onCategorySwitch: (Boolean) -> Unit,
    onHelp: (String) -> Unit
) {
    val tools = AgentToolCategory.toolsOf(category).sortedBy { AgentToolRegistry.displayName(it) }
    // 主开关状态 = 权限可用的工具全部开启（未授权权限的工具不计入，
    // 避免「没给权限、五个分类却全显示开启」的误导）
    val allOn = categoryMasterOn(tools, switches) { toolName ->
        toolGateInfo(
            toolName,
            accessibilityEnabled,
            notificationEnabled,
            usageEnabled,
            shizukuReady
        ).first
    }
    // 分类内缺失的系统权限（去重）：存在缺权限工具时，主开关不允许开启，并 Toast 提示
    val missingPermissions = tools.mapNotNull { toolName ->
        val (usable, need) = toolGateInfo(
            toolName,
            accessibilityEnabled,
            notificationEnabled,
            usageEnabled,
            shizukuReady
        )
        if (!usable) need else null
    }.distinct()
    val contextForToast = LocalContext.current
    var expanded by rememberSaveable(category.id) { mutableStateOf(false) }
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = category.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 主开关：全部（可用）工具开启才显示开；点击一键全开 / 全关；
            // 分类内存在未授权权限的工具时不允许开启，并提示缺失权限
            QuiddityToggleSwitch(
                checked = allOn,
                onCheckedChange = { target ->
                    if (target && missingPermissions.isNotEmpty()) {
                        Toast.makeText(
                            contextForToast,
                            "${missingPermissions.joinToString("、")}未开启，无法开启该类工具",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        onCategorySwitch(target)
                    }
                }
            )
            Spacer(modifier = Modifier.size(4.dp))
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
            Column {
                tools.forEach { toolName ->
                    AgentToolRow(
                        toolName = toolName,
                        switches = switches,
                        accessibilityEnabled = accessibilityEnabled,
                        notificationEnabled = notificationEnabled,
                        usageEnabled = usageEnabled,
                        shizukuReady = shizukuReady,
                        onCheckedChange = { on -> onToolSwitch(toolName, on) },
                        onHelp = { onHelp(AgentToolRegistry.toolExplanation(toolName)) }
                    )
                }
            }
        }
    }
}

/** 单个工具行：中文名 + 问号说明 + 独立开关；所需权限未授权时开关置灰并注明。 */
@Composable
private fun AgentToolRow(
    toolName: String,
    switches: AgentToolSwitches,
    accessibilityEnabled: Boolean,
    notificationEnabled: Boolean,
    usageEnabled: Boolean,
    shizukuReady: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onHelp: () -> Unit
) {
    val (gateEnabled, need) = toolGateInfo(
        toolName,
        accessibilityEnabled,
        notificationEnabled,
        usageEnabled,
        shizukuReady
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .graphicsLayer { alpha = if (gateEnabled) 1f else 0.5f },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = AgentToolRegistry.displayName(toolName),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (need != null) {
                    Text(
                        text = "需" + need + "（未开启）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            IconButton(onClick = onHelp) {
                Icon(
                    imageVector = Icons.Filled.HelpOutline,
                    contentDescription = "工具作用说明",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
            QuiddityToggleSwitch(
                checked = switches.isEnabled(toolName),
                onCheckedChange = onCheckedChange,
                enabled = gateEnabled
            )
        }
    }
}

/**
 * 分类主开关状态（纯函数，1.6.1）：
 * - 只统计「权限已授予」的工具（[isUsable] 为 true）；
 * - 权限可用的工具全部开启 → 主开关显示开；任一可用工具关闭 → 显示关；
 * - 分类内没有任何可用工具（如全部依赖未授权的权限）→ 显示关。
 */
internal fun categoryMasterOn(
    tools: Collection<String>,
    switches: AgentToolSwitches,
    isUsable: (String) -> Boolean
): Boolean {
    val usable = tools.filter { isUsable(it) }
    return usable.isNotEmpty() && usable.all { switches.isEnabled(it) }
}

/** 工具行权限门控信息：返回 (是否可操作, 未授权时的所需权限名)。 */
private fun toolGateInfo(
    toolName: String,
    accessibilityEnabled: Boolean,
    notificationEnabled: Boolean,
    usageEnabled: Boolean,
    shizukuReady: Boolean
): Pair<Boolean, String?> = when (toolName) {
    "read_screen", "toast_monitor", "screenshot" ->
        accessibilityEnabled to (if (accessibilityEnabled) null else "无障碍服务")
    "read_notifications", "notification_guard", "dismiss_notification", "reply_notification" ->
        notificationEnabled to (if (notificationEnabled) null else "通知使用权")
    "usage_stats", "foreground_app", "app_usage_detail", "traffic_ranking" ->
        usageEnabled to (if (usageEnabled) null else "使用情况访问")
    "app_battery", "system_logs", "app_logs", "read_file", "list_files", "file_info",
    "reveal_file", "create_file", "write_file", "append_file", "rename_file", "mkdir",
    "move_file", "copy_file", "delete_file", "run_shell", "disable_app", "enable_app",
    "set_appops", "force_stop", "uninstall_app" ->
        shizukuReady to (if (shizukuReady) null else "Shizuku 授权")
    "click", "long_press", "click_text", "scroll", "global_action", "input_text",
    "click_id", "click_desc", "drag", "scroll_to_text", "lock_screen" ->
        accessibilityEnabled to (if (accessibilityEnabled) null else "无障碍服务")
    else -> false to "Shizuku 授权"
}

/** 添加黑名单弹窗：应用包名 或 文件/目录路径（默认全应用权限，命中即拒绝 AI 查看/更改/删除）。 */
@Composable
private fun AddBlacklistDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var input by rememberSaveable { mutableStateOf("") }
    val valid = input.trim().isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加黑名单") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("应用包名 或 文件/目录路径") },
                placeholder = { Text("例如 com.tencent.mm 或 /sdcard/Download/private") },
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

/** 审计记录详情弹窗：只读展示 AI 工具使用日志（执行结果 / 日期 / 时间 / 工具名称）。 */
@Composable
private fun AuditDetailDialog(
    audit: List<AgentAuditEntry>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 工具使用日志") },
        text = {
            if (audit.isEmpty()) {
                Text("暂无工具执行记录。", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(audit.reversed()) { entry ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = AgentToolRegistry.displayName(entry.tool),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = when {
                                        entry.ok -> "✓ 成功"
                                        entry.confirmed -> "✕ 失败"
                                        else -> "已取消"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (entry.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(
                                text = formatAuditDateTime(entry.ts),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

/** 审计时间格式化：ISO 时间戳 → 「yyyy-MM-dd HH:mm」。 */
private fun formatAuditDateTime(iso: String): String = runCatching {
    val instant = java.time.Instant.parse(iso)
    val millis = instant.toEpochMilli()
    DateUtils.formatTimestamp(millis)
}.getOrDefault(iso)

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
