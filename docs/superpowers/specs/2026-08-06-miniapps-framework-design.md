# 小应用（Mini Apps）框架 + 棋盘小应用 设计文档

> 日期：2026-08-06
> 目标：在 Quiddity Android 中加入微信/QQ 小程序式的"小应用"入口，并落地第一个小应用"棋盘"（五子棋/围棋），验证可拓展性、流畅度与可行性。

## 1. 需求摘要

- 主页下拉进入"小应用"界面（微信式交互），内含两个分区：**收藏小应用**、**全部小应用**。
- 框架必须模块化：未来可以低成本加入更多简单/复杂小应用。
- 首个小应用"棋盘"：
  - 一级页面选择 五子棋 / 围棋；
  - 二级页面选择 邀请好友对战 / 与 AI 对战；
  - 邀请好友：展示用户现有角色，邀请后**先检测 API 连接**，成功则以 LLM 为对手，失败有本地兜底 AI；
  - 双方随机执黑/白；AI 执黑先行时自动落子；
  - 对局中可发送消息，LLM 需要回复；对手为本地电脑时不做聊天回复；
  - 对局决策由 LLM/本地 AI 完成；
  - 结束后，对应角色的私聊会话需要：一条"邀请了 AI 玩什么应用"的气泡 + 对局记忆（LLM 私聊可引用）。
- 整体 UI 拒绝廉价感/广告感。

## 2. 架构总览

```
HomeScreen（下拉手势）
   └─> MiniAppsCenter（收藏/全部）
          └─> MiniAppHost（按 appId 渲染小应用）
                 └─> BoardMiniApp（内部页面状态机：选棋种→选模式→邀请/开局→对局）
```

### 2.1 小应用框架（`ui/miniapps`）

- `MiniApp`：小应用统一接口，字段含 id / 名称 / 描述 / 图标 / 邀请气泡文案模板 / 对局记忆文案模板，方法为 `@Composable Content(host)`。
- `MiniAppRegistry`：全部小应用注册表，`byId()` 查询；新增小应用 = 新增一个实现并注册。
- `MiniAppHost`：小应用宿主上下文（退出、打开会话、提示等），小应用内部用自身状态机切页，不改 NavHost。
- `MiniAppStore`：`mini-apps.json` 持久化收藏列表（仿 `CharacterStore` 的 AtomicFile 方案）。

### 2.2 棋盘小应用（`ui/miniapps/board`）

**领域层（纯 Kotlin，可单测）**

- `BoardGame.kt`：`BoardGameType`（五子棋 15×15 / 围棋 9×9）、`Stone`、`Move`、`BoardState`（不可变，含提子/禁着/打劫/停一手/胜负判定/简化数子计分）。
- `BoardBot.kt`：本地兜底 AI（五子棋威胁评分；围棋提子/延气/随机）。
- `BoardLlmPrompt.kt`：LLM 落子/聊天 prompt 构建与结果解析（`MOVE(r,c)` / `PASS` 严格格式），可单测。
- `BoardLlmClient.kt`：薄封装，调用 `ChatApi.completeNonStreaming`。

**应用层**

- `BoardViewModel`：会话状态机（棋种、模式、对手、随机黑白、落子流转、对局内聊天、结束结算）。
- `BoardSessionManager`：邀请角色时查找/创建该角色的私聊会话、写入邀请气泡、结束时注入对局记忆（模块化文案来自 MiniApp 接口）。
- UI：选棋种、选模式、邀请列表、对局（Canvas 棋盘 + 聊天 + 操作栏）。

## 3. 关键规则与决策

1. **围棋规则简化**：9×9；提子、禁着点（自杀）、位置超 ko（棋盘全局面指纹去重）、停一手、双方连停或认输结束；结束采用简化数子计分（黑白子 + 各自围空，白贴 7.5 目近似）。真实围棋规则（劫争完整处理、贴目细则）留待后续优化，UI 标注"简化规则"。
2. **LLM 落子协议**：system 给出角色人设 + 规则 + 格式约束；输出严格 `MOVE(行,列)` 或 `PASS`。解析失败/非法 → 自动降级本地 AI，并在对局内提示一次。
3. **对局内聊天**：使用对手人设 + 当前棋局上下文，不读取私聊历史；本地电脑对手不回复。
4. **邀请气泡**：以 `Message(isNotice = true)` 写入角色私聊会话（居中小气泡，不参与 LLM 上下文）。
5. **对局记忆**：结束时向会话 `memory` 追加结构化区块 `[小应用·对局记忆]`（限制长度），此后 LLM 私聊通过既有 memory 注入机制可引用。
6. **下拉手势**：`NestedScrollConnection` 监听主页列表顶部下拉，指示器跟手，越过阈值即进入小应用中心；中心页从上向下滑入。
7. **API 检测**：邀请时用当前激活的 API 配置 `testConnection`；成功 → LLM 对手；失败 → 本地兜底 + 提示，不阻塞开始对局。

## 4. 数据与依赖

- `ServiceLocator` 新增 `MiniAppStore`。
- 复用：`ConversationRepository`（建会话/写消息/更新 memory）、`CharacterRepository`（角色列表）、`SettingsRepository`（API 配置）、`ChatApi` / `ApiCatalogManager`（LLM 调用与连接检测）。

## 5. 测试策略

- 领域层 TDD：五子棋胜负判定、围棋提子/禁着/ko/计分、Bot 合法性、LLM 回复解析。
- 构建验证：`gradlew testDebugUnitTest` + `gradlew assembleDebug`（再视情况 release）。

## 6. 非目标（YAGNI）

- 不做小应用市场/下载、不做真实联机对战、不做完整围棋规则、不做小应用内支付/广告。
