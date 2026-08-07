package com.quiddity.app.ui.miniapps.board

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quiddity.app.di.ServiceLocator
import com.quiddity.app.domain.board.BoardGameType
import com.quiddity.app.ui.theme.Motion
import com.quiddity.app.ui.miniapps.MiniApp
import com.quiddity.app.ui.miniapps.MiniAppHost

/*
 * 棋盘小应用：五子棋 / 围棋。
 * 页面流转完全在小应用内部，与主 NavHost 解耦。
 */
object BoardMiniApp : MiniApp {
    override val id: String = "board"
    override val name: String = "棋盘"
    override val description: String = "五子棋 · 围棋，与角色或 AI 下一盘"
    override val icon: ImageVector = Icons.Rounded.GridOn

    /** 邀请气泡文案（带棋种，供邀请流程使用）。 */
    fun inviteBubbleText(game: BoardGameType, opponentName: String): String =
        "你邀请了「$opponentName」一起玩《棋盘·${game.displayName}》"

    override fun inviteBubbleText(opponentName: String): String =
        "你邀请了「$opponentName」一起玩《棋盘》"

    @Composable
    override fun Content(host: MiniAppHost) {
        val vm: BoardViewModel = viewModel(
            factory = BoardViewModelFactory(
                sessionRepository = ServiceLocator.miniAppSessionRepository,
                characterRepository = ServiceLocator.characterRepository,
                inviteManager = ServiceLocator.miniAppInviteManager,
                chatApi = ServiceLocator.chatApi
            )
        )
        val uiState by vm.uiState.collectAsStateWithLifecycle()
        BoardAppRoot(host = host, vm = vm, uiState = uiState)
    }
}

@Composable
private fun BoardAppRoot(
    host: MiniAppHost,
    vm: BoardViewModel,
    uiState: BoardUiState
) {
    AnimatedContent(
        targetState = uiState.route,
        transitionSpec = {
            // 前进：新页面从右侧滑入；后退：旧页面滑出到右侧、新页面从左侧浅入。
            // 入场用减速缓动，退场用加速缓动（Motion Design：入场缓、退场快）。
            val forward = routeDepth(targetState) >= routeDepth(initialState)
            val enter = slideInHorizontally(
                animationSpec = tween(Motion.DurationPageTransition, easing = Motion.EasingStandard)
            ) { fullWidth -> if (forward) fullWidth else -fullWidth / 4 } +
                fadeIn(tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate))
            val exit = slideOutHorizontally(
                animationSpec = tween(Motion.DurationPageTransition, easing = Motion.EasingStandard)
            ) { fullWidth -> if (forward) -fullWidth / 4 else fullWidth } +
                fadeOut(tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate))
            enter togetherWith exit
        },
        label = "board_route"
    ) { route ->
        when (route) {
            BoardRoute.GameSelect -> {
                BoardGameSelectScreen(
                    onBack = host.onExit,
                    onSelect = vm::selectGame
                )
            }
            is BoardRoute.ModeSelect -> {
                BoardModeSelectScreen(
                    game = route.game,
                    onBack = { vm.backToGames() },
                    onInvite = { vm.onChooseInvite(route.game) },
                    onVsComputer = { vm.onChooseVsComputer(route.game) }
                )
            }
            is BoardRoute.Invite -> {
                val characters by vm.characters.collectAsStateWithLifecycle()
                BoardInviteScreen(
                    characters = characters,
                    game = route.game,
                    checking = uiState.inviteChecking,
                    onBack = { vm.backToMode(route.game) },
                    onInvite = vm::inviteCharacter
                )
            }
            is BoardRoute.Playing -> {
                val session = uiState.session
                if (session == null) {
                    LaunchedEffect(Unit) { host.onExit() }
                    return@AnimatedContent
                }
                BoardGameScreen(
                    session = session,
                    // 对局退出回到当前棋种的"选择对手"页，不跨级跳回棋种选择
                    onBack = { vm.backToMode(session.gameType) },
                    onCellTap = vm::onUserMove,
                    onPass = vm::onPass,
                    onResign = vm::onResign,
                    onSendChat = vm::sendChat,
                    onRematch = vm::rematch,
                    onOpenChat = session.conversationId?.let { convId ->
                        { host.onOpenConversation(convId) }
                    }
                )
            }
        }
    }
}

private fun routeDepth(route: BoardRoute): Int = when (route) {
    BoardRoute.GameSelect -> 0
    is BoardRoute.ModeSelect -> 1
    is BoardRoute.Invite -> 2
    is BoardRoute.Playing -> 3
}
