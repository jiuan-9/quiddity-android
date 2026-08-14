package com.quiddity.app.active

/**
 * 屏幕操作小窗（画中画 PiP）桥。
 *
 * Agent 执行模拟操作类工具（滑动/点击/拖拽等）时，聊天页需要自动进入
 * 画中画小窗：目标应用保持全屏供 AI 操作，Quiddity 以小窗展示操作状态。
 * 本对象是执行侧（ChatViewModel / ScreenReaderService）与 UI 宿主
 * （MainActivity）之间的单向回调桥，宿主在 onCreate 注册、onDestroy 注销。
 */
object OperationPipController {

    /** 请求进入小窗（status = 初始状态文本）。宿主注册。 */
    @Volatile
    var onEnterPipRequest: ((String) -> Unit)? = null

    /** 更新小窗状态文本（正在滑动/点击完成等）。宿主注册。 */
    @Volatile
    var onStatusUpdate: ((String) -> Unit)? = null

    /** 是否已进入小窗（宿主维护）。 */
    @Volatile
    var inPip: Boolean = false

    /** 请求进入小窗。 */
    fun requestEnterPip(status: String) {
        if (inPip) {
            updateStatus(status)
            return
        }
        onEnterPipRequest?.invoke(status)
    }

    /** 更新小窗状态文本。 */
    fun updateStatus(text: String) {
        onStatusUpdate?.invoke(text)
    }
}
