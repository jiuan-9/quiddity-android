package com.quiddity.app.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * 角色库主档（schema v2）。
 *
 * 只放**身份类数据**：persona / userPersona / 固定记忆 memory / 角色头像 aiAvatarUri。
 * 会话级数据（场景、压缩摘要、群聊小本本、token 统计、策略覆盖）留在 [Conversation] 上，不进角色库。
 */
@Immutable
@Serializable
data class Character(
    val id: String,
    val persona: Persona = Persona.Empty,
    val userPersona: UserPersona = UserPersona.Empty,
    val memory: String = "",
    val aiAvatarUri: String? = null
)

/**
 * 会话 + 消息捆绑（schema v2 的 privateChats / groupChats 元素）。
 */
@Immutable
@Serializable
data class ConversationBundle(
    val conversation: Conversation,
    val messages: List<Message> = emptyList()
)

/**
 * 资产节（schema v2 的 assets 对象）。
 *
 * - wallpapers：会话级壁纸 Base64（key 为会话 id）
 * - listWallpaper：会话列表壁纸
 * - userAvatar：全局用户头像
 * - aiAvatars：AI 头像，key 从「会话 id」改为「角色 id」（角色库一个头像对应多个会话）
 */
@Immutable
@Serializable
data class ExportAssets(
    val wallpapers: Map<String, WallpaperData> = emptyMap(),
    val listWallpaper: WallpaperData? = null,
    val userAvatar: AvatarData? = null,
    val aiAvatars: Map<String, AvatarData> = emptyMap()
)

/**
 * 导入模式（3.1 三种导入模式）。
 *
 * - REPLACE = 替换：先备份本机数据，清空后写入文件全部内容（换机 / 恢复备份）
 * - MERGE = 合并：按 id 去重，不删除本机任何数据（本机已有数据，补充导入）
 * - CHARACTERS_ONLY = 仅导入角色库：只登记 characters，其余不动
 */
enum class ImportMode {
    REPLACE, MERGE, CHARACTERS_ONLY
}

/**
 * 导入跳过清单条目（3.3 / 3.4）。
 *
 * @param objectType 对象类型（角色 / 私聊 / 群聊 / 消息 / 资产 / 设置）
 * @param id 对象 id（无 id 的对象为空字符串）
 * @param reason 跳过原因
 */
@Immutable
@Serializable
data class ImportSkipItem(
    val objectType: String,
    val id: String,
    val reason: String
)

/**
 * 导入计划（4.1）：解析结果 + 跳过清单，供 UI 展示。
 *
 * @param payload 解析 + 校验 + 资产恢复后的完整数据（v1 文件已按 2.6 迁移为 v2 形态）
 * @param skipItems 跳过清单（对象 + 原因）
 * @param needsKeyRefill 需重新填写密钥的模型配置条目名称列表（3.2 密文解密自检失败项）
 * @param groupChatsSkipped 因成员引用悬空被跳过的群聊条目数（1.5.0 起可正常恢复群聊）
 */
@Immutable
data class ImportPlan(
    val payload: ExportPayload,
    val skipItems: List<ImportSkipItem> = emptyList(),
    val needsKeyRefill: List<String> = emptyList(),
    val groupChatsSkipped: Int = 0
)

/**
 * 两段式压缩结果（6.5.2）。
 *
 * - [summary]：摘要段 → Conversation.compressedMemory
 * - [index]：索引段 → Conversation.memoryIndex（含程序补全的覆盖范围）
 * - [success]：摘要段为空时为 false（本次压缩视为失败，两字段都保持旧值）
 */
@Immutable
data class MemoryCompressionResult(
    val summary: String,
    val index: String,
    val success: Boolean
)
