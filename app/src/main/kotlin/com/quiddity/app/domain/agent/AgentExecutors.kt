package com.quiddity.app.domain.agent

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.TrafficStats
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
        if (!com.quiddity.app.active.ScreenReaderService.isConnected) {
            return "读取屏幕需要先开启无障碍服务（屏幕读取权限），请在 Agent 设置 → 权限状态中开启"
        }
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

    fun appPermissions(pkg: String): String {
        val info = packageInfo(pkg, PackageManager.GET_PERMISSIONS) ?: return "未找到应用 $pkg"
        val flags = info.requestedPermissionsFlags ?: IntArray(0)
        val perms = info.requestedPermissions ?: emptyArray()
        val entries = perms.mapIndexedNotNull { index, name ->
            if (name.isBlank()) null
            else name to ((flags.getOrNull(index) ?: 0) and REQUESTED_PERMISSION_GRANTED_FLAG != 0)
        }
        if (entries.isEmpty()) return "$pkg 未声明任何权限"
        return "$pkg 权限清单（${entries.count { it.second }}/${entries.size} 已授予）：\n" +
            formatPermissions(entries)
    }

    fun appInstallInfo(pkg: String): String {
        val info = packageInfo(pkg, 0) ?: return "未找到应用 $pkg"
        val installer = if (Build.VERSION.SDK_INT >= 30) {
            runCatching { context.packageManager.getInstallSourceInfo(pkg).installingPackageName }
                .getOrNull()
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(pkg)
        }
        return "$pkg 安装信息：\n" + formatInstallInfo(info.firstInstallTime, info.lastUpdateTime, installer)
    }

    suspend fun appBattery(pkg: String): String {
        val shell = shizuku ?: return "未获得 Shizuku 授权，无法读取耗电统计"
        if (!shell.isGranted()) return "未获得 Shizuku 授权，无法读取耗电统计"
        val result = shell.exec(arrayOf("dumpsys", "batterystats", "--package", pkg))
        if (result.exitCode != 0) {
            return "读取耗电统计失败（退出码 ${result.exitCode}）：${result.output.ifBlank { "请检查包名" }}"
        }
        val lines = parseBatteryBlock(result.output)
        return if (lines.isEmpty()) {
            "未找到 $pkg 的耗电统计（系统统计可能未启用，或该应用暂无后台耗电记录）"
        } else {
            "$pkg 耗电统计：\n" + lines.joinToString("\n")
        }
    }

    suspend fun systemLogs(maxLines: Int, filter: String?): String {
        val shell = shizuku ?: return "未获得 Shizuku 授权，无法读取全局日志"
        if (!shell.isGranted()) return "未获得 Shizuku 授权，无法读取全局日志"
        val limit = maxLines.coerceIn(10, 2000)
        val result = shell.exec(arrayOf("logcat", "-d", "-t", limit.toString()))
        if (result.exitCode != 0) {
            return "读取全局日志失败（退出码 ${result.exitCode}）：${result.output.ifBlank { "请检查 Shizuku 授权" }}"
        }
        val lines = result.output.lineSequence().toList()
        val filtered = filter?.trim()?.takeIf { it.isNotEmpty() }?.let { q ->
            lines.filter { it.contains(q, ignoreCase = true) }
        } ?: lines
        if (filtered.isEmpty()) return "未找到匹配的日志"
        return formatSystemLogs(filtered.takeLast(limit), limit)
    }

    fun trafficRanking(limit: Int): String {
        if (!hasUsageAccess()) {
            return "未获得「使用情况访问」权限：请在系统设置 → 应用 → 特殊应用权限中开启"
        }
        val apps = runCatching {
            val pm = context.packageManager
            val infos = if (Build.VERSION.SDK_INT >= 33) {
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledApplications(0)
            }
            infos.map { info -> info.packageName to info.uid }
        }.getOrDefault(emptyList())
        val entries = apps.mapNotNull { (pkg, uid) ->
            val rx = TrafficStats.getUidRxBytes(uid)
            val tx = TrafficStats.getUidTxBytes(uid)
            val rxBytes = rx.takeIf { it > 0 } ?: 0L
            val txBytes = tx.takeIf { it > 0 } ?: 0L
            if (rxBytes + txBytes <= 0L) null else Triple(pkg, rxBytes, txBytes)
        }
        if (entries.isEmpty()) return "未获取到流量统计（设备可能不支持按应用统计）"
        return formatTraffic(entries, limit.coerceIn(1, 50))
    }

    fun fileAccess(pkg: String): String {
        val info = packageInfo(pkg, PackageManager.GET_PERMISSIONS) ?: return "未找到应用 $pkg"
        val flags = info.requestedPermissionsFlags ?: IntArray(0)
        val perms = info.requestedPermissions ?: emptyArray()
        val entries = perms.mapIndexedNotNull { index, name ->
            if (name in FILE_ACCESS_PERMISSIONS) {
                name to ((flags.getOrNull(index) ?: 0) and REQUESTED_PERMISSION_GRANTED_FLAG != 0)
            } else {
                null
            }
        }
        if (entries.isEmpty()) return "$pkg 未声明文件/存储相关权限"
        val granted = entries.count { it.second }
        val summary = buildString {
            append("文件访问能力：已授予 $granted/${entries.size} 项")
            if (entries.any { it.first == "android.permission.MANAGE_EXTERNAL_STORAGE" && it.second }) {
                append("（可管理全部文件）")
            }
        }
        return "$pkg $summary：\n" + formatPermissions(entries)
    }

    suspend fun screenshot(): String =
        com.quiddity.app.active.ScreenReaderService.captureScreenshot(context)

    private fun packageInfo(pkg: String, flags: Int): android.content.pm.PackageInfo? =
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    pkg,
                    PackageManager.PackageInfoFlags.of(flags.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(pkg, flags)
            }
        }.getOrNull()

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

        /** PackageInfo.requestedPermissionsFlags：该权限已授予（API 23+ 隐藏常量值）。 */
        private const val REQUESTED_PERMISSION_GRANTED_FLAG = 0x00000002

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

        private val FILE_ACCESS_PERMISSIONS = setOf(
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
            "android.permission.ACCESS_MEDIA_LOCATION"
        )

        private val BATTERY_KEYWORDS = listOf(
            "power:", "cpu:", "wake_lock:", "wifi:", "mobile:", "gps:",
            "radio:", "sensor:", "wakeup:", "bluetooth:"
        )

        /** 权限条目格式化：权限简称：已授予/未授予。 */
        fun formatPermissions(entries: List<Pair<String, Boolean>>): String =
            entries.joinToString("\n") { (name, granted) ->
                val short = name.removePrefix("android.permission.")
                "$short：${if (granted) "已授予" else "未授予"}"
            }

        /** 安装信息格式化：首次安装 / 最近更新 / 安装来源。 */
        fun formatInstallInfo(installedAt: Long, updatedAt: Long, installer: String?): String = buildString {
            append("首次安装：").append(formatEpoch(installedAt))
            append("\n最近更新：").append(formatEpoch(updatedAt))
            append("\n安装来源：").append(
                installer?.takeIf { it.isNotBlank() }
                    ?: "未知（可能为系统预装或 adb 安装）"
            )
        }

        /** 流量条目格式化：按合计流量倒序，输出接收/发送/合计。 */
        fun formatTraffic(entries: List<Triple<String, Long, Long>>, limit: Int): String =
            entries
                .sortedByDescending { (_, rx, tx) -> rx + tx }
                .take(limit)
                .joinToString("\n") { (pkg, rx, tx) ->
                    "$pkg：接收 ${formatBytes(rx)} / 发送 ${formatBytes(tx)}（合计 ${formatBytes(rx + tx)}）"
                }

        /** 字节数格式化：B / KB / MB / GB。 */
        fun formatBytes(bytes: Long): String = when {
            bytes >= 1L shl 30 -> String.format(java.util.Locale.US, "%.1f GB", bytes.toDouble() / (1L shl 30))
            bytes >= 1L shl 20 -> String.format(java.util.Locale.US, "%.1f MB", bytes.toDouble() / (1L shl 20))
            bytes >= 1L shl 10 -> String.format(java.util.Locale.US, "%.1f KB", bytes.toDouble() / (1L shl 10))
            else -> "$bytes B"
        }

        /** 日志行格式化：按条数截断，单行超长截断避免刷屏。 */
        fun formatSystemLogs(lines: List<String>, maxLines: Int): String =
            lines.takeLast(maxLines)
                .joinToString("\n") { line -> if (line.length <= 400) line else line.take(400) }

        /** 从 dumpsys batterystats 输出中提取首个 Uid 块的关键耗电行。 */
        fun parseBatteryBlock(output: String): List<String> {
            val result = mutableListOf<String>()
            var seenHeader = false
            output.lineSequence().forEach { line ->
                val trimmed = line.trim()
                if (!seenHeader) {
                    if (trimmed.startsWith("Uid u0a") && trimmed.endsWith(":")) {
                        seenHeader = true
                        result.add(trimmed)
                    }
                    return@forEach
                }
                if (trimmed.startsWith("Uid u0a") && trimmed.endsWith(":")) {
                    result.add(trimmed)
                    return@forEach
                }
                if (line.isBlank()) return@forEach
                if (!line[0].isWhitespace()) return@forEach
                if (result.size >= 16) return@forEach
                if (BATTERY_KEYWORDS.any { trimmed.startsWith(it) }) result.add(trimmed)
            }
            return result
        }

        private fun formatEpoch(ms: Long): String =
            if (ms <= 0L) {
                "未知"
            } else {
                java.text.SimpleDateFormat(
                    "yyyy-MM-dd HH:mm",
                    java.util.Locale.getDefault()
                ).format(java.util.Date(ms))
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
