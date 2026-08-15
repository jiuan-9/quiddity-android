package com.quiddity.app.ui.agent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quiddity.app.data.model.Character
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.di.ServiceLocator
import com.quiddity.app.ui.chat.ChatViewModel
import com.quiddity.app.ui.components.AiAvatar


/** 选择角色卡：角色库头像列表，点选即应用到当前 Agent 会话（与群聊同款角色选择 UI）。 */
@Composable
internal fun AgentCharacterPicker(
    conversation: Conversation?,
    viewModel: ChatViewModel,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var candidates by remember { mutableStateOf<List<AgentCharacterCandidate>?>(null) }
    LaunchedEffect(Unit) {
        // 角色 = 角色库角色 + 私聊会话人设（与现有角色列表同一口径）。
        // - 只展示被活跃会话引用的角色卡，或能按名字找回私聊会话的孤儿卡（旧数据
        //   角色卡未回填引用时按名匹配补全），避免「已删除会话的角色卡」残留；
        // - 未回填角色卡的历史私聊仍按会话人设合成候选，保证名单完整。
        val library = runCatching {
            ServiceLocator.characterRepository.listCharacters()
        }.getOrDefault(emptyList())
        val libraryIds = library.map { it.id }.toSet()
        val allConversations = ServiceLocator.conversationRepository.conversations.value
        val solos = allConversations
            .filter {
                it.type == com.quiddity.app.data.model.ConversationType.SOLO &&
                    it.id != conversation?.id
            }
        val referencedIds = allConversations.mapNotNull { it.characterId }.toSet()
        val unlinkedSolos = solos.filter { it.characterId.isNullOrBlank() }
        // 孤儿卡按名字匹配未绑定会话：card id -> 匹配的会话 id
        val cardToConv = buildMap {
            library.forEach { c ->
                if (c.id in referencedIds) return@forEach
                val matched = unlinkedSolos.firstOrNull { conv -> characterNameMatches(c, conv) }
                if (matched != null) put(c.id, matched.id)
            }
        }
        candidates = buildList {
            library.forEach { c ->
                // 无内容 / 无引用且名字找不到对应会话 → 视为已删除，不展示
                if (!characterHasContent(c)) return@forEach
                if (c.id !in referencedIds && c.id !in cardToConv) return@forEach
                add(
                    AgentCharacterCandidate(
                        id = c.id,
                        name = c.persona.name.ifBlank { "未命名角色" },
                        subtitle = buildString {
                            val aiDesc = c.persona.character.ifBlank { c.persona.persona }
                            if (aiDesc.isNotBlank()) append(aiDesc)
                            val userName = c.userPersona.name
                            if (userName.isNotBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append("用户：$userName")
                            }
                            if (isEmpty()) append("点击选用")
                        },
                        avatarUri = c.aiAvatarUri ?: c.persona.aiAvatarUri,
                        library = c,
                        persona = c.persona,
                        userPersona = c.userPersona,
                        memory = c.memory,
                        selected = conversation?.characterId == c.id
                    )
                )
            }
            solos.forEach { conv ->
                // 已被角色卡按名字匹配（该卡已在角色列表中展示），避免重复
                if (cardToConv.values.contains(conv.id)) return@forEach
                if (conv.characterId != null && conv.characterId in libraryIds) return@forEach
                if (!conversationHasContent(conv)) return@forEach
                val p = conv.persona
                add(
                    AgentCharacterCandidate(
                        id = "conv:${conv.id}",
                        name = p.name.ifBlank { conv.title.ifBlank { "未命名角色" } },
                        subtitle = buildString {
                            val aiDesc = p.character.ifBlank { p.persona }
                            if (aiDesc.isNotBlank()) append(aiDesc)
                            val userName = conv.userPersona.name
                            if (userName.isNotBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append("用户：$userName")
                            }
                            if (isEmpty()) append("点击选用")
                        },
                        avatarUri = p.aiAvatarUri,
                        library = null,
                        persona = p,
                        userPersona = conv.userPersona,
                        memory = conv.memory,
                        selected = false
                    )
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // ===== 顶栏：关闭 + 标题 =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "选择角色",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.size(48.dp))
        }

        when {
            candidates == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
            candidates.orEmpty().isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "暂无可选角色\n可先到私聊里设置人设",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (conversation?.characterId != null) {
                        item(key = "clear_character") {
                            CharacterSelectRow(
                                name = "清除角色",
                                subtitle = "移除角色绑定（保留已写入的人设）",
                                avatarUri = null,
                                selected = false,
                                onClick = {
                                    viewModel.clearCharacter()
                                    onDismiss()
                                }
                            )
                        }
                    }
                    items(candidates.orEmpty(), key = { it.id }) { candidate ->
                        CharacterSelectRow(
                            name = candidate.name,
                            subtitle = candidate.subtitle,
                            avatarUri = candidate.avatarUri,
                            selected = candidate.selected,
                            onClick = {
                                val lib = candidate.library
                                if (lib != null) {
                                    viewModel.bindCharacter(lib)
                                } else {
                                    viewModel.bindPersona(
                                        persona = candidate.persona,
                                        userPersona = candidate.userPersona,
                                        memory = candidate.memory
                                    )
                                }
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

internal fun characterHasContent(c: com.quiddity.app.data.model.Character): Boolean {
    val p = c.persona
    val u = c.userPersona
    return p.name.isNotBlank() || p.desired.isNotBlank() || p.persona.isNotBlank() ||
        p.character.isNotBlank() || p.appearance.isNotBlank() || p.worldBackground.isNotBlank() ||
        u.name.isNotBlank() || u.identity.isNotBlank() || u.gender.isNotBlank() ||
        u.age.isNotBlank() || u.appearance.isNotBlank() || c.memory.isNotBlank()
}

internal fun conversationHasContent(conv: Conversation): Boolean {
    val p = conv.persona
    val u = conv.userPersona
    return p.name.isNotBlank() || p.desired.isNotBlank() || p.persona.isNotBlank() ||
        p.character.isNotBlank() || p.appearance.isNotBlank() || p.worldBackground.isNotBlank() ||
        u.name.isNotBlank() || u.identity.isNotBlank() || u.gender.isNotBlank() ||
        u.age.isNotBlank() || u.appearance.isNotBlank() || conv.memory.isNotBlank()
}

internal fun characterNameMatches(
    c: com.quiddity.app.data.model.Character,
    conv: Conversation
): Boolean {
    val name = c.persona.name.trim()
    if (name.isBlank()) return false
    val convName = conv.persona.name.trim().ifBlank { conv.title.trim() }
    return convName.isNotBlank() && convName == name
}

internal data class AgentCharacterCandidate(
    val id: String,
    val name: String,
    val subtitle: String,
    val avatarUri: String?,
    val library: Character?,
    val persona: com.quiddity.app.data.model.Persona,
    val userPersona: com.quiddity.app.data.model.UserPersona,
    val memory: String,
    val selected: Boolean
)

@Composable
internal fun CharacterSelectRow(
    name: String,
    subtitle: String,
    avatarUri: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .background(
                if (selected) colorScheme.primaryContainer.copy(alpha = 0.45f)
                else colorScheme.surfaceContainerLow
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AiAvatar(
            avatarUri = avatarUri,
            name = if (selected) name else name,
            size = 44.dp
        )
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "已选用",
                tint = colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
