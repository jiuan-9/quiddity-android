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
 *    - 以便在真实设备环境中进行功能验证和性能评估。
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
     * 版本信息源 URL 列表（按优先级排序）。
     *
     * 关键设计：国内访问 raw.githubusercontent.com 经常超时 / 被墙，
     * 必须把 Cloudflare Pages 放在第一位，GitHub 源作为兜底。
     * 任一源成功即返回，失败才尝试下一个。
     */
    private val VERSION_CHECK_URLS: List<String> = listOf(
        "https://quiddity-3by.pages.dev/version.json",
        "https://jiuan-9.github.io/Quiddity-website/version.json",
        "https://raw.githubusercontent.com/jiuan-9/Quiddity-website/main/public/version.json"
    )

    /**
     * 官网下载页面 URL（首页）。
     */
    private const val WEBSITE_URL = "https://jiuan-9.github.io/quiddity-website/"

    /**
     * GitHub Releases 兜底 URL。
     */
    private const val GITHUB_RELEASES_URL =
        "https://github.com/jiuan-9/Quiddity-website/releases/latest"

    /**
     * GitHub Releases API 兜底 URL（解析出 APK 直链，国内可通过镜像访问）。
     */
    private const val GITHUB_API_LATEST =
        "https://api.github.com/repos/jiuan-9/Quiddity-website/releases/latest"

    /**
     * APK 备用直链（直接给出的 GitHub Releases APK URL，
     * 当 version.json 中的 downloadUrl 不可用时的最终兜底）。
     */
    private val APK_FALLBACK_URLS: List<String> = listOf(
        "https://quiddity-3by.pages.dev/downloads/quiddity-1.1.1.apk",
        "https://github.com/jiuan-9/Quiddity-website/releases/download/v1.1.1/quiddity-1.1.1.apk"
    )

    /**
     * SharedPreferences 存储键：已忽略的版本号。
     */
    private const val PREFS_NAME = "update_check_prefs"
    private const val KEY_DISMISSED_VERSION = "dismissed_version"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
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

            val remoteInfo = fetchRemoteVersionInfo()
                ?: return@withContext Result.Error("所有版本源均不可达")

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

    /**
     * 多源拉取版本信息，命中任一源即返回。
     * 关键：每次请求都附加 ?t=<timestamp> 绕过 CDN 缓存。
     */
    private fun fetchRemoteVersionInfo(): RemoteVersionInfo? {
        val ts = System.currentTimeMillis()
        for (baseUrl in VERSION_CHECK_URLS) {
            try {
                val url = "$baseUrl?t=$ts"
                val request = Request.Builder()
                    .url(url)
                    .header("Cache-Control", "no-cache")
                    .header("Pragma", "no-cache")
                    .get()
                    .build()
                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    response.close()
                    continue
                }
                val body = response.body?.string()
                response.close()
                if (body.isNullOrBlank()) continue
                val info = json.decodeFromString(RemoteVersionInfo.serializer(), body)
                if (info.effectiveAndroidVersion.isNotBlank()) {
                    return info
                }
            } catch (_: Exception) {
                // 静默失败，继续尝试下一个源
            }
        }
        return null
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
                url.equals("https://quiddity-3by.pages.dev/", ignoreCase = true) -> GITHUB_RELEASES_URL
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
     * 1. URL 为空 / 官网首页 → 尝试 GitHub API 解析最新 APK；失败则用 APK_FALLBACK_URLS 中的预置链接
     * 2. URL 已以 .apk 结尾 → 原样返回
     * 3. URL 指向 GitHub Releases 页面（HTML）→ 调用 GitHub Releases API 解析出
     *    最新 Release 中第一个 .apk 资产的 browser_download_url
     * 4. 其他 URL → 原样返回（不保证是 APK 链接；调用方视情况使用或兜底）
     *
     * @return APK 直链；解析失败返回 null
     */
    suspend fun resolveApkUrl(rawUrl: String): String? = withContext(Dispatchers.IO) {
        val url = rawUrl.trim()
        if (url.isBlank() ||
            url.equals(WEBSITE_URL, ignoreCase = true) ||
            url.equals("https://quiddity-3by.pages.dev/", ignoreCase = true)
        ) {
            return@withContext resolveApkFromGitHubApi()
                ?: APK_FALLBACK_URLS.firstOrNull()
        }

        if (url.endsWith(".apk", ignoreCase = true)) return@withContext url

        if (!isGitHubReleasesUrl(url)) return@withContext url

        val (owner, repo) = parseGitHubOwnerRepo(url) ?: return@withContext null
        fetchLatestApkFromGitHub(owner, repo) ?: APK_FALLBACK_URLS.firstOrNull()
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
     * 通过 GitHub Releases API 拉取默认仓库最新 Release 的第一个 .apk 资产直链。
     */
    private fun resolveApkFromGitHubApi(): String? {
        val (owner, repo) = parseGitHubOwnerRepo(GITHUB_RELEASES_URL) ?: return null
        return fetchLatestApkFromGitHub(owner, repo)
    }

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
                .header("User-Agent", "Quiddity-Android")
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
     * 落地策略：
     * - 使用 setDestinationInExternalFilesDir(context, null, fileName)：
     *   落地到 context.getExternalFilesDir(null)/<fileName> = Android/data/<package>/files/<fileName>
     *   这是 app-private 目录，不受 Scoped Storage 影响，所有 ROM 上都稳定。
     * - 该路径在 file_paths.xml 中已通过 <external-files-path> 暴露给 FileProvider。
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
                .setDestinationInExternalFilesDir(context, null, fileName)
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
     * 关键修复（v1.1.1）：
     * - 旧实现使用 DownloadManager.getUriForDownloadedFile() 返回的 content:// URI
     *   直接安装，会因 DownloadsProvider 不对外授权导致部分 ROM 安装器读不到。
     * - 新实现统一通过 FileProvider.getUriForFile() 包装本地文件，
     *   并通过 Intent.FLAG_GRANT_READ_URI_PERMISSION 授权给目标 Activity。
     * - 同时不再调用无效的 grantUriPermission（自己给自己授权毫无意义）。
     *
     * @return true=已成功发起安装 Intent；false=失败（无 APK / 无法解析文件）
     */
    fun installApk(context: Context, downloadId: Long): Boolean {
        val localFile: File = resolveLocalApkFile(context, downloadId) ?: return false

        val apkUri: Uri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                localFile
            )
        } catch (e: Exception) {
            return false
        }

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
     * 解析已下载的 APK 本地文件路径。
     * 优先级：先查 COLUMN_LOCAL_FILENAME，再查自定义目标目录，再查 Downloads 公共目录。
     */
    private fun resolveLocalApkFile(context: Context, downloadId: Long): File? {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val localFilename: String? = try {
            dm.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
        if (!localFilename.isNullOrBlank()) {
            val f = File(localFilename)
            if (f.exists() && f.length() > 0) return f
        }
        return findApkInTargetDirs(context)
    }

    /**
     * 在预设下载目录中查找最新的 quiddity .apk 文件
     * （防止 DownloadManager 路径解析异常时找不到 APK）。
     */
    private fun findApkInTargetDirs(context: Context): File? {
        val candidates = mutableListOf<File>()
        try {
            context.getExternalFilesDir(null)?.let { base ->
                candidates += base.listFiles { f -> f.isFile && f.name.startsWith("quiddity-", true) && f.name.endsWith(".apk", true) }?.toList() ?: emptyList()
            }
            val cacheDir = context.cacheDir
            candidates += cacheDir.listFiles { f -> f.isFile && f.name.startsWith("quiddity-", true) && f.name.endsWith(".apk", true) }?.toList() ?: emptyList()
            val publicDl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (publicDl.exists()) {
                candidates += publicDl.listFiles { f -> f.isFile && f.name.startsWith("quiddity-", true) && f.name.endsWith(".apk", true) }?.toList() ?: emptyList()
            }
        } catch (_: Exception) {
            // 静默
        }
        return candidates
            .filter { it.length() > 0 }
            .maxByOrNull { it.lastModified() }
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
