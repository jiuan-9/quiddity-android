package com.quiddity.app.domain

/**
 * - 定义 [ChatError] 密封类，按"错误类别"细分。
 * - 所有上层错误流（ChatRepository、ChatViewModel）统一暴露 [ChatError]。
 * - 错误信息展示走 [ChatError.userMessage]（用户可读），排查走 [ChatError.cause]。
 */
sealed class ChatError(
    /** 用户可读的错误描述。 */
    open val userMessage: String,
    /** 底层异常（用于日志、排查）。 */
    open val cause: Throwable? = null
) {
    /** 网络层错误（连接超时、读取超时等）。 */
    data class Network(
        override val userMessage: String,
        override val cause: Throwable? = null
    ) : ChatError(userMessage, cause)

    /** 鉴权 / 凭据错误（API Key 错误、过期、缺失）。 */
    data class Auth(
        override val userMessage: String,
        override val cause: Throwable? = null
    ) : ChatError(userMessage, cause)

    /** API 业务错误（HTTP 4xx/5xx、模型不存在、参数超限等）。 */
    data class Api(
        override val userMessage: String,
        val httpCode: Int? = null,
        override val cause: Throwable? = null
    ) : ChatError(userMessage, cause)

    /** 配置错误（未配置 API 名册、解密失败）。 */
    data class Config(
        override val userMessage: String,
        override val cause: Throwable? = null
    ) : ChatError(userMessage, cause)

    /** 未知错误。 */
    data class Unknown(
        override val userMessage: String,
        override val cause: Throwable? = null
    ) : ChatError(userMessage, cause)
}
