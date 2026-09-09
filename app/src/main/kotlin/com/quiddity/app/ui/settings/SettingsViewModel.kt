package com.quiddity.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quiddity.app.data.model.ApiCatalogEntry
import com.quiddity.app.data.model.AppSettings
import com.quiddity.app.data.model.Character
import com.quiddity.app.data.model.ConversationBundle
import com.quiddity.app.data.model.ExportPayload
import com.quiddity.app.data.model.ImportMode
import com.quiddity.app.data.repo.CharacterRepository
import com.quiddity.app.data.repo.ConversationRepository
import com.quiddity.app.data.repo.SettingsRepository
import com.quiddity.app.di.ServiceLocator
import com.quiddity.app.domain.ApiCatalogManager
import com.quiddity.app.util.QuiddityConstants
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
// 当前规则：通过 SettingsRepository 持久化到 DataStore；提供细粒度修改方法。
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val conversationRepository: ConversationRepository,
    private val apiCatalogManager: ApiCatalogManager,
    private val characterRepository: CharacterRepository,
    private val agentStore: com.quiddity.app.data.local.AgentStore? = null
) : ViewModel() {

    /** 写操作失败提示（防未捕获协程异常导致 App 闪退）。 */
    private val _errorEvent = MutableStateFlow<String?>(null)
    val errorEvent: StateFlow<String?> = _errorEvent.asStateFlow()

    fun consumeError() {
        _errorEvent.value = null
    }

    /** 写操作成功提示（如"模型配置已保存"）。 */
    private val _toastEvent = MutableStateFlow<String?>(null)
    val toastEvent: StateFlow<String?> = _toastEvent.asStateFlow()

    fun consumeToast() {
        _toastEvent.value = null
    }

    val settings: StateFlow<AppSettings> = settingsRepository.observeSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = settingsRepository.currentSnapshot()
        )

