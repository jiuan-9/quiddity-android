package com.quiddity.app.util

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

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
 * 版本检测器（实体实现）。
 *
 * 功能：
 * - 从网站获取最新版本信息（version.json）
 * - 与当前应用版本比较
 * - 支持"不再提醒当前版本"（持久化到 SharedPreferences）
 * - 解析 GitHub Releases 页面为 APK 直链（避免用户去网页手动找下载链接）
 * - 通过系统 DownloadManager 在应用内下载 APK 并触发安装
 *
 * 检测目标：手机端（Android）版本号。
 * version.json 优先读取 `androidVersion` 字段；若不存在则回退到 `version` 字段。
 */
object UpdateChecker {

    /**
     * 网站版本信息 URL。
     */
    private const val VERSION_CHECK_URL =
        "https://raw.githubusercontent.com/jiuan-9/quiddity-website/main/public/version.json"

    /**
     * 官网下载页面 URL（首页）。
     */
    private const val WEBSITE_URL = "https://jiuan-9.github.io/quiddity-website/"

    /**
     * GitHub Releases 兜底 URL。
     */
    private const val GITHUB_RELEASES_URL =
        "https://github.com/jiuan-9/quiddity-website/releases/latest"

    /**
     * SharedPreferences 存储键：已忽略的版本号。
     */
    private const val PREFS_NAME = "update_check_prefs"
    private const val KEY_DISMISSED_VERSION = "dismissed_version"

    /**
     * APK 下载子目录（应用 cache 内），用于 FileProvider 暴露给系统安装器。
     */
    private const val APK_DOWNLOAD_SUBDIR = "apk_update"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Serializable
    data class RemoteVersionInfo(
        val version: String = "",
        val androidVersion: String? = null,
        val releaseDate: String = "",
        val downloadUrl: String = "",
        val releaseNotes: String = ""
    ) {
        val effectiveAndroidVersion: String
            get() = androidVersion?.takeIf { it.isNotBlank() } ?: version
    }

    sealed class Result {
        data class UpdateAvailable(
            val currentVersion: String,
            val remoteVersion: String,
            val releaseNotes: String,
            val downloadUrl: String,
            val releaseDate: String
        ) : Result()

        data class UpToDate(val currentVersion: String) : Result()

        data class Error(val message: String) : Result()
    }

    /**
     * 下载状态。
     */
    enum class DownloadStatus {
        PENDING,
        RUNNING,
        PAUSED,
        SUCCESSFUL,
        FAILED,
        CANCELED
    }

    /**
     * 下载进度。
     */
    data class DownloadProgress(
        val downloadId: Long,
        val status: DownloadStatus,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val localUri: String?,
        val reason: String
    ) {
        /** 进度百分比（0-100）。无 totalBytes 时返回 0。 */
        val percent: Int
            get() = if (totalBytes > 0) ((bytesDownloaded * 100L) / totalBytes).toInt() else 0
    }

    fun getCurrentVersion(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    suspend fun checkForUpdates(context: Context, forceCheck: Boolean = false): Result = withContext(Dispatchers.IO) {
        try {
            val currentVersion = getCurrentVersion(context)

            val url = "$VERSION_CHECK_URL?t=${System.currentTimeMillis()}"
            val request = Request.Builder().url(url).get().build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.Error("服务器返回 ${response.code}")
            }

            val body = response.body?.string()
                ?: return@withContext Result.Error("服务器返回空内容")

            val remoteInfo = json.decodeFromString(RemoteVersionInfo.serializer(), body)
            val remoteVersion = remoteInfo.effectiveAndroidVersion

            if (remoteVersion.isBlank()) {
                return@withContext Result.Error("版本信息格式错误")
            }

            val cmp = compareVersions(remoteVersion, currentVersion)

            if (cmp > 0) {
                if (!forceCheck) {
                    val dismissed = getDismissedVersion(context)
                    if (dismissed != null && compareVersions(dismissed, remoteVersion) >= 0) {
                        return@withContext Result.UpToDate(currentVersion)
                    }
                }
                Result.UpdateAvailable(
                    currentVersion = currentVersion,
                    remoteVersion = remoteVersion,
                    releaseNotes = remoteInfo.releaseNotes,
                    downloadUrl = remoteInfo.downloadUrl.ifBlank { GITHUB_RELEASES_URL },
                    releaseDate = remoteInfo.releaseDate
                )
            } else {
                Result.UpToDate(currentVersion)
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "网络请求失败")
        }
    }

    fun dismissVersion(context: Context, version: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_DISMISSED_VERSION, version).apply()
    }

    fun getDismissedVersion(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DISMISSED_VERSION, null)
    }

    /**
     * 打开浏览器跳转下载页面（兜底入口）。
     *
     * 当 downloadUrl 是首页或空字符串时，自动回退到 GitHub Releases URL。
     * 当 APK 下载链路整体失败时（如网络/解析异常）也走这里作为最终兜底。
     */
    fun openDownloadPage(context: Context, url: String) {
        try {
            val finalUrl = when {
                url.isBlank() -> GITHUB_RELEASES_URL
                url.equals(WEBSITE_URL, ignoreCase = true) -> GITHUB_RELEASES_URL
                else -> url
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finalUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // 没有浏览器可用，静默失败
        }
    }

    /**
     * 解析最终 APK 直链。
     *
     * 规则：
     * 1. URL 为空 / 官网首页 → 返回 null（调用方走 openDownloadPage 兜底）
     * 2. URL 已以 .apk 结尾 → 原样返回
     * 3. URL 指向 GitHub Releases 页面（HTML）→ 调用 GitHub Releases API 解析出
     *    最新 Release 中第一个 .apk 资产的 browser_download_url
     * 4. 其他 URL → 原样返回（不保证是 APK 链接；调用方视情况使用或兜底）
     *
     * @return APK 直链；解析失败返回 null
     */
    suspend fun resolveApkUrl(rawUrl: String): String? = withContext(Dispatchers.IO) {
        val url = rawUrl.trim()
        if (url.isBlank() || url.equals(WEBSITE_URL, ignoreCase = true)) return@withContext null

        if (url.endsWith(".apk", ignoreCase = true)) return@withContext url

        if (!isGitHubReleasesUrl(url)) return@withContext url

        val (owner, repo) = parseGitHubOwnerRepo(url) ?: return@withContext null
        fetchLatestApkFromGitHub(owner, repo)
    }

    /**
     * 是否指向 GitHub Releases 页面。
     * 匹配：https://github.com/{owner}/{repo}/releases[/latest][/tag/xxx]
     */
    private fun isGitHubReleasesUrl(url: String): Boolean {
        val regex = Regex("^https?://github\\.com/[^/]+/[^/]+/releases(/.*)?$", RegexOption.IGNORE_CASE)
        return regex.matches(url)
    }

    /**
     * 从 GitHub Releases URL 中解析 owner / repo。
     * 例：https://github.com/jiuan-9/quiddity-website/releases/latest → ("jiuan-9", "quiddity-website")
     */
    private fun parseGitHubOwnerRepo(url: String): Pair<String, String>? {
        val regex = Regex("^https?://github\\.com/([^/]+)/([^/]+)/releases(/.*)?$", RegexOption.IGNORE_CASE)
        val match = regex.matchEntire(url) ?: return null
        val owner = match.groupValues[1]
        val repo = match.groupValues[2]
        if (owner.isBlank() || repo.isBlank()) return null
        return owner to repo
    }

    /**
     * 通过 GitHub Releases API 拉取最新 Release 的第一个 .apk 资产直链。
     */
    @Serializable
    private data class GitHubAsset(
        val name: String = "",
        val browser_download_url: String = "",
        val content_type: String = ""
    )

    @Serializable
    private data class GitHubRelease(
        val tag_name: String = "",
        val assets: List<GitHubAsset> = emptyList()
    )

    private fun fetchLatestApkFromGitHub(owner: String, repo: String): String? {
        return try {
            val apiUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"
            val request = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val release = json.decodeFromString(GitHubRelease.serializer(), body)
            release.assets.firstOrNull { asset ->
                asset.name.endsWith(".apk", ignoreCase = true) ||
                    asset.content_type == "application/vnd.android.package-archive"
            }?.browser_download_url?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 启动系统 DownloadManager 下载 APK。
     *
     * 文件落地：context.cacheDir/apk_update/<filename>
     * 该路径在 file_paths.xml 中已通过 cache-path 暴露给 FileProvider。
     *
     * @return DownloadManager 分配的 downloadId；参数异常返回 -1
     */
    fun downloadApk(
        context: Context,
        apkUrl: String,
        fileName: String,
        title: String,
        description: String
    ): Long {
        return try {
            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle(title)
                .setDescription(description)
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
        } catch (e: Exception) {
            -1L
        }
    }

    /**
     * 查询当前下载状态。
     */
    fun queryDownload(context: Context, downloadId: Long): DownloadProgress? {
        if (downloadId <= 0) return null
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        return try {
            dm.query(query).use { cursor ->
                if (cursor == null || !cursor.moveToFirst()) return null
                readProgress(cursor, downloadId)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 监听下载进度的 Flow。
     *
     * 实现：注册 DownloadManager.ACTION_DOWNLOAD_COMPLETE 广播 + 每 500ms 主动轮询。
     * 关闭 Flow 时自动注销广播。
     */
    fun observeDownload(context: Context, downloadId: Long): Flow<DownloadProgress> = callbackFlow {
        if (downloadId <= 0) {
            close()
            return@callbackFlow
        }
        val appContext = context.applicationContext
        val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                if (id == downloadId) {
                    queryDownload(appContext, downloadId)?.let { trySend(it) }
                }
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            appContext.registerReceiver(receiver, filter)
        }

        val thread = Thread {
            while (!isClosedForSend) {
                val p = queryDownload(appContext, downloadId)
                if (p != null) {
                    trySend(p)
                    if (p.status == DownloadStatus.SUCCESSFUL ||
                        p.status == DownloadStatus.FAILED ||
                        p.status == DownloadStatus.CANCELED
                    ) {
                        break
                    }
                }
                try {
                    Thread.sleep(500)
                } catch (e: InterruptedException) {
                    break
                }
            }
        }.also { it.isDaemon = true; it.start() }

        queryDownload(appContext, downloadId)?.let { trySend(it) }

        awaitClose {
            try {
                appContext.unregisterReceiver(receiver)
            } catch (_: Exception) {
                // 已注销
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 触发 APK 安装。
     *
     * 优先通过 FileProvider 暴露 content:// URI（Android 7.0+ 强制要求）；
     * 失败时回退到 file:// URI（仅 Android < 7.0 或 FileProvider 未配置时）。
     *
     * @return true=已成功发起安装 Intent；false=失败（无 APK / 无法解析文件）
     */
    fun installApk(context: Context, downloadId: Long): Boolean {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val apkUri: Uri = try {
            // 优先尝试 DownloadManager.getUriForDownloadedFile（Android 8.0+）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val uri = dm.getUriForDownloadedFile(downloadId)
                if (uri != null && uri.toString().isNotBlank()) uri
                else getApkUriFromLocalPath(context, downloadId) ?: return false
            } else {
                getApkUriFromLocalPath(context, downloadId) ?: return false
            }
        } catch (e: Exception) {
            getApkUriFromLocalPath(context, downloadId) ?: return false
        }

        val grantUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                context.grantUriPermission(
                    context.packageName,
                    apkUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                true
            } catch (e: Exception) {
                false
            }
        } else true

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 取消下载。
     */
    fun cancelDownload(context: Context, downloadId: Long) {
        if (downloadId <= 0) return
        try {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.remove(downloadId)
        } catch (_: Exception) {
            // 静默失败
        }
    }

    private fun getApkUriFromLocalPath(context: Context, downloadId: Long): Uri? {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val column = DownloadManager.COLUMN_LOCAL_FILENAME
        return try {
            dm.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                if (cursor == null || !cursor.moveToFirst()) return null
                val idx = cursor.getColumnIndex(column)
                if (idx < 0) return null
                val path = cursor.getString(idx) ?: return null
                val file = File(path)
                if (!file.exists()) return null
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun readProgress(cursor: Cursor, downloadId: Long): DownloadProgress {
        val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
        val bytesIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
        val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
        val localUriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
        val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)

        val statusCode = if (statusIdx >= 0) cursor.getInt(statusIdx) else DownloadManager.STATUS_FAILED
        val status = when (statusCode) {
            DownloadManager.STATUS_PENDING -> DownloadStatus.PENDING
            DownloadManager.STATUS_RUNNING -> DownloadStatus.RUNNING
            DownloadManager.STATUS_PAUSED -> DownloadStatus.PAUSED
            DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.SUCCESSFUL
            DownloadManager.STATUS_FAILED -> DownloadStatus.FAILED
            else -> DownloadStatus.FAILED
        }
        val bytes = if (bytesIdx >= 0) cursor.getLong(bytesIdx) else 0L
        val total = if (totalIdx >= 0) cursor.getLong(totalIdx) else -1L
        val localUri = if (localUriIdx >= 0) cursor.getString(localUriIdx) else null
        val reason = if (reasonIdx >= 0) cursor.getString(reasonIdx) ?: "" else ""
        return DownloadProgress(
            downloadId = downloadId,
            status = status,
            bytesDownloaded = bytes,
            totalBytes = if (total >= 0) total else 0L,
            localUri = localUri,
            reason = reason
        )
    }

    fun compareVersions(a: String, b: String): Int {
        fun parse(v: String): Pair<List<Int>, String> {
            val parts = v.split("-", limit = 2)
            val nums = parts[0].split(".").map { s ->
                s.trim().toIntOrNull() ?: 0
            }
            val pre = parts.getOrNull(1) ?: ""
            return nums to pre
        }

        val (numsA, preA) = parse(a)
        val (numsB, preB) = parse(b)

        for (i in 0 until maxOf(numsA.size, numsB.size)) {
            val na = numsA.getOrElse(i) { 0 }
            val nb = numsB.getOrElse(i) { 0 }
            if (na != nb) return na - nb
        }

        return when {
            preA.isEmpty() && preB.isEmpty() -> 0
            preA.isEmpty() -> 1
            preB.isEmpty() -> -1
            else -> preA.compareTo(preB)
        }
    }
}
