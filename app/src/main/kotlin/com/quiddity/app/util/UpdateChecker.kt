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
import android.provider.Settings
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
     * 多源拉取版本信息，取所有可达源中版本号最高的结果。
     *
     * 关键设计：
     * - 所有源都请求最新（?t=<timestamp> 绕过 CDN 缓存）；
     * - 不用"首个成功即返回"：多个源内容可能不一致（如某源误放了桌面端
     *   版本号、某源未同步更新），首个命中可能是过时甚至错误的版本；
     * - 改为在所有可达源中取版本最高者，并连带使用该源的 downloadUrl /
     *   releaseNotes，避免"检到新版本却下到旧安装包"的错配。
     */
    private fun fetchRemoteVersionInfo(): RemoteVersionInfo? {
        val ts = System.currentTimeMillis()
        var best: RemoteVersionInfo? = null
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
                    if (best == null ||
                        compareVersions(info.effectiveAndroidVersion, best.effectiveAndroidVersion) > 0
                    ) {
                        best = info
                    }
                }
            } catch (_: Exception) {
                // 静默失败，继续尝试下一个源
            }
        }
        return best
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
     * 规则（按优先级）：
     * 1. 空 / 相对路径 / 官网首页 → 跳过，直接进入兜底链；
     * 2. URL 指向 GitHub Releases 页面（HTML）→ 先调用 GitHub Releases API 解析
     *    最新 Release 中的第一个 .apk 资产直链（动态获取，避免版本写死）；
     * 3. 其他合法 HTTP(S) URL → 视为候选直链；
     * 4. 兜底：解析本仓库 GitHub 最新 Release 的 APK 直链（与版本检测同一发布
     *    仓库），保证兜底版本与检测到的远程版本一致，杜绝"检到新版本却下到旧安装包"；
     * 5. 对候选链逐个做 HEAD 可达性校验，返回第一个可用直链。
     *
     * @return APK 直链；全部候选均不可达返回 null
     */
    suspend fun resolveApkUrl(rawUrl: String): String? = resolveApkUrl(
        rawUrl = rawUrl,
        fetchLatestApk = { owner, repo -> fetchLatestApkFromGitHub(owner, repo) },
        isReachable = { url -> isReachable(url) }
    )

    internal suspend fun resolveApkUrl(
        rawUrl: String,
        fetchLatestApk: (owner: String, repo: String) -> String?,
        isReachable: (String) -> Boolean
    ): String? = withContext(Dispatchers.IO) {
        val url = rawUrl.trim()

        val homepageLike = url.isBlank() ||
            url.startsWith("./") || url.startsWith("../") || url.startsWith("/") ||
            url.equals(WEBSITE_URL, ignoreCase = true) ||
            url.equals("https://quiddity-3by.pages.dev/", ignoreCase = true)

        val candidates = buildList {
            if (!homepageLike && url.startsWith("http", ignoreCase = true)) {
                if (isGitHubReleasesUrl(url)) {
                    parseGitHubOwnerRepo(url)?.let { (owner, repo) ->
                        fetchLatestApkFromGitHub(owner, repo)?.let { add(it) }
                    }
                } else {
                    add(url)
                }
            }
            fetchLatestApk("jiuan-9", "Quiddity-website")?.let { add(it) }
        }
        candidates.firstOrNull { isReachable(it) }
    }

    /**
     * 可达性校验：对候选 APK 链接发起 HEAD 请求，仅接受成功的响应。
     * 用于跳过失效 / 误放的预置链接，保证不会把错误的 URL 交给下载器。
     */
    private fun isReachable(url: String): Boolean {
        return try {
            val request = Request.Builder()
                .url(url)
                .head()
                .build()
            httpClient.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
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
     * 使用 OkHttp 直接下载 APK（绕过系统 DownloadManager，兼容所有 ROM）。
     *
     * 下载到 context.filesDir/<fileName>（内部存储，无需权限），
     * 通过 FileProvider 安装（已配置 <files-path name="internal_files" path="." />）。
     *
     * 相比 DownloadManager 的优势：
     * - 不受国产 ROM 对 DownloadManager 的魔改影响
     * - 进度回调实时准确
     * - 失败原因清晰可读
     *
     * 关键修复：每次调用都强制全新下载。旧实现会复用已存在的同名文件并直接返回，
     * 若旧文件来自过期来源（站点曾误放旧安装包 / 此前下载中断残留），会导致装到
     * 旧版本；现改为启动前删除旧文件与残留 .tmp，保证安装包内容与本次 URL 一致。
     *
     * @return Flow，发射下载进度，最终状态为 SUCCESSFUL 或 FAILED
     */
    fun downloadApkDirect(
        context: Context,
        apkUrl: String,
        fileName: String
    ): Flow<DownloadProgress> = callbackFlow {
        val targetFile = File(context.filesDir, fileName)
        val tempFile = File(context.filesDir, "${fileName}.tmp")

        // 清理陈旧文件，确保本次下载内容与请求 URL 一致
        if (targetFile.exists()) targetFile.delete()
        if (tempFile.exists()) tempFile.delete()

        try {
            val request = Request.Builder()
                .url(apkUrl)
                .header("Cache-Control", "no-cache")
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                trySend(
                    DownloadProgress(
                        downloadId = 0,
                        status = DownloadStatus.FAILED,
                        bytesDownloaded = 0,
                        totalBytes = 0,
                        localUri = null,
                        reason = "HTTP ${response.code}"
                    )
                )
                close()
                return@callbackFlow
            }

            val body = response.body ?: run {
                trySend(DownloadProgress(0, DownloadStatus.FAILED, 0, 0, null, "空响应体"))
                close()
                return@callbackFlow
            }

            val contentLength = body.contentLength()
            var downloaded = 0L

            body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead
                        trySend(
                            DownloadProgress(
                                downloadId = 0,
                                status = DownloadStatus.RUNNING,
                                bytesDownloaded = downloaded,
                                totalBytes = if (contentLength > 0) contentLength else downloaded,
                                localUri = null,
                                reason = ""
                            )
                        )
                    }
                }
            }

            if (tempFile.exists()) {
                targetFile.delete()
                tempFile.renameTo(targetFile)
            }

            if (targetFile.exists() && targetFile.length() > 0) {
                trySend(
                    DownloadProgress(
                        downloadId = 0,
                        status = DownloadStatus.SUCCESSFUL,
                        bytesDownloaded = targetFile.length(),
                        totalBytes = targetFile.length(),
                        localUri = targetFile.absolutePath,
                        reason = ""
                    )
                )
            } else {
                trySend(DownloadProgress(0, DownloadStatus.FAILED, 0, 0, null, "文件写入失败"))
            }
        } catch (e: Exception) {
            trySend(
                DownloadProgress(
                    downloadId = 0,
                    status = DownloadStatus.FAILED,
                    bytesDownloaded = 0,
                    totalBytes = 0,
                    localUri = null,
                    reason = e.message ?: "下载异常"
                )
            )
        }

        close()
    }.flowOn(Dispatchers.IO)

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
     * 触发 APK 安装（直接指定文件）。
     *
     * 通过 FileProvider.getUriForFile() 包装本地文件，
     * 并通过 Intent.FLAG_GRANT_READ_URI_PERMISSION 授权给目标 Activity。
     *
     * @return true=已成功发起安装 Intent；false=失败（无 APK / 无法解析文件）
     */
    fun installApk(context: Context, apkFile: File): Boolean {
        val apkUri: Uri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
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
     * Android 8+ 安装未知来源应用前需用户授权；返回是否已授权。
     */
    fun canRequestPackageInstalls(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }

    /**
     * 跳转"安装未知应用"授权页（仅 Android 8+ 需要）。
     */
    fun openInstallUnknownAppsSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    /**
     * 触发 APK 安装（通过 DownloadManager downloadId）。
     *
     * 关键修复（v1.1.1）：
     * - 旧实现使用 DownloadManager.getUriForDownloadedFile() 返回的 content:// URI
     *   直接安装，会因 DownloadsProvider 不对外授权导致部分 ROM 安装器读不到。
     * - 新实现统一通过 FileProvider.getUriForFile() 包装本地文件，
     *   并通过 Intent.FLAG_GRANT_READ_URI_PERMISSION 授权给目标 Activity。
     * - 同时不再调用无效的 grantUriPermission（自己给自己授权毫无意义）。
     * - 路径解析：优先用 COLUMN_LOCAL_URI（未废弃，返回 content:// URI），
     *   再按已知下载路径直接构造 File 对象兜底，最后全局扫描。
     *
     * @return true=已成功发起安装 Intent；false=失败（无 APK / 无法解析文件）
     */
    fun installApk(context: Context, downloadId: Long): Boolean {
        val localFile: File = resolveLocalApkFile(context, downloadId) ?: return false
        return installApk(context, localFile)
    }

    /**
     * 解析已下载的 APK 本地文件路径。
     *
     * 优先级：
     * 1. COLUMN_LOCAL_URI（API 11+ 可用，未废弃）→ 解析 content:// URI 为 File
     * 2. COLUMN_LOCAL_FILENAME（API 29+ 废弃，部分 ROM 返回 null）→ 直接构造 File
     * 3. 已知下载路径兜底：getExternalFilesDir(null) + quiddity-*.apk
     * 4. 全局扫描：cacheDir + 公共 Downloads 目录
     */
    private fun resolveLocalApkFile(context: Context, downloadId: Long): File? {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        try {
            dm.query(DownloadManager.Query().setFilterById(downloadId)).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    // 方法 1: COLUMN_LOCAL_URI（未废弃，返回 content:// URI）
                    val localUriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    if (localUriIdx >= 0) {
                        val uriStr = cursor.getString(localUriIdx)
                        if (!uriStr.isNullOrBlank()) {
                            val uri = Uri.parse(uriStr)
                            if (uri.scheme == "file") {
                                val f = File(uri.path ?: "")
                                if (f.exists() && f.length() > 0) return f
                            }
                        }
                    }

                    // 方法 2: COLUMN_LOCAL_FILENAME（API 29+ 废弃，但部分 ROM 仍返回）
                    val localFnIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME)
                    if (localFnIdx >= 0) {
                        val fn = cursor.getString(localFnIdx)
                        if (!fn.isNullOrBlank()) {
                            val f = File(fn)
                            if (f.exists() && f.length() > 0) return f
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // 静默
        }

        // 方法 3: 已知下载路径兜底（与 downloadApk 中 setDestinationInExternalFilesDir 一致）
        try {
            context.getExternalFilesDir(null)?.let { base ->
                val candidates = base.listFiles { f ->
                    f.isFile && f.name.startsWith("quiddity-", true) && f.name.endsWith(".apk", true)
                }
                if (candidates != null) {
                    bestApkFile(candidates.toList())?.let { return it }
                }
            }
        } catch (_: Exception) {
            // 静默
        }

        // 方法 4: 全局扫描
        return findApkInTargetDirs(context)
    }

    /**
     * 从候选 APK 列表中选出最优文件：优先取文件名中版本号最高者，
     * 版本相同再取修改时间最新者，避免误选旧版安装包。
     */
    private fun bestApkFile(files: List<File>): File? =
        files.filter { it.length() > 0 }
            .sortedWith(
                Comparator { a, b ->
                    val byVersion = compareVersions(apkVersionInName(b.name), apkVersionInName(a.name))
                    if (byVersion != 0) byVersion else b.lastModified().compareTo(a.lastModified())
                }
            )
            .firstOrNull()

    /**
     * 从 quiddity-x.y.z.apk 文件名中提取版本号；无法识别返回空串。
     */
    private fun apkVersionInName(name: String): String =
        Regex("""quiddity[-_](\d+\.\d+\.\d+)\.apk""", RegexOption.IGNORE_CASE)
            .find(name)?.groupValues?.getOrNull(1) ?: ""

    /**
     * 在预设下载目录中查找最优的 quiddity .apk 文件
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
        return bestApkFile(candidates)
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
        /**
         * 版本比较规则（按用户约定）：
         * - 大版本数字逐段比较：1.2.0-beta < 1.3.0（正式版高于任何前一版本的前缀版）；
         * - 同版本号下：正式版 > 前缀版（1.3.0 > 1.3.0-beta）；
         * - 前缀版内部按段比较，数字段按数值（beta.2 < beta.10），
         *   数字段 < 字母段，字母段按字典序。
         */
        data class Parsed(val nums: List<Int>, val pre: List<String>, val preRaw: String)

        fun parse(v: String): Parsed {
            val cleaned = v.trim().trimStart('v', 'V')
            val dashIdx = cleaned.indexOf('-')
            val main = if (dashIdx >= 0) cleaned.substring(0, dashIdx) else cleaned
            val preRaw = if (dashIdx >= 0) cleaned.substring(dashIdx + 1) else ""
            val nums = main.split(".").map { it.trim().toIntOrNull() ?: 0 }
            val pre = if (preRaw.isBlank()) emptyList() else preRaw.split(".")
            return Parsed(nums, pre, preRaw)
        }

        val (numsA, preA, preRawA) = parse(a)
        val (numsB, preB, preRawB) = parse(b)

        for (i in 0 until maxOf(numsA.size, numsB.size)) {
            val na = numsA.getOrElse(i) { 0 }
            val nb = numsB.getOrElse(i) { 0 }
            if (na != nb) return na - nb
        }

        return when {
            preRawA.isEmpty() && preRawB.isEmpty() -> 0
            preRawA.isEmpty() -> 1
            preRawB.isEmpty() -> -1
            else -> comparePreRelease(preA, preB)
        }
    }

    /** 前缀版段比较（如 beta.2 vs beta.10）。 */
    private fun comparePreRelease(a: List<String>, b: List<String>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val pa = a.getOrElse(i) { "" }
            val pb = b.getOrElse(i) { "" }
            if (pa == pb) continue
            val na = pa.toIntOrNull()
            val nb = pb.toIntOrNull()
            val cmp = when {
                na != null && nb != null -> na.compareTo(nb)
                na != null -> -1
                nb != null -> 1
                else -> pa.compareTo(pb)
            }
            if (cmp != 0) return cmp
        }
        return 0
    }
}
