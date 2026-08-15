package com.quiddity.app.active

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.quiddity.app.MainActivity
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.R

/**
 * 行动通知弹窗控制器（应用侧发送，非 Agent 消息）。
 *
 * Agent 执行行动类工具（点击 / 滑动 / 输入等）时，由应用发出**系统通知弹窗**：
 * 高优先级通知以系统 heads-up 形式从屏幕顶部弹出（不是静默的通知栏文案），
 * 说明 Agent 正要执行的操作；每一步都是新的弹窗消息，执行完毕后再弹
 * 完成 / 失败通知弹窗并短暂停留后自动从通知栏移除。文案不使用彩色 emoji；
 * 点击通知直接进入对应会话框。
 *
 * 权限：需要「通知」权限（Android 13+ 运行时申请，App 已声明 POST_NOTIFICATIONS）。
 */
object OperationNotifyController {

    private const val CHANNEL_ID = "agent_action"
    private const val NOTIFY_ID_BASE = 3100
    private const val OPEN_APP_REQUEST_CODE = 3100
    private const val DONE_DISMISS_MS = 2_000L
    private const val ACTING_TIMEOUT_MS = 15_000L

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 当前宿主 Activity（MainActivity 注册，onDestroy 注销），用于取应用上下文。 */
    @Volatile
    private var hostActivity: Activity? = null

    /** Android 13+ 通知权限缺失时的申请回调（MainActivity 注册，触发系统授权弹窗）。 */
    @Volatile
    var onRequestNotificationPermission: (() -> Unit)? = null

    private var seq = 0

    private var lastId: Int? = null

    /** 本次进程内是否已申请过通知权限（避免被拒后每次行动都重复弹申请）。 */
    private var permissionRequestedThisSession = false

    /** MainActivity 在 onCreate 注册。 */
    fun attach(activity: Activity) {
        hostActivity = activity
    }

    /** MainActivity 在 onDestroy 注销并清理通知弹窗。 */
    fun detach(activity: Activity) {
        if (hostActivity === activity) {
            hostActivity = null
        }
        onRequestNotificationPermission = null
        dismiss()
    }

    /** 行动进行中弹窗：保留到本步结束，被下一步或完成弹窗替换。 */
    fun showActing(
        description: String,
        conversationId: String? = null,
        conversationType: ConversationType? = null
    ) {
        post("Agent 正在执行", description, ACTING_TIMEOUT_MS, conversationId, conversationType)
    }

    /** 行动完成弹窗：短暂展示后自动消失。 */
    fun showDone(
        description: String,
        conversationId: String? = null,
        conversationType: ConversationType? = null
    ) {
        post("操作完成", description, DONE_DISMISS_MS, conversationId, conversationType)
    }

    /** 行动失败弹窗：短暂展示后自动消失。 */
    fun showFailed(
        description: String,
        conversationId: String? = null,
        conversationType: ConversationType? = null
    ) {
        post("操作失败", description, DONE_DISMISS_MS, conversationId, conversationType)
    }

    /** 立即移除所有行动通知弹窗（任务结束 / 出错 / 停止生成时兜底清理）。 */
    fun dismiss() {
        mainHandler.post {
            val manager = notificationManager() ?: return@post
            for (i in 0 until seq) {
                runCatching { manager.cancel(NOTIFY_ID_BASE + i) }
            }
            lastId = null
        }
    }

    private fun post(
        title: String,
        text: String,
        timeoutMs: Long,
        conversationId: String?,
        conversationType: ConversationType?
    ) {
        mainHandler.post {
            val context = hostActivity?.applicationContext ?: return@post
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return@post
            // Android 13+ 需要运行时通知权限；缺失时系统会静默丢弃通知，
            // 这里主动申请一次（系统授权弹窗），本次跳过发送
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !NotificationManagerCompat.from(context).areNotificationsEnabled()
            ) {
                if (!permissionRequestedThisSession) {
                    permissionRequestedThisSession = true
                    onRequestNotificationPermission?.invoke()
                }
                return@post
            }
            ensureChannel(manager)
            // 每一步都是新的通知弹窗：先撤掉上一步，再以新 ID 弹出，触发新的 heads-up
            lastId?.let { runCatching { manager.cancel(it) } }
            val id = NOTIFY_ID_BASE + seq
            seq++
            lastId = id
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setContentIntent(conversationPendingIntent(context, conversationId, conversationType))
                .setTimeoutAfter(timeoutMs)
                .build()
            runCatching { manager.notify(id, notification) }
        }
    }

    /** 通知点击：有会话上下文时直接进入对应会话框，否则只打开应用。 */
    private fun conversationPendingIntent(
        context: Context,
        conversationId: String?,
        conversationType: ConversationType?
    ): PendingIntent {
        val intent = if (conversationId != null && conversationType != null) {
            MainActivity.conversationIntent(context, conversationId, conversationType)
        } else {
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
        return PendingIntent.getActivity(
            context,
            conversationId?.hashCode() ?: OPEN_APP_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Agent 行动弹窗",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Agent 执行操作时的系统通知弹窗（每一步弹出新消息）"
                        lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    }
                )
            }
        }
    }

    private fun notificationManager(): NotificationManager? {
        val context = hostActivity?.applicationContext ?: return null
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    }
}
