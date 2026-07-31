package com.quiddity.app.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import com.quiddity.app.data.model.AvatarData
import com.quiddity.app.data.model.ExportPayload
import com.quiddity.app.data.model.WallpaperData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 数据导出 / 导入工具（基于 SAF）。
 *
 * JSON 编解码在 [Dispatchers.Default] 上完成，IO 读写切到 [Dispatchers.IO]。
 *
 * - 导出时读取壁纸图片文件，Base64 编码后嵌入 JSON 的 `wallpapers` 字段。
 * - 导入时将 Base64 数据写回本地 `filesDir/wallpapers/` 目录，
 *   更新 `Conversation.wallpaperUri` 为持久化的 `file://` URI。
 *
 * OOM 防护：
 * - 导入文件大小限制 [MAX_IMPORT_BYTES]（50MB），防止误选大文件。
 * - 壁纸图片读取异常时跳过该会话壁纸，不影响其他数据导出。
 */
object DataPorter {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /**
     * 导入文件最大字节数（OOM 防护）。
     * 50MB 足以容纳 100 万条消息 + 多个 Base64 壁纸，远超普通用户场景。
     */
    private const val MAX_IMPORT_BYTES = 50L * 1024L * 1024L

    /**
     * 导出 [payload] 到指定 SAF Uri。
     *
     * 资源附件（壁纸、头像）读取后 Base64 编码嵌入 JSON：
     * - `wallpapers` / `listWallpaper`：会话级壁纸 + 列表壁纸
     * - `userAvatar`：全局用户头像
     * - `aiAvatars`：每个会话的 AI 头像（key 为会话 ID）
     *
     * 导入时由 [importFrom] 写回本地文件并更新 URI。
     */
    suspend fun exportTo(context: Context, uri: Uri, payload: ExportPayload): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                // 读取每个会话的壁纸图片，Base64 编码
                val wallpapers = mutableMapOf<String, WallpaperData>()
                payload.conversations.forEach { conv ->
                    val wallpaperUriStr = conv.wallpaperUri
                    if (!wallpaperUriStr.isNullOrEmpty()) {
                        try {
                            val bytes = readImageBytes(context, wallpaperUriStr)
                            if (bytes != null && bytes.isNotEmpty()) {
                                wallpapers[conv.id] = WallpaperData(
                                    base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                                    darken = conv.wallpaperDarken
                                )
                                Log.i("DataPorter", "已导出会话 ${conv.id} 的壁纸（${bytes.size} bytes）")
                            }
                        } catch (e: Exception) {
                            Log.w("DataPorter", "读取会话 ${conv.id} 的壁纸失败，跳过", e)
                        }
                    }
                }

                var listWallpaperData: WallpaperData? = null
                val listWallpaperUriStr = payload.settings.listWallpaperUri
                if (!listWallpaperUriStr.isNullOrEmpty()) {
                    try {
                        val bytes = readImageBytes(context, listWallpaperUriStr)
                        if (bytes != null && bytes.isNotEmpty()) {
                            listWallpaperData = WallpaperData(
                                base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                                darken = payload.settings.listWallpaperDarken
                            )
                            Log.i("DataPorter", "已导出列表壁纸（${bytes.size} bytes）")
                        }
                    } catch (e: Exception) {
                        Log.w("DataPorter", "读取列表壁纸失败，跳过", e)
                    }
                }

                // 读取全局用户头像
                var userAvatarData: AvatarData? = null
                val userAvatarUriStr = payload.settings.userAvatarUri
                if (!userAvatarUriStr.isNullOrEmpty()) {
                    try {
                        val bytes = readImageBytes(context, userAvatarUriStr)
                        if (bytes != null && bytes.isNotEmpty()) {
                            userAvatarData = AvatarData(
                                base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            )
                            Log.i("DataPorter", "已导出用户头像（${bytes.size} bytes）")
                        }
                    } catch (e: Exception) {
                        Log.w("DataPorter", "读取用户头像失败，跳过", e)
                    }
                }

                // 读取每个会话的 AI 头像
                val aiAvatars = mutableMapOf<String, AvatarData>()
                payload.conversations.forEach { conv ->
                    val aiAvatarUriStr = conv.persona.aiAvatarUri
                    if (!aiAvatarUriStr.isNullOrEmpty()) {
                        try {
                            val bytes = readImageBytes(context, aiAvatarUriStr)
                            if (bytes != null && bytes.isNotEmpty()) {
                                aiAvatars[conv.id] = AvatarData(
                                    base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                                )
                                Log.i("DataPorter", "已导出会话 ${conv.id} 的 AI 头像（${bytes.size} bytes）")
                            }
                        } catch (e: Exception) {
                            Log.w("DataPorter", "读取会话 ${conv.id} 的 AI 头像失败，跳过", e)
                        }
                    }
                }

                val payloadWithAssets = payload.copy(
                    wallpapers = wallpapers,
                    listWallpaper = listWallpaperData,
                    userAvatar = userAvatarData,
                    aiAvatars = aiAvatars
                )
                val text = withContext(Dispatchers.Default) {
                    json.encodeToString(ExportPayload.serializer(), payloadWithAssets)
                }
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: throw IllegalStateException("无法写入文件")
            }.onFailure { Log.e("DataPorter", "导出失败", it) }
        }

    /**
     * 从 SAF Uri 读取并解析 [ExportPayload]。
     *
     *
     * 1. OOM 防护：先 stat 文件大小，超过 [MAX_IMPORT_BYTES] 直接拒绝。
     * 2. 壁纸恢复：Base64 解码 → 写入 `filesDir/wallpapers/<convId>.jpg` → 更新 URI。
     */
    suspend fun importFrom(context: Context, uri: Uri): Result<ExportPayload> =
        withContext(Dispatchers.IO) {
            runCatching {
                // 1. OOM 防护：先查大小
                context.contentResolver.query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.SIZE),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                            val size = cursor.getLong(sizeIdx)
                            if (size > MAX_IMPORT_BYTES) {
                                throw IllegalStateException(
                                    "导入文件过大（${size / 1024 / 1024}MB > ${MAX_IMPORT_BYTES / 1024 / 1024}MB）"
                                )
                            }
                        }
                    }
                }

                // 2. 读取 + 解码
                val text = context.contentResolver.openInputStream(uri)?.use { ins ->
                    ins.readBytes().toString(Charsets.UTF_8)
                } ?: throw IllegalStateException("无法读取文件")

                val payload = withContext(Dispatchers.Default) {
                    json.decodeFromString(ExportPayload.serializer(), text)
                }

                // 3. 写回壁纸文件 + AI 头像，更新 URI 为持久化的 file:// URI
                val updatedConversations = payload.conversations.map { conv ->
                    var updatedConv = conv

                    // 3a. 会话级壁纸
                    val wallpaperData = payload.wallpapers[conv.id]
                    if (wallpaperData != null) {
                        try {
                            val bytes = Base64.decode(wallpaperData.base64, Base64.DEFAULT)
                            val wallpaperDir = File(context.filesDir, "wallpapers").apply { mkdirs() }
                            val wallpaperFile = File(wallpaperDir, "${conv.id}.jpg")
                            wallpaperFile.writeBytes(bytes)
                            Log.i("DataPorter", "已恢复会话 ${conv.id} 的壁纸到 ${wallpaperFile.absolutePath}")
                            updatedConv = updatedConv.copy(
                                wallpaperUri = Uri.fromFile(wallpaperFile).toString(),
                                wallpaperDarken = wallpaperData.darken
                            )
                        } catch (e: Exception) {
                            Log.w("DataPorter", "写回会话 ${conv.id} 的壁纸失败，保留原 URI", e)
                        }
                    }

                    // 3b. 会话级 AI 头像
                    // 文件名 ai_avatar_<convId>.jpg 与 PersonaPanel 中 outputName 一致，
                    // 确保再次裁剪头像时覆盖此文件而非累积。
                    val aiAvatarData = payload.aiAvatars[conv.id]
                    if (aiAvatarData != null) {
                        try {
                            val bytes = Base64.decode(aiAvatarData.base64, Base64.DEFAULT)
                            val avatarDir = File(context.filesDir, "avatars").apply { mkdirs() }
                            val avatarFile = File(avatarDir, "ai_avatar_${conv.id}.jpg")
                            avatarFile.writeBytes(bytes)
                            Log.i("DataPorter", "已恢复会话 ${conv.id} 的 AI 头像到 ${avatarFile.absolutePath}")
                            updatedConv = updatedConv.copy(
                                persona = updatedConv.persona.copy(
                                    aiAvatarUri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        avatarFile
                                    ).toString()
                                )
                            )
                        } catch (e: Exception) {
                            Log.w("DataPorter", "写回会话 ${conv.id} 的 AI 头像失败，保留原 URI", e)
                        }
                    }

                    updatedConv
                }

                // - 列表壁纸存储在 AppSettings.listWallpaperUri，与会话级壁纸独立
                // - 写回 filesDir/list_wallpapers/list_wallpaper.jpg（与 ListWallpaperPanel 中 outputName 一致）
                // - 更新 settings.listWallpaperUri 为新的 file:// URI
                var updatedSettings = payload.settings
                payload.listWallpaper?.let { lwData ->
                    try {
                        val bytes = Base64.decode(lwData.base64, Base64.DEFAULT)
                        val listWallpaperDir = File(context.filesDir, "list_wallpapers").apply { mkdirs() }
                        // 使用稳定文件名 list_wallpaper.jpg，与 ListWallpaperPanel 中 outputName="list_wallpaper" 一致
                        // 这样导入后用户再次设置列表壁纸时，会覆盖此文件而非累积
                        val listWallpaperFile = File(listWallpaperDir, "list_wallpaper.jpg")
                        listWallpaperFile.writeBytes(bytes)
                        Log.i("DataPorter", "已恢复列表壁纸到 ${listWallpaperFile.absolutePath}")
                        updatedSettings = updatedSettings.copy(
                            listWallpaperUri = Uri.fromFile(listWallpaperFile).toString(),
                            listWallpaperDarken = lwData.darken
                        )
                    } catch (e: Exception) {
                        Log.w("DataPorter", "写回列表壁纸失败，保留原 URI", e)
                    }
                }

                // 全局用户头像：写回 filesDir/avatars/user_avatar.jpg（与 AvatarPicker 中 outputName 一致）
                payload.userAvatar?.let { uaData ->
                    try {
                        val bytes = Base64.decode(uaData.base64, Base64.DEFAULT)
                        val avatarDir = File(context.filesDir, "avatars").apply { mkdirs() }
                        val avatarFile = File(avatarDir, "user_avatar.jpg")
                        avatarFile.writeBytes(bytes)
                        Log.i("DataPorter", "已恢复用户头像到 ${avatarFile.absolutePath}")
                        updatedSettings = updatedSettings.copy(
                            userAvatarUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                avatarFile
                            ).toString()
                        )
                    } catch (e: Exception) {
                        Log.w("DataPorter", "写回用户头像失败，保留原 URI", e)
                    }
                }

                payload.copy(
                    conversations = updatedConversations,
                    settings = updatedSettings
                )
            }.onFailure { Log.e("DataPorter", "导入失败", it) }
        }

    /**
     * 读取图片资源的字节数据（壁纸 / 头像通用）。
     *
     * 支持两种 URI scheme：
     * - `file://`：直接读取本地文件（导入后恢复的资源使用此 scheme）
     * - `content://`：通过 ContentResolver 读取（SAF 选择的资源 / FileProvider 共享的文件使用此 scheme）
     *
     * 读取失败返回 null，不影响其他数据导出。
     */
    private fun readImageBytes(context: Context, uriString: String): ByteArray? {
        return try {
            val uri = Uri.parse(uriString)
            when (uri.scheme) {
                "file" -> {
                    val path = uri.path
                    if (path != null) {
                        val file = File(path)
                        if (file.exists()) file.readBytes() else null
                    } else null
                }
                else -> {
                    // content:// 或其他 scheme，通过 ContentResolver 读取
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
            }
        } catch (e: Exception) {
            Log.w("DataPorter", "读取图片失败: $uriString", e)
            null
        }
    }
}
