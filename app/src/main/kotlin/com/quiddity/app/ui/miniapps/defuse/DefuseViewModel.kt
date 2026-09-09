package com.quiddity.app.ui.miniapps.defuse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quiddity.app.data.model.Character
import com.quiddity.app.data.remote.ChatApi
import com.quiddity.app.data.repo.ApiAccess
import com.quiddity.app.data.repo.CharacterRepository
import com.quiddity.app.data.repo.ConversationRepository
import com.quiddity.app.data.repo.MiniAppSessionRepository
import com.quiddity.app.domain.defuse.DefuseChatTurn
import com.quiddity.app.domain.defuse.DefuseDifficulty
import com.quiddity.app.domain.defuse.DefuseGame
import com.quiddity.app.domain.defuse.DefuseGenerator
import com.quiddity.app.domain.defuse.DefuseLlmPrompt
import com.quiddity.app.domain.defuse.DefuseModule
import com.quiddity.app.domain.defuse.DefuseMove
import com.quiddity.app.domain.defuse.DefuseScoring
import com.quiddity.app.domain.defuse.DefuseScriptPartner
import com.quiddity.app.domain.defuse.DefuseSession
import com.quiddity.app.domain.defuse.ButtonAction
import com.quiddity.app.domain.defuse.cutWire
import com.quiddity.app.domain.defuse.pressButton
import com.quiddity.app.domain.defuse.pressKeypad
import com.quiddity.app.ui.miniapps.board.BoardInvitee
import com.quiddity.app.ui.miniapps.board.buildBoardInvitees
import com.quiddity.app.ui.miniapps.MiniAppInviteManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/*
 * 拆弹小队状态机：设置 → 拆弹 → 结算。
 * 玩家负责操作面板，搭档（LLM 或脚本）负责照手册给指令。
 */

sealed interface DefuseRoute {
    data object Setup : DefuseRoute
    data class Playing(val sessionId: String) : DefuseRoute
    data object Result : DefuseRoute
}

enum class DefuseChatSender { USER, PARTNER }

data class DefuseChatMessage(val sender: DefuseChatSender, val text: String)

enum class DefuseModuleEventKind { STRIKE, DEFUSED, EXPLODE }

data class DefuseModuleEvent(
    val kind: DefuseModuleEventKind,
    val moduleIndex: Int,
    val atMs: Long
)

enum class DefuseResultKind(val title: String, val subtitle: String) {
    WIN("双赢！", "拆弹成功，还赢过了对手队伍"),
    SAVED_LATE("拆弹成功", "但慢了一步，对手队伍先完成"),
    LOST_RACE("功亏一篑", "对手队伍先拆完了炸弹"),
    EXPLODED("轰——", "炸弹爆炸了，下次再战")
}

data class DefuseUiState(
    val route: DefuseRoute = DefuseRoute.Setup,
    val difficulty: DefuseDifficulty = DefuseDifficulty.NORMAL,
    val partner: BoardInvitee? = null,
    val partnerName: String = "内置搭档",
    val partnerAvatarUri: String? = null,
    val partnerPersona: String? = null,
    val game: DefuseGame? = null,
    val session: DefuseSession? = null,
    val messages: List<DefuseChatMessage> = emptyList(),
    val thinking: Boolean = false,
    val checking: Boolean = false,
    val notice: String? = null,
    val conversationId: String? = null,
    val access: ApiAccess.Resolved? = null,
    val startedAtMs: Long = 0L,
    val nowMs: Long = 0L,
    val opponentBoostMs: Long = 0L,
    val helpUsed: Boolean = false,
    val showManual: Boolean = false,
    val lastEvent: DefuseModuleEvent? = null,
    val finishedAtMs: Long? = null,
    val resultKind: DefuseResultKind? = null,
    val score: Int = 0
)

class DefuseViewModel(
    private val characterRepository: CharacterRepository,
    private val conversationRepository: ConversationRepository,
    private val inviteManager: MiniAppInviteManager,
    private val sessionRepository: MiniAppSessionRepository,
    private val chatApi: ChatApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(DefuseUiState())
    val uiState: StateFlow<DefuseUiState> = _uiState.asStateFlow()

    /** 搭档名单：私聊会话为主，角色库作为身份补充（与棋盘小应用一致）。 */
    val invitees: StateFlow<List<BoardInvitee>> =
        combine(conversationRepository.conversations, characterRepository.characters) { conversations, characters ->
            buildBoardInvitees(conversations, characters)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = buildBoardInvitees(
                conversationRepository.conversations.value,
                characterRepository.characters.value
            )
        )

    private var ticker: Job? = null
    private var warned30 = false
    private val random = Random.Default

    // ===== 设置 =====

    fun selectDifficulty(difficulty: DefuseDifficulty) {
        _uiState.update { it.copy(difficulty = difficulty) }
    }

    fun selectPartner(invitee: BoardInvitee) {
        _uiState.update { it.copy(partner = invitee, notice = null) }
    }

    fun selectScriptPartner() {
        _uiState.update { it.copy(partner = null, notice = null) }
    }

    fun start() {
        if (_uiState.value.checking) return
        val expectedRoute = _uiState.value.route
        _uiState.update { it.copy(checking = true) }
        viewModelScope.launch {
            try {
                val partner = _uiState.value.partner
                val difficulty = _uiState.value.difficulty
                val game = DefuseGenerator.generate(difficulty, random)
                var partnerName = "内置搭档"
                var partnerAvatarUri: String? = null
                var partnerPersona: String? = null
                var access: ApiAccess.Resolved? = null
                var conversationId: String? = null
                var notice: String? = null

                if (partner != null) {
                    val conversation = partner.conversation
                    val character = partner.character ?: conversation?.let { conv ->
                        Character(
                            id = conv.characterId ?: "defuse_${conv.id}",
                            persona = conv.persona,
                            userPersona = conv.userPersona,
                            memory = conv.memory,
                            aiAvatarUri = conv.persona.aiAvatarUri
                        )
                    }
                    if (character != null) {
                        if (partner.character != null && conversation != null && conversation.characterId == null) {
                            conversationRepository.updateConversation(conversation.copy(characterId = partner.character.id))
                        }
                        val invite = inviteManager.prepare(
                            character = character,
                            inviteBubbleText = { name -> DefuseMiniApp.inviteBubbleText(name) },
                            miniAppId = DefuseMiniApp.id,
                            miniAppTitle = DefuseMiniApp.name,
                            existingConversation = conversation
                        )
                        access = invite.access
                        conversationId = invite.conversationId
                        partnerName = invite.opponentName
                        partnerPersona = invite.opponentPersona
                        partnerAvatarUri = partner.avatarUri
                        if (access == null) {
                            notice = "未检测到可用 API，已切换为内置脚本搭档"
                        }
                    }
                }

                // 检查期用户可能已返回/离开设置页，校验目标路由后再进入对局
                if (_uiState.value.route != expectedRoute) return@launch

                val session = DefuseSession(game)
                val now = System.currentTimeMillis()
                warned30 = false
                _uiState.update {
                    it.copy(
                        route = DefuseRoute.Playing(game.id),
                        game = game,
                        session = session,
                        partnerName = partnerName,
                        partnerAvatarUri = partnerAvatarUri,
                        partnerPersona = partnerPersona,
                        messages = listOf(
                            DefuseChatMessage(
                                sender = DefuseChatSender.PARTNER,
                                text = DefuseScriptPartner.opening(partnerName)
                            )
                        ),
                        thinking = false,
                        checking = false,
                        notice = notice,
                        conversationId = conversationId,
                        access = access,
                        startedAtMs = now,
                        nowMs = now,
                        opponentBoostMs = 0L,
                        helpUsed = false,
                        showManual = false,
                        lastEvent = null,
                        finishedAtMs = null,
                        resultKind = null,
                        score = 0
                    )
                }
                startTicker()
            } finally {
                _uiState.update { it.copy(checking = false) }
            }
        }
    }

    // ===== 计时与胜负 =====

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                val state = _uiState.value
                val session = state.session ?: break
                if (state.route !is DefuseRoute.Playing) break
                val now = System.currentTimeMillis()
                val elapsed = now - state.startedAtMs
                val total = state.game?.totalTimeMs ?: break
                val opponent = state.game?.opponentTimeMs ?: break
                val remaining = total - elapsed
                if (!warned30 && remaining in 1..30_000L) {
                    warned30 = true
                    appendPartnerLine(URGENT_WARNING)
                }
                if (elapsed >= total && !session.allCleared) {
                    finish(session.copy(exploded = true), total, opponentBeaten = false)
                    break
                }
                // 「首次求助对手加速8秒」应真实生效：输局判定与进度条同口径
                // （(elapsed+boost)/opponentTimeMs 到 100% 即 elapsed = opponentTimeMs - boost）。
                if (!session.allCleared &&
                    elapsed >= (opponent - state.opponentBoostMs).coerceAtLeast(0L)
                ) {
                    finish(session, elapsed, opponentBeaten = false)
                    break
                }
                _uiState.update { it.copy(nowMs = now) }
                delay(TICK_MS)
            }
        }
    }

    /** 对局结束：结算分类、得分并写入搭档对话记忆。 */
    private fun finish(session: DefuseSession, elapsedMs: Long, opponentBeaten: Boolean) {
        ticker?.cancel()
        val state = _uiState.value
        val game = state.game ?: return
        val kind = when {
            session.exploded -> DefuseResultKind.EXPLODED
            session.allCleared && opponentBeaten -> DefuseResultKind.WIN
            session.allCleared -> DefuseResultKind.SAVED_LATE
            else -> DefuseResultKind.LOST_RACE
        }
        val remainingSeconds = ((game.totalTimeMs - elapsedMs) / 1000L).toInt().coerceAtLeast(0)
        val score = DefuseScoring.score(
            difficulty = game.difficulty,
            clearedModules = session.clearedCount,
            livesLeft = session.lives,
            remainingSeconds = remainingSeconds,
            strikes = session.strikes,
            maxCombo = session.maxCombo,
            helpUsed = state.helpUsed
        )
        val convId = state.conversationId
        if (convId != null) {
            val summary = "《拆弹小队》对局结束：${kind.title}，用时 ${elapsedMs / 1000} 秒，" +
                "拆除 ${session.clearedCount}/${game.modules.size} 个模块，" +
                "失误 ${session.strikes} 次，剩余生命 ${session.lives}，得分 $score。"
            viewModelScope.launch {
                sessionRepository.recordGameMemory(convId, summary)
                sessionRepository.appendGameLog(convId, summary, "defuse", "拆弹小队")
            }
        }
        _uiState.update {
            it.copy(
                route = DefuseRoute.Result,
                session = session,
                finishedAtMs = elapsedMs,
                resultKind = kind,
                score = score,
                lastEvent = null,
                thinking = false
            )
        }
    }

    // ===== 面板操作 =====

    fun onCutWire(index: Int) = applyMove { it.cutWire(index) }

    fun onKeypadPress(symbol: String) = applyMove { it.pressKeypad(symbol) }

    fun onButtonAction(action: ButtonAction) = applyMove { it.pressButton(action) }

    private fun applyMove(block: (DefuseSession) -> DefuseMove) {
        val state = _uiState.value
        val session = state.session ?: return
        if (state.route !is DefuseRoute.Playing) return
        if (session.exploded || session.allCleared) return
        when (val move = block(session)) {
            is DefuseMove.Defused -> {
                _uiState.update {
                    it.copy(session = move.session)
                }
                setEvent(DefuseModuleEventKind.DEFUSED, move.session.moduleIndex - 1)
                if (move.session.allCleared) {
                    val elapsed = System.currentTimeMillis() - state.startedAtMs
                    finish(
                        move.session,
                        elapsed,
                        opponentBeaten = elapsed + state.opponentBoostMs <= move.session.game.opponentTimeMs
                    )
                } else {
                    appendPartnerLine(DefuseScriptPartner.onDefused())
                }
            }
            is DefuseMove.Struck -> {
                _uiState.update { it.copy(session = move.session) }
                setEvent(DefuseModuleEventKind.STRIKE, move.session.moduleIndex)
                if (!move.session.exploded) {
                    appendPartnerLine(DefuseScriptPartner.onStrike())
                }
                if (move.session.exploded) {
                    finish(move.session, System.currentTimeMillis() - state.startedAtMs, opponentBeaten = false)
                }
            }
            is DefuseMove.Progressed -> {
                _uiState.update { it.copy(session = move.session) }
            }
            DefuseMove.Invalid -> Unit
        }
    }

    private fun setEvent(kind: DefuseModuleEventKind, moduleIndex: Int) {
        val atMs = System.currentTimeMillis()
        _uiState.update { it.copy(lastEvent = DefuseModuleEvent(kind, moduleIndex, atMs)) }
        viewModelScope.launch {
            delay(EVENT_SHOW_MS)
            val current = _uiState.value.lastEvent
            if (current?.atMs == atMs) {
                _uiState.update { it.copy(lastEvent = null) }
            }
        }
    }

    // ===== 与搭档对话 =====

    fun onReportPanel() {
        val module = _uiState.value.session?.currentModule ?: return
        sendUserMessage(DefuseLlmPrompt.buildReportText(module))
    }

    fun onSendText(text: String) {
        if (text.isBlank()) return
        sendUserMessage(text.trim())
    }

    private fun sendUserMessage(text: String) {
        val state = _uiState.value
        if (state.thinking) return
        if (state.route !is DefuseRoute.Playing) return
        val messages = state.messages + DefuseChatMessage(DefuseChatSender.USER, text)
        _uiState.update { it.copy(messages = messages, thinking = true) }
        requestPartnerReply(text, messages)
    }

    private fun requestPartnerReply(userText: String, messages: List<DefuseChatMessage>) {
        val state = _uiState.value
        val module = state.session?.currentModule ?: return
        val partnerName = state.partnerName
        viewModelScope.launch {
            val reply = if (state.access != null) {
                val system = DefuseLlmPrompt.buildSystemPrompt(
                    partnerName = partnerName,
                    persona = state.partnerPersona
                )
                val history = messages.takeLast(HISTORY_LIMIT).map {
                    DefuseChatTurn(
                        sender = if (it.sender == DefuseChatSender.USER) "你" else partnerName,
                        text = it.text
                    )
                }
                DefuseLlmClient(state.access.toDefuseGateway(chatApi))
                    .requestReply(system, DefuseLlmPrompt.buildUserMessage(module, history))
            } else {
                DefuseScriptPartner.reply(module, userText, reported = userText.contains("面板汇报"))
            }
            val latest = _uiState.value
            val text = reply ?: DefuseScriptPartner.instruction(module)
            _uiState.update {
                it.copy(
                    messages = latest.messages + DefuseChatMessage(DefuseChatSender.PARTNER, text),
                    thinking = false
                )
            }
        }
    }

    // ===== 求助手册 =====

    fun onToggleHelp() {
        val state = _uiState.value
        val show = !state.showManual
        val firstOpen = show && !state.helpUsed
        _uiState.update {
            it.copy(
                showManual = show,
                helpUsed = it.helpUsed || firstOpen,
                opponentBoostMs = it.opponentBoostMs + if (firstOpen) OPPONENT_HELP_BOOST_MS else 0L
            )
        }
    }

    // ===== 导航 =====

    fun rematch() {
        start()
    }

    fun backToSetup() {
        ticker?.cancel()
        ticker = null
        val keep = _uiState.value
        _uiState.value = DefuseUiState(difficulty = keep.difficulty, partner = keep.partner)
    }

    override fun onCleared() {
        ticker?.cancel()
        super.onCleared()
    }

    private fun appendPartnerLine(text: String) {
        _uiState.update { it.copy(messages = it.messages + DefuseChatMessage(DefuseChatSender.PARTNER, text)) }
    }

    private companion object {
        const val TICK_MS = 250L
        const val EVENT_SHOW_MS = 1500L
        const val HISTORY_LIMIT = 8
        const val OPPONENT_HELP_BOOST_MS = 8_000L
        const val URGENT_WARNING = "只剩 30 秒了，集中注意力！"
    }
}

class DefuseViewModelFactory(
    private val characterRepository: CharacterRepository,
    private val conversationRepository: ConversationRepository,
    private val inviteManager: MiniAppInviteManager,
    private val sessionRepository: MiniAppSessionRepository,
    private val chatApi: ChatApi
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DefuseViewModel(
            characterRepository,
            conversationRepository,
            inviteManager,
            sessionRepository,
            chatApi
        ) as T
    }
}
