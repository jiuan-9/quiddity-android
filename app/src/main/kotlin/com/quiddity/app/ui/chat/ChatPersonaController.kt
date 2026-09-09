package com.quiddity.app.ui.chat

import androidx.lifecycle.viewModelScope
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.Persona
import com.quiddity.app.data.model.UserPersona
import com.quiddity.app.data.repo.ChatRepository
import com.quiddity.app.data.repo.ConversationRepository
import com.quiddity.app.data.repo.SettingsRepository
import com.quiddity.app.domain.ApiCatalogManager
import com.quiddity.app.util.QuiddityConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class PersonaController(
    private val state: ChatViewModelState,
    private val conversationRepository: ConversationRepository,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val apiCatalogManager: ApiCatalogManager,
    private val viewModelScope: CoroutineScope
) {
    private val _isCompiling get() = state._isCompiling
    private val conversation get() = state.conversation
    private var quickSetupDraftJob get() = state.quickSetupDraftJob; set(value) { state.quickSetupDraftJob = value }
    fun updatePersona(persona: Persona, compileEnabled: Boolean) {
        viewModelScope.launch {
            // 协程内重读会话，避免与并发的其他设置操作互相覆盖（丢失更新竞态）
            val conv = conversation.value ?: return@launch
            // 头部名字框同步规则：标题跟随人设名，除非用户已手动重命名过。
            // - 标题仍是默认"新会话" → 同步为新名
            // - 标题当前等于旧人设名（之前自动同步过）→ 跟随更新为新名
            // - 用户已手动重命名（标题 != 默认 且 != 旧人设名）→ 尊重用户选择，不覆盖
            val newTitle = syncTitleWithPersonaName(
                currentTitle = conv.title,
                oldPersonaName = conv.persona.name,
                newPersonaName = persona.name
            )
            // 人设字段是否已变更（除 compiledPersona 和 aiAvatarUri 外）
            val personaChanged = hasUserEditableFieldsChanged(conv.persona, persona)
            // 已变更 → 清空 compiledPersona 缓存（与 updatePersona 解耦）
            val cleanedPersona = if (personaChanged) {
                persona.copy(compiledPersona = null)
            } else {
                persona
            }
            // persona 与 compileEnabled 原子写入，避免竞态覆盖
            conversationRepository.updateConversation(
                conv.copy(
                    persona = cleanedPersona,
                    title = newTitle,
                    compileEnabled = compileEnabled
                )
            )
        }
    }
    fun bindCharacter(character: com.quiddity.app.data.model.Character) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            val newTitle = syncTitleWithPersonaName(
                currentTitle = conv.title,
                oldPersonaName = conv.persona.name,
                newPersonaName = character.persona.name
            )
            conversationRepository.updateConversation(
                conv.copy(
                    persona = character.persona.copy(
                        compiledPersona = null,
                        aiAvatarUri = character.aiAvatarUri ?: character.persona.aiAvatarUri
                    ),
                    userPersona = character.userPersona,
                    memory = character.memory,
                    characterId = character.id,
                    title = newTitle
                )
            )
        }
    }
    fun clearCharacter() {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            conversationRepository.updateConversation(conv.copy(characterId = null))
        }
    }
    fun bindPersona(
        persona: Persona,
        userPersona: com.quiddity.app.data.model.UserPersona,
        memory: String
    ) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            val newTitle = syncTitleWithPersonaName(
                currentTitle = conv.title,
                oldPersonaName = conv.persona.name,
                newPersonaName = persona.name
            )
            conversationRepository.updateConversation(
                conv.copy(
                    persona = persona.copy(compiledPersona = null),
                    userPersona = userPersona,
                    memory = memory,
                    title = newTitle
                )
            )
        }
    }
    fun syncTitleWithPersonaName(
        currentTitle: String,
        oldPersonaName: String,
        newPersonaName: String
    ): String {
        // 新名为空：保持原标题（不因清空名字而清空标题）
        if (newPersonaName.isBlank()) return currentTitle
        return when {
            // 旧默认「新会话」或 1.5.0 带编号默认名（新会话 1、2、3…）都可被 AI 名字覆盖
            isDefaultSoloTitle(currentTitle) -> newPersonaName
            currentTitle == oldPersonaName -> newPersonaName
            else -> currentTitle
        }
    }
    fun isDefaultSoloTitle(title: String): Boolean =
        title == QuiddityConstants.DEFAULT_CONVERSATION_TITLE ||
            title.startsWith(QuiddityConstants.SOLO_DEFAULT_TITLE_PREFIX + " ")

    /**
     * 判断用户可编辑的人设字段是否发生变化（用于 [updatePersona] 决定是否清空编译缓存）。
     *
     * 不比较 [Persona.name] 和 [Persona.aiAvatarUri]：
     * - name 是用户给 AI 取的称呼，不影响精调结果
     * - aiAvatarUri 由 [setAiAvatarUri] 独立更新
     * - compiledPersona 是缓存字段，由 [compilePersona] 写入
     *
     * 结论：仅更改头像、名字这两项时，无需重新精调（前提是已精调过的）。
     */
    fun hasUserEditableFieldsChanged(old: Persona, new: Persona): Boolean {
        return old.desired != new.desired ||
            old.persona != new.persona ||
            old.character != new.character ||
            old.appearance != new.appearance ||
            old.worldBackground != new.worldBackground
    }
    fun resolveCurrentTier(): ApiCatalogManager.ModelTier {
        val conv = conversation.value ?: return ApiCatalogManager.ModelTier.FULL
        val settings = settingsRepository.currentSnapshot()
        val entry = settings.catalog
            .firstOrNull { it.id == conv.apiCatalogId }
            ?: settings.catalog.firstOrNull { it.id == settings.activeCatalogId }
            ?: settings.catalog.firstOrNull()
            ?: return ApiCatalogManager.ModelTier.FULL
        return apiCatalogManager.getModelTier(entry.apiModel, entry.providerId)
    }
    fun effectiveMemoryStrategy(conv: Conversation): String? {
        conv.memoryStrategy?.let { return it }
        return if (conv.compressedMemory.isNotBlank() &&
            resolveCurrentTier() == ApiCatalogManager.ModelTier.FULL
        ) {
            QuiddityConstants.MEMORY_STRATEGY_TOOL
        } else {
            QuiddityConstants.MEMORY_STRATEGY_CARRY
        }
    }
    fun updateUserPersona(userPersona: UserPersona) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            conversationRepository.updateConversation(conv.copy(userPersona = userPersona))
        }
    }
    fun updateScene(scene: String) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            // 场景修改后重置 sceneInjected=false，下次对话将新场景注入系统提示词
            conversationRepository.updateConversation(
                conv.copy(scene = scene, sceneInjected = false)
            )
        }
    }
    fun updateMemory(memory: String) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            conversationRepository.updateConversation(conv.copy(memory = memory))
        }
    }
    suspend fun compilePersona(persona: Persona, maxOutputTokens: Int): String {
        _isCompiling.value = true
        try {
            val conv = conversation.value ?: throw IllegalStateException("会话不存在")
            // 先把最新 persona 写入会话，确保 repository 读到的是最新字段
            val newTitle = syncTitleWithPersonaName(
                currentTitle = conv.title,
                oldPersonaName = conv.persona.name,
                newPersonaName = persona.name
            )
            val updatedConv = conv.copy(persona = persona, title = newTitle)
            conversationRepository.updateConversation(updatedConv)
            // 调用 AI 编译，传入 token 上限约束
            return chatRepository.compilePersona(updatedConv, maxOutputTokens)
        } finally {
            _isCompiling.value = false
        }
    }
    suspend fun quickSetupGenerate(
        userDescription: String,
        tier: com.quiddity.app.domain.QuickSetupTier
    ): String {
        val conv = conversation.value ?: throw IllegalStateException("会话不存在")
        return chatRepository.quickSetup(conv, userDescription, tier)
    }
    fun applyQuickSetupResult(
        rawText: String,
        tier: com.quiddity.app.domain.QuickSetupTier
    ) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                val conv = conversation.value ?: return@withContext
                val result = com.quiddity.app.domain.QuickSetupPrompt
                    .parseQuickSetupResult(rawText, tier)
                val newTitle = syncTitleWithPersonaName(
                    currentTitle = conv.title,
                    oldPersonaName = conv.persona.name,
                    newPersonaName = result.persona.name
                )
                conversationRepository.updateConversation(
                    conv.copy(
                        persona = result.persona,
                        userPersona = result.userPersona,
                        scene = result.scene,
                        memory = result.memory,
                        title = newTitle,
                        sceneInjected = false
                    )
                )
            }
        }
    }
    fun updateQuickSetupDraft(draft: String) {
        quickSetupDraftJob?.cancel()
        quickSetupDraftJob = viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            persistQuickSetupDraft(draft)
        }
    }
    fun persistQuickSetupDraft(draft: String) {
        viewModelScope.launch {
            withContext(NonCancellable) {
                val conv = conversation.value ?: return@withContext
                if (conv.quickSetupDraft == draft) return@withContext
                conversationRepository.updateConversation(conv.copy(quickSetupDraft = draft))
            }
        }
    }
    fun updateQuickSetupTemperature(value: Double) {
        viewModelScope.launch {
            settingsRepository.setQuickSetupTemperature(value)
        }
    }
}
