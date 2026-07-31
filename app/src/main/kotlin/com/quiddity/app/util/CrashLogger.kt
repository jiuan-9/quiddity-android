package com.quiddity.app.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局未捕获异常记录器。
 *
 * 将崩溃信息写入 filesDir/crash_logs/，方便在「无崩溃日志」场景下排查闪退原因。
 * 每条崩溃按时间命名，保留最近 20 条，避免无限占用存储。
 */
object CrashLogger {

    private const val TAG = "CrashLogger"
    private const val MAX_LOG_FILES = 20
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())

    /** 安装全局未捕获异常处理器。 */
    fun install(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(context, throwable) }
            // 继续交给系统默认处理器，保证崩溃流程正常结束并弹出系统崩溃对话框
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /** 主动记录一次异常（非致命）。 */
    fun logException(context: Context, throwable: Throwable, tag: String = TAG) {
        Log.w(tag, "记录非致命异常", throwable)
        runCatching { writeCrash(context, throwable, prefix = "nonfatal_") }
    }

    /** 获取最近一条崩溃日志内容，若无则返回 null。 */
    fun latestCrash(context: Context): String? {
        val dir = File(context.filesDir, "crash_logs")
        val file = dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".txt") }
            ?.maxByOrNull { it.lastModified() }
        return file?.readText()
    }

    /** 获取所有崩溃日志文件。 */
    fun listCrashes(context: Context): List<File> {
        val dir = File(context.filesDir, "crash_logs")
        return dir.listFiles()?.filter { it.isFile && it.name.endsWith(".txt") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    private fun writeCrash(context: Context, throwable: Throwable, prefix: String = "crash_") {
        val dir = File(context.filesDir, "crash_logs").apply { mkdirs() }
        val timestamp = dateFormat.format(Date())
        val file = File(dir, "${prefix}${timestamp}.txt")
        file.bufferedWriter().use { out ->
            out.appendLine("Time: $timestamp")
            out.appendLine("Thread: ${Thread.currentThread().name}")
            out.appendLine("Exception: ${throwable.javaClass.name}")
            out.appendLine("Message: ${throwable.message}")
            out.appendLine("StackTrace:")
            throwable.printStackTrace(java.io.PrintWriter(out))
        }
        cleanupOldLogs(dir)
    }

    private fun cleanupOldLogs(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".txt") }
            ?.sortedBy { it.lastModified() } ?: return
        if (files.size > MAX_LOG_FILES) {
            files.take(files.size - MAX_LOG_FILES).forEach { runCatching { it.delete() } }
        }
    }
}
