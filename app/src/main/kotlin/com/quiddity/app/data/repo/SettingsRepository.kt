package com.quiddity.app.data.repo

import com.quiddity.app.data.local.SettingsStore
import com.quiddity.app.data.model.AppSettings
import com.quiddity.app.data.model.ApiCatalogEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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


// 当前规则：暴露 Flow 给 UI；提供 currentSnapshot 避免初始化期间空状态。
class SettingsRepository(private val store: SettingsStore) {

    private val _snapshot = MutableStateFlow(AppSettings.Default)
    val snapshot: StateFlow<AppSettings> = _snapshot.asStateFlow()

    // ===== 三条开发规范（位于文件中间位置） =====
    // 1. 问题修复规范：所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
    //    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
    // 2. 代码注释规范：文件内仅允许保留两类注释——当前规则说明注释与模块划分注释（// ===== xxx =====）。
    //    除此之外，禁止出现任何形式的代码注释（包括单行 / 多行 / 临时调试注释）。
    // 3. 构建交付要求：完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK。
    //    APK 需满足：签名有效且符合发布标准、包含完整功能模块、经过基础性能与兼容性测试。

    private val sharedSettings: StateFlow<AppSettings> = store.data
        .onEach { _snapshot.value = it }
        .stateIn(
            scope = supervisor,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = _snapshot.value
        )

    @Volatile
    private var initialized = false

    fun observeSettings(): Flow<AppSettings> = sharedSettings

    fun currentSnapshot(): AppSettings = _snapshot.value

    /**
     * 确保设置数据已从磁盘加载到内存，避免首次写入覆盖磁盘数据。
     *
     * 多次调用只会触发一次磁盘读取。
     */
    private val initMutex = Mutex()
    suspend fun ensureInitialized() {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return@withLock
            _snapshot.value = store.data.first()
            initialized = true
        }
    }

    suspend fun update(block: (AppSettings) -> AppSettings) {
        ensureInitialized()
        store.update(block)
    }

    suspend fun setDarkMode(enabled: Boolean) = update { it.copy(darkMode = enabled) }
    suspend fun setUserAvatar(uri: String?) = update { it.copy(userAvatarUri = uri) }
    suspend fun setMultilineSplit(enabled: Boolean) = update { it.copy(multilineAutoSplit = enabled) }
    suspend fun setEnterToSend(enabled: Boolean) = update { it.copy(enterToSend = enabled) }
    suspend fun setMaxTokens(value: Int) = update { it.copy(globalMaxTokens = value) }
    suspend fun setSingleMessageTokens(value: Int) = update { it.copy(globalSingleMessageTokens = value) }
    suspend fun setContextLimit(value: Int) = update { it.copy(globalContextLimit = value) }
    /**
     * 与其他 setter 保持一致：仅走 update {} 流程（DataStore.edit 原子事务）。
     */
    suspend fun setBracketGrayEnabled(enabled: Boolean) = update { it.copy(bracketGrayEnabled = enabled) }

    /**
     *
     * - uri 为 null 时清除壁纸
     * - uri 为 file:// 指向 filesDir/list_wallpapers/ 下的持久化文件
     * - 与对话级壁纸独立存储（此处存在 AppSettings，对话级存在 Conversation）
     */
    suspend fun setListWallpaperUri(uri: String?) = update { it.copy(listWallpaperUri = uri) }

    /**
     * 会话列表壁纸暗化程度 0.0f - 1.0f。
     */
    suspend fun setListWallpaperDarken(value: Float) = update {
        it.copy(listWallpaperDarken = value.coerceIn(0f, 1f))
    }

    suspend fun setTypingDelayEnabled(enabled: Boolean) = update {
        it.copy(typingDelayEnabled = enabled)
    }

    suspend fun setTypingDelayMsPerChar(value: Int) = update {
        it.copy(
            typingDelayMsPerChar = value.coerceIn(
                com.quiddity.app.util.QuiddityConstants.MIN_TYPING_DELAY_MS_PER_CHAR,
                com.quiddity.app.util.QuiddityConstants.MAX_TYPING_DELAY_MS_PER_CHAR
            )
        )
    }

    suspend fun setSendDelayEnabled(enabled: Boolean) = update {
        it.copy(sendDelayEnabled = enabled)
    }

    suspend fun setSendDelaySeconds(value: Int) = update {
        it.copy(
            sendDelaySeconds = value.coerceIn(
                com.quiddity.app.util.QuiddityConstants.MIN_SEND_DELAY_SECONDS,
                com.quiddity.app.util.QuiddityConstants.MAX_SEND_DELAY_SECONDS
            )
        )
    }

    suspend fun setFollowSystemFont(enabled: Boolean) = update {
        it.copy(followSystemFont = enabled)
    }

    suspend fun setFontScale(value: Float) = update {
        it.copy(
            fontScale = value.coerceIn(
                com.quiddity.app.util.QuiddityConstants.MIN_FONT_SCALE,
                com.quiddity.app.util.QuiddityConstants.MAX_FONT_SCALE
            )
        )
    }

    suspend fun setProactiveMessageEnabled(enabled: Boolean) = update {
        it.copy(proactiveMessageEnabled = enabled)
    }

    suspend fun setProactiveMessageLastResetDate(date: String) = update {
        it.copy(proactiveMessageLastResetDate = date)
    }

    suspend fun upsertCatalog(entry: ApiCatalogEntry) = update { s ->
        val list = s.catalog.filterNot { it.id == entry.id } + entry
        s.copy(catalog = list, activeCatalogId = s.activeCatalogId ?: entry.id)
    }

    suspend fun removeCatalog(entryId: String) = update { s ->
        val list = s.catalog.filterNot { it.id == entryId }
        val active = if (s.activeCatalogId == entryId) list.firstOrNull()?.id else s.activeCatalogId
        s.copy(catalog = list, activeCatalogId = active)
    }

    suspend fun setActiveCatalog(id: String?) = update { it.copy(activeCatalogId = id) }

    fun getCatalogEntry(id: String?): ApiCatalogEntry? {
        if (id == null) return null
        return _snapshot.value.catalog.firstOrNull { it.id == id }
    }

    companion object {
        /**
         * 共享上游订阅的停止超时：所有消费者断开后等待 5s 再停止，
         * 避免快速切屏（Home → Chat）期间重启订阅带来的不必要 IO。
         */
        private const val STOP_TIMEOUT_MS = 5_000L

        /**
         * 仓库级后台协程作用域，独立于 Activity / ViewModel 生命周期，
         * 监督 [sharedSettings] 的持续订阅。
         */
        private val supervisor: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
