# AI 回复悬浮窗（Reply Overlay）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在应用不可见且 AI 回复时显示可换头像的悬浮窗，回复完成从头像滑出气泡，Agent 工具调用时气泡显示当前动作，并支持多会话并发聚合；同时把「主动消息总开关」移入新的大项「通知与提醒」。

**Architecture:** 纯逻辑聚合层 `ReplyOverlayStateMachine`（JVM 可测）+ 进程内单例控制器 `ReplyOverlayController` + View 体系悬浮窗服务 `ReplyOverlayService`；聊天流与 Agent 工具执行通过少量挂点把状态喂给控制器，控制器按「应用可见性 + 设置开关 + 可见内容」决定窗口显示。

**Tech Stack:** Kotlin / Android Views（overlay 窗口）/ Compose（设置 UI）/ DataStore Preferences / JUnit（纯逻辑测试）

---

## 文件结构

新建：
- `app/src/main/kotlin/com/quiddity/app/active/ReplyOverlayStateMachine.kt`（纯逻辑）
- `app/src/main/kotlin/com/quiddity/app/active/ReplyOverlayController.kt`（单例控制器）
- `app/src/main/kotlin/com/quiddity/app/active/ReplyOverlayService.kt`（悬浮窗服务）
- `app/src/main/kotlin/com/quiddity/app/active/ReplyOverlayView.kt`（悬浮窗视图）
- `app/src/test/kotlin/com/quiddity/app/active/ReplyOverlayStateMachineTest.kt`（测试）

修改：
- `app/src/main/kotlin/com/quiddity/app/data/model/Models.kt`（AppSettings 新字段）
- `app/src/main/kotlin/com/quiddity/app/data/local/SettingsStore.kt`（key + 读写）
- `app/src/main/kotlin/com/quiddity/app/data/repo/SettingsRepository.kt`（setter）
- `app/src/main/kotlin/com/quiddity/app/ui/settings/SettingsViewModel.kt`（setter）
- `app/src/main/kotlin/com/quiddity/app/ui/settings/SettingsBottomSheet.kt`（新大项 + 移出主动消息）
- `app/src/main/kotlin/com/quiddity/app/ui/settings/components/AvatarPicker.kt`（可配置尺寸/文件名前缀）
- `app/src/main/kotlin/com/quiddity/app/QuiddityApp.kt`（ActivityLifecycleCallbacks）
- `app/src/main/kotlin/com/quiddity/app/ui/chat/ChatStreamController.kt`（回复开始/结束挂点）
- `app/src/main/kotlin/com/quiddity/app/ui/chat/ChatGroupController.kt`（群聊回复挂点）
- `app/src/main/kotlin/com/quiddity/app/ui/chat/ChatStreamEventProcessor.kt`（完成气泡挂点）
- `app/src/main/kotlin/com/quiddity/app/domain/agent/AgentSecurity.kt`（工具动作挂点）
- `app/src/main/AndroidManifest.xml`（权限 + 服务声明）

---

### Task 1: 纯逻辑聚合中心（TDD）

**Files:**
- Create: `app/src/main/kotlin/com/quiddity/app/active/ReplyOverlayStateMachine.kt`
- Test: `app/src/test/kotlin/com/quiddity/app/active/ReplyOverlayStateMachineTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
package com.quiddity.app.active

import com.quiddity.app.data.model.ConversationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReplyOverlayStateMachineTest {

    private fun machine() = ReplyOverlayStateMachine()

    @Test
    fun `单会话生命周期 - 开始与结束`() {
        val m = machine()
        assertFalse(m.hasVisibleContent)
        m.startReply("c1", ConversationType.SOLO)
        assertEquals(1, m.activeCount)
        assertEquals("正在回复…", m.aggregateStatusText())
        assertTrue(m.hasVisibleContent)
        m.endReply("c1")
        assertEquals(0, m.activeCount)
        assertFalse(m.hasVisibleContent)
    }

    @Test
    fun `多会话并发聚合 - 计数与文案`() {
        val m = machine()
        m.startReply("c1", ConversationType.SOLO)
        m.startReply("c2", ConversationType.GROUP)
        m.startReply("c3", ConversationType.AGENT)
        assertEquals(3, m.activeCount)
        assertEquals("3 个对话正在回复…", m.aggregateStatusText())
        m.endReply("c1")
        assertEquals(2, m.activeCount)
        assertEquals("2 个对话正在回复…", m.aggregateStatusText())
    }

    @Test
    fun `重复开始同一会话不重复计数`() {
        val m = machine()
        m.startReply("c1", ConversationType.SOLO)
        m.startReply("c1", ConversationType.SOLO)
        assertEquals(1, m.activeCount)
    }

    @Test
    fun `工具动作优先于回复气泡`() {
        val m = machine()
        m.startReply("c1", ConversationType.AGENT)
        m.enqueueBubble("bubble", "c1", ConversationType.AGENT)
        assertNull(m.currentToolAction())
        assertEquals("bubble", m.nextBubble()?.text)
        m.showToolAction("c1", "正在滑动屏幕")
        assertEquals("正在滑动屏幕", m.currentToolAction())
        assertNull(m.nextBubble())
        m.clearToolAction("c1")
        assertNull(m.currentToolAction())
        assertEquals("bubble", m.nextBubble()?.text)
    }

    @Test
    fun `气泡队列按完成顺序消费`() {
        val m = machine()
        m.startReply("c1", ConversationType.SOLO)
        m.enqueueBubble("first", "c1", ConversationType.SOLO)
        m.enqueueBubble("second", "c2", ConversationType.SOLO)
        assertEquals("first", m.consumeBubble()?.text)
        assertEquals("second", m.consumeBubble()?.text)
        assertNull(m.consumeBubble())
    }

    @Test
    fun `endReply 保留未消费气泡`() {
        val m = machine()
        m.startReply("c1", ConversationType.SOLO)
        m.enqueueBubble("keep", "c1", ConversationType.SOLO)
        m.endReply("c1")
        assertEquals(0, m.activeCount)
        assertTrue(m.hasVisibleContent)
        assertEquals("keep", m.nextBubble()?.text)
    }

    @Test
    fun `clearAll 清空全部`() {
        val m = machine()
        m.startReply("c1", ConversationType.SOLO)
        m.startReply("c2", ConversationType.AGENT)
        m.showToolAction("c2", "正在点击")
        m.enqueueBubble("b", "c1", ConversationType.SOLO)
        m.clearAll()
        assertEquals(0, m.activeCount)
        assertNull(m.currentToolAction())
        assertNull(m.nextBubble())
        assertFalse(m.hasVisibleContent)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.quiddity.app.active.ReplyOverlayStateMachineTest"`
Expected: 编译失败 / `unresolved reference: ReplyOverlayStateMachine`

- [ ] **Step 3: 实现最小逻辑**

```kotlin
package com.quiddity.app.active

import com.quiddity.app.data.model.ConversationType

/**
 * 悬浮窗回复状态聚合中心（纯 Kotlin，JVM 可单测）。
 *
 * 负责：按会话隔离回复状态；跨会话聚合计数；Agent 工具动作与完成回复气泡的
 * 优先级调度（工具动作 > 回复气泡 > 状态文本）；气泡按完成顺序排队消费。
 */
class ReplyOverlayStateMachine {

    data class ActiveReply(
        val conversationId: String,
        val conversationType: ConversationType,
        val startedAt: Long = System.currentTimeMillis()
    )

    data class ReplyBubble(
        val text: String,
        val conversationId: String,
        val conversationType: ConversationType,
        val createdAt: Long = System.currentTimeMillis()
    )

    private val lock = Any()
    private val active = LinkedHashMap<String, ActiveReply>()
    private val toolActions = LinkedHashMap<String, String>()
    private val bubbles = ArrayDeque<ReplyBubble>()

    val activeCount: Int
        get() = synchronized(lock) { active.size }

    val hasVisibleContent: Boolean
        get() = synchronized(lock) {
            active.isNotEmpty() || toolActions.isNotEmpty() || bubbles.isNotEmpty()
        }

    fun startReply(conversationId: String, type: ConversationType) {
        synchronized(lock) {
            if (!active.containsKey(conversationId)) {
                active[conversationId] = ActiveReply(conversationId, type)
            }
        }
    }

    fun endReply(conversationId: String) {
        synchronized(lock) {
            active.remove(conversationId)
            toolActions.remove(conversationId)
        }
    }

    fun showToolAction(conversationId: String, actionText: String) {
        synchronized(lock) {
            if (active.containsKey(conversationId)) {
                toolActions[conversationId] = actionText
            }
        }
    }

    fun clearToolAction(conversationId: String) {
        synchronized(lock) { toolActions.remove(conversationId) }
    }

    fun enqueueBubble(
        text: String,
        conversationId: String,
        conversationType: ConversationType
    ) {
        synchronized(lock) {
            bubbles.addLast(ReplyBubble(text, conversationId, conversationType))
        }
    }

    fun consumeBubble(): ReplyBubble? = synchronized(lock) {
        if (bubbles.isEmpty()) null else bubbles.removeFirst()
    }

    fun nextBubble(): ReplyBubble? = synchronized(lock) {
        bubbles.firstOrNull()
    }

    fun currentToolAction(): String? = synchronized(lock) {
        toolActions.values.firstOrNull()
    }

    fun aggregateStatusText(): String = synchronized(lock) {
        when (active.size) {
            0 -> ""
            1 -> "正在回复…"
            else -> "${active.size} 个对话正在回复…"
        }
    }

    fun clearAll() {
        synchronized(lock) {
            active.clear()
            toolActions.clear()
            bubbles.clear()
        }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.quiddity.app.active.ReplyOverlayStateMachineTest"`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add docs/superpowers/specs docs/superpowers/plans app/src/main/kotlin/com/quiddity/app/active/ReplyOverlayStateMachine.kt app/src/test/kotlin/com/quiddity/app/active/ReplyOverlayStateMachineTest.kt
git commit -m "feat(overlay): 新增回复悬浮窗状态聚合中心（纯逻辑）"
```

---

### Task 2: 设置模型层

**Files:**
- Modify: `app/src/main/kotlin/com/quiddity/app/data/model/Models.kt`（AppSettings 尾部新字段）
- Modify: `app/src/main/kotlin/com/quiddity/app/data/local/SettingsStore.kt`
- Modify: `app/src/main/kotlin/com/quiddity/app/data/repo/SettingsRepository.kt`
- Modify: `app/src/main/kotlin/com/quiddity/app/ui/settings/SettingsViewModel.kt`

- [ ] **Step 1: AppSettings 新字段**

在 `Models.kt` 的 `AppSettings` 中 `soloChatCounter` 之后追加：

```kotlin
    /**
     * 回复悬浮窗总开关（需系统悬浮窗权限）。
     * - true = 应用不可见且 AI 回复时显示悬浮窗头像/气泡
     */
    val overlayEnabled: Boolean = false,
    /**
     * 悬浮窗头像 URI（null = 默认应用图标）。
     */
    val overlayAvatarUri: String? = null,
```

- [ ] **Step 2: SettingsStore key + 读写**

`Keys` 增加：
```kotlin
        val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val OVERLAY_AVATAR_URI = stringPreferencesKey("overlay_avatar_uri")
```

`toAppSettings()` 的 `AppSettings(...)` 中增加：
```kotlin
                overlayEnabled = this[Keys.OVERLAY_ENABLED] ?: d.overlayEnabled,
                overlayAvatarUri = this[Keys.OVERLAY_AVATAR_URI] ?: d.overlayAvatarUri,
```

`update()` 的写盘段增加：
```kotlin
                prefs[Keys.OVERLAY_ENABLED] = next.overlayEnabled
                next.overlayAvatarUri?.let { prefs[Keys.OVERLAY_AVATAR_URI] = it }
                    ?: prefs.remove(Keys.OVERLAY_AVATAR_URI)
```

- [ ] **Step 3: SettingsRepository setter**

```kotlin
    suspend fun setOverlayEnabled(enabled: Boolean) = update { it.copy(overlayEnabled = enabled) }
    suspend fun setOverlayAvatarUri(uri: String?) = update { it.copy(overlayAvatarUri = uri) }
```

- [ ] **Step 4: SettingsViewModel setter**

```kotlin
    fun setOverlayEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setOverlayEnabled(enabled)
    }

    fun setOverlayAvatarUri(uri: String?) = viewModelScope.launch {
        settingsRepository.setOverlayAvatarUri(uri)
    }
```

- [ ] **Step 5: 构建与提交**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

```bash
git add app/src/main/kotlin/com/quiddity/app/data/model/Models.kt app/src/main/kotlin/com/quiddity/app/data/local/SettingsStore.kt app/src/main/kotlin/com/quiddity/app/data/repo/SettingsRepository.kt app/src/main/kotlin/com/quiddity/app/ui/settings/SettingsViewModel.kt
git commit -m "feat(overlay): 设置层新增悬浮窗开关与头像字段"
```

---

### Task 3: 悬浮窗控制器

**Files:**
- Create: `app/src/main/kotlin/com/quiddity/app/active/ReplyOverlayController.kt`

- [ ] **Step 1: 实现控制器**

```kotlin
package com.quiddity.app.active

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.quiddity.app.MainActivity
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.di.ServiceLocator
import com.quiddity.app.util.CrashLogger

/**
 * 回复悬浮窗全局控制器（进程内单例）。
 *
 * 状态聚合在 [ReplyOverlayStateMachine]；本类负责：
 * 1) 桥接聊天流 / Agent 工具执行挂点；
 * 2) 应用可见性（QuiddityApp 的 ActivityLifecycleCallbacks 驱动）；
 * 3) 设置开关与头像；
 * 4) 决定何时启动/停止 [ReplyOverlayService]。
 */
object ReplyOverlayController {

    private val machine = ReplyOverlayStateMachine()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var service: ReplyOverlayService? = null

    @Volatile
    private var appVisible = true

    @Volatile
    private var enabled = false

    @Volatile
    var overlayAvatarUri: String? = null
        private set

    private var openedConversation: Pair<String, ConversationType>? = null

    /** QuiddityApp 在 ActivityLifecycleCallbacks 中驱动。 */
    fun setAppVisible(visible: Boolean) {
        if (appVisible == visible) return
        appVisible = visible
        refreshWindow()
    }

    /** 设置变更（SettingsViewModel 或启动时读取后调用）。 */
    fun updateEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (!value) {
            machine.clearAll()
            dismissWindow()
        } else {
            refreshWindow()
        }
    }

    fun updateAvatarUri(uri: String?) {
        if (overlayAvatarUri == uri) return
        overlayAvatarUri = uri
        service?.applyAvatar(uri)
    }

    fun startReply(conversationId: String, type: ConversationType) {
        machine.startReply(conversationId, type)
        refreshWindow()
    }

    fun endReply(conversationId: String) {
        machine.endReply(conversationId)
        refreshWindow()
    }

    fun showToolAction(conversationId: String, actionText: String) {
        machine.showToolAction(conversationId, actionText)
        refreshWindow()
    }

    fun clearToolAction(conversationId: String) {
        machine.clearToolAction(conversationId)
        refreshWindow()
    }

    fun enqueueBubble(
        text: String,
        conversationId: String,
        conversationType: ConversationType
    ) {
        machine.enqueueBubble(text, conversationId, conversationType)
        refreshWindow()
    }

    /** 气泡展示完毕（视图回调）→ 消费下一条。 */
    fun consumeBubble() {
        val next = machine.consumeBubble()
        refreshWindow()
        if (next != null) service?.showBubble(next)
    }

    fun onServiceStarted(instance: ReplyOverlayService) {
        service = instance
        service?.applyAvatar(overlayAvatarUri)
        render()
    }

    fun onServiceStopped(instance: ReplyOverlayService) {
        if (service === instance) service = null
    }

    fun dismissWindow() {
        mainHandler.post {
            service?.stopSelf()
        }
    }

    private fun refreshWindow() {
        mainHandler.post {
            if (!enabled || appVisible || !machine.hasVisibleContent) {
                service?.stopSelf()
                return@post
            }
            if (service == null) {
                val context = ServiceLocator.applicationContext
                runCatching {
                    context.startService(Intent(context, ReplyOverlayService::class.java))
                }.onFailure {
                    CrashLogger.logException(context, it, "ReplyOverlayController.startService")
                }
            } else {
                render()
            }
        }
    }

    private fun render() {
        val svc = service ?: return
        val tool = machine.currentToolAction()
        val count = machine.activeCount
        val status = machine.aggregateStatusText()
        val bubble = machine.nextBubble()
        svc.render(count, tool, status, bubble)
    }

    /** 点击悬浮窗头像 → 打开应用。 */
    fun openApp() {
        val context = ServiceLocator.applicationContext
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    /** 点击气泡 → 打开对应会话（记录最近一次，由视图调用）。 */
    fun setOpenedConversation(id: String, type: ConversationType) {
        openedConversation = id to type
    }

    fun openLastConversation() {
        val (id, type) = openedConversation ?: return run { openApp() }
        val context = ServiceLocator.applicationContext
        context.startActivity(MainActivity.conversationIntent(context, id, type))
    }

    fun snapshotForTest(): ReplyOverlayStateMachine = machine
}
```

- [ ] **Step 2: 编译**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL（`ReplyOverlayService` 未实现会导致失败，见 Task 4 前可先建空壳）

---

### Task 4: 悬浮窗服务与视图

**Files:**
- Create: `app/src/main/kotlin/com/quiddity/app/active/ReplyOverlayService.kt`
- Create: `app/src/main/kotlin/com/quiddity/app/active/ReplyOverlayView.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Manifest 权限与服务**

在 `AndroidManifest.xml` 的 uses-permission 区新增：
```xml
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
```

在 `</application>` 前的 service 区新增：
```xml
        <!-- 回复悬浮窗：应用不可见时展示 AI 回复状态与完成气泡 -->
        <service
            android:name=".active.ReplyOverlayService"
            android:exported="false" />
```

- [ ] **Step 2: OverlayView（头像 + 气泡 + 拖动 + 动画）**

`ReplyOverlayView.kt` 实现要点：
- 圆形头像 `ImageView`（`coil` 的 `ImageLoader` 或 `AsyncImage` 不可用于 View，改用 `coil.load()` 扩展或直接 `setImageDrawable`）；
- 气泡 `TextView`（圆角背景），初始 `translationX` 偏移在头像外侧，`animate()` 滑入/滑出；
- `setOnTouchListener` 拖动窗口（`WindowManager.updateViewLayout`），松手吸附左右边缘；
- 回调接口暴露 `onBubbleDismissed`（交给 Service → Controller.consumeBubble）。

- [ ] **Step 3: Service 装配窗口**

`ReplyOverlayService.kt` 要点：
- `onCreate`：`windowManager = getSystemService(WINDOW_SERVICE)`；
- `onStartCommand`：注册 `ReplyOverlayController.onServiceStarted(this)`；
- `render(count, tool, status, bubble)`：更新角标/状态/气泡；
- `showBubble(bubble)`：播放滑出动画，4 秒后回调消费；
- `onDestroy`：移除窗口、注销控制器引用。

- [ ] **Step 4: 编译**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/kotlin/com/quiddity/app/active/ReplyOverlayService.kt app/src/main/kotlin/com/quiddity/app/active/ReplyOverlayView.kt app/src/main/kotlin/com/quiddity/app/active/ReplyOverlayController.kt
git commit -m "feat(overlay): 悬浮窗服务与视图（头像/气泡/拖动/动画）"
```

---

### Task 5: 业务挂点

**Files:**
- Modify: `app/src/main/kotlin/com/quiddity/app/QuiddityApp.kt`
- Modify: `app/src/main/kotlin/com/quiddity/app/ui/chat/ChatStreamController.kt`
- Modify: `app/src/main/kotlin/com/quiddity/app/ui/chat/ChatGroupController.kt`
- Modify: `app/src/main/kotlin/com/quiddity/app/ui/chat/ChatStreamEventProcessor.kt`
- Modify: `app/src/main/kotlin/com/quiddity/app/domain/agent/AgentSecurity.kt`

- [ ] **Step 1: 应用可见性**

`QuiddityApp.onCreate` 中 `ServiceLocator.init(this)` 后：
```kotlin
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var resumed = 0
            override fun onActivityResumed(activity: Activity) {
                resumed++
                ReplyOverlayController.setAppVisible(resumed > 0)
            }
            override fun onActivityPaused(activity: Activity) {
                resumed = (resumed - 1).coerceAtLeast(0)
                ReplyOverlayController.setAppVisible(resumed > 0)
            }
            override fun onActivityCreated(a: Activity, s: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, s: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
```

- [ ] **Step 2: 回复开始/结束挂点**

`ChatStreamController` 新增辅助方法：
```kotlin
    private fun markReplyStarted() {
        val conv = conversation.value ?: return
        com.quiddity.app.active.ReplyOverlayController.startReply(conv.id, conv.type)
    }

    private fun markReplyEnded() {
        val conv = conversation.value ?: return
        com.quiddity.app.active.ReplyOverlayController.endReply(conv.id)
    }
```

在 `startApiStream`、`runStream`、`regenerateGroupMemberMessage` 中：
- `_isGenerating.value = true` 之后调用 `markReplyStarted()`；
- `_isGenerating.value = false` 之后调用 `markReplyEnded()`；

`ChatGroupController.startGroupQueueProcessor` 同理（`_isGenerating` true/false 两侧）。

- [ ] **Step 3: 完成回复气泡挂点**

`ChatStreamEventProcessor.handle` 的 `CompleteMessage` 分支末尾追加：
```kotlin
            // 悬浮窗：回复完成 → 入队展示气泡（应用不可见时）
            val content = target.content.trim().takeIf { it.isNotBlank() }
            if (content != null && !target.isNotice && !target.isThinking) {
                com.quiddity.app.active.ReplyOverlayController.enqueueBubble(
                    content.take(MAX_BUBBLE_CHARS),
                    conversationId,
                    conversation.value?.type ?: ConversationType.SOLO
                )
            }
```
`MAX_BUBBLE_CHARS = 80`（文件顶部私有常量）。

- [ ] **Step 4: 工具动作挂点**

`AgentSecurity.executeGated` 中 `OperationNotifyController.showActing(...)` 之后追加：
```kotlin
            com.quiddity.app.active.ReplyOverlayController.showToolAction(
                ctx.conversation?.id.orEmpty(),
                agentActionFor(tool.name)
            )
```
并在 `showDone / showFailed` 之后追加：
```kotlin
            com.quiddity.app.active.ReplyOverlayController.clearToolAction(
                ctx.conversation?.id.orEmpty()
            )
```

- [ ] **Step 5: 启动时应用设置 + 清理**

`QuiddityApp.onCreate` 或 `ServiceLocator.init` 后：
```kotlin
        ServiceLocator.appScope.launch {
            val settings = ServiceLocator.settingsRepository.currentSnapshot()
            ReplyOverlayController.updateEnabled(settings.overlayEnabled)
            ReplyOverlayController.updateAvatarUri(settings.overlayAvatarUri)
        }
```

`ChatStreamEventProcessor.handle` 的 `Done` / `Error` 分支追加 `endReply(conversationId)`（在清理 OperationNotifyController 处并列）。

- [ ] **Step 6: 编译 + 单测**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, tests PASS

- [ ] **Step 7: 提交**

```bash
git add app/src/main/kotlin/com/quiddity/app/QuiddityApp.kt app/src/main/kotlin/com/quiddity/app/ui/chat/ChatStreamController.kt app/src/main/kotlin/com/quiddity/app/ui/chat/ChatGroupController.kt app/src/main/kotlin/com/quiddity/app/ui/chat/ChatStreamEventProcessor.kt app/src/main/kotlin/com/quiddity/app/domain/agent/AgentSecurity.kt
git commit -m "feat(overlay): 接入回复生命周期与 Agent 工具动作可视化"
```

---

### Task 6: 设置 UI

**Files:**
- Modify: `app/src/main/kotlin/com/quiddity/app/ui/settings/components/AvatarPicker.kt`
- Modify: `app/src/main/kotlin/com/quiddity/app/ui/settings/SettingsBottomSheet.kt`

- [ ] **Step 1: AvatarPicker 可配置**

`AvatarPicker` 增加参数：
```kotlin
@Composable
fun AvatarPicker(
    avatarUri: String?,
    onPicked: (String) -> Unit,
    size: Dp = 96.dp,
    imageNamePrefix: String = "user_avatar"
)
```
将内部 `cropOutputName = "user_avatar_${...}"` 改为 `"${imageNamePrefix}_${...}"`，
`96.dp` 尺寸改用 `size`。

- [ ] **Step 2: 新增大项「通知与提醒」**

在 `SettingsBottomSheet` 的「显示」大项之前插入新 Section，并把「主动消息」ToggleRow
与 `ActiveMessagePermissionCard` 整体从「显示」移入；追加悬浮窗开关与头像：

```kotlin
                        // ===== Section: 通知与提醒 =====
                        item(key = "section_notify", contentType = { "section" }) {
                            SettingsSectionCard(title = "通知与提醒") {
                            // 主动消息总开关（自「显示」移入，逻辑不变）
                            ToggleRow(
                                icon = Icons.Filled.Notifications,
                                title = "主动消息",
                                subtitle = ...,
                                checked = settings.proactiveMessageEnabled,
                                onCheckedChange = { ...原逻辑... }
                            )
                            if (settings.proactiveMessageEnabled) {
                                ActiveMessagePermissionCard(...)
                            }
                            // 悬浮窗开关
                            ToggleRow(
                                icon = Icons.Filled.PictureInPicture,
                                title = "回复悬浮窗",
                                subtitle = if (settings.overlayEnabled)
                                    "已开启：离开应用时展示 AI 回复状态"
                                else "关闭：AI 回复时不显示悬浮窗",
                                checked = settings.overlayEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled && !Settings.canDrawOverlays(context)) {
                                        // 跳系统授权页
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    } else {
                                        viewModel.setOverlayEnabled(enabled)
                                    }
                                }
                            )
                            // 悬浮窗头像
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AvatarPicker(
                                    avatarUri = settings.overlayAvatarUri,
                                    onPicked = { viewModel.setOverlayAvatarUri(it) },
                                    size = 64.dp,
                                    imageNamePrefix = "overlay_avatar"
                                )
                                Text("悬浮窗头像", ...)
                            }
                            }
                        }
```

- [ ] **Step 3: 移除「显示」中的主动消息**

删除 `SettingsBottomSheet`「显示」Section 内的主动消息 ToggleRow 与
`ActiveMessagePermissionCard` 块。

- [ ] **Step 4: 权限返回后自动刷新**

在 SettingsBottomSheet 顶部（或 MainActivity）监听 `ON_RESUME`，当
`Settings.canDrawOverlays(context)` 为 true 且用户此前请求过授权时，
把 `settings.overlayEnabled` 置 true（由 ViewModel 持久化）。简单方案：
在 ToggleRow 的 `onCheckedChange` 授权分支保存 `overlayEnabled=true` 并让
`updateEnabled(true)` 在授权页返回后生效。

- [ ] **Step 5: 编译 + 提交**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

```bash
git add app/src/main/kotlin/com/quiddity/app/ui/settings/components/AvatarPicker.kt app/src/main/kotlin/com/quiddity/app/ui/settings/SettingsBottomSheet.kt
git commit -m "feat(settings): 新增通知与提醒大项，悬浮窗开关与头像，主动消息移入"
```

---

### Task 7: 构建与验证

- [ ] **Step 1: 全量单测**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: PASS

- [ ] **Step 2: 调试包构建**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: `app\build\outputs\apk\debug\app-debug.apk` 生成

- [ ] **Step 3: 模拟器验证**

```bat
D:\start-emulator.bat
D:\android-sdk\platform-tools\adb.exe wait-for-device
D:\android-sdk\platform-tools\adb.exe install -r app-debug.apk
D:\android-sdk\platform-tools\adb.exe shell am start -n com.quiddity.app.debug/com.quiddity.app.MainActivity
```
手工步骤：设置 → 通知与提醒 → 开启回复悬浮窗（授予悬浮窗权限）→ 发消息 → 切后台 → 观察头像/气泡。

- [ ] **Step 4: 提交收尾**

```bash
git add -A
git commit -m "feat(overlay): 回复悬浮窗完成构建验证"
```
