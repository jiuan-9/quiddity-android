package com.quiddity.app.util

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/**
 * [CryptoUtils] + [SecretKeyDerivation] 单元测试（深层重构 v3 测试基础设施）。
 *
 * ## 关键不变量
 *
 * 1. 派生密钥必须 32 字节（AES-256）
 * 2. 加解密往返必须保留原文
 * 3. 篡改密文必须抛 [CryptoUtils.DecryptFailure.AuthenticationFailed]
 * 4. Base64 损坏必须抛 [CryptoUtils.DecryptFailure.Malformed]
 * 5. **跨设备可解密**（这是为什么不用 Android Keystore 的根本原因）
 */
class CryptoUtilsTest {

    // 期望的明文密钥，用于验证派生正确性
    private val EXPECTED_PLAIN = "Quiddity-Android-2026-SecretKey!"
    private val EXPECTED_BYTES = EXPECTED_PLAIN.toByteArray(Charsets.UTF_8)

    @Test
    fun `SecretKeyDerivation produces 32-byte key matching expected plaintext`() {
        val secret = SecretKeyDerivation.obtainSecret()
        assertEquals(32, secret.size, "AES-256 密钥必须 32 字节")
        assertContentEquals(EXPECTED_BYTES, secret, "派生密钥必须等于明文")
    }

    @Test
    fun `encrypt then decrypt returns original plaintext`() {
        val plain = "sk-test-1234567890abcdef"
        val encrypted = CryptoUtils.encrypt(plain)
        val decrypted = CryptoUtils.decrypt(encrypted)
        assertEquals(plain, decrypted, "加解密往返必须保留原文")
    }

    @Test
    fun `encrypt of empty string returns empty string`() {
        assertEquals("", CryptoUtils.encrypt(""))
    }

    @Test
    fun `decrypt of empty string throws Empty failure`() {
        assertFailsWith<CryptoUtils.DecryptFailure.Empty> {
            CryptoUtils.decrypt("")
        }
    }

    @Test
    fun `decrypt of invalid base64 throws Malformed failure`() {
        assertFailsWith<CryptoUtils.DecryptFailure.Malformed> {
            CryptoUtils.decrypt("not-valid-base64-!!!@@@")
        }
    }

    @Test
    fun `decrypt of too-short ciphertext throws Malformed failure`() {
        // 只有 IV（12 字节），没有密文——长度不足
        val shortCiphertext = java.util.Base64.getEncoder().encodeToString(ByteArray(12))
        assertFailsWith<CryptoUtils.DecryptFailure.Malformed> {
            CryptoUtils.decrypt(shortCiphertext)
        }
    }

    @Test
    fun `tampered ciphertext throws AuthenticationFailed`() {
        // 用足够长的明文确保密文 Base64 字符串里不只有 4 字符
        // （避免篡改时把 padding 弄坏——修复了原版 `swap last 4 chars` 导致的
        //  `Input byte array has wrong 4-byte ending unit` 失败问题）。
        val plain = "sk-test-tamper-detection-longer-plaintext-to-ensure-base64-padding-safe"
        val encrypted = CryptoUtils.encrypt(plain)

        // Base64 字符集，挑一个合法字符做替换不会破坏 padding 结构
        val base64Alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        // 选距离末尾足够远的位置（> 末尾 padding 区间），并保证原字符不是 'A'（避免替换后无变化）
        val safeIdx = encrypted.length / 2
        val originalChar = encrypted[safeIdx]
        val replacement = base64Alphabet[(
            (base64Alphabet.indexOf(originalChar).coerceAtLeast(0) + 1) % base64Alphabet.length
        )]
        val tampered = encrypted.toCharArray().also { it[safeIdx] = replacement }.concatToString()

        // 防止意外：保证篡改后字符串确实不同
        assertNotEquals(encrypted, tampered)
        // 防止意外：保证篡改字符串仍是合法 Base64
        runCatching { java.util.Base64.getDecoder().decode(tampered) }
            .onFailure { throw AssertionError("篡改后必须是合法 Base64，但解析失败：${it.message}") }

        // 期望抛 AuthenticationFailed（说明 GCM 认证标签成功检测到密文被改动）
        assertFailsWith<CryptoUtils.DecryptFailure.AuthenticationFailed> {
            CryptoUtils.decrypt(tampered)
        }
    }

    @Test
    fun `two encryptions of same plaintext produce different ciphertexts (random IV)`() {
        // GCM 模式使用随机 IV——相同明文应产生不同密文
        val plain = "sk-test-random-iv"
        val c1 = CryptoUtils.encrypt(plain)
        val c2 = CryptoUtils.encrypt(plain)
        assertNotEquals(c1, c2, "GCM 随机 IV 应使相同明文产生不同密文")
    }

    @Test
    fun `Chinese plaintext is preserved through encrypt-decrypt roundtrip`() {
        val plain = "测试中文 API Key 密钥：sk-中文-12345"
        val encrypted = CryptoUtils.encrypt(plain)
        val decrypted = CryptoUtils.decrypt(encrypted)
        assertEquals(plain, decrypted)
    }
}
