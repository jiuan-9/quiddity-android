package com.quiddity.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.data.model.Persona
import com.quiddity.app.data.model.PersonaCard
import com.quiddity.app.data.model.Role
import com.quiddity.app.data.model.UserPersona
import com.quiddity.app.data.repo.ConversationRepository
import com.quiddity.app.di.ServiceLocator
import com.quiddity.app.util.QuiddityConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class OpsController(
    private val state: ChatViewModelState,
    private val conversationRepository: ConversationRepository,
    private val viewModelScope: CoroutineScope,
    private val conversationId: String,
    private val stream: StreamController,
    private val persona: PersonaController,
    private val settingsController: SettingsController
) {
    private val _messages get() = state._messages
    private val _isGenerating get() = state._isGenerating
    private val _pendingReedit get() = state._pendingReedit
    private val _pendingWithdraw get() = state._pendingWithdraw
    private val conversation get() = state.conversation
    private val messages get() = state.messages
    private var streamJob get() = state.streamJob; set(value) { state.streamJob = value }
    private var groupStreamJob get() = state.groupStreamJob; set(value) { state.groupStreamJob = value }
    fun renameConversation(newTitle: String) {
        viewModelScope.launch {
            conversationRepository.renameConversation(conversationId, newTitle)
        }
    }
    fun withdrawMessage(messageId: String) {
        if (_isGenerating.value) return
        val current = _messages.value
        if (current.isEmpty()) return
        val targetIndex = current.indexOfFirst { it.id == messageId }
        if (targetIndex < 0) return
        val target = current[targetIndex]
        val newHistory = current.subList(0, targetIndex).toList()
        viewModelScope.launch {
            conversationRepository.replaceMessages(conversationId, newHistory)
        }
        // 撤回后提供「重新编辑」入口：缓存原文/图片/OCR 文本，编辑后作为新消息重发
        if (target.role == Role.USER && (target.content.isNotBlank() || target.imageUri != null)) {
            _pendingReedit.value = PendingReedit(target.content, target.ocrText, target.imageUri)
        }
    }
    fun requestAgentWithdraw(messageId: String) {
        if (_isGenerating.value) return
        val current = _messages.value
        if (current.isEmpty()) return
        val targetIndex = current.indexOfFirst { it.id == messageId }
        if (targetIndex < 0) return
        // 收集目标消息（含）之后所有 AI 消息上固化的本轮创建/更改清单
        val created = current.subList(targetIndex, current.size)
            .flatMap { it.createdPaths }
            .distinct()
        val changed = current.subList(targetIndex, current.size)
            .flatMap { it.changedItems }
            .distinct()
        _pendingWithdraw.value = WithdrawProposal(
            targetId = messageId,
            createdPaths = created,
            changedItems = changed
        )
    }
    fun confirmAgentWithdraw() {
        val proposal = _pendingWithdraw.value ?: return
        _pendingWithdraw.value = null
        val current = _messages.value
        val targetIndex = current.indexOfFirst { it.id == proposal.targetId }
        if (targetIndex < 0) return
        val target = current[targetIndex]
        val newHistory = current.subList(0, targetIndex).toList()
        viewModelScope.launch {
            val executors = com.quiddity.app.di.ServiceLocator.agentExecutors
            // 1. 删除本轮创建的文件（rm -rf，仅删确实存在的路径；失败不阻塞消息撤回）
            if (proposal.createdPaths.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    executors.deleteFilesForWithdraw(proposal.createdPaths)
                }
            }
            // 2. 恢复可逆的应用状态（停用 → 启用 / 启用 → 停用；不可逆项仅提示）
            proposal.changedItems.forEach { item ->
                val enable = item.startsWith("停用 ")
                val disable = item.startsWith("启用 ")
                if (enable || disable) {
                    val pkg = item.substringAfter(if (enable) "停用 " else "启用 ")
                        .substringBefore("（")
                        .trim()
                    if (pkg.isNotBlank()) {
                        withContext(Dispatchers.IO) {
                            executors.revertAppEnabledState(pkg, enable)
                        }
                    }
                }
            }
            // 3. 删除消息（含目标）及其后所有
            conversationRepository.replaceMessages(conversationId, newHistory)
        }
        // 撤回后提供「重新编辑」入口（与私聊一致）
        if (target.role == Role.USER && (target.content.isNotBlank() || target.imageUri != null)) {
            _pendingReedit.value = PendingReedit(target.content, target.ocrText, target.imageUri)
        }
    }
    fun cancelAgentWithdraw() {
        _pendingWithdraw.value = null
    }
    fun resendReedit(newContent: String) {
        val pending = _pendingReedit.value ?: return
        if (newContent.isBlank() && pending.imageUri.isNullOrBlank()) return
        _pendingReedit.value = null
        stream.sendMessage(newContent, ocrText = pending.ocrText, imageUri = pending.imageUri)
    }
    fun clearPendingReedit() {
        _pendingReedit.value = null
    }
    fun deleteMessages(messageIds: Set<String>) {
        if (messageIds.isEmpty()) return
        val current = _messages.value
        if (current.isEmpty()) return
        val newMessages = current.filterNot { it.id in messageIds }
        if (newMessages.size == current.size) return
        viewModelScope.launch {
            conversationRepository.replaceMessages(conversationId, newMessages)
        }
    }
    fun clearConversationSettings() {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            conversationRepository.updateConversation(
                conv.copy(
                    persona = Persona.Empty,
                    userPersona = UserPersona.Empty,
                    scene = "",
                    sceneInjected = false,
                    memory = "",
                    compileEnabled = false
                )
            )
        }
    }
    fun clearConversationMessages() {
        if (_isGenerating.value) return
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            val isGroupConv = conv.type == ConversationType.GROUP
            conversationRepository.replaceMessages(conversationId, emptyList())
            conversationRepository.updateConversation(
                if (isGroupConv) {
                    // 群聊：清除聊天记录并重置群聊小本本
                    conv.copy(groupMemory = "")
                } else {
                    conv.copy(
                        compressedMemory = "",
                        memoryIndex = "",
                        lastCompressedAtRound = 0
                    )
                }
            )
        }
    }
    fun deleteCurrentConversation() {
        val id = conversationId
        groupStreamJob?.cancel()
        groupStreamJob = null
        streamJob?.cancel()
        streamJob = null
        settingsController.cancelPendingSend()
        viewModelScope.launch {
            // NonCancellable：页面即将返回销毁 ViewModel，删除必须完整落盘后再释放
            withContext(NonCancellable) {
                conversationRepository.deleteConversation(id)
            }
        }
    }
    fun exportPersonaCard(): PersonaCard? {
        val conv = conversation.value ?: return null
        return PersonaCard(
            schemaVersion = 1,
            exportedAt = System.currentTimeMillis(),
            persona = conv.persona,
            userPersona = conv.userPersona,
            scene = conv.scene,
            memory = conv.memory,
            quickSetupDraft = conv.quickSetupDraft
        )
    }
    fun importPersonaCard(card: PersonaCard) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            // 场景可能随角色卡变更，重置 sceneInjected 让下次对话重新注入
            conversationRepository.updateConversation(
                conv.copy(
                    persona = card.persona,
                    userPersona = card.userPersona,
                    scene = card.scene,
                    sceneInjected = false,
                    memory = card.memory,
                    quickSetupDraft = card.quickSetupDraft
                )
            )
        }
    }
    fun setAiAvatarUri(uri: String?) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            conversationRepository.updateConversation(
                conv.copy(persona = conv.persona.copy(aiAvatarUri = uri))
            )
        }
    }
    fun setWallpaperUri(uri: String?) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            conversationRepository.updateConversation(conv.copy(wallpaperUri = uri))
        }
    }
    fun setWallpaperDarken(value: Float) {
        viewModelScope.launch {
            val conv = conversation.value ?: return@launch
            val clamped = value.coerceIn(
                QuiddityConstants.MIN_WALLPAPER_DARKEN,
                QuiddityConstants.MAX_WALLPAPER_DARKEN
            )
            conversationRepository.updateConversation(conv.copy(wallpaperDarken = clamped))
        }
    }
    fun importConversationFromText(text: String): Result<Unit> {
        return runCatching {
            val result = com.quiddity.app.util.ConversationCodec.importConversation(
                content = text,
                targetConversationId = conversationId
            )
            viewModelScope.launch {
                // 协程内重读会话，避免覆盖并发修改（丢失更新竞态）
                val conv = conversation.value ?: return@launch
                // 1. 替换消息列表（覆盖式导入）
                conversationRepository.replaceMessages(conversationId, result.messages)

                // 2. 更新角色卡信息（仅在解析到非空内容时更新对应字段）
                val updatedPersona = if (
                    result.persona != Persona.Empty ||
                    result.userPersona != UserPersona.Empty
                ) {
                    conv.persona.copy(
                        // 仅在解析到非空字段时覆盖，避免清空用户已设置的人设
                        name = result.persona.name.ifBlank { conv.persona.name },
                        persona = result.persona.persona.ifBlank { conv.persona.persona },
                        character = result.persona.character.ifBlank { conv.persona.character },
                        appearance = result.persona.appearance.ifBlank { conv.persona.appearance },
                        worldBackground = result.persona.worldBackground.ifBlank { conv.persona.worldBackground },
                        desired = result.persona.desired.ifBlank { conv.persona.desired }
                    )
                } else {
                    conv.persona
                }
                val updatedUserPersona = if (result.userPersona != UserPersona.Empty) {
                    conv.userPersona.copy(
                        name = result.userPersona.name.ifBlank { conv.userPersona.name },
                        identity = result.userPersona.identity.ifBlank { conv.userPersona.identity },
                        gender = result.userPersona.gender.ifBlank { conv.userPersona.gender },
                        age = result.userPersona.age.ifBlank { conv.userPersona.age },
                        appearance = result.userPersona.appearance.ifBlank { conv.userPersona.appearance }
                    )
                } else {
                    conv.userPersona
                }
                val updatedScene = result.scene.ifBlank { conv.scene }
                val updatedMemory = result.memory.ifBlank { conv.memory }
                // 场景变更时重置 sceneInjected，下次对话重新注入新场景
                val sceneChanged = updatedScene != conv.scene

                // 3. 标题更新：仅当当前是默认标题且解析到非默认标题时
                val updatedTitle = if (
                    persona.isDefaultSoloTitle(conv.title) &&
                    result.title.isNotBlank() &&
                    result.title != QuiddityConstants.DEFAULT_CONVERSATION_TITLE
                ) {
                    result.title
                } else {
                    conv.title
                }

                conversationRepository.updateConversation(
                    conv.copy(
                        title = updatedTitle,
                        persona = updatedPersona,
                        userPersona = updatedUserPersona,
                        scene = updatedScene,
                        sceneInjected = if (sceneChanged) false else conv.sceneInjected,
                        memory = updatedMemory
                    )
                )
            }
        }
    }
    fun exportConversationAsText(
        format: com.quiddity.app.util.ConversationCodec.Format
    ): String? {
        val conv = conversation.value ?: return null
        val msgs = _messages.value
        return com.quiddity.app.util.ConversationCodec.exportConversation(
            conversation = conv,
            messages = msgs,
            format = format
        )
    }
    fun rewriteMessage(messageId: String, newContent: String) {
        if (newContent.isBlank()) return
        viewModelScope.launch {
            val msg = _messages.value.firstOrNull { it.id == messageId } ?: return@launch
            conversationRepository.updateMessage(
                msg.copy(content = newContent, isStreaming = false)
            )
        }
    }
}
