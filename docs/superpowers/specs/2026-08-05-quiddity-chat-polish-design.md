# Quiddity 聊天体验优化（11 项）设计文档

> 日期：2026-08-05
> 范围：快速设定（1-5）、AI 对话行为（6-7）、UI 交互（8-11）
> 目标：效果与用户满意度。所有修复均融入现有架构，不使用临时补丁。

## 决策记录

- 第 6/7 条：对话纪律**整合进现有 system 提示词**、置于最前、**不与现有内容重复**（用户明确要求）。
- 第 2 条：快速设定必填按"预览弹窗标红缺失项 + 补全后才能填入"处理（用户授权按我方判断）。
- 第 9 条：发送延迟按"输入框为空且静默满 delayMs 才发请求，任何编辑（含退格）重置计时"处理（用户授权按我方判断）。
- 第 10 条：只动设置页（SettingsBottomSheet / ApiEditBottomSheet），会话页键盘处理（ChatScreen 手动偏移）完全不动。

## 1. 快速设定填入后直接退出设置

**现状**：`HamburgerMenu.kt` 中 `QuickSetupPanel.onApply` 只调用 `applyQuickSetupResult` + Toast，面板停留在汉堡菜单内。

**方案**：在 HamburgerMenu 的 `onApply` 回调里追加 `currentPanel = null; onDismiss()`，填入（含"覆盖确认"路径，两路径共用同一回调）后直接关闭设置、回到完整会话页。复用 `PersonaPanel` 已有的"保存并退出"模式。

**涉及文件**：`HamburgerMenu.kt`（仅回调追加两行）。

## 2. 快速设定每一项必填

**现状**：`QuickSetupResultDialog` 允许空字段直接点"填入"。

**方案**：
- 按当前档位计算必填集合：`tier.aiPersonaFields()` 全部字段 + 用户人设 5 字段 + 场景 +（档位含记忆时）记忆。
- 任一必填字段为空 → "填入"按钮禁用，并在弹窗顶部提示缺失字段列表；对应字段输入框红色描边。
- `[性别]` 解析回退"暂不设置"视为已填（不改动解析逻辑）。

**涉及文件**：`QuickSetupPanel.kt`（校验辅助函数 + 弹窗 UI）。

## 3+4. 快速设定提示词重写（发散 + 质量 + 省 token）

**现状**：`QUICK_SETUP_SYSTEM_PROMPT` 过度强调"不得臆测、宁可不写、字数少完全正常、严禁注水"，模型产出安全但空洞的短模板；`buildQuickSetupUserPrompt` 无充实度要求。

**方案**：重写 system 提示词，**输出格式与解析标记保持完全不变**（解析器依赖 `【AI人设】【用户人设】【场景设置】【记忆设置】` 与 `[字段]` 标记、4 字世界类型规则）。

### 新 QUICK_SETUP_SYSTEM_PROMPT

```text
你是一个"人设生成器"。用户会给你一段可能十分模糊的人设描述，你要基于它生成一份结构完整、可直接被 AI 使用的人设卡。

【输出格式】严格按以下章节输出，每段文字独占一段。分节符不计入字数，仅填空项内容计入字数上限：
【AI人设】
[名字]……
[身份背景]……
[性格]……
[外观]……
[世界背景]……
[期望特质]……
【用户人设】
[名字]……
[身份]……
[性别]……
[年龄]……
[外观]……
【场景设置】
[当前场景]……
【记忆设置】
[需要记住的事]……

生成原则（按优先级执行）：
1. 忠实于用户意图——用户明确说过的设定一字不改；描述模糊或缺失的部分允许你合理发散补全，让角色有血有肉、自成一体，但发散必须贴合用户原意，不得违背用户明确给出的设定。
2. 每个字段都必须填充且内容充实——即使档位较低，每个字段也要有具体信息量；禁止空字段，禁止一句话敷衍。
3. 具体胜于抽象——用具体细节说话：口头禅、习惯性小动作、说话节奏、喜好与厌恶、藏在心里的矛盾、坚持的信念、害怕或渴望的东西。禁止只写"温柔""傲娇"这类空洞形容词而不展开。
4. 多样且不落俗套——根据用户描述量身定制，避免千人一面的模板；不要用雷同的开头句式，不要堆砌流行词或烂梗。
5. 密度优先——充分利用字数上限写出有信息量的内容，但拒绝凑字、空话、套话和重复；每个字都要有用，宁可精炼也不注水。
6. 世界类型——[世界背景]字段必须以恰好 4 个汉字的"世界类型"开头（如：都市世界/玄幻世界/末日世界/校园世界/修仙世界/科幻世界/古风世界/星际世界），紧接详细的世界背景描述。
7. 性别规则——【用户人设】的[性别]仅在用户描述中明确出现时填写；未明确时一律填"暂不设置"，绝不根据名字或语气猜测。
8. 格式严格——只输出上述章节与字段；不得新增任何章节或字段，缺失字段视为生成失败。

现在等待用户的人设描述。
```

### buildQuickSetupUserPrompt 增补

按档位追加"充实度要求"一行，让模型知道该档位的信息密度：

| 档位 | 追加内容 |
|------|----------|
| 粗略 | 本档位要求精炼：每个字段用 1-3 句具体信息填充完整，禁止敷衍。 |
| 具体 | 本档位要求充实：每个字段展开为一段有细节的完整描述。 |
| 全面 | 本档位要求详尽：每个字段充分展开，细节丰富、自成体系。 |

## 5. 快速设定描述持久化（可回看 / 重新生成）

**现状**：`description` 为 `QuickSetupPanel` 内 `rememberSaveable` 局部状态，面板关闭即丢。

**方案**：
- `Conversation` 新增字段 `quickSetupDraft: String = ""`（kotlinx `ignoreUnknownKeys`，旧数据兼容）。
- `ChatViewModel` 新增 `updateQuickSetupDraft(draft)`：500ms 防抖写入 `conversationRepository.updateConversation(conv.copy(quickSetupDraft = draft))`；生成/离开面板时立即 flush。
- `QuickSetupPanel` 新增 `initialDraft` 与 `onDraftChange` 参数；`description` 以 `initialDraft` 初始化，每次输入即回调。
- HamburgerMenu 传入 `conv.quickSetupDraft` / 回调 ViewModel。

**涉及文件**：`Models.kt`、`ChatViewModel.kt`、`QuickSetupPanel.kt`、`HamburgerMenu.kt`。

## 6+7. AI 对话纪律（认知 + 回复完整性）

**根因**：
- system 提示词没有任何说话人纪律；`continueGeneration` 以 assistant 结尾历史续写，模型自然回答自己上一句的提问（第 6 条）。
- 无"回复必须带台词"指令，人设精调/快速设定又侧重行为展开，模型容易只输出括号动作就停（第 7 条）。
- 切分器（MessageStreamCoordinator）本身不丢字，有测试覆盖；第 7 条属生成行为问题，非切分丢字。

**方案**：在 `buildSystemPrompt` 输出**最前面**（人设之前）插入「回复纪律」块。system 提示词本就随每次请求先行发送，满足"每次发送消息前"且位于头部保证注意力权重。

### 整合后的【回复纪律】（约 120-150 字）

```text
【回复纪律（最高优先级）】
1. 只以人设身份对用户说话：「用户」的话是用户说的，你自己说过的话是你自己的；不替用户回答，不把自己说过的话当成用户的话。
2. 用户点「继续说」时，接着你自己上一句的话继续叙述，不要回答自己上一句提出的问题。
3. 每次回复必须包含实际说出口的台词；括号内的动作、神态只是辅助，不能只写动作就结束；不提及自己是 AI 或模型（用户明确询问时除外）。
```

**无重复审计**：现有 system 提示词内容为人设字段 / 用户人设 / 场景 / 记忆 / 时间库密码，无任何回复纪律类内容，三句均为新增，不重复、不冗余。

**群聊适配**：`GROUP_RULES` 已有"只说你作为该角色会说的话，不要替别人发言"，只追加一句不重复的"每次发言必须包含至少一句实际台词；括号动作只是辅助，不能只发动作描写"。

**涉及文件**：`PromptBuilder.kt`（`buildSystemPrompt` / `GROUP_RULES`）。

## 8. 改写弹窗光标定位

**根因**：`RewriteBottomSheet.kt` 用 `TextFieldValue(initialText)`，Compose 默认 `selection = TextRange(0)`，光标落在开头。

**方案**：
```kotlin
var textFieldValue by rememberSaveable(stateSaver = TextFieldValue.Saver) {
    mutableStateOf(TextFieldValue(initialText, selection = TextRange(initialText.length)))
}
```
`TextField` 改用 `textFieldValue` / `onValueChange { textFieldValue = it }`；`canSave` 逻辑改用 `textFieldValue.text`。

## 9. 发送延迟防抖（编辑感知）

**根因**：`sendMessage` 中"先 delay(delayMs)，再 `while (输入框非空) delay(500)`"——退格清空输入框即视为停止输入，立即放行。

**方案**：
- `ChatViewModel` 新增 `private var lastInputEditAt = 0L`；`updateInputText` 在文本变化时记录 `System.currentTimeMillis()`。
- 延迟段改为轮询"输入框为空 **且** 距上次编辑 ≥ delayMs"才放行：

```kotlin
if (settings.sendDelayEnabled) {
    val delayMs = settings.sendDelaySeconds * 1000L
    while (true) {
        val now = System.currentTimeMillis()
        if (_inputBarText.value.isBlank() && now - lastInputEditAt >= delayMs) break
        kotlinx.coroutines.delay(250)
    }
}
```

- 发送本身会清空输入框（`trySend` → `onTextChange("")` → `lastInputEditAt` 刷新），故首轮延迟从发送时刻起算，与现状一致；退格/删除/继续输入都会重新计时。
- 将"是否放行"抽成纯函数 `shouldFireSendDelay(text: String, lastEditAt: Long, now: Long, delayMs: Long)` 以便单测。

## 10. 设置页输入法键盘遮挡

**根因**：`SettingsBottomSheet.kt`（80%）与 `ApiEditBottomSheet.kt`（85%）均为"根 Box `imePadding()` + 面板固定高度"，键盘弹起面板不收缩，LazyColumn 视口一部分藏在键盘后面，底部设置项被遮挡。

**方案**（两处同一改法）：
- 面板 Surface 由 `height(screenHeight * x)` 改为 `heightIn(max = screenHeight * x).fillMaxHeight()`：键盘收起时高度封顶 x；键盘弹起时收缩到键盘上方剩余空间，列表视口完整可见，焦点字段可正常滚入。
- 保留根 `imePadding()` 与 `navigationBarsPadding`。
- 实施时真机验证 TokenEditorPanel（含输入框）、DelaySettingsPanel（滑杆）、底部"关于"区在键盘弹起下均可正常滚动到可见。

## 11. 输入框行数（群聊 2 行，私聊按群聊整体高度）

**现状**：`ChatInputBar.kt` 固定 `maxLines = 4` / `heightIn(max = 140.dp)`，群私聊无差别。

**方案**：
- `ChatInputBar` 新增参数：`maxLines: Int = 4`、`maxFieldHeight: Dp? = null`（null 时维持现状）。
- 群聊：`maxLines = 2`。
- `ChatScreen` 用 `remember { mutableStateOf<Dp?>(null) }` 记录群聊输入栏整体高度（`Modifier.onSizeChanged`，含成员头像栏），私聊传入 `maxFieldHeight = 群聊整体高度 - 上下内边距(16.dp)`；未测量到时回退现状（4 行 / 140dp）。
- `ChatInputBar` 内按 `MaterialTheme.typography.bodyMedium.lineHeight` 换算 `maxLines = floor((maxFieldHeight - 内边距) / lineHeight)`，并 `heightIn(max = maxFieldHeight)`；行数随群聊高度自适应。

## 涉及文件汇总

| 文件 | 改动 |
|------|------|
| `domain/QuickSetupPrompt.kt` | 重写 system 提示词、`buildQuickSetupUserPrompt` 档位充实度 |
| `domain/PromptBuilder.kt` | `buildSystemPrompt` 头部插入【回复纪律】、`GROUP_RULES` 追加台词要求 |
| `data/model/Models.kt` | `Conversation.quickSetupDraft` |
| `ui/chat/ChatViewModel.kt` | `updateQuickSetupDraft`、`lastInputEditAt` + 延迟轮询改防抖、`shouldFireSendDelay` |
| `ui/chat/components/HamburgerMenu.kt` | 快速设定 onApply 退出 + draft 接线 |
| `ui/chat/components/panels/QuickSetupPanel.kt` | 必填校验、draft 参数 |
| `ui/chat/components/RewriteBottomSheet.kt` | 光标落末尾 |
| `ui/chat/components/ChatInputBar.kt` | 行数/高度参数 |
| `ui/chat/ChatScreen.kt` | 群聊高度测量、私聊行数接线 |
| `ui/settings/SettingsBottomSheet.kt` / `ui/components/ApiEditBottomSheet.kt` | 面板自适应高度 |

## 测试计划（TDD）

1. `PromptBuilderTest`：`buildSystemPrompt` 含【回复纪律】三句且各只出现一次；`GROUP_RULES` 含台词要求。
2. `MessageStreamCoordinatorTest`：连续流"（把脸埋在你胸口，声音闷闷的）我真的好想你。"切分为动作 + 台词两条消息，内容完整无丢失。
3. `QuickSetupPromptTest`（新增）：完整/缺失字段解析；档位字段清单；新 system 提示词含发散/密度原则。
4. 序列化回归：`Conversation` 新增字段带默认值，旧 JSON 可解码（沿用 GroupModelSerializationTest 风格）。
5. `shouldFireSendDelay` 纯函数单测：空输入 + 静默满 delayMs 放行；最近有编辑不放行；退格清空后需重新等待满 delayMs。

## 验收标准 / 效果预测

| # | 验收标准 |
|---|----------|
| 1 | 填入/覆盖确认后汉堡菜单关闭，直接回到会话页 |
| 2 | 预览弹窗存在空字段时无法填入，缺失字段标红并有提示 |
| 3+4 | 相同模糊描述多次生成结果差异明显、细节具体、无空字段；字数在档位预算内不注水 |
| 5 | 关闭面板再打开，描述文本仍在；可再次点击设定重新生成 |
| 6 | 继续说不再出现"自己提问自己回答"；普通对话不把 AI 自己的话当用户的话 |
| 7 | 回复不再出现"只有括号动作没有台词"的情况 |
| 8 | 打开改写光标位于文本末尾 |
| 9 | 延迟期间退格清空输入框不会立即触发加载，需静默满 delayMs 才加载；每轮均生效 |
| 10 | 设置页任意底部项在键盘弹起后可滚动到可见、可操作 |
| 11 | 群聊输入框打字 2 行；私聊输入框行数由群聊整体高度换算，随群聊高度自适应 |
