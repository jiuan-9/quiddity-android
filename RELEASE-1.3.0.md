# Quiddity-Android v1.3.0 发布说明

> 版本号递增：versionCode 8 → 9 | versionName 1.2.0 → 1.3.0

## 本版本内容

本版本严格遵循 `docs/2.0.0-数据导出与接口规划.md` 与 `docs/压缩会话流程说明.md`，
为 2.0.0（群聊 + 角色库 + 记忆调用式）预留数据契约与接口；**群聊暂不做实体，仅加入接口**。

### 数据契约（schema v2 字段）

- `Conversation` 新增 `type`（SOLO/GROUP，默认 SOLO）、`memberConversationIds`、`characterId`、
  `memoryIndex`、`memoryStrategy`（CARRY / TOOL / null=跟随分级）、`groupMemory`
- `Message` 新增 `senderId`（私聊默认 null，兼容旧数据）
- 新增 `Character` 角色库主档（persona / userPersona / memory / aiAvatarUri）
- 新增 `ConversationBundle`（privateChats / groupChats 元素）、`ExportAssets` 资产节
  （AI 头像 key 从会话 id 改为角色 id）

### 数据层接口

- `ConversationStore.conversationsByType(type)` / `conversationsGroupedByType()`：私聊 / 群聊分节查询
- 新增 `CharacterStore` / `CharacterRepository`：`characters.json` 持久化 + list/get/save/delete + `resolveCharacter(id)`
- `ConversationRepository.importV2Snapshot(characters, conversations, messages, mode)`：
  替换 / 合并 / 仅导入角色库三种模式，写盘顺序 角色库 → 会话 → 消息
- 替换模式**先备份再替换**（数据文件改名 `.bak`，成功删备份、失败回滚），满足 3.4 原子性要求
- `DataPorter` 升级 v1/v2 双版本导出导入：
  - v2 导出：characters / privateChats / groupChats / assets 分类节 + 导出前引用校验（悬空引用拒绝导出）
  - v2 导入：同时支持 v1 与 v2；v1 文件自动「搬家」（内嵌 persona 抽成角色库记录，会话写回 characterId）
  - 新增 `ImportPlan`（解析结果 + 跳过清单 + 需重填密钥的模型配置）供 UI 展示
  - 群聊条目整体跳过并计入跳过清单（群聊实体未加入）
- `ChatStreamParser` 支持流式 `tool_calls` 增量解析（按 index 聚合函数名与参数分片）

### 领域层接口

- `MessageStreamCoordinator` 增加 `senderId: String? = null` 参数（向后兼容）
- `PromptBuilder` 新增：`buildGroupSystemPrompt` / `buildGroupTranscript` / `buildGroupDecisionPrompt` /
  `buildGroupMemorySummaryPrompt` / `buildReadMemoryTool` / `buildMemoryDrawerContent`
- `buildSystemPrompt` 支持记忆策略分支（随身带 / 小抄两种组装），默认行为不变
- 压缩提示词两段化（【摘要】 + 【索引】，6.5.2），压缩结果解析后写入 `compressedMemory` 与 `memoryIndex`
  （索引含程序补全的覆盖范围），清空聊天记录时同步重置
- `toApiMessages` 支持 senderId 标签（群聊转述可区分说话人）

### 群聊预留接口（仅声明，不实现实体）

- `ChatRepository.streamGroupMemberReply` / `decideGroupResponder` / `compressGroupMemory`（抛出 NotImplementedError）
- `ChatCompletionRequest` 增加 `tools` / `tool_choice`；`ChatMessage` 支持 `tool_calls` / `tool_call_id`
  （6.6.4 第二次请求形状，为 read_memory 记忆调用式预留）

### UI 调整

- 全量备份导出升级为 schema v2；导入弹窗增加「仅导入角色库」选项（3.1）
- 导入结束提示跳过项数量

## 验证

- `:app:testDebugUnitTest`：95 个单元测试全部通过（新增 tool_calls 聚合、两段式压缩解析、
  记忆策略分支、senderId 透传等测试）
- `:app:assembleRelease` 构建成功，APK 签名有效（v2 scheme）

## 构建产物

- **versionCode**: 9
- **versionName**: 1.3.0
- **APK 大小**: 2,991,797 bytes（≈ 2.85 MB）
- **SHA256**: CA5819CE28C2D2BFBEDDC53BE716F85C77B08E20B39B39313505D0833F046ED6
- **签名**: D:/Quiddity-Keys/android/release.keystore (alias: quiddity)

## 兼容性

- 覆盖安装保留全部本地数据（conversations.json / messages_*.json 结构未变，新增字段全带默认值）
- v1 备份文件（schemaVersion=1）仍可正常导入，导入时自动迁移为 v2 形态
