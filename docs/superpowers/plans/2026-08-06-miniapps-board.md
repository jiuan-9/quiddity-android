# 小应用框架 + 棋盘小应用 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: 使用 superpowers:executing-plans 按任务执行，每任务包含 TDD 步骤。

**Goal:** 在主页加入下拉进入的小应用中心（收藏/全部），并交付第一个可玩的小应用"棋盘"（五子棋/围棋，LLM/本地 AI 对弈、对局内聊天、结束后私聊记忆）。

**Architecture:** 模块化 MiniApp 接口 + 注册表 + 宿主路由；棋盘引擎/兜底 AI/LLM prompt 为纯 Kotlin 领域层；ViewModel 驱动对局状态机；复用现有 ConversationRepository/CharacterRepository/ChatApi。

**Tech Stack:** Kotlin 2.0.21 / Jetpack Compose (BOM 2024.10.01) / Material3 / Navigation Compose / kotlinx-serialization / OkHttp。

---

## Task 1: 棋盘领域层（TDD）

**Files:**
- Create: `app/src/main/kotlin/com/quiddity/app/domain/board/BoardGame.kt`
- Create: `app/src/test/kotlin/com/quiddity/app/domain/board/BoardGameTest.kt`

行为：
- `BoardGameType.GOMOKU(15)` / `GO(9)`
- `BoardState.applyMove`：非法/占用/禁着(围棋自杀)/ko(围棋全局指纹去重) 拒绝；提子返回被提数量；五子棋返回胜者。
- `BoardState.pass()`：围棋连停两次 → 终局；`score()` 简化数子计分（黑子+黑空 vs 白子+白空+7.5）。
- 步骤：先写测试（验证红），再实现（验证绿），提交。

## Task 2: 本地兜底 AI + LLM Prompt（TDD）

**Files:**
- Create: `domain/board/BoardBot.kt` + `domain/board/BoardLlmPrompt.kt`
- Create: `test/.../BoardBotTest.kt` + `test/.../BoardLlmPromptTest.kt`

行为：
- `BoardBot.nextMove(state, random)`：五子棋威胁评分（攻/守/中心），围棋提子→延气→随机合法；棋盘满返回 null。
- `BoardLlmPrompt.buildMoveSystemMessage` / `buildMoveUserMessage(state)` / `parseMoveReply(text)`：只接受 `MOVE(r,c)` / `PASS`；非法返回 null。
- `BoardLlmPrompt.buildChatSystemMessage(persona, state)` / `buildChatUserMessage(userText)`。

## Task 3: 小应用框架

**Files:**
- Create: `ui/miniapps/MiniApp.kt`（接口+注册表+宿主）
- Create: `data/local/MiniAppStore.kt`（收藏 JSON，仿 CharacterStore）
- Create: `ui/miniapps/MiniAppsCenterScreen.kt`（收藏/全部两分区、精致卡片）
- Modify: `di/ServiceLocator.kt`（注入 MiniAppStore）

## Task 4: 棋盘小应用（UI + ViewModel + 会话管理）

**Files:**
- Create: `ui/miniapps/board/BoardMiniApp.kt`（MiniApp 实现 + 页面状态机）
- Create: `ui/miniapps/board/BoardViewModel.kt`（对局状态机）
- Create: `ui/miniapps/board/BoardScreens.kt`（选棋种/选模式/邀请列表）
- Create: `ui/miniapps/board/BoardGameScreen.kt`（Canvas 棋盘 + 聊天 + 操作栏）
- Create: `ui/miniapps/board/BoardLlmClient.kt`（LLM 落子/聊天调用）
- Create: `data/repo/MiniAppSessionRepository.kt`（查找/创建角色会话、邀请气泡、记忆注入）

## Task 5: 导航 + 主页下拉

**Files:**
- Modify: `ui/navigation/QuiddityNavHost.kt`（新增 miniapps / miniapp/{appId} 路由，miniapps 自顶滑入）
- Modify: `ui/home/HomeScreen.kt`（NestedScrollConnection 顶部下拉 + 指示器 + 阈值触发）

## Task 6: 验证

- `gradlew.bat testDebugUnitTest`（全绿）
- `gradlew.bat assembleDebug`（编译通过）
- 修复所有编译/测试问题后提交。
