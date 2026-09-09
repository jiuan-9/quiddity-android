# Quiddity-Android 1.6.2 发布说明

版本号递增：versionCode 17 → 18 | versionName 1.6.1 → 1.6.2

## 本版本内容

### 修复

- 修复 DeepSeek 思考模式下「群聊容易报 400，提示思考内容需要回传」的问题。

  根因：DeepSeek 官方约束——携带 tools 的思考模式请求，历史中所有 assistant
  消息必须在后续每一轮原样回传 reasoning_content（字段缺失即 400「思考内容
  需要回传」）。应用此前只在当轮工具回填时回传，跨轮历史从不携带；且
  DeepSeek V4 思考模式默认开启，历史装配也没有回传字段。

  受影响路径（全部修复）：
  - 群聊（进阶/完整级成员携带 search_chat 工具，几乎每轮触发）；
  - Agent 模式（永远携带工具，第二轮起必现）；
  - 私聊「工具检索」记忆策略（携带 read_memory / search_chat）；
  - DeepSeek 官方联网搜索（Responses API 同样要求回传 reasoning）。

  修复方案（思考原文持久化 + 历史原样回传）：
  - Message 新增 reasoningContent 字段：流式结束后把本轮 reasoning_content
    固化到首条正式回复消息（不展示、不压缩、不导出，旧数据自动兼容）；
  - 携带工具的请求装配历史时，assistant 消息挂载 reasoning_content：
    请求方自己的发言回传落库原文，未知思考/群聊里其他成员的发言按空串
    占位（官方校验字段存在性，空串可通过）；
  - Responses API 路径在 assistant 消息前挂 reasoning item 回传。

## 构建产物

- **versionCode**: 18
- **versionName**: 1.6.2（调试包 1.6.2-debug）
- 调试：`app\build\outputs\apk\debug\app-debug.apk`
- 发布：`app\build\outputs\apk\release\app-release.apk`
