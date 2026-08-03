package com.quiddity.app.data.repo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.quiddity.app.MainActivity
import com.quiddity.app.R
import com.quiddity.app.active.AlarmScheduler
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import com.quiddity.app.domain.TimeLibraryEngine
import com.quiddity.app.util.IdGenerator
import com.quiddity.app.util.QuiddityConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap

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
 * 主动消息（时间库）协调器：按算法文档串联全部流程。
 *
 * 覆盖规则链：
 * - 2.2 会话级开启 → 立即触发第一次时间库生成
 * - 3.1 生成时机（每天首次生成；App 启动 / 开机时为所有启用会话补齐，不依赖打开会话）
 * - 3.2 生成规则（LLM 输入人设 + 压缩聊天记录）
 * - 四、时间库更新与兜底（直接覆盖 / 空结果或失败沿用旧库 / 从未生成过保持静默）
 * - 5.2 触发执行流程（决策 LLM + 严格 0 拦截 + 发送 + 立即改 done）
 * - 6.1 触发延迟补偿（≤5 分钟补发，>5 分钟放弃）
 * - 6.2 网络/API 异常（自动重试 1 次，延迟 5 秒，仍失败标 done）
 * - 七、状态重置（每日首次启动 App 时 done → pending）
 * - 八、任务注销（全部 done 或空库 → 注销闹钟）
 */
class TimeLibraryRepository(
    private val conversationRepository: ConversationRepository,
    private val settingsRepository: SettingsRepository,
    private val chatRepository: ChatRepository,
    private val alarmScheduler: AlarmScheduler,
    private val context: Context
) {

    /** 生成中的会话集合（防同一会话并发重复生成）。 */
    private val generatingInFlight = ConcurrentHashMap.newKeySet<String>()

    // ===== 时间库生成 =====

    /**
     * 生成结果分类（UI 层据此给出反馈）。
     */
    sealed class GenerationOutcome {
        /** 会话级开关未开启，不触发生成。 */
        data object NotEnabled : GenerationOutcome()
        /** 当天已生成过，不重复生成。 */
        data object UpToDate : GenerationOutcome()
        /** 生成进行中（另一入口正在为同一会话生成）。 */
        data object Generating : GenerationOutcome()
        /** 已触发生成：本次结果被采纳（含沿用旧库），闹钟已重新注册。 */
        data object Triggered : GenerationOutcome()
        /** 已触发生成但新库旧库均为空：静默不注册任何定时任务，下次打开再次触发。 */
        data object TriggeredSilent : GenerationOutcome()
        /** 生成失败（调用异常且无可沿用的旧库）：不注册定时任务，下次打开/开启时重试。 */
        data object Failed : GenerationOutcome()
    }

    /**
     * 确保当天时间库已生成（对应 3.1 生成时机）。
     *
     * - 会话级开关未开启 → [GenerationOutcome.NotEnabled]
     * - 当天已生成过 → [GenerationOutcome.UpToDate]
     * - 首次生成：调用 LLM（人设 + 压缩聊天记录），解析时间列表
     * - 生成结果非空 → 覆盖旧库并注册闹钟；空结果 / 失败 → 沿用旧库
     * - 新库旧库均为空 → 静默（[GenerationOutcome.TriggeredSilent]），不更新生成日期，下次打开再次触发
     */
    suspend fun ensureLibraryGeneratedToday(convId: String): GenerationOutcome {
        val conv = conversationRepository.getConversation(convId)
            ?: return GenerationOutcome.NotEnabled
        val today = LocalDate.now().toString()
        if (!TimeLibraryEngine.shouldGenerate(conv.activeMessageEnabled, conv.timeLibraryGeneratedDate, today)) {
            return if (conv.activeMessageEnabled) GenerationOutcome.UpToDate else GenerationOutcome.NotEnabled
        }
        if (!generatingInFlight.add(convId)) return GenerationOutcome.Generating
        return try {
            val raw = runCatching { chatRepository.generateTimeLibrary(conv) }.getOrNull()
            val generatedTimes = raw?.let { TimeLibraryEngine.parseGeneratedTimes(it) }.orEmpty()
            val merged = TimeLibraryEngine.mergeGenerated(conv.timeLibrary, generatedTimes)
            if (merged.isEmpty()) {
                if (raw == null && conv.timeLibrary.isEmpty()) {
                    // 调用失败且从未生成过：明确标记失败，供 UI 提示用户检查接口
                    GenerationOutcome.Failed
                } else {
                    // 从未生成过（无新库也无旧库）或 AI 明确判定无需发送：完全静默
                    GenerationOutcome.TriggeredSilent
                }
            } else {
                // 生成成功：一并更新查看密码与是否告知（AI 制定）。
                // 密码无效或缺失时使用按会话+日期稳定的兜底密码，保证"查看时间库"始终可用；
                // 生成失败沿用旧库时保持原密码不变。
                val hasNewLibrary = raw != null && generatedTimes.isNotEmpty()
                val passwordExisted = conv.timeLibraryPassword.isNotBlank()
                // 密码一旦生成就固定不变（不要求唯一）；只有从未设置过时才会在本次生成时制定
                val password = if (passwordExisted) {
                    conv.timeLibraryPassword
                } else if (hasNewLibrary) {
                    TimeLibraryEngine.sanitizePassword(
                        TimeLibraryEngine.parseGeneratedPassword(raw)
                    ).ifBlank { TimeLibraryEngine.fallbackPassword(conv.id + today) }
                } else {
                    conv.timeLibraryPassword
                }
                val revealed = if (passwordExisted) {
                    conv.timeLibraryPasswordRevealed
                } else if (hasNewLibrary) {
                    TimeLibraryEngine.parsePasswordRevealed(raw)
                } else {
                    conv.timeLibraryPasswordRevealed
                }
                conversationRepository.updateConversation(
                    conv.copy(
                        timeLibrary = merged,
                        timeLibraryGeneratedDate = today,
                        timeLibraryPassword = password,
                        timeLibraryPasswordRevealed = revealed
                    )
                )
                scheduleFromLibrary(conv.copy(timeLibrary = merged))
                // 首次生成且 AI 决定告知时：由 App 用真实存储的密码生成一条 AI 气泡消息，
                // 保证告知的密码与「查看时间库」校验的密码完全一致；密码固定后不再重复告知
                if (hasNewLibrary && !passwordExisted && revealed && password.isNotBlank()) {
                    appendPasswordNotice(conv, password)
                }
                GenerationOutcome.Triggered
            }
        } finally {
            generatingInFlight.remove(convId)
        }
    }

    // ===== 会话级开关 =====

    /**
     * 设置会话级"时间库主动消息"开关（对应 2.2）。
     * - 开启：立即触发第一次时间库生成；若当天已生成过则直接注册闹钟
     * - 关闭：注销该会话所有定时闹钟
     */
    suspend fun setConversationEnabled(conv: Conversation, enabled: Boolean): GenerationOutcome {
        if (enabled) {
            conversationRepository.updateConversation(conv.copy(activeMessageEnabled = true))
            val today = LocalDate.now().toString()
            return if (TimeLibraryEngine.shouldGenerate(true, conv.timeLibraryGeneratedDate, today)) {
                ensureLibraryGeneratedToday(conv.id)
            } else {
                if (conv.timeLibrary.isNotEmpty()) {
                    scheduleFromLibrary(conv.copy(activeMessageEnabled = true))
                }
                GenerationOutcome.UpToDate
            }
        } else {
            alarmScheduler.cancelAll(conv.id)
            conversationRepository.updateConversation(conv.copy(activeMessageEnabled = false))
            return GenerationOutcome.NotEnabled
        }
    }

    // ===== 定时触发执行流程 =====

    /**
     * 闹钟触发入口（对应 5.2 触发执行流程 + 6.1 补偿 + 6.2 容错）。
     * 由 [com.quiddity.app.active.ActiveMessageReceiver] / 前台服务调用。
     *
     * @param convId 会话 ID
     * @param timePoint 触发的时间点（"HH:mm"）
     */
    suspend fun onAlarmTriggered(convId: String, timePoint: String) {
        val conv = conversationRepository.getConversation(convId) ?: return
        if (!conv.activeMessageEnabled) {
            alarmScheduler.cancelAll(convId)
            return
        }

        // 该时间点已不在当前库中（库被覆盖 / 重复触发）→ 按当前库重新调度
        val timePointMinutes = TimeLibraryEngine.parseMinutes(timePoint)
        val point = conv.timeLibrary.firstOrNull { it.time == timePoint }
        if (point == null || !point.isPending || timePointMinutes == null) {
            val done = if (point != null) TimeLibraryEngine.markDone(conv.timeLibrary, timePoint) else conv.timeLibrary
            if (done != conv.timeLibrary) {
                conversationRepository.updateConversation(conv.copy(timeLibrary = done))
            }
            scheduleFromLibrary(conv)
            return
        }

        val nowMinutes = LocalTime.now().let { it.hour * 60 + it.minute }

        // 6.1 触发延迟补偿：差值 > 5 分钟 → 放弃该时间点，继续等待下一个 pending
        if (!TimeLibraryEngine.withinLateWindow(timePointMinutes, nowMinutes)) {
            val abandoned = TimeLibraryEngine.markDone(conv.timeLibrary, timePoint)
            conversationRepository.updateConversation(conv.copy(timeLibrary = abandoned))
            scheduleFromLibrary(conv.copy(timeLibrary = abandoned))
            return
        }

        // 5.2 读取未压缩聊天记录；为空 → 跳过本次决策，直接视为"不发送"
        val messages = conversationRepository.observeMessages(convId).value.filterNot { it.isNotice }
        val nextLibrary = if (messages.isEmpty()) {
            TimeLibraryEngine.markDone(conv.timeLibrary, timePoint)
        } else {
            // 6.2 网络/API 异常自动重试 1 次（延迟 5 秒），仍失败则跳过发送
            val content = decideWithRetry(conv, messages, timePoint)
                ?.let { TimeLibraryEngine.parseDecisionResult(it) }
            if (content != null) {
                sendActiveMessage(conv, content)
            }
            // 无论发送与否，该时间点 status 立即改为 done
            TimeLibraryEngine.markDone(conv.timeLibrary, timePoint)
        }
        conversationRepository.updateConversation(conv.copy(timeLibrary = nextLibrary))
        scheduleFromLibrary(conv.copy(timeLibrary = nextLibrary))
    }

    // ===== 每日状态重置与补齐（对应 七、状态重置） =====

    /**
     * 每日首次启动 App 时调用：
     * - 检查当前日期与上次重置日期，若不同，将所有会话时间库的 done 重置为 pending
     * - 无论日期是否变化，都重注册启用会话的闹钟（App 重启 / 开机后闹钟可能已丢失）
     * - 为所有启用会话补齐当天时间库（见 [refreshLibrariesForEnabledSessions]）
     */
    suspend fun onAppStart() {
        runCatching {
            settingsRepository.ensureInitialized()
            val today = LocalDate.now().toString()
            val settings = settingsRepository.currentSnapshot()
            if (TimeLibraryEngine.shouldReset(settings.proactiveMessageLastResetDate, today)) {
                conversationRepository.conversations.value.forEach { conv ->
                    val library = conv.timeLibrary
                    if (library.isNotEmpty()) {
                        val reset = TimeLibraryEngine.resetDoneToPending(library)
                        if (reset != library) {
                            conversationRepository.updateConversation(conv.copy(timeLibrary = reset))
                        }
                    }
                }
                settingsRepository.update { it.copy(proactiveMessageLastResetDate = today) }
            }
        }
        reRegisterAlarms()
        refreshLibrariesForEnabledSessions()
    }

    /**
     * 为所有启用时间库的会话补齐当天时间库（对应算法文档 3.1 生成时机）。
     *
     * 关键修复：旧实现仅"会话首次打开时"触发生成，用户连续多天不打开某个
     * 会话就不会重新生成，导致该会话长期无主动消息；现改为每次 App 启动 /
     * 开机后自动为所有启用会话补齐，已生成当天库的会话由
     * [TimeLibraryEngine.shouldGenerate] 判定为无需生成直接跳过（无多余 LLM 调用）。
     */
    private suspend fun refreshLibrariesForEnabledSessions() {
        // 开机/冷启动时网络可能尚未就绪：先延迟再批量补齐，避免全部生成失败
        kotlinx.coroutines.delay(QuiddityConstants.ACTIVE_MESSAGE_STARTUP_DELAY_MS)
        conversationRepository.conversations.value
            .filter { it.activeMessageEnabled }
            .forEach { ensureLibraryGeneratedToday(it.id) }
    }

    // ===== 闹钟注册 / 注销 =====

    /**
     * 按当前时间库注册会话闹钟（对应 5.1 + 八、任务注销）：
     * - 未启用 / 空库 / 全部 done → 注销所有闹钟
     * - 存在未来 pending 时间点 → 注册最近的一个
     * - 无未来 pending（全部已过）→ 注销（留待次日重置或再次打开重新生成）
     */
    private fun scheduleFromLibrary(conv: Conversation) {
        if (!conv.activeMessageEnabled || conv.timeLibrary.isEmpty() ||
            TimeLibraryEngine.allDone(conv.timeLibrary)
        ) {
            alarmScheduler.cancelAll(conv.id)
            return
        }
        val nowMinutes = LocalTime.now().let { it.hour * 60 + it.minute }
        val next = TimeLibraryEngine.nextSchedulable(conv.timeLibrary, nowMinutes)
        if (next != null) {
            alarmScheduler.schedule(conv.id, next.time)
        } else {
            alarmScheduler.cancelAll(conv.id)
        }
    }

    private fun reRegisterAlarms() {
        conversationRepository.conversations.value.forEach { conv ->
            if (conv.activeMessageEnabled) {
                scheduleFromLibrary(conv)
            }
        }
    }

    // ===== 内部辅助 =====

    /**
     * 决策调用：首次失败后延迟 5 秒重试 1 次；仍失败返回 null（调用方据此标记 done）。
     * 取消异常向上传播，不参与重试。
     */
    private suspend fun decideWithRetry(
        conv: Conversation,
        messages: List<Message>,
        timePoint: String
    ): String? {
        return try {
            try {
                chatRepository.decideActiveMessage(conv, messages, timePoint)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                delay(QuiddityConstants.ACTIVE_MESSAGE_RETRY_DELAY_MS)
                chatRepository.decideActiveMessage(conv, messages, timePoint)
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            null
        }
    }

    /**
     * 以普通聊天方式发送主动消息（对应 5.2"调用发送层"）：
     * 追加一条 ASSISTANT 消息，独立头像、样式与普通 AI 消息一致。
     */
    private suspend fun sendActiveMessage(conv: Conversation, content: String) {
        val message = Message(
            id = IdGenerator.newId(IdGenerator.Prefix.AI_MESSAGE),
            conversationId = conv.id,
            role = Role.ASSISTANT,
            content = content,
            timestamp = System.currentTimeMillis(),
            tokenCount = content.length / QuiddityConstants.SPLITTER_CHARS_PER_TOKEN.toInt()
        )
        conversationRepository.appendMessage(message)
        postSentNotification(conv, content)
    }

    /**
     * 密码告知气泡：App 用已存储的真实密码生成一条 AI 消息，
     * 避免模型在对话中随口编造一个与校验不一致的密码。
     */
    private suspend fun appendPasswordNotice(conv: Conversation, password: String) {
        val message = Message(
            id = IdGenerator.newId(IdGenerator.Prefix.AI_MESSAGE),
            conversationId = conv.id,
            role = Role.ASSISTANT,
            content = "我设置的时间库查看密码是 $password，你可以用它查看今天的时间安排（会话菜单 → 主动消息 → 查看时间库）。",
            timestamp = System.currentTimeMillis()
        )
        conversationRepository.appendMessage(message)
    }

    /**
     * 主动消息发送成功后发一条通知栏消息（微信/QQ 风格）：
     * - 无论 App 是否在前台都发送，确保用户从通知栏就能看到；
     * - 点击通知回到 App。
     */
    private fun postSentNotification(conv: Conversation, content: String) {
        try {
            val channelId = "active_message_sent"
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (manager.getNotificationChannel(channelId) == null) {
                    manager.createNotificationChannel(
                        NotificationChannel(channelId, "主动消息", NotificationManager.IMPORTANCE_DEFAULT)
                    )
                }
            }
            val aiName = conv.persona.name.ifBlank { conv.title }
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pending = PendingIntent.getActivity(
                context,
                conv.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(aiName)
                .setContentText(content.replace("\n", " ").trim().let {
                    if (it.length > 80) it.take(80) + "…" else it
                })
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            manager.notify(1002, notification)
        } catch (t: Throwable) {
            Log.w("TimeLibraryRepository", "主动消息通知发送失败", t)
        }
    }
}
