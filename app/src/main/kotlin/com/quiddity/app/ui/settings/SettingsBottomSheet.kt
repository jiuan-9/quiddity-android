package com.quiddity.app.ui.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quiddity.app.util.QuiddityConstants
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quiddity.app.di.ServiceLocator
import com.quiddity.app.data.model.ImportMode
import com.quiddity.app.ui.components.ActiveMessagePermissionCard
import com.quiddity.app.ui.components.ConfirmDialog
import com.quiddity.app.ui.components.ExpandableText
import com.quiddity.app.ui.components.QuiddityToggleSwitch
import com.quiddity.app.ui.components.UpdateDialog
import com.quiddity.app.ui.components.rememberUpdateController
import com.quiddity.app.util.UpdateChecker
import com.quiddity.app.ui.settings.components.ApiCatalogEditor
import com.quiddity.app.ui.settings.components.AvatarPicker
import com.quiddity.app.ui.settings.components.CustomerServiceRow
import com.quiddity.app.ui.settings.components.DelaySettingsPanel
import com.quiddity.app.ui.settings.components.DonateScreen
import com.quiddity.app.ui.settings.components.DocumentsDrawer
import com.quiddity.app.ui.settings.components.LegalDocsDrawer
import com.quiddity.app.ui.settings.components.ListWallpaperPanel
import com.quiddity.app.ui.settings.components.TokenEditorPanel
import com.quiddity.app.ui.theme.Motion
import com.quiddity.app.util.DataPorter
import com.quiddity.app.util.IdGenerator
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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


// 当前规则：80% 屏幕高度从底部滑入；rememberSaveable 保留子页面状态。
// 顶部抓手可拖动关闭：拖动时整个面板 translationY 实时跟随手指（1:1），
// 超过阈值（屏幕高度 20%）则关闭，否则回弹。
@Composable
fun SettingsBottomSheet(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val screenHeightPx = with(LocalDensity.current) { screenHeight.toPx() }
    val docsProvider = remember { ServiceLocator.docsProvider }
    val apiCatalogManager = remember { ServiceLocator.apiCatalogManager }
    // ClipboardManager 提升到顶层 remember：避免在 LazyColumn 滚动重组时每次都 getSystemService
    val clipboard = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    // ===== 版本检测 =====
    val updateController = rememberUpdateController()
    val currentVersion = remember { UpdateChecker.getCurrentVersion(context) }

    // ===== 子页面状态 =====
    var showApiEditor by rememberSaveable { mutableStateOf(false) }
    var showDonate by rememberSaveable { mutableStateOf(false) }
    var showTokenEditor by rememberSaveable { mutableStateOf(false) }
    var showDocuments by rememberSaveable { mutableStateOf(false) }
    var showDelayEditor by rememberSaveable { mutableStateOf(false) }
    var showListWallpaper by rememberSaveable { mutableStateOf(false) }
    var showLegalDocs by rememberSaveable { mutableStateOf(false) }
    var toastMsg by remember { mutableStateOf<String?>(null) }
    // 主动消息总开关：开启后先弹"已了解该功能"提示，确认后才持久化
    var showProactiveDialog by remember { mutableStateOf(false) }
    // 导入抉择：已有数据时暂存 payload，弹窗让用户选择替换/合并/取消
    var pendingImportPayload by remember {
        mutableStateOf<com.quiddity.app.data.model.ExportPayload?>(null)
    }
    // 导入后需重填密钥的模型配置名称清单（3.2 解密自检失败项）
    var pendingKeyRefill by remember { mutableStateOf<List<String>?>(null) }
    var visible by remember { mutableStateOf(false) }
    // 拖动关闭偏移：直接同步赋值，零协程。graphicsLayer 内 draw phase 读取。
    // 用 mutableFloatStateOf 持有，禁止用 by 委托在组合阶段读取——否则拖动时整个面板每帧重组。
    // 仅在 graphicsLayer lambda 内读 dragOffsetYState.floatValue，确保零重组。
    val dragOffsetYState = remember { mutableFloatStateOf(0f) }
    val dismissThreshold = screenHeightPx * 0.2f

    LaunchedEffect(Unit) { visible = true }

    // ===== 三条开发规范（位于文件中间位置） =====
    // 1. 问题修复规范：所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
    //    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
    // 2. 代码注释规范：文件内仅允许保留两类注释——当前规则说明注释与模块划分注释（// ===== xxx =====）。
    //    除此之外，禁止出现任何形式的代码注释（包括单行 / 多行 / 临时调试注释）。
    // 3. 构建交付要求：完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK。
    //    APK 需满足：签名有效且符合发布标准、包含完整功能模块、经过基础性能与兼容性测试。

    // ===== SAF 导出 =====
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val payload = viewModel.exportAllPayloadV2()
                DataPorter.exportTo(context, uri, payload)
                    .onSuccess { toastMsg = "导出成功" }
                    .onFailure { toastMsg = "导出失败：${it.message}" }
            }
        }
    }

    // ===== SAF 导入 =====
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {}
            scope.launch {
                DataPorter.importFrom(context, uri)
                    .onSuccess { plan ->
                        if (plan.needsKeyRefill.isNotEmpty()) {
                            pendingKeyRefill = plan.needsKeyRefill
                        }
                        // 已有数据时弹窗让用户抉择导入方式；无数据时直接合并导入
                        if (viewModel.hasExistingData()) {
                            pendingImportPayload = plan.payload
                        } else {
                            viewModel.importAllPayload(plan.payload, mode = ImportMode.MERGE)
                            toastMsg = if (plan.skipItems.isEmpty()) {
                                "导入成功"
                            } else {
                                "导入成功（${plan.skipItems.size} 项已跳过）"
                            }
                        }
                    }
                    .onFailure { toastMsg = "导入失败：${it.message}" }
            }
        }
    }

    // Android 13+ 需要通知权限：开启主动消息时请求，保证到点能弹通知
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    toastMsg?.let { msg ->
        LaunchedEffect(msg) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            toastMsg = null
        }
    }

    // 版本检测 toast 反馈
    updateController.toastMessage?.let { msg ->
        LaunchedEffect(msg) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            updateController.consumeToast()
        }
    }

    // ===== 返回键优先级（始终注册，不在 showDonate 时跳过） =====
    BackHandler(enabled = showDonate) {
        showDonate = false
    }
    BackHandler(enabled = showApiEditor) {
        showApiEditor = false
    }
    BackHandler(enabled = showListWallpaper && !showApiEditor) {
        showListWallpaper = false
    }
    BackHandler(enabled = showLegalDocs && !showApiEditor && !showListWallpaper) {
        showLegalDocs = false
    }
    BackHandler(enabled = showDocuments && !showApiEditor && !showListWallpaper && !showLegalDocs) {
        showDocuments = false
    }
    BackHandler(
        enabled = !showApiEditor && !showDocuments && !showListWallpaper && !showLegalDocs && !showDonate
    ) {
        visible = false
        scope.launch {
            kotlinx.coroutines.delay(Motion.DurationShort.toLong())
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // 半透明遮罩
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
                        indication = null
                    ) {
                        visible = false
                        scope.launch {
                            kotlinx.coroutines.delay(Motion.DurationShort.toLong())
                            onDismiss()
                        }
                    }
            )
        }

        // ===== 底部面板本体 =====
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
                    .height(screenHeight * 0.8f)
                    .graphicsLayer {
                    // 整个面板跟随拖动偏移（1:1，draw phase 读取，零重组）
                    translationY = dragOffsetYState.floatValue.coerceAtLeast(0f)
                },
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    // 顶部抓手 + 标题 + 关闭
                    CenterGrabBar(
                        dragOffsetYState = dragOffsetYState,
                        dismissThreshold = dismissThreshold,
                        onClose = {
                            visible = false
                            scope.launch {
                                kotlinx.coroutines.delay(Motion.DurationShort.toLong())
                                onDismiss()
                            }
                        }
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        // 头像区
                        item(key = "avatar") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                AvatarPicker(
                                    avatarUri = settings.userAvatarUri,
                                    onPicked = { uri -> viewModel.setUserAvatar(uri) }
                                )
                            }
                        }

                        // ===== Section 1: 显示 =====
                        item(key = "section_display") {
                            SectionHeader(title = "显示")
                        }
                        item(key = "dark_mode", contentType = { "toggle" }) {
                            ToggleRow(
                                icon = Icons.Filled.Brightness6,
                                title = "深色模式",
                                subtitle = if (settings.darkMode) "当前：暗色" else "当前：亮色",
                                checked = settings.darkMode,
                                onCheckedChange = { viewModel.setDarkMode(it) }
                            )
                        }
                        item(key = "bracket_gray", contentType = { "toggle" }) {
                            ToggleRow(
                                icon = Icons.Filled.FormatSize,
                                title = "括号内容灰化",
                                subtitle = if (settings.bracketGrayEnabled) "括号内文字显示为灰色"
                                else "全部文本统一颜色",
                                checked = settings.bracketGrayEnabled,
                                onCheckedChange = { viewModel.setBracketGrayEnabled(it) }
                            )
                        }
                        item(key = "follow_system_font", contentType = { "toggle" }) {
                            ToggleRow(
                                icon = Icons.Filled.FormatSize,
                                title = "跟随系统字体",
                                subtitle = if (settings.followSystemFont) "使用系统字号设置"
                                else "使用应用内字号（不受系统字号影响）",
                                checked = settings.followSystemFont,
                                onCheckedChange = { viewModel.setFollowSystemFont(it) }
                            )
                        }
                        item(key = "font_size", contentType = { "slider" }) {
                            FontSizeRow(
                                fontScale = settings.fontScale,
                                enabled = !settings.followSystemFont,
                                onValueChangeFinished = { value ->
                                    viewModel.setFontScale(value)
                                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                        item(key = "list_wallpaper", contentType = { "click" }) {
                            ClickableRow(
                                icon = Icons.Filled.Image,
                                title = "会话列表壁纸",
                                subtitle = if (settings.listWallpaperUri != null) "已设置"
                                else "未设置",
                                onClick = { showListWallpaper = true }
                            )
                        }
                        item(key = "proactive_message", contentType = { "toggle" }) {
                            ToggleRow(
                                icon = Icons.Filled.Notifications,
                                title = "主动消息",
                                subtitle = if (settings.proactiveMessageEnabled) "已开启（需在会话内单独启用）"
                                else "AI 在指定时间主动发消息",
                                checked = settings.proactiveMessageEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        // Android 13+ 需要通知权限：开启时一并请求，保证到点能弹通知
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                            ContextCompat.checkSelfPermission(
                                                context,
                                                Manifest.permission.POST_NOTIFICATIONS
                                            ) != PackageManager.PERMISSION_GRANTED
                                        ) {
                                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                        // 仅表知晓：先弹提示，确认后才保存总开关状态
                                        showProactiveDialog = true
                                    } else {
                                        viewModel.setProactiveMessageEnabled(false)
                                    }
                                }
                            )
                        }
                        // 系统条件引导：总开关开启后展示精确闹钟 / 电池优化状态与一键跳转
                        if (settings.proactiveMessageEnabled) {
                            item(key = "proactive_permission", contentType = { "card" }) {
                                ActiveMessagePermissionCard(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // ===== Section 2: 模型配置 =====
                        item(key = "section_api") {
                            SectionHeader(title = "模型配置")
                        }
                        item(key = "api_catalog", contentType = { "click" }) {
                            val apiSubtitle = remember(settings.catalog, settings.activeCatalogId) {
                                if (settings.catalog.isEmpty()) "未配置"
                                else "${settings.catalog.size} 项 · 当前：${settings.catalog.firstOrNull { it.id == settings.activeCatalogId }?.let { "${it.name} · ${it.apiModel}" } ?: "未选择"}"
                            }
                            ClickableRow(
                                icon = Icons.Filled.Layers,
                                title = "模型配置",
                                subtitle = apiSubtitle,
                                onClick = { showApiEditor = true },
                                expandableSubtitle = true
                            )
                        }

                        // ===== Section 3: 生成 =====
                        item(key = "section_generation") {
                            SectionHeader(title = "生成")
                        }
                        item(key = "token_settings", contentType = { "click" }) {
                            val tokenSubtitle = remember(settings.globalMaxTokens, settings.globalSingleMessageTokens) {
                                "最大回复 ${settings.globalMaxTokens} / 单条 ${settings.globalSingleMessageTokens}"
                            }
                            ClickableRow(
                                icon = Icons.Filled.Memory,
                                title = "Token 设置",
                                subtitle = tokenSubtitle,
                                onClick = { showTokenEditor = !showTokenEditor }
                            )
                        }
                        if (showTokenEditor) {
                            item(key = "token_editor", contentType = { "editor" }) {
                                TokenEditorPanel(
                                    maxTokens = settings.globalMaxTokens,
                                    singleTokens = settings.globalSingleMessageTokens,
                                    onMaxChange = { v -> if (v.isNotEmpty()) viewModel.setMaxTokens(v.toIntOrNull() ?: 4096) },
                                    onSingleChange = { v -> if (v.isNotEmpty()) viewModel.setSingleMessageTokens(v.toIntOrNull() ?: 800) },
                                    modifier = Modifier.animateItem(
                                        placementSpec = tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate),
                                        fadeInSpec = tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate),
                                        fadeOutSpec = tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate)
                                    )
                                )
                            }
                        }
                        item(key = "multiline_split", contentType = { "toggle" }) {
                            ToggleRow(
                                icon = Icons.Filled.Layers,
                                title = "AI 回复切分",
                                subtitle = "AI 像人一样分多条发送",
                                checked = settings.multilineAutoSplit,
                                onCheckedChange = { viewModel.setMultilineSplit(it) }
                            )
                        }

                        // ===== Section 4: 交互 =====
                        item(key = "section_interact") {
                            SectionHeader(title = "交互")
                        }
                        item(key = "enter_to_send", contentType = { "toggle" }) {
                            ToggleRow(
                                icon = Icons.AutoMirrored.Filled.Send,
                                title = "回车键发送",
                                subtitle = "不开启则使用发送按钮",
                                checked = settings.enterToSend,
                                onCheckedChange = { viewModel.setEnterToSend(it) }
                            )
                        }
                        // ===== 延迟设置入口 =====
                        item(key = "delay_settings", contentType = { "click" }) {
                            val delayOverall = settings.typingDelayEnabled || settings.sendDelayEnabled
                            val delaySubtitle = remember(
                                delayOverall, settings.typingDelayMsPerChar, settings.sendDelaySeconds
                            ) {
                                if (delayOverall) {
                                    "已启用 · 打字机 ${settings.typingDelayMsPerChar}ms/字 · 发送延迟 ${settings.sendDelaySeconds}s"
                                } else {
                                    "已关闭（点击展开启用总开关）"
                                }
                            }
                            ClickableRow(
                                icon = Icons.Filled.Speed,
                                title = "延迟设置",
                                subtitle = delaySubtitle,
                                onClick = { showDelayEditor = !showDelayEditor }
                            )
                        }
                        if (showDelayEditor) {
                            item(key = "delay_editor", contentType = { "editor" }) {
                                DelaySettingsPanel(
                                    typingDelayEnabled = settings.typingDelayEnabled,
                                    typingDelayMsPerChar = settings.typingDelayMsPerChar,
                                    sendDelayEnabled = settings.sendDelayEnabled,
                                    sendDelaySeconds = settings.sendDelaySeconds,
                                    onTypingDelayEnabledChange = { viewModel.setTypingDelayEnabled(it) },
                                    onTypingDelayMsPerCharChange = { viewModel.setTypingDelayMsPerChar(it) },
                                    onSendDelayEnabledChange = { viewModel.setSendDelayEnabled(it) },
                                    onSendDelaySecondsChange = { viewModel.setSendDelaySeconds(it) },
                                    modifier = Modifier.animateItem(
                                        placementSpec = tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate),
                                        fadeInSpec = tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate),
                                        fadeOutSpec = tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate)
                                    )
                                )
                            }
                        }

                        // ===== Section 5: 数据 =====
                        item(key = "section_data") {
                            SectionHeader(title = "数据")
                        }
                        item(key = "export_data", contentType = { "click" }) {
                            ClickableRow(
                                icon = Icons.Filled.Upload,
                                title = "数据导出",
                                subtitle = "导出全部设置与会话",
                                onClick = {
                                    exportLauncher.launch("quiddity-backup-${IdGenerator.newUuid()}.json")
                                }
                            )
                        }
                        item(key = "import_data", contentType = { "click" }) {
                            ClickableRow(
                                icon = Icons.Filled.Download,
                                title = "数据导入",
                                subtitle = "从备份文件恢复",
                                onClick = {
                                    importLauncher.launch(arrayOf("application/json"))
                                }
                            )
                        }

                        // ===== Section 6: 关于 =====
                        item(key = "section_about") {
                            SectionHeader(title = "关于")
                        }
                        item(key = "check_version", contentType = { "click" }) {
                            ClickableRow(
                                icon = Icons.Filled.Verified,
                                title = "检查更新",
                                subtitle = "当前版本 v$currentVersion",
                                onClick = { updateController.manualCheck() },
                                trailingContent = if (updateController.isChecking) {
                                    {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                } else null
                            )
                        }
                        item(key = "documents", contentType = { "click" }) {
                            ClickableRow(
                                icon = Icons.AutoMirrored.Filled.Article,
                                title = "文档",
                                subtitle = "新手教程、模型方案、API 密钥获取、备份说明",
                                onClick = { showDocuments = true }
                            )
                        }
                        // 法律与隐私文档入口
                        // - 引用国内外相关法律，撇清应用与用户行为的关系
                        // - 点击某法律协议自动复制对应官方地址
                        item(key = "legal_docs", contentType = { "click" }) {
                            ClickableRow(
                                icon = Icons.Filled.Gavel,
                                title = "法律与隐私",
                                subtitle = "用户协议、隐私政策、免责声明",
                                onClick = { showLegalDocs = true }
                            )
                        }
                        item(key = "donate", contentType = { "click" }) {
                            ClickableRow(
                                icon = Icons.Filled.FavoriteBorder,
                                title = "打赏作者",
                                subtitle = "支持一下",
                                onClick = { showDonate = true }
                            )
                        }
                        item(key = "customer_service", contentType = { "info" }) {
                            CustomerServiceRow(
                                qqNumber = "JiuanShen",
                                onCopy = {
                                    clipboard.setPrimaryClip(ClipData.newPlainText("客服QQ", "JiuanShen"))
                                    toastMsg = "QQ号已复制"
                                }
                            )
                        }
                        item(key = "about_footer", contentType = { "footer" }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Quiddity v$currentVersion",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // API 编辑器叠加层（覆盖在总设置上方）
        AnimatedVisibility(
            visible = showApiEditor,
            enter = fadeIn(tween(Motion.DurationMedium)),
            exit = fadeOut(tween(Motion.DurationShort)),
            modifier = Modifier.fillMaxSize()
        ) {
            ApiCatalogEditor(viewModel = viewModel, onBack = { showApiEditor = false })
        }

        // 会话列表壁纸子面板（覆盖在总设置上方）
        AnimatedVisibility(
            visible = showListWallpaper,
            enter = fadeIn(tween(Motion.DurationMedium)),
            exit = fadeOut(tween(Motion.DurationShort)),
            modifier = Modifier.fillMaxSize()
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                ListWallpaperPanel(
                    currentWallpaperUri = settings.listWallpaperUri,
                    currentDarken = settings.listWallpaperDarken,
                    onBack = { showListWallpaper = false },
                    onWallpaperChanged = { uri -> viewModel.setListWallpaperUri(uri) },
                    onDarkenChanged = { value -> viewModel.setListWallpaperDarken(value) }
                )
            }
        }

        // 文档抽屉（覆盖在最上层，避免被设置面板遮挡）
        if (showDocuments) {
            DocumentsDrawer(
                docsProvider = docsProvider,
                catalogManager = apiCatalogManager,
                onDismiss = { showDocuments = false }
            )
        }

        // 法律与隐私文档抽屉
        if (showLegalDocs) {
            LegalDocsDrawer(onDismiss = { showLegalDocs = false })
        }

        // 打赏作者页（覆盖在最上层，与 UpdateDialog 同级）
        if (showDonate) {
            DonateScreen(onBack = { showDonate = false })
        }

        // 主动消息总开关提示弹窗（对应算法文档 2.1）：仅表知晓，确认后才保存状态
        if (showProactiveDialog) {
            ConfirmDialog(
                title = "主动消息",
                message = "为确保到点准时触发，建议同时完成：1) 将本应用的【电池优化】设为“不受限制”；" +
                    "2) Android 12+ 在【闹钟和提醒】中允许本应用使用精确闹钟；3) 允许【自启动】。下方设置项会实时显示这几项状态并提供一键跳转。" +
                    "总设置仅表示您已了解该功能，您需前往对应会话中单独开启该会话的时间库功能。",
                confirmText = "我知道了",
                cancelText = "取消",
                onConfirm = {
                    showProactiveDialog = false
                    viewModel.setProactiveMessageEnabled(true)
                },
                onDismiss = { showProactiveDialog = false }
            )
        }

        // 导入后密钥重填提示
        pendingKeyRefill?.let { names ->
            ConfirmDialog(
                title = "部分接口密钥需重新填写",
                message = "导入的备份中以下模型配置的密钥无法解密（可能来自其他设备），" +
                    "请到「模型配置」中重新填写：\n\n" + names.joinToString("\n"),
                confirmText = "知道了",
                cancelText = null,
                onConfirm = { pendingKeyRefill = null },
                onDismiss = { pendingKeyRefill = null }
            )
        }

        // 导入抉择弹窗：已有数据时让用户选择替换/合并/取消
        pendingImportPayload?.let { payload ->
            Dialog(
                onDismissRequest = { pendingImportPayload = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "导入数据",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Text(
                            text = "检测到应用已有对话数据，请选择导入方式：",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Text(
                                text = "提示：你也可以在会话内汉堡菜单中单独导入人设卡或对话记录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.size(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { pendingImportPayload = null }) {
                                Text("取消")
                            }
                            Spacer(modifier = Modifier.size(4.dp))
                            TextButton(onClick = {
                                val p = payload
                                pendingImportPayload = null
                                scope.launch {
                                    viewModel.importAllPayload(p, mode = ImportMode.MERGE)
                                    toastMsg = "导入成功（已合并）"
                                }
                            }) { Text("合并") }
                            Spacer(modifier = Modifier.size(4.dp))
                            TextButton(onClick = {
                                val p = payload
                                pendingImportPayload = null
                                scope.launch {
                                    viewModel.importAllPayload(p, mode = ImportMode.CHARACTERS_ONLY)
                                    toastMsg = "角色库已导入"
                                }
                            }) { Text("仅导入角色库") }
                            Spacer(modifier = Modifier.size(4.dp))
                            TextButton(onClick = {
                                val p = payload
                                pendingImportPayload = null
                                scope.launch {
                                    viewModel.importAllPayload(p, mode = ImportMode.REPLACE)
                                    toastMsg = "导入成功（已替换）"
                                }
                            }) { Text("替换", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }

        // 版本更新弹窗（手动检测触发）
        updateController.updateResult?.let { result ->
            UpdateDialog(
                result = result,
                onDismiss = { updateController.dismissDialog() }
            )
        }
    }
}

@Composable
private fun CenterGrabBar(
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
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 6.dp)
    )
}

/**
 * 字体大小调节行。
 *
 * 本地状态驱动拖动，仅在 [onValueChangeFinished] 时写入 ViewModel，减少 DataStore 写频率。
 * 离散步进（0.1 一档），避免连续写入与精度漂移。跟随系统字体时整行禁用。
 */
@Composable
private fun FontSizeRow(
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
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
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
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    // Box 替代 Surface：行内无 elevation 需求，Box+background+clip 跳过 Surface 的 CompositionLocalProvider 开销
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
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
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            // 切换时显示"已保存"Toast 反馈
            QuiddityToggleSwitch(
                checked = checked,
                onCheckedChange = {
                    onCheckedChange(it)
                    Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
private fun ClickableRow(
    icon: ImageVector,
    title: String,
    subtitle: String = "",
    onClick: () -> Unit,
    expandableSubtitle: Boolean = false,
    trailingContent: @Composable (() -> Unit)? = null
) {
    // Box 替代 Surface：clickable 移到 Box，避免 Surface 包裹的额外开销
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
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
