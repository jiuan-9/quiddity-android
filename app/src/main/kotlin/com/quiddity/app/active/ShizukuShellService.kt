package com.quiddity.app.active

/**
 * Shizuku 用户服务：以 Shell（ADB/ROOT）身份执行固定命令并回传结果。
 *
 * 命令数组由执行器层构造（固定形状，不接受模型自由拼接），
 * 输出通过 [ShellResult] 回传客户端进程。
 */
class ShizukuShellService : IRemoteShell.Stub() {

    override fun exec(command: Array<String>): ShellResult {
        if (command.isEmpty()) return ShellResult(-1, "", "空命令")
        return runCatching {
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exit = process.waitFor()
            ShellResult(exit, output.trim(), "")
        }.getOrElse {
            ShellResult(-1, "", it.message ?: "执行失败")
        }
    }
}
