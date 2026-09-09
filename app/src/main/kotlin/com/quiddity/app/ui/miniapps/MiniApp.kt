package com.quiddity.app.ui.miniapps

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.quiddity.app.ui.miniapps.board.BoardMiniApp
import com.quiddity.app.ui.miniapps.defuse.DefuseMiniApp
import com.quiddity.app.ui.miniapps.spy.SpyMiniApp

/*
 * 小应用框架核心。
 *
 * 快捷新增：运行仓库根目录 `new-miniapp.ps1 -Id x -Name "名称" -Description "描述"`，
 * 自动生成可编译模板并注册到本文件，无需手工改动。
 *
 * 手动新增小应用三步：
 * 1. 实现 [MiniApp]（id 全局唯一、图标、名称、描述、邀请气泡文案）；
 * 2. 在 [MiniAppRegistry.all] 注册；
 * 3. 中心页与宿主路由自动生效，无需改动导航。
 *
 * 小应用内部页面由自身管理（[MiniApp.Content] 内的状态机），不污染主 NavHost。
 */
interface MiniApp {
    /** 全局唯一 id，用于路由与收藏。 */
    val id: String

    /** 展示名称。 */
    val name: String

    /** 一句话描述。 */
    val description: String

    /** 中心页图标。 */
    val icon: ImageVector

    /**
     * 邀请某角色时写入其私聊的"邀请气泡"文案。
     * 默认通用模板；需要更多上下文（如棋种）的应用可自行覆写。
     */
    fun inviteBubbleText(opponentName: String): String =
        "你邀请了「$opponentName」一起玩《$name》"

    /** 小应用宿主入口。 */
    @Composable
    fun Content(host: MiniAppHost)
}

/** 小应用宿主上下文：提供退出、跳转会话、轻提示等能力。 */
class MiniAppHost(
    val onExit: () -> Unit,
    val onOpenConversation: (String) -> Unit,
    val onToast: (String) -> Unit
)

/** 小应用注册表：未来新应用在此注册即可。 */
object MiniAppRegistry {
    val all: List<MiniApp> = listOf(
        BoardMiniApp,
        SpyMiniApp,
        DefuseMiniApp
    )

    fun byId(id: String): MiniApp? = all.firstOrNull { it.id == id }
}
