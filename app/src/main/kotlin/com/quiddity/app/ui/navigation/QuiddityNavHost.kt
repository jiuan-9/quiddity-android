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
fun QuiddityNavHost(
    pendingConversationRoute: String? = null,
    onPendingConversationConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navigateThrottle = remember { NavigationThrottle() }
    val settingsRepo = remember { ServiceLocator.settingsRepository }

    // ===== 通知深链：主动消息 / 行动弹窗点击后直接进入对应会话框 =====
    // MainActivity 收到带会话参数的 Intent 后传入路由，此处消费并跳转；跳转后回调清空，
    // 保证同一会话被再次点击时 LaunchedEffect 仍能重新触发。
    LaunchedEffect(pendingConversationRoute) {
        val route = pendingConversationRoute ?: return@LaunchedEffect
        if (route.startsWith(QuiddityRoute.Chat.PATTERN_PREFIX) ||
            route.startsWith(QuiddityRoute.AgentChat.PATTERN_PREFIX)
        ) {
            navigateThrottle.tryNavigate { navController.navigate(route) }
        }
        onPendingConversationConsumed()
    }

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
                    ServiceLocator.characterRepository,
                    ServiceLocator.agentStore
                )
            )
            val settings by settingsVm.settings.collectAsStateWithLifecycle()
            HomeScreen(
                viewModel = homeVm,
                settingsViewModel = settingsVm,
                userAvatarUri = settings.userAvatarUri,
                onOpenMiniApps = {
                    navigateThrottle.tryNavigate { navController.navigate(QuiddityRoute.MiniApps.path) }
                },
                onOpenConversation = { convId ->
                    navigateThrottle.tryNavigate {
                        val conv = ServiceLocator.conversationRepository.getConversation(convId)
                        if (conv?.type == ConversationType.AGENT) {
                            navController.navigate(QuiddityRoute.AgentChat.create(convId))
                        } else {
                            navController.navigate(QuiddityRoute.Chat.create(convId))
                        }
                    }
                },
                onOpenMessage = { convId, messageId ->
                    navigateThrottle.tryNavigate { navController.navigate(QuiddityRoute.Chat.create(convId, messageId)) }
                }
            )
        }

        composable(
            route = QuiddityRoute.MiniApps.path,
            enterTransition = {
                slideInVertically(
                    animationSpec = tween(Motion.DurationPageTransition, easing = Motion.EasingStandard),
                    initialOffsetY = { -it }
                )
            },
            popExitTransition = {
                slideOutVertically(
                    animationSpec = tween(Motion.DurationPageTransition, easing = Motion.EasingStandard),
                    targetOffsetY = { -it }
                )
            }
        ) {
            val context = LocalContext.current
            val miniAppStore = ServiceLocator.miniAppStore
            val favorites by miniAppStore.favorites.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            MiniAppsCenterScreen(
                favorites = favorites,
                onToggleFavorite = { id ->
                    scope.launch {
                        miniAppStore.toggleFavorite(id)
                    }
                },
                onOpenApp = { appId ->
                    navigateThrottle.tryNavigate { navController.navigate(QuiddityRoute.MiniAppHost.create(appId)) }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = QuiddityRoute.MiniAppHost.PATTERN,
            arguments = QuiddityRoute.MiniAppHost.arguments,
            enterTransition = {
                // 进入：由 MiniAppLaunchGate 的解压动画接管展开，外层仅做轻量淡入
                fadeIn(tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate))
            },
            exitTransition = {
                fadeOut(tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate))
            },
            popEnterTransition = {
                fadeIn(tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate))
            },
            popExitTransition = {
                scaleOut(
                    animationSpec = tween(Motion.DurationPageTransition, easing = Motion.EasingEmphasizedAccelerate),
                    targetScale = 0.9f
                ) + fadeOut(tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate)) +
                    slideOutVertically(
                        animationSpec = tween(Motion.DurationPageTransition, easing = Motion.EasingEmphasizedAccelerate),
                        targetOffsetY = { it / 8 }
                    )
            }
        ) { backStackEntry ->
            val appId = backStackEntry.arguments?.getString(QuiddityRoute.MiniAppHost.ARG_APP_ID).orEmpty()
            val app = MiniAppRegistry.byId(appId)
            if (app == null) {
                MiniAppMissingScreen(onBack = { navController.popBackStack() })
            } else {
                val context = LocalContext.current
                val host = remember(appId) {
                    MiniAppHost(
                        onExit = { navController.popBackStack() },
                        onOpenConversation = { convId ->
                            navigateThrottle.tryNavigate { navController.navigate(QuiddityRoute.Chat.create(convId)) }
                        },
                        onToast = { message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                MiniAppLaunchGate(app = app) {
                    app.Content(host)
                }
            }
        }

        composable(
            route = QuiddityRoute.Chat.PATTERN,
            arguments = QuiddityRoute.Chat.arguments
        ) { backStackEntry ->
            val convId = backStackEntry.arguments?.getString(QuiddityRoute.Chat.ARG_CONV_ID).orEmpty()
            val messageId = backStackEntry.arguments
                ?.getString(QuiddityRoute.Chat.ARG_MESSAGE_ID)
                ?.takeIf { it.isNotBlank() }
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
                            visionOcrService = ServiceLocator.visionOcrService,
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
                        visionOcrService = ServiceLocator.visionOcrService,
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
                    ServiceLocator.characterRepository,
                    ServiceLocator.agentStore
                )
            )
            ChatScreen(
                viewModel = chatVm,
                settingsViewModel = settingsVm,
                initialMessageId = messageId,
                onBack = { navController.popBackStack() },
                onConversationExit = { chatHost.onScreenExit(convId) },
                onOpenMiniApp = { appId ->
                    navigateThrottle.tryNavigate { navController.navigate(QuiddityRoute.MiniAppHost.create(appId)) }
                }
            )
        }

        composable(
            route = QuiddityRoute.AgentChat.PATTERN,
            arguments = QuiddityRoute.AgentChat.arguments
        ) { backStackEntry ->
            val convId = backStackEntry.arguments?.getString(QuiddityRoute.AgentChat.ARG_CONV_ID).orEmpty()
            val chatHost: ChatViewModelHost = (LocalContext.current as? ComponentActivity)?.let { activity ->
                viewModel(
                    viewModelStoreOwner = activity,
                    factory = ChatViewModelHostFactory { id, onIdle ->
                        ChatViewModel(
                            conversationRepository = ServiceLocator.conversationRepository,
                            chatRepository = ServiceLocator.chatRepository,
                            settingsRepository = settingsRepo,
                            apiCatalogManager = ServiceLocator.apiCatalogManager,
                            visionOcrService = ServiceLocator.visionOcrService,
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
                        visionOcrService = ServiceLocator.visionOcrService,
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
                    ServiceLocator.characterRepository,
                    ServiceLocator.agentStore
                )
            )
            AgentChatScreen(
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
    data object MiniApps : QuiddityRoute("miniapps")
    data object MiniAppHost : QuiddityRoute("miniapp/{appId}") {
        const val PATTERN = "miniapp/{appId}"
        const val ARG_APP_ID = "appId"
        fun create(appId: String) = "miniapp/$appId"
        val arguments = listOf(
            androidx.navigation.navArgument(ARG_APP_ID) { type = androidx.navigation.NavType.StringType }
        )
    }
    data object Chat : QuiddityRoute("chat/{convId}") {
        const val PATTERN = "chat/{convId}?messageId={messageId}"
        const val PATTERN_PREFIX = "chat/"
        const val ARG_CONV_ID = "convId"
        const val ARG_MESSAGE_ID = "messageId"
        fun create(convId: String, messageId: String? = null) =
            if (messageId.isNullOrBlank()) "chat/$convId"
            else "chat/$convId?messageId=$messageId"
        val arguments = listOf(
            androidx.navigation.navArgument(ARG_CONV_ID) { type = androidx.navigation.NavType.StringType },
            androidx.navigation.navArgument(ARG_MESSAGE_ID) {
                type = androidx.navigation.NavType.StringType
                nullable = true
            }
        )
    }

    data object AgentChat : QuiddityRoute("agentchat/{convId}") {
        const val PATTERN = "agentchat/{convId}"
        const val PATTERN_PREFIX = "agentchat/"
        const val ARG_CONV_ID = "convId"
        fun create(convId: String) = "agentchat/$convId"
        val arguments = listOf(
            androidx.navigation.navArgument(ARG_CONV_ID) { type = androidx.navigation.NavType.StringType }
        )
    }

}

// ===== 防多按 =====
// 所有页面跳转统一走 NavigationThrottle：窗口期内重复跳转直接忽略，
// 防止手快连点同一入口导致同一页面在返回栈中堆积，产生逻辑/视觉重复。
private const val NAVIGATION_THROTTLE_MS = 600L

internal class NavigationThrottle(
    private val windowMs: Long = NAVIGATION_THROTTLE_MS,
    private val clock: () -> Long = { SystemClock.uptimeMillis() }
) {
    private var lastNavigateAt = Long.MIN_VALUE

    /** 窗口期内重复调用返回 false 并忽略；否则执行 action 并返回 true。 */
    fun tryNavigate(action: () -> Unit): Boolean {
        val now = clock()
        if (lastNavigateAt != Long.MIN_VALUE && now - lastNavigateAt < windowMs) return false
        lastNavigateAt = now
        action()
        return true
    }
}
