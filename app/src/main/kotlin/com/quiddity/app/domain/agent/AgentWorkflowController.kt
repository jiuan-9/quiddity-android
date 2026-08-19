package com.quiddity.app.domain.agent

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonArray

/*
 * ============================================================================
 * 开发规范 (Development Specifications)
 * ============================================================================
 *
 * 1. 问题修复规范
 *    所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
 *    修复内容需完全融入现有代码架构，保证代码逻辑的连贯性、可维护性和可扩展性。
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
 * Agent 任务编排器（方案 B'，1.6.2）——分级自适应：
 *
 * - **0~1 次工具调用：零介入**。聊天/单工具场景没有任何额外流程（去重要求历史中
 *   已有同键成功记录，首轮自然不触发；失败重试对用户透明——首次失败与重试结果
 *   合并为一条回填文本，模型只看到一次「最终结果」；无进度提示）。
 * - **2~3 次工具调用：仅隐形优化**。同工具同参数的成功调用自动去重（复用结果、
 *   不重复执行、不重复弹确认）；失败调用自动重试一次。用户无感知。
 * - **4 次及以上复杂任务**：在隐形优化的基础上，向 UI 暴露累计调用计数，
 *   聊天页据此显示「已执行 N 次工具操作」的进度提示。
 *
 * 生命周期：一条用户指令（含全部工具轮循环）共享一个实例；
 * 由外层工具循环（ChatRepository）驱动。
 *
 * 重试语义：仅当该调用「已经用户确认」后仍执行失败才重试——重试时确认自动通过
 * （用户已确认过同一操作），黑名单/参数校验/审计等其余门控照常执行。
 */
class AgentWorkflowController {

    /** 一次已完成的工具调用记录。 */
    data class CallRecord(
        val name: String,
        val argsKey: String,
        val ok: Boolean,
        val result: String
    )

    private val history = mutableListOf<CallRecord>()
    private val retriedKeys = mutableSetOf<String>()

    /** 已执行过的调用总数（去重后复用不增加计数）。 */
    /**
     * 去重命中：同工具、同参数（规范化比较）且执行成功的调用。
     * 命中后应复用其结果，不再执行；未命中返回 null。
     * 0~1 次调用场景下历史为空，必然返回 null（零介入）。
     */
    fun findDuplicate(name: String, args: JsonObject): CallRecord? =
        history.lastOrNull { it.name == name && it.argsKey == normalizeArgs(args) && it.ok }

    /**
     * 是否应对该调用自动重试：执行失败、且属于「可重试」失败、且此前未重试过同一调用。
     * @param retryable 是否为可重试失败（真正的执行失败；用户取消 / 未确认导致的
     *   「失败」不可重试，避免绕过用户意图）。
     */
    fun shouldRetry(name: String, args: JsonObject, ok: Boolean, retryable: Boolean): Boolean {
        if (ok || !retryable) return false
        val key = callKey(name, args)
        return key !in retriedKeys
    }

    /** 记录一次重试（无论重试成功与否，同一调用不再重试第二次）。 */
    fun markRetried(name: String, args: JsonObject) {
        retriedKeys += callKey(name, args)
    }

    /** 记录一次已执行的调用（成功与失败都记录，供去重/计数/重试判定）。 */
    fun record(name: String, args: JsonObject, ok: Boolean, result: String) {
        history += CallRecord(name, normalizeArgs(args), ok, result)
    }

    /**
     * 参数规范化：JsonObject → 按键排序的紧凑 JSON 文本，
     * 使「键序不同但语义相同」的调用被视为同一调用。
     */
    fun normalizeArgs(args: JsonObject): String = encodeSorted(args)

    private fun callKey(name: String, args: JsonObject): String =
        name + "|" + normalizeArgs(args)

    private fun encodeSorted(element: JsonElement): String = when (element) {
        is JsonObject -> {
            val sb = StringBuilder("{")
            val entries = element.entries.sortedBy { it.key }
            entries.forEachIndexed { i, (k, v) ->
                if (i > 0) sb.append(",")
                sb.append("\"").append(k).append("\":").append(encodeSorted(v))
            }
            sb.append("}")
            sb.toString()
        }
        is JsonArray -> "[" + element.map { encodeSorted(it) }.joinToString(",") + "]"
        is JsonPrimitive -> element.content
        else -> element.toString()
    }
}
