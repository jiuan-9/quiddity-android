package com.quiddity.app.util

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64 as JvmBase64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/*
 * ============================================================================
 * 开发规范 (Development Specifications)
 * ============================================================================
 *
 * 1. 问题修复规范
 *    所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
 *    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
 *
 * 2. 代码注释规范
 *    文件内仅允许保留两类注释：
 *    - 当前规则说明注释（即本注释块）
 *    - 模块划分注释（用于标识代码功能模块边界）
 *    除此之外，禁止出现任何形式的代码注释（包括但不限于单行注释、多行调试注释等）。
 *
 * 3. 构建交付要求
 *    在完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK 文件。
 *    APK 文件需满足以下条件：
 *    - 签名有效且符合发布标准
 *    - 包含完整的功能模块
 *    - 经过基础性能测试和兼容性测试
 *    以便在真实设备环境中进行功能验证和性能评估。
 *
 * ============================================================================
 */



/**
 * API Key 加密工具（设备绑定）。
 *
 * 安全模型（只允许"特殊渠道"——即本机本应用——获取/使用 API）：
 * - 真机（Android 6+）：加密密钥由系统级 Android Keystore 生成并保管。
 *   支持硬件级保护的设备上密钥不可导出，反向工程拿不到主密钥；即使拿到
 *   导出的数据文件，换一台设备也解不出明文，导入时需重新填写 Key；
 * - JVM 单元测试：Keystore 不可用，回退到 [SecretKeyDerivation] 派生的
 *   固定密钥，保证纯 JVM 测试可运行（仅测试环境，不影响真机安全）。
 *
 * 旧数据迁移：升级前用固定密钥加密的旧密文无法用新密钥解密，通过
 * [isLegacyEncrypted] / [decryptLegacy] 识别，并由
 * [com.quiddity.app.data.repo.SettingsRepository.migrateLegacyApiKeysIfNeeded]
 * 自动用新密钥重新加密，用户无感知。
 */
object CryptoUtils {

    private const val TAG = "CryptoUtils"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12

    /** Android Keystore 中的密钥别名（同一应用内全局唯一）。 */
    private const val KEYSTORE_ALIAS = "quiddity_api_key"

    /** 当前生效密钥：真机为 Keystore 密钥，JVM 测试环境为派生密钥。 */
    private val currentKey: SecretKey by lazy { resolveCurrentKey() }

    /** 旧版固定密钥（仅用于升级迁移，见 [decryptLegacy]）。 */
    private val legacyKey: SecretKey by lazy {
        SecretKeySpec(SecretKeyDerivation.obtainSecret(), "AES")
    }

    /**
     * 是否处于 Android Keystore 可用环境。
     * JVM 单元测试（returnDefaultValues）中 [Build.VERSION.SDK_INT] 返回 0，
     * 走派生密钥分支；真机返回真实 SDK 版本，走 Keystore 分支。
     */
    private fun isAndroidKeystoreAvailable(): Boolean =
        try {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
        } catch (_: Throwable) {
            false
        }

    private fun resolveCurrentKey(): SecretKey {
        if (!isAndroidKeystoreAvailable()) return legacyKey
        return try {
            getOrCreateKeystoreKey()
        } catch (t: Throwable) {
            // 极少数 ROM 的 Keystore 实现异常：回退派生密钥，保证功能可用（安全降级并记录日志）
            Log.e(TAG, "Android Keystore 不可用，回退派生密钥", t)
            legacyKey
        }
    }

    /**
     * 从 Android Keystore 获取或创建 AES-256-GCM 密钥。
     * 密钥生成后由系统安全硬件保管（硬件支持时不可导出），应用只能引用其句柄加解密。
     */
    private fun getOrCreateKeystoreKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    /**
     * 解密失败原因。
     *
     * 让调用方 [com.quiddity.app.data.repo.ApiAccess] 区分以下场景并给出精确提示：
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
        return try {
            encryptWith(currentKey, plain)
        } catch (t: Throwable) {
            // 设备 Keystore 异常（如恢复/迁移后密钥失效）时回退派生密钥，
            // 保证"保存密钥"永不因加密失败而报错
            Log.e(TAG, "当前密钥加密失败，回退派生密钥", t)
            encryptWith(legacyKey, plain)
        }
    }

    /**
     * 解密 [encrypted]（Base64 字符串）。
     *
     * GCM 是带认证的加密，认证失败通常意味着数据被篡改 / 磁盘损坏 / 密钥不匹配。
     * 抛出细分类型的 [DecryptFailure]，调用方按需降级。
     */
    fun decrypt(encrypted: String): String = decryptWith(currentKey, encrypted)

    /**
     * 判断密文是否为旧版（固定密钥）加密：当前密钥解不开、但旧密钥能解开。
     * 用于升级后的自动迁移（见 SettingsRepository.migrateLegacyApiKeysIfNeeded）。
     */
    fun isLegacyEncrypted(encrypted: String): Boolean {
        if (encrypted.isEmpty()) return false
        return try {
            decrypt(encrypted)
            false
        } catch (e: DecryptFailure.AuthenticationFailed) {
            try {
                decryptWith(legacyKey, encrypted)
                true
            } catch (_: DecryptFailure) {
                false
            }
        } catch (_: DecryptFailure) {
            false
        }
    }

    /**
     * 用旧版（固定密钥）解密；仅用于迁移，解不出时抛 [DecryptFailure]。
     */
    fun decryptLegacy(encrypted: String): String = decryptWith(legacyKey, encrypted)

    /**
     * 是否可由本设备解密（当前密钥或旧密钥均可）。
     * 用于数据导入时判断"是否需要重新填写 Key"：任一密钥可解都视为可继续使用，
     * 旧密钥解出的数据会在启动迁移时自动换用新密钥。
     */
    fun isDecryptable(encrypted: String): Boolean {
        if (encrypted.isEmpty()) return true
        return try {
            decrypt(encrypted)
            true
        } catch (_: DecryptFailure) {
            try {
                decryptLegacy(encrypted)
                true
            } catch (_: DecryptFailure) {
                false
            }
        }
    }

    /** 供测试使用的旧版加密入口（与生产迁移路径对应）。 */
    internal fun encryptLegacy(plain: String): String =
        if (plain.isEmpty()) "" else encryptWith(legacyKey, plain)

    private fun encryptWith(key: SecretKey, plain: String): String {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val combined = iv + cipherText
        // java.util.Base64 是 JDK 标准类，JVM 单元测试可运行；
        // getEncoder().encodeToString() 默认带 padding，与 Base64.NO_WRAP 兼容
        return JvmBase64.getEncoder().encodeToString(combined)
    }

    private fun decryptWith(key: SecretKey, encrypted: String): String {
        if (encrypted.isEmpty()) throw DecryptFailure.Empty()
        val combined = try {
            // 使用 java.util.Base64（与旧 android.util.Base64.NO_WRAP 行为完全一致）
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
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
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
