package com.quiddity.app.domain.agent

import android.app.AppOpsManager
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.TrafficStats
import android.os.Process
import android.os.Build
import androidx.core.app.NotificationCompat
import com.quiddity.app.MainActivity
import com.quiddity.app.R
import com.quiddity.app.active.NotificationChannels
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.repo.SettingsRepository
import com.quiddity.app.domain.ApiCatalogManager
import com.quiddity.app.domain.VisionOcrService
import java.io.File
import java.io.FileOutputStream

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
 * 单条 Toast 快照（无障碍服务捕获，Agent 只读执行器消费）。
 */
data class AgentToast(
    val packageName: String,
    val text: String,
    val timestamp: Long
)

/**
 * 单条通知变化事件（NotificationBridge 写入，通知守卫订阅模式消费）。
 * [type] 为 "posted"（新通知）或 "removed"（通知消失）。
 */
data class AgentNotificationEvent(
    val packageName: String,
    val title: String,
    val text: String,
    val timestamp: Long,
    val type: String
)

/**
 * 单条应用用量明细（app_usage_detail 输出项）。
 */
data class AgentUsageDetail(
    val packageName: String,
    val totalMs: Long,
    val launches: Int,
    val lastUsed: Long
)

/**
 * Agent 运行时传感器状态（进程内共享）：
 * 无障碍服务写入屏幕文本与 Toast，通知监听服务写入通知快照与变化事件，执行器只读。
 */
object AgentSensorState {

    @Volatile
    var screenText: String = ""

    private val lock = Any()
    private val items = mutableListOf<AgentNotification>()

    private val toastLock = Any()
    private val toastItems = mutableListOf<AgentToast>()

    private val eventLock = Any()
    private val eventItems = mutableListOf<AgentNotificationEvent>()

    fun updateNotifications(items: List<AgentNotification>) {
        synchronized(lock) {
            this.items.clear()
            this.items.addAll(items)
        }
    }

    fun notifications(): List<AgentNotification> = synchronized(lock) { items.toList() }

    /** 追加 Toast（保留最近 [MAX_TOASTS] 条，FIFO）。 */
    fun appendToast(item: AgentToast) {
        synchronized(toastLock) {
            toastItems.add(item)
            while (toastItems.size > MAX_TOASTS) toastItems.removeAt(0)
        }
    }

    fun toasts(): List<AgentToast> = synchronized(toastLock) { toastItems.toList() }

    /** 追加通知变化事件（保留最近 [MAX_NOTIFICATION_EVENTS] 条，FIFO）。 */
    fun appendNotificationEvent(event: AgentNotificationEvent) {
        synchronized(eventLock) {
            eventItems.add(event)
            while (eventItems.size > MAX_NOTIFICATION_EVENTS) eventItems.removeAt(0)
        }
    }

    fun notificationEvents(): List<AgentNotificationEvent> =
        synchronized(eventLock) { eventItems.toList() }

    private const val MAX_TOASTS = 50
    private const val MAX_NOTIFICATION_EVENTS = 200
}

/**
 * Agent 基础只读执行器（P0）：已安装应用 / 读屏 / 通知 / 用量 / 前台应用。
 *
 * 纯格式化与过滤逻辑收敛到 companion，便于 JVM 单测；
 * 需要系统服务的调用集中在本类，由 ServiceLocator 统一构造。
 */
class AgentExecutors(
    private val context: Context,
    private val shizuku: ShizukuShell? = null,
    private val visionOcrService: VisionOcrService? = null,
    private val settingsRepository: SettingsRepository? = null,
    private val apiCatalogManager: ApiCatalogManager? = null
) {

    /** 本次进程内是否已自动发起过 Shizuku 授权申请（拒绝后不再重复弹窗）。 */
    @Volatile
    private var shizukuRequestedThisSession = false

    /**
     * Shizuku 就绪检查：未授权时自动拉起一次系统授权弹窗（进程内仅一次），
     * 未安装 / 未运行时返回可操作的指引文案。
     *
     * @return null 表示可用；否则返回失败原因（调用方直接回传给模型）。
     */
    private suspend fun requireShizuku(): String? {
        val shell = shizuku ?: return "未获得 Shizuku 授权：请先安装并启动 Shizuku（Agent 设置 → 权限状态 → Shizuku）"
        if (shell.isGranted()) return null
        if (!shizukuRequestedThisSession) {
            shizukuRequestedThisSession = true
            if (shell.requestPermissionAndWait()) return null
        }
        return "未获得 Shizuku 授权：请在弹出的授权窗口中允许；若没有弹窗，请在 Agent 设置 → 权限状态 → Shizuku 中启动并授权后重试"
    }

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

    suspend fun readScreen(maxChars: Int?): String {
        if (!com.quiddity.app.active.ScreenReaderService.isConnected) {
            return "读取屏幕需要先开启无障碍服务（屏幕读取权限），请在 Agent 设置 → 权限状态中开启"
        }
        // 主动采集一次当前屏幕（不依赖窗口变化事件），避免界面静止时读到空内容
        val text = truncate(
            com.quiddity.app.active.ScreenReaderService.refreshAndReadScreenText(),
            maxChars
        )
        return AgentSecurity.wrapUntrustedScreen(text.ifBlank { "当前屏幕无可见文本" })
    }

    /** 读取系统当前时间：日期时间 + 时区 + 时间戳（供 Agent 感知时间）。 */
    fun getTime(): String {
        val now = java.time.ZonedDateTime.now()
        val formatter = java.time.format.DateTimeFormatter.ofPattern(
            "yyyy-MM-dd HH:mm:ss EEE"
        )
        return buildString {
            append("当前时间：").append(now.format(formatter))
            append("\n时区：").append(now.zone.id)
            append("\n时间戳（毫秒）：").append(now.toInstant().toEpochMilli())
        }
    }

    /** 定时等待：挂起 [seconds] 秒（1~300），用于等界面加载、动画结束或定时衔接。 */
    suspend fun sleep(seconds: Int): String {
        val value = seconds.coerceIn(1, 300)
        kotlinx.coroutines.delay(value * 1_000L)
        return "已等待 $value 秒"
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
        requireShizuku()?.let { return it }
        val shell = shizuku ?: return "未获得 Shizuku 授权，无法读取耗电统计"
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
        requireShizuku()?.let { return it }
        val shell = shizuku ?: return "未获得 Shizuku 授权，无法读取全局日志"
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

    /** 读取指定应用的实时日志（logcat --pid，需要 Shizuku 授权）。 */
    suspend fun appLogs(pkg: String, maxLines: Int?, filter: String?): String {
        requireShizuku()?.let { return it }
        val shell = shizuku ?: return "未获得 Shizuku 授权，无法读取应用日志"
        val limit = (maxLines ?: 200).coerceIn(10, 2000)
        val pidResult = shell.exec(arrayOf("pidof", pkg))
        val pid = pidResult.output.trim()
            .split(Regex("\\s+"))
            .firstOrNull { it.isNotBlank() && it.all(Char::isDigit) }
        if (pid == null) {
            return "应用 $pkg 当前没有运行进程，无法读取实时日志。请先打开该应用再试，或使用「全局日志」查看历史记录。"
        }
        val logResult = shell.exec(arrayOf("logcat", "-d", "-t", limit.toString(), "--pid=$pid"))
        if (logResult.exitCode != 0) {
            return "读取应用日志失败（退出码 ${logResult.exitCode}）：${logResult.output.ifBlank { "请检查 Shizuku 授权" }}"
        }
        val lines = logResult.output.lineSequence().toList()
        val filtered = filter?.trim()?.takeIf { it.isNotEmpty() }?.let { q ->
            lines.filter { it.contains(q, ignoreCase = true) }
        } ?: lines
        if (filtered.isEmpty()) return "未找到 $pkg 的匹配日志"
        return formatSystemLogs(filtered.takeLast(limit), limit)
    }

    /** 移动文件 / 目录（需要 Shizuku 授权，命令数组直传不经过 shell 解析）。 */
    suspend fun moveFile(src: String, dst: String): String =
        writeViaShizuku(arrayOf("mv", src, dst), "文件已移动：$src → $dst")

    /** 复制文件 / 目录（需要 Shizuku 授权，命令数组直传不经过 shell 解析）。 */
    suspend fun copyFile(src: String, dst: String): String =
        writeViaShizuku(arrayOf("cp", "-r", src, dst), "文件已复制：$src → $dst")

    /** 删除文件 / 目录（需要 Shizuku 授权，命令数组直传不经过 shell 解析）。 */
    suspend fun deleteFile(path: String): String =
        writeViaShizuku(arrayOf("rm", "-rf", path), "已删除：$path")

    /** 创建空文件（需要 Shizuku 授权）。 */
    suspend fun createFile(path: String): String =
        writeViaShizuku(arrayOf("touch", path), "已创建空文件：$path")

    /** 写入文本内容（覆盖原内容，需要 Shizuku 授权）。 */
    suspend fun writeFile(path: String, content: String): String =
        writeTextViaShizuku(path, content, append = false)

    /** 追加文本内容到文件末尾（需要 Shizuku 授权）。 */
    suspend fun appendFile(path: String, content: String): String =
        writeTextViaShizuku(path, content, append = true)

    /** 重命名文件 / 目录（需要 Shizuku 授权）。 */
    suspend fun renameFile(src: String, dst: String): String =
        writeViaShizuku(arrayOf("mv", src, dst), "已重命名：$src → $dst")

    /** 创建目录（含父目录，需要 Shizuku 授权）。 */
    suspend fun mkdir(path: String): String =
        writeViaShizuku(arrayOf("mkdir", "-p", path), "已创建目录：$path")

    /** 列出目录内容（需要 Shizuku 授权）。 */
    suspend fun listFiles(path: String): String {
        requireShizuku()?.let { return it }
        val shell = shizuku ?: return "未获得 Shizuku 授权，无法列出目录"
        val result = shell.exec(arrayOf("ls", "-la", path))
        if (result.exitCode != 0) {
            return "列出目录失败（退出码 ${result.exitCode}）：${result.output.ifBlank { "请检查路径与权限" }}"
        }
        val lines = result.output.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return "目录 $path 为空"
        return "「$path」目录清单：\n" + lines.joinToString("\n")
    }

    /** 查询文件 / 目录元信息（大小 / 修改时间，需要 Shizuku 授权）。 */
    suspend fun fileInfo(path: String): String {
        requireShizuku()?.let { return it }
        val shell = shizuku ?: return "未获得 Shizuku 授权，无法查询文件信息"
        val result = shell.exec(arrayOf("ls", "-ld", path))
        if (result.exitCode != 0) {
            return "查询文件信息失败（退出码 ${result.exitCode}）：${result.output.ifBlank { "请检查路径与权限" }}"
        }
        val info = formatFileInfo(result.output, path)
        return if (info == null) {
            "未解析到 $path 的元信息：\n${result.output}"
        } else {
            "$path：\n$info"
        }
    }

    /** 读取文件文本内容（需要 Shizuku 授权）。 */
    suspend fun readFile(path: String, maxChars: Int?): String {
        requireShizuku()?.let { return it }
        val shell = shizuku ?: return "未获得 Shizuku 授权，无法读取文件"
        val result = shell.exec(arrayOf("cat", path))
        if (result.exitCode != 0) {
            return "读取文件失败（退出码 ${result.exitCode}）：${result.output.ifBlank { "请检查路径与权限" }}"
        }
        if (result.output.any { it.code == 0 }) {
            return "该文件为二进制文件，不支持文本读取（请用 file_info 查看元信息）"
        }
        val limit = (maxChars ?: 2000).coerceIn(200, 8000)
        val text = result.output
        val shown = truncate(text, limit)
        return if (text.length > limit) "$shown\n（内容过长，已截断，共 ${text.length} 字符）" else shown
    }

    /**
     * 跳转文件：用文件管理器打开指定路径定位（需要 Shizuku 授权）。
     * [pkg] 非空时优先解析该包名对应的 VIEW 处理组件再启动，
     * 避免微信等应用抢走文件打开意图；解析失败回退系统默认。
     */
    suspend fun revealFile(path: String, pkg: String?): String {
        requireShizuku()?.let { return it }
        val shell = shizuku ?: return "未获得 Shizuku 授权，无法跳转文件"
        val target = pkg?.trim()?.takeIf { it.isNotEmpty() }
        if (target != null) {
            val resolve = shell.exec(
                arrayOf(
                    "cmd", "package", "resolve-activity", "--brief",
                    "-a", "android.intent.action.VIEW",
                    "-d", "file://$path",
                    "-t", "*/*"
                )
            )
            val component = parseResolvedComponent(resolve.output, target)
            if (component != null) {
                val started = shell.exec(
                    arrayOf(
                        "am", "start",
                        "-a", "android.intent.action.VIEW",
                        "-d", "file://$path",
                        "-t", "*/*",
                        "-n", component
                    )
                )
                return if (started.exitCode == 0) {
                    "已用 $target 打开文件管理器定位：$path"
                } else {
                    "启动 $target 失败（退出码 ${started.exitCode}）：${started.output.ifBlank { "请检查包名是否已安装" }}"
                }
            }
            val fallback = startReveal(shell, path)
            return if (fallback) {
                "$target 无法直接处理该文件，已回退系统默认应用定位：$path"
            } else {
                "未找到 $target 可处理该文件的组件，且系统默认打开失败"
            }
        }
        val result = shell.exec(
            arrayOf(
                "am", "start",
                "-a", "android.intent.action.VIEW",
                "-d", "file://$path",
                "-t", "*/*"
            )
        )
        return if (result.exitCode == 0) {
            "已打开文件管理器定位：$path"
        } else {
            "跳转文件失败（退出码 ${result.exitCode}）：${result.output.ifBlank { "没有可处理该文件的应用" }}"
        }
    }

    private suspend fun startReveal(shell: ShizukuShell, path: String): Boolean {
        val result = shell.exec(
            arrayOf(
                "am", "start",
                "-a", "android.intent.action.VIEW",
                "-d", "file://$path",
                "-t", "*/*"
            )
        )
        return result.exitCode == 0
    }

    /**
     * 对图片执行 OCR 识别（调用应用内置视觉识图引擎）。
     * 直读失败时经 Shizuku base64 拷贝到应用缓存后再识别，兼容 /sdcard 等受限路径。
     */
    suspend fun ocrImage(path: String, conversation: Conversation?): String {
        val ocr = visionOcrService ?: return "未接入内置识图引擎，请检查视觉 OCR 配置"
        val settings = settingsRepository?.currentSnapshot()
            ?: return "无法读取应用设置，请稍后重试"
        val resolvedPath = if (path.isBlank()) {
            val shot = screenshot()
            val marker = "已保存截图："
            if (shot.startsWith(marker)) {
                shot.removePrefix(marker).substringBefore(" ").trim()
            } else {
                return shot
            }
        } else {
            path
        }
        val uri = runCatching { Uri.fromFile(File(resolvedPath)) }.getOrNull()
            ?: return "图片路径无效：$resolvedPath"
        val chatEntry = apiCatalogManager?.let { manager ->
            conversation?.let { manager.resolveEntry(settings, it) }
        }
        val effectiveUri = makeImageReadable(uri, resolvedPath)
        val result = ocr.recognizeImage(context, effectiveUri, settings, chatEntry)
        return if (result.isSuccess) {
            "识别结果：\n${result.getOrThrow()}"
        } else {
            "识别失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
        }
    }

    /** 主动推送通知栏提醒（标题可选，正文必填）。 */
    fun notifySelf(title: String?, text: String): String =
        runCatching {
            val channelId = "agent_notify"
            NotificationChannels.ensure(
                context,
                channelId,
                "Agent 提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pending = PendingIntent.getActivity(
                context,
                NOTIFY_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title?.takeIf { it.isNotBlank() } ?: "Agent 提醒")
                .setContentText(text.replace("\n", " ").trim().let {
                    if (it.length > 80) it.take(80) + "…" else it
                })
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            manager.notify(NOTIFY_ID, notification)
            "已推送提醒"
        }.getOrElse { "推送提醒失败：${it.message ?: "未知错误"}" }

    /** 读取剪贴板文本（前台直读，失败时经 Shizuku cmd clipboard 兜底）。 */
    suspend fun readClipboard(): String {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val direct = runCatching {
            manager.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(context)
                ?.toString()
        }.getOrNull()
        if (!direct.isNullOrBlank()) return "剪贴板内容：\n$direct"
        val shell = shizuku
        if (shell == null || !shell.isGranted()) return "剪贴板当前为空，且无法通过 Shizuku 兜底读取"
        val result = shell.exec(arrayOf("cmd", "clipboard", "get-primary-clip"))
        val text = result.output.trim().removePrefix("text=").takeIf { it.isNotBlank() }
        return if (text == null) {
            "剪贴板当前为空"
        } else {
            "剪贴板内容：\n$text"
        }
    }

    /** 写入剪贴板文本。 */
    fun writeClipboard(text: String): String =
        runCatching {
            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            manager.setPrimaryClip(ClipData.newPlainText("agent", text))
            "已写入剪贴板"
        }.getOrElse { "写入剪贴板失败：${it.message ?: "未知错误"}" }

    /** 读取最近捕获的 Toast（需要无障碍服务；since 为空时返回最近 [maxItems] 条）。 */
    fun toastMonitor(since: String?, maxItems: Int?): String {
        if (!com.quiddity.app.active.ScreenReaderService.isConnected) {
            return "Toast 监听需要先开启无障碍服务（屏幕读取权限）"
        }
        val sinceMs = parseIso(since)
        val items = AgentSensorState.toasts()
            .filter { sinceMs == null || it.timestamp > sinceMs }
            .sortedByDescending { it.timestamp }
            .take((maxItems ?: 10).coerceIn(1, 50))
        val text = formatToasts(items)
        return if (text.isBlank()) "暂无捕获的 Toast" else text
    }

    /**
     * 通知变化订阅：返回自上次调用以来的新通知/消失通知。
     * [since] 非空时以该时间戳为基准并推进游标；为空时使用上次调用后的游标。
     */
    fun notificationGuard(since: String?): String {
        if (!com.quiddity.app.active.NotificationBridge.isConnected) {
            return "通知守卫需要先开启通知使用权（在 Agent 设置 → 权限状态中开启）"
        }
        val base = parseIso(since) ?: lastGuardCursor
        val events = AgentSensorState.notificationEvents()
            .filter { it.timestamp > base }
            .sortedByDescending { it.timestamp }
        events.maxOfOrNull { it.timestamp }?.let { lastGuardCursor = it }
        val text = formatNotificationEvents(events)
        return if (text.isBlank()) "暂无新通知变化" else text
    }

    /** 执行白名单安全命令（命令名已由安全层校验，参数数组直传不经过 shell 解析）。 */
    suspend fun runShell(command: String, args: List<String>?): String {
        requireShizuku()?.let { return it }
        val shell = shizuku ?: return "未获得 Shizuku 授权，无法执行 Shell 命令"
        val full = buildList {
            add(command)
            args.orEmpty().forEach { add(it) }
        }
        val result = shell.exec(full.toTypedArray())
        val output = result.output.take(MAX_SHELL_RESULT_CHARS)
        return if (result.exitCode == 0) {
            if (output.isBlank()) "命令执行成功（无输出）" else output
        } else {
            "命令执行失败（退出码 ${result.exitCode}）：${output.ifBlank { "请检查参数" }}"
        }
    }

    /** 更细粒度应用使用统计：前台时长 / 启动次数 / 最后使用时间。 */
    fun appUsageDetail(days: Int): String {
        if (!hasUsageAccess()) {
            return "未获得「使用情况访问」权限：请在系统设置 → 应用 → 特殊应用权限中开启"
        }
        val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val start = end - days.coerceIn(1, 30) * DAY_MS
        val stats = runCatching {
            usage.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
        }.getOrDefault(emptyList())
        val launches = mutableMapOf<String, Int>()
        runCatching {
            val events = usage.queryEvents(start, end)
            val event = android.app.usage.UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED ||
                    event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND
                ) {
                    launches[event.packageName] = (launches[event.packageName] ?: 0) + 1
                }
            }
        }
        val entries = stats.mapNotNull { s ->
            val total = s.totalTimeInForeground
            if (total != null && total > 0L) {
                AgentUsageDetail(
                    packageName = s.packageName,
                    totalMs = total,
                    launches = launches[s.packageName] ?: 0,
                    lastUsed = s.lastTimeUsed
                )
            } else {
                null
            }
        }
        val daily = stats
            .filter { (it.totalTimeInForeground ?: 0L) > 0L }
            .groupBy { formatDay(it.firstTimeStamp) }
            .mapValues { (_, list) -> list.sumOf { it.totalTimeInForeground ?: 0L } }
            .toList()
        val text = buildString {
            append(formatUsageDetail(entries))
            if (daily.isNotEmpty()) {
                append("\n\n按日合计：\n")
                append(formatDailyUsage(daily))
            }
        }
        return if (text.isBlank()) "统计时间内没有前台使用记录" else text
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

    /** 模拟点击指定屏幕坐标（像素）。 */
    suspend fun click(x: Int, y: Int): String =
        com.quiddity.app.active.ScreenReaderService.performTap(x.toFloat(), y.toFloat(), 100L)

    /** 长按指定屏幕坐标（像素）。 */
    suspend fun longPress(x: Int, y: Int): String =
        com.quiddity.app.active.ScreenReaderService.performLongPress(x.toFloat(), y.toFloat())

    /** 点击屏幕上包含指定文字的控件。 */
    suspend fun clickText(text: String): String =
        com.quiddity.app.active.ScreenReaderService.performClickByText(text)

    /** 按方向滑动屏幕（up / down / left / right）。 */
    suspend fun scroll(direction: String, distance: Int): String =
        com.quiddity.app.active.ScreenReaderService.performScroll(direction, distance.toFloat())

    /** 执行系统全局动作（返回 / 首页 / 最近任务 / 通知栏 / 快捷设置）。 */
    suspend fun globalAction(action: String): String =
        com.quiddity.app.active.ScreenReaderService.performGlobalAction(action)

    /** 打开指定应用（按包名启动其主 Activity）。 */
    fun openApp(pkg: String): String {
        if (pkg.isBlank()) return "缺少应用包名"
        val intent = runCatching {
            context.packageManager.getLaunchIntentForPackage(pkg)
        }.getOrNull() ?: return "未找到应用 $pkg（包名不存在或未安装）"
        return runCatching {
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            "已打开应用 $pkg"
        }.getOrElse { "打开应用失败：${it.message ?: "未知错误"}" }
    }

    /** 向当前聚焦输入框写入文本。 */
    suspend fun inputText(text: String): String =
        com.quiddity.app.active.ScreenReaderService.performInputText(text)

    /** 按 resource-id 点击控件。 */
    suspend fun clickId(id: String): String =
        com.quiddity.app.active.ScreenReaderService.performClickBy(id = id, desc = null)

    /** 按 contentDescription 点击控件。 */
    suspend fun clickDesc(desc: String): String =
        com.quiddity.app.active.ScreenReaderService.performClickBy(id = null, desc = desc)

    /** 在两点之间拖拽。 */
    suspend fun drag(x1: Int, y1: Int, x2: Int, y2: Int): String =
        com.quiddity.app.active.ScreenReaderService.performDrag(
            x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat()
        )

    /** 向下滚动查找并点击目标文字。 */
    suspend fun scrollToText(text: String, maxScrolls: Int): String =
        com.quiddity.app.active.ScreenReaderService.performScrollToText(text, maxScrolls)

    /** 清空剪贴板。 */
    fun clearClipboard(): String =
        runCatching {
            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (Build.VERSION.SDK_INT >= 28) {
                manager.clearPrimaryClip()
            } else {
                manager.setPrimaryClip(ClipData.newPlainText("", ""))
            }
            "已清空剪贴板"
        }.getOrElse { "清空剪贴板失败：${it.message ?: "未知错误"}" }

    /** 清除指定通知（pkg + 可选 id）。 */
    fun dismissNotification(pkg: String, id: Int?): String =
        com.quiddity.app.active.NotificationBridge.cancelNotification(pkg, id)

    /** 回复指定通知（优先内联回复，不支持则点击打开）。 */
    fun replyNotification(pkg: String, id: Int?, reply: String): String =
        com.quiddity.app.active.NotificationBridge.replyNotification(pkg, id, reply)

    /** 定时提醒：delaySeconds 秒后推送通知栏提醒（秒级，App 进程被系统回收也可触发）。 */
    fun scheduleNotify(title: String?, text: String, delaySeconds: Int): String =
        runCatching {
            val seconds = delaySeconds.coerceIn(1, 86_400)
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, com.quiddity.app.active.AgentReminderReceiver::class.java)
                .setAction(com.quiddity.app.active.AgentReminderReceiver.ACTION_REMINDER)
                .putExtra(com.quiddity.app.active.AgentReminderReceiver.EXTRA_TITLE, title ?: "Agent 提醒")
                .putExtra(com.quiddity.app.active.AgentReminderReceiver.EXTRA_TEXT, text)
            val pending = PendingIntent.getBroadcast(
                context,
                REMINDER_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarm.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + seconds * 1_000L,
                pending
            )
            "已设置 $seconds 秒后的提醒"
        }.getOrElse { "设置提醒失败：${it.message ?: "未知错误"}" }

    @android.annotation.SuppressLint("WrongConstant")
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

    /**
     * 撤回专用：恢复应用启用状态（停用 ↔ 启用 互逆操作）。
     * @param enable true = 恢复为启用（原停用）；false = 恢复为停用（原启用）。
     * @return 恢复失败的包名列表（空 = 全部成功）。
     */
    suspend fun revertAppEnabledState(pkg: String, enable: Boolean): Boolean {
        val shell = shizuku ?: return false
        if (!shell.isGranted()) return false
        val command = if (enable) enableCommand(pkg) else disableCommand(pkg)
        return shell.exec(command).exitCode == 0
    }

    /**
     * 撤回专用：删除本轮创建的文件/目录（rm -rf，仅删除确实存在的路径）。
     * @return 删除失败的路径列表（空 = 全部成功或无需删除）。
     */
    suspend fun deleteFilesForWithdraw(paths: List<String>): List<String> {
        val shell = shizuku ?: return paths
        if (!shell.isGranted()) return paths
        val failed = mutableListOf<String>()
        paths.forEach { path ->
            val result = shell.exec(arrayOf("rm", "-rf", path))
            if (result.exitCode != 0) failed += path
        }
        return failed
    }

    private suspend fun writeViaShizuku(command: Array<String>, success: String): String {
        requireShizuku()?.let { return it }
        val shell = shizuku ?: return "未获得 Shizuku 授权，无法执行写入操作"
        return formatShellResult(shell.exec(command), success)
    }

    /**
     * 经 Shizuku 写入任意文本内容：
     * 内容 base64 编码后由固定形状的 sh -c 脚本解码落盘，避免把原文拼进命令参数。
     */
    private suspend fun writeTextViaShizuku(path: String, content: String, append: Boolean): String {
        requireShizuku()?.let { return it }
        val shell = shizuku ?: return "未获得 Shizuku 授权，无法写入文件"
        val encoded = android.util.Base64.encodeToString(
            content.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
        val redirect = if (append) ">>" else ">"
        val script = "echo $encoded | base64 -d $redirect ${quoteShell(path)}"
        val result = shell.exec(arrayOf("sh", "-c", script))
        return formatShellResult(
            result,
            if (append) "已追加文本到 $path" else "已写入文件 $path"
        )
    }

    /**
     * 确保图片可被应用直接读取：直读失败时经 Shizuku base64 拷贝到应用缓存目录。
     */
    private suspend fun makeImageReadable(uri: Uri, path: String): Uri {
        val direct = runCatching {
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        }.getOrDefault(false)
        if (direct) return uri
        val shell = shizuku
        if (shell == null || !shell.isGranted()) return uri
        val encoded = shell.exec(arrayOf("base64", path))
        if (encoded.exitCode != 0 || encoded.output.isBlank()) return uri
        return runCatching {
            val raw = android.util.Base64.decode(
                encoded.output.filterNot { it.isWhitespace() },
                android.util.Base64.DEFAULT
            )
            val dir = File(context.cacheDir, "agent-ocr").apply { mkdirs() }
            val file = File(dir, "ocr_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { it.write(raw) }
            Uri.fromFile(file)
        }.getOrDefault(uri)
    }

    private fun parseIso(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { java.time.Instant.parse(value.trim()).toEpochMilli() }.getOrNull()
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

        private const val NOTIFY_ID = 1101
        private const val NOTIFY_REQUEST_CODE = 1102
        private const val REMINDER_REQUEST_CODE = 1202
        private const val MAX_SHELL_RESULT_CHARS = 4_000

        /** 通知守卫订阅游标：记录上次调用已消费到的时间戳（进程内）。 */
        @Volatile
        var lastGuardCursor: Long = 0L

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

        /** 解析 resolve-activity --brief 输出中属于指定包名的组件（pkg/activity）。 */
        fun parseResolvedComponent(output: String, pkg: String): String? =
            output.lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .firstNotNullOfOrNull { line ->
                    val slash = line.indexOf('/')
                    if (slash > 0 && line.substring(0, slash) == pkg) line else null
                }

        /** 单引号包裹路径并转义内嵌单引号，供固定形状 sh -c 脚本安全拼接。 */
        fun quoteShell(path: String): String =
            "'" + path.replace("'", "'\\''") + "'"

        /** 解析 ls -ld 输出为可读元信息；解析失败返回 null。 */
        fun formatFileInfo(output: String, path: String): String? {
            val line = output.lineSequence().firstOrNull { it.isNotBlank() } ?: return null
            val tokens = line.trim().split(Regex("\\s+"))
            if (tokens.size < 7) return null
            val mode = tokens[0]
            val size = tokens[4].toLongOrNull()
            val date = tokens[5]
            val time = tokens[6]
            return buildString {
                append("类型：").append(if (mode.startsWith("d")) "目录" else "文件").append("（").append(mode).append("）")
                append("\n大小：").append(size?.let { formatBytes(it) } ?: "未知")
                append("\n修改时间：").append("$date $time")
            }
        }

        /** Toast 列表格式化：按时间倒序，输出「包名 · 文本」。 */
        fun formatToasts(items: List<AgentToast>): String =
            items.sortedByDescending { it.timestamp }
                .joinToString("\n") { t ->
                    val text = t.text.take(200)
                    "${t.packageName}：$text"
                }

        /** 通知变化事件格式化：按时间倒序，输出「新通知/消失：标题：文本（包名）」。 */
        fun formatNotificationEvents(events: List<AgentNotificationEvent>): String =
            events.joinToString("\n") { e ->
                val label = if (e.type == "removed") "通知消失" else "新通知"
                val title = e.title.ifBlank { "(无标题)" }
                val body = e.text.ifBlank { "" }
                if (body.isEmpty()) "$label：$title（${e.packageName}）"
                else "$label：$title：$body（${e.packageName}）"
            }

        /** 用量明细格式化：按前台时长倒序，输出「包名：时长 / 启动 N 次 / 最后使用时间」。 */
        fun formatUsageDetail(items: List<AgentUsageDetail>): String =
            items.sortedByDescending { it.totalMs }
                .take(20)
                .joinToString("\n") { d ->
                    val minutes = d.totalMs / 60_000
                    val seconds = (d.totalMs % 60_000) / 1_000
                    val duration = if (minutes > 0) "$minutes 分钟 $seconds 秒" else "$seconds 秒"
                    "$d.packageName：$duration / 启动 ${d.launches} 次 / 最后使用 ${formatEpoch(d.lastUsed)}"
                }

        /** 用量按日合计：输出「日期：X 分钟 Y 秒」。 */
        fun formatDailyUsage(daily: List<Pair<String, Long>>): String =
            daily.sortedBy { it.first }
                .joinToString("\n") { (day, ms) ->
                    val minutes = ms / 60_000
                    val seconds = (ms % 60_000) / 1_000
                    "$day：${if (minutes > 0) "$minutes 分钟 $seconds 秒" else "$seconds 秒"}"
                }

        private fun formatDay(ms: Long): String =
            if (ms <= 0L) {
                "未知"
            } else {
                java.text.SimpleDateFormat(
                    "MM-dd",
                    java.util.Locale.getDefault()
                ).format(java.util.Date(ms))
            }
    }
}
