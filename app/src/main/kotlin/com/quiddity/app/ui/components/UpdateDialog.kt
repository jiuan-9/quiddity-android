package com.quiddity.app.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.quiddity.app.ui.theme.Motion
import com.quiddity.app.util.UpdateChecker
import kotlinx.coroutines.launch
import java.io.File

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
 *    除此之外，禁止出现任何形式的代码注释（包括但不限于单行注释、多行调试注释等）。
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
 * - "立即下载"按钮：解析 APK 直链 → 系统 DownloadManager 下载 → 进度展示 → 自动触发安装
 * - "浏览器下载"兜底：解析失败时退化为浏览器跳转
 * - "本次不再提醒"按钮：记录当前远程版本到 SharedPreferences，后续不再弹窗
 * - 下载中可"取消"
 *
 * @param result 检测结果（仅 UpdateAvailable 时显示弹窗）
 * @param onDismiss 关闭回调
 * @param onOpenBrowser 浏览器兜底回调（默认走 [UpdateChecker.openDownloadPage]）
 */
@Composable
fun UpdateDialog(
    result: UpdateChecker.Result.UpdateAvailable,
    onDismiss: () -> Unit,
    onOpenBrowser: (Context, String) -> Unit = { ctx, url -> UpdateChecker.openDownloadPage(ctx, url) }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var phase by remember { mutableStateOf<DownloadPhase>(DownloadPhase.Idle) }
    var currentDownloadId by remember { mutableStateOf(0L) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) {
                if (currentDownloadId > 0) {
                    UpdateChecker.cancelDownload(context, currentDownloadId)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (phase !is DownloadPhase.Downloading && phase !is DownloadPhase.Resolving) {
                        onDismiss()
                    }
                }
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
                    onClick = {}
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

                val showProgress = phase is DownloadPhase.Downloading || phase is DownloadPhase.Resolving
                AnimatedVisibility(
                    visible = showProgress,
                    enter = fadeIn(tween(Motion.DurationShort)),
                    exit = fadeOut(tween(Motion.DurationShort))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(modifier = Modifier.size(12.dp))
                        when (val p = phase) {
                            is DownloadPhase.Resolving -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = "正在解析下载链接…",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            is DownloadPhase.Downloading -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "下载中",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${p.percent}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.size(4.dp))
                                LinearProgressIndicator(
                                    progress = { p.fraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                            }
                            else -> Unit
                        }
                    }
                }

                Spacer(modifier = Modifier.size(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (val p = phase) {
                        is DownloadPhase.Idle, is DownloadPhase.Failed -> {
                            TextButton(
                                onClick = {
                                    UpdateChecker.dismissVersion(context, result.remoteVersion)
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "本次不再提醒",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = {
                                    if (p is DownloadPhase.Failed) {
                                        phase = DownloadPhase.Idle
                                    }
                                    startDownload(
                                        context = context,
                                        scope = scope,
                                        result = result,
                                        onPhase = { phase = it },
                                        onDownloadId = { currentDownloadId = it },
                                        onOpenBrowser = onOpenBrowser
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    if (p is DownloadPhase.Failed) "重试" else "立即下载",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        is DownloadPhase.Resolving -> {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("关闭", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "解析中…",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                        is DownloadPhase.Downloading -> {
                            TextButton(
                                onClick = {
                                    if (currentDownloadId > 0) {
                                        UpdateChecker.cancelDownload(context, currentDownloadId)
                                    }
                                    currentDownloadId = 0L
                                    phase = DownloadPhase.Idle
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "${p.percent}%",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        is DownloadPhase.Ready -> {
                            TextButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("稍后", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(
                                onClick = {
                                    if (!UpdateChecker.canRequestPackageInstalls(context)) {
                                        Toast.makeText(
                                            context,
                                            "请先允许安装未知来源应用",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        UpdateChecker.openInstallUnknownAppsSettings(context)
                                        return@TextButton
                                    }
                                    val ok = if (p.localPath != null) {
                                        UpdateChecker.installApk(context, File(p.localPath))
                                    } else {
                                        UpdateChecker.installApk(context, p.downloadId)
                                    }
                                    if (!ok) {
                                        Toast.makeText(
                                            context,
                                            "无法启动安装器，请重试",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        onDismiss()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "立即安装",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun startDownload(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    result: UpdateChecker.Result.UpdateAvailable,
    onPhase: (DownloadPhase) -> Unit,
    onDownloadId: (Long) -> Unit,
    onOpenBrowser: (Context, String) -> Unit
) {
    scope.launch {
        onPhase(DownloadPhase.Resolving)
        val apkUrl = UpdateChecker.resolveApkUrl(result.downloadUrl)
        if (apkUrl.isNullOrBlank()) {
            onOpenBrowser(context, result.downloadUrl)
            onPhase(DownloadPhase.Idle)
            return@launch
        }
        val fileName = "quiddity-${result.remoteVersion}.apk"
        UpdateChecker.downloadApkDirect(context, apkUrl, fileName).collect { progress ->
            when (progress.status) {
                UpdateChecker.DownloadStatus.SUCCESSFUL -> {
                    onPhase(DownloadPhase.Ready(downloadId = 0, localPath = progress.localUri))
                }
                UpdateChecker.DownloadStatus.FAILED -> {
                    onPhase(DownloadPhase.Failed(progress.reason.ifBlank { "下载失败" }))
                }
                else -> {
                    onPhase(
                        DownloadPhase.Downloading(
                            percent = progress.percent,
                            fraction = if (progress.totalBytes > 0) {
                                (progress.bytesDownloaded.toFloat() / progress.totalBytes.toFloat())
                                    .coerceIn(0f, 1f)
                            } else 0f
                        )
                    )
                }
            }
        }
    }
}

sealed class DownloadPhase {
    object Idle : DownloadPhase()
    object Resolving : DownloadPhase()
    data class Downloading(val percent: Int, val fraction: Float) : DownloadPhase()
    data class Ready(val downloadId: Long, val localPath: String? = null) : DownloadPhase()
    data class Failed(val message: String) : DownloadPhase()
}

/**
 * 版本检测控制器。
 *
 * 封装自动检测 + 手动检测逻辑，暴露给顶层 Composable 使用。
 */
@Composable
fun rememberUpdateController(): UpdateController {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember { UpdateController(context, scope) }
}

class UpdateController(
    private val context: android.content.Context,
    private val scope: kotlinx.coroutines.CoroutineScope
) {
    var updateResult by mutableStateOf<UpdateChecker.Result.UpdateAvailable?>(null)
        private set

    var isChecking by mutableStateOf(false)
        private set

    var toastMessage by mutableStateOf<String?>(null)
        private set

    fun consumeToast() {
        toastMessage = null
    }

    fun dismissDialog() {
        updateResult = null
    }

    fun autoCheck() {
        // 并发防护：同一时刻只允许一个检查在跑；弹窗已在展示时不重复触发
        if (isChecking || updateResult != null) return
        scope.launch {
            isChecking = true
            try {
                kotlinx.coroutines.delay(2000)
                val result = UpdateChecker.checkForUpdates(context, forceCheck = false)
                if (result is UpdateChecker.Result.UpdateAvailable) {
                    updateResult = result
                }
            } finally {
                isChecking = false
            }
        }
    }

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
