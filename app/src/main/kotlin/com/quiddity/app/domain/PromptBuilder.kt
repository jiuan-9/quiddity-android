package com.quiddity.app.domain

import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.MemoryCompressionResult
import com.quiddity.app.data.model.Persona
import com.quiddity.app.data.model.Role
import com.quiddity.app.data.model.UserPersona
import com.quiddity.app.data.remote.ChatMessage
import com.quiddity.app.data.remote.ResponsesInputItem
import com.quiddity.app.data.remote.ResponsesTool
import com.quiddity.app.data.remote.ToolDefinition
import com.quiddity.app.data.remote.ToolFunction
import com.quiddity.app.util.QuiddityConstants
import com.quiddity.app.util.TokenEstimator
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
/**
 * 提示词中枢：统一管理所有发给 LLM 的提示词，分为两大类。
 *
 * 一、功能类——与具体人设无关的工具型提示词：记忆压缩 / 人设精调。
 * 二、人设类——聊天 system 提示词：AI 人设 / 用户人设 / 场景 / 记忆。
 *
 * 字段命名规范（全文件统一，跨提示词一致，对准应用内设置填空项）：
 * - AI 人设：【名字】【身份背景】【性格】【外观】【世界背景】【期望特质】
 * - 用户人设（【用户信息】下）：名字 / 身份 / 性别 / 年龄 / 外观
 * - 其他：【当前场景】【历史对话摘要】【需要记住的事】
 *
 * 多消息切分不再由提示词驱动：[MessageStreamCoordinator] 在流式输出阶段按句末标点 +
 * 括号确定性切分，无需告知 LLM 任何分割标记或规则。
 */
object PromptBuilder {

    /**
     * 外部压缩占位符（`<<ccr:...>>`）：真实内容已不存在，发给模型只会污染上下文。
     * 组装 API 历史时剥离该标记；整条消息只剩占位符时整条跳过。
     */
    private val CCR_REFERENCE_REGEX = Regex("""<<ccr:[^>]*>>""")

