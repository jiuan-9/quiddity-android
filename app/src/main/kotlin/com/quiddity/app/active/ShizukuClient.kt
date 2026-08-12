package com.quiddity.app.active

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.quiddity.app.domain.agent.AgentShellResult
import com.quiddity.app.domain.agent.ShizukuShell
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku
import java.util.concurrent.atomic.AtomicReference

/*
 * ============================================================================
 * 开发规范 (Development Specifications)
 * ============================================================================
 *
 * 1. 问题修复规范
 *    所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
 *    修复内容需完全融入现有代码架构，保证代码逻辑的连贯性、可维护性和可扩展性。
 *
 * 2. 代码注释规范
 *    文件内仅允许保留两类注释：
 *    - 当前规则说明注释（即本注释块）
 *    - 模块划分注释（用于标识代码功能模块边界）
 *    除此之外，禁止出现任何形式的代码注释（包括但不限于单行注释、多行注释、临时调试注释）。
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

/** Shizuku 通道状态：未安装 / 已安装未运行 / 运行未授权 / 已授权。 */
enum class ShizukuStatus {
    NOT_INSTALLED,
    INSTALLED_NOT_RUNNING,
    RUNNING_NOT_GRANTED,
    GRANTED
}

/**
 * Shizuku 客户端：状态查询、权限请求、以及通过用户服务执行固定命令。
 *
 * 执行链路：bindUserService 绑定 [ShizukuShellService]（以 Shell 身份运行），
 * 通过 AIDL 同步执行命令；绑定失败/未授权时返回中文错误结果。
 */
class ShizukuClient(private val context: Context) : ShizukuShell {

    private val shellMutex = Mutex()
    private val boundService = AtomicReference<IRemoteShell?>()
    @Volatile
    private var pendingBind: CompletableDeferred<Boolean>? = null

    fun status(): ShizukuStatus {
        if (!isInstalled(context)) return ShizukuStatus.NOT_INSTALLED
        return runCatching {
            if (!Shizuku.pingBinder()) {
                ShizukuStatus.INSTALLED_NOT_RUNNING
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                ShizukuStatus.GRANTED
            } else {
                ShizukuStatus.RUNNING_NOT_GRANTED
            }
        }.getOrDefault(ShizukuStatus.INSTALLED_NOT_RUNNING)
    }

    override fun isGranted(): Boolean = status() == ShizukuStatus.GRANTED

    /**
     * 请求 Shizuku 授权。
     *
     * @return true 表示已发起请求（或已授权并回调）；false 表示 Shizuku 未运行。
     */
    fun requestPermission(
        requestCode: Int = REQUEST_CODE,
        onResult: ((Boolean) -> Unit)? = null
    ): Boolean {
        if (isGranted()) {
            onResult?.invoke(true)
            return true
        }
        if (!Shizuku.pingBinder()) return false
        if (onResult != null) {
            val listener = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    Shizuku.removeRequestPermissionResultListener(this)
                    onResult(grantResult == PackageManager.PERMISSION_GRANTED)
                }
            }
            Shizuku.addRequestPermissionResultListener(listener)
        }
        Shizuku.requestPermission(requestCode)
        return true
    }

    override suspend fun exec(command: Array<String>): AgentShellResult {
        if (!isGranted()) return AgentShellResult(-1, "未获得 Shizuku 授权")
        return shellMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    val service = bindAndGet()
                    val result = service.exec(command)
                    val output = listOf(result.stdout, result.stderr)
                        .filter { it.isNotBlank() }
                        .joinToString("\n")
                    AgentShellResult(result.exitCode, output)
                }.getOrElse {
                    AgentShellResult(-1, it.message ?: "执行失败")
                }
            }
        }
    }

    private suspend fun bindAndGet(): IRemoteShell {
        boundService.get()?.let { return it }
        val deferred = CompletableDeferred<Boolean>()
        pendingBind = deferred
        val args = Shizuku.UserServiceArgs(ComponentName(context, ShizukuShellService::class.java))
            .processNameSuffix("shizuku")
            .version(1)
            .daemon(true)
        Shizuku.bindUserService(args, connection)
        try {
            withTimeout(BIND_TIMEOUT_MS) { deferred.await() }
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            throw IllegalStateException("连接 Shizuku 用户服务失败：${t.message ?: "超时"}")
        } finally {
            pendingBind = null
        }
        return boundService.get() ?: throw IllegalStateException("Shizuku 用户服务未连接")
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            boundService.set(binder?.let { IRemoteShell.Stub.asInterface(it) })
            pendingBind?.complete(true)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundService.set(null)
        }

        override fun onBindingDied(name: ComponentName?) {
            boundService.set(null)
        }
    }

    companion object {
        private const val REQUEST_CODE = 1101
        private const val BIND_TIMEOUT_MS = 10_000L

        fun isInstalled(context: Context): Boolean {
            val packages = listOf(
                "moe.shizuku.privileged.api",
                "moe.shizuku.xyz"
            )
            return packages.any { pkg ->
                runCatching {
                    context.packageManager.getPackageInfo(pkg, 0)
                    true
                }.getOrDefault(false)
            }
        }
    }
}
