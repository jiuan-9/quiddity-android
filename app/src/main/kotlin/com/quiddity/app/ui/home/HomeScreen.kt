package com.quiddity.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.ui.components.ConfirmDialog
import com.quiddity.app.ui.settings.SettingsBottomSheet
import com.quiddity.app.ui.settings.SettingsViewModel
import com.quiddity.app.ui.theme.Motion
import com.quiddity.app.util.DateUtils
import kotlinx.coroutines.delay

// 当前规则：壁纸存在时启用毛玻璃质感和顶部栏半透明；暗化遮罩保证可读性。
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel,
    userAvatarUri: String?,
    onOpenConversation: (String) -> Unit
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    // ===== 三条开发规范（位于文件中间位置） =====
    // 1. 问题修复规范：所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
    //    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
    // 2. 代码注释规范：文件内仅允许保留两类注释——当前规则说明注释与模块划分注释（// ===== xxx =====）。
    //    除此之外，禁止出现任何形式的代码注释（包括单行 / 多行 / 临时调试注释）。
    // 3. 构建交付要求：完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK。
    //    APK 需满足：签名有效且符合发布标准、包含完整功能模块、经过基础性能与兼容性测试。

    val listWallpaperUri = settings.listWallpaperUri
    val listWallpaperDarken = settings.listWallpaperDarken
    val hasListWallpaper = listWallpaperUri != null

    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredConversations by remember {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                conversations
            } else {
                val q = searchQuery.trim()
                conversations.filter {
                    it.title.contains(q, ignoreCase = true) ||
                            it.lastMessagePreview.contains(q, ignoreCase = true)
                }
            }
        }
    }

    var pendingDeleteIds by remember { mutableStateOf<List<String>?>(null) }

    val multiSelectSaver = remember {
        androidx.compose.runtime.saveable.Saver<Pair<Boolean, Set<String>>, String>(
            save = { (isMulti, ids) ->
                "${if (isMulti) "1" else "0"}|${ids.sorted().joinToString(",")}"
            },
            restore = { saved ->
                val parts = saved.split("|", limit = 2)
                val isMulti = parts.firstOrNull() == "1"
                val ids = parts.getOrNull(1)
                    ?.split(",")
                    ?.filter { it.isNotEmpty() }
                    ?.toSet()
                    ?: emptySet()
                isMulti to ids
            }
        )
    }
    val multiSelectState = rememberSaveable(stateSaver = multiSelectSaver) {
        androidx.compose.runtime.mutableStateOf(false to emptySet<String>())
    }
    val isMultiSelect = multiSelectState.value.first
    val selectedIds = multiSelectState.value.second

    fun syncMultiSelect(newIsMulti: Boolean, newIds: Set<String>) {
        multiSelectState.value = newIsMulti to newIds
    }

    fun exitMultiSelect() {
        syncMultiSelect(false, emptySet())
    }

    fun toggleSelection(id: String) {
        val newIds = if (id in selectedIds) {
            selectedIds - id
        } else {
            selectedIds + id
        }
        if (newIds.isEmpty() && isMultiSelect) {
            syncMultiSelect(false, newIds)
        } else {
            syncMultiSelect(true, newIds)
        }
    }

    fun selectAll() {
        syncMultiSelect(true, conversations.map { it.id }.toSet())
    }

    fun invertSelection() {
        val allIds = conversations.map { it.id }.toSet()
        val newIds = allIds - selectedIds
        if (newIds.isEmpty() && isMultiSelect) {
            syncMultiSelect(false, newIds)
        } else {
            syncMultiSelect(true, newIds)
        }
    }

    BackHandler(enabled = isMultiSelect) {
        exitMultiSelect()
    }

    // - 壁纸存在时：底层渲染壁纸图片 + 暗化遮罩，内容层半透明叠加
    // - 壁纸不存在时：使用默认背景色
    Box(modifier = Modifier.fillMaxSize()) {
        // 壁纸层（铺满全屏，在所有内容之下）
        // 注：hasListWallpaper 已包含 listWallpaperUri != null 判断
        if (hasListWallpaper) {
            AsyncImage(
                model = listWallpaperUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // 暗化遮罩：确保上层文字可读
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = listWallpaperDarken))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                // 壁纸存在时内容层透明，让壁纸显示；否则使用默认背景色
                .let { mod ->
                    if (hasListWallpaper) mod
                    else mod.background(MaterialTheme.colorScheme.background)
                }
        ) {
            AnimatedContent(
                targetState = isMultiSelect,
                transitionSpec = {
                    if (targetState) {
                        (slideInVertically(
                            initialOffsetY = { -it / 4 },
                            animationSpec = tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate)
                        ) + fadeIn(tween(Motion.DurationMedium))) togetherWith
                                (slideOutVertically(
                                    targetOffsetY = { it / 4 },
                                    animationSpec = tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate)
                                ) + fadeOut(tween(Motion.DurationShort)))
                    } else {
                        (slideInVertically(
                            initialOffsetY = { it / 4 },
                            animationSpec = tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate)
                        ) + fadeIn(tween(Motion.DurationMedium))) togetherWith
                                (slideOutVertically(
                                    targetOffsetY = { -it / 4 },
                                    animationSpec = tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate)
                                ) + fadeOut(tween(Motion.DurationShort)))
                    }
                },
                label = "topbar_switch"
            ) { multiSelect ->
                if (multiSelect) {
                    MultiSelectTopBar(
                        selectedCount = selectedIds.size,
                        totalCount = conversations.size,
                        onBack = { exitMultiSelect() },
                        onSelectAll = { selectAll() },
                        onInvert = { invertSelection() },
                        onDelete = { pendingDeleteIds = selectedIds.toList() },
                        hasListWallpaper = hasListWallpaper
                    )
                } else {
                    HomeTopBar(
                        userAvatarUri = userAvatarUri,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        onSettingsClick = { showSettings = true },
                        onNewConversation = { viewModel.createConversation() },
                        hasListWallpaper = hasListWallpaper
                    )
                }
            }

            val homeUiState = when {
                isLoading -> "loading"
                conversations.isEmpty() -> "empty"
                filteredConversations.isEmpty() -> "search_empty"
                else -> "content"
            }
            AnimatedContent(
                targetState = homeUiState,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    fadeIn(tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate)) togetherWith
                            fadeOut(tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate))
                },
                label = "home_state_switch"
            ) { state ->
                when (state) {
                    "loading" -> {
                        Box(modifier = Modifier.fillMaxSize())
                    }
                    "empty" -> {
                        WelcomeContent()
                    }
                    "search_empty" -> {
                        SearchEmptyContent(query = searchQuery)
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(
                                items = filteredConversations,
                                key = { it.id },
                                contentType = { "conversation" }
                            ) { conv ->
                                ConversationCard(
                                    conversation = conv,
                                    isMultiSelect = isMultiSelect,
                                    isSelected = conv.id in selectedIds,
                                    onTap = {
                                        if (isMultiSelect) {
                                            toggleSelection(conv.id)
                                        } else {
                                            onOpenConversation(conv.id)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isMultiSelect) {
                                            syncMultiSelect(true, setOf(conv.id))
                                        } else {
                                            toggleSelection(conv.id)
                                        }
                                    },
                                    modifier = Modifier.animateItem(
                                        placementSpec = tween(
                                            Motion.DurationLong,
                                            easing = Motion.EasingEmphasizedDecelerate
                                        ),
                                        fadeInSpec = tween(
                                            Motion.DurationMedium,
                                            easing = Motion.EasingEmphasizedDecelerate
                                        ),
                                        fadeOutSpec = tween(
                                            Motion.DurationShort,
                                            easing = Motion.EasingEmphasizedAccelerate
                                        )
                                    ),
                                    hasListWallpaper = hasListWallpaper
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "所有内容由 AI 生成",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (hasListWallpaper) 0.7f else 0.3f
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(vertical = 4.dp)
            )
        }
    }

    if (showSettings) {
        SettingsBottomSheet(
            viewModel = settingsViewModel,
            onDismiss = { showSettings = false }
        )
    }

    pendingDeleteIds?.let { ids ->
        val isAll = ids.size == conversations.size
        ConfirmDialog(
            title = if (isAll) "删除全部会话" else "删除选中会话",
            message = if (isAll) {
                "确定删除全部 ${ids.size} 个会话？包括所有消息、设置、媒体文件等数据，该操作不可撤销。"
            } else {
                "确定删除选中的 ${ids.size} 个会话？该操作不可撤销。"
            },
            confirmText = "删除",
            onConfirm = {
                viewModel.deleteConversations(ids)
                pendingDeleteIds = null
                exitMultiSelect()
            },
            onDismiss = { pendingDeleteIds = null }
        )
    }
}

@Composable
private fun HomeTopBar(
    userAvatarUri: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onNewConversation: () -> Unit,
    hasListWallpaper: Boolean = false
) {
    // - 实现策略：顶部栏背景使用 surfaceContainerLow 半透明叠加，让壁纸透出
    // - 图标/文字颜色保持 onSurface，确保可读性
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (hasListWallpaper) {
                        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f)
                    } else Color.Transparent
                )
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSettingsClick
                ),
            contentAlignment = Alignment.Center
        ) {
            if (userAvatarUri != null) {
                AsyncImage(
                    model = userAvatarUri,
                    contentDescription = "设置",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(36.dp).clip(CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "设置",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        SearchConversationBar(
            query = searchQuery,
            onQueryChange = onSearchQueryChange,
            modifier = Modifier.weight(1f),
            hasListWallpaper = hasListWallpaper
        )

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (hasListWallpaper) {
                        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f)
                    } else Color.Transparent
                )
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onNewConversation
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.AddComment,
                contentDescription = "新建对话",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun MultiSelectTopBar(
    selectedCount: Int,
    totalCount: Int,
    onBack: () -> Unit,
    onSelectAll: () -> Unit,
    onInvert: () -> Unit,
    onDelete: () -> Unit,
    hasListWallpaper: Boolean = false
) {
    val isAllSelected = selectedCount == totalCount && totalCount > 0
    // 多选栏的按钮背景使用半透明色，保持视觉一致性。
    val iconBgColor = if (hasListWallpaper) {
        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f)
    } else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(iconBgColor)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "取消多选",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.size(4.dp))

        Text(
            text = if (selectedCount == 0) "选择会话" else "已选 $selectedCount 项",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(start = 8.dp)
        )

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(iconBgColor)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onInvert
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.RemoveDone,
                contentDescription = "反选",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(iconBgColor)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSelectAll
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isAllSelected) Icons.Filled.RemoveDone else Icons.Filled.DoneAll,
                contentDescription = if (isAllSelected) "全不选" else "全选",
                tint = if (isAllSelected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }

        val deleteEnabled = selectedCount > 0
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (deleteEnabled) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                    else iconBgColor
                )
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = deleteEnabled,
                    onClick = onDelete
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "删除",
                tint = if (deleteEnabled) MaterialTheme.colorScheme.onErrorContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun ConversationCard(
    conversation: Conversation,
    isMultiSelect: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasListWallpaper: Boolean = false
) {
    // 实现策略：
    // - 壁纸不存在时：保持原有不透明 surfaceContainerLow（视觉无变化）
    // - 壁纸存在时：
    //   * 普通态：surfaceContainerLow @ 0.78f 透明度，让壁纸透出形成毛玻璃质感
    //   * 多选未选：surfaceContainerLow @ 0.55f 透明度（更透，弱化未选项）
    //   * 多选已选：primaryContainer @ 0.80f 透明度（保留选中高亮）
    // - tonalElevation 在壁纸存在时设为 0，避免 M3 自动叠加的不透明色调破坏透明效果
    val cardColor = when {
        isMultiSelect && isSelected -> {
            if (hasListWallpaper) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.80f)
            else MaterialTheme.colorScheme.primaryContainer
        }
        isMultiSelect -> {
            if (hasListWallpaper) MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
        }
        else -> {
            if (hasListWallpaper) MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.78f)
            else MaterialTheme.colorScheme.surfaceContainerLow
        }
    }

    // - 仅保留头像 + 名字
    // - 多选模式下右侧显示选中状态勾选框
    val aiName = conversation.persona?.name?.ifBlank { null } ?: conversation.title
    val aiAvatarUri = conversation.persona?.aiAvatarUri

    // Box 替代 Surface：cardColor 已计算最终色值，Box+background+clip 跳过 Surface 的 CompositionLocalProvider 与 elevation 处理开销
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(cardColor)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isMultiSelect) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "已选中",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.size(14.dp))
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (aiAvatarUri != null) {
                        AsyncImage(
                            model = aiAvatarUri,
                            contentDescription = "AI 头像",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "默认头像",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.size(14.dp))
            }

            Text(
                text = aiName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun WelcomeContent() {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp
    val isLandscape = screenWidthDp > screenHeightDp

    val titleFontSize = when {
        screenWidthDp.value < 360f -> 52.sp
        screenWidthDp.value < 480f -> 64.sp
        screenWidthDp.value < 720f -> 76.sp
        else -> 88.sp
    }

    val subtitleFontSize = if (isLandscape) 20.sp else 22.sp

    val titleColor = MaterialTheme.colorScheme.onSurface
    val subtitleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    val verticalSpacing = if (isLandscape) 12.dp else 20.dp

    val horizontalPadding = if (isLandscape) 40.dp else 24.dp

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(40)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(
            animationSpec = tween(
                durationMillis = 300,
                easing = Motion.EasingEmphasizedDecelerate
            )
        ) + slideInVertically(
            initialOffsetY = { it / 6 },
            animationSpec = tween(
                durationMillis = 320,
                easing = Motion.EasingEmphasizedDecelerate
            )
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterStart
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(verticalSpacing)
            ) {
                Text(
                    text = "Quiddity",
                    fontSize = titleFontSize,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    textAlign = TextAlign.Start,
                    letterSpacing = (-1.2).sp,
                    lineHeight = (titleFontSize.value * 1.05f).sp,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "开始你的旅程",
                    fontSize = subtitleFontSize,
                    fontWeight = FontWeight.Medium,
                    color = subtitleColor,
                    textAlign = TextAlign.Start,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SearchConversationBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hasListWallpaper: Boolean = false
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        // 壁纸存在时半透明叠加，让壁纸透出
        color = if (hasListWallpaper) {
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.7f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        // 壁纸存在时 tonalElevation=0，避免 M3 强制不透明色调
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "搜索会话",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = "清除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onQueryChange("") }
                        )
                )
            }
        }
    }
}

@Composable
private fun SearchEmptyContent(query: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "未找到与“${query}”相关的会话",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}
