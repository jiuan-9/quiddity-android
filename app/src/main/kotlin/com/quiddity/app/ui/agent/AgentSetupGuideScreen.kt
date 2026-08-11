package com.quiddity.app.ui.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quiddity.app.ui.theme.Motion
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
 * Agent 权限教程：底部弹层（与总设置子页面一致），按系统分类、权限集中、全部可展开。
 * 文案与「Quiddity 授权助手」docs/tutorials-src 保持同一来源，避免两份教程不一致。
 */
@Composable
fun AgentSetupGuideSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val screenHeightPx = with(LocalDensity.current) { screenHeight.toPx() }
    val dragOffsetYState = remember { mutableFloatStateOf(0f) }
    val dismissThreshold = screenHeightPx * 0.2f
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    fun dismiss() {
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
                        onClick = { dismiss() }
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
                    .heightIn(max = screenHeight * 0.85f)
                    .fillMaxHeight()
                    .graphicsLayer {
                        translationY = dragOffsetYState.floatValue.coerceAtLeast(0f)
                    },
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
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
                    GuideGrabBar(
                        dragOffsetYState = dragOffsetYState,
                        dismissThreshold = dismissThreshold,
                        onClose = { dismiss() }
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 32.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item(key = "brands", contentType = { "section" }) {
                            AccordionSection(title = "按系统开启「开发者选项」", defaultExpanded = false) {
                                BrandEntryRow("MIUI 11 / 12（Android 8-10）", "设置 → 我的设备 → 全部参数与信息 → 连点「MIUI 版本」7 次")
                                BrandEntryRow("HyperOS（Android 11+）", "设置 → 我的设备 → 全部参数与信息 → 连点「HyperOS 版本」7 次")
                                BrandEntryRow("EMUI 10 / HarmonyOS 2", "设置 → 关于手机 → 连点「版本号」7 次")
                                BrandEntryRow("ColorOS 7 / realme UI 1", "设置 → 关于本机 → 连点「版本号」7 次")
                                BrandEntryRow("Funtouch OS 9 / 10", "设置 → 关于手机 → 连点「软件版本号」7 次")
                                BrandEntryRow("Magic UI 3.x", "设置 → 关于手机 → 连点「版本号」7 次")
                                BrandEntryRow("One UI 2.x", "设置 → 关于手机 → 软件信息 → 连点「编译编号」7 次")
                                BrandEntryRow("原生 Android", "设置 → 关于手机 → 连点「版本号」7 次")
                            }
                        }

                        item(key = "wireless", contentType = { "section" }) {
                            AccordionSection(title = "Android 11+：无线调试启动 Shizuku", defaultExpanded = false) {
                                StepList(
                                    listOf(
                                        "打开手机「设置」→「开发者选项」→ 打开「无线调试」。",
                                        "打开 Shizuku 应用 →「无线调试」→ 点「开始」。",
                                        "在「无线调试」中点击「使用配对码配对设备」，记下 6 位配对码。",
                                        "在 Shizuku 的通知中输入配对码，完成配对。",
                                        "返回 Shizuku 点「启动」，等待提示已运行。"
                                    )
                                )
                                NoteText(
                                    listOf(
                                        "每次手机重启后需重新启动一次（配对只需一次）。",
                                        "若一直「正在搜索配对服务」：允许 Shizuku 后台运行；小米机型把通知样式改为「Android」样式。",
                                        "配对失败或输入配对码无效：配对码已过期，重新配对并在 60 秒内输入。"
                                    )
                                )
                            }
                        }

                        item(key = "pc", contentType = { "section" }) {
                            AccordionSection(title = "Android 8-10：USB + 授权助手", defaultExpanded = false) {
                                StepList(
                                    listOf(
                                        "先开启「开发者选项」（入口见上方各系统对照表）。",
                                        "在「开发者选项」中打开「USB 调试」，首次弹窗点「允许」；建议同时打开「USB 安装」。",
                                        "用数据线连接电脑，USB 模式选择「传输文件」。",
                                        "手机弹出「允许 USB 调试？」时，勾选「始终允许使用这台计算机进行调试」，点「确定」。",
                                        "在电脑上打开「Quiddity 授权助手」，点「一键授权」，工具会自动安装并启动 Shizuku。"
                                    )
                                )
                            }
                        }

                        item(key = "permissions", contentType = { "section" }) {
                            AccordionSection(title = "权限开启（屏幕 / 通知 / 用量 / Shizuku）", defaultExpanded = true) {
                                PermissionJumpRow(
                                    label = "无障碍（读屏）",
                                    desc = "Agent 读取屏幕文字的前提",
                                    action = Settings.ACTION_ACCESSIBILITY_SETTINGS,
                                    context = context
                                )
                                PermissionJumpRow(
                                    label = "通知使用权",
                                    desc = "Agent 读取通知的前提",
                                    action = Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
                                    context = context
                                )
                                PermissionJumpRow(
                                    label = "使用情况访问",
                                    desc = "Agent 用量统计 / 前台应用的前提",
                                    action = Settings.ACTION_USAGE_ACCESS_SETTINGS,
                                    context = context
                                )
                                UrlJumpRow(
                                    label = "Shizuku 下载安装",
                                    desc = "官方直装 APK（全中文页面）",
                                    url = "https://jiuan-9.github.io/Quiddity-website/downloads/shizuku.apk",
                                    context = context
                                )
                                NoteText(
                                    listOf(
                                        "Shizuku：Android 11+ 用上方「无线调试」启动；Android 8-10 用「授权助手」。",
                                        "启动后回到 App → Agent → 设置 → 权限 →「去开启」，同意 Binder 授权弹窗即可解锁进阶能力。"
                                    )
                                )
                            }
                        }

                        item(key = "errors", contentType = { "section" }) {
                            AccordionSection(title = "常见错误速查", defaultExpanded = false) {
                                StepList(
                                    listOf(
                                        "检测不到设备：充电线/数据线损坏、USB 模式不是「传输文件」或缺少驱动 → 换数据线；选「传输文件」；装官方驱动后重新检测。",
                                        "一直显示「未授权」：手机锁屏或授权弹窗被关闭 → 解锁手机，点「允许 USB 调试」并勾选「始终允许」。",
                                        "安装失败 INSTALL_FAILED_UPDATE_INCOMPATIBLE：已装签名不一致的旧版 → 先卸载旧版再重装。",
                                        "提示需要先在手机上打开一次：从未打开过 Shizuku → 在手机上打开一次 Shizuku 应用。",
                                        "Shizuku 已运行但仍锁定：未授予本 App 的 Binder 权限 → Agent 设置 → 权限 →「去开启」。"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 下载/网页跳转行。 */
@Composable
private fun UrlJumpRow(
    label: String,
    desc: String,
    url: String,
    context: Context
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "去下载",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }.onFailure {
                        Toast.makeText(context, "无法打开下载页面", Toast.LENGTH_SHORT).show()
                    }
                }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

/** 展开式分组：点头部展开/收起。 */
@Composable
private fun AccordionSection(
    title: String,
    defaultExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(defaultExpanded) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { expanded = !expanded }
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun StepList(steps: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        steps.forEachIndexed { index, step ->
            Text(
                text = "${index + 1}. $step",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun NoteText(lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { line ->
            Text(
                text = "• $line",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BrandEntryRow(brand: String, entry: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = brand,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(max = 150.dp)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = entry,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

/** 权限行：说明 + 直接跳系统设置。 */
@Composable
private fun PermissionJumpRow(
    label: String,
    desc: String,
    action: String,
    context: Context
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = "去开启",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    runCatching { context.startActivity(Intent(action)) }
                        .onFailure { Toast.makeText(context, "无法打开系统设置", Toast.LENGTH_SHORT).show() }
                }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun GuideGrabBar(
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
                                val anim = androidx.compose.animation.core.Animatable(dragOffsetYState.floatValue)
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
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
        )
        Text(
            text = "权限开启教程",
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
