package com.quiddity.app.active

import com.quiddity.app.data.model.ConversationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReplyOverlayStateMachineTest {

    private fun machine() = ReplyOverlayStateMachine()

    @Test
    fun `单会话生命周期 - 开始与结束`() {
        val m = machine()
        assertFalse(m.hasVisibleContent)
        m.startReply("c1", ConversationType.SOLO)
        assertEquals(1, m.activeCount)
        assertEquals("正在回复…", m.aggregateStatusText())
        assertTrue(m.hasVisibleContent)
        m.endReply("c1")
        assertEquals(0, m.activeCount)
        assertFalse(m.hasVisibleContent)
    }

    @Test
    fun `多会话并发聚合 - 计数与文案`() {
        val m = machine()
        m.startReply("c1", ConversationType.SOLO)
        m.startReply("c2", ConversationType.GROUP)
        m.startReply("c3", ConversationType.AGENT)
        assertEquals(3, m.activeCount)
        assertEquals("3 个对话正在回复…", m.aggregateStatusText())
        m.endReply("c1")
        assertEquals(2, m.activeCount)
        assertEquals("2 个对话正在回复…", m.aggregateStatusText())
    }

    @Test
    fun `重复开始同一会话不重复计数`() {
        val m = machine()
        m.startReply("c1", ConversationType.SOLO)
        m.startReply("c1", ConversationType.SOLO)
        assertEquals(1, m.activeCount)
    }

    @Test
    fun `工具动作优先于回复气泡`() {
        val m = machine()
        m.startReply("c1", ConversationType.AGENT)
        m.enqueueBubble("bubble", "c1", ConversationType.AGENT)
        assertNull(m.currentToolAction())
        assertEquals("bubble", m.nextBubble()?.text)
        m.showToolAction("c1", "正在滑动屏幕")
        assertEquals("正在滑动屏幕", m.currentToolAction())
        // 气泡仍在队列中，仅显示优先级让位于工具动作
        assertEquals("bubble", m.nextBubble()?.text)
        m.clearToolAction("c1")
        assertNull(m.currentToolAction())
        assertEquals("bubble", m.nextBubble()?.text)
    }

    @Test
    fun `新气泡替换旧气泡 仅保留最新一条`() {
        val m = machine()
        m.startReply("c1", ConversationType.SOLO)
        m.enqueueBubble("first", "c1", ConversationType.SOLO)
        m.enqueueBubble("second", "c2", ConversationType.SOLO)
        assertEquals("second", m.nextBubble()?.text, "新气泡应直接替换旧气泡")
        assertEquals("second", m.consumeBubble()?.text)
        assertNull(m.consumeBubble())
    }

    @Test
    fun `消费后的气泡不再重播`() {
        val m = machine()
        m.startReply("c1", ConversationType.SOLO)
        m.enqueueBubble("keep", "c1", ConversationType.SOLO)
        m.consumeBubble()
        assertNull(m.nextBubble(), "消费后不应再出现该气泡")
    }

    @Test
    fun `endReply 保留未消费气泡`() {
        val m = machine()
        m.startReply("c1", ConversationType.SOLO)
        m.enqueueBubble("keep", "c1", ConversationType.SOLO)
        m.endReply("c1")
        assertEquals(0, m.activeCount)
        assertTrue(m.hasVisibleContent)
        assertEquals("keep", m.nextBubble()?.text)
    }

    @Test
    fun `clearAll 清空全部`() {
        val m = machine()
        m.startReply("c1", ConversationType.SOLO)
        m.startReply("c2", ConversationType.AGENT)
        m.showToolAction("c2", "正在点击")
        m.enqueueBubble("b", "c1", ConversationType.SOLO)
        m.clearAll()
        assertEquals(0, m.activeCount)
        assertNull(m.currentToolAction())
        assertNull(m.nextBubble())
        assertFalse(m.hasVisibleContent)
    }

    @Test
    fun `窗口显示条件 - 仅在有可见内容或输入模式时保持`() {
        fun keep(enabled: Boolean, visible: Boolean, content: Boolean, input: Boolean) =
            ReplyOverlayStateMachine.shouldKeepWindow(enabled, visible, content, input)
        assertFalse(keep(false, false, true, false), "开关关闭时不显示")
        assertFalse(keep(true, true, true, false), "应用可见时不显示")
        assertFalse(keep(true, false, false, false), "无进行中会话且无输入模式时不显示")
        assertTrue(keep(true, false, true, false), "有进行中回复/工具动作/气泡时显示")
        assertTrue(keep(true, false, false, true), "输入模式打开时保留窗口")
        assertTrue(keep(true, false, true, true), "有内容且输入模式同时保留")
    }
}
