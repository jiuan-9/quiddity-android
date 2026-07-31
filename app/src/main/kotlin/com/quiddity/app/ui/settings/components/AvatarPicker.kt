package com.quiddity.app.ui.settings.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
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

/**
 * 圆形头像选择器（96dp）。
 *
 * 点击触发 PickVisualMedia（仅图片）。选定后先进入标准化裁剪界面，
 * 裁剪完成的 Uri 通过 [onPicked] 回调返回。
 */
@Composable
fun AvatarPicker(
    avatarUri: String?,
    onPicked: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 临时状态：正在将外部 URI 复制到内部存储
    var isCopying by remember { mutableStateOf(false) }
    var copyError by remember { mutableStateOf<String?>(null) }
    // 裁剪界面绑定的 URI（已复制到内部存储的 file:// URI）
    var croppingUri by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isCopying = true
            copyError = null
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        ImageUtils.copyToInternalStorage(context, uri)
                    }
                }
                result.onSuccess { internalUri ->
                    croppingUri = internalUri
                }.onFailure { err ->
                    CrashLogger.logException(
                        context,
                        err,
                        "AvatarPicker.copyToInternalStorage"
                    )
                    copyError = "图片加载失败：${err.message ?: "未知错误"}"
                }
                isCopying = false
            }
        }
    }

    // 头像裁剪界面（全屏 Dialog）
    croppingUri?.let { uri ->
        Dialog(
            onDismissRequest = { croppingUri = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            ImageCropper(
                imageUri = uri,
                outputName = "user_avatar",
                onCropComplete = { croppedUri ->
                    onPicked(croppedUri.toString())
                    croppingUri = null
                    // 清理临时源文件
                    ImageUtils.deleteTempFile(uri)
                },
                onCancel = {
                    croppingUri = null
                    // 清理临时源文件
                    ImageUtils.deleteTempFile(uri)
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                launcher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            isCopying -> {
                // 复制图片到内部存储期间显示 loading
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            avatarUri != null -> {
                AsyncImage(
                    model = avatarUri,
                    contentDescription = "头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(96.dp).clip(CircleShape)
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = "默认头像",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        // 右下角相机图标（小）
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.AddAPhoto,
                contentDescription = "更换头像",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(14.dp)
            )
        }

        // 复制失败时显示错误提示（短暂覆盖在头像上）
        copyError?.let { err ->
            LaunchedEffect(err) {
                kotlinx.coroutines.delay(2500)
                copyError = null
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clip(CircleShape)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = err,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
