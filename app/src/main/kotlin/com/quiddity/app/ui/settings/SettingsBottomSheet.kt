package com.quiddity.app.ui.settings

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Thermostat
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.quiddity.app.di.ServiceLocator
import com.quiddity.app.data.model.ImportMode
import com.quiddity.app.data.model.ImportPlan
import com.quiddity.app.ui.components.ActiveMessagePermissionCard
import com.quiddity.app.ui.components.ConfirmDialog
import com.quiddity.app.ui.components.ExpandableText
import com.quiddity.app.ui.components.QuiddityToggleSwitch
import com.quiddity.app.ui.components.TemperatureSlider
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
import com.quiddity.app.ui.settings.components.VisionCatalogEditor
import com.quiddity.app.ui.theme.Motion
import com.quiddity.app.util.DataPorter
import com.quiddity.app.util.IdGenerator
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
// 当前规则：80% 屏幕高度从底部滑入；rememberSaveable 保留子页面状态。
// 顶部抓手可拖动关闭：拖动时整个面板 translationY 实时跟随手指（1:1），
// 超过阈值（屏幕高度 20%）则关闭，否则回弹。
// 悬浮窗授权跳转超时窗口：超过该时长视为非本次返回，不再自动开启悬浮窗
private const val OVERLAY_REQUEST_WINDOW_MS = 5 * 60 * 1000L

@Composable
fun SettingsBottomSheet(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val settingsError by viewModel.errorEvent.collectAsStateWithLifecycle()
    val settingsToast by viewModel.toastEvent.collectAsStateWithLifecycle()
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

