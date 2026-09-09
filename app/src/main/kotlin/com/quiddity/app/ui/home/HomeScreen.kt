package com.quiddity.app.ui.home

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
import com.quiddity.app.ui.agent.AgentTab
import com.quiddity.app.ui.agent.AgentSettingsScreen
import com.quiddity.app.ui.components.AiAvatar
import com.quiddity.app.ui.components.ConfirmDialog
import com.quiddity.app.ui.settings.SettingsBottomSheet
import com.quiddity.app.ui.settings.SettingsViewModel
import com.quiddity.app.ui.theme.Motion
import com.quiddity.app.util.DateUtils
import com.quiddity.app.util.WallpaperContrast
import com.quiddity.app.util.rememberWallpaperBrightness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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


// 当前规则：壁纸存在时启用毛玻璃质感和顶部栏半透明；暗化遮罩保证可读性。
/** 下拉提示松开后自动收回的延迟（无新滚动事件即视为已松手）。 */
private const val PULL_HINT_RESET_MS = 600L

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    settingsViewModel: SettingsViewModel,
    userAvatarUri: String?,
    onOpenMiniApps: () -> Unit = {},
    onOpenConversation: (String) -> Unit,
    onOpenMessage: (String, String) -> Unit
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val newConversationId by viewModel.newConversationId.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showAgentSettings by rememberSaveable { mutableStateOf(false) }

    // ===== 私聊 / 群聊双 Tab（方案十四） =====
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    val pagerScope = rememberCoroutineScope()
    var showGroupTutorial by rememberSaveable { mutableStateOf(false) }
    var showAgentTutorial by rememberSaveable { mutableStateOf(false) }

    // 首次进入群聊页弹教程（记录已看过，只弹一次，方案十.9）
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 2 && !settings.groupTutorialSeen) {
            showGroupTutorial = true
            settingsViewModel.setGroupTutorialSeen(true)
        } else if (pagerState.currentPage == 0 && !settings.agentTutorialSeen) {
            showAgentTutorial = true
            settingsViewModel.setAgentTutorialSeen(true)
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
    // ===== 列表壁纸自动对比度：采样亮度，自动叠加保证文字可读的遮罩基线 =====
    val listWallpaperBrightness = rememberWallpaperBrightness(listWallpaperUri)
    val listWallpaperScrim = remember(
        listWallpaperBrightness, listWallpaperDarken, settings.darkMode
    ) {
        val alpha = WallpaperContrast.effectiveScrimAlpha(
            listWallpaperBrightness,
            listWallpaperDarken,
            settings.darkMode
        )
        if (settings.darkMode) Color.Black.copy(alpha = alpha)
        else Color.White.copy(alpha = alpha)
    }

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
    val agentConversations by viewModel.agentConversations.collectAsStateWithLifecycle()

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
    val agentFiltered = remember(agentConversations, searchQuery) { filterByQuery(agentConversations) }

    // ===== 搜索会话仅对当前模式有效（方案十四 + 需求） =====
    val currentTab = pagerState.currentPage
    val agentIds = remember(agentConversations) { agentConversations.map { it.id }.toSet() }
    val soloIds = remember(soloConversations) { soloConversations.map { it.id }.toSet() }
    val groupIds = remember(groupConversations) { groupConversations.map { it.id }.toSet() }
    val scopedMessageHits = remember(messageHits, currentTab, agentIds, soloIds, groupIds) {
        val ids = when (currentTab) {
            0 -> agentIds
            1 -> soloIds
            else -> groupIds
        }
        messageHits.filter { it.conversationId in ids }
    }
    val searchScopeConversations = when (currentTab) {
        0 -> agentFiltered
        1 -> soloFiltered
        else -> groupFiltered
    }

    // ===== 顶部 UI 模式切换"重新加载"动画：整体淡出 → 淡入并轻微下落复位。
    // 由 graphicsLayer 在 draw phase 驱动（零重组）；速度放缓避免闪动。 =====
    val topBarReloadAlpha = remember { Animatable(1f) }
    val topBarReloadOffsetY = remember { Animatable(0f) }
    var firstTopBarRender by remember { mutableStateOf(true) }
    LaunchedEffect(pagerState.currentPage) {
        if (firstTopBarRender) {
            firstTopBarRender = false
            return@LaunchedEffect
        }
        topBarReloadAlpha.snapTo(1f)
        // 淡出
        topBarReloadAlpha.animateTo(0f, tween(220, easing = Motion.EasingStandard))
        // 预置轻微上移，淡入的同时下落复位，模拟内容重新加载
        topBarReloadOffsetY.snapTo(-12f)
        coroutineScope {
            launch {
                topBarReloadAlpha.animateTo(1f, tween(320, easing = Motion.EasingStandard))
            }
            launch {
                topBarReloadOffsetY.animateTo(0f, tween(320, easing = Motion.EasingStandard))
            }
        }
    }

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

    // ===== 下拉进入小应用 =====
    // 适配双 Tab：私聊/群聊各自维护列表状态，按当前页判断是否在顶部；
    // 列表在顶部时下拉手势被拦截并跟手显示"小应用"指示器，超过阈值进入小应用中心。
    val density = LocalDensity.current
    val maxPullDp = 150f
    val thresholdDp = 108f
    var pullDp by remember { mutableFloatStateOf(0f) }
    var pullTriggered by remember { mutableStateOf(false) }
    // 时间闸：一次下拉手势只推一次小应用中心，防止手指未抬起时跨阈值重复 navigate
    var lastOpenTriggerMs by remember { mutableLongStateOf(0L) }
    val agentListState = rememberLazyListState()
    val soloListState = rememberLazyListState()
    val groupListState = rememberLazyListState()
    val atTop by remember(conversations, searchQuery, pagerState.currentPage, isMultiSelect) {
        derivedStateOf {
            val pageEmpty = when (pagerState.currentPage) {
                0 -> agentFiltered.isEmpty()
                1 -> soloFiltered.isEmpty()
                else -> groupFiltered.isEmpty()
            }
            val pageAtTop = when (pagerState.currentPage) {
                0 -> agentListState.firstVisibleItemIndex == 0 && agentListState.firstVisibleItemScrollOffset == 0
                1 -> soloListState.firstVisibleItemIndex == 0 && soloListState.firstVisibleItemScrollOffset == 0
                else -> groupListState.firstVisibleItemIndex == 0 && groupListState.firstVisibleItemScrollOffset == 0
            }
            !isMultiSelect && searchQuery.isBlank() && (
                isLoading || conversations.isEmpty() || pageEmpty || pageAtTop
            )
        }
    }
    val pullConnection = remember(atTop, onOpenMiniApps, density) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source != NestedScrollSource.UserInput || !atTop) return Offset.Zero
                val dyDp = available.y / density.density
                if (dyDp > 0 && !pullTriggered) {
                    pullDp = (pullDp + dyDp).coerceAtMost(maxPullDp)
                    if (pullDp >= thresholdDp) {
                        val now = System.currentTimeMillis()
                        if (now - lastOpenTriggerMs > 800L) {
                            lastOpenTriggerMs = now
                            pullTriggered = true
                            onOpenMiniApps()
                        }
                    }
                    return Offset(0f, available.y)
                }
                if (dyDp < 0 && pullDp > 0f) {
                    pullDp = (pullDp + dyDp).coerceAtLeast(0f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
        }
    }
    // 空状态（无会话/搜索无结果）下没有可滚动子组件，nestedScroll 收不到手势，
    // 这里用根层指针手势兜底：仅在没有列表内容时启用，不与 LazyColumn 抢手势。
    val pageEmpty = when (pagerState.currentPage) {
        0 -> agentFiltered.isEmpty()
        1 -> soloFiltered.isEmpty()
        else -> groupFiltered.isEmpty()
    }
    val noScrollContent = isLoading || conversations.isEmpty() || pageEmpty ||
        (searchQuery.isNotBlank() && searchScopeConversations.isEmpty() && scopedMessageHits.isEmpty())
    val usePointerPull = atTop && noScrollContent
    LaunchedEffect(pullTriggered) {
        if (pullTriggered) {
            pullDp = 0f
            pullTriggered = false
        }
    }
    // 下拉提示回弹：手指松开（无新滚动事件）后自动收回，避免"继续下拉"提示常驻
    LaunchedEffect(pullDp, pullTriggered) {
        if (pullDp > 0f && !pullTriggered) {
            delay(PULL_HINT_RESET_MS)
            if (pullDp > 0f && !pullTriggered) {
                pullDp = 0f
            }
        }
    }

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

    // 当前 Tab 的会话列表（多选「全选/反选」只作用于当前页，避免跨 Tab 误删其它会话）
    val currentTabIds = when (pagerState.currentPage) {
        0 -> agentFiltered.map { it.id }.toSet()
        1 -> soloFiltered.map { it.id }.toSet()
        else -> groupFiltered.map { it.id }.toSet()
    }

    fun selectAll() {
        syncMultiSelect(true, currentTabIds)
    }

    fun invertSelection() {
        val newIds = currentTabIds - selectedIds
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(pullConnection)
            .pointerInput(usePointerPull, pullTriggered, onOpenMiniApps, density) {
                if (!usePointerPull) return@pointerInput
                detectVerticalDragGestures(
                    onDragEnd = { pullDp = 0f },
                    onDragCancel = { pullDp = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        if (pullTriggered) return@detectVerticalDragGestures
                        val dyDp = dragAmount / density.density
                        pullDp = (pullDp + dyDp).coerceIn(0f, maxPullDp)
                        if (pullDp >= thresholdDp) {
                            val now = System.currentTimeMillis()
                            if (now - lastOpenTriggerMs > 800L) {
                                lastOpenTriggerMs = now
                                pullTriggered = true
                                onOpenMiniApps()
                            }
                        }
                    }
                )
            }
    ) {
        // 壁纸层（铺满全屏，在所有内容之下）
        // 注：hasListWallpaper 已包含 listWallpaperUri != null 判断
        if (hasListWallpaper) {
            AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(listWallpaperUri)
                    .size(1080)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // 暗化遮罩：确保上层文字可读
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(listWallpaperScrim)
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
                        totalCount = currentTabIds.size,
                        onBack = { exitMultiSelect() },
                        onSelectAll = { selectAll() },
                        onInvert = { invertSelection() },
                        onDelete = { pendingDeleteIds = selectedIds.toList() },
                        hasListWallpaper = hasListWallpaper
                    )
                } else {
                    // 顶部三个 UI（头像/搜索/新建）在模式切换时做一次快速
                    // 加载脉动（alpha 0.25→1），表达"重新加载"，不整屏闪动
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = topBarReloadAlpha.value
                                translationY = topBarReloadOffsetY.value
                            }
                    ) {
                        HomeTopBar(
                            userAvatarUri = userAvatarUri,
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it },
                            onSettingsClick = {
                                // Agent 页：设置直接在会话列表上方滑出（与总设置同款底部弹层）
                                if (pagerState.currentPage == 0) showAgentSettings = true else showSettings = true
                            },
                            onNewConversation = {
                                when (pagerState.currentPage) {
                                    0 -> viewModel.createAgentConversation()
                                    1 -> viewModel.createConversation()
                                    else -> viewModel.createGroupConversation()
                                }
                            },
                            hasListWallpaper = hasListWallpaper
                        )
                    }
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
                                // 多选模式下禁用左右滑动：避免把当前 Tab 的选中项带到他 Tab 造成误删
                                userScrollEnabled = !isMultiSelect,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                if (page == 0) {
                                    AgentTab(
                                        conversations = agentFiltered,
                                        listState = agentListState,
                                        isMultiSelect = isMultiSelect,
                                        selectedIds = selectedIds,
                                        toggleSelection = ::toggleSelection,
                                        syncMultiSelect = ::syncMultiSelect,
                                        onOpenConversation = onOpenConversation,
                                        hasListWallpaper = hasListWallpaper,
                                        newConversationId = newConversationId
                                    )
                                } else if (page == 1) {
                                    ChatListPage(
                                        conversations = soloFiltered,
                                        listState = soloListState,
                                        isMultiSelect = isMultiSelect,
                                        selectedIds = selectedIds,
                                        isGroup = false,
                                        userAvatarUri = userAvatarUri,
                                        memberResolver = viewModel::memberConversations,
                                        toggleSelection = ::toggleSelection,
                                        syncMultiSelect = ::syncMultiSelect,
                                        onOpenConversation = onOpenConversation,
                                        hasListWallpaper = hasListWallpaper,
                                        newConversationId = newConversationId
                                    )
                                } else {
                                    ChatListPage(
                                        conversations = groupFiltered,
                                        listState = groupListState,
                                        isMultiSelect = isMultiSelect,
                                        selectedIds = selectedIds,
                                        isGroup = true,
                                        userAvatarUri = userAvatarUri,
                                        memberResolver = viewModel::memberConversations,
                                        toggleSelection = ::toggleSelection,
                                        syncMultiSelect = ::syncMultiSelect,
                                        onOpenConversation = onOpenConversation,
                                        hasListWallpaper = hasListWallpaper,
                                        newConversationId = newConversationId
                                    )
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
                    pagerState = pagerState,
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

        // 下拉指示器：跟手出现的小应用入口
        AnimatedVisibility(
            visible = pullDp > 0f && !pullTriggered,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            enter = fadeIn(tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate)) +
                scaleIn(
                    initialScale = 0.85f,
                    animationSpec = tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate)
                ),
            exit = fadeOut(tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate)) +
                scaleOut(
                    targetScale = 0.92f,
                    animationSpec = tween(Motion.DurationShort, easing = Motion.EasingEmphasizedAccelerate)
                )
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "小应用",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (pullDp >= thresholdDp) "松开进入" else "继续下拉",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ===== 群聊教程问号按钮（群聊模式淡入，随时可再开教程，方案三.8） =====
        androidx.compose.animation.AnimatedVisibility(
            visible = pagerState.currentPage == 2 && !isMultiSelect && searchQuery.isBlank(),
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

    // ===== Agent 设置：与总设置同款，直接在会话列表上方滑出 =====
    if (showAgentSettings) {
        AgentSettingsScreen(
            settingsViewModel = settingsViewModel,
            onBack = { showAgentSettings = false }
        )
    }

    if (showGroupTutorial) {
        GroupTutorialDialog(onDismiss = { showGroupTutorial = false })
    }

    if (showAgentTutorial) {
        AgentTutorialDialog(onDismiss = { showAgentTutorial = false })
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
                var referenced = emptyList<String>()
                viewModel.deleteConversations(
                    ids,
                    onReferencedByGroups = { referenced = it }
                )
                if (referenced.isNotEmpty()) {
                    // 保留完整选择集，二次确认后全部删除（含被群聊引用的）
                    pendingReferencedDelete = ids
                }
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
