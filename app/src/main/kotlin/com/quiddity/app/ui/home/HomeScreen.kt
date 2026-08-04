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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.domain.GlobalChatSearch
import com.quiddity.app.ui.components.ConfirmDialog
import com.quiddity.app.ui.settings.SettingsBottomSheet
import com.quiddity.app.ui.settings.SettingsViewModel
import com.quiddity.app.ui.theme.Motion
import com.quiddity.app.util.DateUtils
import com.quiddity.app.util.QuiddityConstants
import kotlinx.coroutines.delay
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


// 当前规则：壁纸存在时启用毛玻璃质感和顶部栏半透明；暗化遮罩保证可读性。
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel,
    userAvatarUri: String?,
    onOpenConversation: (String) -> Unit,
    onOpenMessage: (String, String) -> Unit
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showSettings by rememberSaveable { mutableStateOf(false) }

    // ===== 私聊 / 群聊双 Tab（方案十四） =====
    val pagerState = rememberPagerState(pageCount = { 2 })
    val pagerScope = rememberCoroutineScope()
    var showNewGroupDialog by rememberSaveable { mutableStateOf(false) }
    var showGroupTutorial by rememberSaveable { mutableStateOf(false) }

    // 首次进入群聊页弹教程（记录已看过，只弹一次，方案十.9）
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 1 && !settings.groupTutorialSeen) {
            showGroupTutorial = true
            settingsViewModel.setGroupTutorialSeen(true)
        }
    }

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
    var messageHits by remember { mutableStateOf<List<GlobalChatSearch.Hit>>(emptyList()) }

    // 全局消息搜索：输入防抖 300ms，首次搜索时懒加载全量消息索引
    LaunchedEffect(searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            messageHits = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        viewModel.ensureMessageIndexLoaded()
        messageHits = viewModel.searchMessages(q)
    }

    val soloConversations by viewModel.soloConversations.collectAsStateWithLifecycle()
    val groupConversations by viewModel.groupConversations.collectAsStateWithLifecycle()

    fun filterByQuery(list: List<Conversation>): List<Conversation> {
        if (searchQuery.isBlank()) return list
        val q = searchQuery.trim()
        return list.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.lastMessagePreview.contains(q, ignoreCase = true)
        }
    }
    val soloFiltered = remember(soloConversations, searchQuery) { filterByQuery(soloConversations) }
    val groupFiltered = remember(groupConversations, searchQuery) { filterByQuery(groupConversations) }

    // ===== 搜索会话仅对当前模式有效（方案十四 + 需求） =====
    val currentTab = pagerState.currentPage
    val soloIds = remember(soloConversations) { soloConversations.map { it.id }.toSet() }
    val groupIds = remember(groupConversations) { groupConversations.map { it.id }.toSet() }
    val scopedMessageHits = remember(messageHits, currentTab, soloIds, groupIds) {
        val ids = if (currentTab == 0) soloIds else groupIds
        messageHits.filter { it.conversationId in ids }
    }
    val searchScopeConversations = if (currentTab == 0) soloFiltered else groupFiltered

    val deleteIdsSaver = remember {
        androidx.compose.runtime.saveable.Saver<List<String>?, String>(
            save = { ids -> ids?.joinToString(",") ?: "" },
            restore = { saved -> if (saved.isEmpty()) null else saved.split(",").filter { it.isNotEmpty() } }
        )
    }
    var pendingDeleteIds by rememberSaveable(stateSaver = deleteIdsSaver) {
        mutableStateOf<List<String>?>(null)
    }
    var pendingReferencedDelete by rememberSaveable(stateSaver = deleteIdsSaver) {
        mutableStateOf<List<String>?>(null)
    }

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
                    // 模式切换（私聊/群聊）为纯滑动切换，不做顶部淡入淡出，
                    // 避免切换时出现闪动
                    HomeTopBar(
                        userAvatarUri = userAvatarUri,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        onSettingsClick = { showSettings = true },
                        onNewConversation = {
                            if (pagerState.currentPage == 0) {
                                viewModel.createConversation()
                            } else {
                                showNewGroupDialog = true
                            }
                        },
                        hasListWallpaper = hasListWallpaper
                    )
                }
            }

            val homeUiState = when {
                isLoading -> "loading"
                conversations.isEmpty() -> "empty"
                searchQuery.isNotBlank() &&
                    searchScopeConversations.isEmpty() &&
                    scopedMessageHits.isEmpty() -> "search_empty"
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
                        if (searchQuery.isNotBlank()) {
                            GlobalSearchResultList(
                                conversations = searchScopeConversations,
                                messageHits = scopedMessageHits,
                                query = searchQuery,
                                isMultiSelect = isMultiSelect,
                                selectedIds = selectedIds,
                                toggleSelection = ::toggleSelection,
                                syncMultiSelect = ::syncMultiSelect,
                                onOpenConversation = onOpenConversation,
                                onOpenMessage = onOpenMessage,
                                hasListWallpaper = hasListWallpaper
                            )
                        } else {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                AnimatedContent(
                                    targetState = page,
                                    transitionSpec = {
                                        fadeIn(
                                            tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate)
                                        ) togetherWith fadeOut(
                                            tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate)
                                        )
                                    },
                                    label = "tab_list_switch"
                                ) { currentPage ->
                                    if (currentPage == 0) {
                                        ChatListPage(
                                            conversations = soloFiltered,
                                            isMultiSelect = isMultiSelect,
                                            selectedIds = selectedIds,
                                            isGroup = false,
                                            userAvatarUri = userAvatarUri,
                                            memberResolver = viewModel::memberConversations,
                                            toggleSelection = ::toggleSelection,
                                            syncMultiSelect = ::syncMultiSelect,
                                            onOpenConversation = onOpenConversation,
                                            hasListWallpaper = hasListWallpaper
                                        )
                                    } else {
                                        ChatListPage(
                                            conversations = groupFiltered,
                                            isMultiSelect = isMultiSelect,
                                            selectedIds = selectedIds,
                                            isGroup = true,
                                            userAvatarUri = userAvatarUri,
                                            memberResolver = viewModel::memberConversations,
                                            toggleSelection = ::toggleSelection,
                                            syncMultiSelect = ::syncMultiSelect,
                                            onOpenConversation = onOpenConversation,
                                            hasListWallpaper = hasListWallpaper
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ===== 私聊 / 群聊底部 Tab（方案十四.1-4） =====
            // 零会话（欢迎页）时不显示模式切换；从欢迎页进入正式页时淡入（需求）
            AnimatedVisibility(
                visible = !isMultiSelect && searchQuery.isBlank() && conversations.isNotEmpty(),
                enter = fadeIn(tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate)),
                exit = fadeOut(tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate))
            ) {
                ChatTypeTabBar(
                    currentPage = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                    darkMode = settings.darkMode,
                    onSelect = { page ->
                        pagerScope.launch { pagerState.animateScrollToPage(page) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                )
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

        // ===== 群聊教程问号按钮（群聊模式淡入，随时可再开教程，方案三.8） =====
        androidx.compose.animation.AnimatedVisibility(
            visible = pagerState.currentPage == 1 && !isMultiSelect && searchQuery.isBlank(),
            enter = fadeIn(tween(Motion.DurationMedium)),
            exit = fadeOut(tween(Motion.DurationShort)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 68.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (hasListWallpaper) {
                            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showGroupTutorial = true },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    if (showSettings) {
        SettingsBottomSheet(
            viewModel = settingsViewModel,
            onDismiss = { showSettings = false }
        )
    }

    if (showNewGroupDialog) {
        NewGroupDialog(
            soloConversations = soloConversations,
            hasApiConfig = settings.catalog.isNotEmpty(),
            onCreate = { memberIds, title ->
                viewModel.createGroup(memberIds, title) { result, failures ->
                    showNewGroupDialog = false
                    if (failures.isNotEmpty()) {
                        android.widget.Toast.makeText(
                            context,
                            "${failures.size} 个成员未通过校验：\n${failures.joinToString("\n")}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                    result.fold(
                        onSuccess = { groupId -> onOpenConversation(groupId) },
                        onFailure = { e ->
                            android.widget.Toast.makeText(
                                context,
                                e.message ?: "创建群聊失败",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            },
            onDismiss = { showNewGroupDialog = false }
        )
    }

    if (showGroupTutorial) {
        GroupTutorialDialog(onDismiss = { showGroupTutorial = false })
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
                viewModel.deleteConversations(
                    ids,
                    onReferencedByGroups = { referenced -> pendingReferencedDelete = referenced }
                )
                pendingDeleteIds = null
                exitMultiSelect()
            },
            onDismiss = { pendingDeleteIds = null }
        )
    }

    pendingReferencedDelete?.let { ids ->
        ConfirmDialog(
            title = "删除会话",
            message = "选中的会话被群聊引用为成员。删除后这些成员将从群聊中移除，" +
                "群聊及其历史消息保留。确定删除？",
            confirmText = "删除",
            onConfirm = {
                viewModel.confirmDeleteReferencedConversations(ids)
                pendingReferencedDelete = null
                exitMultiSelect()
            },
            onDismiss = { pendingReferencedDelete = null }
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

    val titleFontSize = (screenWidthDp.value * 0.20f).sp
    val maxTitleSize = 72.sp
    val finalTitleSize = if (titleFontSize.value > maxTitleSize.value) maxTitleSize else titleFontSize

    val subtitleFontSize = 18.sp

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
                    fontSize = finalTitleSize,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    textAlign = TextAlign.Start,
                    letterSpacing = (-1.2).sp,
                    lineHeight = (finalTitleSize.value * 1.05f).sp,
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
            text = "未找到与“${query}”相关的会话或消息",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

/** 搜索结果分区标题（会话 / 消息）。 */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

/** 全局消息搜索命中行：会话标题 + 角色/内容摘录 + 时间。 */
@Composable
private fun MessageHitRow(
    hit: GlobalChatSearch.Hit,
    query: String,
    onClick: () -> Unit
) {
    val roleLabel = if (hit.message.role == com.quiddity.app.data.model.Role.USER) "我" else "AI"
    val colorScheme = MaterialTheme.colorScheme
    val excerpt = remember(hit.message.content, query) {
        com.quiddity.app.domain.ChatRecordSearch.buildExcerpt(hit.message.content, query)
    }
    val displayText = remember(excerpt, hit.message.content, roleLabel, colorScheme) {
        val fallback = hit.message.content.replace("\n", " ").trim()
            .let { if (it.length > 80) it.take(80) + "…" else it }
        val prefix = "$roleLabel："
        buildAnnotatedString {
            append(prefix)
            append(excerpt?.text ?: fallback)
            if (excerpt != null) {
                excerpt.highlights.forEach { range ->
                    addStyle(
                        SpanStyle(
                            background = colorScheme.primary.copy(alpha = 0.15f),
                            fontWeight = FontWeight.SemiBold
                        ),
                        start = prefix.length + range.first,
                        end = prefix.length + range.last + 1
                    )
                }
            }
        }
    }
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
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = hit.conversationTitle.ifBlank { "未命名会话" },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = DateUtils.formatSearchTime(hit.message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ============================================================
// 群聊 1.5.0：私聊/群聊双 Tab、群卡片、建群弹窗、教程
// ============================================================

/**
 * 全局搜索结果列表（搜索时替代双 Tab 内容，跨私聊/群聊展示）。
 */
@Composable
private fun GlobalSearchResultList(
    conversations: List<Conversation>,
    messageHits: List<GlobalChatSearch.Hit>,
    query: String,
    isMultiSelect: Boolean,
    selectedIds: Set<String>,
    toggleSelection: (String) -> Unit,
    syncMultiSelect: (Boolean, Set<String>) -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenMessage: (String, String) -> Unit,
    hasListWallpaper: Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (conversations.isNotEmpty()) {
            item(key = "header_conversations", contentType = { "section" }) {
                SectionHeader(text = "会话")
            }
            items(conversations, key = { it.id }, contentType = { "conversation" }) { conv ->
                ConversationCard(
                    conversation = conv,
                    isMultiSelect = isMultiSelect,
                    isSelected = conv.id in selectedIds,
                    onTap = {
                        if (isMultiSelect) toggleSelection(conv.id) else onOpenConversation(conv.id)
                    },
                    onLongClick = {
                        if (!isMultiSelect) syncMultiSelect(true, setOf(conv.id)) else toggleSelection(conv.id)
                    },
                    hasListWallpaper = hasListWallpaper
                )
            }
        }
        if (messageHits.isNotEmpty()) {
            item(key = "header_messages", contentType = { "section" }) {
                SectionHeader(text = "消息")
            }
            items(messageHits, key = { it.message.id }, contentType = { "message_hit" }) { hit ->
                MessageHitRow(
                    hit = hit,
                    query = query,
                    onClick = { onOpenMessage(hit.conversationId, hit.message.id) }
                )
            }
        }
    }
}

/**
 * 单个列表页（私聊 / 群聊），切换 Tab 时整体淡入淡出（方案十四.7）。
 */
@Composable
private fun ChatListPage(
    conversations: List<Conversation>,
    isMultiSelect: Boolean,
    selectedIds: Set<String>,
    isGroup: Boolean,
    userAvatarUri: String?,
    memberResolver: (List<String>) -> List<Conversation>,
    toggleSelection: (String) -> Unit,
    syncMultiSelect: (Boolean, Set<String>) -> Unit,
    onOpenConversation: (String) -> Unit,
    hasListWallpaper: Boolean
) {
    if (conversations.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (isGroup) "还没有群聊\n点击右上角新建群聊" else "还没有私聊\n点击右上角新建私聊",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(conversations, key = { it.id }, contentType = { if (isGroup) "group" else "conversation" }) { conv ->
            val cardModifier = Modifier.animateItem(
                placementSpec = tween(Motion.DurationLong, easing = Motion.EasingEmphasizedDecelerate),
                fadeInSpec = tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate),
                fadeOutSpec = tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate)
            )
            val onTap = {
                if (isMultiSelect) toggleSelection(conv.id) else onOpenConversation(conv.id)
            }
            val onLongClick = {
                if (!isMultiSelect) syncMultiSelect(true, setOf(conv.id)) else toggleSelection(conv.id)
            }
            if (isGroup) {
                GroupConversationCard(
                    conversation = conv,
                    isMultiSelect = isMultiSelect,
                    isSelected = conv.id in selectedIds,
                    userAvatarUri = userAvatarUri,
                    memberResolver = memberResolver,
                    onTap = onTap,
                    onLongClick = onLongClick,
                    modifier = cardModifier,
                    hasListWallpaper = hasListWallpaper
                )
            } else {
                ConversationCard(
                    conversation = conv,
                    isMultiSelect = isMultiSelect,
                    isSelected = conv.id in selectedIds,
                    onTap = onTap,
                    onLongClick = onLongClick,
                    modifier = cardModifier,
                    hasListWallpaper = hasListWallpaper
                )
            }
        }
    }
}

/**
 * 底部「私聊 / 群聊」Tab 栏（方案十四.1-4）：
 * 两个词靠近居中排列，无滑动指示块；高亮由文字颜色表达——
 * 当前页文字高亮（浅色黑 / 深色白），滑动时颜色随滑动方向在两个词之间渐变过渡。
 */
@Composable
private fun ChatTypeTabBar(
    currentPage: Float,
    darkMode: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // 私聊高亮比例：停在 0 页为 1，滑到 1 页降为 0
    val soloHighlight = (1f - currentPage).coerceIn(0f, 1f)
    // 群聊高亮比例：停在 1 页为 1，滑回 0 页降为 0
    val groupHighlight = currentPage.coerceIn(0f, 1f)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChatTypeTabWord(
            label = "私聊",
            highlightFraction = soloHighlight,
            darkMode = darkMode,
            onClick = { onSelect(0) }
        )
        Spacer(modifier = Modifier.size(24.dp))
        ChatTypeTabWord(
            label = "群聊",
            highlightFraction = groupHighlight,
            darkMode = darkMode,
            onClick = { onSelect(1) }
        )
    }
}

@Composable
private fun ChatTypeTabWord(
    label: String,
    highlightFraction: Float,
    darkMode: Boolean,
    onClick: () -> Unit
) {
    val highlightColor = if (darkMode) Color.White else Color.Black
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val wordColor = lerp(mutedColor, highlightColor, highlightFraction)
    Box(
        modifier = Modifier
            .height(40.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = wordColor
        )
    }
}

/**
 * 群聊卡片：群名称 + 更新时间 + 头像拼合图（用户头像 + 成员头像，方案十四.5），
 * 不显示消息预览。
 */
@Composable
private fun GroupConversationCard(
    conversation: Conversation,
    isMultiSelect: Boolean,
    isSelected: Boolean,
    userAvatarUri: String?,
    memberResolver: (List<String>) -> List<Conversation>,
    onTap: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasListWallpaper: Boolean = false
) {
    val members = remember(conversation.memberConversationIds) {
        memberResolver(conversation.memberConversationIds)
    }
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
                GroupAvatarComposite(
                    userAvatarUri = userAvatarUri,
                    members = members
                )
                Spacer(modifier = Modifier.size(14.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title.ifBlank { "新群聊" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.size(2.dp))
                Text(
                    text = DateUtils.formatTimestamp(conversation.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * 头像拼合图：用户头像 + 成员头像（最多 3 个），按人数自适应宽度重叠排列。
 */
@Composable
private fun GroupAvatarComposite(
    userAvatarUri: String?,
    members: List<Conversation>
) {
    val avatarUris = buildList<String?> {
        add(userAvatarUri)
        members.take(3).forEach { add(it.persona?.aiAvatarUri) }
    }
    val overlap = 16.dp
    Box(
        modifier = Modifier.size(
            width = (28.dp.value + (avatarUris.size - 1) * overlap.value).dp,
            height = 28.dp
        )
    ) {
        avatarUris.forEachIndexed { index, uri ->
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (index * overlap.value).dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (uri != null) {
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * 新建群聊弹窗（方案二.6）：从私聊列表勾选 1～3 个成员 → 确定执行 API 测试 →
 * 群名可空（自动编号「新群聊 N」）→ 创建后进入群聊。
 */
@Composable
private fun NewGroupDialog(
    soloConversations: List<Conversation>,
    hasApiConfig: Boolean,
    onCreate: (List<String>, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    var groupName by rememberSaveable { mutableStateOf("") }

    fun toggle(id: String) {
        selected = if (id in selected) selected - id else selected + id
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建群聊") },
        text = {
            Column {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("群名称（可留空）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "选择成员（1~${QuiddityConstants.GROUP_MAX_MEMBERS} 个，确定后执行 API 测试）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.size(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(soloConversations, key = { it.id }) { conv ->
                        val checked = conv.id in selected
                        val status = when {
                            !hasApiConfig -> "API 未配置"
                            conv.userPersona.name.isBlank() -> "用户名未设置"
                            conv.persona.name.isBlank() -> "AI 名未设置"
                            else -> "可加入"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { toggle(conv.id) }
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = { toggle(conv.id) })
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (conv.persona.aiAvatarUri != null) {
                                    AsyncImage(
                                        model = conv.persona.aiAvatarUri,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.size(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = conv.title.ifBlank { "未命名会话" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "AI：${conv.persona.name.ifBlank { "未设置" }} · $status",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (status == "可加入") {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    }
                                )
                            }
                        }
                    }
                }
                if (selected.size > QuiddityConstants.GROUP_MAX_MEMBERS) {
                    Text(
                        text = "最多选择 ${QuiddityConstants.GROUP_MAX_MEMBERS} 个成员",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.isNotEmpty() && selected.size <= QuiddityConstants.GROUP_MAX_MEMBERS,
                onClick = { onCreate(selected.toList(), groupName.trim()) }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 群聊教程弹窗（方案三.8）：首次进入群聊模式列表页弹出，右下角问号可再次打开。
 */
@Composable
private fun GroupTutorialDialog(onDismiss: () -> Unit) {
    val lines = listOf(
        "发送消息后，点一下成员的头像，TA 才会回复你",
        "可以连着点好几个头像，TA 们会排队依次回复（最多 1 个在回复、2 个在排队）",
        "不点头像就没人回；想听谁说，就点谁",
        "成员管理、上下文条数等都可以在群聊设置里调整",
        "用户名、AI 名没设置或 API 测试没通过的角色不能加入群聊，会收到通知，可重试",
        "点头像没反应的可能原因：成员已被踢出、群聊没有成员、或已到排队上限"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("群聊玩法") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lines.forEachIndexed { index, line ->
                    Text(
                        text = "${index + 1}. $line",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    )
}
