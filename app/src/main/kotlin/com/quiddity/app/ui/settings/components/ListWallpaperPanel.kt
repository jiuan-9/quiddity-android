package com.quiddity.app.ui.settings.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.quiddity.app.ui.components.ConfirmDialog
import com.quiddity.app.ui.components.ImageCropper
import com.quiddity.app.util.CrashLogger
import com.quiddity.app.util.IdGenerator
import com.quiddity.app.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * 设计要点：
 * - 全局设置：应用于 HomeScreen 的背景，所有会话共享
 * - 选择图片后按屏幕宽高比裁剪（与会话级壁纸同源方案）
 * - 壁纸文件持久化到 filesDir/list_wallpapers/
 * - 暗化程度可调（0.0f - 1.0f）
 * - 可通过数据导出/导入迁移（DataPorter 处理）
 *
 * 与对话级壁纸的区别：
 * - 对话级壁纸存在 Conversation.wallpaperUri，每个会话独立
 * - 列表壁纸存在 AppSettings.listWallpaperUri，全局唯一
 * - 两者独立设置，互不影响
 *
 * @param currentWallpaperUri 当前列表壁纸 URI（null=未设置）
 * @param currentDarken 当前暗化程度
 * @param onBack 返回总设置
 * @param onWallpaperChanged 壁纸变更回调
 * @param onDarkenChanged 暗化程度变更回调
 */
@Composable
fun ListWallpaperPanel(
    currentWallpaperUri: String?,
    currentDarken: Float,
    onBack: () -> Unit,
    onWallpaperChanged: (uri: String?) -> Unit,
    onDarkenChanged: (value: Float) -> Unit
) {
    var localDarken by remember(currentDarken) { mutableFloatStateOf(currentDarken) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var isCopying by remember { mutableStateOf(false) }
    var copyError by remember { mutableStateOf<String?>(null) }
    var croppingUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    // 屏幕宽高比（宽/高），用于壁纸裁剪框比例
    val screenAspectRatio = configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat()

    // 选择图片 → 复制到内部存储 → 裁剪 → 应用
    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isCopying = true
            copyError = null
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        ImageUtils.copyToInternalStorage(context, uri, subdir = "list_wallpapers")
                    }
                }
                result.onSuccess { internalUri ->
                    croppingUri = internalUri
                }.onFailure { err ->
                    CrashLogger.logException(
                        context, err, "ListWallpaperPanel.copyToInternal"
                    )
                    copyError = "图片加载失败：${err.message ?: "未知错误"}"
                }
                isCopying = false
            }
        }
    }

    // 裁剪界面（全屏 Dialog）
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
                outputName = "list_wallpaper",
                onCropComplete = { croppedUri ->
                    // 删除旧列表壁纸文件
                    currentWallpaperUri?.let { oldPath ->
                        val oldFile = File(oldPath.removePrefix("file://"))
                        if (oldFile.exists() && oldFile.parentFile?.name == "list_wallpapers") {
                            runCatching { oldFile.delete() }
                        }
                    }
                    onWallpaperChanged(croppedUri.toString())
                    croppingUri = null
                    ImageUtils.deleteTempFile(uri)
                },
                onCancel = {
                    croppingUri = null
                    ImageUtils.deleteTempFile(uri)
                },
                aspectRatio = screenAspectRatio,
                outputSubdir = "list_wallpapers",
                outputWidth = (1920 * screenAspectRatio).roundToInt().coerceIn(720, 1920),
                outputHeight = 1920,
                title = "裁剪列表壁纸"
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 头部：返回 + 标题
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = "会话列表壁纸",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Spacer(modifier = Modifier.size(16.dp))

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = localDarken))
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
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // 暗化程度滑块
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
                ListWallpaperActionButton(
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
                ListWallpaperActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Refresh,
                    label = "恢复默认",
                    enabled = currentWallpaperUri != null,
                    onClick = { showClearConfirm = true }
                )
            }

            Text(
                text = "提示：列表壁纸应用于会话列表界面，所有会话共享。会话条目将以毛玻璃质感叠加在壁纸上。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }

    if (showClearConfirm) {
        ConfirmDialog(
            title = "清除列表壁纸",
            message = "将移除会话列表界面的壁纸设置。",
            confirmText = "清除",
            onConfirm = {
                currentWallpaperUri?.let { oldPath ->
                    val oldFile = File(oldPath.removePrefix("file://"))
                    if (oldFile.exists() && oldFile.parentFile?.name == "list_wallpapers") {
                        runCatching { oldFile.delete() }
                    }
                }
                onWallpaperChanged(null)
                localDarken = 0f
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false }
        )
    }
}

@Composable
private fun ListWallpaperActionButton(
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
