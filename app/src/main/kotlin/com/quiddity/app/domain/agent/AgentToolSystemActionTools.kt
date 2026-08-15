package com.quiddity.app.domain.agent

import com.quiddity.app.domain.agent.AgentConfirmPolicy
import com.quiddity.app.domain.agent.AgentPermissionLevel
import com.quiddity.app.domain.agent.AgentTool

internal fun agentSystemActionTools(executors: AgentExecutors?): List<AgentTool> = listOf(
                AgentTool(
                    name = "ocr_image",
                    description = "对截图或图片执行 OCR 识别，返回图片中的文字；path 留空时自动截取当前屏幕再识别（调用应用内置识图引擎，需已配置视觉 OCR 模型）。",
                    params = paramsObject(
                        properties = mapOf(
                            "path" to stringParam("图片文件绝对路径，可选；留空则自动截图识别")
                        ),
                        required = emptyList()
                    ),
                    level = AgentPermissionLevel.BASIC,
                    confirm = AgentConfirmPolicy.AUTO,
                    enabledByDefault = true,
                    execute = { ctx, args ->
                        executors?.ocrImage(
                            path = argString(args, "path").orEmpty(),
                            conversation = ctx.conversation
                        ) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "notify_self",
                    description = "主动向用户推送一条通知栏提醒（标题可选，正文必填），用于定时提醒或结果通知。",
                    params = paramsObject(
                        properties = mapOf(
                            "title" to stringParam("通知标题，可选，默认「Agent 提醒」"),
                            "text" to stringParam("通知正文内容")
                        ),
                        required = listOf("text")
                    ),
                    level = AgentPermissionLevel.BASIC,
                    confirm = AgentConfirmPolicy.AUTO,
                    enabledByDefault = true,
                    execute = { _, args ->
                        executors?.notifySelf(
                            title = argString(args, "title"),
                            text = argString(args, "text").orEmpty()
                        ) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "read_clipboard",
                    description = "读取当前剪贴板文本内容（涉及隐私数据，需要用户确认）。",
                    params = paramsObject(
                        properties = emptyMap(),
                        required = emptyList()
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, _ ->
                        executors?.readClipboard() ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "write_clipboard",
                    description = "把文本写入剪贴板（需要用户确认）。",
                    params = paramsObject(
                        properties = mapOf(
                            "text" to stringParam("要写入剪贴板的文本内容")
                        ),
                        required = listOf("text")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, args ->
                        executors?.writeClipboard(argString(args, "text").orEmpty()) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "toast_monitor",
                    description = "读取最近捕获的 Toast / 短弹窗文本（瞬时提示，需已开启无障碍服务）。",
                    params = paramsObject(
                        properties = mapOf(
                            "since" to stringParam("标准时间格式，只返回该时间之后的 Toast，可选"),
                            "maxItems" to intParam("最多返回条数，可选，默认 10，上限 50")
                        ),
                        required = emptyList()
                    ),
                    level = AgentPermissionLevel.BASIC,
                    confirm = AgentConfirmPolicy.AUTO,
                    enabledByDefault = true,
                    execute = { _, args ->
                        executors?.toastMonitor(
                            since = argString(args, "since"),
                            maxItems = argInt(args, "maxItems")
                        ) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "notification_guard",
                    description = "通知变化订阅：返回自上次调用以来新出现/消失的通知；可用 since 指定基准时间（需通知使用权）。",
                    params = paramsObject(
                        properties = mapOf(
                            "since" to stringParam("标准时间格式，只返回该时间之后的通知变化，可选")
                        ),
                        required = emptyList()
                    ),
                    level = AgentPermissionLevel.BASIC,
                    confirm = AgentConfirmPolicy.AUTO,
                    enabledByDefault = true,
                    execute = { _, args ->
                        executors?.notificationGuard(argString(args, "since")) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "run_shell",
                    description = "执行白名单内的安全 Shell 命令（仅限只读命令，如 ls / cat / getprop / dumpsys 等，需要 Shizuku 授权与用户确认）。",
                    params = paramsObject(
                        properties = mapOf(
                            "command" to stringParam("白名单内的命令名，如 ls / cat / getprop"),
                            "args" to arrayParam("命令参数列表，可选")
                        ),
                        required = listOf("command")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, args ->
                        executors?.runShell(
                            command = argString(args, "command").orEmpty(),
                            args = argArray(args, "args")
                        ) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "app_usage_detail",
                    description = "更细粒度的应用使用统计：前台时长、启动次数、最后使用时间（需要使用情况访问权限）。",
                    params = paramsObject(
                        properties = mapOf(
                            "days" to intParam("统计天数，可选，默认 1 天，上限 30")
                        ),
                        required = emptyList()
                    ),
                    level = AgentPermissionLevel.BASIC,
                    confirm = AgentConfirmPolicy.AUTO,
                    enabledByDefault = true,
                    execute = { _, args ->
                        executors?.appUsageDetail(argInt(args, "days") ?: 1)
                            ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "schedule_notify",
                    description = "设置定时提醒：delaySeconds 秒后主动推送通知栏提醒（进程被回收也可触发）。",
                    params = paramsObject(
                        properties = mapOf(
                            "title" to stringParam("提醒标题，可选，默认「Agent 提醒」"),
                            "text" to stringParam("提醒正文"),
                            "delaySeconds" to intParam("延迟秒数，1 到 86400")
                        ),
                        required = listOf("text", "delaySeconds")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, args ->
                        executors?.scheduleNotify(
                            title = argString(args, "title"),
                            text = argString(args, "text").orEmpty(),
                            delaySeconds = argInt(args, "delaySeconds") ?: 60
                        ) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "lock_screen",
                    description = "锁屏（需要无障碍服务与用户确认）。",
                    params = paramsObject(
                        properties = emptyMap(),
                        required = emptyList()
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, _ ->
                        executors?.globalAction("lock") ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "sleep",
                    description = "等待指定秒数（1~300 秒）后再继续，用于等待界面加载、动画结束或定时衔接。",
                    params = paramsObject(
                        properties = mapOf(
                            "seconds" to intParam("等待秒数，1~300")
                        ),
                        required = listOf("seconds")
                    ),
                    level = AgentPermissionLevel.BASIC,
                    confirm = AgentConfirmPolicy.AUTO,
                    enabledByDefault = true,
                    execute = { _, args ->
                        executors?.sleep(argInt(args, "seconds") ?: 1)
                            ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "disable_app",
                    description = "停用指定的应用（需要授权通道、白名单与用户确认）。",
                    params = paramsObject(
                        properties = mapOf(
                            "pkg" to stringParam("目标应用包名"),
                            "confirmed" to boolParam("用户已确认")
                        ),
                        required = listOf("pkg")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, args ->
                        val pkg = argString(args, "pkg").orEmpty()
                        executors?.disableApp(pkg) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "enable_app",
                    description = "重新启用指定的应用（需要授权通道、白名单与用户确认）。",
                    params = paramsObject(
                        properties = mapOf(
                            "pkg" to stringParam("目标应用包名"),
                            "confirmed" to boolParam("用户已确认")
                        ),
                        required = listOf("pkg")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, args ->
                        val pkg = argString(args, "pkg").orEmpty()
                        executors?.enableApp(pkg) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "set_appops",
                    description = "修改应用的权限模式（需要授权通道、白名单与用户确认）。",
                    params = paramsObject(
                        properties = mapOf(
                            "pkg" to stringParam("目标应用包名"),
                            "op" to stringParam("权限操作名，如 震动"),
                            "mode" to stringParam("允许 / 拒绝 / 忽略 / 恢复默认"),
                            "confirmed" to boolParam("用户已确认")
                        ),
                        required = listOf("pkg", "op", "mode")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, args ->
                        val pkg = argString(args, "pkg").orEmpty()
                        val op = argString(args, "op").orEmpty()
                        val mode = argString(args, "mode").orEmpty()
                        executors?.setAppOps(pkg, op, mode) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "force_stop",
                    description = "强制停止指定的应用（需要授权通道、白名单与用户确认）。",
                    params = paramsObject(
                        properties = mapOf(
                            "pkg" to stringParam("目标应用包名"),
                            "confirmed" to boolParam("用户已确认")
                        ),
                        required = listOf("pkg")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, args ->
                        val pkg = argString(args, "pkg").orEmpty()
                        executors?.forceStop(pkg) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "uninstall_app",
                    description = "卸载指定的应用（需要授权通道、白名单与用户确认）。",
                    params = paramsObject(
                        properties = mapOf(
                            "pkg" to stringParam("目标应用包名"),
                            "confirmed" to boolParam("用户已确认")
                        ),
                        required = listOf("pkg")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, args ->
                        val pkg = argString(args, "pkg").orEmpty()
                        executors?.uninstallApp(pkg) ?: "尚未接入执行器"
                    }
                )
)
