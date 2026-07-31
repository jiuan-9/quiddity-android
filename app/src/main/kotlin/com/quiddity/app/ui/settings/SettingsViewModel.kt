package com.quiddity.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quiddity.app.data.model.ApiCatalogEntry
import com.quiddity.app.data.model.AppSettings
import com.quiddity.app.data.repo.ConversationRepository
import com.quiddity.app.data.repo.SettingsRepository
import com.quiddity.app.domain.ApiCatalogManager
import com.quiddity.app.util.QuiddityConstants
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
/*
 * ============================================================================
 * 开发规范 (Development Specifications)
 * ============================================================================
 *
 * 1. 问题修复规范
 *    所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
 *    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
 *
 * 2. 代码注释规范
 *    文件内仅允许保留两类注释：
 *    - 当前规则说明注释（即本注释块）
 *    - 模块划分注释（用于标识代码功能模块边界）
 *    除此之外，禁止出现任何形式的代码注释（包括但不限于单行注释、多行注释、临时调试注释等）。
 *
 * 3. 构建交付要求
 *    在完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK 文件。
 *    APK 文件需满足以下条件：
 *    - 签名有效且符合发布标准
 *    - 包含完整的功能模块
 *    - 经过基础性能测试和兼容性测试
 *    以便在真实设备环境中进行功能验证和性能评估。
 *
 * ============================================================================
 */


// 当前规则：通过 SettingsRepository 持久化到 DataStore；提供细粒度修改方法。
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val conversationRepository: ConversationRepository,
    private val apiCatalogManager: ApiCatalogManager
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.observeSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = settingsRepository.currentSnapshot()
        )

    // ===== 三条开发规范（位于文件中间位置） =====
    // 1. 问题修复规范：所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
    //    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
    // 2. 代码注释规范：文件内仅允许保留两类注释——当前规则说明注释与模块划分注释（// ===== xxx =====）。
    //    除此之外，禁止出现任何形式的代码注释（包括单行 / 多行 / 临时调试注释）。
    // 3. 构建交付要求：完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK。
    //    APK 需满足：签名有效且符合发布标准、包含完整功能模块、经过基础性能与兼容性测试。

    // ===== 基础开关 =====
    fun setDarkMode(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setDarkMode(enabled)
    }

    fun setMultilineSplit(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setMultilineSplit(enabled)
    }

    fun setEnterToSend(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setEnterToSend(enabled)
    }

    /**
     * 暴露给 [SettingsBottomSheet] 的"显示"section 使用。
     */
    fun setBracketGrayEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setBracketGrayEnabled(enabled)
    }

    /**
     * 暴露给 [SettingsBottomSheet] 的"显示"section → 会话列表壁纸 子面板使用。
     */
    fun setListWallpaperUri(uri: String?) = viewModelScope.launch {
        settingsRepository.setListWallpaperUri(uri)
    }

    fun setListWallpaperDarken(value: Float) = viewModelScope.launch {
        settingsRepository.setListWallpaperDarken(value)
    }

    // ===== Token 与记忆 =====
    fun setMaxTokens(value: Int) = viewModelScope.launch {
        settingsRepository.setMaxTokens(
            value.coerceIn(QuiddityConstants.MIN_MAX_TOKENS, QuiddityConstants.MAX_MAX_TOKENS)
        )
    }

    fun setSingleMessageTokens(value: Int) = viewModelScope.launch {
        settingsRepository.setSingleMessageTokens(
            value.coerceIn(
                QuiddityConstants.MIN_SINGLE_MESSAGE_TOKENS,
                QuiddityConstants.MAX_SINGLE_MESSAGE_TOKENS
            )
        )
    }

    fun setContextLimit(value: Int) = viewModelScope.launch {
        settingsRepository.setContextLimit(
            value.coerceIn(
                QuiddityConstants.MIN_CONTEXT_LIMIT,
                QuiddityConstants.MAX_CONTEXT_LIMIT
            )
        )
    }

    fun setTypingDelayEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setTypingDelayEnabled(enabled)
    }

    fun setTypingDelayMsPerChar(value: Int) = viewModelScope.launch {
        settingsRepository.setTypingDelayMsPerChar(value)
    }

    fun setSendDelayEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setSendDelayEnabled(enabled)
    }

    fun setSendDelaySeconds(value: Int) = viewModelScope.launch {
        settingsRepository.setSendDelaySeconds(value)
    }

    // ===== 头像 =====
    fun setUserAvatar(uri: String?) = viewModelScope.launch {
        settingsRepository.setUserAvatar(uri)
    }

    // ===== 模型配置 =====

    /**
     * 模型配置写操作统一委托给 [ApiCatalogManager]。
     */
    fun upsertCatalog(
        id: String?,
        name: String,
        providerId: String,
        apiUrl: String,
        apiModel: String,
        apiKey: String
    ) = viewModelScope.launch {
        val entry = apiCatalogManager.buildEntry(
            id = id,
            name = name,
            providerId = providerId,
            apiUrl = apiUrl,
            apiModel = apiModel,
            apiKey = apiKey
        )
        settingsRepository.upsertCatalog(entry)
    }

    fun removeCatalog(entryId: String) = viewModelScope.launch {
        settingsRepository.removeCatalog(entryId)
    }

    fun setActiveCatalog(id: String?) = viewModelScope.launch {
        settingsRepository.setActiveCatalog(id)
    }

    /** 解密 API Key 用于在编辑器中显示（用户可看到原值）。 */
    fun decryptApiKey(entry: ApiCatalogEntry): String = apiCatalogManager.decryptKey(entry)

    /** 测试 API 连接（封装 Result，UI 层只关心成功 / 失败）。 */
    suspend fun testApiConnection(
        apiUrl: String,
        apiKey: String,
        model: String
    ): Result<String> = apiCatalogManager.testConnection(apiUrl, apiKey, model)

    // ===== 数据导出 / 导入 =====
    suspend fun exportAllPayload(): com.quiddity.app.data.model.ExportPayload {
        val s = settingsRepository.currentSnapshot()
        val convs = conversationRepository.exportAllConversations()
        // 过滤 isNotice 提示气泡：不导出（UI 专用，非对话内容）
        val msgs = conversationRepository.exportAllMessages()
            .mapValues { (_, list) -> list.filterNot { it.isNotice } }
        return com.quiddity.app.data.model.ExportPayload(
            schemaVersion = 1,
            exportedAt = System.currentTimeMillis(),
            settings = s,
            conversations = convs,
            messages = msgs
        )
    }

    suspend fun importAllPayload(payload: com.quiddity.app.data.model.ExportPayload) {
        val sanitizedSettings = if (payload.listWallpaper == null) {
            payload.settings.copy(listWallpaperUri = null)
        } else {
            payload.settings
        }
        settingsRepository.update { _ -> sanitizedSettings }
        conversationRepository.importAll(payload.conversations, payload.messages)
    }
}

class SettingsViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val conversationRepository: ConversationRepository,
    private val apiCatalogManager: ApiCatalogManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(settingsRepository, conversationRepository, apiCatalogManager) as T
    }
}
