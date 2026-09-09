package com.quiddity.app.ui.miniapps

import com.quiddity.app.domain.board.BoardGameType
import com.quiddity.app.ui.miniapps.board.BoardMiniApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/*
 * 小应用框架可拓展性测试：注册表唯一性、路由可解析、邀请气泡文案可用。
 */
class MiniAppRegistryTest {

    @Test
    fun allApps_haveUniqueNonBlankMetadata() {
        val ids = MiniAppRegistry.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "小应用 id 必须全局唯一")
        for (app in MiniAppRegistry.all) {
            assertTrue(app.id.isNotBlank(), "id 不能为空")
            assertTrue(app.name.isNotBlank(), "${app.id} 名称不能为空")
            assertTrue(app.description.isNotBlank(), "${app.id} 描述不能为空")
        }
    }

    @Test
    fun byId_roundTripsEveryApp() {
        for (app in MiniAppRegistry.all) {
            assertEquals(app, MiniAppRegistry.byId(app.id))
        }
        assertNotNull(MiniAppRegistry.byId("board"))
        assertEquals(null, MiniAppRegistry.byId("not-exist"))
    }

    @Test
    fun inviteBubbleText_isAvailableForEveryApp() {
        for (app in MiniAppRegistry.all) {
            val text = app.inviteBubbleText("小美")
            assertTrue(text.contains("小美"), "${app.id} 气泡应包含对手名")
            assertTrue(text.contains(app.name), "${app.id} 气泡应包含应用名")
        }
    }

    @Test
    fun boardInviteBubble_includesGameType() {
        val text = BoardMiniApp.inviteBubbleText(BoardGameType.GOMOKU, "小美")
        assertTrue(text.contains("五子棋"))
        assertTrue(text.contains("小美"))
        val goText = BoardMiniApp.inviteBubbleText(BoardGameType.GO, "阿哲")
        assertTrue(goText.contains("围棋"))
    }
}
