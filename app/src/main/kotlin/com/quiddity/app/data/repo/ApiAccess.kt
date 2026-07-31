package com.quiddity.app.data.repo

import com.quiddity.app.data.model.ApiCatalogEntry
import com.quiddity.app.data.model.AppSettings
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.remote.ChatException
import com.quiddity.app.util.CryptoUtils

/**
 * API 调用的"安全包装"：
 * - **单一来源**：所有需要 API Key 的代码都通过 [resolve] 进入。
 * - **结构化结果**：[Result] 类型清晰区分成功 / 配置错误 / Key 错误，避免抛出后又捕获的繁琐。
 * - **避免明文 Key 泄漏**：调用方持有 [ApiAccess] 引用，而非裸 String。
 *
 * ## 使用示例
 *
 * ```kotlin
 * when (val access = ApiAccess.resolve(settings, conv, conversationRepo)) {
 *     is ApiAccess.Resolved -> api.streamChat(access.apiUrl, access.apiKey, req)
 *     is ApiAccess.Failure -> emitError(access.userMessage, access.cause)
 * }
 * ```
 */
sealed class ApiAccess {

    /**
     * 成功解析的 API 访问凭据。
     *
     * - [apiUrl]：完整 chat/completions URL（已规范化为非空）。
     * - [apiKey]：解密的明文 API Key（可能为空字符串表示未配置 Key 的服务）。
     * - [model]：当前会话选用的模型 id。
     */
    data class Resolved(
        val apiUrl: String,
        val apiKey: String,
        val model: String
    ) : ApiAccess()

    /**
     * 解析失败。
     *
     * 失败原因已分类（[Reason]），UI 层可针对性提示。
     * - [cause]：原始异常（可为 null），用于日志。
     */
    data class Failure(
        val reason: Reason,
        val userMessage: String,
        val cause: Throwable? = null
    ) : ApiAccess() {
        enum class Reason {
            /** 未配置任何模型配置条目 */
            NO_CATALOG,
            /** 当前会话和全局都没有选中模型配置，且 catalog 列表为空 */
            CATALOG_EMPTY,
            /** 接口密钥字段未填写 */
            KEY_NOT_CONFIGURED,
            /** Key 数据格式损坏 */
            KEY_MALFORMED,
            /** Key 数据被篡改 / 设备系统时间异常 */
            KEY_AUTHENTICATION_FAILED,
            /** 系统加密模块不可用 */
            KEY_CRYPTO_ERROR
        }
    }

    companion object {
        /**
         * 解析当前会话的 API 访问凭据（统一入口）。
         *
         * 解析顺序：
         * 1. `conv.apiCatalogId`（会话级覆盖） → `settings.activeCatalogId`（全局默认） → 第一条 catalog
         * 2. 仍未找到 → 报 [Failure.Reason.NO_CATALOG] 或 [Failure.Reason.CATALOG_EMPTY]
         * 3. 解密 `apiKeyEnc`，失败时按 [CryptoUtils.DecryptFailure] 子类映射 [Failure.Reason]
         *
         * @param settings 全局设置（含 catalog 列表与 activeCatalogId）
         * @param conv 当前会话（含会话级 apiCatalogId）
         * @return [Resolved] 含 URL/Key/Model；[Failure] 含可分类的错误
         */
        fun resolve(
            settings: AppSettings,
            conv: Conversation
        ): ApiAccess {
            // 步骤 1：解析 catalog 选中顺序
            val entry: ApiCatalogEntry? = settings.catalog
                .firstOrNull { it.id == conv.apiCatalogId }
                ?: settings.catalog.firstOrNull { it.id == settings.activeCatalogId }
                ?: settings.catalog.firstOrNull()

            if (entry == null) {
                return Failure(
                    reason = if (settings.catalog.isEmpty())
                        Failure.Reason.CATALOG_EMPTY
                    else
                        Failure.Reason.NO_CATALOG,
                    userMessage = "未配置模型配置，请在设置中添加"
                )
            }

            // 步骤 2：解密 API Key
            val apiKey = try {
                CryptoUtils.decrypt(entry.apiKeyEnc)
            } catch (e: CryptoUtils.DecryptFailure.Empty) {
                return Failure(
                    reason = Failure.Reason.KEY_NOT_CONFIGURED,
                    userMessage = "接口密钥未配置",
                    cause = e
                )
            } catch (e: CryptoUtils.DecryptFailure.Malformed) {
                return Failure(
                    reason = Failure.Reason.KEY_MALFORMED,
                    userMessage = "接口密钥数据格式损坏",
                    cause = e
                )
            } catch (e: CryptoUtils.DecryptFailure.AuthenticationFailed) {
                return Failure(
                    reason = Failure.Reason.KEY_AUTHENTICATION_FAILED,
                    userMessage = "接口密钥数据认证失败——可能设备系统时间错误或应用数据被篡改",
                    cause = e
                )
            } catch (e: CryptoUtils.DecryptFailure.CryptoError) {
                return Failure(
                    reason = Failure.Reason.KEY_CRYPTO_ERROR,
                    userMessage = "接口密钥解密失败（系统加密模块异常）",
                    cause = e
                )
            }

            return Resolved(
                apiUrl = entry.apiUrl,
                apiKey = apiKey,
                model = entry.apiModel
            )
        }
    }
}

/**
 * 将 [ApiAccess] 转换为 [ChatException]（用于事件流错误的统一异常类型）。
 *
 * @receiver 解析失败结果
 * @return 包装了用户提示的 [ChatException]
 */
fun ApiAccess.Failure.toChatException(): ChatException =
    ChatException(userMessage, cause)
