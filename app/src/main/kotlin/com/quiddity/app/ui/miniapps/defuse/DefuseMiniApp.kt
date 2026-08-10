package com.quiddity.app.ui.miniapps.defuse

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
import androidx.compose.material.icons.rounded.Bolt
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
 * 拆弹小队小应用：玩家看面板，LLM 搭档翻手册，组队对抗另一支 LLM 拆弹队。
 */
object DefuseMiniApp : MiniApp {
    override val id: String = "defuse"
    override val name: String = "拆弹小队"
    override val description: String = "你看面板，AI 翻手册 —— 组队拆弹赢过对手队"
    override val icon: ImageVector = Icons.Rounded.Bolt

    override fun inviteBubbleText(opponentName: String): String =
        "你邀请了「$opponentName」一起拆弹小队：你看面板，TA 翻手册，别剪错线！"

    @Composable
    override fun Content(host: MiniAppHost) {
        val vm: DefuseViewModel = viewModel(
            factory = DefuseViewModelFactory(
                characterRepository = ServiceLocator.characterRepository,
                conversationRepository = ServiceLocator.conversationRepository,
                inviteManager = ServiceLocator.miniAppInviteManager,
                sessionRepository = ServiceLocator.miniAppSessionRepository,
                chatApi = ServiceLocator.chatApi
            )
        )
        val uiState by vm.uiState.collectAsStateWithLifecycle()
        DefuseAppRoot(host = host, vm = vm, uiState = uiState)
    }
}

@Composable
private fun DefuseAppRoot(
    host: MiniAppHost,
    vm: DefuseViewModel,
    uiState: DefuseUiState
) {
    AnimatedContent(
        targetState = uiState.route,
        transitionSpec = {
            val forward = routeDepth(targetState) >= routeDepth(initialState)
            val touchesResult = targetState is DefuseRoute.Result || initialState is DefuseRoute.Result
            if (touchesResult) {
                val enter = scaleIn(
                    animationSpec = tween(Motion.DurationMedium + 40, easing = Motion.EasingEmphasizedDecelerate),
                    initialScale = 0.92f
                ) + fadeIn(tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate))
                val exit = scaleOut(
                    animationSpec = tween(Motion.DurationShort + 40, easing = Motion.EasingEmphasizedAccelerate),
                    targetScale = 0.94f
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
        label = "defuse_route"
    ) { route ->
        when (route) {
            DefuseRoute.Setup -> {
                val invitees by vm.invitees.collectAsStateWithLifecycle()
                DefuseSetupScreen(
                    invitees = invitees,
                    state = uiState,
                    onBack = host.onExit,
                    onSelectPartner = vm::selectPartner,
                    onSelectScript = vm::selectScriptPartner,
                    onSelectDifficulty = vm::selectDifficulty,
                    onStart = vm::start
                )
            }
            is DefuseRoute.Playing -> {
                if (uiState.session == null || uiState.game == null) {
                    LaunchedEffect(Unit) { host.onExit() }
                    return@AnimatedContent
                }
                DefusePlayingScreen(
                    state = uiState,
                    onBack = vm::backToSetup,
                    onCutWire = vm::onCutWire,
                    onKeypadPress = vm::onKeypadPress,
                    onButtonAction = vm::onButtonAction,
                    onSendText = vm::onSendText,
                    onReportPanel = vm::onReportPanel,
                    onToggleHelp = vm::onToggleHelp
                )
            }
            DefuseRoute.Result -> {
                DefuseResultScreen(
                    state = uiState,
                    onRematch = vm::rematch,
                    onBack = vm::backToSetup
                )
            }
        }
    }
}

private fun routeDepth(route: DefuseRoute): Int = when (route) {
    DefuseRoute.Setup -> 0
    is DefuseRoute.Playing -> 1
    DefuseRoute.Result -> 2
}
