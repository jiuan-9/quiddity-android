package com.quiddity.app.domain.agent

/*
 * Agent 模式开通路径路由：按系统版本自动分流到对应教程。
 *
 * - Android 11+（API 31+）：无线调试本地配对，免电脑；
 * - Android 8-10（API 26-30）：需 PC 一键授权工具（USB）；
 * - 低于 Android 8（API <26）：不支持（App minSdk 26，防御性兜底）。
 *
 * 注意：纯血鸿蒙（HarmonyOS NEXT）无法运行 Android 应用，应用本身不会走到本路由，
 * 不支持说明放在官网下载 FAQ 中。
 */
enum class AgentSetupPath {
    WirelessDebugging,
    PcTool,
    Unsupported
}

object AgentSetupRouter {
    const val MIN_SUPPORTED_SDK = 26
    const val WIRELESS_DEBUGGING_SDK = 31

    fun routeFor(sdkInt: Int): AgentSetupPath = when {
        sdkInt < MIN_SUPPORTED_SDK -> AgentSetupPath.Unsupported
        sdkInt >= WIRELESS_DEBUGGING_SDK -> AgentSetupPath.WirelessDebugging
        else -> AgentSetupPath.PcTool
    }

    fun routeForCurrent(sdkInt: () -> Int = { android.os.Build.VERSION.SDK_INT }): AgentSetupPath =
        routeFor(sdkInt())
}
