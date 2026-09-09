package com.quiddity.app.domain

import com.quiddity.app.data.model.Message
import com.quiddity.app.util.QuiddityConstants

/**
 * 群聊点名回复队列（纯 Kotlin，便于 JVM 单测）。
 *
 * 规则（方案四）：
 * - 正在回复的最多 1 个，排队最多 2 个；
 * - 每个成员入队时冻结点击那一刻的群聊消息快照（方案四.8 上下文定格）；
 * - 停止模式 A 只移除正在回复的成员（排队的顺位递补），
 *   停止模式 B 清空整个队列（由调用方决定）。
 */
class GroupReplyQueue(
    private val maxReplying: Int = 1,
    private val maxQueued: Int = QuiddityConstants.GROUP_MAX_MEMBERS - 1
) {
    data class Item(
        val memberId: String,
        val frozenMessages: List<Message>
    )

    private val items = ArrayDeque<Item>()

    val isFull: Boolean get() = items.size >= maxReplying + maxQueued
    val size: Int get() = items.size
    val isEmpty: Boolean get() = items.isEmpty()
    val isNotEmpty: Boolean get() = items.isNotEmpty()

    fun contains(memberId: String): Boolean = items.any { it.memberId == memberId }

    /** 入队：已在队或队满时返回 false。 */
    fun enqueue(memberId: String, frozenMessages: List<Message>): Boolean {
        if (isFull || contains(memberId)) return false
        items.addLast(Item(memberId, frozenMessages))
        return true
    }

    /** 当前正在回复的成员；队空时 null。 */
    /** 取出正在回复的成员（回复完成后调用），排队的顺位递补。 */
    fun dequeue(): Item? = if (items.isEmpty()) null else items.removeFirst()

    /** 清空整个队列（停止模式 B）。 */
    fun clear() = items.clear()

    /** 只移除正在回复的成员（停止模式 A），排队的顺位递补。 */
    fun removeReplying(): Item? = if (items.isEmpty()) null else items.removeFirst()

    /** 成员当前排队序号：0=正在回复，1=第 1 个排队，2=第 2 个排队；不在队返回 -1。 */
    fun positionOf(memberId: String): Int =
        items.indexOfFirst { it.memberId == memberId }

    fun snapshot(): List<Item> = items.toList()
}
