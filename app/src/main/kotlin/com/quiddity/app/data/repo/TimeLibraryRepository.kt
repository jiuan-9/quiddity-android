package com.quiddity.app.data.repo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.quiddity.app.R
import com.quiddity.app.active.AlarmScheduler
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.data.model.Message
import com.quiddity.app.data.model.Role
import com.quiddity.app.data.model.TimePoint
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
        // 全局总开关（1.6.2）：总设置关闭时，任何会话都不生成、不触发
        if (!settingsRepository.currentSnapshot().proactiveMessageEnabled) {
            alarmScheduler.cancelAll(convId)
            return GenerationOutcome.NotEnabled
        }
        val conv = conversationRepository.getConversation(convId)
            ?: return GenerationOutcome.NotEnabled
        // Agent 会话不参与主动消息（时间库）：一律不生成、不注册闹钟
        if (conv.type == ConversationType.AGENT) {
            alarmScheduler.cancelAll(convId)
            return GenerationOutcome.NotEnabled
        }
        val today = LocalDate.now().toString()
        if (!TimeLibraryEngine.shouldGenerate(conv.activeMessageEnabled, conv.timeLibraryGeneratedDate, today)) {
            return if (conv.activeMessageEnabled) GenerationOutcome.UpToDate else GenerationOutcome.NotEnabled
        }
        if (!generatingInFlight.add(convId)) return GenerationOutcome.Generating
        return try {
            val raw = runCatching { chatRepository.generateTimeLibrary(conv) }.getOrNull()
            val generatedTimes = raw?.let {
                TimeLibraryEngine.parseGeneratedTimes(
                    it,
                    disabledSlots = conv.disabledTimeSlots.toSet()
                )
            }.orEmpty()
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
                conversationRepository.updateConversation(
                    conv.copy(
                        timeLibrary = merged,
                        timeLibraryGeneratedDate = today
                    )
                )
                scheduleFromLibrary(conv.copy(timeLibrary = merged))
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
        // Agent 会话不支持主动消息：无论开关状态一律停用
        if (conv.type == ConversationType.AGENT) {
            alarmScheduler.cancelAll(conv.id)
            return GenerationOutcome.NotEnabled
        }
        // 全局总开关关闭时，会话内禁止开启（UI 层同步置灰，此处双保险）
        if (enabled && !settingsRepository.currentSnapshot().proactiveMessageEnabled) {
            alarmScheduler.cancelAll(conv.id)
            return GenerationOutcome.NotEnabled
        }
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

    /** 用户手动保存时间库：写入时间点与禁用框，并立即重新注册闹钟。 */
    suspend fun saveTimeLibrary(
        conv: Conversation,
        times: List<String>,
        disabledSlots: List<Int>
    ) {
        val sanitizedTimes = times.mapNotNull { raw ->
            val minutes = TimeLibraryEngine.parseMinutes(raw) ?: return@mapNotNull null
            val hour = (minutes / 60).toString().padStart(2, '0')
            val minute = (minutes % 60).toString().padStart(2, '0')
            "$hour:$minute"
        }
        val sanitizedDisabled = disabledSlots
            .filter { it in 0 until TimeLibraryEngine.SLOT_COUNT }
            .distinct()
            .sorted()
        val updated = conv.copy(
            timeLibrary = sanitizedTimes.map { TimePoint(it) },
            disabledTimeSlots = sanitizedDisabled
        )
        conversationRepository.updateConversation(updated)
        scheduleFromLibrary(updated)
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
        // 全局总开关关闭（运行期被用户关闭）：不发送，注销闹钟
        if (!settingsRepository.currentSnapshot().proactiveMessageEnabled) {
            alarmScheduler.cancelAll(convId)
            return
        }
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
        val messages = conversationRepository.observeMessages(convId).value
            .filterNot { it.isNotice || it.isThinking }
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
            // 全局总开关关闭：不重置、不注册、不补齐，注销全部会话闹钟
            if (!settingsRepository.currentSnapshot().proactiveMessageEnabled) {
                conversationRepository.conversations.value.forEach { alarmScheduler.cancelAll(it.id) }
                return
            }
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
            .filter { it.activeMessageEnabled && it.type != ConversationType.AGENT }
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
        // Agent 会话不参与主动消息：一律注销闹钟
        if (conv.type == ConversationType.AGENT) {
            alarmScheduler.cancelAll(conv.id)
            return
        }
        // 全局总开关关闭：一律注销（覆盖重注册/手动保存等所有注册路径）
        if (!settingsRepository.currentSnapshot().proactiveMessageEnabled) {
            alarmScheduler.cancelAll(conv.id)
            return
        }
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
            if (conv.activeMessageEnabled && conv.type != ConversationType.AGENT) {
                scheduleFromLibrary(conv)
            }
        }
    }

    /**
     * 全局总开关变化（1.6.2）：
     * - 开启：重注册全部启用会话的闹钟，并补齐当天时间库；
     * - 关闭：立即注销所有会话闹钟（主动消息全局停止）。
     */
    suspend fun onGlobalEnabledChanged(enabled: Boolean) {
        if (enabled) {
            reRegisterAlarms()
            refreshLibrariesForEnabledSessions()
        } else {
            conversationRepository.conversations.value.forEach { alarmScheduler.cancelAll(it.id) }
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
     * 主动消息发送成功后发一条通知栏消息（微信/QQ 风格）：
     * - 无论 App 是否在前台都发送，确保用户从通知栏就能看到；
     * - 点击通知直接进入该会话的聊天框（而非只打开应用）。
     */
    private fun postSentNotification(conv: Conversation, content: String) {
        try {
            val channelId = "active_message_sent"
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val aiName = conv.persona.name.ifBlank { conv.title }
            val intent = com.quiddity.app.MainActivity.conversationIntent(context, conv.id, conv.type)
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
            // 通知 ID 按会话派生：多个会话的主动消息互不覆盖
            manager.notify(notificationIdFor(conv.id), notification)
        } catch (t: Throwable) {
            Log.w("TimeLibraryRepository", "主动消息通知发送失败", t)
        }
    }

    private fun notificationIdFor(convId: String): Int =
        1_000 + (convId.hashCode() and 0xFFFFFF)
}