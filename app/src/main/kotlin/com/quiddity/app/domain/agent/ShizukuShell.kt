package com.quiddity.app.domain.agent

/** Shizuku 命令执行结果（域层类型，屏蔽 AIDL Parcelable 细节）。 */
data class AgentShellResult(
    val exitCode: Int,
    val output: String
)

/** Shizuku 执行通道抽象：固定命令数组输入，结果回传。 */
interface ShizukuShell {
    fun isGranted(): Boolean
    suspend fun exec(command: Array<String>): AgentShellResult

    /**
     * 申请 Shizuku 授权并等待结果：
     * - 已授权：立即返回 true；
     * - Shizuku 未运行 / 无法发起请求：返回 false；
     * - 未授权：拉起系统授权弹窗，等待用户决定后返回结果（超时按 false 处理）。
     */
    suspend fun requestPermissionAndWait(): Boolean
}
