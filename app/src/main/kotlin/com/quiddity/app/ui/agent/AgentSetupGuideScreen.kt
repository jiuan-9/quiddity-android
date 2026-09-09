package com.quiddity.app.ui.agent

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
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
                        item(key = "assistant_download", contentType = { "section" }) {
                            CopyLinkRow(
                                title = "Quiddity 授权助手（电脑端）",
                                desc = "Android 8 到 10 一键授权工具：点「复制」拿到下载链接，到电脑浏览器打开即可",
                                url = "https://jiuan-9.github.io/Quiddity-website/#/assistant",
                                context = context
                            )
                        }

                        // ===== 按系统一条龙：每个系统 = 适用/开发者选项/USB调试/启动Shizuku/常见错误 =====
                        systemTutorials.forEach { system ->
                            item(key = system.title, contentType = { "system" }) {
                                AccordionSection(title = system.title, defaultExpanded = false) {
                                    SystemTutorialContent(system)
                                }
                            }
                        }

                        item(key = "permissions", contentType = { "section" }) {
                            AccordionSection(title = "权限开启（屏幕、通知、用量、Shizuku）", defaultExpanded = false) {
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
                                    desc = "Agent 用量统计与前台应用的前提",
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
                "检测不到设备：充电线或数据线损坏、USB 模式不是「传输文件」或缺少驱动 → 换数据线；选「传输文件」；装官方驱动后重新检测。",
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

/** 单个系统的完整教程数据（与「Quiddity 授权助手」各品牌页面同源）。 */
private data class SystemTutorial(
    val title: String,
    val applies: String,
    val devSteps: List<String>,
    val usbSteps: List<String>,
    val wirelessSteps: List<String>,
    val wirelessNote: String?,
    val authSteps: List<String>,
    val usbNotes: List<String> = emptyList(),
    val errors: List<Pair<String, String>>
)

/** Android 8-10 完整授权流程（与「Quiddity 授权助手」源码流程一致）。 */
private val AUTH_USB_STEPS = listOf(
    "确认手机系统版本：「设置」→「关于手机」→「Android 版本」是 8、9 或 10；11 及以上请改用「无线调试」。",
    "在电脑上运行「Quiddity 授权助手」（下载链接见本教程顶部「复制」按钮）。",
    "用数据线连接手机，USB 模式选择「传输文件」。",
    "点击「重新检测」，等待设备状态变绿「已就绪」：检测不到设备→检查数据线（要数据线不是充电线）、USB 模式「传输文件」、驱动、USB 调试是否已开；一直「未授权」→解锁手机，点「允许 USB 调试」并勾选「始终允许」；设备离线→换数据线或接口、重装驱动。",
    "点「一键授权」，工具自动完成：检查/安装 Shizuku → 打开一次应用（生成 start.sh）→ 启动服务并校验。",
    "出现「Shizuku 已运行」即完成。",
    "回到手机 Quiddity → Agent → 设置 → 权限 →「去开启」，同意 Binder 授权弹窗即可解锁进阶能力。"
)

/** Android 8-10 授权注意事项。 */
private val USB_NOTES = listOf(
    "工具内置官方 Shizuku（v13.6.0），无需自己下载；安装后会校验包名，保证官方来源。",
    "若手机已装签名不一致的旧版 Shizuku，工具会自动先卸载再重装（对应 INSTALL_FAILED_UPDATE_INCOMPATIBLE）。",
    "若提示「需要先在手机上打开一次」：说明 start.sh 未生成，手动打开一次 Shizuku 应用后再点「一键授权」。",
    "手机重启后：adb 授权与 Shizuku 服务都会失效，需重新插线、重新点「一键授权」。",
    "若提示「所选 APK 不是 Shizuku」：安装的不是官方包，请确认从官方渠道下载正确的 APK。"
)

private val COMMON_ERRORS = listOf(
    "检测不到设备" to "换数据线；USB 模式选「传输文件」；安装厂商官方驱动后重新检测。",
    "一直显示「未授权」" to "解锁手机，点「允许 USB 调试」并勾选「始终允许」，重新检测。",
    "Shizuku 已运行但仍锁定" to "打开 Quiddity → Agent → 设置 → 权限 →「去开启」，同意 Binder 授权弹窗。"
)

/** USB 授权专属错误（与授权助手错误矩阵一致）。 */
private val USB_ERRORS = COMMON_ERRORS + listOf(
    "安装失败 INSTALL_FAILED_UPDATE_INCOMPATIBLE" to "手机已装签名不一致的旧版 Shizuku → 工具会自动卸载旧版并重装一次。",
    "提示需要先在手机上打开一次" to "start.sh 未生成 → 手动打开一次 Shizuku 应用，再点「一键授权」。",
    "手机重启后启动输出为空或 permission denied" to "adb 授权丢失 → 重新检测设备，重新走一遍授权流程。"
)

private val systemTutorials = listOf(
    SystemTutorial(
        title = "原生 Android（Android 8 到 10）",
        applies = "适用于原生系统或未列出的品牌。先确认「设置」→「关于手机」→「Android 版本」是 8、9 或 10；是 11 或更高请改用「无线调试」方式。",
        devSteps = listOf(
            "打开「设置」→「关于手机」。",
            "连续点击「版本号」7 次，直到提示「您已处于开发者模式」。",
            "返回「设置」→「系统」→ 进入「开发者选项」。"
        ),
        usbSteps = listOf(
            "打开「USB 调试」，首次弹出确认窗口时点「允许」。",
            "用数据线连接电脑，USB 模式选择「传输文件」。",
            "手机弹出「允许 USB 调试？」时，勾选「始终允许使用这台计算机进行调试」并点「确定」。"
        ),
        wirelessSteps = emptyList(),
        wirelessNote = "Android 11+ 请改用 Shizuku 的「无线调试」方式，不要走 USB 流程。",
        authSteps = AUTH_USB_STEPS,
        usbNotes = USB_NOTES,
        errors = USB_ERRORS
    ),
    SystemTutorial(
        title = "MIUI 11 到 12（Android 8 到 10，小米、红米）",
        applies = "适用于仍停留在 Android 8 到 10 的 MIUI 11、12、12.5 机型。MIUI 13、14、HyperOS 均为 Android 11 及以上，请改用「无线调试」。",
        devSteps = listOf(
            "打开「设置」→「我的设备」→「全部参数与信息」。",
            "连续点击「MIUI 版本」7 次，直到提示「您已处于开发者模式」。",
            "返回「设置」→「更多设置」→ 进入「开发者选项」。"
        ),
        usbSteps = listOf(
            "打开「USB 调试」，首次弹窗点「允许」；建议同时打开「USB 安装」。",
            "用数据线连接电脑，USB 模式选择「传输文件」。",
            "手机弹出「允许 USB 调试？」时，勾选「始终允许」并点「确定」。"
        ),
        wirelessSteps = emptyList(),
        wirelessNote = "MIUI 13、14、HyperOS（Android 11 及以上）请改用「无线调试」方式。",
        authSteps = AUTH_USB_STEPS,
        usbNotes = USB_NOTES,
        errors = USB_ERRORS
    ),
    SystemTutorial(
        title = "HyperOS（澎湃OS，Android 11 及以上）",
        applies = "适用于小米、红米 HyperOS 1、2、3（Android 13、14、16）。Android 11 及以上只能走「无线调试」，不支持 USB 一键授权。",
        devSteps = listOf(
            "打开「设置」→「我的设备」→「全部参数与信息」。",
            "连续点击「HyperOS 版本」7 次，直到提示已处于开发者模式。",
            "返回「设置」→「更多设置」→ 进入「开发者选项」。"
        ),
        usbSteps = emptyList(),
        wirelessSteps = listOf(
            "打开「开发者选项」→ 打开「无线调试」。",
            "打开 Shizuku 应用 →「无线调试」→ 点「开始」。",
            "在「无线调试」中点击「使用配对码配对设备」，记下 6 位配对码。",
            "在 Shizuku 的通知中输入配对码，完成配对。",
            "返回 Shizuku 点「启动」，等待提示已运行。"
        ),
        wirelessNote = "每次手机重启后需重新启动一次（配对只需一次）。若一直「正在搜索配对服务」：允许 Shizuku 后台运行，并把通知样式切换为「Android」样式。",
        authSteps = listOf(
            "回到手机 Quiddity → Agent → 设置 → 权限 →「去开启」，同意 Binder 授权弹窗即可解锁进阶能力。"
        ),
        errors = listOf(
            "配对失败或输入配对码无效" to "配对码已过期，重新点击「使用配对码配对设备」，在 60 秒内输入。",
            "一直「正在搜索配对服务」" to "允许 Shizuku 后台运行；小米机型把通知样式改为「Android」样式。",
            "Shizuku 已运行但仍锁定" to "打开 Quiddity → Agent → 设置 → 权限 →「去开启」，同意 Binder 授权弹窗。"
        )
    ),
    SystemTutorial(
        title = "EMUI 10 与 HarmonyOS 2（Android 10，华为）",
        applies = "适用于 EMUI 10 或 HarmonyOS 2（兼容层为 Android 10）。HarmonyOS 3、4 兼容层为 Android 12，请改用「无线调试」。",
        devSteps = listOf(
            "打开「设置」→「关于手机」。",
            "连续点击「版本号」7 次，直到提示已处于开发者模式。",
            "返回「设置」→「系统和更新」→ 进入「开发人员选项」。"
        ),
        usbSteps = listOf(
            "打开「USB 调试」，首次弹窗点「允许」。",
            "用数据线连接电脑，手机弹出「允许 USB 调试？」时，勾选「始终允许」并点「确定」。"
        ),
        wirelessSteps = emptyList(),
        wirelessNote = "HarmonyOS 3、4（Android 12）请改用「无线调试」方式。",
        authSteps = AUTH_USB_STEPS,
        usbNotes = USB_NOTES,
        errors = USB_ERRORS + (
            "检测不到设备" to "换数据线；USB 模式选「传输文件」；安装华为手机助手或官方驱动后重新检测。"
            )
    ),
    SystemTutorial(
        title = "ColorOS 7 与 realme UI 1（Android 10，OPPO、一加、真我）",
        applies = "适用于 Android 10 的 ColorOS 7、realme UI 1.0。ColorOS 11+、realme UI 2+ 为 Android 11+，请改用「无线调试」。",
        devSteps = listOf(
            "打开「设置」→「关于本机」（真我机型为「关于手机」）。",
            "连续点击「版本号」7 次，直到提示已处于开发者模式。",
            "返回「设置」→「其他设置」→ 进入「开发者选项」。"
        ),
        usbSteps = listOf(
            "打开「USB 调试」，首次弹窗点「允许」。",
            "用数据线连接电脑，USB 模式选择「传输文件」。",
            "手机弹出「允许 USB 调试？」时，勾选「始终允许」并点「确定」。"
        ),
        wirelessSteps = emptyList(),
        wirelessNote = "ColorOS 11+、realme UI 2+（Android 11+）请改用「无线调试」方式。",
        authSteps = AUTH_USB_STEPS,
        usbNotes = USB_NOTES,
        errors = USB_ERRORS
    ),
    SystemTutorial(
        title = "Funtouch OS 9 到 10（vivo、iQOO）",
        applies = "适用于 Android 9 到 10 的 Funtouch OS 9、10。OriginOS 1.0 起全部基于 Android 11 及以上（如 iQOO Neo 5），请改用「无线调试」。",
        devSteps = listOf(
            "打开「设置」→「关于手机」。",
            "连续点击「软件版本号」7 次，直到提示已处于开发者模式。",
            "返回「设置」→「更多设置」→ 进入「开发者选项」。"
        ),
        usbSteps = listOf(
            "打开「USB 调试」，首次弹窗点「允许」。",
            "用数据线连接电脑，USB 模式选择「传输文件」。",
            "手机弹出「允许 USB 调试？」时，勾选「始终允许」并点「确定」。"
        ),
        wirelessSteps = emptyList(),
        wirelessNote = "OriginOS（Android 11+）请改用「无线调试」方式。",
        authSteps = AUTH_USB_STEPS,
        usbNotes = USB_NOTES,
        errors = USB_ERRORS
    ),
    SystemTutorial(
        title = "Magic UI 3.x（荣耀）",
        applies = "适用于 Android 10 的荣耀 Magic UI 3.x（如荣耀 20 系列、荣耀 V30 系列）。MagicOS 7、8 为 Android 12 及以上，请改用「无线调试」。",
        devSteps = listOf(
            "打开「设置」→「关于手机」。",
            "连续点击「版本号」7 次，直到提示已处于开发者模式。",
            "返回「设置」→「系统和更新」→ 进入「开发人员选项」。"
        ),
        usbSteps = listOf(
            "打开「USB 调试」，首次弹窗点「允许」。",
            "用数据线连接电脑，手机弹出「允许 USB 调试？」时，勾选「始终允许」并点「确定」。"
        ),
        wirelessSteps = emptyList(),
        wirelessNote = "MagicOS（Android 12+）请改用「无线调试」方式。",
        authSteps = AUTH_USB_STEPS,
        usbNotes = USB_NOTES,
        errors = USB_ERRORS
    ),
    SystemTutorial(
        title = "One UI 2.x（三星）",
        applies = "适用于 Android 10 的三星 One UI 2.x。One UI 3+ 为 Android 11+，请改用「无线调试」。",
        devSteps = listOf(
            "打开「设置」→「关于手机」→「软件信息」。",
            "连续点击「编译编号」7 次，直到提示「开发者模式已启用」。",
            "返回「设置」→ 进入「开发者选项」。"
        ),
        usbSteps = listOf(
            "打开「USB 调试」，首次弹窗点「允许」。",
            "用数据线连接电脑，手机弹出「允许 USB 调试？」时，勾选「始终允许」并点「确定」。"
        ),
        wirelessSteps = emptyList(),
        wirelessNote = "One UI 3+（Android 11+）请改用「无线调试」方式。",
        authSteps = AUTH_USB_STEPS,
        usbNotes = USB_NOTES,
        errors = USB_ERRORS + (
            "检测不到设备" to "换数据线；USB 模式选「传输文件」；安装三星 USB 驱动（Samsung USB Driver）后重新检测。"
            )
    )
)

/** 单个系统的完整教程渲染：适用 / 开发者选项 / USB 调试 / 启动 Shizuku / 常见错误。 */
@Composable
private fun SystemTutorialContent(system: SystemTutorial) {
    GuideSubTitle("适用")
    Text(
        text = system.applies,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    GuideSubTitle("① 开启开发者选项")
    StepList(system.devSteps)
    if (system.usbSteps.isNotEmpty()) {
        GuideSubTitle("② 开启 USB 调试")
        StepList(system.usbSteps)
    }
    GuideSubTitle(if (system.wirelessSteps.isNotEmpty()) "② 无线调试启动 Shizuku" else "② 启动 Shizuku")
    if (system.wirelessSteps.isNotEmpty()) {
        StepList(system.wirelessSteps)
    }
    if (system.authSteps.isNotEmpty()) {
        StepList(system.authSteps)
    }
    if (system.usbNotes.isNotEmpty()) {
        NoteText(system.usbNotes)
    }
    system.wirelessNote?.let { NoteText(listOf(it)) }
    GuideSubTitle("常见错误")
    ErrorList(system.errors)
}

/** 小节标题。 */
@Composable
private fun GuideSubTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

/** 错误列表：现象（加粗）→ 解决（次级色）。 */
@Composable
private fun ErrorList(rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { (phenomenon, solution) ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = phenomenon,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = solution,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

/** 复制链接行：只复制，不跳转。 */
@Composable
private fun CopyLinkRow(
    title: String,
    desc: String,
    url: String,
    context: Context
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "复制",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("下载链接", url))
                        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
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
                        .padding(start = 14.dp, end = 14.dp, bottom = 10.dp)
                ) {
                    // 标题与内容的细分隔线
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        content = content
                    )
                }
            }
        }
    }
}

@Composable
private fun StepList(steps: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        steps.forEachIndexed { index, step ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(28.dp)
                )
                Text(
                    text = step,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NoteText(lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.forEach { line ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(20.dp)
                )
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
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
