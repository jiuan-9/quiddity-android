package com.quiddity.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.quiddity.app.data.model.AppSettings
import com.quiddity.app.data.model.ApiCatalogEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

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



/**
 * 应用设置本地存储（DataStore Preferences）。
 *
 * 所有默认值统一从 [AppSettings.Default] 派生，避免遗漏导致用户升级后行为不一致。
 */
val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "quiddity_settings",
    // 容错：设置文件损坏时用空设置代替，避免启动时读设置抛异常闪退
    corruptionHandler = ReplaceFileCorruptionHandler { _ -> emptyPreferences() }
)

class SettingsStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * 启动屏快速偏好镜像（SharedPreferences）。
     *
     * 设计目的：DataStore 是异步的，Application.onCreate 中无法同步读取 darkMode。
     * 但 splash 主题需要在应用启动瞬间确定（Android 12+ 由系统在 Activity 启动前
     * 渲染）。因此用 SharedPreferences 做 darkMode 的同步镜像——每次设置变更时
     * 同步写入，启动时同步读取，确保 splash 颜色与用户主题设置完全一致。
     */
    private val splashPrefs = context.getSharedPreferences("splash_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val SPLASH_KEY_DARK_MODE = "dark_mode"
        /**
         * 同步读取 darkMode（供 Application.onCreate / MainActivity splash 主题判定使用）。
         * 默认返回 true（与 [AppSettings.Default.darkMode] 一致：暗色为默认主题）。
         */
        fun readDarkModeSync(context: Context): Boolean =
            context.getSharedPreferences("splash_prefs", Context.MODE_PRIVATE)
                .getBoolean(SPLASH_KEY_DARK_MODE, true)
    }

    private object Keys {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val USER_AVATAR = stringPreferencesKey("user_avatar_uri")
        val GLOBAL_MAX_TOKENS = intPreferencesKey("global_max_tokens")
        val GLOBAL_SINGLE_MSG_TOKENS = intPreferencesKey("global_single_msg_tokens")
        val GLOBAL_CONTEXT_LIMIT = intPreferencesKey("global_context_limit")
        val MULTILINE_SPLIT = booleanPreferencesKey("multiline_split")
        val ENTER_TO_SEND = booleanPreferencesKey("enter_to_send")
        val ACTIVE_CATALOG_ID = stringPreferencesKey("active_catalog_id")
        val CATALOG_JSON = stringPreferencesKey("catalog_json")
        val OCR_ENABLED = booleanPreferencesKey("ocr_enabled")
        val VISION_CATALOG_JSON = stringPreferencesKey("vision_catalog_json")
        val ACTIVE_VISION_CATALOG_ID = stringPreferencesKey("active_vision_catalog_id")
        val BRACKET_GRAY_ENABLED = booleanPreferencesKey("bracket_gray_enabled")
        val MARKDOWN_ENABLED = booleanPreferencesKey("markdown_enabled")
        val LIST_WALLPAPER_URI = stringPreferencesKey("list_wallpaper_uri")
        val LIST_WALLPAPER_DARKEN = floatPreferencesKey("list_wallpaper_darken")
        val TYPING_DELAY_ENABLED = booleanPreferencesKey("typing_delay_enabled")
        val TYPING_DELAY_MS_PER_CHAR = intPreferencesKey("typing_delay_ms_per_char")
        val SEND_DELAY_ENABLED = booleanPreferencesKey("send_delay_enabled")
        val SEND_DELAY_SECONDS = intPreferencesKey("send_delay_seconds")
        val FOLLOW_SYSTEM_FONT = booleanPreferencesKey("follow_system_font")
        val FONT_SCALE = floatPreferencesKey("font_scale")
        val PROACTIVE_MESSAGE_ENABLED = booleanPreferencesKey("proactive_message_enabled")
        val PROACTIVE_MESSAGE_LAST_RESET_DATE = stringPreferencesKey("proactive_message_last_reset_date")
        val GROUP_TUTORIAL_SEEN = booleanPreferencesKey("group_tutorial_seen")
        val SOLO_CHAT_COUNTER = intPreferencesKey("solo_chat_counter")
        val GROUP_CHAT_COUNTER = intPreferencesKey("group_chat_counter")
        val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val OVERLAY_AVATAR_URI = stringPreferencesKey("overlay_avatar_uri")
    }

    /** 从 Preferences 还原 [AppSettings]，缺省值统一来自 [AppSettings.Default]。 */
    private fun Preferences.toAppSettings(): AppSettings {
        // 容错：任一偏好类型异常（如损坏文件里类型错位）都回退默认值，不阻塞读写
        return try {
            val d = AppSettings.Default
            AppSettings(
                darkMode = this[Keys.DARK_MODE] ?: d.darkMode,
                userAvatarUri = this[Keys.USER_AVATAR] ?: d.userAvatarUri,
                globalMaxTokens = this[Keys.GLOBAL_MAX_TOKENS] ?: d.globalMaxTokens,
                globalSingleMessageTokens = this[Keys.GLOBAL_SINGLE_MSG_TOKENS] ?: d.globalSingleMessageTokens,
                globalContextLimit = this[Keys.GLOBAL_CONTEXT_LIMIT] ?: d.globalContextLimit,
                multilineAutoSplit = this[Keys.MULTILINE_SPLIT] ?: d.multilineAutoSplit,
                enterToSend = this[Keys.ENTER_TO_SEND] ?: d.enterToSend,
                activeCatalogId = this[Keys.ACTIVE_CATALOG_ID] ?: d.activeCatalogId,
                catalog = parseCatalog(this[Keys.CATALOG_JSON]),
                ocrEnabled = this[Keys.OCR_ENABLED] ?: d.ocrEnabled,
                visionCatalog = parseCatalog(this[Keys.VISION_CATALOG_JSON]),
                activeVisionCatalogId =
                    this[Keys.ACTIVE_VISION_CATALOG_ID] ?: d.activeVisionCatalogId,
                bracketGrayEnabled = this[Keys.BRACKET_GRAY_ENABLED] ?: d.bracketGrayEnabled,
                markdownEnabled = this[Keys.MARKDOWN_ENABLED] ?: d.markdownEnabled,
                listWallpaperUri = this[Keys.LIST_WALLPAPER_URI] ?: d.listWallpaperUri,
                listWallpaperDarken = this[Keys.LIST_WALLPAPER_DARKEN] ?: d.listWallpaperDarken,
                typingDelayEnabled = this[Keys.TYPING_DELAY_ENABLED] ?: d.typingDelayEnabled,
                typingDelayMsPerChar = this[Keys.TYPING_DELAY_MS_PER_CHAR] ?: d.typingDelayMsPerChar,
                sendDelayEnabled = this[Keys.SEND_DELAY_ENABLED] ?: d.sendDelayEnabled,
                sendDelaySeconds = this[Keys.SEND_DELAY_SECONDS] ?: d.sendDelaySeconds,
                followSystemFont = this[Keys.FOLLOW_SYSTEM_FONT] ?: d.followSystemFont,
                fontScale = this[Keys.FONT_SCALE] ?: d.fontScale,
                proactiveMessageEnabled = this[Keys.PROACTIVE_MESSAGE_ENABLED] ?: d.proactiveMessageEnabled,
                proactiveMessageLastResetDate =
                    this[Keys.PROACTIVE_MESSAGE_LAST_RESET_DATE] ?: d.proactiveMessageLastResetDate,
                groupTutorialSeen = this[Keys.GROUP_TUTORIAL_SEEN] ?: d.groupTutorialSeen,
                soloChatCounter = this[Keys.SOLO_CHAT_COUNTER] ?: d.soloChatCounter,
                groupChatCounter = this[Keys.GROUP_CHAT_COUNTER] ?: d.groupChatCounter,
                overlayEnabled = this[Keys.OVERLAY_ENABLED] ?: d.overlayEnabled,
                overlayAvatarUri = this[Keys.OVERLAY_AVATAR_URI] ?: d.overlayAvatarUri
            )
        } catch (t: Throwable) {
            android.util.Log.e("SettingsStore", "解析设置失败，回退默认值", t)
            AppSettings.Default
        }
    }

    val data: Flow<AppSettings> = context.appSettingsDataStore.data.map { it.toAppSettings() }

    /**
     * 写入设置。返回是否写盘成功（失败记录日志，调用方据此提示）。
     * 统一契约：写失败不抛异常，避免未捕获协程异常导致 App 闪退。
     */
    suspend fun update(block: (AppSettings) -> AppSettings): Boolean {
        return try {
            context.appSettingsDataStore.edit { prefs ->
                val current = prefs.toAppSettings()
                val next = block(current)
                // 名册 / 视觉 OCR 名册 JSON 序列化失败时整个事务中止（不写任何键），
                // 避免"界面提示保存成功、实际名册未落盘"的静默不一致
                val (catalogJson, visionCatalogJson) = try {
                    val catalogEncoded = json.encodeToString(
                        kotlinx.serialization.builtins.ListSerializer(ApiCatalogEntry.serializer()),
                        next.catalog
                    )
                    val visionEncoded = json.encodeToString(
                        kotlinx.serialization.builtins.ListSerializer(ApiCatalogEntry.serializer()),
                        next.visionCatalog
                    )
                    catalogEncoded to visionEncoded
                } catch (t: Throwable) {
                    android.util.Log.e("SettingsStore", "序列化 API 名册失败，事务中止", t)
                    throw t
                }
                prefs[Keys.DARK_MODE] = next.darkMode
                // 同步镜像 darkMode 到 SharedPreferences，确保下次启动 splash 主题与用户设置一致
                splashPrefs.edit().putBoolean(SPLASH_KEY_DARK_MODE, next.darkMode).apply()
                next.userAvatarUri?.let { prefs[Keys.USER_AVATAR] = it } ?: prefs.remove(Keys.USER_AVATAR)
                prefs[Keys.GLOBAL_MAX_TOKENS] = next.globalMaxTokens
                prefs[Keys.GLOBAL_SINGLE_MSG_TOKENS] = next.globalSingleMessageTokens
                prefs[Keys.GLOBAL_CONTEXT_LIMIT] = next.globalContextLimit
                prefs[Keys.MULTILINE_SPLIT] = next.multilineAutoSplit
                prefs[Keys.ENTER_TO_SEND] = next.enterToSend
                next.activeCatalogId?.let { prefs[Keys.ACTIVE_CATALOG_ID] = it } ?: prefs.remove(Keys.ACTIVE_CATALOG_ID)
                prefs[Keys.OCR_ENABLED] = next.ocrEnabled
                prefs[Keys.VISION_CATALOG_JSON] = visionCatalogJson
                next.activeVisionCatalogId?.let { prefs[Keys.ACTIVE_VISION_CATALOG_ID] = it }
                    ?: prefs.remove(Keys.ACTIVE_VISION_CATALOG_ID)
                prefs[Keys.BRACKET_GRAY_ENABLED] = next.bracketGrayEnabled
                prefs[Keys.MARKDOWN_ENABLED] = next.markdownEnabled
                next.listWallpaperUri?.let { prefs[Keys.LIST_WALLPAPER_URI] = it } ?: prefs.remove(Keys.LIST_WALLPAPER_URI)
                prefs[Keys.LIST_WALLPAPER_DARKEN] = next.listWallpaperDarken
                prefs[Keys.TYPING_DELAY_ENABLED] = next.typingDelayEnabled
                prefs[Keys.TYPING_DELAY_MS_PER_CHAR] = next.typingDelayMsPerChar
                prefs[Keys.SEND_DELAY_ENABLED] = next.sendDelayEnabled
                prefs[Keys.SEND_DELAY_SECONDS] = next.sendDelaySeconds
                prefs[Keys.FOLLOW_SYSTEM_FONT] = next.followSystemFont
                prefs[Keys.FONT_SCALE] = next.fontScale
                prefs[Keys.PROACTIVE_MESSAGE_ENABLED] = next.proactiveMessageEnabled
                prefs[Keys.PROACTIVE_MESSAGE_LAST_RESET_DATE] = next.proactiveMessageLastResetDate
                prefs[Keys.GROUP_TUTORIAL_SEEN] = next.groupTutorialSeen
                prefs[Keys.SOLO_CHAT_COUNTER] = next.soloChatCounter
                prefs[Keys.GROUP_CHAT_COUNTER] = next.groupChatCounter
                prefs[Keys.OVERLAY_ENABLED] = next.overlayEnabled
                next.overlayAvatarUri?.let { prefs[Keys.OVERLAY_AVATAR_URI] = it }
                    ?: prefs.remove(Keys.OVERLAY_AVATAR_URI)
                prefs[Keys.CATALOG_JSON] = catalogJson
            }
            true
        } catch (t: Throwable) {
            android.util.Log.e("SettingsStore", "写入设置失败", t)
            false
        }
    }

    /** 预加载设置数据，确保首次写入前磁盘数据已加载到内存。 */
    private fun parseCatalog(jsonStr: String?): List<ApiCatalogEntry> {
        if (jsonStr.isNullOrEmpty()) return emptyList()
        return runCatching {
            val list = json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(ApiCatalogEntry.serializer()),
                jsonStr
            )
            // 存量数据修复：早期版本可能把新建条目存成空 id（""），
            // 会导致列表 key 重复崩溃 / 保存互相覆盖。加载时自动补独立 id。
            list.map { entry ->
                if (entry.id.isBlank()) {
                    entry.copy(id = com.quiddity.app.util.IdGenerator.newId(
                        com.quiddity.app.util.IdGenerator.Prefix.CATALOG_ENTRY
                    ))
                } else {
                    entry
                }
            }
        }.getOrElse {
            android.util.Log.w("SettingsStore", "解析 API 名册 JSON 失败", it)
            emptyList()
        }
    }
}
