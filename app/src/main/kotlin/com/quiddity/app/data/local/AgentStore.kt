package com.quiddity.app.data.local

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/*
 * ============================================================================
 * 开发规范 (Development Specifications)
 * ============================================================================
 *
 * 1. 问题修复规范
 *    所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
 *    修复内容需完全融入现有代码架构，保证代码逻辑的连贯性、可维护性和可扩展性。
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
 * Agent 工具开关集合（v2 五分类重构）。
 *
 * - [tools]：每工具独立开关（唯一事实来源），工具名 → 是否启用。
 *   空 Map = 尚未迁移的旧数据（v1 按字段存储），读取时由 [migrateLegacy] 补齐。
 * - v1 旧字段仅用于迁移与向后兼容读取，任何运行期判断一律走 [tools]。
 *
 * 五分类（工具使用开关 UI 分组）：
 * - 只读（监听类、读取类）：read
 * - 更改（创建、更改、改入等）：modify
 * - 删除（仅删除类）：delete
 * - 行为（截图、滑动等 AI 实际操作项，包括发送信息）：act
 * - 识图（仅供 AI 自行使用 OCR）：ocr
 */
@Serializable
data class AgentToolSwitches(
    val tools: Map<String, Boolean> = emptyMap(),
    // ===== v1 旧字段（仅迁移读取；运行期一律走 [tools]） =====
    val sense_screen: Boolean = true,
    val sense_notifications: Boolean = true,
    val sense_usage: Boolean = true,
    val read_apps: Boolean = true,
    val read_system: Boolean = true,
    val read_app_info: Boolean = true,
    val read_battery: Boolean = true,
    val read_traffic: Boolean = true,
    val read_screenshot: Boolean = true,
    val read_logs: Boolean = true,
    val read_ocr: Boolean = true,
    val sense_toasts: Boolean = true,
    val interact_notify: Boolean = true,
    val read_clipboard: Boolean = false,
    val write_clipboard: Boolean = false,
    val run_shell: Boolean = false,
    val simulate_click: Boolean = false,
    val open_app: Boolean = false,
    val read_files: Boolean = false,
    val write_files: Boolean = false,
    val write_disable: Boolean = false,
    val write_appops: Boolean = false,
    val write_force_stop: Boolean = false,
    val write_uninstall: Boolean = false
) {

    /** 指定工具的开关状态；未在 Map 中的工具按旧字段 / 默认值兜底（[default] 为工具注册表默认值）。 */
    fun isEnabled(toolName: String, default: Boolean? = null): Boolean {
        tools[toolName]?.let { return it }
        TOOL_TO_LEGACY[toolName]?.let { legacyName ->
            return legacyValue(legacyName)
        }
        return default ?: DEFAULT_ENABLED[toolName] ?: true
    }

    /** 旧字段值查询（v1 迁移兜底）。 */
    private fun legacyValue(name: String): Boolean = when (name) {
        "sense_screen" -> sense_screen
        "sense_notifications" -> sense_notifications
        "sense_usage" -> sense_usage
        "read_apps" -> read_apps
        "read_system" -> read_system
        "read_app_info" -> read_app_info
        "read_battery" -> read_battery
        "read_traffic" -> read_traffic
        "read_screenshot" -> read_screenshot
        "read_logs" -> read_logs
        "read_ocr" -> read_ocr
        "sense_toasts" -> sense_toasts
        "interact_notify" -> interact_notify
        "read_clipboard" -> read_clipboard
        "write_clipboard" -> write_clipboard
        "run_shell" -> run_shell
        "simulate_click" -> simulate_click
        "open_app" -> open_app
        "read_files" -> read_files
        "write_files" -> write_files
        "write_disable" -> write_disable
        "write_appops" -> write_appops
        "write_force_stop" -> write_force_stop
        "write_uninstall" -> write_uninstall
        else -> true
    }

    /** 迁移为纯 [tools] 形态（tools 为空时调用）：旧字段 → 每工具开关，缺失工具按默认值补齐。 */
    fun migrateLegacy(): AgentToolSwitches {
        if (tools.isNotEmpty()) return this
        val migrated = mutableMapOf<String, Boolean>()
        TOOL_TO_LEGACY.forEach { (toolName, legacyName) ->
            migrated[toolName] = legacyValue(legacyName)
        }
        DEFAULT_ENABLED.forEach { (name, def) ->
            if (name !in migrated) migrated[name] = def
        }
        return copy(tools = migrated)
    }

    companion object {

        /** 工具默认开关（与工具注册表 enabledByDefault 保持一致；迁移缺失项兜底）。 */
        val DEFAULT_ENABLED: Map<String, Boolean> = mapOf(
            "list_apps" to true,
            "read_screen" to true,
            "get_time" to true,
            "read_notifications" to true,
            "usage_stats" to true,
            "foreground_app" to true,
            "app_permissions" to true,
            "app_install_info" to true,
            "app_battery" to true,
            "traffic_ranking" to true,
            "file_access" to true,
            "screenshot" to true,
            "system_logs" to true,
            "app_logs" to true,
            "ocr_image" to true,
            "notify_self" to true,
            "toast_monitor" to true,
            "notification_guard" to true,
            "app_usage_detail" to true,
            "read_file" to false,
            "list_files" to false,
            "file_info" to false,
            "reveal_file" to false,
            "create_file" to false,
            "write_file" to false,
            "append_file" to false,
            "rename_file" to false,
            "mkdir" to false,
            "move_file" to false,
            "copy_file" to false,
            "delete_file" to false,
            "read_clipboard" to false,
            "write_clipboard" to false,
            "clear_clipboard" to false,
            "run_shell" to false,
            "click" to false,
            "long_press" to false,
            "click_text" to false,
            "scroll" to false,
            "global_action" to false,
            "input_text" to false,
            "click_id" to false,
            "click_desc" to false,
            "drag" to false,
            "scroll_to_text" to false,
            "lock_screen" to false,
            "open_app" to false,
            "dismiss_notification" to false,
            "reply_notification" to false,
            "schedule_notify" to false,
            "sleep" to true,
            "disable_app" to false,
            "enable_app" to false,
            "set_appops" to false,
            "force_stop" to false,
            "uninstall_app" to false
        )

        /** 工具名 → 旧字段名（迁移用）。 */
        val TOOL_TO_LEGACY: Map<String, String> = mapOf(
            "read_screen" to "sense_screen",
            "read_notifications" to "sense_notifications",
            "notification_guard" to "sense_notifications",
            "dismiss_notification" to "sense_notifications",
            "reply_notification" to "sense_notifications",
            "usage_stats" to "sense_usage",
            "foreground_app" to "sense_usage",
            "app_usage_detail" to "sense_usage",
            "list_apps" to "read_apps",
            "app_permissions" to "read_app_info",
            "app_install_info" to "read_app_info",
            "file_access" to "read_app_info",
            "app_battery" to "read_battery",
            "traffic_ranking" to "read_traffic",
            "screenshot" to "read_screenshot",
            "system_logs" to "read_logs",
            "app_logs" to "read_logs",
            "ocr_image" to "read_ocr",
            "toast_monitor" to "sense_toasts",
            "notify_self" to "interact_notify",
            "schedule_notify" to "interact_notify",
            "read_clipboard" to "read_clipboard",
            "write_clipboard" to "write_clipboard",
            "clear_clipboard" to "write_clipboard",
            "run_shell" to "run_shell",
            "click" to "simulate_click",
            "long_press" to "simulate_click",
            "click_text" to "simulate_click",
            "scroll" to "simulate_click",
            "global_action" to "simulate_click",
            "input_text" to "simulate_click",
            "click_id" to "simulate_click",
            "click_desc" to "simulate_click",
            "drag" to "simulate_click",
            "scroll_to_text" to "simulate_click",
            "lock_screen" to "simulate_click",
            "open_app" to "open_app",
            "read_file" to "read_files",
            "list_files" to "read_files",
            "file_info" to "read_files",
            "reveal_file" to "read_files",
            "create_file" to "write_files",
            "write_file" to "write_files",
            "append_file" to "write_files",
            "rename_file" to "write_files",
            "mkdir" to "write_files",
            "move_file" to "write_files",
            "copy_file" to "write_files",
            "delete_file" to "write_files",
            "disable_app" to "write_disable",
            "enable_app" to "write_disable",
            "set_appops" to "write_appops",
            "force_stop" to "write_force_stop",
            "uninstall_app" to "write_uninstall"
        )

    }
}

/**
 * 权限管控程度：
 * - ASK（过问）：危险 / 写入类工具每次执行前弹窗让用户确认（批量收集一次展示）；
 * - FULL（完全）：信任开关与黑名单，不再逐次弹窗，自动执行。
 */
@Serializable
enum class AgentPermissionControl {
    ASK,
    FULL
}

/**
 * Agent 工具执行审计条目。
 *
 * [ts] 为 ISO 时间戳；[args] 为工具参数 JSON 文本；
 * [ok] 表示执行是否成功；[confirmed] 表示是否经过用户确认。
 */
@Serializable
data class AgentAuditEntry(
    val ts: String,
    val tool: String,
    val args: String,
    val ok: Boolean,
    val confirmed: Boolean
)

/**
 * Agent 模式设置快照（agent-settings.json）。
 *
 * - [blacklist]：黑名单（包名 / 文件路径）。默认全应用权限；黑名单内的应用与文件，
 *   AI 无权查看、更改或删除。
 * - [toolSwitches]：各工具开关（v2：每工具独立 + 五分类）。
 * - [audit]：审计记录，最多保留 500 条（FIFO）。
 */
@Serializable
data class AgentSettings(
    val version: Int = 2,
    /**
     * 黑名单（默认全应用权限）。
     * 条目为包名（如 com.tencent.mm）或文件/目录绝对路径（如 /sdcard/Download/private）。
     * 命中黑名单的应用或路径：AI 无权查看、更改、删除。
     */
    val blacklist: List<String> = emptyList(),
    /**
     * v1 遗留白名单（已废弃：语义反转，不再参与任何门控）。
     * 旧数据迁移时保留原值以便用户查看，运行期一律忽略。
     */
    val whitelist: List<String> = emptyList(),
    val toolSwitches: AgentToolSwitches = AgentToolSwitches(),
    val permissionControl: AgentPermissionControl = AgentPermissionControl.ASK,
    val audit: List<AgentAuditEntry> = emptyList()
)

/**
 * Agent 设置本地存储：agent-settings.json（AtomicFile 原子写）。
 *
 * 所有可测逻辑收敛到 companion 纯函数（[applySwitch] / [applyCategory] / [cappedAudit] /
 * [blacklistWith] / [blacklistWithout] / [encode] / [decode]），
 * 便于 JVM 单元测试；文件读写层保持薄封装。
 */
class AgentStore(private val context: Context) {

    companion object {
        const val AUDIT_LIMIT = 500

        val json: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun encode(settings: AgentSettings): String =
            json.encodeToString(AgentSettings.serializer(), settings)

        fun decode(text: String): AgentSettings {
            if (text.isBlank()) return AgentSettings()
            return runCatching {
                var settings = json.decodeFromString(AgentSettings.serializer(), text)
                // v1 → v2 迁移：
                // 1. 工具开关：tools 为空时按旧字段补齐为每工具开关
                // 2. 白名单：语义已反转废弃，不再参与门控（保留字段仅供查看）
                settings = settings.copy(toolSwitches = settings.toolSwitches.migrateLegacy())
                settings
            }.getOrElse {
                android.util.Log.w("AgentStore", "解析 agent-settings.json 失败", it)
                AgentSettings()
            }
        }

        /** 应用工具开关（工具名）；未知开关名返回 null。 */
        fun applySwitch(switches: AgentToolSwitches, name: String, enabled: Boolean): AgentToolSwitches? {
            val normalized = switches.migrateLegacy()
            if (name !in AgentToolSwitches.DEFAULT_ENABLED) return null
            return normalized.copy(tools = normalized.tools + (name to enabled))
        }

        /**
         * 整类工具开关：一键开启/关闭某分类下全部工具。
         * [categoryTools] 为该分类下的工具名集合。
         */
        fun applyCategory(
            switches: AgentToolSwitches,
            categoryTools: Set<String>,
            enabled: Boolean
        ): AgentToolSwitches {
            val normalized = switches.migrateLegacy()
            val next = normalized.tools.toMutableMap()
            categoryTools.forEach { next[it] = enabled }
            return normalized.copy(tools = next)
        }

        /** 追加审计条目，超限时按 FIFO 丢弃最旧记录。 */
        fun cappedAudit(current: List<AgentAuditEntry>, entry: AgentAuditEntry): List<AgentAuditEntry> {
            val next = current + entry
            return if (next.size > AUDIT_LIMIT) next.takeLast(AUDIT_LIMIT) else next
        }

        /** 应用权限管控程度；未知值回退 ASK。 */
        fun applyPermissionControl(
            settings: AgentSettings,
            control: AgentPermissionControl
        ): AgentSettings = settings.copy(permissionControl = control)

        /** 追加黑名单（去重，保持顺序）。 */
        fun blacklistWith(current: List<String>, entry: String): List<String> =
            if (entry in current) current else current + entry

        /** 从黑名单移除条目。 */
        fun blacklistWithout(current: List<String>, entry: String): List<String> =
            current.filterNot { it == entry }
    }

    private val dataDir: File by lazy {
        File(context.filesDir, "quiddity-data").apply { mkdirs() }
    }

    private val prefsFile: File by lazy {
        File(dataDir, "agent-settings.json")
    }

    private val prefsAtomicFile: AtomicFile by lazy {
        AtomicFile(prefsFile)
    }

    private val _settings = MutableStateFlow(AgentSettings())
    val settings: StateFlow<AgentSettings> = _settings.asStateFlow()

    private val writeMutex = Mutex()

    suspend fun load() = withContext(Dispatchers.IO) {
        _settings.value = readPrefs()
    }

    fun snapshot(): AgentSettings = _settings.value

    suspend fun setToolSwitch(name: String, enabled: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                val current = _settings.value
                val next = applySwitch(current.toolSwitches, name, enabled) ?: return@withLock false
                writePrefs(current.copy(toolSwitches = next))
                _settings.value = current.copy(toolSwitches = next)
                true
            }
        }

    /** 整类工具开关：一键开启/关闭某分类下全部工具。 */
    suspend fun setCategorySwitch(categoryTools: Set<String>, enabled: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                val current = _settings.value
                val next = applyCategory(current.toolSwitches, categoryTools, enabled)
                writePrefs(current.copy(toolSwitches = next))
                _settings.value = current.copy(toolSwitches = next)
                true
            }
        }

    suspend fun addBlacklist(entry: String) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val current = _settings.value
            val next = current.copy(blacklist = blacklistWith(current.blacklist, entry.trim()))
            writePrefs(next)
            _settings.value = next
        }
    }

    suspend fun removeBlacklist(entry: String) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val current = _settings.value
            val next = current.copy(blacklist = blacklistWithout(current.blacklist, entry))
            writePrefs(next)
            _settings.value = next
        }
    }

    /** 整体覆盖工具开关与黑名单（导入恢复用）。 */
    suspend fun replaceAll(toolSwitches: AgentToolSwitches, blacklist: List<String>) =
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                val current = _settings.value
                val next = current.copy(
                    toolSwitches = toolSwitches.migrateLegacy(),
                    blacklist = blacklist
                )
                writePrefs(next)
                _settings.value = next
            }
        }

    suspend fun appendAudit(entry: AgentAuditEntry) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val current = _settings.value
            val next = current.copy(audit = cappedAudit(current.audit, entry))
            writePrefs(next)
            _settings.value = next
        }
    }

    suspend fun setPermissionControl(control: AgentPermissionControl) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val current = _settings.value
            val next = applyPermissionControl(current, control)
            writePrefs(next)
            _settings.value = next
        }
    }

    private fun readPrefs(): AgentSettings {
        if (!prefsFile.exists()) return AgentSettings()
        return runCatching {
            val text = prefsAtomicFile.openRead().use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
            decode(text)
        }.getOrElse {
            android.util.Log.w("AgentStore", "读取 agent-settings.json 失败", it)
            AgentSettings()
        }
    }

    private fun writePrefs(settings: AgentSettings) {
        val text = encode(settings)
        runCatching {
            var stream = prefsAtomicFile.startWrite()
            try {
                stream.write(text.toByteArray(Charsets.UTF_8))
                stream.flush()
                prefsAtomicFile.finishWrite(stream)
            } catch (t: Throwable) {
                prefsAtomicFile.failWrite(stream)
                throw t
            }
        }.onFailure { android.util.Log.e("AgentStore", "写入 agent-settings.json 失败", it) }
    }
}
