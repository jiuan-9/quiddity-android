package com.quiddity.app.domain.agent

import com.quiddity.app.domain.agent.AgentConfirmPolicy
import com.quiddity.app.domain.agent.AgentPermissionLevel
import com.quiddity.app.domain.agent.AgentTool

internal fun agentFileTools(executors: AgentExecutors?): List<AgentTool> = listOf(
                AgentTool(
                    name = "file_access",
                    description = "列出指定应用的文件/存储访问能力（相关权限是否授予）。",
                    params = paramsObject(
                        properties = mapOf(
                            "pkg" to stringParam("目标应用包名")
                        ),
                        required = listOf("pkg")
                    ),
                    level = AgentPermissionLevel.BASIC,
                    confirm = AgentConfirmPolicy.AUTO,
                    enabledByDefault = true,
                    execute = { _, args ->
                        val pkg = argString(args, "pkg").orEmpty()
                        executors?.fileAccess(pkg) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "read_file",
                    description = "读取文件文本内容并返回（需要 Shizuku 授权）。",
                    params = paramsObject(
                        properties = mapOf(
                            "path" to stringParam("文件绝对路径，如 /sdcard/Download/note.txt"),
                            "maxChars" to intParam("最多返回字符数，可选，默认 2000，上限 8000")
                        ),
                        required = listOf("path")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.AUTO,
                    enabledByDefault = false,
                    execute = { _, args ->
                        executors?.readFile(
                            path = argString(args, "path").orEmpty(),
                            maxChars = argInt(args, "maxChars")
                        ) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "reveal_file",
                    description = "跳转文件：用文件管理器打开指定路径定位（需要 Shizuku 授权）。" +
                        "可用 pkg 指定文件管理器包名（如 com.google.android.documentsui），避免微信等应用抢走打开意图。",
                    params = paramsObject(
                        properties = mapOf(
                            "path" to stringParam("文件或目录绝对路径"),
                            "pkg" to stringParam("文件管理器包名，可选；不传时由系统选择默认处理应用")
                        ),
                        required = listOf("path")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.AUTO,
                    enabledByDefault = false,
                    execute = { _, args ->
                        executors?.revealFile(
                            path = argString(args, "path").orEmpty(),
                            pkg = argString(args, "pkg")
                        ) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "move_file",
                    description = "移动文件或目录（需要 Shizuku 授权与用户确认）。",
                    params = paramsObject(
                        properties = mapOf(
                            "src" to stringParam("源路径"),
                            "dst" to stringParam("目标路径")
                        ),
                        required = listOf("src", "dst")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, args ->
                        executors?.moveFile(
                            src = argString(args, "src").orEmpty(),
                            dst = argString(args, "dst").orEmpty()
                        ) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "copy_file",
                    description = "复制文件或目录（需要 Shizuku 授权与用户确认）。",
                    params = paramsObject(
                        properties = mapOf(
                            "src" to stringParam("源路径"),
                            "dst" to stringParam("目标路径")
                        ),
                        required = listOf("src", "dst")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, args ->
                        executors?.copyFile(
                            src = argString(args, "src").orEmpty(),
                            dst = argString(args, "dst").orEmpty()
                        ) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "delete_file",
                    description = "删除文件或目录（不可恢复，需要 Shizuku 授权与用户确认）。",
                    params = paramsObject(
                        properties = mapOf(
                            "path" to stringParam("待删除路径")
                        ),
                        required = listOf("path")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, args ->
                        executors?.deleteFile(argString(args, "path").orEmpty()) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "create_file",
                    description = "在指定路径创建空文件（需要 Shizuku 授权与用户确认）。",
                    params = paramsObject(
                        properties = mapOf(
                            "path" to stringParam("待创建文件的绝对路径")
                        ),
                        required = listOf("path")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, args ->
                        executors?.createFile(argString(args, "path").orEmpty()) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "write_file",
                    description = "向文件写入文本内容（覆盖原内容，需要 Shizuku 授权与用户确认）。",
                    params = paramsObject(
                        properties = mapOf(
                            "path" to stringParam("目标文件绝对路径"),
                            "content" to stringParam("要写入的文本内容")
                        ),
                        required = listOf("path", "content")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, args ->
                        executors?.writeFile(
                            path = argString(args, "path").orEmpty(),
                            content = argString(args, "content").orEmpty()
                        ) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "append_file",
                    description = "向文件末尾追加文本内容（需要 Shizuku 授权与用户确认）。",
                    params = paramsObject(
                        properties = mapOf(
                            "path" to stringParam("目标文件绝对路径"),
                            "content" to stringParam("要追加的文本内容")
                        ),
                        required = listOf("path", "content")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, args ->
                        executors?.appendFile(
                            path = argString(args, "path").orEmpty(),
                            content = argString(args, "content").orEmpty()
                        ) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "rename_file",
                    description = "重命名文件或目录（需要 Shizuku 授权与用户确认）。",
                    params = paramsObject(
                        properties = mapOf(
                            "src" to stringParam("原路径"),
                            "dst" to stringParam("新路径")
                        ),
                        required = listOf("src", "dst")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, args ->
                        executors?.renameFile(
                            src = argString(args, "src").orEmpty(),
                            dst = argString(args, "dst").orEmpty()
                        ) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "mkdir",
                    description = "创建目录（含父目录，需要 Shizuku 授权与用户确认）。",
                    params = paramsObject(
                        properties = mapOf(
                            "path" to stringParam("待创建目录的绝对路径")
                        ),
                        required = listOf("path")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.ALWAYS_CONFIRM,
                    enabledByDefault = false,
                    execute = { _, args ->
                        executors?.mkdir(argString(args, "path").orEmpty()) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "list_files",
                    description = "列出目录下的文件清单（含权限/大小/修改时间，需要 Shizuku 授权）。",
                    params = paramsObject(
                        properties = mapOf(
                            "path" to stringParam("目录绝对路径")
                        ),
                        required = listOf("path")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.AUTO,
                    enabledByDefault = false,
                    execute = { _, args ->
                        executors?.listFiles(argString(args, "path").orEmpty()) ?: "尚未接入执行器"
                    }
                ),
                AgentTool(
                    name = "file_info",
                    description = "查询文件或目录的大小、修改时间等元信息（需要 Shizuku 授权）。",
                    params = paramsObject(
                        properties = mapOf(
                            "path" to stringParam("文件或目录绝对路径")
                        ),
                        required = listOf("path")
                    ),
                    level = AgentPermissionLevel.ADVANCED,
                    confirm = AgentConfirmPolicy.AUTO,
                    enabledByDefault = false,
                    execute = { _, args ->
                        executors?.fileInfo(argString(args, "path").orEmpty()) ?: "尚未接入执行器"
                    }
                )
)
