package com.quiddity.app.domain.defuse

import kotlin.random.Random

/*
 * 拆弹小队领域层（纯 Kotlin，无 Android 依赖，可单测）。
 *
 * 合作机制：玩家看到炸弹面板，LLM 搭档持有拆弹手册（见 DefuseLlmPrompt），
 * 双方必须互相配合才能拆除模块；本层负责模块生成与操作校验。
 */

/** 拆弹难度：模块数量、总时限、生命值、对手队伍用时系数。 */
enum class DefuseDifficulty(
    val label: String,
    val description: String,
    val moduleCount: Int,
    val timeSeconds: Int,
    val lives: Int,
    val opponentFactor: Double
) {
    EASY("简单", "2 个模块，对手较慢，适合热身", 2, 150, 3, 1.18),
    NORMAL("普通", "3 个模块，势均力敌", 3, 180, 3, 1.0),
    HARD("困难", "4 个模块，对手更快，只有 2 条命", 4, 200, 2, 0.86)
}

/** 电线颜色。 */
enum class WireColor(val label: String) {
    RED("红"),
    BLUE("蓝"),
    YELLOW("黄"),
    WHITE("白"),
    GREEN("绿")
}

/** 按钮颜色。 */
enum class ButtonColor(val label: String) {
    RED("红色"),
    BLUE("蓝色"),
    YELLOW("黄色"),
    WHITE("白色")
}

/** 按钮文字。 */
enum class ButtonLabel(val text: String) {
    DETONATE("引爆"),
    HOLD("保持"),
    ABORT("中止"),
    PRESS("按下")
}

/** 按钮可执行的操作。 */
enum class ButtonAction(val label: String) {
    CLICK("点击"),
    HOLD("长按 1 秒")
}

/** 模块类型。 */
enum class DefuseModuleType(val label: String) {
    WIRES("电线"),
    KEYPAD("密码锁"),
    BUTTON("按钮")
}

// ===== 电线模块规则（与 DefuseLlmPrompt.MANUAL 保持一致） =====

object WireRules {

    const val MANUAL = "【电线模块】\n面板上有 5 根线，从左到右编号 1-5。按顺序判断，命中即执行：\n" +
        "1. 第 1 根是红色 → 剪第 2 根\n" +
        "2. 否则，最后一根是蓝色 → 剪最后一根\n" +
        "3. 否则，红色线恰好 2 根 → 剪第 4 根\n" +
        "4. 否则 → 剪第 1 根"

    /** 返回应剪的线号（1 起）。 */
    fun answer(wires: List<WireColor>): Int {
        require(wires.size == 5) { "电线模块固定 5 根线" }
        return when {
            wires[0] == WireColor.RED -> 2
            wires.last() == WireColor.BLUE -> 5
            wires.count { it == WireColor.RED } == 2 -> 4
            else -> 1
        }
    }
}

// ===== 密码锁模块规则（与 DefuseLlmPrompt.MANUAL 保持一致） =====

object KeypadRules {

    /** 全局符号顺序：按此顺序点按面板上出现的符号。 */
    val GLYPHS = listOf("☀", "★", "☾", "♥", "✿", "◈")

    const val MANUAL = "【密码锁模块】\n面板上有 4 个符号。按以下全局顺序依次点按（只点面板上出现的）：\n" +
        "☀ → ★ → ☾ → ♥ → ✿ → ◈"

    fun order(symbols: List<String>): List<String> =
        symbols.sortedBy { GLYPHS.indexOf(it) }
}

// ===== 按钮模块规则（与 DefuseLlmPrompt.MANUAL 保持一致） =====

object ButtonRules {

    const val MANUAL = "【按钮模块】\n面板上有一个按钮（颜色+文字）和一个指示灯（亮/灭）。按顺序判断，命中即执行：\n" +
        "1. 文字是「引爆」→ 点击按钮\n" +
        "2. 否则，按钮是红色 → 长按 1 秒\n" +
        "3. 否则，按钮是蓝色且指示灯亮 → 点击按钮\n" +
        "4. 否则，指示灯灭 → 长按 1 秒\n" +
        "5. 否则 → 点击按钮"

    fun answer(module: DefuseModule.Button): ButtonAction = when {
        module.label == ButtonLabel.DETONATE -> ButtonAction.CLICK
        module.color == ButtonColor.RED -> ButtonAction.HOLD
        module.color == ButtonColor.BLUE && module.lightOn -> ButtonAction.CLICK
        !module.lightOn -> ButtonAction.HOLD
        else -> ButtonAction.CLICK
    }
}

/** 炸弹模块（不可变；中途状态如已剪线、已输入符号随 session 更新）。 */
sealed interface DefuseModule {
    val type: DefuseModuleType

    data class Wires(
        val wires: List<WireColor>,
        val damaged: Set<Int> = emptySet()
    ) : DefuseModule {
        override val type = DefuseModuleType.WIRES
        val answerIndex: Int get() = WireRules.answer(wires)
    }

    data class Keypad(
        val symbols: List<String>,
        val entered: List<String> = emptyList()
    ) : DefuseModule {
        override val type = DefuseModuleType.KEYPAD
        val answerOrder: List<String> get() = KeypadRules.order(symbols)
    }

    data class Button(
        val color: ButtonColor,
        val label: ButtonLabel,
        val lightOn: Boolean
    ) : DefuseModule {
        override val type = DefuseModuleType.BUTTON
        val answerAction: ButtonAction get() = ButtonRules.answer(this)
    }
}

/** 一局拆弹：固定模块序列 + 时限 + 对手队伍用时。 */
data class DefuseGame(
    val id: String,
    val difficulty: DefuseDifficulty,
    val modules: List<DefuseModule>,
    val totalTimeMs: Long,
    val opponentTimeMs: Long
)

/** 对局进行态：当前模块、剩余生命、失误次数。 */
data class DefuseSession(
    val game: DefuseGame,
    val modules: List<DefuseModule> = game.modules,
    val moduleIndex: Int = 0,
    val lives: Int = game.difficulty.lives,
    val strikes: Int = 0,
    val exploded: Boolean = false,
    val combo: Int = 0,
    val maxCombo: Int = 0
) {
    val currentModule: DefuseModule? get() = modules.getOrNull(moduleIndex)
    val allCleared: Boolean get() = moduleIndex >= modules.size
    val clearedCount: Int get() = moduleIndex.coerceAtMost(modules.size)
}

/** 一次操作的判定结果。 */
sealed interface DefuseMove {
    /** 模块拆除成功（可能已全部清空）。 */
    data class Defused(val session: DefuseSession) : DefuseMove

    /** 操作失误：扣一条命（可能爆炸）。 */
    data class Struck(val session: DefuseSession) : DefuseMove

    /** 密码锁部分输入（未完成、未失误）。 */
    data class Progressed(val session: DefuseSession) : DefuseMove

    data object Invalid : DefuseMove
}

/** 剪线：正确则拆除，错误则扣命并将该线标记为已剪。 */
fun DefuseSession.cutWire(index: Int): DefuseMove {
    val module = currentModule as? DefuseModule.Wires ?: return DefuseMove.Invalid
    if (index !in 1..module.wires.size || index in module.damaged) return DefuseMove.Invalid
    if (index == module.answerIndex) {
        return DefuseMove.Defused(advanced())
    }
    return withDamaged(module, module.damaged + index)
}

/** 密码锁：按序点按符号；错误符号扣命并清空已输入。 */
fun DefuseSession.pressKeypad(symbol: String): DefuseMove {
    val module = currentModule as? DefuseModule.Keypad ?: return DefuseMove.Invalid
    if (symbol !in module.symbols || symbol in module.entered) return DefuseMove.Invalid
    val expected = module.answerOrder.getOrNull(module.entered.size) ?: return DefuseMove.Invalid
    if (symbol != expected) {
        return withModule(module.copy(entered = emptyList()), strike = true)
    }
    val entered = module.entered + symbol
    if (entered.size == module.symbols.size) {
        return DefuseMove.Defused(advanced())
    }
    return withModule(module.copy(entered = entered))
}

/** 按钮：操作匹配规则则拆除，否则扣命。 */
fun DefuseSession.pressButton(action: ButtonAction): DefuseMove {
    val module = currentModule as? DefuseModule.Button ?: return DefuseMove.Invalid
    if (action == module.answerAction) {
        return DefuseMove.Defused(advanced())
    }
    return withModule(module, strike = true)
}

/** 生成一局：按难度随机模块序列与对手用时。 */
object DefuseGenerator {

    fun generate(difficulty: DefuseDifficulty, random: Random = Random.Default): DefuseGame {
        val baseTypes = buildList {
            add(DefuseModuleType.WIRES)
            add(DefuseModuleType.KEYPAD)
            if (difficulty.moduleCount >= 3) add(DefuseModuleType.BUTTON)
        }
        val modules = List(difficulty.moduleCount) { i ->
            val type = if (i < baseTypes.size) baseTypes[i] else baseTypes.random(random)
            createModule(type, random)
        }.shuffled(random)
        val opponentMs = (
            difficulty.timeSeconds * 1000L *
                difficulty.opponentFactor *
                (0.95 + random.nextDouble() * 0.1)
            ).toLong().coerceAtLeast(30_000L)
        return DefuseGame(
            id = "defuse_${System.currentTimeMillis()}_${random.nextInt(10000)}",
            difficulty = difficulty,
            modules = modules,
            totalTimeMs = difficulty.timeSeconds * 1000L,
            opponentTimeMs = opponentMs
        )
    }

    private fun createModule(type: DefuseModuleType, random: Random): DefuseModule = when (type) {
        DefuseModuleType.WIRES -> DefuseModule.Wires(
            wires = List(5) { WireColor.entries.random(random) }
        )
        DefuseModuleType.KEYPAD -> DefuseModule.Keypad(
            symbols = KeypadRules.GLYPHS.shuffled(random).take(4)
        )
        DefuseModuleType.BUTTON -> DefuseModule.Button(
            color = ButtonColor.entries.random(random),
            label = ButtonLabel.entries.random(random),
            lightOn = random.nextBoolean()
        )
    }
}

/** 结算得分：拆除模块、剩余生命、剩余时间加分，失误扣分，难度与求助影响倍率。 */
object DefuseScoring {

    fun score(
        difficulty: DefuseDifficulty,
        clearedModules: Int,
        livesLeft: Int,
        remainingSeconds: Int,
        strikes: Int,
        maxCombo: Int,
        helpUsed: Boolean
    ): Int {
        if (clearedModules == 0) return 0
        val base = clearedModules * 500 + livesLeft.coerceAtLeast(0) * 300 +
            remainingSeconds.coerceAtLeast(0) * 5 - strikes * 80 + maxCombo.coerceAtLeast(0) * 100
        val difficultyMultiplier = when (difficulty) {
            DefuseDifficulty.EASY -> 1.0
            DefuseDifficulty.NORMAL -> 1.5
            DefuseDifficulty.HARD -> 2.0
        }
        val helpFactor = if (helpUsed) 0.7 else 1.0
        return (base * difficultyMultiplier * helpFactor).toInt().coerceAtLeast(0)
    }
}

private fun DefuseSession.withDamaged(module: DefuseModule.Wires, damaged: Set<Int>): DefuseMove {
    return withModule(module.copy(damaged = damaged), strike = true)
}

private fun DefuseSession.withModule(module: DefuseModule, strike: Boolean = false): DefuseMove {
    val modules = modules.toMutableList().also { it[moduleIndex] = module }
    if (!strike) return DefuseMove.Progressed(copy(modules = modules))
    val livesLeft = lives - 1
    return DefuseMove.Struck(
        copy(
            modules = modules,
            lives = livesLeft.coerceAtLeast(0),
            strikes = strikes + 1,
            exploded = livesLeft <= 0,
            combo = 0
        )
    )
}

private fun DefuseSession.advanced(): DefuseSession {
    val nextCombo = combo + 1
    return copy(
        moduleIndex = moduleIndex + 1,
        combo = nextCombo,
        maxCombo = maxOf(maxCombo, nextCombo)
    )
}
