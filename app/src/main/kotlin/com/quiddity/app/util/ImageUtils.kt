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
        subdir: String = "avatars"
    ): Uri {
        val dir = File(context.filesDir, subdir).apply { mkdirs() }
        val destFile = File(dir, "source_temp_${IdGenerator.newUuid()}.jpg")
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
     * 所有合法的图片存储子目录名。
     * - avatars：用户头像 / AI 头像
     * - wallpapers：会话专属壁纸
     * - list_wallpapers：会话列表界面壁纸（全局）
     */
    private val IMAGE_SUBDIRS = setOf("avatars", "wallpapers", "list_wallpapers")
}
