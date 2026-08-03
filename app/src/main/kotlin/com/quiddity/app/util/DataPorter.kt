package com.quiddity.app.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import com.quiddity.app.BuildConfig
import com.quiddity.app.data.model.AvatarData
import com.quiddity.app.data.model.Character
import com.quiddity.app.data.model.ConversationBundle
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.data.model.ExportAssets
import com.quiddity.app.data.model.ExportPayload
import com.quiddity.app.data.model.ImportPlan
import com.quiddity.app.data.model.ImportSkipItem
import com.quiddity.app.data.model.Persona
import com.quiddity.app.data.model.WallpaperData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

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
 * 数据导出 / 导入工具（基于 SAF）。
 *
 * JSON 编解码在 [Dispatchers.Default] 上完成，IO 读写切到 [Dispatchers.IO]。
 *
 * - 导出：schema v1（平铺 conversations / wallpapers / aiAvatars）与 schema v2
 *   （characters / privateChats / groupChats / assets 分类节）双版本，按 [ExportPayload.schemaVersion] 分发。
 * - 导入：读取端同时支持 v1 与 v2（2.6）；v1 文件自动「搬家」为 v2 形态
 *   （内嵌 persona 抽成角色库记录，会话写回 characterId 引用）。
 * - 返回 [ImportPlan]（解析结果 + 跳过清单 + 需重填密钥的模型配置），供 UI 展示（4.1）。
 *
 * OOM 防护：
 * - 导入文件大小限制 [MAX_IMPORT_BYTES]（50MB，3.5），防止误选大文件。
 * - 壁纸图片读取异常时跳过该会话壁纸，不影响其他数据导出。
 */
object DataPorter {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /**
     * 导入文件最大字节数（OOM 防护，3.5）。
     * 50MB 足以容纳 100 万条消息 + 多个 Base64 壁纸，远超普通用户场景。
     */
    private const val MAX_IMPORT_BYTES = 50L * 1024L * 1024L

    /**
     * 导出 [payload] 到指定 SAF Uri。
     *
     * 按 [ExportPayload.schemaVersion] 分发：
     * - v1：资源附件（壁纸、头像）读取后 Base64 编码平铺嵌入顶层字段（`wallpapers` / `listWallpaper` /
     *   `userAvatar` / `aiAvatars`，AI 头像 key 为会话 id）。
     * - v2：分类节结构（characters / privateChats / groupChats / assets），AI 头像 key 为角色 id；
     *   导出前做引用校验（2.5），存在悬空引用时拒绝导出。
     */
    suspend fun exportTo(context: Context, uri: Uri, payload: ExportPayload): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (payload.isV2) {
                    exportV2(context, uri, payload)
                } else {
                    exportV1(context, uri, payload)
                }
            }.onFailure { Log.e("DataPorter", "导出失败", it) }
        }

    // ============================================================
    // 导出实现（v1 / v2）
    // ============================================================

    private suspend fun exportV1(context: Context, uri: Uri, payload: ExportPayload) {
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

        // 读取每个会话的 AI 头像（v1：key 为会话 id）
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
    }

    private suspend fun exportV2(context: Context, uri: Uri, payload: ExportPayload) {
        // 2.5 导出前引用校验：存在悬空引用时拒绝导出，避免生成"半残备份"
        val missing = collectMissingReferences(payload)
        if (missing.isNotEmpty()) {
            throw IllegalStateException("导出前引用校验失败，存在悬空引用：${missing.joinToString("；")}")
        }

        // 兼容：调用方只填了 conversations/messages 时，导出前组装为 privateChats
        val bundles = if (payload.privateChats.isNotEmpty()) {
            payload.privateChats
        } else {
            payload.conversations.map { conv ->
                ConversationBundle(
                    conversation = conv,
                    messages = payload.messages[conv.id].orEmpty()
                )
            }
        }

        // 会话级壁纸
        val wallpapers = mutableMapOf<String, WallpaperData>()
        bundles.forEach { bundle ->
            val conv = bundle.conversation
            if (!conv.wallpaperUri.isNullOrEmpty()) {
                try {
                    val bytes = readImageBytes(context, conv.wallpaperUri)
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
        if (!payload.settings.listWallpaperUri.isNullOrEmpty()) {
            try {
                val bytes = readImageBytes(context, payload.settings.listWallpaperUri)
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

        var userAvatarData: AvatarData? = null
        if (!payload.settings.userAvatarUri.isNullOrEmpty()) {
            try {
                val bytes = readImageBytes(context, payload.settings.userAvatarUri)
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

        // 2.4 AI 头像：角色库头像 key 为角色 id；1.3.0 无角色库 UI 时的会话级头像
        // （Persona.aiAvatarUri）key 为会话 id，两者合并导出，导入时分别恢复
        val aiAvatars = mutableMapOf<String, AvatarData>()
        payload.characters.forEach { ch ->
            if (!ch.aiAvatarUri.isNullOrEmpty()) {
                try {
                    val bytes = readImageBytes(context, ch.aiAvatarUri)
                    if (bytes != null && bytes.isNotEmpty()) {
                        aiAvatars[ch.id] = AvatarData(
                            base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        )
                        Log.i("DataPorter", "已导出角色 ${ch.id} 的 AI 头像（${bytes.size} bytes）")
                    }
                } catch (e: Exception) {
                    Log.w("DataPorter", "读取角色 ${ch.id} 的 AI 头像失败，跳过", e)
                }
            }
        }
        bundles.forEach { bundle ->
            val conv = bundle.conversation
            val avatarUri = conv.persona.aiAvatarUri
            if (!avatarUri.isNullOrEmpty()) {
                try {
                    val bytes = readImageBytes(context, avatarUri)
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

        val v2Payload = payload.copy(
            schemaVersion = ExportPayload.SCHEMA_VERSION_2,
            appVersion = payload.appVersion.ifBlank { BuildConfig.VERSION_NAME },
            conversations = emptyList(),
            messages = emptyMap(),
            privateChats = bundles,
            wallpapers = emptyMap(),
            listWallpaper = null,
            userAvatar = null,
            aiAvatars = emptyMap(),
            assets = ExportAssets(
                wallpapers = wallpapers,
                listWallpaper = listWallpaperData,
                userAvatar = userAvatarData,
                aiAvatars = aiAvatars
            )
        )
        val text = withContext(Dispatchers.Default) {
            json.encodeToString(ExportPayload.serializer(), v2Payload)
        }
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.flush()
        } ?: throw IllegalStateException("无法写入文件")
    }

    /**
     * 导出前引用校验（2.5）：群聊的 memberConversationIds、消息的 senderId、
     * 会话的 characterId 必须指向文件内存在的对象；返回缺失项列表，空列表表示通过。
     */
    private fun collectMissingReferences(payload: ExportPayload): List<String> {
        val missing = mutableListOf<String>()
        val characterIds = payload.characters.map { it.id }.toSet()
        val privateIds = payload.privateChats.map { it.conversation.id }.toSet()

        payload.privateChats.forEach { bundle ->
            val characterId = bundle.conversation.characterId
            if (characterId != null && characterId !in characterIds) {
                missing += "私聊会话 ${bundle.conversation.id} 的 characterId $characterId"
            }
        }

        payload.groupChats.forEach { bundle ->
            val conv = bundle.conversation
            conv.memberConversationIds.filterNot { it in privateIds }.forEach { memberId ->
                missing += "群聊会话 ${conv.id} 的成员会话 $memberId"
            }
            bundle.messages
                .filter { it.senderId != null && it.senderId !in conv.memberConversationIds }
                .forEach { msg ->
                    missing += "群聊会话 ${conv.id} 消息 ${msg.id} 的 senderId ${msg.senderId}"
                }
        }
        return missing
    }

    // ============================================================
    // 导入实现（v1 / v2 双版本 + v1 迁移）
    // ============================================================

    /**
     * 从 SAF Uri 读取并解析备份文件，返回 [ImportPlan]（解析结果 + 跳过清单 + 需重填密钥的模型配置）。
     *
     * 1. OOM 防护：先 stat 文件大小，超过 [MAX_IMPORT_BYTES] 直接拒绝。
     * 2. 解析：v1 与 v2 均可读（[ExportPayload] 字段全带默认值）。
     * 3. 迁移：v1 文件自动「搬家」为 v2 形态（2.6：内嵌 persona 抽成角色库记录，会话写回 characterId 引用）。
     * 4. 资产恢复：Base64 解码 → 写回 filesDir 稳定文件名 → 更新 URI。
     * 5. 校验：引用断裂按 3.3 处理，群聊条目计入跳过清单。
     * 6. API Key 解密自检（3.2）：失败条目标记「需重新填写密钥」，不阻塞其他数据。
     */
    suspend fun importFrom(context: Context, uri: Uri): Result<ImportPlan> =
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

                // 3. v1 → v2 迁移（2.6）
                val migrated = if (payload.isV2) payload else migrateV1ToV2(payload)

                // 4. 资产写回（v2 形态：privateChats + characters + assets）
                val (restored, assetSkips) = restoreV2Assets(context, migrated)

                // 5. 引用校验 → 跳过清单（3.3 / 3.4）
                val skipItems = buildSkipItems(restored) + assetSkips

                // 6. API Key 密文解密自检（3.2）：失败条目标记「需重新填写密钥」
                val needsKeyRefill = restored.settings.catalog
                    .filter { entry ->
                        runCatching { CryptoUtils.decrypt(entry.apiKeyEnc) }.isFailure
                    }
                    .map { it.name }

                ImportPlan(
                    payload = restored,
                    skipItems = skipItems,
                    needsKeyRefill = needsKeyRefill,
                    groupChatsSkipped = restored.groupChats.size
                )
            }.onFailure { Log.e("DataPorter", "导入失败", it) }
        }

    /**
     * v1 → v2 迁移（2.6）：
     * - 内嵌 persona 抽成角色库记录（每个带身份/记忆的会话生成一个 Character，id 走 IdGenerator CHARACTER 前缀）
     * - 会话写回 characterId 引用；persona 字段保留作为解析后的缓存副本
     * - 消息无 senderId，按私聊默认值处理（type=SOLO）
     * - memoryIndex / memoryStrategy / groupMemory 取默认值
     * - AI 头像 key 从会话 id 改为角色 id（2.4）
     */
    private fun migrateV1ToV2(payload: ExportPayload): ExportPayload {
        val newCharacters = mutableListOf<Character>()
        val convToCharId = mutableMapOf<String, String>()
        payload.conversations.forEach { conv ->
            if (conv.characterId == null && (conv.persona != Persona.Empty || conv.memory.isNotBlank())) {
                val charId = IdGenerator.newId(IdGenerator.Prefix.CHARACTER)
                newCharacters += Character(
                    id = charId,
                    persona = conv.persona,
                    userPersona = conv.userPersona,
                    memory = conv.memory,
                    aiAvatarUri = conv.persona.aiAvatarUri
                )
                convToCharId[conv.id] = charId
            }
        }
        val privateChats = payload.conversations.map { conv ->
            ConversationBundle(
                conversation = conv.copy(
                    type = ConversationType.SOLO,
                    characterId = convToCharId[conv.id] ?: conv.characterId
                ),
                messages = payload.messages[conv.id].orEmpty()
            )
        }
        // AI 头像 key：会话 id → 角色 id
        val aiAvatars = payload.aiAvatars.mapKeys { (convId, _) -> convToCharId[convId] ?: convId }
        return payload.copy(
            schemaVersion = ExportPayload.SCHEMA_VERSION_2,
            appVersion = payload.appVersion.ifBlank { BuildConfig.VERSION_NAME },
            characters = payload.characters + newCharacters,
            privateChats = privateChats,
            groupChats = payload.groupChats,
            conversations = emptyList(),
            messages = emptyMap(),
            wallpapers = emptyMap(),
            listWallpaper = null,
            userAvatar = null,
            aiAvatars = emptyMap(),
            assets = ExportAssets(
                wallpapers = payload.wallpapers,
                listWallpaper = payload.listWallpaper,
                userAvatar = payload.userAvatar,
                aiAvatars = aiAvatars
            )
        )
    }

    /**
     * 资产写回（v2 形态）：壁纸 / 头像 Base64 解码 → 写回 filesDir 稳定文件名 → 更新 URI。
     * 写回失败计入跳过清单，不影响其他数据（3.4 / 3.5）。
     */
    private suspend fun restoreV2Assets(
        context: Context,
        payload: ExportPayload
    ): Pair<ExportPayload, List<ImportSkipItem>> {
        val assets = payload.assets ?: ExportAssets()
        val skipItems = mutableListOf<ImportSkipItem>()

        // 会话级壁纸
        val updatedBundles = payload.privateChats.map { bundle ->
            var conv = bundle.conversation
            val wallpaperData = assets.wallpapers[conv.id]
            if (wallpaperData != null) {
                try {
                    val bytes = Base64.decode(wallpaperData.base64, Base64.DEFAULT)
                    val wallpaperDir = File(context.filesDir, "wallpapers").apply { mkdirs() }
                    val wallpaperFile = File(wallpaperDir, "${conv.id}.jpg")
                    wallpaperFile.writeBytes(bytes)
                    Log.i("DataPorter", "已恢复会话 ${conv.id} 的壁纸到 ${wallpaperFile.absolutePath}")
                    conv = conv.copy(
                        wallpaperUri = Uri.fromFile(wallpaperFile).toString(),
                        wallpaperDarken = wallpaperData.darken
                    )
                } catch (e: Exception) {
                    Log.w("DataPorter", "写回会话 ${conv.id} 的壁纸失败，保留原 URI", e)
                    skipItems += ImportSkipItem("资产", conv.id, "壁纸写回失败：${e.message}")
                }
            }
            bundle.copy(conversation = conv)
        }

        // 列表壁纸（稳定文件名 list_wallpaper.jpg，覆盖而非累积）
        var updatedSettings = payload.settings
        assets.listWallpaper?.let { lwData ->
            try {
                val bytes = Base64.decode(lwData.base64, Base64.DEFAULT)
                val listWallpaperDir = File(context.filesDir, "list_wallpapers").apply { mkdirs() }
                val listWallpaperFile = File(listWallpaperDir, "list_wallpaper.jpg")
                listWallpaperFile.writeBytes(bytes)
                Log.i("DataPorter", "已恢复列表壁纸到 ${listWallpaperFile.absolutePath}")
                updatedSettings = updatedSettings.copy(
                    listWallpaperUri = Uri.fromFile(listWallpaperFile).toString(),
                    listWallpaperDarken = lwData.darken
                )
            } catch (e: Exception) {
                Log.w("DataPorter", "写回列表壁纸失败，保留原 URI", e)
                skipItems += ImportSkipItem("资产", "list_wallpaper", "列表壁纸写回失败：${e.message}")
            }
        }

        // 全局用户头像（稳定文件名 user_avatar.jpg）
        assets.userAvatar?.let { uaData ->
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
                skipItems += ImportSkipItem("资产", "user_avatar", "用户头像写回失败：${e.message}")
            }
        }

        // AI 头像：key 为角色 id，写回 ai_avatar_<charId>.jpg（再次裁剪时覆盖而非累积）
        val updatedCharacters = payload.characters.map { ch ->
            var updated = ch
            val avatarData = assets.aiAvatars[ch.id]
            if (avatarData != null) {
                try {
                    val bytes = Base64.decode(avatarData.base64, Base64.DEFAULT)
                    val avatarDir = File(context.filesDir, "avatars").apply { mkdirs() }
                    val avatarFile = File(avatarDir, "ai_avatar_${ch.id}.jpg")
                    avatarFile.writeBytes(bytes)
                    Log.i("DataPorter", "已恢复角色 ${ch.id} 的 AI 头像到 ${avatarFile.absolutePath}")
                    updated = ch.copy(
                        aiAvatarUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            avatarFile
                        ).toString()
                    )
                } catch (e: Exception) {
                    Log.w("DataPorter", "写回角色 ${ch.id} 的 AI 头像失败，保留原 URI", e)
                    skipItems += ImportSkipItem("资产", ch.id, "AI 头像写回失败：${e.message}")
                }
            }
            updated
        }

        // 会话级 AI 头像：key 为会话 id（1.3.0 会话内头像导出路径），写回并覆盖会话 persona
        val convIds = updatedBundles.map { it.conversation.id }.toSet()
        val convAvatarUris = mutableMapOf<String, String>()
        assets.aiAvatars.forEach { (key, avatarData) ->
            if (key in convIds) {
                try {
                    val bytes = Base64.decode(avatarData.base64, Base64.DEFAULT)
                    val avatarDir = File(context.filesDir, "avatars").apply { mkdirs() }
                    val avatarFile = File(avatarDir, "ai_avatar_$key.jpg")
                    avatarFile.writeBytes(bytes)
                    Log.i("DataPorter", "已恢复会话 $key 的 AI 头像到 ${avatarFile.absolutePath}")
                    convAvatarUris[key] = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        avatarFile
                    ).toString()
                } catch (e: Exception) {
                    Log.w("DataPorter", "写回会话 $key 的 AI 头像失败，保留原 URI", e)
                    skipItems += ImportSkipItem("资产", key, "AI 头像写回失败：${e.message}")
                }
            }
        }

        // 头像 URI 同步进引用会话的 persona 缓存副本（2.2），会话级头像优先
        val charAvatarUris = updatedCharacters.associate { it.id to it.aiAvatarUri }
        val finalBundles = updatedBundles.map { bundle ->
            val conv = bundle.conversation
            val avatarUri = convAvatarUris[conv.id]
                ?: conv.characterId?.let { charAvatarUris[it] }
            if (avatarUri != null) {
                bundle.copy(conversation = conv.copy(persona = conv.persona.copy(aiAvatarUri = avatarUri)))
            } else {
                bundle
            }
        }

        val updated = payload.copy(
            privateChats = finalBundles,
            characters = updatedCharacters,
            settings = updatedSettings
        )
        return updated to skipItems
    }

    /**
     * 导入前引用校验 → 跳过清单（3.3）：
     * - 群聊整体跳过（1.3.0 群聊实体未加入；成员引用悬空时同样整体跳过）
     * - 消息 senderId 悬空仅发生在文件本身损坏时（导出前已校验），导入保留并显示「未知成员」，不跳过
     * - 会话 characterId 悬空回退使用 conversation.persona 内嵌副本（旧数据路径），不阻塞
     */
    private fun buildSkipItems(payload: ExportPayload): List<ImportSkipItem> {
        val privateIds = payload.privateChats.map { it.conversation.id }.toSet()
        return payload.groupChats.map { bundle ->
            val conv = bundle.conversation
            val danglingMembers = conv.memberConversationIds.filterNot { it in privateIds }
            ImportSkipItem(
                objectType = "群聊",
                id = conv.id,
                reason = if (danglingMembers.isNotEmpty()) {
                    "群聊功能未实现且成员引用悬空（${danglingMembers.joinToString(",")}），整体跳过"
                } else {
                    "群聊功能未实现（1.3.0 仅预留接口），整体跳过"
                }
            )
        }
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
