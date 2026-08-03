package com.quiddity.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.quiddity.app.domain.ApiCatalogManager
import kotlinx.coroutines.launch

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
 * 模型配置编辑表单（共享状态与 UI）。
 */
@Composable
fun ApiEditForm(
    initial: ApiCatalogEditFormState?,
    catalogManager: ApiCatalogManager,
    testConnection: suspend (apiUrl: String, apiKey: String, model: String) -> Result<String>,
    onDismiss: () -> Unit,
    onSave: (ApiCatalogEditFormState) -> Unit,
    modifier: Modifier = Modifier
) {
    val providers = catalogManager.providers
    val initialProvider = catalogManager.findProvider(initial?.providerId)

    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var selectedProviderId by rememberSaveable { mutableStateOf(initialProvider.id) }
    var apiUrl by rememberSaveable { mutableStateOf(initial?.apiUrl ?: initialProvider.defaultUrl) }
    var apiModel by rememberSaveable {
        mutableStateOf(
            initial?.apiModel
                ?: if (initialProvider.id == "custom") "" else initialProvider.models.firstOrNull().orEmpty()
        )
    }
    // 安全规则：密钥明文仅存在于组合内存（remember），不进 rememberSaveable，避免进程回收后落盘
    var apiKey by remember { mutableStateOf(initial?.apiKey ?: "") }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    // 新增时 Key 可见（鼓励用户核对），编辑时默认隐藏
    var keyVisible by rememberSaveable { mutableStateOf(initial == null) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testIsSuccess by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }

    val selectedProvider = catalogManager.findProvider(selectedProviderId)
    val isCustomProvider = selectedProvider.id == "custom"
    val canSave = name.isNotBlank() && apiUrl.isNotBlank() && apiModel.isNotBlank()
    val canTest = apiUrl.isNotBlank() && apiModel.isNotBlank()

    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 标题 + 帮助问号
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (initial == null) "添加模型配置" else "编辑模型配置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            ApiHelpTooltip(onOpenDialog = { showHelpDialog = true })
        }

        // 服务商选择
        FieldLabel("服务商")
        Box {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { providerMenuExpanded = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedProvider.name.replace('\n', ' '),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            DropdownMenu(
                expanded = providerMenuExpanded,
                onDismissRequest = { providerMenuExpanded = false }
            ) {
                // 自定义服务商与预设服务商之间加入分隔，避免混淆。
                val customIndex = providers.indexOfLast { it.id == "custom" }
                providers.forEachIndexed { index, provider ->
                    if (index == customIndex && index > 0) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                    val isCustom = provider.id == "custom"
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = provider.name.replace('\n', ' '),
                                color = if (isCustom) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        },
                        onClick = {
                            selectedProviderId = provider.id
                            apiUrl = provider.defaultUrl
                            apiModel = if (provider.id == "custom") "" else provider.models.firstOrNull().orEmpty()
                            providerMenuExpanded = false
                        }
                    )
                }
            }
        }

        QuiddityTextField(
            value = name,
            onValueChange = { name = it },
            label = "名称",
            placeholder = "如 我的 OpenAI",
            singleLine = true
        )

        // 接口地址：自定义服务商旁显示帮助问号
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuiddityTextField(
                value = apiUrl,
                onValueChange = { apiUrl = it },
                label = "接口地址",
                placeholder = if (isCustomProvider) {
                    "https://api.example.com/v1/chat/completions"
                } else {
                    "https://api.openai.com/v1/chat/completions"
                },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            if (isCustomProvider) {
                Spacer(modifier = Modifier.size(8.dp))
                ApiHelpTooltip(
                    onOpenDialog = { showHelpDialog = true },
                    iconSize = 22
                )
            }
        }

        // 自定义服务商格式提示
        if (isCustomProvider) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = "自定义模型统一使用 OpenAI 兼容格式。请求地址需填写完整 URL（含 /chat/completions）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // 模型选择：自定义 -> 文本框；预设 -> 下拉
        if (isCustomProvider) {
            QuiddityTextField(
                value = apiModel,
                onValueChange = { apiModel = it },
                label = "模型",
                placeholder = "如 gpt-4o-mini",
                singleLine = true
            )
        } else {
            FieldLabel("模型")
            Box {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { modelMenuExpanded = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = apiModel.ifEmpty { "请选择模型" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (apiModel.isEmpty())
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                DropdownMenu(
                    expanded = modelMenuExpanded,
                    onDismissRequest = { modelMenuExpanded = false }
                ) {
                    selectedProvider.models.forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m) },
                            onClick = {
                                apiModel = m
                                modelMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // 接口密钥（带可见切换）
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("接口密钥") },
            placeholder = { Text("sk-...") },
            singleLine = true,
            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                Icon(
                    imageVector = if (keyVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = if (keyVisible) "隐藏" else "显示",
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { keyVisible = !keyVisible }
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        // 测试连接
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    if (!canTest || testing) return@TextButton
                    testing = true
                    testResult = null
                    scope.launch {
                        testConnection(apiUrl.trim(), apiKey.trim(), apiModel.trim())
                            .onSuccess {
                                testIsSuccess = true
                                testResult = it
                            }
                            .onFailure {
                                testIsSuccess = false
                                testResult = it.message ?: "连接失败"
                            }
                        testing = false
                    }
                },
                enabled = canTest && !testing
            ) {
                Text(if (testing) "测试中…" else "测试连接")
            }
            Spacer(modifier = Modifier.size(8.dp))
            testResult?.let { msg ->
                Icon(
                    imageVector = if (testIsSuccess) Icons.Filled.Check else Icons.Filled.Clear,
                    contentDescription = null,
                    tint = if (testIsSuccess) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (testIsSuccess) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.size(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onDismiss) { Text("取消") }
            TextButton(
                onClick = {
                    if (canSave) {
                        onSave(
                            ApiCatalogEditFormState(
                                id = initial?.id.orEmpty(),
                                name = name.trim(),
                                providerId = selectedProviderId,
                                apiUrl = apiUrl.trim(),
                                apiModel = apiModel.trim(),
                                apiKey = apiKey.trim()
                            )
                        )
                    }
                },
                enabled = canSave
            ) { Text("保存") }
        }
    }

    if (showHelpDialog) {
        ApiKeyHelpDialog(
            catalogManager = catalogManager,
            onDismiss = { showHelpDialog = false }
        )
    }
}

/** 表单状态数据类。id 为空时表示新增，非空时表示更新现有条目。 */
data class ApiCatalogEditFormState(
    val id: String,
    val name: String,
    val providerId: String,
    val apiUrl: String,
    val apiModel: String,
    val apiKey: String
)

@Composable
private fun ApiHelpTooltip(
    onOpenDialog: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Int = 20
) {
    var expanded by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val popupOffset = with(density) { (iconSize.dp + 6.dp).roundToPx() }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
            contentDescription = "帮助",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(iconSize.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { expanded = true }
        )
        if (expanded) {
            Popup(
                alignment = Alignment.BottomCenter,
                offset = IntOffset(0, popupOffset),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    modifier = Modifier
                        .width(270.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.inverseSurface)
                        .padding(12.dp)
                ) {
                    Text(
                        text = buildAnnotatedString {
                            append("建议使用开发者已设定好的模型，一般只需填入 API-KEY 即可直接使用。")
                            withStyle(
                                style = SpanStyle(
                                    color = MaterialTheme.colorScheme.inversePrimary,
                                    textDecoration = TextDecoration.Underline
                                )
                            ) {
                                append("API-KEY 如何获取？")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            expanded = false
                            onOpenDialog()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ApiKeyHelpDialog(
    catalogManager: ApiCatalogManager,
    onDismiss: () -> Unit
) {
    val providers = remember { catalogManager.providers.filter { it.id != "custom" } }
    var selectedProviderId by remember { mutableStateOf(providers.firstOrNull()?.id ?: "") }
    val selectedProvider = providers.firstOrNull { it.id == selectedProviderId }

    // 返回键：关闭帮助弹窗，返回表单，而不是退出应用
    BackHandler(enabled = true) { onDismiss() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "API-KEY 获取指南",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.size(12.dp))

                val context = LocalContext.current

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "选择服务商查看获取方式",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    item {
                        ProviderSelector(
                            providers = providers,
                            selectedId = selectedProviderId,
                            onSelect = { selectedProviderId = it }
                        )
                    }
                    item {
                        selectedProvider?.let { provider ->
                            val info = ApiKeyAcquisitionInfo.forProvider(provider.id)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = provider.name.replace('\n', ' '),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                info.steps.forEachIndexed { index, step ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "${index + 1}.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = step,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(modifier = Modifier.size(6.dp))
                                }
                                if (info.url.isNotEmpty()) {
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Surface(
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surface
                                        ) {
                                            Text(
                                                text = info.url,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        IconButton(
                                            onClick = { copyToClipboard(context, info.url) }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ContentCopy,
                                                contentDescription = "复制地址",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                    item {
                        Text(
                            text = "已预设模型",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    providers.forEach { provider ->
                        item {
                            ProviderModelSection(provider = provider)
                        }
                    }
                }

                Spacer(modifier = Modifier.size(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
    }
}

@Composable
private fun ProviderModelSection(provider: ApiCatalogManager.Provider) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f))
            .padding(12.dp)
    ) {
        Text(
            text = provider.name.replace('\n', ' '),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.size(6.dp))
        Column {
            provider.models.forEach { model ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = model,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderSelector(
    providers: List<ApiCatalogManager.Provider>,
    selectedId: String,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        providers.chunked(2).forEach { rowProviders ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowProviders.forEach { provider ->
                    val selected = provider.id == selectedId
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelect(provider.id) },
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = provider.name.substringBefore('\n'),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                if (rowProviders.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private data class AcquisitionInfo(val steps: List<String>, val url: String)

private object ApiKeyAcquisitionInfo {
    fun forProvider(id: String): AcquisitionInfo = when (id) {
        "alibaba" -> AcquisitionInfo(
            listOf("访问阿里云百炼/灵积控制台", "登录阿里云账号", "在 API-KEY 管理页面创建新密钥", "将 Key 粘贴到上方输入框即可"),
            "https://dashscope.aliyun.com"
        )
        "baidu" -> AcquisitionInfo(
            listOf("访问百度智能云千帆平台", "登录百度账号", "进入应用接入并创建应用", "在应用详情页获取 API Key"),
            "https://qianfan.cloud.baidu.com"
        )
        "siliconflow" -> AcquisitionInfo(
            listOf("访问 SiliconCloud 控制台", "注册/登录 SiliconFlow 账号", "进入 API 密钥页面", "新建 API 密钥并复制"),
            "https://cloud.siliconflow.cn"
        )
        "stepfun" -> AcquisitionInfo(
            listOf("访问阶跃星辰开放平台", "注册/登录账号", "进入 API Key 管理", "创建 Key 并复制"),
            "https://platform.stepfun.com"
        )
        "iflytek" -> AcquisitionInfo(
            listOf("访问讯飞开放平台", "登录账号", "进入讯飞星火开放服务页面", "在认证信息中获取 APIKey 与 APISecret"),
            "https://xinghuo.xfyun.cn"
        )
        "minimax" -> AcquisitionInfo(
            listOf("访问 MiniMax 开放平台", "注册/登录账号", "进入密钥管理", "创建并复制 API Key"),
            "https://platform.minimaxi.com"
        )
        "deepseek" -> AcquisitionInfo(
            listOf("访问 DeepSeek 开放平台", "注册/登录账号", "进入 API keys 页面", "创建新 API key 并复制"),
            "https://platform.deepseek.com"
        )
        "tencent" -> AcquisitionInfo(
            listOf("访问腾讯云混元大模型控制台", "登录腾讯云账号", "开通混元大模型服务", "在 API 密钥管理创建并复制"),
            "https://console.cloud.tencent.com/hunyuan"
        )
        "moonshot" -> AcquisitionInfo(
            listOf("访问 Moonshot AI 开放平台", "注册/登录账号", "进入 API Key 管理", "创建 API Key 并复制"),
            "https://platform.moonshot.cn"
        )
        "bytedance" -> AcquisitionInfo(
            listOf("访问火山引擎方舟控制台", "登录火山引擎账号", "进入 API Key 管理", "创建并复制 API Key"),
            "https://console.volcengine.com/ark"
        )
        "zhipu" -> AcquisitionInfo(
            listOf("访问智谱 AI 开放平台", "注册/登录账号", "进入 API Keys 页面", "添加新 API Key 并复制"),
            "https://open.bigmodel.cn"
        )
        else -> AcquisitionInfo(listOf("请前往该服务商官方网站，登录账号后在控制台或开发者中心创建 API Key。"), "")
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("控制台地址", text))
    Toast.makeText(context, "地址已复制", Toast.LENGTH_SHORT).show()
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium
    )
}
