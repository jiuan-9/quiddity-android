# AI 回复悬浮窗（Reply Overlay）设计文档

> 日期：2026-08-17
> 状态：已确认（按 superpowers brainstorming 流程落地）

## 目标

在本应用不可见（用户在前台使用其他应用、或处于分屏/自由窗口但看不到本应用）且 AI 正在回复时，
通过系统悬浮窗展示 AI 的回复状态：

1. 回复加载中：悬浮窗头像（默认应用图标，可在全局设置中更换）＋状态提示；
2. 回复完成：从悬浮窗头像一侧滑出气泡，展示 AI 的回复内容；
3. Agent 模式：调用工具（滑动/点击/跳转/长按等）时，气泡内容实时替换为「AI 正在干什么」；
4. 多个会话同时回复时状态不混乱（按会话隔离 + 全局聚合）。

同时把「主动消息总开关」从「显示」大项移动到新的大类「通知与提醒」。

## 现状梳理

- 设置存储：`AppSettings`（DataStore Preferences），`SettingsStore` / `SettingsRepository` /
  `SettingsViewModel` 三层结构；总设置 UI 在 `SettingsBottomSheet.kt`，目前大项为：
  显示、模型配置、视觉 OCR、生成、交互、数据、关于。「主动消息」开关错误地挂在「显示」下。
- 回复状态：`ChatViewModelState._isGenerating` 为每会话回复中标记，由
  `StreamController.startApiStream / runStream / regenerateGroupMemberMessage` 与
  `GroupController.startGroupQueueProcessor` 置位；`StreamEventProcessor.handle` 是
  所有流事件（含 `CompleteMessage / Done / Error`）的统一收口。
- Agent 工具执行：`AgentSecurity.executeGated` 是全部动作类工具的统一执行门控，已在此调用
  `OperationNotifyController.showActing/showDone/showFailed`（系统通知横幅）；`agentActionFor(name)`
  已提供「正在滑动屏幕」等动作文案。
- 多会话并发：每个会话一个 `ChatViewModel`（独立 `_isGenerating`）；群聊内部由
  `GroupReplyQueue` 串行消费，天然按会话隔离。跨会话并行时需要一个全局聚合中心。
- 应用可见性：目前无全局前台状态追踪；`MainActivity` 是唯一 Activity。

## 设计

### 1. 纯逻辑层：ReplyOverlayStateMachine

新建 `active/ReplyOverlayStateMachine.kt`（纯 Kotlin，JVM 可单测），作为全局聚合中心：

- 状态：
  - `activeReplies: Map<String, ActiveReply>`，key = conversationId；
    `ActiveReply(conversationId, conversationType, startedAt)`；
  - `toolActions: Map<String, String>`，key = conversationId，value = 动作文案；
  - `bubbleQueue: ArrayDeque<ReplyBubble>`（`ReplyBubble(text, conversationId, conversationType, createdAt)`）。
- 操作：`startReply / endReply / showToolAction / clearToolAction / enqueueBubble / consumeBubble / clearAll`。
- 派生（供 UI 消费）：
  - `activeCount`：活跃回复会话数；
  - `aggregateStatusText`：1 个 → 「正在回复…」；多个 → 「N 个对话正在回复…」；
  - `currentToolAction`：任一活跃会话存在工具动作时返回其文案（取最早开始者），否则 null；
  - `nextBubble`：队首气泡；
  - `hasVisibleContent`：`activeCount > 0 || bubbleQueue 非空 || currentToolAction != null`。
- 优先级：工具动作气泡 > 完成回复气泡 > 状态文本。多个会话同时完成时按完成顺序排队，
  一个气泡显示约 4 秒后自动消费下一条。

### 2. 全局控制器：ReplyOverlayController

新建 `active/ReplyOverlayController.kt`（进程内单例）：

- 持有 `ReplyOverlayStateMachine` 与主线程 Handler；
- `setAppVisible(visible: Boolean)`：由 `QuiddityApp` 注册的
  `ActivityLifecycleCallbacks` 维护已恢复 Activity 计数后调用；
- `startReply / endReply / showToolAction / clearToolAction / enqueueBubble`：
  转调 StateMachine 后调用 `refreshWindow()`；
- `refreshWindow()`：当且仅当 设置开关开启 且 应用不可见 且 `hasVisibleContent` 时
  启动/复用 `ReplyOverlayService`；否则通知服务隐藏/停止；
- 设置变更监听：`overlayEnabled / overlayAvatarUri` 变化时刷新；
- 打开会话：气泡/头像点击通过 `MainActivity.conversationIntent` 跳转。

### 3. 悬浮窗运行时：ReplyOverlayService + OverlayView

新建 `active/ReplyOverlayService.kt`（普通 started service，不占用前台通知）与
`active/ReplyOverlayView.kt`（View 体系，便于 overlay 窗口动画与拖动）：

- 窗口类型 `TYPE_APPLICATION_OVERLAY`，`FLAG_NOT_FOCUSABLE | FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS`；
- 布局：圆形头像（默认 `@mipmap/ic_launcher`，可用 `overlayAvatarUri` 替换）＋脉冲光环＋
  多会话数量角标＋气泡卡片；
- 气泡动画：从头像一侧水平滑出（translation + alpha），停留 4 秒后滑回，随后消费下一条；
- 头像可拖动，松手吸附左右边缘；点击头像打开应用，点击气泡打开对应会话；
- 无可见内容时移除窗口。

### 4. 设置层

- `AppSettings` 新增：`overlayEnabled: Boolean = false`、`overlayAvatarUri: String? = null`；
- `SettingsStore` 新增 key 与读写；`SettingsRepository` 新增
  `setOverlayEnabled / setOverlayAvatarUri`；`SettingsViewModel` 暴露对应 setter；
- `SettingsBottomSheet`：
  - 新增大项「通知与提醒」：主动消息总开关（自「显示」移入）＋系统条件引导卡片＋
    悬浮窗开关（未授权时跳转系统悬浮窗设置页）＋悬浮窗头像（复用 `AvatarPicker`）；
  - 「显示」大项移除主动消息开关；
- Manifest：新增 `SYSTEM_ALERT_WINDOW` 权限与 `ReplyOverlayService` 声明；
- `QuiddityApp`：注册 `ActivityLifecycleCallbacks` 维护应用可见性。

### 5. 业务挂点（最小侵入）

- 回复开始/结束：
  - `StreamController.startApiStream / runStream / regenerateGroupMemberMessage` 中
    `_isGenerating = true/false` 赋值点；
  - `GroupController.startGroupQueueProcessor` 中 `_isGenerating` 赋值点；
  统一调用 `ReplyOverlayController.startReply/endReply(conversationId, type)`。
- 回复完成：`StreamEventProcessor.handle(CompleteMessage)` → `enqueueBubble`（内容截断展示）。
- 工具动作：`AgentSecurity.executeGated` 中与 `OperationNotifyController` 并列调用
  `showToolAction(conversationId, agentActionFor(tool.name))`，done/failed 时 `clearToolAction`。
- 清理：`Event.Done / Error / stopGeneration` → `endReply`（气泡队列保留至消费完毕）。

### 6. 并发与一致性

- 状态按 conversationId 隔离，群聊队列串行天然不冲突；
- 跨会话并行 → `activeCount` 聚合、角标计数、气泡按完成顺序排队；
- 工具动作气泡优先级最高，避免「完成回复气泡」与「正在操作」互相覆盖；
- 所有状态变更在 StateMachine 内同步（synchronized），Android 侧只在主线程刷新窗口。

## 错误处理与边界

- 悬浮窗权限缺失：设置页开关跳转系统授权页，授权返回后自动刷新状态；
- 服务启动失败（权限被回收等）：捕获异常并记日志，不影响聊天主流程；
- 进程被杀：悬浮窗随进程消失（回复也随进程终止），无残留状态；
- 应用回到前台：悬浮窗立即隐藏（聊天页本身已展示加载态），状态保留，再次离屏自动恢复。

## 测试

- `ReplyOverlayStateMachineTest`（纯 JVM）：
  - 单会话 start/end 生命周期；
  - 多会话并发聚合（activeCount / 角标文案）；
  - 工具动作优先级高于回复气泡；
  - 气泡队列按完成顺序消费；
  - clearAll / endReply 清理语义。
- 全量单测 `gradlew :app:testDebugUnitTest`；
- 构建 `gradlew :app:assembleDebug`；
- 模拟器验证：开启悬浮窗 → 发消息 → 切后台观察头像/气泡 → Agent 工具动作文案。

## 范围外（YAGNI）

- 主动消息（时间库）触发的后台回复暂不接入悬浮窗（后续可复用同一 StateMachine）；
- 不做窗口透明度/大小/位置的持久化设置（会话内记忆即可）。
