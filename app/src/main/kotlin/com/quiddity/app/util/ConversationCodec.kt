package com.quiddity.app.util

import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ExportPayload
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Persona
import com.quiddity.app.data.model.Role
import com.quiddity.app.data.model.UserPersona
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
 *
 * 支持 3 种格式：
 * 1. **JSON**（[Format.JSON]）：完整结构化数据（设置 + 会话 + 消息 + 壁纸）。
 *    用于备份/恢复；与 [DataPorter] 的 ExportPayload 完全兼容。
 * 2. **Markdown**（[Format.MARKDOWN]）：人类可读的对话记录。
 *    格式对齐 PC 端 settings.js 的 exportConversation：
 *    ```
 *    # Quiddity对话记录
 *    > 导出时间：yyyy-MM-dd HH:mm
 *    > 格式版本：1
 *    > 会话标题：xxx
 *
 *    ---
 *
 *    **用户名** (HH:mm)
 *
 *    消息内容（含代码块、Markdown 格式原样保留）
 *
 *    ---
 *
 *    **AI名** (HH:mm)
 *
 *    消息内容
 *
 *    ---
 *    ```
 * 3. **纯文本**（[Format.TEXT]）：最简文本格式，每条消息以分隔线分开。
 *    用于跨应用迁移；不保留 Markdown 格式但保留代码块原文本。
 *
 * - 导出：保留消息原始内容（含 ``` 代码块、**bold**、`inline code` 等）
 * - 导入：解析为 [Message] 列表，重建对话
 * - 往返一致性：导出 → 导入 → 再导出，应得到相同内容
 */
object ConversationCodec {

    /** 支持的导出/导入格式。 */
    enum class Format(val extension: String, val mime: String, val displayName: String) {
        JSON("json", "application/json", "JSON（完整备份）"),
        MARKDOWN("md", "text/markdown", "Markdown（对话记录）"),
        TEXT("txt", "text/plain", "纯文本（跨应用）");

        companion object
    }

    /** Markdown 导出文件头模板。 */
    private const val MD_HEADER_TEMPLATE = "# Quiddity对话记录\n" +
        "> 导出时间：%s\n" +
        "> 格式版本：1（Quiddity Android 兼容）\n" +
        "> 会话标题：%s\n" +
        "> 消息条数：%d\n\n" +
        "---\n\n"

    /** 纯文本导出文件头模板。 */
    private const val TXT_HEADER_TEMPLATE = "Quiddity对话记录\n" +
        "导出时间：%s\n" +
        "会话标题：%s\n" +
        "消息条数：%d\n" +
        "========================================\n\n"

    /** 消息分隔线（Markdown）。 */
    private const val MD_SEPARATOR = "\n\n---\n\n"

    /** 消息分隔线（纯文本）。 */
    private const val TXT_SEPARATOR = "\n\n----------------------------------------\n\n"

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }

    /**
     * 将单个会话及其消息导出为指定格式的字符串。
     *
     * @param conversation 会话
     * @param messages 消息列表
     * @param format 目标格式
     * @return 格式化后的字符串
     */
    fun exportConversation(
        conversation: Conversation,
        messages: List<Message>,
        format: Format
    ): String {
        // 过滤 isNotice 提示气泡：不导出（UI 专用，非对话内容）
        val exportableMessages = messages.filterNot { it.isNotice }
        if (exportableMessages.isEmpty()) {
            return when (format) {
                Format.JSON -> "{}"
                Format.MARKDOWN -> MD_HEADER_TEMPLATE.format(
                    isoFormat.format(Date()),
                    conversation.title,
                    0
                )
                Format.TEXT -> TXT_HEADER_TEMPLATE.format(
                    isoFormat.format(Date()),
                    conversation.title,
                    0
                )
            }
        }

        return when (format) {
            Format.JSON -> throw IllegalArgumentException(
                "JSON 格式请使用 DataPorter 导出 ExportPayload，本方法仅处理 MD/TXT"
            )
            Format.MARKDOWN -> exportAsMarkdown(conversation, exportableMessages)
            Format.TEXT -> exportAsText(conversation, exportableMessages)
        }
    }

    /**
     * 导出为 Markdown 格式。
     *
     * 格式示例：
     * ```
     * # Quiddity对话记录
     * > 导出时间：2026-07-25 14:30
     * > 格式版本：1（Quiddity Android 兼容）
     * > 会话标题：和小九的对话
     * > 消息条数：4
     *
     * ---
     *
     * **用户** (14:30:25)
     *
     * 帮我写一段 Python 代码
     *
     * ```python
     * print("hello")
     * ```
     *
     * ---
     *
     * **小九** (14:30:28)
     *
     * 好的，这是代码...
     * ```
     */
    private fun exportAsMarkdown(
        conversation: Conversation,
        messages: List<Message>
    ): String {
        val sb = StringBuilder()
        sb.append(
            MD_HEADER_TEMPLATE.format(
                isoFormat.format(Date()),
                conversation.title,
                messages.size
            )
        )

        // 人设卡信息（如果存在，附加在头部之后）
        appendPersonaInfo(sb, conversation, isMarkdown = true)

        val persona = conversation.persona
        val userPersona = conversation.userPersona
        val aiName = persona.name.ifBlank { "AI" }
        val userName = userPersona.name.ifBlank { "用户" }

        for (msg in messages) {
            val label = when (msg.role) {
                Role.USER -> userName
                Role.ASSISTANT -> aiName
                Role.SYSTEM -> "系统"
            }
            val timeStr = timeFormat.format(Date(msg.timestamp))
            sb.append("**$label** ($timeStr)\n\n")
            // 消息内容原样保留（含代码块、Markdown 格式）
            // 如果消息内容是空（流式中），跳过
            if (msg.content.isNotEmpty()) {
                sb.append(msg.content)
            } else {
                sb.append("*(空消息)*")
            }
            sb.append(MD_SEPARATOR)
        }

        return sb.toString()
    }

    /**
     * 导出为纯文本格式。
     *
     * 与 Markdown 的差异：
     * - 不使用 ** 加粗
     * - 不使用 > 引用
     * - 代码块原样保留（带 ``` 围栏）
     * - 使用 ===== 等符号作为分隔
     */
    private fun exportAsText(
        conversation: Conversation,
        messages: List<Message>
    ): String {
        val sb = StringBuilder()
        sb.append(
            TXT_HEADER_TEMPLATE.format(
                isoFormat.format(Date()),
                conversation.title,
                messages.size
            )
        )

        appendPersonaInfo(sb, conversation, isMarkdown = false)

        val persona = conversation.persona
        val userPersona = conversation.userPersona
        val aiName = persona.name.ifBlank { "AI" }
        val userName = userPersona.name.ifBlank { "用户" }

        for (msg in messages) {
            val label = when (msg.role) {
                Role.USER -> userName
                Role.ASSISTANT -> aiName
                Role.SYSTEM -> "系统"
            }
            val timeStr = timeFormat.format(Date(msg.timestamp))
            sb.append("[$label] $timeStr\n")
            sb.append("----------------------------------------\n")
            if (msg.content.isNotEmpty()) {
                sb.append(msg.content)
            } else {
                sb.append("(空消息)")
            }
            sb.append(TXT_SEPARATOR)
        }

        return sb.toString()
    }

    /**
     * 附加人设卡信息到导出文件头部。
     *
     * 包含：AI 人设、用户人设、场景、记忆。
     * 这些信息让导出文件自包含——即使不通过 JSON 导出也能恢复人设上下文。
     */
    private fun appendPersonaInfo(
        sb: StringBuilder,
        conversation: Conversation,
        isMarkdown: Boolean
    ) {
        val persona = conversation.persona
        val userPersona = conversation.userPersona
        val hasAny = persona.name.isNotBlank() ||
            persona.persona.isNotBlank() ||
            persona.character.isNotBlank() ||
            persona.appearance.isNotBlank() ||
            persona.worldBackground.isNotBlank() ||
            persona.desired.isNotBlank() ||
            userPersona.name.isNotBlank() ||
            userPersona.identity.isNotBlank() ||
            conversation.scene.isNotBlank() ||
            conversation.memory.isNotBlank()

        if (!hasAny) return

        if (isMarkdown) {
            sb.append("## 人设卡\n\n")
        } else {
            sb.append("人设卡\n")
            sb.append("========================================\n\n")
        }

        if (persona.name.isNotBlank() || persona.persona.isNotBlank() ||
            persona.character.isNotBlank() || persona.appearance.isNotBlank() ||
            persona.worldBackground.isNotBlank() || persona.desired.isNotBlank()
        ) {
            if (isMarkdown) sb.append("### AI 人设\n\n") else sb.append("[AI 人设]\n")
            if (persona.name.isNotBlank()) {
                if (isMarkdown) sb.append("- **名字**：${persona.name}\n")
                else sb.append("名字：${persona.name}\n")
            }
            if (persona.persona.isNotBlank()) {
                if (isMarkdown) sb.append("- **身份背景**：${persona.persona}\n")
                else sb.append("身份背景：${persona.persona}\n")
            }
            if (persona.character.isNotBlank()) {
                if (isMarkdown) sb.append("- **性格**：${persona.character}\n")
                else sb.append("性格：${persona.character}\n")
            }
            if (persona.appearance.isNotBlank()) {
                if (isMarkdown) sb.append("- **外观**：${persona.appearance}\n")
                else sb.append("外观：${persona.appearance}\n")
            }
            if (persona.worldBackground.isNotBlank()) {
                if (isMarkdown) sb.append("- **世界背景**：${persona.worldBackground}\n")
                else sb.append("世界背景：${persona.worldBackground}\n")
            }
            if (persona.desired.isNotBlank()) {
                if (isMarkdown) sb.append("- **期望**：${persona.desired}\n")
                else sb.append("期望：${persona.desired}\n")
            }
            sb.append("\n")
        }

        if (userPersona.name.isNotBlank() || userPersona.identity.isNotBlank() ||
            userPersona.gender.isNotBlank() || userPersona.age.isNotBlank() ||
            userPersona.appearance.isNotBlank()
        ) {
            if (isMarkdown) sb.append("### 用户人设\n\n") else sb.append("[用户人设]\n")
            if (userPersona.name.isNotBlank()) {
                if (isMarkdown) sb.append("- **名字**：${userPersona.name}\n")
                else sb.append("名字：${userPersona.name}\n")
            }
            if (userPersona.identity.isNotBlank()) {
                if (isMarkdown) sb.append("- **身份**：${userPersona.identity}\n")
                else sb.append("身份：${userPersona.identity}\n")
            }
            if (userPersona.gender.isNotBlank()) {
                if (isMarkdown) sb.append("- **性别**：${userPersona.gender}\n")
                else sb.append("性别：${userPersona.gender}\n")
            }
            if (userPersona.age.isNotBlank()) {
                if (isMarkdown) sb.append("- **年龄**：${userPersona.age}\n")
                else sb.append("年龄：${userPersona.age}\n")
            }
            if (userPersona.appearance.isNotBlank()) {
                if (isMarkdown) sb.append("- **外观**：${userPersona.appearance}\n")
                else sb.append("外观：${userPersona.appearance}\n")
            }
            sb.append("\n")
        }

        if (conversation.scene.isNotBlank()) {
            if (isMarkdown) {
                sb.append("### 场景\n\n${conversation.scene}\n\n")
            } else {
                sb.append("[场景]\n${conversation.scene}\n\n")
            }
        }

        if (conversation.memory.isNotBlank()) {
            if (isMarkdown) {
                sb.append("### 记忆\n\n${conversation.memory}\n\n")
            } else {
                sb.append("[记忆]\n${conversation.memory}\n\n")
            }
        }

        sb.append(if (isMarkdown) "---\n\n" else "========================================\n\n")
    }

    /**
     * 从字符串内容导入对话。
     *
     * 支持自动识别格式：
     * - JSON：以 `{` 开头 → 解析为 ExportPayload
     * - Markdown：以 `# Quiddity对话记录` 开头 → 解析 Markdown
     * - 纯文本：以 `Quiddity对话记录` 开头 → 解析纯文本
     *
     * @param content 文件内容
     * @param targetConversationId 目标会话 ID（用于填充 Message.conversationId）
     * @return 解析结果：会话 + 消息列表
     */
    fun importConversation(
        content: String,
        targetConversationId: String
    ): ImportResult {
        val trimmed = content.trimStart()
        return when {
            trimmed.startsWith("{") || trimmed.startsWith("[") -> {
                // JSON 格式由 DataPorter 处理；这里仅作为兜底返回空
                throw IllegalArgumentException(
                    "JSON 格式请通过 DataPorter.importFrom 处理，本方法仅解析 MD/TXT"
                )
            }
            trimmed.startsWith("# Quiddity对话记录") -> parseMarkdown(trimmed, targetConversationId)
            trimmed.startsWith("Quiddity对话记录") -> parseText(trimmed, targetConversationId)
            else -> {
                // 未知格式：尝试按 Markdown 解析（容错）
                parseMarkdown(trimmed, targetConversationId)
            }
        }
    }

    /**
     * 解析 Markdown 格式的对话记录。
     *
     * 对齐 PC 端 settings.js 的 parseConversationMarkdown，但增强：
     * - 解析人设卡信息（AI 人设、用户人设、场景、记忆）
     * - 解析消息内容（原样保留，含代码块）
     * - 解析时间戳（基于今日日期 + HH:mm:ss）
     */
    private fun parseMarkdown(
        content: String,
        targetConversationId: String
    ): ImportResult {
        // 提取会话标题
        val titleMatch = Regex("> 会话标题：(.+)").find(content)
        val title = titleMatch?.groupValues?.get(1)?.trim() ?: "导入的对话"

        // 解析人设卡
        val persona = parsePersonaSection(content, isMarkdown = true)

        // 移除头部和人设卡，保留消息部分
        val bodyStart = content.indexOf("\n---\n", content.indexOf("---\n"))
            .let { if (it >= 0) it + 5 else 0 }
        val body = if (bodyStart > 0) content.substring(bodyStart) else content

        // 按分隔线分块
        val blocks = body.split(Regex("\n\n---\n\n"))
        val messages = mutableListOf<Message>()
        val now = System.currentTimeMillis()

        for ((index, block) in blocks.withIndex()) {
            val trimmedBlock = block.trim()
            if (trimmedBlock.isEmpty()) continue

            // 匹配消息头：**Label** (HH:mm:ss)
            val headerMatch = Regex("\\*\\*(.+?)\\*\\*\\s*\\((\\d{2}:\\d{2}(?::\\d{2})?)\\)").find(trimmedBlock)
            if (headerMatch == null) {
                // 跳过非消息块（如人设卡残留）
                continue
            }

            val label = headerMatch.groupValues[1].trim()
            val timeStr = headerMatch.groupValues[2].trim()
            val msgContent = trimmedBlock.substring(headerMatch.range.last + 1).trim()

            // 跳过空消息占位
            if (msgContent == "*(空消息)*" || msgContent.isEmpty()) continue

            // 判断角色：优先匹配 AI 名，否则 USER
            val role = when {
                persona.first.name.isNotBlank() && label == persona.first.name -> Role.ASSISTANT
                label == "AI" -> Role.ASSISTANT
                label == "系统" -> Role.SYSTEM
                else -> Role.USER
            }

            // 时间戳：基于今日 + 解析的 HH:mm:ss
            val timestamp = parseTimestampToday(timeStr, now)

            messages.add(
                Message(
                    id = IdGenerator.newId(IdGenerator.Prefix.USER_MESSAGE),
                    conversationId = targetConversationId,
                    role = role,
                    content = msgContent,
                    timestamp = timestamp - (messages.size - index) // 保证顺序
                )
            )
        }

        return ImportResult(
            title = title,
            persona = persona.first,
            userPersona = persona.second,
            scene = persona.third,
            memory = persona.fourth,
            messages = messages
        )
    }

    /**
     * 解析纯文本格式。
     *
     * 与 [parseMarkdown] 类似，但消息头格式为 `[Label] HH:mm:ss`。
     *
     * 文本块结构：
     * ```
     * [Label] HH:mm:ss
     * ----------------------------------------
     * 消息内容（可多行）
     * ```
     */
    private fun parseText(
        content: String,
        targetConversationId: String
    ): ImportResult {
        val titleMatch = Regex("会话标题：(.+)").find(content)
        val title = titleMatch?.groupValues?.get(1)?.trim() ?: "导入的对话"

        val persona = parsePersonaSection(content, isMarkdown = false)

        // 移除头部
        val bodyStart = content.indexOf("========================================\n\n")
            .let { if (it >= 0) it + "========================================\n\n".length else 0 }
        val body = if (bodyStart > 0) content.substring(bodyStart) else content

        // 按分隔线分块（消息之间的分隔符）
        val blocks = body.split(Regex("\n\n----------------------------------------\n\n"))
        val messages = mutableListOf<Message>()
        val now = System.currentTimeMillis()

        for ((index, block) in blocks.withIndex()) {
            val trimmedBlock = block.trim()
            if (trimmedBlock.isEmpty()) continue

            // 匹配消息头：[Label] HH:mm:ss
            val headerMatch = Regex("\\[(.+?)\\]\\s*(\\d{2}:\\d{2}(?::\\d{2})?)").find(trimmedBlock)
            if (headerMatch == null) continue

            val label = headerMatch.groupValues[1].trim()
            val timeStr = headerMatch.groupValues[2].trim()

            // 消息内容：跳过 header 行 + 下面的 dash 分隔线
            // 找到 header 后第一个换行，跳过 dash 行，再找下一个换行，剩下的是消息内容
            val headerEnd = headerMatch.range.last + 1
            val afterHeader = trimmedBlock.substring(headerEnd)
            // 跳过 header 后的换行 + dash 行
            // afterHeader 形如："\n----------------------------------------\n消息内容"
            val firstNewline = afterHeader.indexOf('\n')
            val afterDashLine = if (firstNewline >= 0) {
                val afterFirstNl = afterHeader.substring(firstNewline + 1)
                // 跳过 dash 行（以 - 开头的行）
                val secondNewline = afterFirstNl.indexOf('\n')
                if (secondNewline >= 0 && afterFirstNl.startsWith("-")) {
                    afterFirstNl.substring(secondNewline + 1)
                } else {
                    // 无 dash 行，直接返回 header 之后的内容
                    afterFirstNl
                }
            } else {
                ""
            }
            val msgContent = afterDashLine.trim()

            if (msgContent == "(空消息)" || msgContent.isEmpty()) continue

            val role = when {
                persona.first.name.isNotBlank() && label == persona.first.name -> Role.ASSISTANT
                label == "AI" -> Role.ASSISTANT
                label == "系统" -> Role.SYSTEM
                else -> Role.USER
            }

            val timestamp = parseTimestampToday(timeStr, now)

            messages.add(
                Message(
                    id = IdGenerator.newId(IdGenerator.Prefix.USER_MESSAGE),
                    conversationId = targetConversationId,
                    role = role,
                    content = msgContent,
                    timestamp = timestamp - (messages.size - index)
                )
            )
        }

        return ImportResult(
            title = title,
            persona = persona.first,
            userPersona = persona.second,
            scene = persona.third,
            memory = persona.fourth,
            messages = messages
        )
    }

    /**
     * 解析人设卡区域。
     *
     * 返回四元组：(Persona, UserPersona, Scene, Memory)
     */
    private fun parsePersonaSection(
        content: String,
        isMarkdown: Boolean
    ): Quadruple<Persona, UserPersona, String, String> {
        var aiName = ""
        var aiPersona = ""
        var aiCharacter = ""
        var aiAppearance = ""
        var aiWorldBackground = ""
        var aiDesired = ""
        var userName = ""
        var userIdentity = ""
        var userGender = ""
        var userAge = ""
        var userAppearance = ""
        var scene = ""
        var memory = ""

        // 找到人设卡区域的边界
        val startMarker = if (isMarkdown) "## 人设卡" else "人设卡\n========================================"
        val startIdx = content.indexOf(startMarker)
        if (startIdx < 0) {
            return Quadruple(Persona.Empty, UserPersona.Empty, "", "")
        }

        // 找到人设卡区域结束：下一个 --- 分隔线
        val endMarker = if (isMarkdown) "\n---\n" else "========================================"
        val endIdx = content.indexOf(endMarker, startIdx + startMarker.length)
        val section = if (endIdx > 0) {
            content.substring(startIdx, endIdx)
        } else {
            content.substring(startIdx)
        }

        // AI 人设字段
        aiName = extractField(section, isMarkdown, "名字", "AI 人设") ?: ""
        aiPersona = extractField(section, isMarkdown, "身份背景", "AI 人设") ?: ""
        aiCharacter = extractField(section, isMarkdown, "性格", "AI 人设") ?: ""
        aiAppearance = extractField(section, isMarkdown, "外观", "AI 人设") ?: ""
        aiWorldBackground = extractField(section, isMarkdown, "世界背景", "AI 人设") ?: ""
        aiDesired = extractField(section, isMarkdown, "期望", "AI 人设") ?: ""

        // 用户人设字段
        userName = extractField(section, isMarkdown, "名字", "用户人设") ?: ""
        userIdentity = extractField(section, isMarkdown, "身份", "用户人设") ?: ""
        userGender = extractField(section, isMarkdown, "性别", "用户人设") ?: ""
        userAge = extractField(section, isMarkdown, "年龄", "用户人设") ?: ""
        userAppearance = extractField(section, isMarkdown, "外观", "用户人设") ?: ""

        // 场景
        scene = extractLongField(section, isMarkdown, "场景") ?: ""

        // 记忆
        memory = extractLongField(section, isMarkdown, "记忆") ?: ""

        val persona = Persona(
            name = aiName,
            persona = aiPersona,
            character = aiCharacter,
            appearance = aiAppearance,
            worldBackground = aiWorldBackground,
            desired = aiDesired
        )
        val userPersona = UserPersona(
            name = userName,
            identity = userIdentity,
            gender = userGender,
            age = userAge,
            appearance = userAppearance
        )
        return Quadruple(persona, userPersona, scene, memory)
    }

    /**
     * 提取单行字段值。
     *
     * Markdown 格式：`- **字段名**：值`
     * 纯文本格式：`字段名：值`
     *
     * @param section 人设卡区域文本
     * @param isMarkdown 是否 Markdown 格式
     * @param fieldName 字段名（如 "名字"、"身份背景"）
     * @param sectionName 区域名（"AI 人设" 或 "用户人设"，用于在多区域中定位）
     */
    private fun extractField(
        section: String,
        isMarkdown: Boolean,
        fieldName: String,
        sectionName: String
    ): String? {
        // 定位到指定区域（[AI 人设] 或 [用户人设]）
        val sectionStart = if (isMarkdown) {
            section.indexOf("### $sectionName")
        } else {
            section.indexOf("[$sectionName]")
        }
        if (sectionStart < 0) return null

        // 找到下一个区域开始（### 或 [）
        val nextSection = if (isMarkdown) {
            val idx1 = section.indexOf("### ", sectionStart + 4)
            val idx2 = section.indexOf("\n---", sectionStart + 4)
            minOf(
                if (idx1 > 0) idx1 else Int.MAX_VALUE,
                if (idx2 > 0) idx2 else Int.MAX_VALUE
            )
        } else {
            val idx1 = section.indexOf("\n[", sectionStart + 4)
            val idx2 = section.indexOf("================================", sectionStart + 4)
            minOf(
                if (idx1 > 0) idx1 else Int.MAX_VALUE,
                if (idx2 > 0) idx2 else Int.MAX_VALUE
            )
        }
        val effectiveEnd = if (nextSection == Int.MAX_VALUE) section.length else nextSection
        val subSection = section.substring(sectionStart, effectiveEnd)

        // 在子区域中查找字段
        val pattern = if (isMarkdown) {
            Regex("- \\*\\*$fieldName\\*\\*：(.+)")
        } else {
            Regex("$fieldName：(.+)")
        }
        return pattern.find(subSection)?.groupValues?.get(1)?.trim()
    }

    /**
     * 提取长文本字段（场景、记忆，可能跨多行）。
     */
    private fun extractLongField(
        section: String,
        isMarkdown: Boolean,
        fieldName: String
    ): String? {
        val headerPattern = if (isMarkdown) {
            Regex("### $fieldName\\s*\\n\\n([\\s\\S]+?)(?=\\n###|\\n---|$)")
        } else {
            Regex("\\[$fieldName\\]\\s*\\n([\\s\\S]+?)(?=\\n\\[|====|$)")
        }
        return headerPattern.find(section)?.groupValues?.get(1)?.trim()
    }

    /**
     * 基于"今日日期 + HH:mm:ss"构造时间戳。
     *
     * 导入的对话只有时间没日期，使用今日日期补齐。
     */
    private fun parseTimestampToday(timeStr: String, fallback: Long): Long {
        return try {
            val parts = timeStr.split(":").map { it.toIntOrNull() ?: 0 }
            val cal = java.util.Calendar.getInstance()
            cal.set(
                java.util.Calendar.HOUR_OF_DAY,
                parts.getOrNull(0) ?: 0
            )
            cal.set(java.util.Calendar.MINUTE, parts.getOrNull(1) ?: 0)
            cal.set(java.util.Calendar.SECOND, parts.getOrNull(2) ?: 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            cal.timeInMillis
        } catch (_: Exception) {
            fallback
        }
    }

    /**
     * 导入结果。
     *
     * @param title 会话标题
     * @param persona AI 人设（来自人设卡区域，可能为 Empty）
     * @param userPersona 用户人设
     * @param scene 场景
     * @param memory 记忆
     * @param messages 消息列表
     */
    data class ImportResult(
        val title: String,
        val persona: Persona,
        val userPersona: UserPersona,
        val scene: String,
        val memory: String,
        val messages: List<Message>
    )

    /** 简易四元组。 */
    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )
}
