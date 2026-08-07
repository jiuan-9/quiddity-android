package com.quiddity.app.ui.miniapps.board

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quiddity.app.data.model.Character
import com.quiddity.app.data.remote.ChatApi
import com.quiddity.app.data.repo.ApiAccess
import com.quiddity.app.data.repo.CharacterRepository
import com.quiddity.app.data.repo.MiniAppSessionRepository
import com.quiddity.app.domain.board.BoardBot
import com.quiddity.app.domain.board.BoardGameType
import com.quiddity.app.domain.board.BoardState
import com.quiddity.app.domain.board.GameChatTurn
import com.quiddity.app.domain.board.GoScore
import com.quiddity.app.domain.board.LlmMove
import com.quiddity.app.domain.board.MoveOutcome
import com.quiddity.app.domain.board.PassOutcome
import com.quiddity.app.domain.board.Stone
import com.quiddity.app.util.IdGenerator
import com.quiddity.app.ui.miniapps.MiniAppInviteManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/*
 * 棋盘小应用状态机：选棋种 → 选模式 → 邀请/开局 → 对局。
 *
 * 对局流程：
 * - 随机黑白；若 AI（LLM/本地电脑）执黑先行，开局自动落子；
 * - 对手决策优先 LLM（严格 MOVE/PASS 协议），失败/非法自动降级本地 AI；
 * - 对局内聊天仅 LLM 对手回复，本地电脑对手不回；
 * - 结束后向邀请角色的私聊会话注入对局记忆（气泡在邀请时已写入）。
 */
sealed interface BoardRoute {
    data object GameSelect : BoardRoute
    data class ModeSelect(val game: BoardGameType) : BoardRoute
    data class Invite(val game: BoardGameType) : BoardRoute
    data class Playing(val sessionId: String) : BoardRoute
}

enum class BoardGameMode {
    INVITE_CHARACTER,
    VS_COMPUTER
}

data class BoardChatMessage(
    val id: String,
    val fromUser: Boolean,
    val text: String,
    val timestamp: Long
)

sealed interface BoardStatus {
    data object Playing : BoardStatus
    data class Finished(
        val winner: Stone?,
        val winnerName: String?,
        val reason: String,
        val score: GoScore?,
        val summary: String
    ) : BoardStatus
}

data class BoardSession(
    val id: String,
    val gameType: BoardGameType,
    val mode: BoardGameMode,
    val opponentName: String,
    val opponentPersona: String?,
    val userStone: Stone,
    val llmEnabled: Boolean,
    val access: ApiAccess.Resolved?,
    val board: BoardState,
    val chat: List<BoardChatMessage>,
    val status: BoardStatus,
    val conversationId: String?,
    val thinking: Boolean,
    val notice: String?
)

data class BoardUiState(
    val route: BoardRoute = BoardRoute.GameSelect,
    val session: BoardSession? = null,
    val inviteChecking: Boolean = false
)

class BoardViewModel(
    private val sessionRepository: MiniAppSessionRepository,
    private val characterRepository: CharacterRepository,
    private val inviteManager: MiniAppInviteManager,
    private val chatApi: ChatApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(BoardUiState())
    val uiState: StateFlow<BoardUiState> = _uiState.asStateFlow()

    val characters = characterRepository.characters

    fun selectGame(game: BoardGameType) {
        _uiState.update { it.copy(route = BoardRoute.ModeSelect(game)) }
    }

    fun backToGames() {
        _uiState.update { it.copy(route = BoardRoute.GameSelect, session = null) }
    }

    fun backToMode(game: BoardGameType) {
        _uiState.update { it.copy(route = BoardRoute.ModeSelect(game)) }
    }

    fun onChooseInvite(game: BoardGameType) {
        _uiState.update { it.copy(route = BoardRoute.Invite(game)) }
    }

    /** 与电脑对战：固定使用本地棋力，无需联网，不与 AI 混在一起。 */
    fun onChooseVsComputer(game: BoardGameType) {
        startSession(
            game = game,
            mode = BoardGameMode.VS_COMPUTER,
            opponentName = "电脑棋手",
            opponentPersona = null,
            conversationId = null,
            access = null,
            notice = null
        )
    }

    /** 邀请角色：先检测 API 连接，成功则 LLM 对手；失败则本地电脑兜底，不阻塞开始。 */
    fun inviteCharacter(character: Character) {
        val game = (_uiState.value.route as? BoardRoute.Invite)?.game ?: return
        _uiState.update { it.copy(inviteChecking = true) }
        viewModelScope.launch {
            val invite = inviteManager.prepare(
                character = character,
                inviteBubbleText = { name -> BoardMiniApp.inviteBubbleText(game, name) },
                miniAppId = BoardMiniApp.id,
                miniAppTitle = BoardMiniApp.name
            )

            startSession(
                game = game,
                mode = BoardGameMode.INVITE_CHARACTER,
                opponentName = invite.opponentName,
                opponentPersona = invite.opponentPersona,
                conversationId = invite.conversationId,
                access = invite.access,
                notice = if (invite.access == null) "API 连接失败，已切换为本地电脑对弈" else null
            )
        }
    }

    fun onUserMove(row: Int, col: Int) {
        val session = _uiState.value.session ?: return
        if (session.status != BoardStatus.Playing || session.thinking) return
        if (session.board.current != session.userStone) return
        when (val outcome = session.board.applyMove(row, col)) {
            is MoveOutcome.Played -> {
                if (outcome.gameOver) {
                    finishGame(session, outcome.winner, outcome.state.endReason, outcome.state)
                } else {
                    _uiState.update { it.copy(session = session.copy(board = outcome.state)) }
                    requestOpponentMove(_uiState.value.session ?: return)
                }
            }
            else -> Unit
        }
    }

    fun onPass() {
        val session = _uiState.value.session ?: return
        if (session.status != BoardStatus.Playing || session.thinking) return
        if (!session.gameType.isGo || session.board.current != session.userStone) return
        val pass = session.board.pass() as? PassOutcome.Done ?: return
        if (pass.gameOver) {
            finishGame(session, pass.winner, "双方停一手", pass.state)
        } else {
            _uiState.update { it.copy(session = session.copy(board = pass.state)) }
            requestOpponentMove(_uiState.value.session ?: return)
        }
    }

    fun onResign() {
        val session = _uiState.value.session ?: return
        if (session.status != BoardStatus.Playing) return
        val resigned = session.board.resign()
        finishGame(session, resigned.winner, "认输", resigned)
    }

    fun sendChat(text: String) {
        val session = _uiState.value.session ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        // 最新这条用户消息不进"历史"，由 chatReply 的 user 消息单独携带，避免重复
        val historyBefore = session.chat
        val userMsg = BoardChatMessage(
            id = IdGenerator.newId(IdGenerator.Prefix.USER_MESSAGE),
            fromUser = true,
            text = trimmed,
            timestamp = System.currentTimeMillis()
        )
        _uiState.update { st ->
            val s = st.session ?: return@update st
            st.copy(session = s.copy(chat = s.chat + userMsg))
        }

        if (!session.llmEnabled) {
            if (session.chat.none { !it.fromUser && it.text.startsWith(LOCAL_BOT_CHAT_NOTICE_PREFIX) }) {
                val noticeMsg = BoardChatMessage(
                    id = IdGenerator.newId(IdGenerator.Prefix.AI_MESSAGE),
                    fromUser = false,
                    text = LOCAL_BOT_CHAT_NOTICE_PREFIX + "对方是本地电脑，不会回复消息。",
                    timestamp = System.currentTimeMillis()
                )
                _uiState.update { st ->
                    val s = st.session ?: return@update st
                    st.copy(session = s.copy(chat = s.chat + noticeMsg))
                }
            }
            return
        }

        val currentSession = _uiState.value.session ?: return
        _uiState.update { it.copy(session = it.session?.copy(thinking = true)) }
        viewModelScope.launch {
            val reply = currentSession.access?.let { access ->
                BoardLlmClient(access.toLlmGateway(chatApi))
                    .chatReply(
                        currentSession.board,
                        currentSession.opponentName,
                        currentSession.opponentPersona,
                        trimmed,
                        historyBefore.map { GameChatTurn(it.fromUser, it.text) }
                    )
            }
            val latest = _uiState.value.session ?: return@launch
            val replyMsg = if (reply != null) {
                BoardChatMessage(
                    id = IdGenerator.newId(IdGenerator.Prefix.AI_MESSAGE),
                    fromUser = false,
                    text = reply,
                    timestamp = System.currentTimeMillis()
                )
            } else {
                BoardChatMessage(
                    id = IdGenerator.newId(IdGenerator.Prefix.AI_MESSAGE),
                    fromUser = false,
                    text = "（对方暂时没有回应……）",
                    timestamp = System.currentTimeMillis()
                )
            }
            _uiState.update { st ->
                val s = st.session ?: return@update st
                if (s.id != currentSession.id) return@update st
                st.copy(session = s.copy(chat = s.chat + replyMsg, thinking = false))
            }
        }
    }

    fun rematch() {
        val session = _uiState.value.session ?: return
        startSession(
            game = session.gameType,
            mode = session.mode,
            opponentName = session.opponentName,
            opponentPersona = session.opponentPersona,
            conversationId = session.conversationId,
            access = session.access,
            notice = null
        )
    }

    private fun startSession(
        game: BoardGameType,
        mode: BoardGameMode,
        opponentName: String,
        opponentPersona: String?,
        conversationId: String?,
        access: ApiAccess.Resolved?,
        notice: String?
    ) {
        val userStone = if (Random.nextBoolean()) Stone.BLACK else Stone.WHITE
        val session = BoardSession(
            id = IdGenerator.newUuid(),
            gameType = game,
            mode = mode,
            opponentName = opponentName,
            opponentPersona = opponentPersona,
            userStone = userStone,
            llmEnabled = access != null,
            access = access,
            board = BoardState(gameType = game),
            chat = emptyList(),
            status = BoardStatus.Playing,
            conversationId = conversationId,
            thinking = false,
            notice = notice
        )
        _uiState.update {
            it.copy(
                session = session,
                route = BoardRoute.Playing(session.id),
                inviteChecking = false
            )
        }
        if (session.board.current != userStone) {
            requestOpponentMove(session)
        }
    }

    private fun requestOpponentMove(session: BoardSession) {
        if (session.status != BoardStatus.Playing) return
        _uiState.update { it.copy(session = it.session?.copy(thinking = true, notice = null)) }
        viewModelScope.launch {
            delay(if (session.board.moveCount == 0) AI_THINK_DELAY_FIRST_MS else AI_THINK_DELAY_MS)
            val latest = _uiState.value.session ?: return@launch
            if (latest.status != BoardStatus.Playing) return@launch
            // 开局即由对方落子 = 对方执黑先行（用户执白），落子后给出明确提示，避免"灵异棋"疑惑
            val isAutoFirstMove = latest.board.moveCount == 0

            var fallbackNotice: String? = null
            var llmMove: LlmMove? = null
            if (latest.llmEnabled) {
                llmMove = latest.access?.let { access ->
                    BoardLlmClient(access.toLlmGateway(chatApi))
                        .requestMove(
                            latest.board,
                            latest.opponentName,
                            latest.opponentPersona,
                            latest.chat.map { GameChatTurn(it.fromUser, it.text) }
                        )
                }
                // LLM 调用彻底失败（重试后仍不可用）→ 交给本地棋力代下，并明确告知用户
                if (llmMove == null) {
                    fallbackNotice = "AI 接口暂时不可用，本手由本地棋力代下"
                }
            }

            var moveOutcome: MoveOutcome? = null
            var passOutcome: PassOutcome? = null
            if (llmMove is LlmMove.Place) {
                val outcome = latest.board.applyMove(llmMove.move)
                if (outcome is MoveOutcome.Played) {
                    moveOutcome = outcome
                } else if (outcome is MoveOutcome.Ko) {
                    fallbackNotice = "对方落子触发打劫禁手，已自动兜底"
                } else {
                    fallbackNotice = "对方给出的落子不合法，已自动兜底"
                }
            } else if (llmMove is LlmMove.Pass && latest.gameType.isGo) {
                passOutcome = latest.board.pass()
            }

            if (moveOutcome == null && passOutcome == null) {
                val botMove = BoardBot.nextMove(latest.board)
                moveOutcome = botMove?.let { latest.board.applyMove(it) } as? MoveOutcome.Played
                if (moveOutcome == null && latest.gameType.isGo) {
                    passOutcome = latest.board.pass()
                }
            }

            val newBoard: BoardState
            val finished: Pair<Stone?, String>?
            when {
                moveOutcome is MoveOutcome.Played -> {
                    newBoard = moveOutcome.state
                    finished = if (moveOutcome.gameOver) moveOutcome.winner to moveOutcome.state.endReason else null
                }
                passOutcome is PassOutcome.Done -> {
                    newBoard = passOutcome.state
                    finished = if (passOutcome.gameOver) passOutcome.winner to "双方停一手" else null
                }
                else -> {
                    newBoard = latest.board
                    finished = null
                }
            }

            // 竞态防护：协程执行期间用户可能已认输/退出/重开，校验会话仍为同一局且对局未结束
            val current = _uiState.value.session
            if (current == null || current.id != latest.id || current.status != BoardStatus.Playing) {
                return@launch
            }
            if (finished != null) {
                finishGame(current.copy(board = newBoard, thinking = false), finished.first, finished.second, newBoard)
            } else {
                _uiState.update { st ->
                    if (st.session?.id != current.id) return@update st
                    val notice = fallbackNotice
                        ?: if (isAutoFirstMove) "你执白，${latest.opponentName}执黑先行，已自动落子" else null
                    st.copy(session = st.session.copy(board = newBoard, thinking = false, notice = notice))
                }
            }
        }
    }

    private fun finishGame(session: BoardSession, winner: Stone?, reason: String, board: BoardState) {
        val score = if (session.gameType.isGo && reason != "认输") board.score() else null
        val winnerName = winner?.let { if (it == session.userStone) "你" else session.opponentName }
        val summary = buildGameSummary(session, winner, reason, board, score)
        val finished = session.copy(
            board = board,
            thinking = false,
            status = BoardStatus.Finished(winner, winnerName, reason, score, summary)
        )
        _uiState.update { it.copy(session = finished) }
        session.conversationId?.let { convId ->
            viewModelScope.launch {
                sessionRepository.recordGameMemory(convId, summary)
            }
        }
    }

    private fun buildGameSummary(
        session: BoardSession,
        winner: Stone?,
        reason: String,
        board: BoardState,
        score: GoScore?
    ): String {
        val result = when {
            winner == null -> "平局"
            winner == session.userStone -> "你获胜"
            else -> "「${session.opponentName}」获胜"
        }
        val scoreText = score?.let { "；黑 $it.black : 白 $it.white（白贴 7.5）" } ?: ""
        return "《棋盘·${session.gameType.displayName}》对局：你执${session.userStone.label}，$result（$reason，共 ${board.moveCount} 手$scoreText）。"
    }

    private companion object {
        const val LOCAL_BOT_CHAT_NOTICE_PREFIX = "（本地电脑）"
        /** 对方落子前的"思考"延迟：首手略长（让用户看清自动先手），后续统一短延迟。 */
        const val AI_THINK_DELAY_FIRST_MS = 900L
        const val AI_THINK_DELAY_MS = 500L
    }
}

class BoardViewModelFactory(
    private val sessionRepository: MiniAppSessionRepository,
    private val characterRepository: CharacterRepository,
    private val inviteManager: MiniAppInviteManager,
    private val chatApi: ChatApi
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return BoardViewModel(
            sessionRepository,
            characterRepository,
            inviteManager,
            chatApi
        ) as T
    }
}
