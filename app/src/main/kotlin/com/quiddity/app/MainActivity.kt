package com.quiddity.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.quiddity.app.active.OperationNotifyController
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.di.ServiceLocator
import com.quiddity.app.ui.navigation.QuiddityRoute
import com.quiddity.app.ui.navigation.QuiddityNavHost
import com.quiddity.app.ui.theme.QuiddityTheme
import com.quiddity.app.ui.components.AppLaunchIntro
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
// 当前规则：仅承载 Compose 根容器；状态栏图标颜色跟随应用主题。
class MainActivity : ComponentActivity() {

    /** 通知深链待消费的会话路由（主动消息 / 行动弹窗点击后直接进入对应会话框）。 */
    private var pendingConversationRoute by androidx.compose.runtime.mutableStateOf<String?>(null)

    /** Android 13+ 通知权限申请（首次行动弹窗被系统静默丢弃时触发）。 */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 授权结果无需处理 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // 行动通知弹窗宿主：Agent 执行行动类工具时由应用发系统通知弹窗说明正在进行的操作
        OperationNotifyController.attach(this)
        OperationNotifyController.onRequestNotificationPermission = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        pendingConversationRoute = conversationRouteFromIntent(intent)
        // 全局沉浸式（内容延伸到系统栏，壁纸/背景铺满全屏）。
        // 键盘处理在聊天页内完成：窗口可见区域测量键盘高度 + 内容平滑跟随（见 ChatScreen）。
        enableEdgeToEdge()
        // adjustResize：确保键盘弹起时窗口可见区域（visibleFrame）正确缩小，
        // 应用据此测量键盘高度，不依赖 ROM 的 IME insets 派发。
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

