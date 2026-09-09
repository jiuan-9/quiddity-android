package com.quiddity.app.ui.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.ui.chat.components.panels.TokenStatsPanel
import com.quiddity.app.ui.components.ActiveMessagePermissionCard
import com.quiddity.app.ui.components.ExpandableText
import com.quiddity.app.ui.settings.SettingsViewModel
import com.quiddity.app.util.QuiddityConstants


// ==================== 主菜单 ====================

@Composable
internal fun MainMenuContent(
    conversation: Conversation?,
    messages: List<com.quiddity.app.data.model.Message>,
    currentTier: com.quiddity.app.domain.ApiCatalogManager.ModelTier,
    settings: com.quiddity.app.data.model.AppSettings,
    onPanelSelected: (HamburgerPanel) -> Unit,
    onPersonaClick: () -> Unit,
    onDismiss: () -> Unit,
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onClearSettings: () -> Unit,
    onExportPersona: () -> Unit,
    onImportPersona: () -> Unit,
    onExportConversation: () -> Unit,
    onImportConversation: () -> Unit,
    onContextLimitChange: (Int) -> Unit,
    onResetContextLimit: () -> Unit,
    onMemoryBankEnabledChange: (Boolean) -> Unit,
    onMemoryBankRoundsChange: (Int) -> Unit,
    onCompressionClick: () -> Unit,
    onClearMessages: () -> Unit,
    onActiveMessageChange: (Boolean) -> Unit,
    onViewTimeLibrary: () -> Unit,
    onOpenSearchChat: () -> Unit,
    webSearchSupported: Boolean,
    currentModelId: String,
    onTemperatureChange: (Double?) -> Unit,
    onThinkingEnabledChange: (Boolean) -> Unit,
    onThinkingDepthChange: (String) -> Unit,
    onWebSearchChange: (Boolean) -> Unit
) {
    var showTemperatureEditor by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 顶部：标题 + 关闭
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "会话设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Close, "关闭",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.size(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 外观
            MenuSectionCard(title = "外观") {
            // 会话内的"外观"只是全局主题的一个快捷入口，状态直接来自 settings.darkMode，
            // 切换时调用 SettingsViewModel.setDarkMode，与设置面板中的总开关保持一致。
            ToggleMenuRow(
                title = "深色模式",
                subtitle = if (darkMode) "当前：暗色" else "当前：亮色",
                checked = darkMode,
                onCheckedChange = onDarkModeChange,
            )
            // - 仅对当前会话生效，不影响其他会话
            // - 持久化到 conversation.wallpaperUri
            MenuRow(
                title = "会话壁纸",
                subtitle = if (conversation?.wallpaperUri != null) "已设置" else "未设置",
                onClick = { onPanelSelected(HamburgerPanel.Wallpaper) },
            )

            // 人设
            }
            MenuSectionCard(title = "人设") {
                if (conversation?.type == ConversationType.AGENT) {
                    // Agent：人设栏只有一个「选择角色」框（角色 = AI 人设 + 用户人设，点选即用）
                    val aiName = conversation?.persona?.name
                    MenuRow(
                        title = "选择角色",
                        subtitle = if (!aiName.isNullOrBlank()) "当前：$aiName" else "从角色库点选",
                        onClick = onPersonaClick,
                        expandableSubtitle = true,
                        subtitleColor = if (!aiName.isNullOrBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        }
                    )
                } else {
                    MenuRow(
                        title = "快速设定",
                        subtitle = "描述你想要的人设，AI 一次性生成并填入",
                        onClick = { onPanelSelected(HamburgerPanel.QuickSetup) },
                        expandableSubtitle = true
                    )
                    val aiPersonaName = conversation?.persona?.name
                    val aiPersonaSet = !aiPersonaName.isNullOrBlank()
                    MenuRow(
                        title = "AI 人设",
                        subtitle = if (aiPersonaSet) "AI: $aiPersonaName" else "未设置",
                        onClick = onPersonaClick,
                        expandableSubtitle = true,
                        subtitleColor = if (aiPersonaSet) {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        }
                    )
                    MenuRow(
                        title = "用户人设",
                        subtitle = if (conversation?.userPersona?.name?.isNotBlank() == true)
                            "用户: ${conversation.userPersona.name}" else "未设置",
                        onClick = { onPanelSelected(HamburgerPanel.UserPersona) },
                        expandableSubtitle = true
                    )
                    MenuRow(
                        title = "场景设置",
                        subtitle = if (conversation?.scene?.isNotBlank() == true)
                            conversation.scene.trim() else "未设置",
                        onClick = { onPanelSelected(HamburgerPanel.Scene) },
                        expandableSubtitle = true
                    )
                }
            // 模型配置
            }
            MenuSectionCard(title = "模型配置") {
            MenuRow(
                title = "选择模型配置",
                subtitle = settings.catalog
                    .firstOrNull { it.id == (conversation?.apiCatalogId ?: settings.activeCatalogId) }
                    ?.let { "当前：${it.name} · ${it.apiModel}" } ?: "未选择",
                onClick = { onPanelSelected(HamburgerPanel.ApiSelector) },
                expandableSubtitle = true
            )
            MenuRow(
                title = "管理模型配置",
                subtitle = "添加、编辑或删除模型配置",
                onClick = { onPanelSelected(HamburgerPanel.ApiEditor) },
            )
            Spacer(modifier = Modifier.size(4.dp))
            // 内部思考：提示词引导模型输出【思考】/【回答】，任意模型可用
            ThinkingMenuRow(
                enabled = conversation?.thinkingEnabled == true,
                supported = true,
                depth = conversation?.thinkingDepth ?: QuiddityConstants.THINKING_DEPTH_SHALLOW,
                onThinkingChange = onThinkingEnabledChange,
                onDepthChange = onThinkingDepthChange
            )
            Spacer(modifier = Modifier.size(4.dp))
            // 会话级温度：控制本会话回复的随机性/创造性（跟随全局默认）
            val temperatureSubtitle = if (conversation?.temperature != null) {
                "本会话 " + String.format(java.util.Locale.US, "%.1f", conversation.temperature) +
                    " · 默认 " + String.format(java.util.Locale.US, "%.1f", settings.globalTemperature)
            } else {
                "跟随默认（" + String.format(java.util.Locale.US, "%.1f", settings.globalTemperature) +
                    "）· 控制回复的发散程度"
            }
            ExpandableMenuGroup(
                title = "温度",
                subtitle = temperatureSubtitle,
                expanded = showTemperatureEditor,
                onClick = { showTemperatureEditor = !showTemperatureEditor }
            ) {
                TemperatureEditorPanel(
                    current = conversation?.temperature,
                    globalDefault = settings.globalTemperature,
                    onTemperatureChange = onTemperatureChange
                )
            }
            Spacer(modifier = Modifier.size(4.dp))
            WebSearchMenuRow(
                enabled = conversation?.webSearchEnabled == true,
                supported = webSearchSupported,
                currentModelId = currentModelId,
                onWebSearchChange = onWebSearchChange
            )
            }

            MenuSectionCard(title = "统计") {
            TokenStatsPanel(
                conversation = conversation,
                messages = messages,
                onContextLimitChange = onContextLimitChange,
                onResetContextLimit = onResetContextLimit
            )

            // 主动消息（对应算法文档 2.2 会话级开启）
            }
            // Agent 会话不支持主动消息（时间库），不展示该区块
            if (conversation?.type != ConversationType.AGENT) {
            MenuSectionCard(title = "主动消息") {
            ToggleMenuRow(
                title = "主动消息",
                subtitle = if (!settings.proactiveMessageEnabled) {
                    "总设置未开启，请先到总设置开启"
                } else if (conversation?.activeMessageEnabled == true) {
                    "已开启：AI 按时间库主动发消息"
                } else {
                    "开启后立即生成当日时间库"
                },
                checked = conversation?.activeMessageEnabled == true,
                // 全局总开关关闭：本会话开关置灰不可操作（1.6.2）
                enabled = settings.proactiveMessageEnabled,
                onCheckedChange = onActiveMessageChange,
            )
            // 系统条件引导：会话级开启后展示精确闹钟 / 电池优化状态与一键跳转
            if (conversation?.activeMessageEnabled == true) {
                Spacer(modifier = Modifier.size(8.dp))
                ActiveMessagePermissionCard(
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.size(4.dp))
                MenuRow(
                    title = "查看时间库",
                    subtitle = "查看本会话今日时间库（按 AI 设定可能需要密码）",
                    onClick = onViewTimeLibrary,
                )
            }

            // 数据
            }
            }
            MenuSectionCard(title = "数据") {
            MenuRow(
                title = "会话压缩",
                subtitle = if (conversation?.memoryBankEnabled == true) {
                    "已启用 · 每 ${conversation.memoryBankRounds} 轮压缩"
                } else {
                    "未启用（点击进入配置）"
                },
                onClick = onCompressionClick,
                trailingIcon = Icons.Filled.Compress,
                trailingTint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.size(4.dp))
            // 数据一览：当前轮数 + 距离下一次压缩还有多少轮（灰色小字）
            val userRounds = messages.count { it.role == com.quiddity.app.data.model.Role.USER }
            val roundsToCompression = conversation?.let { conv ->
                if (conv.memoryBankEnabled) {
                    (conv.memoryBankRounds - (userRounds - conv.lastCompressedAtRound)).coerceAtLeast(0)
                } else {
                    null
                }
            }
            Text(
                text = "当前：${userRounds} 轮 · 距离下一次压缩还有：${
                    roundsToCompression?.let { "${it} 轮" } ?: "未启用"
                }",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )
            Spacer(modifier = Modifier.size(4.dp))
            // Agent 模式不提供角色卡导出/导入：人设来自角色库唯一角色卡（uid）直接引用
            if (conversation?.type != ConversationType.AGENT) {
                ExportImportCard(
                    title = "角色卡",
                    subtitle = "导出或导入当前会话「人设」一栏的全部设置（含快速设定内容）",
                    onExport = onExportPersona,
                    onImport = onImportPersona,
                )
            }
            Spacer(modifier = Modifier.size(4.dp))
            ExportImportCard(
                title = "对话记录",
                subtitle = "导出或导入当前会话的全部数据",
                onExport = onExportConversation,
                onImport = onImportConversation,
            )
            Spacer(modifier = Modifier.size(4.dp))
            MenuRow(
                title = "查找聊天记录",
                subtitle = "按关键词搜索本会话的历史消息",
                onClick = onOpenSearchChat,
            )

            // 危险操作：清空设置放在最底部并加感叹号，以示区别
            Spacer(modifier = Modifier.size(16.dp))
            }
            MenuSectionCard(title = "危险操作") {
            MenuRow(
                title = "清空会话记录",
                subtitle = "删除全部消息并重置压缩对话（仅影响当前会话，保留人设/场景/记忆/壁纸）",
                onClick = onClearMessages,
                trailingIcon = Icons.Filled.DeleteSweep,
                trailingTint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.size(4.dp))
            MenuRow(
                title = "清空会话设置",
                subtitle = "重置当前会话的人设/用户/场景/记忆（不影响消息记录）",
                onClick = onClearSettings,
                trailingIcon = Icons.Filled.Warning,
                trailingTint = MaterialTheme.colorScheme.error
            )
            }
        }
    }

}

@Composable
internal fun MenuSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    // 大分区默认收起，点头部展开/收起（统计分区内容样式保持不变）
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            // 毛玻璃半透明卡片：与总设置一色透明，无描边
            .clip(RoundedCornerShape(20.dp))
            .background(com.quiddity.app.ui.components.glassCardColor())
            .padding(horizontal = 2.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { expanded = !expanded }
                .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                content()
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

/**
 * 可展开菜单组：把「设置行 + 展开的子面板」框进同一个容器，
 * 用连续边框表明子面板归属于上方这一行（母设置框），避免展开面板看起来是独立悬浮卡片。
 */
@Composable
private fun ExpandableMenuGroup(
    title: String,
    subtitle: String = "",
    expanded: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
            ) {
                // 行与子面板之间的连接线：强调两者同属一个设置框
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(com.quiddity.app.ui.components.glassCardBorderColor())
                )
                Spacer(modifier = Modifier.size(10.dp))
                content()
            }
        }
    }
}

@Composable
internal fun MenuRow(
    title: String,
    subtitle: String = "",
    onClick: () -> Unit,
    expandableSubtitle: Boolean = false,
    trailingIcon: ImageVector? = null,
    trailingTint: androidx.compose.ui.graphics.Color? = null,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    subtitleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
) {
    // Box 替代 Surface：行内无 elevation 需求，Box+background+clip 跳过 Surface 的 CompositionLocalProvider 开销
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = titleColor
                )
                if (subtitle.isNotEmpty()) {
                    if (expandableSubtitle) {
                        ExpandableText(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            maxCollapsedLines = 1
                        )
                    } else {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = subtitleColor
                        )
                    }
                }
            }
            Icon(
                imageVector = trailingIcon ?: Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = trailingTint ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
