package com.quiddity.app.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
 *    除此之外，禁止出现任何形式的代码注释（包括但不限于单行注释、多行注释、临时调试注释等）。
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
