package com.quiddity.app.domain.board

/*
 * 棋盘对局领域层（纯 Kotlin，无 Android 依赖，可单测）。
 *
 * - 五子棋：15×15，先连成五子者胜。
 * - 围棋：9×9 简化规则——提子、禁着（自杀）、位置超 ko（棋盘全局面指纹去重）、
 *   停一手（双方连停结束）、认输；结束用简化数子计分（黑子+黑空 vs 白子+白空+贴目）。
 */

/** 棋子（含空位）。 */
enum class Stone(val code: Int, val label: String) {
    EMPTY(0, "空"),
    BLACK(1, "黑"),
    WHITE(2, "白");

    fun opponent(): Stone = when (this) {
        BLACK -> WHITE
        WHITE -> BLACK
        EMPTY -> EMPTY
    }

    companion object {
        fun fromCode(code: Int): Stone = when (code) {
            BLACK.code -> BLACK
            WHITE.code -> WHITE
            else -> EMPTY
        }
    }
}

/** 棋种：五子棋 / 围棋（含棋盘尺寸）。 */
enum class BoardGameType(val size: Int, val displayName: String) {
    GOMOKU(15, "五子棋"),
    GO(9, "围棋");

    val isGo: Boolean get() = this == GO
}

/** 落子坐标（0 起）。 */
data class Move(val row: Int, val col: Int) {
    init {
        require(row >= 0 && col >= 0) { "坐标不能为负" }
    }
}

/** 落子结果。 */
sealed interface MoveOutcome {
    data class Played(
        val state: BoardState,
        val captured: Int,
        val winner: Stone?,
        val gameOver: Boolean
    ) : MoveOutcome

    data object Rejected : MoveOutcome
    data object Ko : MoveOutcome
}

/** 停一手结果。 */
sealed interface PassOutcome {
    data class Done(val state: BoardState, val gameOver: Boolean, val winner: Stone?) : PassOutcome
}

/** 围棋简化计分结果。 */
data class GoScore(val black: Double, val white: Double, val winner: Stone?)

/**
 * 不可变棋盘状态。
 *
 * [grid] 为一维列表，索引 = row * size + col，取值见 [Stone.code]。
 * [history] 保存每一步后的全局指纹，用于围棋位置超 ko 去重。
 */
data class BoardState(
    val gameType: BoardGameType,
    val grid: List<Int> = List(gameType.size * gameType.size) { Stone.EMPTY.code },
    val current: Stone = Stone.BLACK,
    val lastMove: Move? = null,
    val consecutivePasses: Int = 0,
    val moveCount: Int = 0,
    val history: List<String> = emptyList(),
    val gameOver: Boolean = false,
    val winner: Stone? = null,
    val endReason: String = ""
) {

    val size: Int get() = gameType.size

    fun stoneAt(row: Int, col: Int): Stone {
        if (row !in 0 until size || col !in 0 until size) return Stone.EMPTY
        return Stone.fromCode(grid[row * size + col])
    }

    /** 当前棋盘全局指纹。 */
    fun fingerprint(): String = grid.joinToString("")

    fun applyMove(row: Int, col: Int): MoveOutcome = applyMove(Move(row, col))

    fun applyMove(move: Move): MoveOutcome {
        if (gameOver) return MoveOutcome.Rejected
        if (move.row !in 0 until size || move.col !in 0 until size) return MoveOutcome.Rejected
        if (stoneAt(move.row, move.col) != Stone.EMPTY) return MoveOutcome.Rejected
        return if (gameType.isGo) applyGoMove(move) else applyGomokuMove(move)
    }

    fun pass(): PassOutcome {
        if (gameOver) return PassOutcome.Done(this, true, winner)
        val passes = if (gameType.isGo) consecutivePasses + 1 else 0
        val next = copy(
            current = current.opponent(),
            consecutivePasses = passes
        )
        if (gameType.isGo && passes >= 2) {
            val score = score()
            return PassOutcome.Done(
                next.copy(gameOver = true, winner = score.winner, endReason = "双方停一手"),
                true,
                score.winner
            )
        }
        return PassOutcome.Done(next, false, null)
    }

    /** 当前方认输，对方获胜。 */
    fun resign(): BoardState = copy(gameOver = true, winner = current.opponent(), endReason = "认输")

    /**
     * 简化数子计分：黑子数 + 黑围空 vs 白子数 + 白围空 + 贴目。
     * 空点区域边界只含单色时计为该色地盘；公海（双色边界）不计。
     */
    fun score(): GoScore {
        val visited = BooleanArray(size * size)
        var blackTerritory = 0
        var whiteTerritory = 0
        for (idx in grid.indices) {
            if (grid[idx] != Stone.EMPTY.code || visited[idx]) continue
            val boundary = mutableSetOf<Stone>()
            val queue = ArrayDeque<Int>()
            queue.add(idx)
            visited[idx] = true
            while (queue.isNotEmpty()) {
                val cur = queue.removeFirst()
                val r = cur / size
                val c = cur % size
                for ((dr, dc) in DIRECTIONS) {
                    val nr = r + dr
                    val nc = c + dc
                    if (nr !in 0 until size || nc !in 0 until size) continue
                    val ni = nr * size + nc
                    val s = Stone.fromCode(grid[ni])
                    if (s == Stone.EMPTY) {
                        if (!visited[ni]) {
                            visited[ni] = true
                            queue.add(ni)
                        }
                    } else {
                        boundary += s
                    }
                }
            }
            if (boundary.size == 1) {
                when (boundary.first()) {
                    Stone.BLACK -> blackTerritory++
                    Stone.WHITE -> whiteTerritory++
                    else -> Unit
                }
            }
        }
        val black = (grid.count { it == Stone.BLACK.code } + blackTerritory).toDouble()
        val white = (grid.count { it == Stone.WHITE.code } + whiteTerritory).toDouble() + GO_KOMI
        val winner = when {
            black > white -> Stone.BLACK
            white > black -> Stone.WHITE
            else -> null
        }
        return GoScore(black, white, winner)
    }

    private fun applyGomokuMove(move: Move): MoveOutcome {
        val idx = move.row * size + move.col
        val newGrid = grid.toMutableList().apply { this[idx] = current.code }
        val winner = if (hasFiveInRow(move, newGrid)) current else null
        val next = copy(
            grid = newGrid,
            current = current.opponent(),
            lastMove = move,
            moveCount = moveCount + 1,
            history = history + newGrid.joinToString(""),
            gameOver = winner != null,
            winner = winner,
            endReason = if (winner != null) "${current.label}五子连珠" else ""
        )
        return MoveOutcome.Played(next, 0, winner, winner != null)
    }

    private fun applyGoMove(move: Move): MoveOutcome {
        val idx = move.row * size + move.col
        val working = grid.toMutableList()
        working[idx] = current.code
        var captured = 0
        for ((dr, dc) in DIRECTIONS) {
            val nr = move.row + dr
            val nc = move.col + dc
            if (nr !in 0 until size || nc !in 0 until size) continue
            if (Stone.fromCode(working[nr * size + nc]) == current.opponent() &&
                groupLiberties(working, nr, nc) == 0
            ) {
                captured += removeGroup(working, nr, nc)
            }
        }
        if (groupLiberties(working, move.row, move.col) == 0) {
            return MoveOutcome.Rejected
        }
        val fingerprint = working.joinToString("")
        if (fingerprint in history) return MoveOutcome.Ko
        val next = copy(
            grid = working,
            current = current.opponent(),
            lastMove = move,
            consecutivePasses = 0,
            moveCount = moveCount + 1,
            history = history + fingerprint
        )
        return MoveOutcome.Played(next, captured, null, false)
    }

    private fun hasFiveInRow(move: Move, grid: List<Int>): Boolean {
        val me = current.code
        for ((dr, dc) in LINE_DIRECTIONS) {
            var count = 1
            var r = move.row + dr
            var c = move.col + dc
            while (r in 0 until size && c in 0 until size && grid[r * size + c] == me) {
                count++
                r += dr
                c += dc
            }
            r = move.row - dr
            c = move.col - dc
            while (r in 0 until size && c in 0 until size && grid[r * size + c] == me) {
                count++
                r -= dr
                c -= dc
            }
            if (count >= 5) return true
        }
        return false
    }

    private fun groupLiberties(grid: List<Int>, row: Int, col: Int): Int {
        val me = Stone.fromCode(grid[row * size + col])
        val seen = BooleanArray(size * size)
        val queue = ArrayDeque<Int>()
        queue.add(row * size + col)
        seen[row * size + col] = true
        var liberties = 0
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val r = cur / size
            val c = cur % size
            for ((dr, dc) in DIRECTIONS) {
                val nr = r + dr
                val nc = c + dc
                if (nr !in 0 until size || nc !in 0 until size) continue
                val ni = nr * size + nc
                val s = Stone.fromCode(grid[ni])
                when (s) {
                    Stone.EMPTY -> liberties++
                    me -> if (!seen[ni]) {
                        seen[ni] = true
                        queue.add(ni)
                    }
                    else -> Unit
                }
            }
        }
        return liberties
    }

    private fun removeGroup(grid: MutableList<Int>, row: Int, col: Int): Int {
        val target = grid[row * size + col]
        val queue = ArrayDeque<Int>()
        val seen = BooleanArray(size * size)
        queue.add(row * size + col)
        seen[row * size + col] = true
        var removed = 0
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            if (grid[cur] != target) continue
            grid[cur] = Stone.EMPTY.code
            removed++
            val r = cur / size
            val c = cur % size
            for ((dr, dc) in DIRECTIONS) {
                val nr = r + dr
                val nc = c + dc
                if (nr in 0 until size && nc in 0 until size) {
                    val ni = nr * size + nc
                    if (!seen[ni] && grid[ni] == target) {
                        seen[ni] = true
                        queue.add(ni)
                    }
                }
            }
        }
        return removed
    }

    companion object {
        /** 围棋贴目（白方），简化计分使用。 */
        const val GO_KOMI = 7.5

        private val DIRECTIONS = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        private val LINE_DIRECTIONS = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)
    }
}
