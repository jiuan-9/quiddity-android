package com.quiddity.app.domain.board

import kotlin.random.Random

/*
 * 本地兜底 AI：LLM 不可用 / 落子非法时的降级方案。
 * - 五子棋：威胁评分（进攻 + 防守 + 中心偏好）。
 * - 围棋：优先提子 → 己方危棋延气 → 随机合法落子。
 */
object BoardBot {

    private val LINE_DIRECTIONS = listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)

    /** 返回一步合法落子；棋盘已满返回 null。 */
    fun nextMove(state: BoardState, random: Random = Random.Default): Move? {
        if (state.gameOver) return null
        return if (state.gameType.isGo) goMove(state, random) else gomokuMove(state)
    }

    private fun gomokuMove(state: BoardState): Move? {
        val me = state.current
        val opponent = me.opponent()
        val size = state.size
        val center = size / 2
        var best: Move? = null
        var bestScore = -1
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (state.stoneAt(r, c) != Stone.EMPTY) continue
                val attack = evaluateCell(state, r, c, me)
                val defense = evaluateCell(state, r, c, opponent)
                val centerBonus = 8 - (kotlin.math.abs(r - center) + kotlin.math.abs(c - center))
                val score = attack * 10 + defense * 8 + centerBonus
                if (score > bestScore) {
                    bestScore = score
                    best = Move(r, c)
                }
            }
        }
        return best
    }

    private fun evaluateCell(state: BoardState, row: Int, col: Int, stone: Stone): Int {
        var total = 0
        for ((dr, dc) in LINE_DIRECTIONS) {
            var count = 1
            var r = row + dr
            var c = col + dc
            while (r in 0 until state.size && c in 0 until state.size && state.stoneAt(r, c) == stone) {
                count++
                r += dr
                c += dc
            }
            r = row - dr
            c = col - dc
            while (r in 0 until state.size && c in 0 until state.size && state.stoneAt(r, c) == stone) {
                count++
                r -= dr
                c -= dc
            }
            total += when {
                count >= 5 -> 1_000_000
                count == 4 -> 10_000
                count == 3 -> 100
                count == 2 -> 10
                else -> 1
            }
        }
        return total
    }

    private fun goMove(state: BoardState, random: Random): Move? {
        val size = state.size
        val me = state.current
        val opponent = me.opponent()

        // 1. 提子：找对方仅剩一口气的棋串，落在那口气上
        scanGroups(state, opponent)?.let { return it }
        // 2. 己方危棋延气
        scanGroups(state, me)?.let { return it }
        // 3. 随机合法落子（偏向中腹附近）
        val candidates = (0 until size * size).shuffled(random)
        for (idx in candidates) {
            val r = idx / size
            val c = idx % size
            if (state.stoneAt(r, c) != Stone.EMPTY) continue
            if (state.applyMove(r, c) is MoveOutcome.Played) return Move(r, c)
        }
        return null
    }

    /**
     * 扫描所有目标棋串；若某串只剩一口气，返回该口气位置；否则 null。
     */
    private fun scanGroups(state: BoardState, target: Stone): Move? {
        val size = state.size
        val visited = BooleanArray(size * size)
        for (idx in 0 until size * size) {
            if (state.grid[idx] != target.code || visited[idx]) continue
            val liberties = mutableSetOf<Int>()
            val queue = ArrayDeque<Int>()
            queue.add(idx)
            visited[idx] = true
            while (queue.isNotEmpty()) {
                val cur = queue.removeFirst()
                val r = cur / size
                val c = cur % size
                for ((dr, dc) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                    val nr = r + dr
                    val nc = c + dc
                    if (nr !in 0 until size || nc !in 0 until size) continue
                    val ni = nr * size + nc
                    when (Stone.fromCode(state.grid[ni])) {
                        Stone.EMPTY -> liberties.add(ni)
                        target -> if (!visited[ni]) {
                            visited[ni] = true
                            queue.add(ni)
                        }
                        else -> Unit
                    }
                }
            }
            if (liberties.size == 1) {
                val li = liberties.first()
                val r = li / size
                val c = li % size
                val move = Move(r, c)
                if (state.applyMove(move) is MoveOutcome.Played) return move
            }
        }
        return null
    }
}
