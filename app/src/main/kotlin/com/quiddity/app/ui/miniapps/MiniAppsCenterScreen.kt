package com.quiddity.app.ui.miniapps

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.rounded.Apps
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import com.quiddity.app.ui.theme.Motion
import kotlinx.coroutines.delay

/*
 * 小应用中心：收藏 / 全部 两个分区。
 * 视觉：顶部下拉把手 + 大标题 + 圆角卡片，保持克制的质感。
 */

@Composable
fun MiniAppsCenterScreen(
    favorites: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onOpenApp: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val density = LocalDensity.current
    var dragUp by remember { mutableStateOf(0f) }
    // 整页"下拉关闭"：列表在顶部时向下拉超过阈值即返回主页（微信式），头部上滑仍可用
    val listState = rememberLazyListState()
    var pullDown by remember { mutableStateOf(0f) }
    var pullClosed by remember { mutableStateOf(false) }
    val atListTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    val closeConnection = remember(atListTop, onBack, density) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source != NestedScrollSource.UserInput || !atListTop || pullClosed) return Offset.Zero
                val dyDp = available.y / density.density
                if (dyDp > 0) {
                    pullDown = (pullDown + dyDp).coerceAtMost(160f)
                    return Offset(0f, available.y)
                }
                if (dyDp < 0 && pullDown > 0f) {
                    pullDown = (pullDown + dyDp).coerceAtLeast(0f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            // 松手时再判断：避免拖动中途 pop 导致界面异常
            override suspend fun onPostFling(
                consumed: Velocity,
                available: Velocity
            ): Velocity {
                if (pullDown >= 80f && !pullClosed) {
                    pullClosed = true
                    onBack()
                }
                pullDown = 0f
                return Velocity.Zero
            }
        }
    }
    LaunchedEffect(pullClosed) {
        if (pullClosed) {
            pullDown = 0f
            pullClosed = false
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .nestedScroll(closeConnection)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 16.dp)
                .pointerInput(onBack) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            dragUp = (dragUp + dragAmount).coerceAtMost(0f)
                        },
                        onDragEnd = {
                            if (dragUp <= -with(density) { 60.dp.toPx() }) {
                                onBack()
                            }
                            dragUp = 0f
                        },
                        onDragCancel = { dragUp = 0f }
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
            )
            Spacer(Modifier.size(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Apps,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "小应用",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, bottom = 28.dp
            )
        ) {
            item(key = "fav_header") {
                SectionHeader("收藏小应用")
            }
            if (favorites.isEmpty()) {
                item(key = "fav_empty") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            text = "还没有收藏。点击卡片右侧的星标，常用小应用会出现在这里。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                item(key = "fav_row") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 8.dp)
                    ) {
                        itemsIndexed(MiniAppRegistry.all.filter { it.id in favorites }, key = { _, app -> app.id }) { index, app ->
                            RoundAppIcon(
                                app = app,
                                index = index,
                                isFavorite = true,
                                onToggleFavorite = { onToggleFavorite(app.id) },
                                onClick = { onOpenApp(app.id) }
                            )
                        }
                    }
                }
            }

            item(key = "all_header") {
                SectionHeader("全部小应用")
            }
            item(key = "all_row") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(end = 8.dp)
                ) {
                    itemsIndexed(MiniAppRegistry.all, key = { _, app -> app.id }) { index, app ->
                        RoundAppIcon(
                            app = app,
                            index = index,
                            isFavorite = app.id in favorites,
                            onToggleFavorite = { onToggleFavorite(app.id) },
                            onClick = { onOpenApp(app.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun RoundAppIcon(
    app: MiniApp,
    index: Int = 0,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    // 交错入场：卡片依次淡入 + 轻微放大（总时长控制在 400ms 内）
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 40L)
        appear.animateTo(1f, tween(Motion.DurationMedium, easing = Motion.EasingEmphasizedDecelerate))
    }
    // 收藏星标：切换时弹一下（跳过首次合成）
    val starScale = remember { Animatable(1f) }
    var firstStar by remember { mutableStateOf(true) }
    LaunchedEffect(isFavorite) {
        if (firstStar) {
            firstStar = false
            return@LaunchedEffect
        }
        starScale.snapTo(1f)
        starScale.animateTo(1.35f, tween(90, easing = Motion.EasingEmphasizedDecelerate))
        starScale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 600f))
    }
    Column(
        modifier = Modifier
            .width(92.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp)
            .graphicsLayer {
                alpha = appear.value
                val s = 0.85f + 0.15f * appear.value
                scaleX = s
                scaleY = s
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier.size(64.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = app.icon,
                    contentDescription = app.name,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(30.dp)
                )
            }
            // 收藏星标：圆形图标右上角的小徽章
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        CircleShape
                    )
                    .clickable(onClick = onToggleFavorite),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isFavorite) "取消收藏" else "收藏",
                    tint = if (isFavorite) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    },
                    modifier = Modifier
                        .size(13.dp)
                        .graphicsLayer {
                            scaleX = starScale.value
                            scaleY = starScale.value
                        }
                )
            }
        }
        Text(
            text = app.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MiniAppMissingScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "小应用不存在或已下架",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(12.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary,
                onClick = onBack
            ) {
                Text(
                    text = "返回",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp)
                )
            }
        }
    }
}
