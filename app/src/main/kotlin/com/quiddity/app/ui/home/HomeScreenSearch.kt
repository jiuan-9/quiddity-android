package com.quiddity.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.domain.GlobalChatSearch
import com.quiddity.app.util.DateUtils


@Composable
internal fun SearchConversationBar(
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
internal fun SearchEmptyContent(query: String) {
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

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
internal fun MessageHitRow(
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

@Composable
internal fun GlobalSearchResultList(
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

@Composable
internal fun ChatTypeTabBar(
    pagerState: PagerState,
    darkMode: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChatTypeTabWord(
            label = "Agent",
            page = 0,
            pagerState = pagerState,
            darkMode = darkMode,
            onClick = { onSelect(0) }
        )
        Spacer(modifier = Modifier.size(24.dp))
        ChatTypeTabWord(
            label = "私聊",
            page = 1,
            pagerState = pagerState,
            darkMode = darkMode,
            onClick = { onSelect(1) }
        )
        Spacer(modifier = Modifier.size(24.dp))
        ChatTypeTabWord(
            label = "群聊",
            page = 2,
            pagerState = pagerState,
            darkMode = darkMode,
            onClick = { onSelect(2) }
        )
    }
}

@Composable
internal fun ChatTypeTabWord(
    label: String,
    page: Int,
    pagerState: PagerState,
    darkMode: Boolean,
    onClick: () -> Unit
) {
    val highlightColor = if (darkMode) Color.White else Color.Black
    Box(
        modifier = Modifier
            .height(40.dp)
            // 性能：高亮随滑动进度用 graphicsLayer alpha 在 draw phase 插值，
            // 滑动过程中零重组（视觉上等效于颜色从灰渐变到高亮）
            .graphicsLayer {
                val continuous = pagerState.currentPage + pagerState.currentPageOffsetFraction
                val fraction = (1f - kotlin.math.abs(continuous - page)).coerceIn(0f, 1f)
                alpha = 0.55f + 0.45f * fraction
            }
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
            color = highlightColor
        )
    }
}

@Composable
internal fun ChatListPage(
    conversations: List<Conversation>,
    listState: LazyListState,
    isMultiSelect: Boolean,
    selectedIds: Set<String>,
    isGroup: Boolean,
    emptyText: String? = null,
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
                text = emptyText ?: if (isGroup) "还没有群聊\n点击右上角新建群聊" else "还没有私聊\n点击右上角新建私聊",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
        return
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(conversations, key = { it.id }, contentType = { if (isGroup) "group" else "conversation" }) { conv ->
            // 性能：去掉 per-item 动画修饰（animateItem），滚动/增删零动画开销
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
                    hasListWallpaper = hasListWallpaper
                )
            } else {
                ConversationCard(
                    conversation = conv,
                    isMultiSelect = isMultiSelect,
                    isSelected = conv.id in selectedIds,
                    onTap = onTap,
                    onLongClick = onLongClick,
                    hasListWallpaper = hasListWallpaper
                )
            }
        }
    }
}
