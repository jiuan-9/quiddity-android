package com.quiddity.app.ui.chat.components.panels

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.quiddity.app.data.model.Persona
import com.quiddity.app.data.model.UserPersona
import com.quiddity.app.domain.ApiCatalogManager
import com.quiddity.app.ui.components.ImageCropper
import com.quiddity.app.ui.components.ModelTierInfoDialog
import com.quiddity.app.ui.components.QuiddityTextField
import com.quiddity.app.ui.components.QuiddityToggleSwitch
import com.quiddity.app.ui.theme.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
 * 人设 / 场景子面板。
 *
 * 三个相关面板集中到本文件，便于单元测试与未来单独替换实现。
 * 提供统一的 [SubPanelScaffold] 头部组件，避免每个面板重写返回按钮。
 *
 * 人设精调开关：
 * - 保存时若开启精调，调用 AI 编译人设为系统提示词，
 *   结果写入 [Persona.compiledPersona]，被 [com.quiddity.app.domain.PromptBuilder] 优先使用。
 * - 编译期间显示 CircularProgressIndicator，保存按钮禁用，避免重复触发。
 * - 保存成功 / 失败均通过 Toast 反馈。
 *
 * 模型分级权能细分：
 * - 基础级：仅可编辑名字 / 身份 / 性格。
 * - 进阶级：除「你希望ta是什么样的」外全部可编辑。
 * - 完整级：全部可编辑，使用旗舰指令。
 * - 自定义模型自动归为完整级。
 */

// ==================== 通用：子面板头部（返回 + 标题） ====================

@Composable
internal fun SubPanelScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Spacer(modifier = Modifier.size(16.dp))
        content()
    }
}

// ==================== AI 人设 ====================

/**
 * AI 人设面板。
 *
 * 行为：
 * - **自动保存**：字段变更后 500ms 防抖自动保存（不关闭面板），点击遮罩/返回时数据已落盘
 * - **精调后退出**：开启精调 → 编译 → 采用 → 保存并退出到会话（onSaveAndExit）
 * - **普通保存**：未开启精调 → 点击"保存" → 保存并返回主菜单（onSave）
 *
 * @param onSave 保存并返回主菜单（非精调场景）
 * @param onSaveAndExit 保存并退出到会话（精调采用后）
 * @param onAutoSave 自动保存（防抖触发，不关闭面板）
 */
@Composable
fun PersonaPanel(
    initial: Persona,
    ownerId: String,
    compileEnabled: Boolean,
    modelTier: ApiCatalogManager.ModelTier,
    catalogManager: ApiCatalogManager,
    onBack: () -> Unit,
    onSave: (Persona, Boolean) -> Unit,
    onCompile: suspend (Persona, Int) -> String,
    onSaveAndExit: (Persona, Boolean) -> Unit = onSave,
    onAutoSave: (Persona, Boolean) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 所有输入字段全部使用 rememberSaveable，旋转屏/字体大小变更不会丢输入。
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var desired by rememberSaveable { mutableStateOf(initial.desired) }
    var persona by rememberSaveable { mutableStateOf(initial.persona) }
    var character by rememberSaveable { mutableStateOf(initial.character) }
    var appearance by rememberSaveable { mutableStateOf(initial.appearance) }
    var worldBackground by rememberSaveable { mutableStateOf(initial.worldBackground) }
    var aiAvatarUri by rememberSaveable { mutableStateOf(initial.aiAvatarUri) }
    var compiledEnabledState by rememberSaveable { mutableStateOf(compileEnabled) }
    var croppingUri by remember { mutableStateOf<Uri?>(null) }
    var toastMsg by remember { mutableStateOf<String?>(null) }
    // 编译中状态：保存按钮禁用 + 显示加载动画
    var isCompiling by remember { mutableStateOf(false) }
    // 精调结果预览
    var compilePreview by remember { mutableStateOf<String?>(null) }
    // 模型分配方案弹窗
    var showTierInfo by remember { mutableStateOf(false) }

    // 字段可用性：按模型级别决定
    // 「你希望 ta 是什么样的」（desired）仅完整级可用——进阶级不再开放此字段。
    //
    // 规则：
    // - 基础级：仅开放名字 / 身份 / 性格。
    // - 进阶级：开放名字 / 身份 / 性格 / 外观 / 世界背景（不含 desired）。
    // - 完整级：全部开放。
    val appearanceEnabled = modelTier != ApiCatalogManager.ModelTier.BASIC
    val worldBackgroundEnabled = modelTier != ApiCatalogManager.ModelTier.BASIC
    val desiredEnabled = modelTier == ApiCatalogManager.ModelTier.FULL
    val compileToggleEnabled = true

    val onTierHelpClick = { showTierInfo = true }

    var isCopyingAvatar by remember { mutableStateOf(false) }
    val avatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isCopyingAvatar = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        com.quiddity.app.util.ImageUtils.copyToInternalStorage(context, uri)
                    }
                }
                result.onSuccess { internalUri ->
                    croppingUri = internalUri
                }.onFailure { err ->
                    com.quiddity.app.util.CrashLogger.logException(
                        context, err, "PersonaPanel.copyAvatarToInternal"
                    )
                    toastMsg = "图片加载失败：${err.message ?: "未知错误"}"
                }
                isCopyingAvatar = false
            }
        }
    }

    croppingUri?.let { uri ->
        // ImageCropper 内部已处理返回键：取消裁剪并返回面板，而不是退出应用。
        Dialog(
            onDismissRequest = {
                croppingUri = null
                com.quiddity.app.util.ImageUtils.deleteTempFile(uri)
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            ImageCropper(
                imageUri = uri,
                outputName = "ai_avatar_$ownerId",
                onCropComplete = { croppedUri ->
                    aiAvatarUri = croppedUri.toString()
                    croppingUri = null
                    com.quiddity.app.util.ImageUtils.deleteTempFile(uri)
                },
                onCancel = {
                    croppingUri = null
                    com.quiddity.app.util.ImageUtils.deleteTempFile(uri)
                }
            )
        }
    }

    compilePreview?.let { previewText ->
        BackHandler(enabled = true) { compilePreview = null }
        Dialog(
            onDismissRequest = { compilePreview = null },
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
                        text = "精调预览",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = previewText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.size(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        TextButton(onClick = { compilePreview = null }) {
                            Text("返回重调")
                        }
                        TextButton(
                        onClick = {
                            val finalPersona = buildPersonaFromState(
                                initial = initial,
                                name = name,
                                desired = desired,
                                persona = persona,
                                character = character,
                                appearance = appearance,
                                worldBackground = worldBackground,
                                aiAvatarUri = aiAvatarUri,
                                compiledPersona = previewText,
                                modelTier = modelTier
                            )
                            onSaveAndExit(finalPersona, true)
                            compilePreview = null
                            toastMsg = "已采用精调结果"
                        }
                    ) { Text("采用") }
                    }
                }
            }
        }
    }

    if (showTierInfo) {
        ModelTierInfoDialog(
            catalogManager = catalogManager,
            onDismiss = { showTierInfo = false }
        )
    }

    toastMsg?.let { msg ->
        LaunchedEffect(msg) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            toastMsg = null
        }
    }

    // 自动保存（防抖 500ms）
    // - 任意字段变更后 500ms 触发自动保存（不关闭面板）
    // - 用户点击遮罩/返回时数据已落盘，无需显式点击"保存"
    // - 编译中（isCompiling）时不触发自动保存，避免与精调流程冲突
    // - 预览弹窗打开时（compilePreview != null）不触发，避免覆盖正在预览的精调结果
    LaunchedEffect(
        name, desired, persona, character, appearance, worldBackground,
        aiAvatarUri, compiledEnabledState
    ) {
        if (isCompiling || compilePreview != null) return@LaunchedEffect
        kotlinx.coroutines.delay(500)
        if (isCompiling || compilePreview != null) return@LaunchedEffect
        val autoPersona = buildPersonaFromState(
            initial = initial,
            name = name,
            desired = desired,
            persona = persona,
            character = character,
            appearance = appearance,
            worldBackground = worldBackground,
            aiAvatarUri = aiAvatarUri,
            compiledPersona = null,
            modelTier = modelTier
        )
        onAutoSave(autoPersona, compiledEnabledState)
    }

    // 面板关闭时最终保存：确保用户快速返回（500ms 内）不丢失编辑。
    // DisposableEffect(Unit) 仅在进入/离开组合时执行，onDispose 读取最新状态。
    // rememberUpdatedState 保持对最新字段值的引用，避免 onDispose 捕获到过期值。
    val latestName = rememberUpdatedState(name)
    val latestDesired = rememberUpdatedState(desired)
    val latestPersonaField = rememberUpdatedState(persona)
    val latestCharacter = rememberUpdatedState(character)
    val latestAppearance = rememberUpdatedState(appearance)
    val latestWorldBackground = rememberUpdatedState(worldBackground)
    val latestAiAvatarUri = rememberUpdatedState(aiAvatarUri)
    val latestCompiledEnabled = rememberUpdatedState(compiledEnabledState)
    val latestIsCompiling = rememberUpdatedState(isCompiling)
    val latestCompilePreview = rememberUpdatedState(compilePreview)
    val latestOnAutoSave = rememberUpdatedState(onAutoSave)
    DisposableEffect(Unit) {
        onDispose {
            if (latestIsCompiling.value || latestCompilePreview.value != null) return@onDispose
            val finalPersona = buildPersonaFromState(
                initial = initial,
                name = latestName.value,
                desired = latestDesired.value,
                persona = latestPersonaField.value,
                character = latestCharacter.value,
                appearance = latestAppearance.value,
                worldBackground = latestWorldBackground.value,
                aiAvatarUri = latestAiAvatarUri.value,
                compiledPersona = null,
                modelTier = modelTier
            )
            latestOnAutoSave.value(finalPersona, latestCompiledEnabled.value)
        }
    }

    SubPanelScaffold(title = "AI 人设", onBack = onBack) {
        // AI 头像选择
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable {
                        avatarLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                when {
                    isCopyingAvatar -> {
                        // 复制图片到内部存储期间显示 loading
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    aiAvatarUri != null -> {
                        coil.compose.AsyncImage(
                            model = aiAvatarUri,
                            contentDescription = "AI 头像",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    }
                    else -> {
                        Icon(
                            Icons.Filled.Person,
                            "默认头像",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
            Column {
                Text(
                    "AI 头像",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "点击更换",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        Spacer(modifier = Modifier.size(16.dp))

        // 字段顺序：名字 → 身份背景 → 性格 → 外观 → 世界背景 → 你希望ta是什么样的
        // 多行字段启用折叠展开（内容超过 3 行时显示折叠按钮）
        QuiddityTextField(
            value = name, onValueChange = { name = it },
            label = "名字", placeholder = "为ta取一个名字，如 林夕", singleLine = true
        )
        Spacer(modifier = Modifier.size(12.dp))
        QuiddityTextField(
            value = persona, onValueChange = { persona = it },
            label = "身份背景",
            placeholder = "用户的AI助手",
            singleLine = false,
            collapsible = true
        )
        Spacer(modifier = Modifier.size(12.dp))
        QuiddityTextField(
            value = character, onValueChange = { character = it },
            label = "性格",
            placeholder = "ta的性情，如 开朗爱笑，偶尔有些小迷糊",
            singleLine = false,
            collapsible = true
        )
        Spacer(modifier = Modifier.size(12.dp))
        QuiddityTextField(
            value = appearance,
            onValueChange = { appearance = it },
            label = "外观（衣服等）",
            placeholder = "ta的模样，如 一头乌黑长发，喜欢穿白衬衫",
            singleLine = false,
            enabled = appearanceEnabled,
            collapsible = true
        )
        if (!appearanceEnabled) {
            TierRestrictedHint(featureName = "外观（衣服等）", onOpenTierInfo = onTierHelpClick)
        }
        Spacer(modifier = Modifier.size(12.dp))
        QuiddityTextField(
            value = worldBackground,
            onValueChange = { worldBackground = it },
            label = "世界背景",
            placeholder = "ta所处的世界，如 近未来的滨海都市",
            singleLine = false,
            enabled = worldBackgroundEnabled,
            collapsible = true
        )
        if (!worldBackgroundEnabled) {
            TierRestrictedHint(featureName = "世界背景", onOpenTierInfo = onTierHelpClick)
        }
        Spacer(modifier = Modifier.size(12.dp))
        QuiddityTextField(
            value = desired,
            onValueChange = { desired = it },
            label = "你希望 ta 是什么样的？",
            placeholder = "描绘你心中的ta，如 温柔如水，眼神中带着笑意",
            singleLine = false,
            enabled = desiredEnabled,
            collapsible = true
        )
        if (!desiredEnabled) {
            TierRestrictedHint(featureName = "“你希望 ta 是什么样的”", onOpenTierInfo = onTierHelpClick)
        }
        Spacer(modifier = Modifier.size(16.dp))

        // 人设精调开关
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "人设精调",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (compileToggleEnabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    )
                    if (!compileToggleEnabled) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "帮助",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onTierHelpClick
                                )
                        )
                    }
                }
                Text(
                    "将人设信息编译为系统提示词，让 AI 更精准地扮演角色",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.size(12.dp))
            QuiddityToggleSwitch(
                checked = compiledEnabledState,
                onCheckedChange = { compiledEnabledState = it },
                enabled = compileToggleEnabled
            )
        }
        Spacer(modifier = Modifier.size(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onBack, enabled = !isCompiling) { Text("取消") }
            // 保存按钮：编译中时禁用并显示加载动画
            TextButton(
                onClick = {
                    val newPersona = buildPersonaFromState(
                        initial = initial,
                        name = name,
                        desired = desired,
                        persona = persona,
                        character = character,
                        appearance = appearance,
                        worldBackground = worldBackground,
                        aiAvatarUri = aiAvatarUri,
                        compiledPersona = null,
                        modelTier = modelTier
                    )
                    if (compiledEnabledState) {
                        // 开启精调：调用 AI 编译，期间显示加载动画
                        isCompiling = true
                        scope.launch {
                            val result = runCatching { onCompile(newPersona, 1024) }
                            isCompiling = false
                            result.onSuccess { compiledText ->
                                compilePreview = compiledText
                            }.onFailure { e ->
                                toastMsg = "精调失败：${e.message ?: "请检查模型配置"}"
                            }
                        }
                    } else {
                        // 未开启精调：直接保存
                        onSave(newPersona, false)
                        toastMsg = "已保存"
                    }
                },
                enabled = !isCompiling
            ) {
                if (isCompiling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("精调中…")
                } else {
                    Text(if (compiledEnabledState) "精调并保存" else "保存")
                }
            }
        }
    }
}

/**
 * 根据当前 UI 状态与模型级别构造 [Persona]。
 *
 * 被禁用的字段不会覆盖原值，避免用户误清空。
 */
private fun buildPersonaFromState(
    initial: Persona,
    name: String,
    desired: String,
    persona: String,
    character: String,
    appearance: String,
    worldBackground: String,
    aiAvatarUri: String?,
    compiledPersona: String?,
    modelTier: ApiCatalogManager.ModelTier
): Persona {
    val appearanceEnabled = modelTier != ApiCatalogManager.ModelTier.BASIC
    val worldBackgroundEnabled = modelTier != ApiCatalogManager.ModelTier.BASIC
    val desiredEnabled = modelTier == ApiCatalogManager.ModelTier.FULL

    return Persona(
        name = name,
        desired = if (desiredEnabled) desired else initial.desired,
        persona = persona,
        character = character,
        appearance = if (appearanceEnabled) appearance else initial.appearance,
        worldBackground = if (worldBackgroundEnabled) worldBackground else initial.worldBackground,
        compiledPersona = compiledPersona,
        aiAvatarUri = aiAvatarUri
    )
}

/**
 * 模型分级功能受限提示。
 *
 * 当字段因当前模型非完整级而被禁用时，在输入框下方显示文字提示，
 * 并将“《模型管理方案》”做成带下划线的可点击文本，点击后打开说明弹窗。
 * 使用 ClickableText 整段渲染，避免 Row 中两个 Text 换行/截断异常。
 */
@Composable
private fun TierRestrictedHint(
    featureName: String,
    onOpenTierInfo: () -> Unit
) {
    val hintText = buildAnnotatedString {
        append("$featureName 仅支持完整级模型使用，具体信息请查看：")
        pushLink(
            LinkAnnotation.Clickable(
                tag = "tier_info",
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline
                    )
                ),
                linkInteractionListener = { onOpenTierInfo() }
            )
        )
        append("《模型管理方案》")
        pop()
    }

    Text(
        text = hintText,
        style = MaterialTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, start = 4.dp)
    )
}

// ==================== 用户人设 ====================

/**
 * 用户人设面板。
 *
 * 支持自动保存（防抖 500ms），点击遮罩/返回时数据已落盘。
 */
@Composable
fun UserPersonaPanel(
    initial: UserPersona,
    initialMemory: String,
    onBack: () -> Unit,
    onSave: (UserPersona, String) -> Unit,
    onAutoSave: (UserPersona, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var identity by rememberSaveable { mutableStateOf(initial.identity) }
    var gender by rememberSaveable { mutableStateOf(initial.gender) }
    var age by rememberSaveable { mutableStateOf(initial.age) }
    var appearance by rememberSaveable { mutableStateOf(initial.appearance) }
    var memory by rememberSaveable { mutableStateOf(initialMemory) }

    // 自动保存（防抖 500ms）
    LaunchedEffect(name, identity, gender, age, appearance, memory) {
        kotlinx.coroutines.delay(500)
        onAutoSave(
            UserPersona(
                name = name,
                identity = identity,
                gender = gender,
                age = age,
                appearance = appearance
            ),
            memory
        )
    }

    SubPanelScaffold(title = "用户人设", onBack = onBack) {
        QuiddityTextField(
            value = name, onValueChange = { name = it },
            label = "你的名字", placeholder = "如 小明", singleLine = true
        )
        Spacer(modifier = Modifier.size(12.dp))
        QuiddityTextField(
            value = identity, onValueChange = { identity = it },
            label = "你的背景",
            placeholder = "如 大学生，喜欢编程",
            singleLine = false
        )
        Spacer(modifier = Modifier.size(12.dp))
        // 性别：三选一 Chip 按钮（男 / 女 / 暂不设置），避免填空歧义
        Column {
            Text(
                text = "性别",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GenderChip(
                    label = "男",
                    selected = gender == "男",
                    onClick = { gender = "男" }
                )
                GenderChip(
                    label = "女",
                    selected = gender == "女",
                    onClick = { gender = "女" }
                )
                GenderChip(
                    label = "暂不设置",
                    selected = gender == "暂不设置" || gender.isEmpty(),
                    onClick = { gender = "暂不设置" }
                )
            }
        }
        Spacer(modifier = Modifier.size(12.dp))
        QuiddityTextField(
            value = age, onValueChange = { age = it },
            label = "年龄",
            placeholder = "如 20",
            singleLine = true
        )
        Spacer(modifier = Modifier.size(12.dp))
        QuiddityTextField(
            value = appearance, onValueChange = { appearance = it },
            label = "你的外观",
            placeholder = "如 黑色短发，戴眼镜",
            singleLine = false,
            collapsible = true
        )
        Spacer(modifier = Modifier.size(12.dp))
        QuiddityTextField(
            value = memory, onValueChange = { memory = it },
            label = "AI 记忆",
            placeholder = "AI 需要记住的关于你的信息",
            singleLine = false
        )
        Spacer(modifier = Modifier.size(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onBack) { Text("取消") }
            TextButton(onClick = {
                onSave(
                    UserPersona(
                        name = name,
                        identity = identity,
                        gender = gender,
                        age = age,
                        appearance = appearance
                    ),
                    memory
                )
                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
            }) { Text("保存") }
        }
    }
}

/**
 * 性别选择 Chip：选中态用 primary 填充，未选中态用 surface 描边。
 *
 * 用 Box+background+clip 替代 Surface，避免 CompositionLocalProvider 与 elevation 开销。
 */
@Composable
private fun GenderChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val fg = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = fg
        )
    }
}

// ==================== 场景 ====================

/**
 * 场景面板。
 *
 * 支持自动保存（防抖 500ms），点击遮罩/返回时数据已落盘。
 */
@Composable
fun ScenePanel(
    initialScene: String,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
    onAutoSave: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var scene by rememberSaveable { mutableStateOf(initialScene) }

    // 自动保存（防抖 500ms）
    LaunchedEffect(scene) {
        kotlinx.coroutines.delay(500)
        onAutoSave(scene.trim())
    }

    SubPanelScaffold(title = "场景设置", onBack = onBack) {
        QuiddityTextField(
            value = scene, onValueChange = { scene = it },
            label = "场景描述",
            placeholder = "如 在森林中的小屋，黄昏时分",
            singleLine = false
        )
        Spacer(modifier = Modifier.size(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onBack) { Text("取消") }
            TextButton(onClick = {
                onSave(scene.trim())
                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
            }) { Text("保存") }
        }
    }
}
