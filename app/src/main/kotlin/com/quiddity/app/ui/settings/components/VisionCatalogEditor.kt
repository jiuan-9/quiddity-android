package com.quiddity.app.ui.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quiddity.app.data.model.ApiCatalogEntry
import com.quiddity.app.di.ServiceLocator
import com.quiddity.app.domain.ApiCatalogManager
import com.quiddity.app.ui.components.ApiCatalogEditFormState
import com.quiddity.app.ui.components.ApiEditBottomSheet
import com.quiddity.app.ui.components.ConfirmDialog
import com.quiddity.app.ui.components.ExpandableText
import com.quiddity.app.ui.settings.SettingsViewModel
import com.quiddity.app.ui.theme.Motion
import kotlinx.coroutines.launch

/**
 * 视觉 OCR 模型配置编辑器。
 *
 * 与 [ApiCatalogEditor] 结构一致，但读写独立的视觉名册 [AppSettings.visionCatalog]，
 * 服务商下拉使用 [ApiCatalogManager.visionProviders]（各大厂商视觉模型预置项）。
 */
@Composable
fun VisionCatalogEditor(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val apiCatalogManager = remember { ServiceLocator.apiCatalogManager }
    val visionOcrService = remember { ServiceLocator.visionOcrService }
    val visionProviders = remember { apiCatalogManager.visionProviders }
    // 安全规则：不把解密后的 API Key 明文写入 rememberSaveable（可能落盘）；
    // 恢复编辑状态时 apiKey 置空，保存时未重输密钥则保留原密文（见 SettingsViewModel.upsertVisionCatalog）。
    val editingStateSaver = rememberCatalogEditingStateSaver()
    var editingState by rememberSaveable(stateSaver = editingStateSaver) {
        mutableStateOf<ApiCatalogEditFormState?>(null)
    }
    var isCreating by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ApiCatalogEntry?>(null) }


    CatalogEditorScaffold(
        title = "视觉 OCR 模型配置",
        onBack = onBack,
        onAdd = { isCreating = true }
    ) {
        if (settings.visionCatalog.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "尚未配置视觉 OCR 模型\n点右上角 + 选择服务商并填写 API Key",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = settings.visionCatalog,
                key = { it.id },
                contentType = { "vision_catalog_entry" }
            ) { entry ->
                VisionCatalogCard(
                    entry = entry,
                    isActive = entry.id == settings.activeVisionCatalogId,
                    catalogManager = apiCatalogManager,
                    onClick = {
                        // 编辑回显已保存密钥：解密后预填，用户可直接核对
                        editingState = ApiCatalogEditFormState(
                            id = entry.id,
                            name = entry.name,
                            providerId = entry.providerId,
                            apiUrl = entry.apiUrl,
                            apiModel = entry.apiModel,
                            apiKey = apiCatalogManager.decryptKey(entry) ?: "",
                            maxTemperature = entry.maxTemperature
                        )
                    },
                    onSetActive = { viewModel.setActiveVisionCatalog(entry.id) },
                    onDelete = { pendingDelete = entry }
                )
            }
        }
    }

    // 编辑底部面板
    editingState?.let { state ->
        ApiEditBottomSheet(
            initial = state,
            catalogManager = apiCatalogManager,
            providers = visionProviders,
            hasStoredKey = settings.visionCatalog.firstOrNull { it.id == state.id }?.let {
                apiCatalogManager.hasStoredKey(it)
            } ?: false,
            testConnection = { url, key, model ->
                apiCatalogManager.testConnection(url, key, model)
            },
            testVision = { url, key, model ->
                visionOcrService.testVision(url, key, model)
            },
            onDismiss = { editingState = null },
            onSave = { updated ->
                viewModel.upsertVisionCatalog(
                    id = updated.id,
                    name = updated.name,
                    providerId = updated.providerId,
                    apiUrl = updated.apiUrl,
                    apiModel = updated.apiModel,
                    apiKey = updated.apiKey,
                    maxTemperature = updated.maxTemperature
                )
                editingState = null
            }
        )
    }

    // 新建底部面板
    if (isCreating) {
        ApiEditBottomSheet(
            initial = null,
            catalogManager = apiCatalogManager,
            providers = visionProviders,
            testConnection = { url, key, model ->
                apiCatalogManager.testConnection(url, key, model)
            },
            testVision = { url, key, model ->
                visionOcrService.testVision(url, key, model)
            },
            onDismiss = { isCreating = false },
            onSave = { newState ->
                viewModel.upsertVisionCatalog(
                    id = null,
                    name = newState.name,
                    providerId = newState.providerId,
                    apiUrl = newState.apiUrl,
                    apiModel = newState.apiModel,
                    apiKey = newState.apiKey,
                    maxTemperature = newState.maxTemperature
                )
                isCreating = false
            }
        )
    }

    pendingDelete?.let { entry ->
        CatalogDeleteConfirmDialog(
            entry = entry,
            title = "删除视觉 OCR 配置",
            onConfirm = {
                viewModel.removeVisionCatalog(entry.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun VisionCatalogCard(
    entry: ApiCatalogEntry,
    isActive: Boolean,
    catalogManager: ApiCatalogManager,
    onClick: () -> Unit,
    onSetActive: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer
        else com.quiddity.app.ui.components.glassCardColor(),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.size(6.dp))
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.size(4.dp))
                val providerName = catalogManager.visionDisplayNameOf(entry.providerId)
                ExpandableText(
                    text = "${entry.apiModel} · $providerName",
                    style = MaterialTheme.typography.bodySmall,
                    maxCollapsedLines = 1
                )
                ExpandableText(
                    text = entry.apiUrl,
                    style = MaterialTheme.typography.labelSmall,
                    maxCollapsedLines = 1
                )
                Text(
                    text = if (catalogManager.hasStoredKey(entry)) "密钥：已设置" else "密钥：未设置",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (catalogManager.hasStoredKey(entry)) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    }
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (isActive) "当前" else "设为当前",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSetActive() }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onDelete() }
                        .padding(2.dp)
                )
            }
        }
    }
}
