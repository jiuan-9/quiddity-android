package com.quiddity.app.ui.chat.components.panels

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.quiddity.app.util.IdGenerator
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.quiddity.app.ui.components.ImageCropper
import com.quiddity.app.util.CrashLogger
import com.quiddity.app.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

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
 * 设计要点：
 * - 每个会话独立壁纸：URI 存储在 [com.quiddity.app.data.model.Conversation.wallpaperUri]
 * - 用户可"选择图片" / "清除" / 调整暗化程度
 * - 选择图片后**先进入裁剪界面**（按屏幕宽高比裁剪），确认后才应用为壁纸
 * - 暗化程度 0.0f - 1.0f，默认 0.0f（原始亮度，用户可手动调暗）
 * - 仅当前会话生效：调用 [onWallpaperChanged] 写回当前会话的 wallpaperUri
 * - 裁剪比例使用设备屏幕实际宽高比（screenWidthDp / screenHeightDp），
 *   确保裁剪结果与屏幕比例一致，ContentScale.Crop 时不会裁切关键内容。
 * - 输出分辨率 1080×1920（或按屏幕比例等比缩放），兼顾清晰度与内存。
 */
@Composable
fun WallpaperPanel(
    currentWallpaperUri: String?,
    currentDarken: Float,
    onBack: () -> Unit,
    onWallpaperChanged: (uri: String?) -> Unit,
    onDarkenChanged: (value: Float) -> Unit
) {
    // 临时暗化值：滑块在拖动时实时显示，松开后回调上层
    var localDarken by remember(currentDarken) { mutableFloatStateOf(currentDarken) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var isCopying by remember { mutableStateOf(false) }
    var copyError by remember { mutableStateOf<String?>(null) }
    // 裁剪界面绑定的 URI（已复制到内部存储的 file:// URI）
    var croppingUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    // 屏幕宽高比（宽/高），用于壁纸裁剪框比例
    val screenAspectRatio = configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat()

    // 处理流程：
    // 1. 选择图片 → 立即复制到内部存储（file:// URI，无权限依赖）
    // 2. 打开 ImageCropper 按屏幕宽高比裁剪
    // 3. 裁剪确认 → 删除旧壁纸文件 → 应用新壁纸 URI
    // 4. 裁剪取消 → 清理临时源文件
    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isCopying = true
            copyError = null
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        ImageUtils.copyToInternalStorage(context, uri, subdir = "wallpapers")
                    }
                }
                result.onSuccess { internalUri ->
                    croppingUri = internalUri
                }.onFailure { err ->
                    CrashLogger.logException(
                        context, err, "WallpaperPanel.copyToInternal"
                    )
                    copyError = "图片加载失败：${err.message ?: "未知错误"}"
                }
                isCopying = false
            }
        }
    }

    // 裁剪界面（全屏 Dialog，与 AvatarPicker 同模式）
    croppingUri?.let { uri ->
        Dialog(
            onDismissRequest = {
                croppingUri = null
                ImageUtils.deleteTempFile(uri)
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            ImageCropper(
                imageUri = uri,
                outputName = "wallpaper_${IdGenerator.newUuid()}",
                onCropComplete = { croppedUri ->
                    // 删除旧壁纸文件（如果有且在 wallpapers 目录下）
                    currentWallpaperUri?.let { oldPath ->
                        val oldFile = File(oldPath.removePrefix("file://"))
                        if (oldFile.exists() && oldFile.parentFile?.name == "wallpapers") {
                            runCatching { oldFile.delete() }
                        }
                    }
                    onWallpaperChanged(croppedUri.toString())
                    croppingUri = null
                    // 清理临时源文件
                    ImageUtils.deleteTempFile(uri)
                },
                onCancel = {
                    croppingUri = null
                    // 清理临时源文件
                    ImageUtils.deleteTempFile(uri)
                },
                // 按屏幕宽高比裁剪，确保壁纸与屏幕比例一致
                aspectRatio = screenAspectRatio,
                outputSubdir = "wallpapers",
                // 输出分辨率：按屏幕比例等比缩放，长边限制 1920 兼顾清晰度与内存
                outputWidth = (1920 * screenAspectRatio).roundToInt().coerceIn(720, 1920),
                outputHeight = 1920,
                title = "裁剪壁纸"
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        SubPanelScaffold(title = "会话壁纸", onBack = onBack) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 预览区
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    when {
                        isCopying -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        strokeWidth = 3.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "正在加载图片...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        currentWallpaperUri != null -> {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = currentWallpaperUri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // 暗化遮罩预览
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Color.Black.copy(alpha = localDarken)
                                        )
                                )
                            }
                        }
                        else -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = copyError ?: "当前无壁纸\n点击下方选择图片",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (copyError != null)
                                        MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // 暗化程度滑块（仅在有壁纸时可调）
                if (currentWallpaperUri != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "暗化程度",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${(localDarken * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Slider(
                            value = localDarken,
                            onValueChange = { localDarken = it },
                            valueRange = 0f..1f,
                            onValueChangeFinished = { onDarkenChanged(localDarken) }
                        )
                    }
                }

                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionTileButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.Image,
                        label = "选择图片",
                        onClick = {
                            pickMedia.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                    )
                    ActionTileButton(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.Refresh,
                        label = "恢复默认",
                        enabled = currentWallpaperUri != null,
                        onClick = { showClearConfirm = true }
                    )
                }

                Text(
                    text = "提示：壁纸仅对当前会话生效，不影响其他会话。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }

    if (showClearConfirm) {
        com.quiddity.app.ui.components.ConfirmDialog(
            title = "清除会话壁纸",
            message = "将移除当前会话的壁纸设置。此操作不影响其他会话。",
            confirmText = "清除",
            onConfirm = {
                // 删除内部存储中的壁纸文件，避免存储膨胀
                currentWallpaperUri?.let { oldPath ->
                    val oldFile = File(oldPath.removePrefix("file://"))
                    if (oldFile.exists() && oldFile.parentFile?.name == "wallpapers") {
                        runCatching { oldFile.delete() }
                    }
                }
                onWallpaperChanged(null)
                // 同步重置持久化的暗化值，避免下次换壁纸时滑块显示与实际渲染不一致
                onDarkenChanged(0f)
                localDarken = 0f
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false }
        )
    }
}

@Composable
private fun ActionTileButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        color = if (enabled) MaterialTheme.colorScheme.surfaceContainerLow
        else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}
