package com.quiddity.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.quiddity.app.di.ServiceLocator
import com.quiddity.app.ui.components.UpdateDialog
import com.quiddity.app.ui.components.rememberUpdateController
import com.quiddity.app.ui.chat.ChatScreen
import com.quiddity.app.ui.chat.ChatViewModel
import com.quiddity.app.ui.chat.ChatViewModelFactory
import com.quiddity.app.ui.home.HomeScreen
import com.quiddity.app.ui.home.HomeViewModel
import com.quiddity.app.ui.home.HomeViewModelFactory
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
fun QuiddityNavHost() {
    val navController = rememberNavController()
    val settingsRepo = remember { ServiceLocator.settingsRepository }

    // ===== 版本更新 =====
    val updateController = rememberUpdateController()
    LaunchedEffect(Unit) {
        updateController.autoCheck()
    }

    // ===== 三条开发规范（位于文件中间位置） =====
    // 1. 问题修复规范：所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
    //    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
    // 2. 代码注释规范：文件内仅允许保留两类注释——当前规则说明注释与模块划分注释（// ===== xxx =====）。
    //    除此之外，禁止出现任何形式的代码注释（包括单行 / 多行 / 临时调试注释）。
    // 3. 构建交付要求：完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK。
    //    APK 需满足：签名有效且符合发布标准、包含完整功能模块、经过基础性能与兼容性测试。

    // ===== 路由表 =====
    // 当前规则：所有页面级动画统一 0.4s（400ms），纯位移无 fade——
    //   前进 Home→Chat：Chat 从右滑入（enterTransition），主页不动（exit=None）被自然覆盖。
    //   后退 Chat→Home：松手 onBack() 后，Chat 由 popExitTransition 继续向右滑出屏外
    //     （叠加 ChatDragController 保持的手势偏移，无缝衔接），
    //     主页由 popEnterTransition 从左视差滑入（-fullWidth/6 → 0），
    //     过渡期间双页面同屏，底层露出真正的 HomeScreen 而非纯色背景。
    NavHost(
        navController = navController,
        startDestination = QuiddityRoute.Home.path,
        enterTransition = {
            slideInHorizontally(
                animationSpec = tween(Motion.DurationPageTransition, easing = Motion.EasingStandard),
                initialOffsetX = { it }
            )
        },
        exitTransition = {
            // 主页不动画：让 Chat 滑入时自然盖住主页
            androidx.compose.animation.ExitTransition.None
        },
        popEnterTransition = {
            // 主页从左视差滑入：松手返回时与 Chat 滑出同步，底层露出真正的会话列表
            slideInHorizontally(
                animationSpec = tween(Motion.DurationPageTransition, easing = Motion.EasingStandard),
                initialOffsetX = { -it / 6 }
            )
        },
        popExitTransition = {
            // Chat 继续向右滑出屏外（0→fullWidth），叠加 ChatScreen 内部保持的手势偏移，
            // 视觉上从手势位置继续滑出，无跳跃
            slideOutHorizontally(
                animationSpec = tween(Motion.DurationPageTransition, easing = Motion.EasingStandard),
                targetOffsetX = { it }
            )
        }
    ) {
        composable(QuiddityRoute.Home.path) {
            val homeVm: HomeViewModel = viewModel(factory = HomeViewModelFactory(ServiceLocator.conversationRepository))
            val settingsVm: SettingsViewModel = viewModel(
                factory = SettingsViewModelFactory(
                    settingsRepo,
                    ServiceLocator.conversationRepository,
                    ServiceLocator.apiCatalogManager
                )
            )
            val settings by settingsVm.settings.collectAsStateWithLifecycle()
            HomeScreen(
                viewModel = homeVm,
                settingsViewModel = settingsVm,
                userAvatarUri = settings.userAvatarUri,
                onOpenConversation = { convId ->
                    navController.navigate(QuiddityRoute.Chat.create(convId))
                }
            )
        }

        composable(
            route = QuiddityRoute.Chat.PATTERN,
            arguments = QuiddityRoute.Chat.arguments
        ) { backStackEntry ->
            val convId = backStackEntry.arguments?.getString(QuiddityRoute.Chat.ARG_CONV_ID).orEmpty()
            val chatVm: ChatViewModel = viewModel(
                factory = ChatViewModelFactory(
                    conversationRepository = ServiceLocator.conversationRepository,
                    chatRepository = ServiceLocator.chatRepository,
                    settingsRepository = settingsRepo,
                    apiCatalogManager = ServiceLocator.apiCatalogManager,
                    conversationId = convId
                )
            )
            val settingsVm: SettingsViewModel = viewModel(
                factory = SettingsViewModelFactory(
                    settingsRepo,
                    ServiceLocator.conversationRepository,
                    ServiceLocator.apiCatalogManager
                )
            )
            ChatScreen(
                viewModel = chatVm,
                settingsViewModel = settingsVm,
                onBack = { navController.popBackStack() }
            )
        }
    }

    // UpdateDialog 放在 NavHost 之后（z-order 上层）：
    // ChatScreen 滑动退出时 NavHost 内容滑走，底层不再露出 UpdateDialog（之前放 NavHost 前导致滑出时露出更新窗口）。
    updateController.updateResult?.let { result ->
        UpdateDialog(
            result = result,
            onDismiss = { updateController.dismissDialog() }
        )
    }
}

sealed class QuiddityRoute(val path: String) {
    data object Home : QuiddityRoute("home")
    data object Chat : QuiddityRoute("chat/{convId}") {
        const val PATTERN = "chat/{convId}"
        const val ARG_CONV_ID = "convId"
        fun create(convId: String) = "chat/$convId"
        val arguments = listOf(
            androidx.navigation.navArgument(ARG_CONV_ID) { type = androidx.navigation.NavType.StringType }
        )
    }
}
