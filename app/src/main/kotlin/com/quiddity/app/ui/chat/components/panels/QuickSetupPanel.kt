package com.quiddity.app.ui.chat.components.panels

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.quiddity.app.domain.ApiCatalogManager
import com.quiddity.app.domain.QuickSetupTier
import com.quiddity.app.ui.components.ConfirmDialog
import com.quiddity.app.ui.components.QuiddityTextField
import com.quiddity.app.ui.theme.Motion
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
 * 快速设定子面板。
 *
 * 流程：
 * 1. 顶部档位指示器（粗略/具体/全面，与模型等级锁定，仅当前档可用）；
 * 2. 用户填写人设描述 → 点击「设定」；
 * 3. 加载弹窗（失败提示「生成失败，请重试 / API 未配置」）；
 * 4. 结果预览弹窗（可编辑全部返回内容）→「填入」覆盖各设置项 /「取消」退出。
 *
 * 填入前若检测到已有 persona/userPersona/scene/memory 内容，弹确认框二次确认。
 */
@Composable
fun QuickSetupPanel(
    currentTier: ApiCatalogManager.ModelTier,
    hasExistingContent: Boolean,
    onGenerate: suspend (String, QuickSetupTier) -> String,
    onApply: (String, QuickSetupTier) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 可用档位列表（按模型等级解锁）
    val availableTiers = remember(currentTier) { QuickSetupTier.availableTiers(currentTier) }
    val defaultTier = remember(currentTier) { QuickSetupTier.defaultForTier(currentTier) }
    var selectedTier by rememberSaveable(currentTier) { mutableStateOf(defaultTier) }
    var description by rememberSaveable { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var resultText by rememberSaveable { mutableStateOf<String?>(null) }
    var toastMsg by remember { mutableStateOf<String?>(null) }
    var pendingApplyText by remember { mutableStateOf<String?>(null) }

    toastMsg?.let { msg ->
        LaunchedEffect(msg) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            toastMsg = null
        }
    }

    // ===== 加载弹窗 =====
    if (isGenerating) {
        Dialog(
            onDismissRequest = { /* 生成中不可取消，等待结果 */ },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "正在生成人设…",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "请稍候，AI 正在按「${selectedTier.chineseName}」档位生成",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }

    // ===== 结果预览弹窗（可编辑） =====
    resultText?.let { raw ->
        QuickSetupResultDialog(
            initialText = raw,
            tier = selectedTier,
            onFillIn = { editedText ->
                if (hasExistingContent) {
                    pendingApplyText = editedText
                    resultText = null
                } else {
                    onApply(editedText, selectedTier)
                    resultText = null
                    toastMsg = "已填入人设"
                }
            },
            onCancel = { resultText = null }
        )
    }

    // ===== 覆盖确认弹窗 =====
    pendingApplyText?.let { textToApply ->
        ConfirmDialog(
            title = "覆盖现有内容",
            message = "当前已有人设 / 用户人设 / 场景 / 记忆内容，填入将直接覆盖，此操作不可撤销。确认填入？",
            confirmText = "确认覆盖",
            onConfirm = {
                onApply(textToApply, selectedTier)
                pendingApplyText = null
                toastMsg = "已填入人设"
            },
            onDismiss = { pendingApplyText = null }
        )
    }

    // ===== 三条开发规范（位于文件中间位置） =====
    // 1. 问题修复规范：所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
    //    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
    // 2. 代码注释规范：文件内仅允许保留两类注释——当前规则说明注释与模块划分注释（// ===== xxx =====）。
    //    除此之外，禁止出现任何形式的代码注释（包括单行 / 多行 / 临时调试注释）。
    // 3. 构建交付要求：完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK。
    //    APK 需满足：签名有效且符合发布标准、包含完整功能模块、经过基础性能与兼容性测试。

    SubPanelScaffold(title = "快速设定", onBack = onBack) {
        // 档位指示器（用户可点击切换可用档位）
        TierIndicator(
            availableTiers = availableTiers,
            selectedTier = selectedTier,
            onTierSelected = { newTier ->
                if (newTier in availableTiers) selectedTier = newTier
            }
        )
        Spacer(modifier = Modifier.size(16.dp))

        // 用户描述输入
        Text(
            text = "描述你想要的人设",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = "可以很模糊，AI 会基于你的描述生成完整的 AI 人设、用户人设、场景设置与记忆设置。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.size(12.dp))
        QuiddityTextField(
            value = description,
            onValueChange = { description = it },
            label = "人设描述",
            placeholder = "如：一个温柔的学姐，叫林夕，喜欢读书；我是大一新生小明",
            singleLine = false,
            collapsible = true,
            collapsedMaxLines = 6
        )
        Spacer(modifier = Modifier.size(20.dp))

        // 设定按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onBack) { Text("取消") }
            TextButton(
                onClick = {
                    if (description.isBlank()) {
                        toastMsg = "请先填写人设描述"
                        return@TextButton
                    }
                    isGenerating = true
                    scope.launch {
                        val result = runCatching { onGenerate(description, selectedTier) }
                        isGenerating = false
                        result.onSuccess { text ->
                            if (text.isBlank()) {
                                toastMsg = "生成失败：返回内容为空，请重试"
                            } else {
                                resultText = text
                            }
                        }.onFailure { e ->
                            val msg = e.message.orEmpty()
                            toastMsg = when {
                                "API 未配置" in msg -> "API 未配置，请先在模型配置中添加"
                                "未配置" in msg -> "API 未配置，请先在模型配置中添加"
                                else -> "生成失败：${msg.ifBlank { "请重试" }}"
                            }
                        }
                    }
                },
                enabled = !isGenerating
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoFixHigh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text("设定")
            }
        }
    }
}

// ==================== 档位指示器 ====================

/**
 * 三段式档位指示器：粗略 / 具体 / 全面。
 *
 * 视觉规则（层级解锁）：
 * - 可用档位（受当前模型等级解锁）：文字 + 背景高亮 + 可点击切换；
 * - 不可用档位（受当前模型等级限制）：文字灰色，无高亮，点击无反应。
 *
 * 实现：
 * - 滑动指示器用 drawBehind 直接绘制单段宽度的圆角矩形，配合 animateFloatAsState
 *   平滑滑动到目标段位置——避免 matchParentSize 全宽指示器覆盖多个档位文字的问题；
 * - 文字层使用 Row + weight(1f) 等分三段，确保指示器位置与文字对齐。
 */
@Composable
private fun TierIndicator(
    availableTiers: List<QuickSetupTier>,
    selectedTier: QuickSetupTier,
    onTierSelected: (QuickSetupTier) -> Unit
) {
    val tiers = QuickSetupTier.entries
    val selectedIndexInAll = tiers.indexOf(selectedTier)

    // 当前规则：drawBehind 内直接读 State.value（非 by 委托），确保 draw phase state 追踪可靠
    val indicatorOffsetState = animateFloatAsState(
        targetValue = selectedIndexInAll.toFloat(),
        animationSpec = spring(
            dampingRatio = Motion.SpringStandard.dampingRatio,
            stiffness = Motion.SpringStandard.stiffness
        ),
        label = "tier_indicator"
    )

    // 当前规则：drawBehind 在 draw phase 绘制单段宽度指示器，零重组且不覆盖相邻档位文字。
    val indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    val indicatorCornerRadius = 10.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(4.dp)
            .drawBehind {
                if (tiers.isNotEmpty()) {
                    val segmentWidth = size.width / tiers.size
                    val indicatorX = indicatorOffsetState.value * segmentWidth
                    drawRoundRect(
                        color = indicatorColor,
                        topLeft = Offset(indicatorX, 0f),
                        size = Size(segmentWidth, size.height),
                        cornerRadius = CornerRadius(
                            indicatorCornerRadius.toPx(),
                            indicatorCornerRadius.toPx()
                        )
                    )
                }
            }
    ) {
        // 三段文字
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tiers.forEachIndexed { index, tier ->
                val isActive = index == selectedIndexInAll
                val isAvailable = tier in availableTiers
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = isAvailable
                        ) { onTierSelected(tier) }
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = tier.chineseName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        color = when {
                            !isAvailable -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            isActive -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Spacer(modifier = Modifier.size(2.dp))
                    Text(
                        text = "${tier.maxChars}字内",
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            !isAvailable -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                            isActive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

// ==================== 结果预览弹窗 ====================

@Composable
private fun QuickSetupResultDialog(
    initialText: String,
    tier: QuickSetupTier,
    onFillIn: (String) -> Unit,
    onCancel: () -> Unit
) {
    var editedText by rememberSaveable(initialText) { mutableStateOf(initialText) }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "生成结果（可编辑）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = "档位：${tier.chineseName} · 直接编辑后点击「填入」将覆盖各设置项",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.size(12.dp))
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 420.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    placeholder = { Text("生成内容为空") }
                )
                Spacer(modifier = Modifier.size(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = onCancel) { Text("取消") }
                    TextButton(onClick = { onFillIn(editedText) }) {
                        Text("填入", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
