package com.quiddity.app.domain.agent

import com.quiddity.app.domain.agent.AgentConfirmPolicy
import com.quiddity.app.domain.agent.AgentPermissionLevel
import com.quiddity.app.domain.agent.AgentTool

internal fun agentInteractionTools(executors: AgentExecutors?): List<AgentTool> = listOf(
                AgentTool(
                    name = "click",
                    description = "模拟点击屏幕指定坐标（像素，需要无障碍服务与用户确认）。",
                    params = paramsObject(
                        properties = mapOf(
                            "x" to intParam("点击横坐标，像素"),
                            "y" to intParam("点击纵坐标，像素")
                        ),
                        required = listOf("x", "y")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, args ->
                        executors?.click(
                            x = argInt(args, "x") ?: 0,
                            y = argInt(args, "y") ?: 0
                        ) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "long_press",
                    description = "长按屏幕指定坐标（像素，需要无障碍服务与用户确认）。",
                    params = paramsObject(
                        properties = mapOf(
                            "x" to intParam("长按横坐标，像素"),
                            "y" to intParam("长按纵坐标，像素")
                        ),
                        required = listOf("x", "y")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, args ->
                        executors?.longPress(
                            x = argInt(args, "x") ?: 0,
                            y = argInt(args, "y") ?: 0
                        ) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "click_text",
                    description = "点击屏幕上包含指定文字的控件（需要无障碍服务与用户确认）。",
                    params = paramsObject(
                        properties = mapOf(
                            "text" to stringParam("要点击控件上包含的文字")
                        ),
                        required = listOf("text")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, args ->
                        executors?.clickText(argString(args, "text").orEmpty()) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "scroll",
                    description = "在屏幕上滑动（方向 up / down / left / right，需要无障碍服务与用户确认）。",
                    params = paramsObject(
                        properties = mapOf(
                            "direction" to stringParam("滑动方向：up / down / left / right"),
                            "distance" to intParam("滑动距离，像素，可选，默认 600")
                        ),
                        required = listOf("direction")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, args ->
                        executors?.scroll(
                            direction = argString(args, "direction").orEmpty(),
                            distance = argInt(args, "distance") ?: 600
                        ) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "global_action",
                    description = "执行系统全局动作：back / home / recents / notifications / quick_settings / lock（需要无障碍服务与用户确认）。",
                    params = paramsObject(
                        properties = mapOf(
                            "action" to stringParam("系统动作：back / home / recents / notifications / quick_settings / lock")
                        ),
                        required = listOf("action")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, args ->
                        executors?.globalAction(argString(args, "action").orEmpty()) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "open_app",
                    description = "直接打开指定应用（按包名，如 com.tencent.mm 打开微信），用于快速跳转导航；无需无障碍服务，免确认执行。",
                    params = paramsObject(
                        properties = mapOf(
                            "pkg" to stringParam("要打开的应用包名")
                        ),
                        required = listOf("pkg")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.AUTO,
                    enabledByDefault = false,
                    execute = { _, args ->
                        executors?.openApp(argString(args, "pkg").orEmpty()) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "input_text",
                    description = "向当前聚焦的输入框写入文本（需要无障碍服务与用户确认）。",
                    params = paramsObject(
                        properties = mapOf(
                            "text" to stringParam("要输入的文本内容")
                        ),
                        required = listOf("text")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, args ->
                        executors?.inputText(argString(args, "text").orEmpty()) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "click_id",
                    description = "按控件 resource-id 点击（需要无障碍服务与用户确认）。",
                    params = paramsObject(
                        properties = mapOf(
                            "id" to stringParam("控件的 resource-id，如 com.example:id/confirm")
                        ),
                        required = listOf("id")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, args ->
                        executors?.clickId(argString(args, "id").orEmpty()) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "click_desc",
                    description = "按控件内容描述（contentDescription）点击（需要无障碍服务与用户确认）。",
                    params = paramsObject(
                        properties = mapOf(
                            "desc" to stringParam("控件描述文字")
                        ),
                        required = listOf("desc")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, args ->
                        executors?.clickDesc(argString(args, "desc").orEmpty()) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "drag",
                    description = "在屏幕上从起点拖拽到终点（像素，需要无障碍服务与用户确认）。",
                    params = paramsObject(
                        properties = mapOf(
                            "x1" to intParam("起点横坐标，像素"),
                            "y1" to intParam("起点纵坐标，像素"),
                            "x2" to intParam("终点横坐标，像素"),
                            "y2" to intParam("终点纵坐标，像素")
                        ),
                        required = listOf("x1", "y1", "x2", "y2")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, args ->
                        executors?.drag(
                            x1 = argInt(args, "x1") ?: 0,
                            y1 = argInt(args, "y1") ?: 0,
                            x2 = argInt(args, "x2") ?: 0,
                            y2 = argInt(args, "y2") ?: 0
                        ) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "scroll_to_text",
                    description = "向下滚动查找并点击目标文字（需要无障碍服务与用户确认）。",
                    params = paramsObject(
                        properties = mapOf(
                            "text" to stringParam("要查找并点击的文字"),
                            "maxScrolls" to intParam("最多滚动次数，可选，默认 5，上限 10")
                        ),
                        required = listOf("text")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, args ->
                        executors?.scrollToText(
                            text = argString(args, "text").orEmpty(),
                            maxScrolls = argInt(args, "maxScrolls") ?: 5
                        ) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "clear_clipboard",
                    description = "清空剪贴板内容（需要用户确认）。",
                    params = paramsObject(
                        properties = emptyMap(),
                        required = emptyList()
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, _ ->
                        executors?.clearClipboard() ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "dismiss_notification",
                    description = "清除通知栏里指定包名的通知（可指定通知 id，需要通知使用权与用户确认）。",
                    params = paramsObject(
                        properties = mapOf(
                            "pkg" to stringParam("通知所属应用包名"),
                            "id" to intParam("通知 id，可选，不传则清除该包名的第一条")
                        ),
                        required = listOf("pkg")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, args ->
                        executors?.dismissNotification(
                            pkg = argString(args, "pkg").orEmpty(),
                            id = argInt(args, "id")
                        ) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "reply_notification",
                    description = "回复指定通知：优先走内联回复，目标不支持则点击打开应用（需要通知使用权与用户确认）。",
                    params = paramsObject(
                        properties = mapOf(
                            "pkg" to stringParam("通知所属应用包名"),
                            "id" to intParam("通知 id，可选，不传则回复该包名的第一条"),
                            "reply" to stringParam("回复内容")
                        ),
                        required = listOf("pkg", "reply")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, args ->
                        executors?.replyNotification(
                            pkg = argString(args, "pkg").orEmpty(),
                            id = argInt(args, "id"),
                            reply = argString(args, "reply").orEmpty()
                        ) ?: "尚未接入执行器"
                    }
                )
)
