package com.quiddity.app.ui.miniapps.spy

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quiddity.app.di.ServiceLocator
import com.quiddity.app.ui.miniapps.MiniApp
import com.quiddity.app.ui.miniapps.MiniAppHost
import com.quiddity.app.ui.theme.Motion

/*
 * 谁是卧底小应用：多人桌游（真人 + LLM 角色 + 脚本路人）。
 * 页面流转完全在小应用内部，与主 NavHost 解耦。
 */
object SpyMiniApp : MiniApp {
    override val id: String = "spy"
    override val name: String = "谁是卧底"
    override val description: String = "多人桌游 · 找出发言可疑的卧底"
    override val icon: ImageVector = Icons.Rounded.Groups

    override fun inviteBubbleText(opponentName: String): String =
        "你邀请了「$opponentName」一起玩《谁是卧底》。"

    @Composable
    override fun Content(host: MiniAppHost) {
        val vm: SpyViewModel = viewModel(
            factory = SpyViewModelFactory(
                characterRepository = ServiceLocator.characterRepository,
                inviteManager = ServiceLocator.miniAppInviteManager,
                sessionRepository = ServiceLocator.miniAppSessionRepository,
                chatApi = ServiceLocator.chatApi,
                settingsRepository = ServiceLocator.settingsRepository
            )
        )
        val uiState by vm.uiState.collectAsStateWithLifecycle()
        SpyAppRoot(host = host, vm = vm, uiState = uiState)
    }
}

@Composable
private fun SpyAppRoot(
    host: MiniAppHost,
    vm: SpyViewModel,
    uiState: SpyUiState
) {
    AnimatedContent(
        targetState = uiState.route,
        transitionSpec = {
            val forward = routeDepth(targetState) >= routeDepth(initialState)
            val touchesResult = targetState is SpyRoute.Result || initialState is SpyRoute.Result
            if (touchesResult) {
                val enter = scaleIn(
                    animationSpec = tween(Motion.DurationMedium + 40, easing = Motion.EasingEmphasizedDecelerate),
                    initialScale = 0.9f
                ) + fadeIn(tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate))
                val exit = scaleOut(
                    animationSpec = tween(Motion.DurationShort + 40, easing = Motion.EasingEmphasizedAccelerate),
                    targetScale = 0.92f
                ) + fadeOut(tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate))
                enter togetherWith exit
            } else {
                val enter = slideInHorizontally(
                    animationSpec = tween(Motion.DurationPageTransition, easing = Motion.EasingStandard)
                ) { fullWidth -> if (forward) fullWidth else -fullWidth / 4 } +
                    fadeIn(tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate))
                val exit = slideOutHorizontally(
                    animationSpec = tween(Motion.DurationPageTransition, easing = Motion.EasingStandard)
                ) { fullWidth -> if (forward) -fullWidth / 4 else fullWidth } +
                    fadeOut(tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate))
                enter togetherWith exit
            }
        },
        label = "spy_route"
    ) { route ->
        when (route) {
            SpyRoute.Setup -> {
                val characters by vm.characters.collectAsStateWithLifecycle()
                SpySetupScreen(
                    characters = characters,
                    setup = uiState.setup,
                    checking = uiState.checked,
                    userAvatarUri = uiState.userAvatarUri,
                    onBack = host.onExit,
                    onToggleLlm = vm::reselectLlmFrom,
                    onRemoveLlm = vm::removeLlmCharacter,
                    onCategorySelect = vm::selectCategory,
                    onModeSelect = vm::selectMode,
                    onStart = vm::startGame
                )
            }
            SpyRoute.Deal -> {
                val game = uiState.game
                if (game == null) {
                    LaunchedEffect(Unit) { host.onExit() }
                    return@AnimatedContent
                }
                SpyDealScreen(
                    game = game,
                    userIndex = uiState.userIndex,
                    notice = uiState.notice,
                    onContinue = vm::startFirstRound,
                    onBack = vm::backToSetup
                )
            }
            is SpyRoute.Playing -> {
                val game = uiState.game
                if (game == null) {
                    LaunchedEffect(Unit) { host.onExit() }
                    return@AnimatedContent
                }
                SpyPlayingScreen(
                    game = game,
                    userIndex = uiState.userIndex,
                    lastVoteEvent = uiState.lastVoteEvent,
                    onBack = vm::backToSetup,
                    onSpeakSubmit = vm::submitSpeaking,
                    onVoteSubmit = vm::submitVote
                )
            }
            SpyRoute.Result -> {
                val game = uiState.game
                if (game == null) {
                    LaunchedEffect(Unit) { host.onExit() }
                    return@AnimatedContent
                }
                SpyResultScreen(
                    game = game,
                    onRematch = vm::rematch,
                    onBack = vm::backToSetup
                )
            }
        }
    }
}

private fun routeDepth(route: SpyRoute): Int = when (route) {
    SpyRoute.Setup -> 0
    SpyRoute.Deal -> 1
    is SpyRoute.Playing -> 2
    SpyRoute.Result -> 3
}
