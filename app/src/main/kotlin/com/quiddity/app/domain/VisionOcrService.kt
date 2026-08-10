package com.quiddity.app.domain

import android.content.Context
import android.net.Uri
import com.quiddity.app.data.model.ApiCatalogEntry
import com.quiddity.app.data.model.AppSettings
import com.quiddity.app.data.remote.ChatApi
import com.quiddity.app.util.ImageUtils

/**
 * 视觉识图服务：图片 → 视觉模型 OCR → 文本。
 *
 * 供聊天发图流程使用（用户发图时先识图，再把识别文本交给聊天 API）：
 * 1. 通过 [ApiCatalogManager.resolveOcrEntry] 按优先级解析识图模型——
 *    当前对话模型自带视觉则直接用对话模型，否则用用户配置的视觉 OCR 模型兜底；
 * 2. 图片统一压缩编码为 base64 data URL 后调用视觉接口；
 * 3. 返回识别文本，失败时携带可读错误信息。
 */
class VisionOcrService(
    private val chatApi: ChatApi,
    private val apiCatalogManager: ApiCatalogManager
) {

    /**
     * 识图并返回识别文本。
     *
     * @param settings 当前应用设置（含视觉 OCR 名册与开关）
     * @param imageUri 图片 file:// 或 content:// URI
     * @param chatEntry 当前对话使用的模型配置；null 表示无可用的聊天配置
     * @return 成功返回识别文本；失败返回带原因的 [Result.failure]
     */
    suspend fun recognizeImage(
        context: Context,
        imageUri: Uri,
        settings: AppSettings,
        chatEntry: ApiCatalogEntry?
    ): Result<String> {
        val entry = apiCatalogManager.resolveOcrEntry(settings, chatEntry)
            ?: return Result.failure(
                IllegalStateException("未配置视觉 OCR 模型，请在总设置 → 视觉 OCR 中配置")
            )
        val apiKey = apiCatalogManager.decryptKey(entry)
            ?: return Result.failure(IllegalStateException("视觉 OCR 模型密钥不可用，请重新填写"))
        val imageData = ImageUtils.encodeImageForVision(context, imageUri)
            ?: return Result.failure(IllegalStateException("图片读取失败，请重新选择图片"))
        return runCatching {
            val raw = chatApi.completeVisionNonStreaming(
                apiUrl = entry.apiUrl,
                apiKey = apiKey,
                model = entry.apiModel,
                prompt = OCR_PROMPT,
                imageData = imageData,
                maxTokens = 2048,
                temperature = 0.2
            )
            cleanOcrResult(raw).ifBlank {
                throw IllegalStateException(
                    "模型未返回有效识别内容（可能被推理内容截断），请重试或换用非思考型视觉模型"
                )
            }
        }
    }

    /**
     * 端到端测试视觉配置：生成一张带文字的测试图，用给定的 URL / Key / 模型真实调用一次。
     *
     * 用于设置页"测试识图"按钮——能一次性验证密钥是否有效、模型 ID 是否正确、
     * 请求格式是否被该厂商接受，成功时返回模型识别出的文字。
     */
    suspend fun testVision(
        apiUrl: String,
        apiKey: String,
        model: String
    ): Result<String> {
        val imageData = buildTestImageData()
            ?: return Result.failure(IllegalStateException("测试图片生成失败"))
        return runCatching {
            val text = chatApi.completeVisionNonStreaming(
                apiUrl = apiUrl.trim(),
                apiKey = apiKey.trim(),
                model = model.trim(),
                prompt = TEST_PROMPT,
                imageData = imageData,
                maxTokens = 128,
                temperature = 0.0
            )
            val cleaned = cleanOcrResult(text)
            if (cleaned.isBlank()) {
                throw IllegalStateException("模型未返回识别内容，请检查模型是否支持图片输入")
            }
            "识别成功：$cleaned"
        }
    }

    /**
     * 清理视觉模型返回中的推理噪声，只保留识别正文：
     * - 若带 `<answer>...</answer>` 包裹则只取其中内容（思考型模型的标准输出结构）；
     * - 去掉 `<think>...</think>` 推理片段，兼容被 max_tokens 截断、
     *   没有闭合标签的情况（从 `<think>` 直接删到末尾）；
     * - 去掉智谱的 `<|begin_of_box|>` / `<|end_of_box|>` 标记。
     */
    private fun cleanOcrResult(raw: String): String {
        var text = raw
        val answer = Regex("<answer>(.*?)</answer>", RegexOption.DOT_MATCHES_ALL)
            .find(text)?.groupValues?.getOrNull(1)
        if (!answer.isNullOrBlank()) {
            text = answer
        } else {
            val thinkStart = text.indexOf("<think>")
            if (thinkStart >= 0) {
                val thinkEnd = text.indexOf("</think>", thinkStart)
                text = if (thinkEnd >= 0) {
                    text.removeRange(thinkStart, thinkEnd + "</think>".length)
                } else {
                    // 截断响应：<think> 未闭合，其后的所有内容都是推理噪声
                    text.substring(0, thinkStart)
                }
            }
        }
        return text
            .replace(Regex("<\\|begin_of_box\\|>"), "")
            .replace(Regex("<\\|end_of_box\\|>"), "")
            .trim()
    }

    /** 生成一张白底黑字的测试图（OCR TEST），编码为 base64 data URL。 */
    private fun buildTestImageData(): String? = runCatching {
        val width = 480
        val height = 160
        val bitmap = android.graphics.Bitmap.createBitmap(
            width, height, android.graphics.Bitmap.Config.ARGB_8888
        )
        try {
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK
                textSize = 56f
                textAlign = android.graphics.Paint.Align.CENTER
            }
            canvas.drawText("OCR TEST 1234", width / 2f, height / 2f + 20f, paint)
            val output = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, output)
            "data:image/jpeg;base64," +
                android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP)
        } finally {
            bitmap.recycle()
        }
    }.getOrNull()

    companion object {
        /**
         * 识图指令：优先原样提取图片中的文字，无文字时退化为简要描述。
         */
        const val OCR_PROMPT =
            "请识别这张图片中的文字内容，并尽量原样输出（保留原有分段与符号）。" +
                "如果图片中几乎没有文字，请用一两句话简要描述图片内容。"

        /** 测试识图指令：只要求输出识别到的文字，便于人工核对。 */
        private const val TEST_PROMPT =
            "请识别这张图片中的文字内容，只输出识别到的文字，不要附加任何说明。"
    }
}
