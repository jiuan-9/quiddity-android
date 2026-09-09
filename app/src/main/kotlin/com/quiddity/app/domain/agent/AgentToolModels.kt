package com.quiddity.app.domain.agent

import com.quiddity.app.data.local.AgentToolSwitches
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.data.remote.ToolDefinition
import com.quiddity.app.data.remote.ToolFunction
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

enum class AgentPermissionLevel {
    BASIC,
    ADVANCED
}

/**
 * Agent 工具确认策略。
 * - AUTO：直接执行（仅限读取类）
 * - ALWAYS_CONFIRM：每次执行前都必须用户确认（V1 不可关闭）
 */
enum class AgentConfirmPolicy {
    AUTO,
    ALWAYS_CONFIRM
}

/**
 * Agent 工具分类（工具使用开关 UI 的五大分组，1.6.0 架构重整）。
 * - READ：只读（监听类、读取类）
 * - MODIFY：更改（创建、更改、改入等）
 * - DELETE：删除（仅删除类）
 * - ACT：行为（截图、滑动等 AI 实际操作项，包括发送信息）
 * - OCR：识图（仅供 AI 自行使用 OCR）
 */
enum class AgentToolCategory(
    val id: String,
    val title: String,
    val subtitle: String
) {
    READ("read", "只读", "监听类、读取类"),
    MODIFY("modify", "更改", "创建、更改、改入等"),
    DELETE("delete", "删除", "仅删除类"),
    ACT("act", "行为", "截图、滑动等 AI 实际操作项，包括发送信息"),
    OCR("ocr", "识图", "仅供 AI 自行使用 OCR");

    companion object {
        val ALL: List<AgentToolCategory> = entries

        /** 分类 → 该分类下全部工具名（供主开关一键开启/关闭）。 */
        fun toolsOf(category: AgentToolCategory): Set<String> =
            AgentToolRegistry.allToolNames().filter { categoryOf(it) == category }.toSet()
    }
}

/** 工具分类映射（与 displayName / actionFor 同构，供清单分组与 UI 归类）。 */
fun categoryOf(name: String): AgentToolCategory = when (name) {
    "read_screen", "get_time", "read_notifications", "usage_stats", "foreground_app",
    "list_apps", "app_permissions", "app_install_info", "app_battery",
    "traffic_ranking", "file_access", "system_logs", "app_logs",
    "read_file", "list_files", "file_info",
    "read_clipboard", "toast_monitor", "notification_guard", "app_usage_detail" ->
        AgentToolCategory.READ
    "create_file", "write_file", "append_file", "rename_file", "mkdir",
    "move_file", "copy_file", "write_clipboard",
    "set_appops", "disable_app", "enable_app", "force_stop" ->
        AgentToolCategory.MODIFY
    "delete_file", "clear_clipboard", "dismiss_notification", "uninstall_app" ->
        AgentToolCategory.DELETE
    "screenshot", "reveal_file", "run_shell",
    "click", "long_press", "click_text", "scroll", "global_action",
    "input_text", "click_id", "click_desc", "drag", "scroll_to_text", "lock_screen",
    "open_app", "notify_self", "reply_notification", "schedule_notify", "sleep" ->
        AgentToolCategory.ACT
    "ocr_image" -> AgentToolCategory.OCR
    else -> AgentToolCategory.ACT
}

/**
 * Agent 工具定义。
 *
 * [params] 为 OpenAI 兼容 ToolDefinition.parameters（JsonObject 形状：
 * {"type":"object","properties":{...},"required":[...]}）。
 * [enabledByDefault] 对应 AgentStore 工具开关的默认值。
 */
data class AgentTool(
    val name: String,
    val description: String,
    val params: JsonObject,
    val level: AgentPermissionLevel,
    val confirm: AgentConfirmPolicy,
    val enabledByDefault: Boolean,
    val execute: suspend (AgentContext, JsonObject) -> String
) {
    /** 工具分类（由名称映射，供清单分组与 UI 归类使用）。 */
    val category: AgentToolCategory get() = categoryOf(name)

    /** 转换为 OpenAI 兼容工具声明（params 直接复用 ToolDefinition 形状）。 */
    fun toToolDefinition(): ToolDefinition = ToolDefinition(
        type = "function",
        function = ToolFunction(
            name = name,
            description = description,
            parameters = params
        )
    )
}

/**
 * Agent 执行上下文。
 *
 * [conversation] 当前 Agent 会话；[switches] 工具开关快照；
 * [blacklist] 黑名单（包名 / 文件路径，命中即拒绝查看/更改/删除）；
 * [auditAppend] 审计追加回调（AgentStore）。
 */
data class AgentContext(
    val conversation: Conversation?,
    val switches: AgentToolSwitches,
    val blacklist: Set<String>,
    val auditAppend: suspend (com.quiddity.app.data.local.AgentAuditEntry) -> Unit = {},
    /** 权限管控「完全」模式：跳过危险工具逐次确认，自动执行（仍受开关与黑名单约束）。 */
    val autoConfirm: Boolean = false,
    /**
     * 危险操作确认回调：返回 null 表示未接确认 UI（按未确认处理），
     * 返回 true / false 表示用户已确认 / 已取消。
     */
    val confirmRequest: (suspend (AgentTool, JsonObject) -> Boolean)? = null,
    /**
     * 批量确认回调：一轮模型工具调用中所有需要确认的工具一次列出，
     * 返回 true / false 表示用户整体批准 / 取消。
     */
    val confirmRequestBatch: (suspend (List<Pair<AgentTool, JsonObject>>) -> Boolean)? = null,
    /**
     * 本轮（一条用户消息 → 全部工具轮）行为追踪器：
     * 记录本轮创建/更改的文件与应用状态，撤回时用于删除创建物与提示更改项。
     */
    val roundEffects: AgentRoundEffects = AgentRoundEffects()
)

/**
 * Agent 单轮行为追踪器（撤回语义的数据基础）。
 *
 * 一条用户消息内的全部工具执行共享同一实例（跨多个工具轮循环），
 * 撤回时按 [createdFiles] 删除本轮创建的文件，按 [changedItems] 提示不可逆更改项。
 */
class AgentRoundEffects {

    private val lock = Any()
    private val created = mutableListOf<String>()
    private val changed = mutableListOf<String>()

    /**
     * 记录一次工具执行的文件/应用影响（仅执行成功时调用）。
     *
     * 分类规则：
     * - 创建类（create_file / mkdir / copy_file / move_file / rename_file 的目标路径）→ [created]
     * - 更改类（write_file / append_file 覆盖既有内容、应用状态类工具）→ [changed]
     */
    fun record(toolName: String, args: JsonObject, ok: Boolean) {
        if (!ok) return
        val path = argString(args, "path")
        val src = argString(args, "src")
        val dst = argString(args, "dst")
        val pkg = argString(args, "pkg")
        synchronized(lock) {
            when (toolName) {
                "create_file", "mkdir" -> {
                    if (!path.isNullOrBlank()) created += path
                }
                "copy_file", "move_file", "rename_file" -> {
                    if (!dst.isNullOrBlank()) created += dst
                    if (!src.isNullOrBlank() && toolName != "rename_file") {
                        changed += (if (toolName == "copy_file") "复制 $src" else "移动 $src")
                    }
                }
                "write_file", "append_file" -> {
                    if (!path.isNullOrBlank()) changed += "写入 $path"
                }
                "disable_app" -> {
                    if (!pkg.isNullOrBlank()) changed += "停用 $pkg（撤回可恢复）"
                }
                "enable_app" -> {
                    if (!pkg.isNullOrBlank()) changed += "启用 $pkg（撤回可恢复）"
                }
                "force_stop" -> {
                    if (!pkg.isNullOrBlank()) changed += "强停 $pkg（瞬态，无需恢复）"
                }
                "set_appops" -> {
                    if (!pkg.isNullOrBlank()) changed += "修改 $pkg 权限（不可自动恢复，请手动检查）"
                }
                else -> Unit
            }
        }
    }

    /** 本轮创建的文件 / 目录（撤回时删除）。 */
    fun createdFiles(): List<String> = synchronized(lock) { created.distinct().toList() }

    /** 本轮更改项（撤回时提示，不可删除）。 */
    fun changedItems(): List<String> = synchronized(lock) { changed.distinct().toList() }

    private fun argString(args: JsonObject, key: String): String? =
        (args[key] as? JsonPrimitive)?.content
}

/**
 * 批量工具调用请求项：一轮模型回复中携带的单个工具调用。
 */
data class AgentToolCallRequest(
    val name: String,
    val argsJson: String
)

/**
 * Agent 工具注册表：名称 → 工具定义，负责按名称分派并施加安全门控。
 */
