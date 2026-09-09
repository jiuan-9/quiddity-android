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
import androidx.compose.material3.AlertDialog
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
import com.quiddity.app.domain.AiPersonaField
import com.quiddity.app.domain.ApiCatalogManager
import com.quiddity.app.domain.QuickSetupPrompt
import com.quiddity.app.domain.QuickSetupTier
import com.quiddity.app.domain.missingRequiredFieldKeys
import com.quiddity.app.ui.components.ConfirmDialog
import com.quiddity.app.ui.components.QuiddityTextField
import com.quiddity.app.ui.components.TemperatureSlider
import com.quiddity.app.ui.theme.Motion
import kotlinx.coroutines.launch
/**
 * 快速设定子面板。
 *
 * 流程：
 * 1. 顶部档位指示器（粗略/具体/全面，与模型等级锁定，仅当前档可用）；
 * 2. 用户填写人设描述 → 点击「设定」；
 * 3. 加载弹窗（失败提示「生成失败，请重试 / API 未配置」）；
 * 4. 结果预览弹窗（按 AI 人设 / 用户人设 / 场景 / 记忆分区编辑，仅展示当前档位支持的字段）→「填入」覆盖各设置项 /「取消」退出。
 *
 * 填入前若检测到已有 persona/userPersona/scene/memory 内容，弹确认框二次确认。
 */
@Composable
fun QuickSetupPanel(
    currentTier: ApiCatalogManager.ModelTier,
    quickSetupTemperature: Double = com.quiddity.app.util.QuiddityConstants.DEFAULT_QUICK_SETUP_TEMPERATURE,
    onTemperatureChange: (Double) -> Unit = {},
    hasExistingContent: Boolean,
    hasMessages: Boolean = false,
    initialDraft: String = "",
    onDraftChange: (String) -> Unit = {},
    onGenerate: suspend (String, QuickSetupTier) -> String,
    onApply: (String, QuickSetupTier) -> Unit,
    onFinished: () -> Unit = {},
    onClearMessages: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 可用档位列表（按模型等级解锁）
    val availableTiers = remember(currentTier) { QuickSetupTier.availableTiers(currentTier) }
    val defaultTier = remember(currentTier) { QuickSetupTier.defaultForTier(currentTier) }
    var selectedTier by rememberSaveable(currentTier) { mutableStateOf(defaultTier) }
    var description by rememberSaveable(initialDraft) { mutableStateOf(initialDraft) }
    var isGenerating by remember { mutableStateOf(false) }
    var resultText by rememberSaveable { mutableStateOf<String?>(null) }
    var toastMsg by remember { mutableStateOf<String?>(null) }
    var pendingApplyText by remember { mutableStateOf<String?>(null) }
    var pendingClearMessages by remember { mutableStateOf(false) }

    toastMsg?.let { msg ->
        LaunchedEffect(msg) {
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            toastMsg = null
        }
    }

    // 应用结果：先落盘（onApply），再询问是否清除聊天记录，全部结束后回调 onFinished 关闭面板
    fun performApply(text: String, tier: QuickSetupTier) {
        onApply(text, tier)
        if (hasMessages) {
            pendingClearMessages = true
        } else {
            toastMsg = "已填入人设"
            onFinished()
        }
    }

