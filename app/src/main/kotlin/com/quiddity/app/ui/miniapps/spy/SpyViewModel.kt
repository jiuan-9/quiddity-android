package com.quiddity.app.ui.miniapps.spy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quiddity.app.data.model.Character
import com.quiddity.app.data.remote.ChatApi
import com.quiddity.app.data.repo.ApiAccess
import com.quiddity.app.data.repo.CharacterRepository
import com.quiddity.app.data.repo.MiniAppSessionRepository
import com.quiddity.app.data.repo.SettingsRepository
import com.quiddity.app.domain.spy.SpyDeal
import com.quiddity.app.domain.spy.SpyGame
import com.quiddity.app.domain.spy.SpyGameMode
import com.quiddity.app.domain.spy.SpyGameState
import com.quiddity.app.domain.spy.SpyPhase
import com.quiddity.app.domain.spy.SpyPkSpeak
import com.quiddity.app.domain.spy.SpyPkVoteOutcome
import com.quiddity.app.domain.spy.SpyPlayer
import com.quiddity.app.domain.spy.SpyPlayerKind
import com.quiddity.app.domain.spy.SpyRole
import com.quiddity.app.domain.spy.SpyScriptBot
import com.quiddity.app.domain.spy.SpySpeak
import com.quiddity.app.domain.spy.SpyVoteOutcome
import com.quiddity.app.domain.spy.SpyWordBank
import com.quiddity.app.ui.miniapps.MiniAppInviteManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/*
 * 谁是卧底状态机：配置 → 发牌 → 轮转发言 → 投票 → 结算。
 *
 * 多玩家轮转由 [turnJob] 单协程驱动：真人轮到后退出等待输入，
 * LLM/脚本轮到后自动行动并继续，从根本上避免自锁/重入。
 */
sealed interface SpyRoute {
    data object Setup : SpyRoute
    data object Deal : SpyRoute
    data class Playing(val sessionId: String) : SpyRoute
    data object Result : SpyRoute
}

data class SpySetupState(
    val llmCharacters: List<Character> = emptyList(),
    val category: String? = null,
    val mode: SpyGameMode = SpyGameMode.CLASSIC
)

/** 一轮投票结束后的揭晓事件：谁出局、是否平票（含 PK 平票）、各人得票。 */
data class SpyVoteEvent(
    val round: Int,
    val eliminatedIndex: Int?,
    val tie: Boolean,
    val gameOver: Boolean,
    val tallies: Map<Int, Int>,
    val pk: Boolean = false
)

data class SpyUiState(
    val route: SpyRoute = SpyRoute.Setup,
    val setup: SpySetupState = SpySetupState(),
    val game: SpyGameState? = null,
    val userIndex: Int = 0,
    val userAvatarUri: String? = null,
    val thinking: Boolean = false,
    val checked: Boolean = false,
    val notice: String? = null,
    val lastVoteEvent: SpyVoteEvent? = null,
    val lastSetup: SpySetupState? = null,
    val access: ApiAccess.Resolved? = null,
    val conversationId: String? = null
)

class SpyViewModel(
    private val characterRepository: CharacterRepository,
    private val inviteManager: MiniAppInviteManager,
    private val sessionRepository: MiniAppSessionRepository,
    private val chatApi: ChatApi,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpyUiState())
    val uiState: StateFlow<SpyUiState> = _uiState.asStateFlow()

    val characters = characterRepository.characters

    private var turnJob: Job? = null
    private val random = Random.Default

    // ============ 配置 ============

    fun removeLlmCharacter(characterId: String) {
        val setup = _uiState.value.setup
        _uiState.update {
            it.copy(setup = setup.copy(llmCharacters = setup.llmCharacters.filterNot { c -> c.id == characterId }))
        }
    }

    fun reselectLlmFrom(character: Character) {
        // 强制 3 名 LLM：最多选 3，移除最早加入的以容纳新选择
        val setup = _uiState.value.setup
        val filtered = setup.llmCharacters.filterNot { it.id == character.id }
        val next = if (character in filtered) {
            filtered + character
        } else {
            (filtered + character).takeLast(MAX_LLM)
        }
        _uiState.update { it.copy(setup = setup.copy(llmCharacters = next)) }
    }

    /** 选择本局词库分类；null 表示全部。 */
    fun selectCategory(category: String?) {
        _uiState.update { it.copy(setup = it.setup.copy(category = category)) }
    }

    /** 选择玩法模式：经典 / 双卧底 / 白板。 */
    fun selectMode(mode: SpyGameMode) {
        _uiState.update { it.copy(setup = it.setup.copy(mode = mode)) }
    }

    /** 开始游戏：随机选一名已加入角色，用它的 API 生成一组词并发牌（失败回退内置词）。 */
    fun startGame() {
        val setup = _uiState.value.setup
        val llmChars = setup.llmCharacters.take(MAX_LLM)
        if (llmChars.size < MIN_LLM_PLAYERS) return

        val players = mutableListOf(
            SpyPlayer(
                index = 0,
                name = "你",
                kind = SpyPlayerKind.USER,
                avatarUri = settingsRepository.currentSnapshot().userAvatarUri
            )
        )
        llmChars.forEachIndexed { i, c ->
            players += SpyPlayer(
                index = players.size,
                name = c.persona.name.ifBlank { "角色${i + 1}" },
                kind = SpyPlayerKind.LLM,
                persona = inviteManager.buildPersonaText(c, null),
                avatarUri = c.aiAvatarUri ?: c.persona.aiAvatarUri
            )
        }

        _uiState.update { it.copy(checked = true, lastSetup = setup) }
        viewModelScope.launch {
            try {
                // 随机挑一名已加入角色：既作为连接检测对象，也作为词库生成来源
                val picked = llmChars.randomOrNull(random) ?: llmChars.firstOrNull()
                val access: ApiAccess.Resolved?
                val conversationId: String?
                if (picked != null) {
                    val invite = inviteManager.prepare(
                        character = picked,
                        inviteBubbleText = { name -> "你邀请了「$name」一起玩《谁是卧底》" },
                        miniAppId = "spy",
                        miniAppTitle = "谁是卧底"
                    )
                    access = invite.access
                    conversationId = invite.conversationId
                } else {
                    access = null
                    conversationId = null
                }

                // 检查期用户可能已返回/离开设置页，校验仍处于 Setup 再继续
                if (_uiState.value.route != SpyRoute.Setup) return@launch
                val notice = if (picked != null && access == null) "API 连接失败，已改用内置词库" else null

                // 用所选名册自动生成本局词库；失败回退内置词
                val category = setup.category
                val wordPair = if (access != null) {
                    SpyLlmClient(access.toSpyGateway(chatApi))
                        .generateWordPairs(WORD_GEN_COUNT, category ?: "").firstOrNull()
                } else {
                    null
                } ?: SpyWordBank.randomPair(random, category)

                val dealt = SpyGame.deal(players, wordPair, random, setup.mode)
                if (dealt !is SpyDeal.Done) return@launch
                // 词库生成可能耗时（LLM），进入发牌页前再次校验路由
                if (_uiState.value.route != SpyRoute.Setup) return@launch
                _uiState.update {
                    it.copy(
                        route = SpyRoute.Deal,
                        game = dealt.state,
                        userIndex = 0,
                        userAvatarUri = settingsRepository.currentSnapshot().userAvatarUri,
                        checked = false,
                        notice = notice,
                        access = access,
                        conversationId = conversationId
                    )
                }
            } finally {
                _uiState.update { it.copy(checked = false) }
            }
        }
    }

    // ============ 发牌 → 开局 ============

    /** 用户看完自己的词后开始第一轮发言。 */
    fun startFirstRound() {
        val state = _uiState.value
        val game = state.game ?: return
        if (state.route != SpyRoute.Deal) return
        val started = SpyGame.startSpeaking(game)
        _uiState.update {
            it.copy(
                route = SpyRoute.Playing(game.id),
                game = started
            )
        }
        driveTurns()
    }

    // ============ 轮转驱动 ============

    private fun driveTurns() {
        if (turnJob?.isActive == true) return
        turnJob = viewModelScope.launch {
            while (true) {
                val state = _uiState.value
                val game = state.game ?: break
                when (game.phase) {
                    SpyPhase.SPEAK -> {
                        val speaker = game.nextSpeaker() ?: break
                        if (speaker.kind == SpyPlayerKind.USER) {
                            break // 真人发言：等待 submitSpeaking
                        }
                        processAITurn(speaker)
                    }
                    SpyPhase.VOTE -> {
                        val voter = game.nextVoter() ?: break
                        if (voter.kind == SpyPlayerKind.USER) {
                            break // 真人投票：等待 submitVote
                        }
                        processAIVote(voter)
                    }
                    SpyPhase.PK -> {
                        val speaker = game.nextPkSpeaker()
                        if (speaker != null) {
                            if (speaker.kind == SpyPlayerKind.USER) {
                                break // 真人 PK 发言：等待 submitSpeaking
                            }
                            processAITurn(speaker, pk = true)
                        } else {
                            val voter = game.nextPkVoter() ?: break
                            if (voter.kind == SpyPlayerKind.USER) {
                                break // 真人 PK 投票：等待 submitVote
                            }
                            processAIVote(voter, pk = true)
                        }
                    }
                    else -> break
                }
            }
            turnJob = null
        }
    }

    /** 处理 AI 玩家一次发言（LLM 优先，脚本兜底），完成后继续驱动。 */
    private suspend fun processAITurn(speaker: SpyPlayer, pk: Boolean = false) {
        _uiState.update { it.copy(thinking = true) }
        val game = _uiState.value.game ?: run { _uiState.update { it.copy(thinking = false) }; return@processAITurn }
        delay(if (pk || game.roundSpeeches.isEmpty()) AI_THINK_FIRST_MS else AI_THINK_MS)
        _uiState.update { it.copy(thinking = true) }

        val text = when (speaker.kind) {
            SpyPlayerKind.LLM -> {
                val access = _uiState.value.access
                if (pk) {
                    access?.let { a ->
                        SpyLlmClient(a.toSpyGateway(chatApi)).requestPkSpeech(
                            player = speaker,
                            round = game.round,
                            pkCandidates = game.pkCandidates.mapNotNull { idx -> game.player(idx)?.let { it.index to it.name } },
                            pkSpeeches = game.pkSpeeches,
                            playerName = speaker.name,
                            mode = game.mode
                        )
                    } ?: SpyScriptBot.speak(game, speaker.index, random, pk = true)
                } else {
                    access?.let { a ->
                        SpyLlmClient(a.toSpyGateway(chatApi)).requestSpeech(
                            player = speaker,
                            round = game.round,
                            speeches = game.roundSpeeches,
                            playerName = speaker.name,
                            mode = game.mode
                        )
                    } ?: SpyScriptBot.speak(game, speaker.index, random)
                }
            }
            else -> SpyScriptBot.speak(game, speaker.index, random, pk = pk)
        }

        val latest = _uiState.value.game ?: run { _uiState.update { it.copy(thinking = false) }; return@processAITurn }
        if (pk) {
            val result = SpyGame.pkSpeak(latest, speaker.index, text ?: "")
            if (result is SpyPkSpeak.Done) {
                _uiState.update { it.copy(game = result.state, thinking = false) }
            } else {
                // 发言被拒（空文本/阶段不符）：复位 thinking，避免卡住
                _uiState.update { it.copy(thinking = false) }
            }
        } else {
            val result = SpyGame.speak(latest, speaker.index, text ?: "")
            if (result is SpySpeak.Done) {
                _uiState.update { it.copy(game = result.state, thinking = false) }
            } else {
                _uiState.update { it.copy(thinking = false) }
            }
        }
    }

    /** 处理 AI 玩家一次投票（LLM 优先，脚本兜底），完成后继续驱动。 */
    private suspend fun processAIVote(voter: SpyPlayer, pk: Boolean = false) {
        _uiState.update { it.copy(thinking = true) }
        val game = _uiState.value.game ?: run { _uiState.update { it.copy(thinking = false) }; return@processAIVote }
        delay(AI_THINK_MS)
        val candidates = if (pk) {
            game.pkCandidates.mapNotNull { idx -> game.player(idx)?.let { it.index to it.name } }
        } else {
            game.alivePlayers.filter { it.index != voter.index }
                .map { it.index to it.name }
        }

        val voteIndex = when (voter.kind) {
            SpyPlayerKind.LLM -> {
                val access = _uiState.value.access
                if (pk) {
                    access?.let { a ->
                        SpyLlmClient(a.toSpyGateway(chatApi)).requestPkVote(
                            player = voter,
                            round = game.round,
                            candidates = candidates,
                            speeches = game.roundSpeeches + game.pkSpeeches,
                            voterName = voter.name,
                            mode = game.mode
                        )
                    } ?: SpyScriptBot.pkVote(game, voter.index, random)
                } else {
                    access?.let { a ->
                        SpyLlmClient(a.toSpyGateway(chatApi)).requestVote(
                            player = voter,
                            round = game.round,
                            candidates = candidates,
                            speeches = game.roundSpeeches,
                            voterName = voter.name,
                            mode = game.mode
                        )
                    } ?: SpyScriptBot.vote(game, voter.index, random)
                }
            }
            else -> if (pk) SpyScriptBot.pkVote(game, voter.index, random) else SpyScriptBot.vote(game, voter.index, random)
        }

        val latest = _uiState.value.game ?: run { _uiState.update { it.copy(thinking = false) }; return@processAIVote }
        if (pk) {
            val target = voteIndex ?: SpyScriptBot.pkVote(latest, voter.index, random)
            val outcome = if (target != null) {
                SpyGame.pkVote(latest, voter.index, target)
            } else {
                null
            }
            if (outcome is SpyPkVoteOutcome.Done) {
                applyPkVoteOutcome(outcome)
            } else {
                _uiState.update { it.copy(thinking = false) }
            }
        } else {
            val target = voteIndex ?: SpyScriptBot.vote(latest, voter.index, random)
            val outcome = if (target != null) {
                SpyGame.vote(latest, voter.index, target)
            } else {
                null
            }
            if (outcome is SpyVoteOutcome.Done) {
                applyVoteOutcome(outcome)
            } else {
                _uiState.update { it.copy(thinking = false) }
            }
        }
    }

    /** 投票落子：仅当整轮投票完成（有计票结果）时展示揭晓动画，再进入下一轮或结算页。 */
    private suspend fun applyVoteOutcome(outcome: SpyVoteOutcome.Done) {
        val isTallied = outcome.eliminatedIndex != null || outcome.tie || outcome.gameOver
        _uiState.update { it.copy(game = outcome.state, thinking = false) }
        if (!isTallied) return
        val voteRound = outcome.state.votes.lastOrNull()?.round ?: outcome.state.round
        val event = SpyVoteEvent(
            round = voteRound,
            eliminatedIndex = outcome.eliminatedIndex,
            tie = outcome.tie,
            gameOver = outcome.gameOver,
            tallies = outcome.state.votes
                .filter { it.round == voteRound }
                .groupBy { it.toIndex }
                .mapValues { it.value.size }
        )
        revealVote(event)
    }

    /** PK 投票落子：展示揭晓动画后进入下一轮或结算页。 */
    private suspend fun applyPkVoteOutcome(outcome: SpyPkVoteOutcome.Done) {
        val isTallied = outcome.eliminatedIndex != null || outcome.tie || outcome.gameOver
        _uiState.update { it.copy(game = outcome.state, thinking = false) }
        if (!isTallied) return
        val voteRound = outcome.state.pkVotes.lastOrNull()?.round ?: outcome.state.round
        val event = SpyVoteEvent(
            round = voteRound,
            eliminatedIndex = outcome.eliminatedIndex,
            tie = outcome.tie,
            gameOver = outcome.gameOver,
            tallies = outcome.state.pkVotes
                .filter { it.round == voteRound }
                .groupBy { it.toIndex }
                .mapValues { it.value.size },
            pk = true
        )
        revealVote(event)
    }

    /** 揭晓动画：短暂展示后若本局结束则进入结算页。 */
    private suspend fun revealVote(event: SpyVoteEvent) {
        _uiState.update { it.copy(lastVoteEvent = event) }
        delay(VOTE_REVEAL_MS)
        val latest = _uiState.value.game
        if (event.gameOver && latest?.phase == SpyPhase.FINISHED) {
            finalizeGame(latest)
        }
    }

    // ============ 真人输入 ============

    fun submitSpeaking(playerIndex: Int, text: String) {
        val state = _uiState.value
        val game = state.game ?: return
        when (game.phase) {
            SpyPhase.SPEAK -> {
                if (game.nextSpeaker()?.index != playerIndex) return
                val result = SpyGame.speak(game, playerIndex, text)
                if (result is SpySpeak.Done) {
                    _uiState.update { it.copy(game = result.state, thinking = false) }
                    driveTurns()
                }
            }
            SpyPhase.PK -> {
                if (game.nextPkSpeaker()?.index != playerIndex) return
                val result = SpyGame.pkSpeak(game, playerIndex, text)
                if (result is SpyPkSpeak.Done) {
                    _uiState.update { it.copy(game = result.state, thinking = false) }
                    driveTurns()
                }
            }
            else -> return
        }
    }

    fun submitVote(playerIndex: Int, targetIndex: Int) {
        val state = _uiState.value
        val game = state.game ?: return
        when (game.phase) {
            SpyPhase.VOTE -> {
                if (game.nextVoter()?.index != playerIndex) return
                val outcome = SpyGame.vote(game, playerIndex, targetIndex)
                if (outcome is SpyVoteOutcome.Done) {
                    viewModelScope.launch {
                        applyVoteOutcome(outcome)
                        driveTurns()
                    }
                }
            }
            SpyPhase.PK -> {
                if (game.nextPkVoter()?.index != playerIndex) return
                val outcome = SpyGame.pkVote(game, playerIndex, targetIndex)
                if (outcome is SpyPkVoteOutcome.Done) {
                    viewModelScope.launch {
                        applyPkVoteOutcome(outcome)
                        driveTurns()
                    }
                }
            }
            else -> return
        }
    }

    // ============ 结算 ============

    private fun finalizeGame(game: SpyGameState) {
        val convId = _uiState.value.conversationId
        if (convId != null) {
            val summary = buildSummary(game)
            if (summary != null) {
                viewModelScope.launch {
                    sessionRepository.recordGameMemory(convId, summary)
                    sessionRepository.appendGameLog(convId, summary, "spy", "谁是卧底")
                }
            }
        }
        _uiState.update { it.copy(route = SpyRoute.Result) }
    }

    /** 用上一局的配置直接再开一局。 */
    fun rematch() {
        val setup = _uiState.value.lastSetup ?: return
        if (setup.llmCharacters.size < MIN_LLM_PLAYERS) return
        _uiState.update { SpyUiState(setup = setup, lastSetup = setup) }
        startGame()
    }

    fun backToSetup() {
        turnJob?.cancel()
        turnJob = null
        _uiState.update { SpyUiState() }
    }

    private fun buildSummary(game: SpyGameState): String? {
        if (game.winner == null) return null
        val winner = if (game.winner == SpyRole.CIVILIAN) "平民阵营" else "卧底方"
        val reveal = buildList {
            game.spyIndices.forEach { idx -> game.player(idx)?.name?.let { add("卧底「$it」") } }
            game.blankIndex?.let { idx -> game.player(idx)?.name?.let { add("白板「$it」") } }
        }.joinToString("、")
        return "《谁是卧底·${game.mode.label}》对局结束：$winner 获胜（${reveal}，共 ${game.round} 轮）。"
    }


    private companion object {
        const val MIN_LLM_PLAYERS = 3
        const val WORD_GEN_COUNT = 1
        const val AI_THINK_FIRST_MS = 700L
        const val AI_THINK_MS = 450L
        const val VOTE_REVEAL_MS = 1600L
    }
}

class SpyViewModelFactory(
    private val characterRepository: CharacterRepository,
    private val inviteManager: MiniAppInviteManager,
    private val sessionRepository: MiniAppSessionRepository,
    private val chatApi: ChatApi,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SpyViewModel(
            characterRepository,
            inviteManager,
            sessionRepository,
            chatApi,
            settingsRepository
        ) as T
    }
}
