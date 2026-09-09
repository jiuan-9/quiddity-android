package com.quiddity.app.ui.chat.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.quiddity.app.data.model.AppSettings
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.data.model.Message
import com.quiddity.app.domain.ApiCatalogManager
import com.quiddity.app.ui.chat.ChatViewModel
import com.quiddity.app.ui.chat.components.panels.ApiEditorPanel
import com.quiddity.app.ui.chat.components.panels.ApiSelectorPanel
import com.quiddity.app.ui.chat.components.panels.CompressionPanel
import com.quiddity.app.ui.chat.components.panels.PersonaPanel
import com.quiddity.app.ui.chat.components.panels.QuickSetupPanel
import com.quiddity.app.ui.chat.components.panels.ScenePanel
import com.quiddity.app.ui.chat.components.panels.UserPersonaPanel
import com.quiddity.app.ui.chat.components.panels.WallpaperPanel
import com.quiddity.app.ui.settings.SettingsViewModel
import com.quiddity.app.util.IdGenerator
import kotlinx.coroutines.launch

@Composable
internal fun HamburgerPanelHost(
    panel: HamburgerPanel?,
    conversation: Conversation?,
    messages: List<Message>,
    settings: AppSettings,
    viewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel,
    apiCatalogManager: ApiCatalogManager,
    currentTier: ApiCatalogManager.ModelTier,
    webSearchSupported: Boolean,
    currentModelId: String,
    context: Context,
    notificationPermissionLauncher: ActivityResultLauncher<String>,
    launchers: HamburgerFileLaunchers,
    onPanelSelected: (HamburgerPanel?) -> Unit,
    onDismiss: () -> Unit,
    onJumpToMessage: ((String) -> Unit)?,
    onPersonaOverride: (() -> Unit)?,
    onToast: (String) -> Unit,
    onRequestExportFormat: () -> Unit,
    onRequestClearSettings: () -> Unit,
    onRequestClearMessages: () -> Unit,
    onRequestDeleteConversation: () -> Unit
) {
when (panel) {
    null -> {
        if (conversation?.type == ConversationType.GROUP) {
            GroupMenuContent(
                conversation = conversation,
                onDismiss = onDismiss,
                onRename = {
                    onPanelSelected(HamburgerPanel.GroupName)
                },
                onContextLimit = {
                    onPanelSelected(HamburgerPanel.GroupContextLimit)
                },
                onStopModeChange = { mode ->
                    viewModel.updateGroupStopMode(mode)
                },
                onGroupBackground = {
                    onPanelSelected(HamburgerPanel.GroupBackground)
                },
                onWallpaper = {
                    onPanelSelected(HamburgerPanel.Wallpaper)
                },
                onManageMembers = {
                    onPanelSelected(HamburgerPanel.GroupMembers)
                },
                onSearchChat = { onPanelSelected(HamburgerPanel.SearchChat) },
                onClearMessages = { onRequestClearMessages() },
                onDeleteConversation = { onRequestDeleteConversation() },
                darkMode = settings.darkMode,
                onDarkModeChange = { dark -> settingsViewModel.setDarkMode(dark) }
            )
        } else {
            MainMenuContent(
                conversation = conversation,
                messages = messages,
                currentTier = currentTier,
                settings = settings,
                onPanelSelected = onPanelSelected,
                onPersonaClick = onPersonaOverride
                    ?: { onPanelSelected(HamburgerPanel.Persona) },
                onDismiss = onDismiss,
                darkMode = settings.darkMode,
                onDarkModeChange = { settingsViewModel.setDarkMode(it) },
                onClearSettings = { onRequestClearSettings() },
                onExportPersona = { launchers.personaExport.launch("quiddity-persona-${IdGenerator.newUuid()}.json") },
                onImportPersona = { launchers.personaImport.launch(arrayOf("application/json")) },
                onExportConversation = { onRequestExportFormat() },
                onImportConversation = {
                    launchers.conversationImport.launch(
                        arrayOf(
                            "application/json",
                            "text/markdown",
                            "text/plain",
                            "application/octet-stream"
                        )
                    )
                },
                onContextLimitChange = { limit ->
                    viewModel.updateContextLimit(limit)
                },
                onResetContextLimit = {
                    viewModel.resetContextLimitToTierDefault()
                },
                onMemoryBankEnabledChange = { enabled ->
                    viewModel.updateMemoryBankEnabled(enabled)
                },
                onMemoryBankRoundsChange = { rounds ->
                    viewModel.updateMemoryBankRounds(rounds)
                },
                onCompressionClick = { onPanelSelected(HamburgerPanel.Compression) },
                onClearMessages = { onRequestClearMessages() },
                onActiveMessageChange = { enabled ->
                    if (enabled &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    viewModel.setActiveMessageEnabled(enabled)
                },
                onViewTimeLibrary = {
                    onPanelSelected(HamburgerPanel.TimeLibrary)
                },
                onOpenSearchChat = { onPanelSelected(HamburgerPanel.SearchChat) },
                webSearchSupported = webSearchSupported,
                currentModelId = currentModelId,
                onTemperatureChange = { value ->
                    viewModel.updateTemperature(value)
                },
                onThinkingEnabledChange = { enabled ->
                    viewModel.updateThinkingEnabled(enabled)
                },
                onThinkingDepthChange = { depth ->
                    viewModel.updateThinkingDepth(depth)
                },
                onWebSearchChange = { enabled ->
                    viewModel.updateWebSearchEnabled(enabled)
                }
            )
        }
    }
    HamburgerPanel.QuickSetup -> {
        conversation?.let { conv ->
            val tier = viewModel.resolveCurrentTier()
            val hasExisting = conv.persona.name.isNotBlank() ||
                conv.persona.persona.isNotBlank() ||
                conv.persona.character.isNotBlank() ||
                conv.persona.appearance.isNotBlank() ||
                conv.persona.worldBackground.isNotBlank() ||
                conv.persona.desired.isNotBlank() ||
                conv.userPersona.name.isNotBlank() ||
                conv.userPersona.identity.isNotBlank() ||
                conv.userPersona.gender.isNotBlank() ||
                conv.userPersona.age.isNotBlank() ||
                conv.userPersona.appearance.isNotBlank() ||
                conv.scene.isNotBlank() ||
                conv.memory.isNotBlank()
            QuickSetupPanel(
                currentTier = tier,
                quickSetupTemperature = settings.quickSetupTemperature,
                onTemperatureChange = { value ->
                    viewModel.updateQuickSetupTemperature(value)
                },
                hasExistingContent = hasExisting,
                hasMessages = messages.any { !it.isNotice },
                initialDraft = conv.quickSetupDraft,
                onDraftChange = { draft ->
                    viewModel.updateQuickSetupDraft(draft)
                },
                onGenerate = { description, selectedTier ->
                    viewModel.quickSetupGenerate(description, selectedTier)
                },
                onApply = { rawText, selectedTier ->
                    viewModel.applyQuickSetupResult(rawText, selectedTier)
                },
                onFinished = {
                    onPanelSelected(null)
                    onDismiss()
                },
                onClearMessages = {
                    viewModel.clearConversationMessages()
                },
                onBack = { onPanelSelected(null) }
            )
        }
    }
    HamburgerPanel.Persona -> {
        conversation?.let { conv ->
            val currentTier = viewModel.resolveCurrentTier()
            PersonaPanel(
                initial = conv.persona,
                ownerId = conv.id,
                compileEnabled = conv.compileEnabled,
                modelTier = currentTier,
                catalogManager = apiCatalogManager,
                onBack = { onPanelSelected(null) },
                onSave = { persona, compileEnabled ->
                    viewModel.updatePersona(persona, compileEnabled)
                    onPanelSelected(null)
                },
                onSaveAndExit = { persona, compileEnabled ->
                    viewModel.updatePersona(persona, compileEnabled)
                    onPanelSelected(null)
                    onDismiss()
                },
                onAutoSave = { persona, compileEnabled ->
                    viewModel.updatePersona(persona, compileEnabled)
                },
                onCompile = { persona, maxTokens ->
                    viewModel.compilePersona(persona, maxTokens)
                }
            )
        }
    }
    HamburgerPanel.UserPersona -> {
        conversation?.let { conv ->
            UserPersonaPanel(
                initial = conv.userPersona,
                initialMemory = conv.memory,
                onBack = { onPanelSelected(null) },
                onSave = { userPersona, memory ->
                    viewModel.updateUserPersona(userPersona)
                    viewModel.updateMemory(memory)
                    onPanelSelected(null)
                },
                onAutoSave = { userPersona, memory ->
                    viewModel.updateUserPersona(userPersona)
                    viewModel.updateMemory(memory)
                }
            )
        }
    }
    HamburgerPanel.Scene -> {
        conversation?.let { conv ->
            ScenePanel(
                initialScene = conv.scene,
                onBack = { onPanelSelected(null) },
                onSave = {
                    viewModel.updateScene(it)
                    onPanelSelected(null)
                },
                onAutoSave = { viewModel.updateScene(it) }
            )
        }
    }
    HamburgerPanel.ApiSelector -> {
        ApiSelectorPanel(
            catalog = settings.catalog,
            currentSelection = conversation?.apiCatalogId ?: settings.activeCatalogId,
            onBack = { onPanelSelected(null) },
            onSelect = { id ->
                if (id != null) viewModel.setConversationApi(id)
                onPanelSelected(null)
            },
            activeCatalogId = settings.activeCatalogId,
            onSetDefault = { id -> settingsViewModel.setActiveCatalog(id) }
        )
    }
    HamburgerPanel.ApiEditor -> {
        ApiEditorPanel(
            catalog = settings.catalog,
            catalogManager = apiCatalogManager,
            onBack = { onPanelSelected(null) },
            onAddCatalog = { state ->
                // UI 层不预生成 id，统一由 SettingsViewModel.upsertCatalog 负责
                settingsViewModel.upsertCatalog(
                    id = null,
                    name = state.name,
                    providerId = state.providerId,
                    apiUrl = state.apiUrl,
                    apiModel = state.apiModel,
                    apiKey = state.apiKey,
                    maxTemperature = state.maxTemperature
                )
            },
            onUpdateCatalog = { state ->
                if (state.id.isBlank()) {
                    onToast("更新失败：模型配置 id 为空")
                } else {
                    settingsViewModel.upsertCatalog(
                        id = state.id,
                        name = state.name,
                        providerId = state.providerId,
                        apiUrl = state.apiUrl,
                        apiModel = state.apiModel,
                        apiKey = state.apiKey,
                        maxTemperature = state.maxTemperature
                    )
                }
            },
            onDeleteCatalog = { id -> settingsViewModel.removeCatalog(id) }
        )
    }
    // - 每次打开都从当前 conversation 读取最新 wallpaperUri / darken
    // - 修改通过 viewModel.setWallpaperUri / setWallpaperDarken 写回
    // - 此面板状态独立于其他子面板（每次进入都重新初始化）
    HamburgerPanel.Wallpaper -> {
        conversation?.let { conv ->
            WallpaperPanel(
                currentWallpaperUri = conv.wallpaperUri,
                currentDarken = conv.wallpaperDarken,
                onBack = { onPanelSelected(null) },
                onWallpaperChanged = { uri ->
                    viewModel.setWallpaperUri(uri)
                },
                onDarkenChanged = { value ->
                    viewModel.setWallpaperDarken(value)
                }
            )
        }
    }
    HamburgerPanel.Compression -> {
        conversation?.let { conv ->
            val contextLimit = conv.contextLimit
            CompressionPanel(
                enabled = conv.memoryBankEnabled,
                rounds = conv.memoryBankRounds,
                contextLimit = contextLimit,
                onBack = { onPanelSelected(null) },
                onEnabledChange = { viewModel.updateMemoryBankEnabled(it) },
                onRoundsChange = { viewModel.updateMemoryBankRounds(it) }
            )
        }
    }
    HamburgerPanel.SearchChat -> {
        SearchChatPanel(
            conversation = conversation,
            messages = messages,
            onBack = { onPanelSelected(null) },
            onOpenMessage = { id ->
                onJumpToMessage?.invoke(id)
                onDismiss()
            }
        )
    }
    HamburgerPanel.TimeLibrary -> {
        conversation?.let { conv ->
            TimeLibraryEditorPanel(
                conversation = conv,
                onSave = { times, disabled ->
                    viewModel.updateTimeLibrary(times, disabled)
                },
                onBack = { onPanelSelected(null) }
            )
        }
    }
    HamburgerPanel.GroupName -> {
        conversation?.let { conv ->
            GroupNamePanel(
                currentName = conv.title,
                onBack = { onPanelSelected(null) },
                onSave = { name ->
                    viewModel.renameConversation(name)
                    onPanelSelected(null)
                }
            )
        }
    }
    HamburgerPanel.GroupContextLimit -> {
        conversation?.let { conv ->
            GroupContextLimitPanel(
                currentLimit = conv.groupContextLimit,
                onBack = { onPanelSelected(null) },
                onSave = { limit ->
                    viewModel.updateGroupContextLimit(limit)
                    onPanelSelected(null)
                }
            )
        }
    }
    HamburgerPanel.GroupMembers -> {
        conversation?.let { conv ->
            GroupMemberManagePanel(
                group = conv,
                viewModel = viewModel,
                settings = settings,
                onBack = { onPanelSelected(null) }
            )
        }
    }
    HamburgerPanel.GroupBackground -> {
        conversation?.let { conv ->
            GroupBackgroundPanel(
                currentText = conv.groupBackground,
                currentMode = conv.groupBackgroundMode,
                onBack = { onPanelSelected(null) },
                onSave = { text, mode ->
                    viewModel.updateGroupBackground(text, mode)
                    onPanelSelected(null)
                }
            )
        }
    }
}
}
