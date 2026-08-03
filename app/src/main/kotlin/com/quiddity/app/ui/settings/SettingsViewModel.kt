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
    private val apiCatalogManager: ApiCatalogManager,
    private val characterRepository: CharacterRepository
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

    fun setFollowSystemFont(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setFollowSystemFont(enabled)
    }

    /**
     * 主动消息总设置开关（对应算法文档 2.1）。
     * 仅持久化"已了解该功能"标记；实际生效需在具体会话中单独开启。
     */
    fun setProactiveMessageEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setProactiveMessageEnabled(enabled)
    }

    fun setFontScale(value: Float) = viewModelScope.launch {
        settingsRepository.setFontScale(value)
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
        // 编辑时未重新输入密钥（表单明文未持久化，进程回收后为空）：保留原密文
        val existing = id?.let { settingsRepository.getCatalogEntry(it) }
        val entry = if (apiKey.isBlank() && existing != null) {
            existing.copy(
                name = name,
                providerId = providerId,
                apiUrl = apiUrl,
                apiModel = apiModel
            )
        } else {
            apiCatalogManager.buildEntry(
                id = id,
                name = name,
                providerId = providerId,
                apiUrl = apiUrl,
                apiModel = apiModel,
                apiKey = apiKey
            )
        }
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
    /**
     * 全量导出（schema v2，2.0.0 数据契约）。
     *
     * - 角色库主档：CharacterRepository 当前列表（1.3.0 尚无角色库 UI，通常为空）
     * - 私聊：Conversation + messages 组装为 privateChats；会话保留 persona 缓存副本，characterId 按现状透传
     * - 群聊：groupChats 为空（1.3.0 不加入群聊实体）
     * - 资产节由 DataPorter 导出时读取 Base64 内嵌
     */
    suspend fun exportAllPayloadV2(): ExportPayload {
        val s = settingsRepository.currentSnapshot()
        val convs = conversationRepository.exportAllConversations()
        val msgs = conversationRepository.exportAllMessages()
            .mapValues { (_, list) -> list.filterNot { it.isNotice } }
        val characters = characterRepository.listCharacters()
        val privateChats = convs.map { conv ->
            ConversationBundle(
                conversation = conv,
                messages = msgs[conv.id].orEmpty()
            )
        }
        return ExportPayload(
            schemaVersion = ExportPayload.SCHEMA_VERSION_2,
            exportedAt = System.currentTimeMillis(),
            appVersion = com.quiddity.app.BuildConfig.VERSION_NAME,
            settings = s,
            conversations = emptyList(),
            messages = emptyMap(),
            characters = characters,
            privateChats = privateChats,
            groupChats = emptyList()
        )
    }

    /**
     * 导入完整备份数据。
     *
     * @param payload 解析后的导出数据
     * @param mode 导入模式（3.1）：REPLACE=替换 / MERGE=合并 / CHARACTERS_ONLY=仅导入角色库
     */
    suspend fun importAllPayload(
        payload: com.quiddity.app.data.model.ExportPayload,
        mode: ImportMode = ImportMode.MERGE
    ) {
        val hasWallpaperAsset = payload.listWallpaper != null || payload.assets?.listWallpaper != null
        val sanitizedSettings = if (!hasWallpaperAsset) {
            payload.settings.copy(listWallpaperUri = null)
        } else {
            payload.settings
        }
        when (mode) {
            ImportMode.REPLACE -> {
                settingsRepository.update { _ -> sanitizedSettings }
            }
            ImportMode.MERGE -> {
                // 合并模式：保留本机 UI 偏好（暗色/字体/延迟等），只合并模型配置与缺失的媒体资源，
                // 避免"合并导入"把用户本机设置整个覆盖掉
                settingsRepository.update { local ->
                    mergeSettings(local, sanitizedSettings, hasWallpaperAsset)
                }
            }
            ImportMode.CHARACTERS_ONLY -> Unit
        }
        conversationRepository.importV2Snapshot(
            characters = payload.characters,
            conversations = payload.privateChats.map { it.conversation },
            messages = payload.privateChats.associate { it.conversation.id to it.messages },
            mode = mode
        )
    }

    /**
     * 合并导入时的设置合并规则：
     * - UI 偏好（darkMode / 字体 / 延迟 / 开关）全部保留本机值；
     * - 模型配置按 id 合并（导入条目优先，与会话合并语义一致）；
     * - 激活配置：本机已有则保留，否则用导入值；
     * - 头像 / 列表壁纸：仅当导入文件带对应资产时补入本机缺失项，否则保持本机现状。
     */
    private fun mergeSettings(
        local: AppSettings,
        incoming: AppSettings,
        hasWallpaperAsset: Boolean
    ): AppSettings {
        val incomingIds = incoming.catalog.map { it.id }.toSet()
        val mergedCatalog = incoming.catalog + local.catalog.filterNot { it.id in incomingIds }
        return local.copy(
            catalog = mergedCatalog,
            activeCatalogId = local.activeCatalogId ?: incoming.activeCatalogId,
            userAvatarUri = local.userAvatarUri ?: incoming.userAvatarUri,
            listWallpaperUri = if (hasWallpaperAsset) {
                local.listWallpaperUri ?: incoming.listWallpaperUri
            } else {
                local.listWallpaperUri
            },
            listWallpaperDarken = if (hasWallpaperAsset) {
                if (local.listWallpaperUri == null) incoming.listWallpaperDarken else local.listWallpaperDarken
            } else {
                local.listWallpaperDarken
            }
        )
    }

    /**
     * 当前是否有会话数据（UI 据此决定是否弹窗让用户抉择导入方式）。
     */
    fun hasExistingData(): Boolean = conversationRepository.hasConversations()
}

class SettingsViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val conversationRepository: ConversationRepository,
    private val apiCatalogManager: ApiCatalogManager,
    private val characterRepository: CharacterRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SettingsViewModel(
            settingsRepository,
            conversationRepository,
            apiCatalogManager,
            characterRepository
        ) as T
    }
}
