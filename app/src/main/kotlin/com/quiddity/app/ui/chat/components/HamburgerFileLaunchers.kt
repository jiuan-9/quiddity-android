package com.quiddity.app.ui.chat.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.quiddity.app.data.model.AppSettings
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ExportPayload
import com.quiddity.app.data.model.ImportMode
import com.quiddity.app.data.model.PersonaCard
import com.quiddity.app.ui.chat.ChatViewModel
import com.quiddity.app.ui.settings.SettingsViewModel
import com.quiddity.app.util.ConversationCodec
import com.quiddity.app.util.DataPorter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 文件传输 launcher 集合：人设卡导出/导入、会话三种格式导出、会话导入。
 */
internal data class HamburgerFileLaunchers(
    val personaExport: ActivityResultLauncher<String>,
    val personaImport: ActivityResultLauncher<Array<String>>,
    val jsonExport: ActivityResultLauncher<String>,
    val markdownExport: ActivityResultLauncher<String>,
    val textExport: ActivityResultLauncher<String>,
    val conversationImport: ActivityResultLauncher<Array<String>>
)

/**
 * 创建汉堡菜单所需的全部 SAF 文件传输 launcher，副作用通过回调上报。
 */
@Composable
internal fun rememberHamburgerFileLaunchers(
    context: Context,
    scope: CoroutineScope,
    conversation: Conversation?,
    settings: AppSettings,
    viewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel,
    onToast: (String) -> Unit,
    onKeyRefill: (List<String>) -> Unit,
    onImportPayload: (ExportPayload) -> Unit
): HamburgerFileLaunchers {
    val personaExport = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            val card = viewModel.exportPersonaCard()
            if (card != null) {
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.Default) {
                            val text = Json.encodeToString(PersonaCard.serializer(), card)
                            withContext(Dispatchers.IO) {
                                context.contentResolver.openOutputStream(uri)
                                    ?.use { it.write(text.toByteArray()) }
                            }
                        }
                    }.onSuccess { onToast("人设卡已导出") }
                        .onFailure { onToast("导出失败：${it.message}") }
                }
            }
        }
    }

    val personaImport = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }
            scope.launch {
                runCatching {
                    val text = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)
                            ?.use { it.readBytes().toString(Charsets.UTF_8) }
                    } ?: throw IllegalStateException("无法读取")
                    val card = withContext(Dispatchers.Default) {
                        Json.decodeFromString(PersonaCard.serializer(), text)
                    }
                    viewModel.importPersonaCard(card)
                }.onSuccess { onToast("人设卡已导入") }
                    .onFailure { onToast("导入失败：${it.message}") }
            }
        }
    }

    // JSON / Markdown / 纯文本 三种格式各自一个 launcher
    val jsonExport = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            val conv = conversation ?: return@rememberLauncherForActivityResult
            scope.launch {
                // 过滤 isNotice 提示气泡：不导出（UI 专用，非对话内容）
                val exportableMessages = viewModel.messages.value.filterNot { it.isNotice }
                val payload = ExportPayload(
                    schemaVersion = 1,
                    exportedAt = System.currentTimeMillis(),
                    settings = settings,
                    conversations = listOf(conv),
                    messages = mapOf(conv.id to exportableMessages)
                )
                DataPorter.exportTo(context, uri, payload)
                    .onSuccess { onToast("对话记录已导出（JSON）") }
                    .onFailure { onToast("导出失败：${it.message}") }
            }
        }
    }

    val markdownExport = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val text = withContext(Dispatchers.Default) {
                        viewModel.exportConversationAsText(ConversationCodec.Format.MARKDOWN)
                            ?: throw IllegalStateException("会话未加载")
                    }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)
                            ?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                            ?: throw IllegalStateException("无法写入文件")
                    }
                }.onSuccess { onToast("对话记录已导出（Markdown）") }
                    .onFailure { onToast("导出失败：${it.message}") }
            }
        }
    }

    val textExport = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val text = withContext(Dispatchers.Default) {
                        viewModel.exportConversationAsText(ConversationCodec.Format.TEXT)
                            ?: throw IllegalStateException("会话未加载")
                    }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)
                            ?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                            ?: throw IllegalStateException("无法写入文件")
                    }
                }.onSuccess { onToast("对话记录已导出（纯文本）") }
                    .onFailure { onToast("导出失败：${it.message}") }
            }
        }
    }

    // 支持 application/json + text/markdown + text/plain
    // 读取后根据内容自动识别格式并调用对应解析器
    val conversationImport = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            }
            scope.launch {
                runCatching {
                    val text = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)
                            ?.use { it.readBytes().toString(Charsets.UTF_8) }
                            ?: throw IllegalStateException("无法读取文件")
                    }
                    val trimmed = text.trimStart()
                    when {
                        // JSON 格式：交给 DataPorter 处理（含壁纸等完整数据）
                        trimmed.startsWith("{") || trimmed.startsWith("[") -> {
                            DataPorter.importFrom(context, uri)
                                .onSuccess { plan ->
                                    if (plan.needsKeyRefill.isNotEmpty()) {
                                        onKeyRefill(plan.needsKeyRefill)
                                    }
                                    // 与设置页导入一致：先恢复壁纸/头像等资产（复制到内部存储并改写 URI），
                                    // 否则导入后这些图片仍是源设备的无效路径而显示损坏。
                                    val (restored, assetSkips) = DataPorter.restoreAssets(context, plan.payload)
                                    // 已有数据时弹窗让用户抉择；无数据时直接合并导入
                                    if (settingsViewModel.hasExistingData()) {
                                        onImportPayload(restored)
                                    } else {
                                        settingsViewModel.importAllPayload(restored, mode = ImportMode.MERGE)
                                        onToast(if (plan.skipItems.isEmpty() && assetSkips.isEmpty()) {
                                            "对话记录已导入（JSON）"
                                        } else {
                                            "对话记录已导入（${plan.skipItems.size + assetSkips.size} 项已跳过）"
                                        })
                                    }
                                }
                                .onFailure { onToast("导入失败：${it.message}") }
                        }
                        // Markdown 或纯文本：交给 ConversationCodec 处理
                        else -> {
                            val result = withContext(Dispatchers.Default) {
                                viewModel.importConversationFromText(text)
                            }
                            result.onSuccess {
                                onToast("对话记录已导入")
                            }.onFailure {
                                onToast("导入失败：${it.message}")
                            }
                        }
                    }
                }.onFailure { onToast("导入失败：${it.message}") }
            }
        }
    }

    return HamburgerFileLaunchers(
        personaExport = personaExport,
        personaImport = personaImport,
        jsonExport = jsonExport,
        markdownExport = markdownExport,
        textExport = textExport,
        conversationImport = conversationImport
    )
}
