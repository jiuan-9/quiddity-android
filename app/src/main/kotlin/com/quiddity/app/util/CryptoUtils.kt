package com.quiddity.app.util

import android.util.Log
import java.security.SecureRandom
import java.util.Base64 as JvmBase64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * API Key 加密工具。
 *
 * 采用 AES-256-GCM 对称加密。
 * 密钥派生自固定应用密钥（设备无关，便于导出后导入解密）。
 *
 * 注：本工具不依赖 Android Keystore，因为导出/导入需要跨设备可解密。
 * 安全性依赖用户设备本身的沙箱保护。
 */
object CryptoUtils {

    private const val TAG = "CryptoUtils"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12

    // 密钥通过 [SecretKeyDerivation] 在运行时从拆分 Base64 拼接派生，
    // `strings` 看到的是无意义字符，无法直接还原。
    private val secretKey: SecretKey =
        SecretKeySpec(SecretKeyDerivation.obtainSecret(), "AES")

    /**
     * 解密失败原因。
     *
     * 让调用方 [ChatRepository] 区分以下场景并给出精确提示：
     * "Base64 损坏"、"GCM 标签校验失败（被篡改/数据损坏）"、"Cipher 不可用"。
     */
    sealed class DecryptFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
        /** 输入字符串为空——可能 API Key 字段从未写入 */
        class Empty : DecryptFailure("encrypted text is empty")
        /** Base64 格式损坏 / 长度不足 */
        class Malformed(cause: Throwable) : DecryptFailure("encrypted text is not valid base64", cause)
        /** GCM 认证标签校验失败——可能数据被篡改、磁盘损坏或密钥不匹配 */
        class AuthenticationFailed(cause: Throwable) : DecryptFailure("GCM authentication failed", cause)
        /** 其他 Cipher 初始化 / 系统级失败 */
        class CryptoError(cause: Throwable) : DecryptFailure("crypto operation failed", cause)
    }

    /** 加密明文，返回 Base64 字符串（包含 IV + 密文）。 */
    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val combined = iv + cipherText
        // - java.util.Base64 是 JDK 标准类，JVM 单元测试可运行
        // - getEncoder().encodeToString() 默认带 padding，与 Base64.NO_WRAP 兼容
        return JvmBase64.getEncoder().encodeToString(combined)
    }

    /**
     * 解密 [encrypted]（Base64 字符串）。
     *
     * GCM 是带认证的加密，认证失败通常意味着数据被篡改 / 磁盘损坏 / 密钥不匹配。
     * 抛出细分类型的 [DecryptFailure]，调用方按需降级（典型：向用户展示具体错误 + 提示重新填写）。
     */
    fun decrypt(encrypted: String): String {
        if (encrypted.isEmpty()) throw DecryptFailure.Empty()
        val combined = try {
            // 使用 java.util.Base64（与原 android.util.Base64.NO_WRAP 行为完全一致）
            JvmBase64.getDecoder().decode(encrypted)
        } catch (t: Throwable) {
            Log.w(TAG, "Base64 decode failed: ${t.javaClass.simpleName}")
            throw DecryptFailure.Malformed(t)
        }
        if (combined.size <= GCM_IV_LENGTH_BYTES) {
            Log.w(TAG, "decoded length too short: ${combined.size}")
            throw DecryptFailure.Malformed(IllegalStateException("decoded length $combined.size <= IV length"))
        }
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val cipherText = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)
        val cipher = try {
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            }
        } catch (t: Throwable) {
            Log.e(TAG, "cipher init failed", t)
            throw DecryptFailure.CryptoError(t)
        }
        return try {
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        } catch (t: javax.crypto.AEADBadTagException) {
            // GCM 认证失败——典型场景：数据被篡改 / 磁盘损坏 / 不同密钥加密的密文
            Log.e(TAG, "GCM auth tag mismatch — possible tampering or key mismatch", t)
            throw DecryptFailure.AuthenticationFailed(t)
        } catch (t: Throwable) {
            Log.e(TAG, "decrypt failed", t)
            throw DecryptFailure.CryptoError(t)
        }
    }
}
