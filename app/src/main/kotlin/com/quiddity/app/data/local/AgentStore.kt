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
 * Agent 工具开关集合。
 *
 * 与工具清单一一对应：
 * - 感知类：sense_screen / sense_notifications / sense_usage
 * - 读取类：read_apps / read_system
 * - 写入类：write_disable / write_appops / write_force_stop / write_uninstall
 *   （写入类默认关闭，需 Shizuku 授权后由用户在设置中开启）
 */
@Serializable
data class AgentToolSwitches(
    val sense_screen: Boolean = true,
    val sense_notifications: Boolean = true,
    val sense_usage: Boolean = true,
    val read_apps: Boolean = true,
    val read_system: Boolean = true,
    val write_disable: Boolean = false,
    val write_appops: Boolean = false,
    val write_force_stop: Boolean = false,
    val write_uninstall: Boolean = false
)

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
 * - [whitelist]：允许写入操作的包名白名单
 * - [toolSwitches]：各工具开关
 * - [audit]：审计记录，最多保留 500 条（FIFO）
 */
@Serializable
data class AgentSettings(
    val version: Int = 1,
    val whitelist: List<String> = emptyList(),
    val toolSwitches: AgentToolSwitches = AgentToolSwitches(),
    val audit: List<AgentAuditEntry> = emptyList()
)

/**
 * Agent 设置本地存储：agent-settings.json（AtomicFile 原子写）。
 *
 * 所有可测逻辑收敛到 companion 纯函数（[applySwitch] / [cappedAudit] /
 * [whitelistWith] / [whitelistWithout] / [encode] / [decode]），
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
                json.decodeFromString(AgentSettings.serializer(), text)
            }.getOrElse {
                android.util.Log.w("AgentStore", "解析 agent-settings.json 失败", it)
                AgentSettings()
            }
        }

        /** 应用工具开关；未知开关名返回 null。 */
        fun applySwitch(switches: AgentToolSwitches, name: String, enabled: Boolean): AgentToolSwitches? =
            when (name) {
                "sense_screen" -> switches.copy(sense_screen = enabled)
                "sense_notifications" -> switches.copy(sense_notifications = enabled)
                "sense_usage" -> switches.copy(sense_usage = enabled)
                "read_apps" -> switches.copy(read_apps = enabled)
                "read_system" -> switches.copy(read_system = enabled)
                "write_disable" -> switches.copy(write_disable = enabled)
                "write_appops" -> switches.copy(write_appops = enabled)
                "write_force_stop" -> switches.copy(write_force_stop = enabled)
                "write_uninstall" -> switches.copy(write_uninstall = enabled)
                else -> null
            }

        /** 追加审计条目，超限时按 FIFO 丢弃最旧记录。 */
        fun cappedAudit(current: List<AgentAuditEntry>, entry: AgentAuditEntry): List<AgentAuditEntry> {
            val next = current + entry
            return if (next.size > AUDIT_LIMIT) next.takeLast(AUDIT_LIMIT) else next
        }

        fun clearedAudit(): List<AgentAuditEntry> = emptyList()

        /** 追加白名单（去重，保持顺序）。 */
        fun whitelistWith(current: List<String>, pkg: String): List<String> =
            if (pkg in current) current else current + pkg

        /** 从白名单移除包名。 */
        fun whitelistWithout(current: List<String>, pkg: String): List<String> =
            current.filterNot { it == pkg }
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

    suspend fun addWhitelist(pkg: String) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val current = _settings.value
            val next = current.copy(whitelist = whitelistWith(current.whitelist, pkg))
            writePrefs(next)
            _settings.value = next
        }
    }

    suspend fun removeWhitelist(pkg: String) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val current = _settings.value
            val next = current.copy(whitelist = whitelistWithout(current.whitelist, pkg))
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

    suspend fun clearAudit() = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val current = _settings.value
            val next = current.copy(audit = clearedAudit())
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
