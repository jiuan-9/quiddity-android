package com.quiddity.app.util

import java.security.SecureRandom
import java.util.UUID

/**
 * 全局 ID 生成工具。
 *
 * - 单一真相源：所有 ID 生成都通过本工具。
 * - 提供类型安全的前缀枚举（[Prefix]），避免硬编码字符串导致的拼写错误。
 * - 时间有序：前 48 位为毫秒时间戳，天然按创建时间排序，数据库索引更友好
 *
 * 性能说明：
 * - SecureRandom 单次调用耗时 < 10μs，远优于数据库自增 ID 方案
 */
object IdGenerator {

    /**
     * ID 前缀枚举：集中维护所有 ID 类型的前缀字符串。
     */
    enum class Prefix(val value: String) {
        /** 会话 ID（前缀 `conv_`）。 */
        CONVERSATION("conv_"),
        /** 用户消息 ID（前缀 `msg_`）。 */
        USER_MESSAGE("msg_"),
        /** AI 流式消息 ID（前缀 `ai_`）。用于 [com.quiddity.app.domain.MessageStreamCoordinator]。 */
        AI_MESSAGE("ai_"),
        /** API 名册条目 ID（前缀 `cat_`）。 */
        CATALOG_ENTRY("cat_"),
        /** 头像裁剪临时文件 ID（前缀 `avatar_`）。 */
        AVATAR_TEMP("avatar_"),
        /** 数据导出文件 ID（前缀 `export_`）。 */
        EXPORT_FILE("export_")
    }

    private val secureRandom = SecureRandom()

    /**
     * 生成一个全局唯一 ID。
     *
     * @param prefix ID 类型前缀，参见 [Prefix]。
     */
    fun newId(prefix: Prefix): String = "${prefix.value}${newUuidV7()}"

    /**
     * 生成一个 UUID v7 字符串。
     *
     * 适用场景：
     * - [com.quiddity.app.data.repo.ChatRepository] 中流式协调器的 runId（嵌套到 AI 消息 ID 中）。
     * - 任何需要 UUID 但不需要语义前缀的内部标识。
     *
     * ```
     *  0                   1                   2                   3
     *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |                         unix_ts_ms                            |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |          unix_ts_ms           |  ver  |       rand_a          |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |var|                       rand_b                              |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * |                       rand_b (continued)                      |
     * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
     * ```
     *
     * - bits 0-47: 48 位 Unix 毫秒时间戳
     * - bits 48-51: 版本号 = 0x7
     * - bits 52-63: 12 位随机数
     * - bits 64-65: 变体 = 0b10
     * - bits 66-127: 62 位随机数
     */
    fun newUuid(): String = newUuidV7()

    /**
     * 生成 UUID v7 字符串。
     *
     * 内部实现使用 [SecureRandom] 保证密码学级随机性。
     * 时间戳取自 [System.currentTimeMillis]，精度毫秒。
     */
    fun newUuidV7(): String {
        val timestampMs = System.currentTimeMillis()

        // 48 位时间戳拆为高 32 位 + 低 16 位
        val tsHigh = (timestampMs ushr 16).toInt()
        val tsLow = (timestampMs and 0xFFFF).toInt()

        // 12 位随机 + 4 位版本(0x7) → 组合为 16 位
        val randA = secureRandom.nextInt() and 0x0FFF
        val versionAndRandA = (0x7 shl 12) or randA

        // 62 位随机 + 2 位变体(0b10) → 组合为 64 位
        val randBHigh = secureRandom.nextInt() and 0x3FFF
        val variantAndRandBHigh = (0x2 shl 14) or randBHigh
        val randBLow = secureRandom.nextLong() and 0xFFFFFFFFFFFFL

        // 组装 MSB (bits 0-63) 和 LSB (bits 64-127)
        val msb = (tsHigh.toLong() and 0xFFFFFFFFL shl 32) or
            (tsLow.toLong() and 0xFFFFL shl 16) or
            (versionAndRandA.toLong() and 0xFFFFL)
        val lsb = (variantAndRandBHigh.toLong() shl 48) or randBLow

        return UUID(msb, lsb).toString()
    }
}
