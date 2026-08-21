package com.quiddity.app.ui.chat.components

import android.widget.Toast
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.data.model.hasPersonaContent
import com.quiddity.app.di.ServiceLocator
import com.quiddity.app.ui.chat.ChatViewModel
import com.quiddity.app.ui.components.AiAvatar
import com.quiddity.app.ui.components.ApiEditBottomSheet
import com.quiddity.app.util.QuiddityConstants
import kotlinx.coroutines.launch


/**
 * 成员添加流程状态机（多弹窗稳定切换：同一时刻只显示一个弹窗；
 * 新增 API 底部面板作为覆盖层叠加在配置弹窗之上）。
 */
internal sealed interface MemberAddFlow {
    data object None : MemberAddFlow
    data object Selecting : MemberAddFlow
    data class Result(
        val passedCount: Int,
        val failed: List<Pair<Conversation, String>>
    ) : MemberAddFlow
    data class Config(
        val member: Conversation,
        val failed: List<Pair<Conversation, String>>
    ) : MemberAddFlow
}

/**
 * 群聊成员管理面板（方案十.4-5 + 需求）：
 * 显示当前成员头像（通过的加入后立即显示）；不足 3 个显示加号按钮（添加成员）；
 * 单个 API 有问题时通过的正常加入，未通过的进入弹窗重试/配置（可现场新增 API）。
 */
@Composable
internal fun GroupMemberManagePanel(
    group: Conversation,
    viewModel: ChatViewModel,
    settings: com.quiddity.app.data.model.AppSettings,
    onBack: () -> Unit
) {
    var addFlow by remember { mutableStateOf<MemberAddFlow>(MemberAddFlow.None) }
    var showApiCreateSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val conversationRepo = com.quiddity.app.di.ServiceLocator.conversationRepository
    val settingsRepo = com.quiddity.app.di.ServiceLocator.settingsRepository
    val apiCatalogManager = com.quiddity.app.di.ServiceLocator.apiCatalogManager

    val members = remember(group.memberConversationIds) {
        group.memberConversationIds.mapNotNull { id ->
            conversationRepo.getConversation(id)
        }
    }
    // 仅展示有角色卡内容的私聊：无任何设定的空会话不被群聊成员检测拾取
    val soloList = remember(settings.catalog) {
        conversationRepo.conversations.value
            .filter { it.type == ConversationType.SOLO }
            .filter { it.hasPersonaContent }
    }

    /** 提交添加：通过的正常加入（显示头像），未通过的进入结果弹窗。 */
    fun submitAdd(ids: List<String>) {
        if (ids.isEmpty()) {
            addFlow = MemberAddFlow.None
            return
        }
        viewModel.addGroupMembers(ids) { _, failures ->
            val failedPairs = failures.mapNotNull { (id, reason) ->
                conversationRepo.getConversation(id)?.let { it to reason }
            }
            val passedCount = ids.size - failures.size
            if (failures.isEmpty()) {
                android.widget.Toast.makeText(
                    context,
                    "已添加 $passedCount 个成员",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                addFlow = MemberAddFlow.None
            } else {
                addFlow = MemberAddFlow.Result(passedCount, failedPairs)
            }
        }
    }

    /** 把已通过校验的成员真正并入群聊（刷新群聊本体，避免用过期快照覆盖）；超限/已存在则忽略。 */
    suspend fun addMemberToGroup(memberId: String) {
        val currentGroup = conversationRepo.conversations.value
            .firstOrNull { it.id == group.id } ?: group
        val newIds = currentGroup.memberConversationIds + memberId
        if (newIds.size <= QuiddityConstants.GROUP_MAX_MEMBERS && memberId !in currentGroup.memberConversationIds) {
            conversationRepo.updateConversation(currentGroup.copy(memberConversationIds = newIds))
        }
    }

    /** 配置成员 API 后重新校验；通过则从失败列表移除并真正加入群聊。 */
    fun configureMemberAndRevalidate(member: Conversation, catalogId: String?) {
        scope.launch {
            val updated = member.copy(apiCatalogId = catalogId)
            conversationRepo.updateConversation(updated)
            val result = conversationRepo.validateGroupMember(updated)
            if (result.isSuccess) {
                // 真正并入群聊：仅更新成员自身会话不足以让成员成为群聊成员
                addMemberToGroup(updated.id)
                android.widget.Toast.makeText(
                    context,
                    "${member.persona.name.ifBlank { "成员" }} 配置成功，已加入",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                val remaining = (addFlow as? MemberAddFlow.Config)
                    ?.failed
                    ?.filterNot { it.first.id == member.id }
                    .orEmpty()
                addFlow = if (remaining.isEmpty()) MemberAddFlow.None
                else MemberAddFlow.Result(0, remaining)
            } else {
                android.widget.Toast.makeText(
                    context,
                    "仍未通过：${result.exceptionOrNull()?.message ?: "校验失败"}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "成员管理",
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
                    ) { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.size(16.dp))

        Text(
            text = "当前成员（${members.size}/${QuiddityConstants.GROUP_MAX_MEMBERS}）",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.size(8.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(members, key = { it.id }) { member ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        AiAvatar(
                            avatarUri = member.persona.aiAvatarUri,
                            name = member.persona.name,
                            size = 40.dp
                        )
                    }
                    Spacer(modifier = Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = member.persona.name.ifBlank { "未命名 AI" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "模型：${member.apiCatalogId?.let { id ->
                                settings.catalog.firstOrNull { c -> c.id == id }?.apiModel
                            } ?: "跟随全局"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        enabled = members.size > 1,
                        onClick = { viewModel.removeGroupMember(member.id) }
                    ) {
                        Text(
                            text = "移除",
                            color = if (members.size > 1) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
            if (members.size < QuiddityConstants.GROUP_MAX_MEMBERS) {
                item(key = "add_member") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { addFlow = MemberAddFlow.Selecting }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(
                            text = "添加成员",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    when (val flow = addFlow) {
        is MemberAddFlow.Selecting -> {
            AddGroupMembersDialog(
                soloList = soloList,
                currentIds = group.memberConversationIds,
                hasApiConfig = settings.catalog.isNotEmpty(),
                onConfirm = { ids -> submitAdd(ids) },
                onDismiss = { addFlow = MemberAddFlow.None }
            )
        }
        is MemberAddFlow.Result -> {
            MemberAddResultDialog(
                passedCount = flow.passedCount,
                failed = flow.failed,
                onRetry = { submitAdd(flow.failed.map { it.first.id }) },
                onConfig = { member ->
                    addFlow = MemberAddFlow.Config(member, flow.failed)
                },
                onDone = { addFlow = MemberAddFlow.None }
            )
        }
        is MemberAddFlow.Config -> {
            MemberApiConfigDialog(
                member = flow.member,
                catalog = settings.catalog,
                currentId = flow.member.apiCatalogId,
                onSelect = { catalogId -> configureMemberAndRevalidate(flow.member, catalogId) },
                onAddNew = { showApiCreateSheet = true },
                onRetry = {
                    scope.launch {
                        val updated = conversationRepo.getConversation(flow.member.id)
                            ?: return@launch
                        val result = conversationRepo.validateGroupMember(updated)
                        if (result.isSuccess) {
                            // 真正并入群聊：仅更新成员自身会话不足以让成员成为群聊成员
                            addMemberToGroup(updated.id)
                            android.widget.Toast.makeText(
                                context,
                                "${updated.persona.name.ifBlank { "成员" }} 校验通过，已加入",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            val remaining = flow.failed.filterNot { it.first.id == updated.id }
                            addFlow = if (remaining.isEmpty()) MemberAddFlow.None
                            else MemberAddFlow.Result(0, remaining)
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                "仍未通过：${result.exceptionOrNull()?.message ?: "校验失败"}",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                },
                onBack = { addFlow = MemberAddFlow.Result(0, flow.failed) }
            )
        }
        else -> Unit
    }

    // 新增模型配置底部面板（叠加在配置弹窗之上，保存后回到配置弹窗继续选择）
    if (showApiCreateSheet) {
        ApiEditBottomSheet(
            initial = null,
            catalogManager = apiCatalogManager,
            testConnection = { url, key, model ->
                apiCatalogManager.testConnection(url, key, model)
            },
            onDismiss = { showApiCreateSheet = false },
            onSave = { state ->
                scope.launch {
                    runCatching {
                        val entry = apiCatalogManager.buildEntry(
                            id = state.id,
                            name = state.name,
                            providerId = state.providerId,
                            apiUrl = state.apiUrl,
                            apiModel = state.apiModel,
                            apiKey = state.apiKey
                        )
                        settingsRepo.upsertCatalog(entry)
                    }.onFailure {
                        android.util.Log.e("GroupMemberManagePanel", "新增模型配置失败", it)
                        android.widget.Toast.makeText(
                            context,
                            "新增模型配置失败：${it.message ?: "未知错误"}",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
                showApiCreateSheet = false
            }
        )
    }
}
