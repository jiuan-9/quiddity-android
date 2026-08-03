package com.quiddity.app.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.quiddity.app.ui.chat.ChatViewModelHost
import com.quiddity.app.ui.chat.ChatViewModelHostFactory
import com.quiddity.app.ui.home.HomeScreen
import com.quiddity.app.ui.home.HomeViewModel
import com.quiddity.app.ui.home.HomeViewModelFactory
import com.quiddity.app.ui.settings.SettingsViewModel
import com.quiddity.app.ui.settings.SettingsViewModelFactory
import com.quiddity.app.ui.theme.Motion
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

    // ===== 版本更新（每次进入前台自动检查，对应算法：检查时机 = 每次 ON_RESUME） =====
    val updateController = rememberUpdateController()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, updateController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                updateController.autoCheck()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // 组合期若已处于 RESUMED（如 Activity 重建后）则立即补一次检查
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            updateController.autoCheck()
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                    ServiceLocator.apiCatalogManager,
                    ServiceLocator.characterRepository
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
            // 当前规则：ChatViewModel 由 Activity 作用域的宿主按会话 ID 管理。
            // - 退出会话时：无未完结任务（流式/压缩/发送延迟）→ 立即释放；
            // - 有未完结任务 → 等任务完结后再释放；
            // - 未退出时 VM 常驻，流式任务照常跑完并写入存储，返回页面直接看到完整回复。
            val chatHost: ChatViewModelHost = (LocalContext.current as? ComponentActivity)?.let { activity ->
                viewModel(
                    viewModelStoreOwner = activity,
                    factory = ChatViewModelHostFactory { id, onIdle ->
                        ChatViewModel(
                            conversationRepository = ServiceLocator.conversationRepository,
                            chatRepository = ServiceLocator.chatRepository,
                            settingsRepository = settingsRepo,
                            apiCatalogManager = ServiceLocator.apiCatalogManager,
                            conversationId = id,
                            onIdle = onIdle
                        )
                    }
                )
            } ?: remember {
                ChatViewModelHost { id, _ ->
                    ChatViewModel(
                        conversationRepository = ServiceLocator.conversationRepository,
                        chatRepository = ServiceLocator.chatRepository,
                        settingsRepository = settingsRepo,
                        apiCatalogManager = ServiceLocator.apiCatalogManager,
                        conversationId = id
                    )
                }
            }
            val chatVm: ChatViewModel = chatHost.get(convId)
            val settingsVm: SettingsViewModel = viewModel(
                factory = SettingsViewModelFactory(
                    settingsRepo,
                    ServiceLocator.conversationRepository,
                    ServiceLocator.apiCatalogManager,
                    ServiceLocator.characterRepository
                )
            )
            ChatScreen(
                viewModel = chatVm,
                settingsViewModel = settingsVm,
                onBack = { navController.popBackStack() },
                onConversationExit = { chatHost.onScreenExit(convId) }
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
