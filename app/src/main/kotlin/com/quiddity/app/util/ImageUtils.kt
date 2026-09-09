package com.quiddity.app.util

import android.content.Context
import android.net.Uri
import java.io.File

/**
 *
 * 设计动机：
 * - PickVisualMedia 返回的 content URI 仅授予进程生命周期内的临时读权限。
 *   Activity 被系统回收重建后，URI 字符串仍在（rememberSaveable）但权限已丢失，
 *   后续 BitmapFactory.decodeStream 会触发 SecurityException，部分设备上甚至
 *   抛出 native 层异常导致 SIGSEGV 闪退（无 Java 崩溃记录）。
 * - 统一在选定图片后立即复制到内部存储，后续读取 file:// URI 完全不依赖临时权限。
 *
 * 使用场景：
 * - [com.quiddity.app.ui.settings.components.AvatarPicker] 用户头像
 * - [com.quiddity.app.ui.chat.components.panels.PersonaPanel] AI 头像
 * - [com.quiddity.app.ui.chat.components.panels.WallpaperPanel] 会话壁纸
 */
object ImageUtils {

    /**
     * 将外部 content URI 指向的图片复制到应用内部存储。
     *
     * @param context 上下文
     * @param sourceUri PickVisualMedia 返回的 content URI
     * @param subdir 内部存储子目录（默认 "avatars"）
     * @return file:// URI，无需任何运行时权限即可读取
     * @throws IllegalStateException 无法读取源图片时抛出
     */
    fun copyToInternalStorage(
        context: Context,
        sourceUri: Uri,
        subdir: String = "avatars",
        fileNamePrefix: String = "source_temp_"
    ): Uri {
        val dir = File(context.filesDir, subdir).apply { mkdirs() }
        val destFile = File(dir, "$fileNamePrefix${IdGenerator.newUuid()}.jpg")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("无法读取所选图片")
        return Uri.fromFile(destFile)
    }

    /**
     * 安全删除 file:// URI 指向的临时文件。
     *
     * 用于裁剪完成/取消后清理 [copyToInternalStorage] 生成的临时源文件，
     * 避免存储膨胀。非 file:// URI 静默忽略。
     *
     */
    fun deleteTempFile(uri: Uri?) {
        if (uri == null) return
        runCatching {
            val path = uri.path
            if (path != null && uri.scheme == "file") {
                val file = File(path)
                // 仅清理图片子目录下的 source_temp_* 文件，避免误删
                val parentName = file.parentFile?.name
                if (parentName in IMAGE_SUBDIRS &&
                    file.name.startsWith("source_temp_")) {
                    file.delete()
                }
            }
        }
    }

    /**
     * 安全删除持久化的聊天图片（file:// chat_images/msg_*）。
     * 供删除会话/消息时回收空间；非 file:// 或非聊天图片子目录的 URI 忽略。
     */
    fun deleteChatImage(uri: Uri?) {
        if (uri == null) return
        runCatching {
            val path = uri.path
            if (path != null && uri.scheme == "file") {
                val file = File(path)
                if (file.parentFile?.name == "chat_images" && file.name.startsWith("msg_")) {
                    file.delete()
                }
            }
        }
    }

    /**
     * 所有合法的图片存储子目录名。
     * - avatars：用户头像 / AI 头像
     * - wallpapers：会话专属壁纸
     * - list_wallpapers：会话列表界面壁纸（全局）
     */
    private val IMAGE_SUBDIRS = setOf("avatars", "wallpapers", "list_wallpapers", "chat_images")

    /**
     * 把图片编码为视觉模型可用的 data URL（JPEG，带降采样压缩）。
     *
     * 用于图片 → OCR → 聊天 API 流程：
     * - 读取图片尺寸后按 [maxDimension] 降采样，控制 base64 体积，避免超出
     *   各视觉模型接口的图片大小限制；
     * - 统一转 JPEG 输出，兼容绝大多数 OpenAI 兼容视觉端点。
     *
     * @return `data:image/jpeg;base64,...`；读取/解码失败返回 null。
     */
    fun encodeImageForVision(
        context: Context,
        sourceUri: Uri,
        maxDimension: Int = 2048,
        quality: Int = 85
    ): String? = runCatching {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(sourceUri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
            sample *= 2
        }
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(sourceUri)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        try {
            val output = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, output)
            "data:image/jpeg;base64," +
                android.util.Base64.encodeToString(output.toByteArray(), android.util.Base64.NO_WRAP)
        } finally {
            bitmap.recycle()
        }
    }.getOrNull()
}
