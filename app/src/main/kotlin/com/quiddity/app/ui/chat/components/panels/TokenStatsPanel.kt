package com.quiddity.app.ui.chat.components.panels

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import com.quiddity.app.domain.PromptBuilder
import com.quiddity.app.util.QuiddityConstants
import com.quiddity.app.util.TokenEstimator

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
 * 会话内 Token 统计面板（参考 PC 端 chart-card 表型 UI）。
 *
 * 显示内容：
 * - 当前会话统计：对话轮数、Token 用量（估算）
 * - 人设卡统计：总字量（含系统指令）、Token 估算
 * - 上下文使用情况：已用 / 限制
 *
 * 会话压缩设置已迁出到汉堡菜单"数据 → 会话压缩"。
 *
 * @param conversation 当前会话
 * @param messages 当前会话的消息列表
 * @param onRefresh 刷新统计回调
 * @param onContextLimitChange 会话级上下文记忆轮数变更回调
 * @param onResetContextLimit 重置为当前模型分级默认值
 */
@Composable
fun TokenStatsPanel(
    conversation: Conversation?,
    messages: List<Message>,
    onRefresh: () -> Unit = {},
    onContextLimitChange: (Int) -> Unit = {},
    onResetContextLimit: () -> Unit = {}
) {
    // 计算统计数据
    val stats = remember(conversation, messages) {
        calculateStats(conversation, messages)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .animateContentSize()
    ) {
        // ===== 当前会话统计 =====
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "当前会话统计",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.size(12.dp))

                // 统计卡片行（参考 PC 端 chart-card 布局）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatsCard(
                        title = "对话轮数",
                        value = stats.rounds.toString(),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    StatsCard(
                        title = "Token 用量",
                        subtitle = "估算",
                        value = stats.sessionTokens.toString(),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }

                Spacer(modifier = Modifier.size(8.dp))

                // 上下文使用情况
                val contextUsage = if (stats.contextLimit > 0) {
                    "${stats.contextUsed} / ${stats.contextLimit} 条"
                } else {
                    "${stats.contextUsed} 条（无限制）"
                }
                StatsRow(
                    label = "上下文使用",
                    value = contextUsage
                )

                ContextLimitEditor(
                    currentLimit = stats.contextLimit,
                    onLimitChange = onContextLimitChange,
                    onResetToDefault = onResetContextLimit
                )
            }
        }

        Spacer(modifier = Modifier.size(10.dp))

        // ===== 人设卡统计 =====
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "人设卡统计",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = "含系统指令（内部规则、场景、记忆等全部系统文本）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.size(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatsCard(
                        title = "总字量",
                        subtitle = "含系统",
                        value = stats.personaCharCount.toString(),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    StatsCard(
                        title = "Token 估算",
                        value = stats.personaTokens.toString(),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }

                Spacer(modifier = Modifier.size(8.dp))

                // 人设卡各部分字量明细
                StatsRow(
                    label = "用户填写字量",
                    value = "${stats.userPersonaChars} 字"
                )
                StatsRow(
                    label = "系统指令字量",
                    value = "${stats.systemChars} 字"
                )
            }
        }

        Spacer(modifier = Modifier.size(8.dp))

        // 刷新按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text("刷新统计")
            }
        }
    }
}

/**
 * 单个统计卡片（参考 PC 端 chart-card 样式）。
 *
 * 为保持四张卡片大小完全一致，subtitle 为空时也渲染一个等高的占位文本
 * （不可见但占用空间），保证同行 + 跨行卡片高度统一。
 */
@Composable
private fun StatsCard(
    title: String,
    value: String,
    subtitle: String = "",
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            } else {
                // 占位：同字号、同行高的不可见文本
                Text(
                    text = " ",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Transparent
                )
            }
            Spacer(modifier = Modifier.size(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 标签-值行。
 */
@Composable
private fun StatsRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 会话级上下文记忆轮数编辑器。
 *
 * - 显示当前记忆轮数，支持 +/- 按钮调整
 * - 显示当前模型分级的默认值提示
 * - "重置为默认"按钮一键恢复分级默认值（80/40/12）
 * - 仅影响当前会话，不影响其他会话
 */
@Composable
private fun ContextLimitEditor(
    currentLimit: Int,
    onLimitChange: (Int) -> Unit,
    onResetToDefault: () -> Unit
) {
    Spacer(modifier = Modifier.size(12.dp))

    // 分隔线
    androidx.compose.material3.HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )

    Spacer(modifier = Modifier.size(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "记忆轮数",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 减少按钮
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        val newLimit = (currentLimit - 1).coerceAtLeast(1)
                        if (newLimit != currentLimit) onLimitChange(newLimit)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = "减少",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
            }

            // 当前值
            Text(
                text = "$currentLimit",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            // 增加按钮
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        val newLimit = (currentLimit + 1).coerceAtMost(200)
                        if (newLimit != currentLimit) onLimitChange(newLimit)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "增加",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

    Spacer(modifier = Modifier.size(6.dp))

    // 重置为默认按钮 + 说明
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "仅当前会话生效，切换模型自动重置",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        TextButton(
            onClick = onResetToDefault,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 8.dp,
                vertical = 0.dp
            )
        ) {
            Text(
                text = "重置为默认",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * 统计数据计算结果。
 */
data class SessionTokenStats(
    val rounds: Int,
    val sessionTokens: Int,
    val contextUsed: Int,
    val contextLimit: Int,
    val personaCharCount: Int,
    val personaTokens: Int,
    val userPersonaChars: Int,
    val systemChars: Int
)

/**
 * 计算会话统计数据。
 *
 * 人设卡总字量包括系统给的字量。
 * - 用户填写字量：人设字段（name/persona/character/appearance/worldBackground/desired）
 *   + 用户人设 + 场景 + 记忆
 * - 系统指令字量：段落标题（【你的名字】【身份背景】等）
 * - 总字量 = 用户填写 + 系统指令
 */
fun calculateStats(
    conversation: Conversation?,
    messages: List<Message>
): SessionTokenStats {
    if (conversation == null) {
        return SessionTokenStats(0, 0, 0, 0, 0, 0, 0, 0)
    }

    // 对话轮数 = USER 消息数
    val rounds = messages.count { it.role == Role.USER }

    // 会话 Token 总量
    val sessionTokens = messages.sumOf { it.tokenCount }

    // 上下文使用
    val contextUsed = messages.size
    val contextLimit = conversation.contextLimit

    // 人设卡统计（含系统指令）
    val systemPrompt = PromptBuilder.buildSystemPrompt(conversation)
    val systemStats = TokenEstimator.analyze(systemPrompt)

    // 用户填写部分（不含系统指令）
    val userFilledText = buildUserFilledText(conversation)
    val userStats = TokenEstimator.analyze(userFilledText)

    // 系统指令部分 = 总量 - 用户填写
    val systemChars = (systemStats.charCount - userStats.charCount).coerceAtLeast(0)

    return SessionTokenStats(
        rounds = rounds,
        sessionTokens = sessionTokens,
        contextUsed = contextUsed,
        contextLimit = contextLimit,
        personaCharCount = systemStats.charCount,
        personaTokens = systemStats.tokenEstimate,
        userPersonaChars = userStats.charCount,
        systemChars = systemChars
    )
}

/**
 * 构建用户填写的文本（不含系统指令和段落标题）。
 */
private fun buildUserFilledText(conv: com.quiddity.app.data.model.Conversation): String {
    val sb = StringBuilder()
    val persona = conv.persona
    if (persona.name.isNotBlank()) sb.append(persona.name).append("\n")
    if (persona.persona.isNotBlank()) sb.append(persona.persona).append("\n")
    if (persona.character.isNotBlank()) sb.append(persona.character).append("\n")
    if (persona.appearance.isNotBlank()) sb.append(persona.appearance).append("\n")
    if (persona.worldBackground.isNotBlank()) sb.append(persona.worldBackground).append("\n")
    if (persona.desired.isNotBlank()) sb.append(persona.desired).append("\n")

    val user = conv.userPersona
    if (user.name.isNotBlank()) sb.append(user.name).append("\n")
    if (user.identity.isNotBlank()) sb.append(user.identity).append("\n")
    if (user.gender.isNotBlank()) sb.append(user.gender).append("\n")
    if (user.age.isNotBlank()) sb.append(user.age).append("\n")
    if (user.appearance.isNotBlank()) sb.append(user.appearance).append("\n")

    if (conv.scene.isNotBlank()) sb.append(conv.scene).append("\n")
    if (conv.memory.isNotBlank()) sb.append(conv.memory).append("\n")

    return sb.toString()
}
