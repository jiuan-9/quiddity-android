package com.quiddity.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.quiddity.app.data.repo.ChatRepository
import com.quiddity.app.data.repo.ConversationRepository
import com.quiddity.app.data.repo.SettingsRepository
import com.quiddity.app.domain.ApiCatalogManager
import com.quiddity.app.domain.VisionOcrService

class ChatViewModelFactory(
    private val conversationRepository: ConversationRepository,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val apiCatalogManager: ApiCatalogManager,
    private val visionOcrService: VisionOcrService,
    private val conversationId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ChatViewModel(
            conversationRepository,
            chatRepository,
            settingsRepository,
            apiCatalogManager,
            visionOcrService,
            conversationId
        ) as T
    }
}

/**
 * 待确认的危险工具调用：聊天页弹窗展示 [items]，
 * 用户点击后通过 [resume] 把决定交还给挂起的工具执行流。
 */
data class PendingToolConfirm(
    val items: List<ChatRepository.ToolConfirmItem>,
    val resume: (Boolean) -> Unit
)

/**
 * 流式输出过程中的工具痕迹：记录工具名、运行状态与结果摘要。
 */
data class ToolTrace(
    val name: String,
    val status: String,
    val summary: String?
)

/**
 * 撤回消息后的「重新编辑」缓存：保留被撤回消息的原文、图片与 OCR 文本，
 * 用户点击「重新编辑」后以编辑结果作为新消息重新发出。
 */
data class PendingReedit(
    val content: String,
    val ocrText: String?,
    val imageUri: String?
)

/**
 * Agent 撤回提案：目标消息 + 本轮创建/更改项目清单。
 * [createdPaths] 为本轮创建的文件（确认后删除）；[changedItems] 为更改项摘要（仅提示）。
 */
data class WithdrawProposal(
    val targetId: String,
    val createdPaths: List<String>,
    val changedItems: List<String>
) {
    /** 本轮是否有创建/更改项目（决定弹窗文案是否带项目清单）。 */
    val hasEffects: Boolean get() = createdPaths.isNotEmpty() || changedItems.isNotEmpty()
}

// 当前规则：压缩状态与 isGenerating 解耦；Compressing 驱动 UI 弹窗与发送置灰，Success/Failed 为瞬态供 Toast 后 consume 回 Idle。
sealed interface CompressionState {
    data object Idle : CompressionState
    data object Compressing : CompressionState
    data object Success : CompressionState
    data object Failed : CompressionState
}

/**
 * 图片 OCR 识图状态：Idle = 空闲；Recognizing = 正在识图（输入栏显示加载圈）。
 */
sealed interface OcrState {
    data object Idle : OcrState
    data object Recognizing : OcrState
}
