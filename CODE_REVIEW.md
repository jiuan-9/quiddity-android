# Quiddity-Chat 与 Quiddity-Android 源代码审查报告

> 审查原则：严格依据实际代码内容，不做主观猜测。
> 审查方式：人工读取关键文件 + 两个独立后台代理并行审查 + 编译验证。
> 审查范围：两个项目的关键功能文件。

---

## 一、Quiddity-Chat（电脑版）审查结果

### 1.1 已确认实际具备的功能

| 模块 | 实际功能 |
|---|---|
| 核心窗口 | 单实例锁、无边框窗口、自定义标题栏（设置/主题/最小化/最大化/关闭）、CSP |
| 数据目录 | 便携模式回退、旧 C 盘数据迁移、旧 config.json 迁移、损坏文件备份 |
| 侧边栏 | 会话列表、搜索框、新建会话、折叠按钮、帮助按钮、快捷键提示 |
| 主题 | 深色/浅色切换、三波纹动画、首屏无闪烁 |
| 输入栏 | 图片按钮、联网搜索按钮、深度思考按钮、多行文本框、发送/停止按钮 |
| 设置面板 | 全局设置对话框、对话设置面板（右侧滑入）、分模块保存 |
| AI 设定字段 | AI 名字、核心参考、人设、性格、外观、内部指令（共 6 项） |
| 用户设定字段 | 名字、身份、性别、年龄、外观（共 5 项） |
| API 名册 | 多账户管理、测试连接、下拉选用、加密保存 |
| 搜索引擎 | DuckDuckGo、SearXNG、Bing（三引擎） |
| 深度思考 | 低/中/高三档、DeepSeek/智谱适配、提示词降级 |
| 会话 | 新建、切换、删除、重命名、置顶、搜索、右键菜单 |
| 图片 | 本地选择/粘贴/拖拽、最多 10 张、单张 10MB、客户端压缩、放大查看 |
| 导出导入 | 单会话 Markdown 导出/导入、全部数据 JSON 导出/导入、人设模板导出/导入 |
| 统计 | 每日 rounds/tokens、30 天双柱状图、当前会话统计 |
| 安全 | `safeStorage` 加密、CSP、进程隔离、加密失败拒绝落盘 |
| 更新 | 自动检测 GitHub 版本、30 秒气泡通知、忽略版本 |
| 快捷键 | Enter/Shift+Enter/Esc/Ctrl+Z/N/R/G（共 7 项） |
| 代码块 | 自研语法高亮、品牌头部、复制按钮 |
| 打赏 | 微信/支付宝二维码、放大预览 |
| 健康检查 | 独立脚本（部分检查项与当前目录结构不匹配） |

### 1.2 发现的问题

| # | 问题 | 位置 | 原因 |
|---|---|---|---|
| 1 | 图片删除按钮 `data-idx` 与读取的 `dataset.index` 不匹配 | `src/renderer/chat.js:681` 与 `:163` | 点击删除会误删第一张图而非目标图 |
| 2 | `ai-loading` 元素未被 `dom` 对象引用，精调动画不显示 | `src/renderer/chat.js:68-100`、`src/renderer/settings.js:1367` | `dom.aiLoading` 为 `undefined`，loading 动画和按钮禁用失效 |
| 3 | 切换会话会清空未保存的 API 设置 | `src/renderer/chat.js:183-193` | `PER_CONV_KEYS` 包含 apiKey/apiUrl/apiModel，空值时重置为 `''` |
| 4 | 工具类使用了不存在的 `chat-messages` ID | `src/renderer/tool-system.js:38`、`:180` | 实际 ID 是 `messages-container`，工具气泡是死代码 |
| 5 | 导出完整数据时 `state.stats` 可能为 `undefined` | `src/renderer/settings.js:1241`、`src/renderer/chat.js:52-65` | 未打开过全局设置则 stats 未初始化 |
| 6 | 清空所有设置丢失自定义模板/导出目录 | `src/renderer/settings.js:1610-1613` | 只保留 darkMode/activeConversationId/apiCatalog |
| 7 | 人设翻译缓存路径与主程序数据目录不一致 | `src/main/persona-translator.js:31-34` | 便携模式下缓存会读写错误目录 |
| 8 | 健康检查脚本引用不存在的 agent/tools 目录 | `health-check.js:84-85`、`:181-263` | 当前项目无这些目录，检查必然失败 |
| 9 | `oldHistory._find` 检查恒为真 | `src/main/main.js:200-201` | 数组无 `_find` 属性，无法防止重复迁移 |
| 10 | `write-file` IPC 存在路径穿越风险 | `src/main/main.js:409-413` | `filename` 未校验，可写入 `dataDir` 之外 |
| 11 | DeepSeek 关闭深度思考时仍发送 `reasoning_effort: 'high'` | `src/renderer/chat.js:1082-1090` | 非深度思考模式下仍设置推理强度 |
| 12 | 语义化版本预发布标签比较不准确 | `src/renderer/update-check.js:43` | `beta.10` 会被认为小于 `beta.2` |
| 13 | 语法高亮数字正则不完整 | `src/main/syntax-highlight.js:137` | 无法匹配 `.5`、`1e10`、`0xFF` 等 |
| 14 | `onWinStateChanged` 暴露但未被使用 | `src/preload/preload.js:37-41` | 最大化按钮图标不会随窗口状态更新 |
| 15 | 窗口大小/位置未持久化 | `src/main/main.js:342-344`、`:424` | `createWindow` 无 `savedBounds` 参数 |
| 16 | 导入 Markdown 时间戳解析不完整 | `src/renderer/settings.js:1952-1954` | 只取小时分钟，且强制使用当天日期 |
| 17 | 文件读写无锁机制 | `src/main/main.js:125` | 注释明确写"无缓存、无锁" |
| 18 | 会话 ID 使用时间戳 | `src/renderer/chat.js:206-214` | 高频创建可能 ID 重复 |
| 19 | `testApiConnection` AbortController IIFE 可读性差 | `src/renderer/settings.js:1182` | 无法主动取消，timeout 仍会在后台触发 |
| 20 | 模板迁移表为空 | `src/renderer/settings.js:24-26` | `TEMPLATE_MIGRATIONS` 无实际迁移函数 |

---

## 二、Quiddity-Android（手机版）审查结果

### 2.1 编译验证

- 执行 `./gradlew.bat clean compileDebugKotlin` 成功（BUILD SUCCESSFUL）。
- 所有指定文件均能成功编译，无语法错误或未解析符号。

### 2.2 已确认实际具备的功能

| 模块 | 实际功能 |
|---|---|
| 核心窗口 | 单 Activity、Splash 启动屏、Edge-to-Edge、主题手动切换、状态栏图标颜色适配 |
| 导航 | Home → Chat 页面跳转（`QuiddityNavHost`） |
| 主题 | 默认深色、深色/浅色切换、括号内容灰化 |
| 设置 | 底部抽屉（80% 高度）、分区显示、用户头像、Token 设置、记忆设置、多行自动切分、回车发送 |
| 数据 | 全量导出/导入、版本检查（硬编码 v1.0.0）、文档抽屉、打赏、客服 QQ 复制 |
| 聊天 | 消息列表、输入栏、流式输出、typing 光标、思考气泡、重说/继续说、用户消息撤回、括号灰化 |
| 会话 | 主页列表、搜索、长按多选、批量删除、AI 头像/名字、最后消息预览、时间戳 |
| 图片 | 头像裁剪器（1:1、512x512 JPEG）、会话壁纸、暗化调节、打赏二维码保存 |
| 人设 | AI 人设 7 字段、用户人设 5 字段、场景、记忆、人设精调编译、人设卡导入/导出 |
| 导入导出 | 人设卡 JSON、单条/全量对话 JSON、壁纸 Base64 嵌入、导入恢复 |
| 模型 | 12 个服务商（11 内置+custom）、54 个模型、三级分级（FULL/ADVANCED/BASIC） |
| 安全 | API Key 加密、崩溃日志记录 |

### 2.3 发现的问题

| # | 问题 | 位置 | 原因 |
|---|---|---|---|
| 1 | Android 6~9 保存二维码缺少运行时权限 | `ui/settings/components/DonateScreen.kt:251-287` | 使用外部存储但未申请 `WRITE_EXTERNAL_STORAGE` |
| 2 | `Conversation.wallpaperUri` 注释与实现不一致 | `data/model/Models.kt:94-98` | 注释说存 SAF content URI，实际存 `file://` URI |
| 3 | 清除壁纸后未重置持久化的暗化值 | `ui/chat/components/panels/WallpaperPanel.kt:271-289` | 只更新本地状态，未调用 `onDarkenChanged` |
| 4 | API Key 明文通过 `rememberSaveable` 保存到 Bundle | `ui/components/ApiEditForm.kt:101`、`ui/settings/components/ApiCatalogEditor.kt:91-110` | 进程回收时 Bundle 可能落盘，存在泄露风险 |
| 5 | 文本选择与点击手势冲突 | `ui/chat/components/MessageBubble.kt:379-384` | `clickable` 消费事件，干扰长按选择 |
| 6 | 汉堡菜单子面板导航状态配置变更后丢失 | `ui/chat/components/HamburgerMenu.kt:112` | `currentPanel` 使用 `remember` 而非 `rememberSaveable` |
| 7 | 对话页汉堡菜单开关状态配置变更后丢失 | `ui/chat/ChatScreen.kt:122` | `showHamburger` 使用 `remember` |
| 8 | 主页删除确认状态配置变更后丢失 | `ui/home/HomeScreen.kt:128` | `pendingDeleteIds` 使用 `remember` |
| 9 | 会话列表 `_conversations` 更新存在竞态 | `data/local/ConversationStore.kt:280-329`、`:344-363` | `appendMessage` 与 `replaceMessages` 并发可能覆盖 |
| 10 | 损坏的 JSON 被静默降级为空列表 | `data/local/ConversationStore.kt:144-161`、`:201-217` | 文件损坏直接返回空列表，用户感知为记录消失 |
| 11 | 键盘高度变化时高频启动滚动协程 | `ui/chat/ChatScreen.kt:144-152` | `LaunchedEffect(imeBottom)` 每帧都重启，列表可能跳动 |
| 12 | 流式消息每次更新都同步写盘 | `data/local/ConversationStore.kt:280-329` | 每个 streaming delta 都重写消息文件和会话文件 |
| 13 | 头像图标分支逻辑冗余 | `ui/chat/components/MessageBubble.kt:119` | `if/else` 两个分支结果相同 |
| 14 | `var stream` 未重新赋值却使用 `var` | `data/local/ConversationStore.kt:172`、`:226` | 应改为 `val` |
| 15 | 字符串字面量末尾含多余空格 | `ui/settings/components/ApiCatalogEditor.kt:309` | 代码整洁性问题 |
| 16 | 会话卡片中对非空字段使用不必要的安全调用 | `ui/home/HomeScreen.kt:637`、`:641` | `conversation.persona` 非空，安全调用多余 |
| 17 | `ApiCatalogEntry` 数据类缺少 `avatarUri` 字段 | `data/model/Models.kt:59-66` | 对比文档声称有 API 条目头像，但数据模型未包含 |
| 18 | `AppSettings` 字段少于功能实际所需 | `data/model/Models.kt:143-163` | 很多设置项（temperature/maxTokens/topP 等）未在 `AppSettings` 中体现，可能分散在 `Conversation` 或其他地方 |
| 19 | `Conversation.compileEnabled` 默认 `false` | `data/model/Models.kt:92` | 与电脑版默认 `true` 不一致 |
| 20 | 自定义服务商强制完整级 | `domain/ApiCatalogManager.kt:148` | `providerId == "custom"` 直接返回 `FULL`，未让用户自选 |

---

## 三、两端功能对齐情况

| 维度 | 对齐状态 | 说明 |
|---|---|---|
| 服务商 ID 与默认 URL | 基本对齐 | Android 的 `providers` 明确说明与 Chat 的 `AI_PROVIDERS` 对齐 |
| 模型 ID | 部分不同 | Android 对部分模型做了更严格的筛选（百度/讯飞/MiniMax/腾讯/Kimi/智谱） |
| 人设字段 | Android 多一个 | 手机版多了 `worldBackground` |
| 数据导出格式 | 不互通 | Chat 导出 settings/conversations/stats；Android 导出 settings/conversations/messages/wallpapers |
| 主题默认值 | 不同 | Chat 默认浅色，Android 默认深色 |
| 编译默认开关 | 不同 | Chat 默认开启，Android 默认关闭 |
| API 条目头像 | 不一致 | Android 文档/功能声称支持，但 `ApiCatalogEntry` 数据类无该字段 |

---

## 四、审查结论

### Quiddity-Chat
- 功能完整，1.1.0 已稳定。
- 主要风险：DOM/ID 不匹配导致部分功能失效、数据一致性缺陷、死代码/结构不一致、文件读写无锁。
- 代码风格：传统 Electron 主/渲染进程分离，全局状态管理。

### Quiddity-Android
- 架构更清晰，数据模型集中，编译通过。
- 主要风险：配置变更状态丢失、敏感信息落盘风险、并发竞态、JSON 损坏静默降级、权限处理不完整。
- 部分功能描述与数据模型不一致（如 API 条目头像），需要进一步核对实现。
