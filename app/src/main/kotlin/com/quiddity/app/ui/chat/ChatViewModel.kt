package com.quiddity.app.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.Persona
import com.quiddity.app.data.model.PersonaCard
import com.quiddity.app.data.model.UserPersona
import com.quiddity.app.data.repo.ChatRepository
import com.quiddity.app.data.repo.ConversationRepository
import com.quiddity.app.data.repo.SettingsRepository
import com.quiddity.app.domain.ApiCatalogManager
import com.quiddity.app.domain.VisionOcrService
import com.quiddity.app.util.QuiddityConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class ChatViewModel(
    private val conversationRepository: ConversationRepository,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val apiCatalogManager: ApiCatalogManager,
    private val visionOcrService: VisionOcrService,
    private val conversationId: String,
    private val onIdle: ((String) -> Unit)? = null
) : ViewModel() {
    private val state = ChatViewModelState(conversationRepository, viewModelScope, conversationId)

    private val personaController = PersonaController(
        state = state,
        conversationRepository = conversationRepository,
        chatRepository = chatRepository,
        settingsRepository = settingsRepository,
        apiCatalogManager = apiCatalogManager,
        viewModelScope = viewModelScope
    )
    private val settingsController = SettingsController(
        state = state,
        conversationRepository = conversationRepository,
        settingsRepository = settingsRepository,
        apiCatalogManager = apiCatalogManager,
        viewModelScope = viewModelScope,
        persona = personaController
    )
    private lateinit var groupController: GroupController
    private val streamController = StreamController(
        state = state,
        conversationRepository = conversationRepository,
        chatRepository = chatRepository,
        settingsRepository = settingsRepository,
        apiCatalogManager = apiCatalogManager,
        visionOcrService = visionOcrService,
        viewModelScope = viewModelScope,
        conversationId = conversationId,
        persona = personaController,
        settingsController = settingsController,
        onIdle = onIdle
    )
    private val opsController = OpsController(
        state = state,
        conversationRepository = conversationRepository,
        viewModelScope = viewModelScope,
        conversationId = conversationId,
        stream = streamController,
        persona = personaController,
        settingsController = settingsController
    )

    init {
        groupController = GroupController(
            state = state,
            conversationRepository = conversationRepository,
            chatRepository = chatRepository,
            viewModelScope = viewModelScope,
            conversationId = conversationId,
            stream = streamController
        )
        streamController.attachGroup(groupController)
        // 消息流生命周期管理：用单一 launch + collect 自动跟随 viewModelScope 生命周期，
        // suspend observeMessages 在协程内调用，IO 自动调度。
        viewModelScope.launch {
            conversationRepository.observeMessages(conversationId)
                .collect {
                    state._messages.value = it
                    // 首次发射后标记加载完成——后续发射不再改变 isLoading（始终 false）
                    state._isLoading.value = false
                }
        }

        // 一次性同步：会话首次加载时，若 memoryBankEnabled 且 memoryBankRounds 与
        // contextLimit 不一致（历史数据残留旧默认值 40），自动同步为 contextLimit。
        // 同步后两者相等，不会重复触发；用户后续手动调整 memoryBankRounds 不会被覆盖
        // （仅当 contextLimit 再次变化时才由 updateContextLimit 重新同步）。
        viewModelScope.launch {
            state.conversation.firstOrNull { conv ->
                conv != null && conv.memoryBankEnabled && conv.memoryBankRounds != conv.contextLimit
            }?.let { conv ->
                val syncRounds = conv.contextLimit.coerceIn(
                    QuiddityConstants.MIN_MEMORY_BANK_ROUNDS,
                    QuiddityConstants.MAX_MEMORY_BANK_ROUNDS
                )
                conversationRepository.updateConversation(
                    conv.copy(memoryBankRounds = syncRounds)
                )
            }
        }
    }

    val conversation get() = state.conversation
    val messages get() = state.messages
    val isLoading get() = state.isLoading
    val pendingImageUri get() = state.pendingImageUri
    val ocrState get() = state.ocrState
    val isGenerating get() = state.isGenerating
    val toolTraces get() = state.toolTraces
    val pendingToolConfirm get() = state.pendingToolConfirm
    val pendingReedit get() = state.pendingReedit
    val compressionState get() = state.compressionState
    val errorEvent get() = state.errorEvent
    val activeSegmentEnds get() = state.activeSegmentEnds
    val chatError get() = state.chatError
    val senderNameMap get() = state.senderNameMap
    val senderAvatarMap get() = state.senderAvatarMap
    val groupQueue get() = state.groupQueue
    val isCompiling get() = state.isCompiling
    val pendingWithdraw get() = state.pendingWithdraw
    val inputBarText get() = state.inputBarText
    val timeLibraryHint get() = state.timeLibraryHint

    fun setPendingImage(uri: String?) {
        state._pendingImageUri.value = uri
        if (uri == null) {
            state._ocrState.value = OcrState.Idle
        }
    }

    fun clearPendingImage() = setPendingImage(null)

    fun confirmTool(approved: Boolean) {
        val pending = state._pendingToolConfirm.value ?: return
        state._pendingToolConfirm.value = null
        pending.resume(approved)
    }

    fun updatePersona(persona: Persona, compileEnabled: Boolean) = personaController.updatePersona(persona, compileEnabled)
    fun bindCharacter(character: com.quiddity.app.data.model.Character) = personaController.bindCharacter(character)
    fun clearCharacter() = personaController.clearCharacter()
    fun bindPersona(
        persona: Persona,
        userPersona: com.quiddity.app.data.model.UserPersona,
        memory: String
    ) = personaController.bindPersona(persona, userPersona, memory)
    fun resolveCurrentTier(): ApiCatalogManager.ModelTier = personaController.resolveCurrentTier()
    fun updateUserPersona(userPersona: UserPersona) = personaController.updateUserPersona(userPersona)
    fun updateScene(scene: String) = personaController.updateScene(scene)
    fun updateMemory(memory: String) = personaController.updateMemory(memory)
    suspend fun compilePersona(persona: Persona, maxOutputTokens: Int): String = personaController.compilePersona(persona, maxOutputTokens)
    suspend fun quickSetupGenerate(
        userDescription: String,
        tier: com.quiddity.app.domain.QuickSetupTier
    ): String = personaController.quickSetupGenerate(userDescription, tier)
    fun applyQuickSetupResult(
        rawText: String,
        tier: com.quiddity.app.domain.QuickSetupTier
    ) = personaController.applyQuickSetupResult(rawText, tier)
    fun updateQuickSetupDraft(draft: String) = personaController.updateQuickSetupDraft(draft)
    fun updateQuickSetupTemperature(value: Double) = personaController.updateQuickSetupTemperature(value)
    fun updateContextLimit(limit: Int) = settingsController.updateContextLimit(limit)
    fun updateTemperature(value: Double?) = settingsController.updateTemperature(value)
    fun updateWebSearchEnabled(enabled: Boolean) = settingsController.updateWebSearchEnabled(enabled)
    fun updateThinkingEnabled(enabled: Boolean) = settingsController.updateThinkingEnabled(enabled)
    fun updateThinkingDepth(depth: String) = settingsController.updateThinkingDepth(depth)
    fun updateGroupContextLimit(limit: Int) = settingsController.updateGroupContextLimit(limit)
    fun updateGroupStopMode(mode: String) = settingsController.updateGroupStopMode(mode)
    fun updateGroupBackground(text: String, mode: String) = settingsController.updateGroupBackground(text, mode)
    fun addGroupMembers(ids: List<String>, onDone: (Result<Unit>, List<Pair<String, String>>) -> Unit) = settingsController.addGroupMembers(ids, onDone)
    fun setMemberApi(memberId: String, catalogId: String?) = settingsController.setMemberApi(memberId, catalogId)
    fun removeGroupMember(memberId: String) = settingsController.removeGroupMember(memberId)
    fun resetContextLimitToTierDefault() = settingsController.resetContextLimitToTierDefault()
    fun updateMemoryBankEnabled(enabled: Boolean) = settingsController.updateMemoryBankEnabled(enabled)
    fun updateMemoryBankRounds(rounds: Int) = settingsController.updateMemoryBankRounds(rounds)
    fun updateInputText(text: String) = settingsController.updateInputText(text)
    fun cancelPendingSend() = settingsController.cancelPendingSend()
    fun setConversationApi(catalogId: String) = settingsController.setConversationApi(catalogId)
    fun setActiveMessageEnabled(enabled: Boolean) = settingsController.setActiveMessageEnabled(enabled)
    fun markTimeLibraryUnlocked() = settingsController.markTimeLibraryUnlocked()
    fun updateTimeLibrary(times: List<String>, disabledSlots: List<Int>) = settingsController.updateTimeLibrary(times, disabledSlots)
    fun ensureTimeLibraryGenerated() = settingsController.ensureTimeLibraryGenerated()
    fun consumeTimeLibraryHint() = settingsController.consumeTimeLibraryHint()
    fun renameConversation(newTitle: String) = opsController.renameConversation(newTitle)
    fun withdrawMessage(messageId: String) = opsController.withdrawMessage(messageId)
    fun requestAgentWithdraw(messageId: String) = opsController.requestAgentWithdraw(messageId)
    fun confirmAgentWithdraw() = opsController.confirmAgentWithdraw()
    fun cancelAgentWithdraw() = opsController.cancelAgentWithdraw()
    fun resendReedit(newContent: String) = opsController.resendReedit(newContent)
    fun clearPendingReedit() = opsController.clearPendingReedit()
    fun deleteMessages(messageIds: Set<String>) = opsController.deleteMessages(messageIds)
    fun clearConversationSettings() = opsController.clearConversationSettings()
    fun clearConversationMessages() = opsController.clearConversationMessages()
    fun deleteCurrentConversation() = opsController.deleteCurrentConversation()
    fun exportPersonaCard(): PersonaCard? = opsController.exportPersonaCard()
    fun importPersonaCard(card: PersonaCard) = opsController.importPersonaCard(card)
    fun setAiAvatarUri(uri: String?) = opsController.setAiAvatarUri(uri)
    fun setWallpaperUri(uri: String?) = opsController.setWallpaperUri(uri)
    fun setWallpaperDarken(value: Float) = opsController.setWallpaperDarken(value)
    fun importConversationFromText(text: String): Result<Unit> = opsController.importConversationFromText(text)
    fun exportConversationAsText(
        format: com.quiddity.app.util.ConversationCodec.Format
    ): String? = opsController.exportConversationAsText(format)
    fun rewriteMessage(messageId: String, newContent: String) = opsController.rewriteMessage(messageId, newContent)
    fun isGroup(): Boolean = groupController.isGroup()
    fun groupMembers(): List<Conversation> = groupController.groupMembers()
    fun memberName(convId: String): String = groupController.memberName(convId)
    fun memberAvatar(convId: String): String? = groupController.memberAvatar(convId)
    fun enqueueGroupMember(memberId: String) = groupController.enqueueGroupMember(memberId)
    fun queuePosition(memberId: String): Int = groupController.queuePosition(memberId)
    fun isQueueFull(): Boolean = groupController.isQueueFull()
    fun sendMessage(
        text: String,
        ocrText: String? = null,
        imageUri: String? = null
    ) = streamController.sendMessage(text, ocrText, imageUri)
    fun sendMessageWithImage(context: Context, text: String, imageUri: String) = streamController.sendMessageWithImage(context, text, imageUri)
    fun letAiStart() = streamController.letAiStart()
    fun regenerate() = streamController.regenerate()
    fun continueGeneration() = streamController.continueGeneration()
    fun regenerateGroupMemberMessage(messageId: String) = streamController.regenerateGroupMemberMessage(messageId)
    fun stopGeneration() = streamController.stopGeneration()
    fun hasActiveWork(): Boolean = streamController.hasActiveWork()
    fun consumeCompressionResult() = streamController.consumeCompressionResult()
    fun consumeError() = streamController.consumeError()
    fun consumeChatError() = streamController.consumeChatError()
}
