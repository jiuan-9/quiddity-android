package com.quiddity.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 版本检测器（实体实现）。
 *
 * 功能：
 * - 从网站获取最新版本信息（version.json）
 * - 与当前应用版本比较
 * - 支持"不再提醒当前版本"（持久化到 SharedPreferences）
 *
 * 检测目标：手机端（Android）版本号。
 * version.json 优先读取 `androidVersion` 字段；若不存在则回退到 `version` 字段。
 *
 * 对齐 PC 端 [d:\Quiddity-Chat\src\renderer\update-check.js] 的实现逻辑：
 * - 相同的 VERSION_CHECK_URL
 * - 语义化版本比较（支持预发布标签）
 * - 关闭后该版本不再提醒
 */
object UpdateChecker {

    /**
     * 网站版本信息 URL。
     * 与 PC 端 update-check.js 保持一致。
     */
    private const val VERSION_CHECK_URL =
        "https://raw.githubusercontent.com/jiuan-9/quiddity-website/main/public/version.json"

    /**
     * 官网下载页面 URL（首页）。
     */
    private const val WEBSITE_URL = "https://jiuan-9.github.io/quiddity-website/"

    /**
     * GitHub Releases 兜底 URL。
     * 当前规则：website 域名尚未配置 Pages 时，downloadUrl 退到此 URL，
     * 确保用户始终能跳转到可用的下载页（GitHub Releases 直接展示最新 APK）。
     */
    private const val GITHUB_RELEASES_URL =
        "https://github.com/jiuan-9/quiddity-website/releases/latest"

    /**
     * SharedPreferences 存储键：已忽略的版本号。
     * 用户点击"本次不再提醒"后，该版本号被存储；后续检测到相同版本不再弹窗。
     */
    private const val PREFS_NAME = "update_check_prefs"
    private const val KEY_DISMISSED_VERSION = "dismissed_version"

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

    /**
     * 网站版本信息结构。
     *
     * `androidVersion` 字段为手机端专用版本号（若网站未提供则回退到 `version`）。
     */
    @Serializable
    data class RemoteVersionInfo(
        val version: String = "",
        val androidVersion: String? = null,
        val releaseDate: String = "",
        val downloadUrl: String = "",
        val releaseNotes: String = ""
    ) {
        /**
         * 手机端有效版本号：优先 androidVersion，回退到 version。
         */
        val effectiveAndroidVersion: String
            get() = androidVersion?.takeIf { it.isNotBlank() } ?: version
    }

    /**
     * 版本检测结果。
     */
    sealed class Result {
        /** 有新版本可用。 */
        data class UpdateAvailable(
            val currentVersion: String,
            val remoteVersion: String,
            val releaseNotes: String,
            val downloadUrl: String,
            val releaseDate: String
        ) : Result()

        /** 已是最新版。 */
        data class UpToDate(val currentVersion: String) : Result()

        /** 检测失败（网络错误等）。 */
        data class Error(val message: String) : Result()
    }

    /**
     * 获取当前应用版本名。
     */
    fun getCurrentVersion(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    /**
     * 检查更新（网络请求）。
     *
     * @param context 用于读取当前版本号
     * @param forceCheck true=强制检查（忽略"已忽略版本"）；false=自动检查时如果已忽略则跳过
     */
    suspend fun checkForUpdates(context: Context, forceCheck: Boolean = false): Result = withContext(Dispatchers.IO) {
        try {
            val currentVersion = getCurrentVersion(context)

            // 构建请求（加时间戳防缓存）
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

            // 比较版本号
            val cmp = compareVersions(remoteVersion, currentVersion)

            if (cmp > 0) {
                // 远程版本 > 当前版本 → 有更新
                // 检查是否已被用户忽略
                if (!forceCheck) {
                    val dismissed = getDismissedVersion(context)
                    if (dismissed != null && compareVersions(dismissed, remoteVersion) >= 0) {
                        // 用户已忽略此版本或更高版本，不弹窗
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
     * 记录用户忽略了某个版本（"本次不再提醒"）。
     */
    fun dismissVersion(context: Context, version: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_DISMISSED_VERSION, version).apply()
    }

    /**
     * 读取已忽略的版本号。
     */
    fun getDismissedVersion(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DISMISSED_VERSION, null)
    }

    /**
     * 打开浏览器跳转下载页面。
     *
     * 当前规则：当 downloadUrl 是首页或空字符串时，自动回退到 GitHub Releases
     * 兜底 URL，避免用户跳转到尚未配置 GitHub Pages 的网站首页（404）。
     * 其他异常（如无浏览器）静默失败。
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
     * 语义化版本比较（对齐 PC 端 update-check.js 的 compareVersions）。
     *
     * 支持格式：1.2.0、1.2.0-beta、1.2.0-rc.1
     *
     * @return 正数=a>b，负数=a<b，0=相等
     */
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

        // 比较数字部分
        for (i in 0 until maxOf(numsA.size, numsB.size)) {
            val na = numsA.getOrElse(i) { 0 }
            val nb = numsB.getOrElse(i) { 0 }
            if (na != nb) return na - nb
        }

        // 数字部分相同，比较预发布标签
        // 有预发布标签的版本 < 无标签的正式版
        return when {
            preA.isEmpty() && preB.isEmpty() -> 0
            preA.isEmpty() -> 1  // a 是正式版，b 是预发布 → a > b
            preB.isEmpty() -> -1 // b 是正式版，a 是预发布 → a < b
            else -> preA.compareTo(preB)
        }
    }
}
