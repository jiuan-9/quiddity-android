package com.quiddity.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quiddity.app.ui.theme.Motion
import com.quiddity.app.util.UpdateChecker
import kotlinx.coroutines.launch

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
 * 版本更新弹窗。
 *
 * 功能：
 * - 显示新版本号、发布日期、更新说明
 * - "前往下载"按钮：打开浏览器跳转下载页
 * - "本次不再提醒"按钮：记录当前远程版本到 SharedPreferences，后续不再弹窗
 * - "稍后"按钮：仅关闭弹窗，下次启动仍会检测
 *
 * @param result 检测结果（仅 UpdateAvailable 时显示弹窗）
 * @param onDismiss 关闭回调
 */
@Composable
fun UpdateDialog(
    result: UpdateChecker.Result.UpdateAvailable,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .padding(horizontal = 24.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // 阻止点击穿透
                ),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 顶部图标
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.size(16.dp))

                Text(
                    text = "发现新版本",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.size(8.dp))

                // 版本号对比
                Text(
                    text = "v${result.currentVersion} → v${result.remoteVersion}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )

                if (result.releaseDate.isNotBlank()) {
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = "发布日期：${result.releaseDate}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                // 更新说明
                if (result.releaseNotes.isNotBlank()) {
                    Spacer(modifier = Modifier.size(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Text(
                            text = result.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                                .height(maxOf(80.dp, 0.dp))
                        )
                    }
                }

                Spacer(modifier = Modifier.size(20.dp))

                // 按钮区
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // "本次不再提醒"
                    TextButton(
                        onClick = {
                            UpdateChecker.dismissVersion(context, result.remoteVersion)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("本次不再提醒", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // "前往下载"
                    TextButton(
                        onClick = {
                            UpdateChecker.openDownloadPage(context, result.downloadUrl)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("前往下载", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * 版本检测控制器。
 *
 * 封装自动检测 + 手动检测逻辑，暴露给顶层 Composable 使用。
 *
 * 使用方式：
 * ```
 * val updateController = rememberUpdateController()
 * updateController.autoCheck()  // 启动时自动检测
 * updateController.manualCheck() // 用户点击"检查更新"时手动检测
 * ```
 */
@Composable
fun rememberUpdateController(): UpdateController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember { UpdateController(context, scope) }
}

/**
 * 版本检测控制器实例。
 *
 * 持有检测状态（StateFlow），UI 据此渲染弹窗/Toast。
 */
class UpdateController(
    private val context: android.content.Context,
    private val scope: kotlinx.coroutines.CoroutineScope
) {
    /** 当前检测结果（仅 UpdateAvailable 时显示弹窗）。 */
    var updateResult by mutableStateOf<UpdateChecker.Result.UpdateAvailable?>(null)
        private set

    /** 手动检测时的"检测中"状态。 */
    var isChecking by mutableStateOf(false)
        private set

    /** 手动检测的反馈消息（"已是最新版"/"检测失败"等）。 */
    var toastMessage by mutableStateOf<String?>(null)
        private set

    /** 已消费 toast（UI 显示后调用）。 */
    fun consumeToast() {
        toastMessage = null
    }

    /** 关闭弹窗。 */
    fun dismissDialog() {
        updateResult = null
    }

    /**
     * 自动检测（启动时调用）。
     * - 静默模式：检测失败不提示
     * - 仅在有更新且未被忽略时弹窗
     */
    fun autoCheck() {
        scope.launch {
            // 延迟 2 秒，避免影响启动速度
            kotlinx.coroutines.delay(2000)
            val result = UpdateChecker.checkForUpdates(context, forceCheck = false)
            if (result is UpdateChecker.Result.UpdateAvailable) {
                updateResult = result
            }
            // 自动检测时不显示"已是最新版"或错误 toast
        }
    }

    /**
     * 手动检测（用户点击"检查版本"时调用）。
     * - 强制模式：忽略"已忽略版本"
     */
    fun manualCheck() {
        scope.launch {
            isChecking = true
            try {
                val result = UpdateChecker.checkForUpdates(context, forceCheck = true)
                when (result) {
                    is UpdateChecker.Result.UpdateAvailable -> {
                        updateResult = result
                    }
                    is UpdateChecker.Result.UpToDate -> {
                        toastMessage = "当前已是最新版本 v${result.currentVersion}"
                    }
                    is UpdateChecker.Result.Error -> {
                        toastMessage = "检查更新失败：${result.message}"
                    }
                }
            } finally {
                isChecking = false
            }
        }
    }
}
