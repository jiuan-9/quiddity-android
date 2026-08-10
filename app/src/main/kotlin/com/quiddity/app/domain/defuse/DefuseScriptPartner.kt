package com.quiddity.app.domain.defuse

/*
 * 内置脚本搭档：无可用 API 时的离线兜底，指令由领域层直接计算，保证可玩。
 */

object DefuseScriptPartner {

    fun opening(name: String): String =
        "我是$name，拆弹手册在我手里。把面板信息报给我——点下面的「汇报面板」一键上报，或直接描述你看到的。"

    fun reply(module: DefuseModule, userText: String, reported: Boolean): String = when {
        reported || userText.contains("汇报") || userText.contains("报告") -> instruction(module)
        userText.contains("下一步") || userText.contains("继续") -> "继续：把当前面板完整报给我，我再对照手册给指令。"
        userText.contains("再解释") || userText.contains("没听懂") -> instruction(module)
        else -> "我这边看不到面板。请点「汇报面板」一键上报，或告诉我：线的颜色顺序 / 面板符号 / 按钮颜色文字和指示灯状态。"
    }

    fun instruction(module: DefuseModule): String = when (module) {
        is DefuseModule.Wires -> "收到。按手册判断：剪第 ${module.answerIndex} 根（从左到右）。"
        is DefuseModule.Keypad -> "收到。按顺序点：${module.answerOrder.joinToString(" ")}。"
        is DefuseModule.Button -> "收到。操作：${module.answerAction.label}。"
    }

    fun onStrike(): String = "等一下！刚才那次操作不对。别慌，重新核对面板再汇报一次。"

    fun onDefused(): String = "模块拆除了！干得漂亮，检查下一个。"
}
