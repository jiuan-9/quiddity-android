package com.quiddity.app.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import android.os.SystemClock
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.quiddity.app.di.ServiceLocator
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.ui.agent.AgentChatScreen
import com.quiddity.app.ui.components.UpdateDialog
import com.quiddity.app.ui.components.rememberUpdateController
import com.quiddity.app.ui.chat.ChatScreen
import com.quiddity.app.ui.chat.ChatViewModel
import com.quiddity.app.ui.chat.ChatViewModelHost
import com.quiddity.app.ui.chat.ChatViewModelHostFactory
import com.quiddity.app.ui.home.HomeScreen
import com.quiddity.app.ui.home.HomeViewModel
import com.quiddity.app.ui.home.HomeViewModelFactory
import com.quiddity.app.ui.miniapps.MiniAppHost
import com.quiddity.app.ui.miniapps.MiniAppLaunchGate
import com.quiddity.app.ui.miniapps.MiniAppMissingScreen
import com.quiddity.app.ui.miniapps.MiniAppRegistry
import com.quiddity.app.ui.miniapps.MiniAppsCenterScreen
import com.quiddity.app.ui.settings.SettingsViewModel
import com.quiddity.app.ui.settings.SettingsViewModelFactory
import com.quiddity.app.ui.theme.Motion
// 当前规则（手势驱动式滑动返回）：
// - 进入会话：Chat 从右滑入 400ms（slideInHorizontally），主页在底层被自然覆盖（exit=None）。
// - 退出会话：ChatScreen 内部 ChatDragController 驱动整个屏幕 1:1 跟手滑出（含背景），
//   松手判定返回时直接 onBack()，由 NavHost popExitTransition 接管剩余滑出动画，
//   同时 popEnterTransition 让主页从左视差滑入——过渡期间双页面同屏，底层露出真正的 HomeScreen。
// - 主页前进时不动画（exit=None），后退时从左视差滑入（popEnter）。
@Composable
fun QuiddityNavHost(
    pendingConversationRoute: String? = null,
    onPendingConversationConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navigateThrottle = remember { NavigationThrottle() }
    val settingsRepo = remember { ServiceLocator.settingsRepository }

