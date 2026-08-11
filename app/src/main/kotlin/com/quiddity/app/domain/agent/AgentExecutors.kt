package com.quiddity.app.domain.agent

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.os.Build

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
 * 单条通知快照（NotificationBridge 写入，Agent 只读执行器消费）。
 */
data class AgentNotification(
    val packageName: String,
    val title: String,
    val text: String,
    val timestamp: Long
)

/**
 * Agent 运行时传感器状态（进程内共享）：
 * 无障碍服务写入屏幕文本，通知监听服务写入通知快照，执行器只读。
 */
object AgentSensorState {

    @Volatile
    var screenText: String = ""

    private val lock = Any()
    private val items = mutableListOf<AgentNotification>()

    fun updateNotifications(items: List<AgentNotification>) {
        synchronized(lock) {
            this.items.clear()
            this.items.addAll(items)
        }
    }

    fun notifications(): List<AgentNotification> = synchronized(lock) { items.toList() }
}

/**
 * Agent 基础只读执行器（P0）：已安装应用 / 读屏 / 通知 / 用量 / 前台应用。
 *
 * 纯格式化与过滤逻辑收敛到 companion，便于 JVM 单测；
 * 需要系统服务的调用集中在本类，由 ServiceLocator 统一构造。
 */
class AgentExecutors(
    private val context: Context,
    private val shizuku: ShizukuShell? = null
) {

    fun listApps(query: String?): String {
        val apps = runCatching {
            val pm = context.packageManager
            val infos = if (Build.VERSION.SDK_INT >= 33) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
            infos.map { info -> info.packageName to info.loadLabel(pm).toString() }
        }.getOrDefault(emptyList())
        return formatApps(apps, query).ifBlank { "未找到匹配的应用" }
    }

    fun readScreen(maxChars: Int?): String {
        val text = truncate(AgentSensorState.screenText, maxChars)
        return AgentSecurity.wrapUntrustedScreen(text.ifBlank { "当前屏幕无可见文本" })
    }

    fun readNotifications(sinceIso: String?): String {
        val text = formatNotifications(AgentSensorState.notifications(), sinceIso)
        return AgentSecurity.wrapUntrustedNotifications(text.ifBlank { "暂无通知" })
    }

    fun usageStats(days: Int): String {
        if (!hasUsageAccess()) {
            return "未获得「使用情况访问」权限：请在系统设置 → 应用 → 特殊应用权限中开启"
        }
        val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - days.coerceIn(1, 90) * DAY_MS
        val entries = runCatching {
            usage.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
                .mapNotNull { stats ->
                    val total = stats.totalTimeInForeground
                    if (total != null && total > 0L) stats.packageName to total else null
                }
        }.getOrDefault(emptyList())
        return formatUsage(entries).ifBlank { "统计时间内没有前台使用记录" }
    }

    fun foregroundApp(): String {
        if (!hasUsageAccess()) {
            return "未获得「使用情况访问」权限：请在系统设置 → 应用 → 特殊应用权限中开启"
        }
        val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val events = runCatching { usage.queryEvents(end - 30_000, end) }.getOrNull() ?: return "无法读取前台应用"
        var pkg: String? = null
        val event = android.app.usage.UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                pkg = event.packageName
            }
        }
        return pkg?.let { "当前前台应用：$it" } ?: "未检测到前台应用"
    }

    suspend fun disableApp(pkg: String): String =
        writeViaShizuku(disableCommand(pkg), "已停用 $pkg")

    suspend fun enableApp(pkg: String): String =
        writeViaShizuku(enableCommand(pkg), "已启用 $pkg")

    suspend fun setAppOps(pkg: String, op: String, mode: String): String =
        writeViaShizuku(appOpsCommand(pkg, op, mode), "已设置 $pkg 的 $op 为 $mode")

    suspend fun forceStop(pkg: String): String =
        writeViaShizuku(forceStopCommand(pkg), "已强制停止 $pkg")

    suspend fun uninstallApp(pkg: String): String =
        writeViaShizuku(uninstallCommand(pkg), "已卸载 $pkg")

    private suspend fun writeViaShizuku(command: Array<String>, success: String): String {
        val shell = shizuku ?: return "未获得 Shizuku 授权，无法执行写入操作"
        if (!shell.isGranted()) return "未获得 Shizuku 授权，无法执行写入操作"
        return formatShellResult(shell.exec(command), success)
    }

    private fun hasUsageAccess(): Boolean = runCatching {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    companion object {

        private const val DAY_MS = 86_400_000L

        /** 按 query 大小写不敏感过滤（包名/标签），按标签排序输出「包名：标签」。 */
        fun formatApps(apps: List<Pair<String, String>>, query: String?): String {
            val q = query?.trim().orEmpty()
            return apps
                .filter { (pkg, label) ->
                    q.isEmpty() || pkg.contains(q, ignoreCase = true) || label.contains(q, ignoreCase = true)
                }
                .sortedBy { (_, label) -> label }
                .joinToString("\n") { (pkg, label) -> "$pkg：$label" }
        }

        /** 按 since（ISO 时间）过滤、按时间倒序，输出「包名 · 标题：文本」。 */
        fun formatNotifications(items: List<AgentNotification>, sinceIso: String?): String {
            val since = runCatching {
                sinceIso?.trim()?.takeIf { it.isNotEmpty() }?.let { java.time.Instant.parse(it) }
            }.getOrNull()
            return items
                .filter { since == null || java.time.Instant.ofEpochMilli(it.timestamp) >= since }
                .sortedByDescending { it.timestamp }
                .joinToString("\n") { n ->
                    val title = n.title.ifBlank { "(无标题)" }
                    val body = n.text.ifBlank { "" }
                    if (body.isEmpty()) "$title（${n.packageName}）" else "$title：$body（${n.packageName}）"
                }
        }

        /** 按前台时长倒序，输出「包名：X 分钟 Y 秒」。 */
        fun formatUsage(entries: List<Pair<String, Long>>): String =
            entries
                .sortedByDescending { it.second }
                .joinToString("\n") { (pkg, ms) ->
                    val minutes = ms / 60_000
                    val seconds = (ms % 60_000) / 1_000
                    if (minutes > 0) "$pkg：$minutes 分钟 $seconds 秒" else "$pkg：$seconds 秒"
                }

        /** 超长文本截断；maxChars 为 null 或超过文本长度时原样返回。 */
        fun truncate(text: String, maxChars: Int?): String {
            val limit = maxChars?.takeIf { it > 0 } ?: return text
            return if (text.length <= limit) text else text.take(limit)
        }

        /** 固定命令形状：pm disable-user（不传可能误伤系统应用）。 */
        fun disableCommand(pkg: String): Array<String> =
            arrayOf("pm", "disable-user", "--user", "0", pkg)

        fun enableCommand(pkg: String): Array<String> =
            arrayOf("pm", "enable", pkg)

        fun appOpsCommand(pkg: String, op: String, mode: String): Array<String> =
            arrayOf("cmd", "appops", "set", pkg, op, mode)

        fun forceStopCommand(pkg: String): Array<String> =
            arrayOf("am", "force-stop", pkg)

        fun uninstallCommand(pkg: String): Array<String> =
            arrayOf("pm", "uninstall", pkg)

        /** 命令结果格式化：退出码 0 视为成功，否则附上错误输出。 */
        fun formatShellResult(result: AgentShellResult, success: String): String =
            if (result.exitCode == 0) {
                val detail = result.output.takeIf { it.isNotBlank() }
                if (detail == null) success else "$success：$detail"
            } else {
                "执行失败（退出码 ${result.exitCode}）：${result.output.ifBlank { "请检查包名与权限" }}"
            }
    }
}
