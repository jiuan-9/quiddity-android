package com.quiddity.app.domain.agent

import com.quiddity.app.domain.agent.AgentExecutors
import com.quiddity.app.domain.agent.AgentTool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class AgentToolRegistry(
    private val tools: Map<String, AgentTool>
) {

    fun tools(): Collection<AgentTool> = tools.values

    operator fun get(name: String): AgentTool? = tools[name]

    /**
     * 分派工具调用：
     * 未知工具 → 不存在；开关关闭 → 未启用；
     * 其余走 [AgentSecurity.executeGated]（参数校验 / 白名单 / 确认 / 审计）。
     */
    suspend fun dispatch(name: String, argsJson: String, ctx: AgentContext): String {
        val tool = tools[name] ?: return "工具 $name 不存在"
        if (!AgentSecurity.isSwitchEnabled(tool, ctx.switches)) {
            return "工具 $name 未启用"
        }
        val parsed = runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(argsJson) as? JsonObject
        }.getOrNull()
        val args = parsed ?: JsonObject(emptyMap())
        // 模型自带的 confirmed 字段不可信：一律剥掉，由确认弹窗统一补上。
        val sanitized = JsonObject(args.toMutableMap().apply { remove("confirmed") })
        return AgentSecurity.executeGated(tool, sanitized, ctx)
    }

    /**
     * 批量分派同一轮模型工具调用：
     * 先统一收集需要确认的工具并只弹一次确认（或按「完全」模式自动批准），
     * 再逐个执行并返回一一对应的结果文本。
     */
    suspend fun dispatchAll(
        calls: List<AgentToolCallRequest>,
        ctx: AgentContext
    ): List<String> {
        data class Resolved(val request: AgentToolCallRequest, val tool: AgentTool?, val args: JsonObject)

        val resolved = calls.map { req ->
            val tool = tools[req.name]
            val args = runCatching {
                kotlinx.serialization.json.Json.parseToJsonElement(req.argsJson) as? JsonObject
            }.getOrNull() ?: JsonObject(emptyMap())
            Resolved(
                request = req,
                tool = tool,
                args = JsonObject(args.toMutableMap().apply { remove("confirmed") })
            )
        }

        val confirmItems = resolved.mapNotNull { r ->
            val tool = r.tool
            if (tool != null &&
                tool.confirm == AgentConfirmPolicy.ALWAYS_CONFIRM &&
                AgentSecurity.isSwitchEnabled(tool, ctx.switches)
            ) {
                tool to r.args
            } else {
                null
            }
        }

        val approved = when {
            ctx.autoConfirm -> true
            confirmItems.isEmpty() -> null
            else -> ctx.confirmRequestBatch?.invoke(confirmItems)
        }

        return resolved.map { r ->
            val tool = r.tool
            when {
                tool == null -> "工具 ${r.request.name} 不存在"
                !AgentSecurity.isSwitchEnabled(tool, ctx.switches) -> "工具 ${r.request.name} 未启用"
                tool.confirm == AgentConfirmPolicy.ALWAYS_CONFIRM && approved == false ->
                    "用户已取消执行 ${r.request.name}"
                else -> {
                    val effective = if (tool.confirm == AgentConfirmPolicy.ALWAYS_CONFIRM && approved == true) {
                        JsonObject(r.args.toMutableMap().apply { put("confirmed", JsonPrimitive(true)) })
                    } else {
                        r.args
                    }
                    AgentSecurity.executeGated(tool, effective, ctx)
                }
            }
        }
    }
    companion object {
        /**
         * 全部已登记工具名（与工具默认开关清单一致）。
         */
        fun allToolNames(): Set<String> = agentAllToolNames()

        fun displayName(name: String): String = agentDisplayName(name)

        fun toolExplanation(name: String): String = agentToolExplanation(name)

        fun actionDescription(name: String, args: JsonObject): String = agentActionDescription(name, args)

        fun defaultRegistry(executors: AgentExecutors? = null): AgentToolRegistry {
            val tools = agentAppReadTools(executors) +
                agentFileTools(executors) +
                agentSystemActionTools(executors) +
                agentInteractionTools(executors)
            return AgentToolRegistry(tools.associateBy { it.name })
        }
    }
}