package com.quiddity.app.ui.chat

import androidx.lifecycle.viewModelScope
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.data.repo.ConversationRepository
import com.quiddity.app.data.repo.SettingsRepository
import com.quiddity.app.data.repo.TimeLibraryRepository.GenerationOutcome
import com.quiddity.app.di.ServiceLocator
import com.quiddity.app.domain.ApiCatalogManager
import com.quiddity.app.domain.TimeLibraryEngine
import com.quiddity.app.util.QuiddityConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

internal class SettingsController(
    private val state: ChatViewModelState,
    private val conversationRepository: ConversationRepository,
    private val settingsRepository: SettingsRepository,
    private val apiCatalogManager: ApiCatalogManager,
    private val viewModelScope: CoroutineScope,
    private val persona: PersonaController
) {
    private val _errorEvent get() = state._errorEvent
    private val _inputBarText get() = state._inputBarText
    private val _timeLibraryHint get() = state._timeLibraryHint
    private val conversation get() = state.conversation
    private var sendDelayJob get() = state.sendDelayJob; set(value) { state.sendDelayJob = value }
    private var lastInputEditAt get() = state.lastInputEditAt; set(value) { state.lastInputEditAt = value }
    fun updateContextLimit(limit: Int) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            val clamped = limit.coerceIn(
                QuiddityConstants.MIN_CONTEXT_LIMIT,
                QuiddityConstants.MAX_CONTEXT_LIMIT
            )
            val newConv = if (conv.memoryBankEnabled) {
                // 同步压缩轮数：跟随上下文记忆轮数，但限制在合法范围内
                val syncRounds = clamped.coerceIn(
                    QuiddityConstants.MIN_MEMORY_BANK_ROUNDS,
                    QuiddityConstants.MAX_MEMORY_BANK_ROUNDS
                )
                conv.copy(contextLimit = clamped, memoryBankRounds = syncRounds)
            } else {
                conv.copy(contextLimit = clamped)
            }
            conversationRepository.updateConversation(newConv)
        }
    }
    fun updateTemperature(value: Double?) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            val clamped = value?.coerceIn(
                QuiddityConstants.MIN_TEMPERATURE,
                QuiddityConstants.MAX_TEMPERATURE
            )
            conversationRepository.updateConversation(conv.copy(temperature = clamped))
        }
    }
    fun updateWebSearchEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            conversationRepository.updateConversation(conv.copy(webSearchEnabled = enabled))
        }
    }
    fun updateThinkingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            conversationRepository.updateConversation(conv.copy(thinkingEnabled = enabled))
        }
    }
    fun updateThinkingDepth(depth: String) {
        val safe = when (depth) {
            QuiddityConstants.THINKING_DEPTH_DEEP -> depth
            else -> QuiddityConstants.THINKING_DEPTH_SHALLOW
        }
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            if (conv.thinkingDepth == safe) return@launch
            conversationRepository.updateConversation(conv.copy(thinkingDepth = safe))
        }
    }
    fun updateGroupContextLimit(limit: Int) {
        viewModelScope.launch {
            val group = conversation.value ?: return@launch
            if (group.type != ConversationType.GROUP) return@launch
            val clamped = limit.coerceIn(
                QuiddityConstants.GROUP_MIN_CONTEXT_LIMIT,
                QuiddityConstants.GROUP_MAX_CONTEXT_LIMIT
            )
            conversationRepository.updateConversation(group.copy(groupContextLimit = clamped))
        }
    }
    fun updateGroupStopMode(mode: String) {
        viewModelScope.launch {
            val group = conversation.value ?: return@launch
            if (group.type != ConversationType.GROUP) return@launch
            conversationRepository.updateConversation(group.copy(stopMode = mode))
        }
    }
    fun updateGroupBackground(text: String, mode: String) {
        viewModelScope.launch {
            val group = conversation.value ?: return@launch
            if (group.type != ConversationType.GROUP) return@launch
            conversationRepository.updateConversation(
                group.copy(groupBackground = text.trim(), groupBackgroundMode = mode)
            )
        }
    }
    fun addGroupMembers(ids: List<String>, onDone: (Result<Unit>, List<Pair<String, String>>) -> Unit) {
        viewModelScope.launch {
            val group = conversation.value ?: return@launch
            if (group.type != ConversationType.GROUP) return@launch
            val current = group.memberConversationIds
            val newIds = ids.filter { it !in current }
            val failures = mutableListOf<Pair<String, String>>()
            val passed = mutableListOf<String>()
            for (id in newIds) {
                val member = conversationRepository.getConversation(id) ?: continue
                val result = conversationRepository.validateGroupMember(member)
                if (result.isFailure) {
                    val name = member.persona.name.ifBlank { member.title }
                    failures += id to "${result.exceptionOrNull()?.message ?: "校验失败"}（$name）"
                } else {
                    passed += id
                }
            }
            if (passed.isEmpty()) {
                onDone(
                    Result.failure(IllegalStateException(
                        failures.joinToString("\n") { (_, reason) -> reason }
                    )),
                    failures
                )
                return@launch
            }
            val target = (current + passed).distinct()
            if (target.size > QuiddityConstants.GROUP_MAX_MEMBERS) {
                onDone(
                    Result.failure(IllegalStateException("群聊成员最多 ${QuiddityConstants.GROUP_MAX_MEMBERS} 个")),
                    failures
                )
                return@launch
            }
            conversationRepository.updateConversation(group.copy(memberConversationIds = target))
            onDone(Result.success(Unit), failures)
        }
    }
    fun setMemberApi(memberId: String, catalogId: String?) {
        viewModelScope.launch {
            val member = conversationRepository.getConversation(memberId) ?: return@launch
            conversationRepository.updateConversation(member.copy(apiCatalogId = catalogId))
        }
    }
    fun removeGroupMember(memberId: String) {
        viewModelScope.launch {
            val group = conversation.value ?: return@launch
            if (group.type != ConversationType.GROUP) return@launch
            if (group.memberConversationIds.size <= 1) {
                _errorEvent.value = "群聊至少保留 1 个成员"
                return@launch
            }
            conversationRepository.updateConversation(
                group.copy(memberConversationIds = group.memberConversationIds - memberId)
            )
        }
    }
    fun resetContextLimitToTierDefault() {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            val tier = persona.resolveCurrentTier()
            val defaultLimit = apiCatalogManager.defaultContextLimitForTier(tier)
            val newConv = if (conv.memoryBankEnabled) {
                val syncRounds = defaultLimit.coerceIn(
                    QuiddityConstants.MIN_MEMORY_BANK_ROUNDS,
                    QuiddityConstants.MAX_MEMORY_BANK_ROUNDS
                )
                conv.copy(contextLimit = defaultLimit, memoryBankRounds = syncRounds)
            } else {
                conv.copy(contextLimit = defaultLimit)
            }
            conversationRepository.updateConversation(newConv)
        }
    }
    fun updateMemoryBankEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            val newConv = if (enabled) {
                val syncRounds = conv.contextLimit.coerceIn(
                    QuiddityConstants.MIN_MEMORY_BANK_ROUNDS,
                    QuiddityConstants.MAX_MEMORY_BANK_ROUNDS
                )
                conv.copy(memoryBankEnabled = true, memoryBankRounds = syncRounds)
            } else {
                conv.copy(memoryBankEnabled = false)
            }
            conversationRepository.updateConversation(newConv)
        }
    }
    fun updateMemoryBankRounds(rounds: Int) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            val clamped = rounds.coerceIn(
                QuiddityConstants.MIN_MEMORY_BANK_ROUNDS,
                QuiddityConstants.MAX_MEMORY_BANK_ROUNDS
            )
            conversationRepository.updateConversation(conv.copy(memoryBankRounds = clamped))
        }
    }
    suspend fun checkAndUpdateModelContext() {
        val conv = conversation.value ?: return
        val settings = settingsRepository.currentSnapshot()
        val entry = settings.catalog
            .firstOrNull { it.id == conv.apiCatalogId }
            ?: settings.catalog.firstOrNull { it.id == settings.activeCatalogId }
            ?: return
        applyEntrySelection(conv, entry, explicit = false)
    }
    suspend fun accumulateTokenUsage(tokenCount: Int) {
        if (tokenCount <= 0) return
        val conv = conversation.value ?: return
        conversationRepository.updateConversation(
            conv.copy(sessionTokenUsed = conv.sessionTokenUsed + tokenCount)
        )
    }
    suspend fun applyEntrySelection(
        conv: Conversation,
        entry: com.quiddity.app.data.model.ApiCatalogEntry,
        explicit: Boolean
    ) {
        val tier = apiCatalogManager.getModelTier(entry.apiModel, entry.providerId)
        val tierDefaultContext = apiCatalogManager.defaultContextLimitForTier(tier)
        val modelChanged = conv.lastUsedModel != null && conv.lastUsedModel != entry.apiModel
        val apiChanged = conv.tokenCountApiId != null && conv.tokenCountApiId != entry.id

        if (!explicit && !apiChanged && !modelChanged && conv.tokenCountApiId != null) return

        val shouldResetContext = explicit || modelChanged || conv.lastUsedModel == null
        val newContextLimit = if (shouldResetContext) tierDefaultContext else conv.contextLimit
        val syncRounds = if (conv.memoryBankEnabled && shouldResetContext) {
            newContextLimit.coerceIn(
                QuiddityConstants.MIN_MEMORY_BANK_ROUNDS,
                QuiddityConstants.MAX_MEMORY_BANK_ROUNDS
            )
        } else {
            conv.memoryBankRounds
        }
        // 切换到不支持服务端搜索的模型时自动关闭官方联网搜索，避免开关状态与实际能力不一致
        val newWebSearch = if (conv.webSearchEnabled) {
            apiCatalogManager.supportsServerWebSearch(entry)
        } else {
            false
        }
        conversationRepository.updateConversation(
            conv.copy(
                apiCatalogId = if (explicit) entry.id else conv.apiCatalogId,
                sessionTokenUsed = 0,
                tokenCountApiId = entry.id,
                lastUsedModel = entry.apiModel,
                contextLimit = newContextLimit,
                memoryBankRounds = syncRounds,
                webSearchEnabled = newWebSearch
            )
        )
    }
    fun setConversationApi(catalogId: String) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            val settings = settingsRepository.currentSnapshot()
            val entry = settings.catalog.firstOrNull { it.id == catalogId } ?: return@launch
            applyEntrySelection(conv, entry, explicit = true)
        }
    }
    fun updateInputText(text: String) {
        if (_inputBarText.value == text) return
        _inputBarText.value = text
        lastInputEditAt = System.currentTimeMillis()
    }
    fun cancelPendingSend() {
        sendDelayJob?.cancel()
        sendDelayJob = null
    }
    fun setActiveMessageEnabled(enabled: Boolean) {
        if (enabled) {
            _timeLibraryHint.value = "正在生成今日时间库…"
        }
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            val outcome = ServiceLocator.timeLibraryRepository.setConversationEnabled(conv, enabled)
            if (enabled) {
                val times = conversation.value?.timeLibrary.orEmpty()
                _timeLibraryHint.value = when (outcome) {
                    GenerationOutcome.Triggered ->
                        "时间库已生成：${times.joinToString("、") { it.time }}"
                    GenerationOutcome.TriggeredSilent ->
                        "时间库为空：AI 判断今天不需要主动发消息"
                    GenerationOutcome.Failed ->
                        "时间库生成失败：请检查模型接口配置后重试"
                    GenerationOutcome.UpToDate ->
                        "今日时间库已就绪"
                    GenerationOutcome.Generating ->
                        "时间库生成中，请稍候"
                    GenerationOutcome.NotEnabled -> ""
                }
            }
        }
    }
    fun markTimeLibraryUnlocked() {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            if (!conv.timeLibraryPasswordUnlocked) {
                conversationRepository.updateConversation(conv.copy(timeLibraryPasswordUnlocked = true))
            }
        }
    }
    fun updateTimeLibrary(times: List<String>, disabledSlots: List<Int>) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            ServiceLocator.timeLibraryRepository.saveTimeLibrary(conv, times, disabledSlots)
        }
    }
    fun ensureTimeLibraryGenerated() {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            // 全局总开关关闭：静默跳过（不生成、不提示）
            if (!settingsRepository.currentSnapshot().proactiveMessageEnabled) return@launch
            if (!conv.activeMessageEnabled) return@launch
            val today = java.time.LocalDate.now().toString()
            if (!TimeLibraryEngine.shouldGenerate(true, conv.timeLibraryGeneratedDate, today)) return@launch
            _timeLibraryHint.value = "正在整理前一天的记忆！"
            val outcome = ServiceLocator.timeLibraryRepository.ensureLibraryGeneratedToday(conv.id)
            val times = conversation.value?.timeLibrary.orEmpty()
            _timeLibraryHint.value = when (outcome) {
                GenerationOutcome.Triggered ->
                    "时间库已生成：${times.joinToString("、") { it.time }}"
                GenerationOutcome.TriggeredSilent ->
                    "时间库为空：AI 判断今天不需要主动发消息"
                GenerationOutcome.Failed ->
                    "时间库生成失败：请检查模型接口配置后重试"
                else -> null
            }
        }
    }
    fun consumeTimeLibraryHint() {
        _timeLibraryHint.value = null
    }
}
