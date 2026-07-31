package com.quiddity.app.ui.chat.components.panels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quiddity.app.data.model.ApiCatalogEntry
import com.quiddity.app.di.ServiceLocator
import com.quiddity.app.domain.ApiCatalogManager
import com.quiddity.app.ui.components.ApiCatalogEditFormState
import com.quiddity.app.ui.components.ApiEditBottomSheet
import com.quiddity.app.ui.components.ConfirmDialog
import com.quiddity.app.ui.components.ExpandableText

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



// ==================== 模型选择 ====================

@Composable
fun ApiSelectorPanel(
    catalog: List<ApiCatalogEntry>,
    currentSelection: String?,
    onBack: () -> Unit,
    onSelect: (String?) -> Unit
) {
    SubPanelScaffold(title = "选择模型", onBack = onBack) {
        if (catalog.isEmpty()) {
            Text(
                "未配置模型，请通过下方「管理模型配置」添加",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            catalog.forEach { entry ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(entry.id) },
                    color = if (entry.id == currentSelection)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            entry.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        ExpandableText(
                            text = entry.apiModel,
                            style = MaterialTheme.typography.bodySmall,
                            maxCollapsedLines = 1
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.size(16.dp))
        TextButton(onClick = { onSelect(null) }) { Text("使用默认") }
    }
}

// ==================== 模型配置管理 ====================

@Composable
fun ApiEditorPanel(
    catalog: List<ApiCatalogEntry>,
    catalogManager: ApiCatalogManager,
    onBack: () -> Unit,
    onAddCatalog: (ApiCatalogEditFormState) -> Unit,
    onUpdateCatalog: (ApiCatalogEditFormState) -> Unit,
    onDeleteCatalog: (String) -> Unit
) {
    // 编辑/新建状态在配置变更后不丢失，保持底部面板保持打开。
    val editingStateSaver = remember {
        Saver<ApiCatalogEditFormState?, List<String>>(
            save = { state ->
                if (state == null) emptyList()
                else listOf(state.id, state.name, state.providerId, state.apiUrl, state.apiModel, state.apiKey)
            },
            restore = { saved ->
                if (saved.size < 6) null
                else ApiCatalogEditFormState(
                    id = saved[0],
                    name = saved[1],
                    providerId = saved[2],
                    apiUrl = saved[3],
                    apiModel = saved[4],
                    apiKey = saved[5]
                )
            }
        )
    }
    var editingState by rememberSaveable(stateSaver = editingStateSaver) { mutableStateOf<ApiCatalogEditFormState?>(null) }
    var isCreating by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ApiCatalogEntry?>(null) }

    SubPanelScaffold(title = "管理模型配置", onBack = onBack) {
        // 添加新 API 按钮
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { isCreating = true },
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    "添加新的模型配置",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.size(12.dp))

        if (catalog.isEmpty()) {
            Text(
                "暂无模型配置，点击上方按钮添加",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            catalog.forEach { entry ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            // 编辑时解密已有 Key 并显示
                            val decryptedKey = if (entry.apiKeyEnc.isNotEmpty()) {
                                runCatching { catalogManager.decryptKey(entry) }.getOrDefault("")
                            } else ""
                            editingState = ApiCatalogEditFormState(
                                id = entry.id,
                                name = entry.name,
                                providerId = entry.providerId,
                                apiUrl = entry.apiUrl,
                                apiModel = entry.apiModel,
                                apiKey = decryptedKey
                            )
                        },
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    entry.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                ExpandableText(
                                    text = entry.apiModel,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxCollapsedLines = 1
                                )
                            }
                            // 删除按钮
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { pendingDelete = entry },
                                contentAlignment = Alignment.Center
                            ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // 编辑底部面板
    editingState?.let { state ->
        ApiEditBottomSheet(
            initial = state,
            catalogManager = catalogManager,
            testConnection = { url, key, model -> catalogManager.testConnection(url, key, model) },
            onDismiss = { editingState = null },
            onSave = { updated ->
                onUpdateCatalog(updated)
                editingState = null
            }
        )
    }

    // 创建底部面板
    if (isCreating) {
        ApiEditBottomSheet(
            initial = null,
            catalogManager = catalogManager,
            testConnection = { url, key, model -> catalogManager.testConnection(url, key, model) },
            onDismiss = { isCreating = false },
            onSave = { newState ->
                onAddCatalog(newState)
                isCreating = false
            }
        )
    }

    // 删除确认
    pendingDelete?.let { entry ->
        ConfirmDialog(
            title = "删除模型配置",
            message = "确定删除「${entry.name}」吗？此操作不可撤销。",
            confirmText = "删除",
            onConfirm = {
                onDeleteCatalog(entry.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null }
        )
    }
}
