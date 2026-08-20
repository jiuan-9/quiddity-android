# Quiddity Android

> Quiddity AI 多模型对话 Android 客户端 — 一个客户端，聚合所有主流大模型。
>
> 知所不尽，往复不止 — Know no bounds, repeat no end.

[![Release](https://img.shields.io/badge/release-v1.6.0-blue)](#下载)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green)](#系统要求)
[![License](https://img.shields.io/badge/license-MIT-blue)](./LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple)](#技术栈)

## 项目简介

Quiddity Android 是 Quiddity 移动端的独立产品（与 Quiddity-Chat、Quiddity-Agent 桌面端完全分离，三个产品数据互不互通）。本仓库为 Android 端的独立实现。

### 核心特性

- **9 家内置 AI 服务商 + 自定义**，42 个内置模型（基础级 / 进阶级 / 完整级）
- **独立视觉 OCR 名册**：识图不走聊天模型档位，qwen-vl / GLM-4V / 豆包视觉 / DeepSeek-OCR 等单独配置
- **模型分配方案**：按场景自动匹配最优模型（写作 / 编程 / 翻译 / 视觉…）
- **多轮对话 + 上下文记忆**：可配置上下文轮数（1-200）
- **会话压缩（记忆库）**：进阶级模型默认每 20 轮自动压缩一次，节省 token
- **角色卡 / System Prompt**：完全自定义 AI 身份与人设
- **Markdown 渲染 + 代码高亮**：内置语法高亮与数学公式
- **图像识别（Vision 模型）**：上传图片自动调用多模态模型，纯文本模型可开启 OCR 兜底
- **AI 回复悬浮窗**：应用切后台时气泡展示回复进度，支持快捷回复与拖动收起
- **暗黑 / 浅色主题**：跟随系统或手动切换
- **离线草稿 / 消息搜索 / 会话导出 / 一键分享**
- **本地存储**：API Key 使用 AES-GCM（Android Keystore）加密保存；对话记录仅存本地 JSON，不上传
- **继续说 / 延迟发送 / 重新生成 / 撤回消息** 等完备的发送控制

## 下载

前往官网 [https://quiddity-3by.pages.dev/](https://quiddity-3by.pages.dev/) 下载最新版本。

最新 APK 以官网下载页为准（当前版本 1.6.0）。

## 系统要求

- **最低 Android 版本**：Android 8.0（API Level 26）
- **目标 Android 版本**：Android 14（API Level 34）
- **架构**：arm64-v8a（推荐）/ armeabi-v7a / x86_64
- **存储**：约 50 MB
- **网络**：需要联网访问 AI API（除本地模型外）

## 技术栈

- **语言**：Kotlin 2.0.21
- **UI**：Jetpack Compose（BOM 2024.10.01，Material 3）
- **架构**：MVVM + Repository + ServiceLocator 手动依赖注入
- **数据持久化**：
  - DataStore Preferences（设置项）
  - AES-256-GCM（Android Keystore 密钥，API Key 加密）
  - 自有 JSON 持久化（对话与消息；不依赖 Room）
- **网络**：OkHttp 4.12 + okhttp-sse（流式响应）
- **图片加载**：Coil 2.7
- **协程**：kotlinx-coroutines 1.9
- **序列化**：kotlinx-serialization-json
- **导航**：Navigation Compose 2.8
- **构建工具**：Gradle 8.9 + AGP 8.6 + KSP 2.0.21

## 项目结构

```
Quiddity-android/
├── app/                                 # 应用模块
│   ├── build.gradle.kts                 # 模块构建脚本
│   ├── proguard-rules.pro               # R8/ProGuard 规则
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml      # 应用清单
│       │   ├── kotlin/com/quiddity/app/ # Kotlin 源代码
│       │   │   ├── data/                # 数据层
│       │   │   │   ├── local/           # 本地持久化（DataStore、加密文件）
│       │   │   │   ├── model/           # 数据模型（Conversation、Message…）
│       │   │   │   ├── remote/          # 网络层（API、SSE）
│       │   │   │   └── repo/            # 仓储层（ChatRepository 等）
│       │   │   ├── di/                  # ServiceLocator 手动依赖注入
│       │   │   ├── domain/              # 业务逻辑（ApiCatalogManager 等）
│       │   │   ├── ui/                  # UI 层
│       │   │   │   ├── chat/            # 对话页（核心）
│       │   │   │   ├── components/      # 通用组件
│       │   │   │   ├── conversations/   # 会话列表
│       │   │   │   ├── home/            # 首页
│       │   │   │   ├── navigation/      # 导航图
│       │   │   │   ├── settings/        # 设置页
│       │   │   │   ├── agent/           # Agent 模式（工具执行、角色选择）
│       │   │   │   ├── miniapps/        # 小应用（间谍游戏 / 拆弹 / 棋盘等）
│       │   │   │   ├── start/           # 启动页
│       │   │   │   └── theme/           # 主题
│       │   │   ├── active/              # 无障碍读屏 / 主动消息 / 通知桥
│       │   │   └── util/                # 工具类（QuiddityConstants 等）
│       │   └── res/                     # 资源文件
│       │       ├── drawable/            # 矢量图
│       │       ├── mipmap-*/            # 启动图标
│       │       ├── values/              # 字符串、颜色、主题
│       │       └── ...
│       └── test/                        # 单元测试
├── build.gradle.kts                     # 顶层构建脚本
├── settings.gradle.kts                  # Gradle 设置
├── gradle.properties                    # Gradle 配置
├── gradle/
│   ├── libs.versions.toml               # 版本目录（version catalog）
│   └── wrapper/                         # Gradle wrapper
├── gradlew / gradlew.bat                # Gradle wrapper 脚本
├── keystore.properties.example          # 签名配置模板（不含密码）
└── README.md                            # 本文件
```

## 快速开始

### 环境要求

- **JDK 17**（`gradle.properties` 已预设 `org.gradle.java.home=D:\jdk-17`）
- **Android SDK 34**（`local.properties` 指向 `D:\android-sdk`）
- **Gradle 8.9**（通过 wrapper 自动下载）
- **Kotlin 2.0.21**
- 系统已在 `gradle.properties` 中预设 `org.gradle.java.home`

### 克隆与构建

```bash
git clone https://github.com/jiuan-9/quiddity-android.git
cd quiddity-android

# 1. 准备签名（可选：debug 构建不需要）
cp keystore.properties.example keystore.properties
# 编辑 keystore.properties 填入真实签名信息

# 2. 使用项目自带的 Gradle wrapper 构建
# Windows
.\gradlew.bat assembleRelease
# macOS / Linux
./gradlew assembleRelease

# 构建产物：app/build/outputs/apk/release/app-release.apk
```

### 开发与调试

```bash
# 编译 debug 版本
./gradlew assembleDebug

# 编译并安装到当前连接的设备
./gradlew installDebug

# 单元测试
./gradlew test

# Lint 检查
./gradlew lint
```

### 在 Android Studio 中打开

1. 打开 Android Studio（Hedgehog 或更新）
2. `File` → `Open` → 选择 `Quiddity-android` 目录
3. 等待 Gradle Sync 完成
4. 选择 `app` Run Configuration，点击 ▶ 运行

## 配置文件

### `keystore.properties`（不提交）

签名配置，结构如下：

```properties
storeFile=D:/Quiddity-Keys/android/release.keystore
storePassword=xxx
keyAlias=quiddity
keyPassword=xxx
```

可参考 [`keystore.properties.example`](./keystore.properties.example)。

### `gradle.properties`

- `org.gradle.jvmargs`：JVM 堆大小（默认 1024MB）
- `org.gradle.java.home`：JDK 17 安装路径
- `android.useAndroidX=true`
- `android.nonTransitiveRClass=true`

### `gradle/libs.versions.toml`

统一管理所有依赖版本（Version Catalog），新增依赖请修改此文件。

## 核心模块说明

### 1. AI 调用与流式响应

- **入口**：[`ChatRepository.streamAssistantReply`](app/src/main/kotlin/com/quiddity/app/data/repo/ChatRepository.kt)
- **网络**：[`data/remote/`](app/src/main/kotlin/com/quiddity/app/data/remote/)（OkHttp + okhttp-sse）
- **协议**：兼容 OpenAI Chat Completions API 规范
- **流式**：通过 SSE（Server-Sent Events）实时接收增量内容
- **多模型支持**：通过 `ApiCatalogManager` 统一管理 42 个内置模型与自定义条目

### 2. 会话压缩（记忆库）

- **进阶级（ADVANCED）**：默认 20 轮触发一次
- **完整级（FULL）**：默认 40 轮触发一次
- **基础级（BASIC）**：默认 6 轮触发一次
- **触发条件**：由 [`CompressionStateMachine`](app/src/main/kotlin/com/quiddity/app/domain/CompressionStateMachine.kt) 判定（启用记忆库且距上次压缩达到阈值），流结束后在 `ChatViewModel` 流式管线中触发

### 3. 上下文裁剪

[`ChatContextTrimmer`](app/src/main/kotlin/com/quiddity/app/domain/ChatContextTrimmer.kt) 实现了**按"轮"（以 USER 消息为锚点）裁剪**的算法（`takeLastRounds` / `takeFromRound`），正确处理"继续说"与"延迟发送"导致的单轮多消息情况；工具轮回传截断由 [`ChatToolRoundTrimmer`](app/src/main/kotlin/com/quiddity/app/data/repo/ChatToolRoundTrimmer.kt) 承担。

### 4. 模型分配方案

[`ApiCatalogManager`](app/src/main/kotlin/com/quiddity/app/domain/ApiCatalogManager.kt) 定义了三档模型分级：

| 档位 | 默认上下文 | 压缩频率 | 典型模型 |
|---|---|---|---|
| 基础级（BASIC） | 6 轮 | 每 6 轮 | kimi-k2.7-code-highspeed、spark-x、glm-4-flash 等 |
| 进阶级（ADVANCED） | 20 轮 | 每 20 轮 | glm-5.1、kimi-k2.6、spark-x2、MiniMax-M2.5 等 |
| 完整级（FULL） | 40 轮 | 每 40 轮 | deepseek-v4-pro、kimi-k3、glm-5.3、hy3、ernie-5.1 等 |

图片识图不占用模型档位：发送图片时当前对话模型自带视觉则直接识图，否则走独立「视觉 OCR」名册兜底（qwen-vl / GLM-4V / 豆包视觉 / DeepSeek-OCR 等）。

### 5. 小应用框架

主页下拉进入"小应用中心"（收藏/全部），小应用通过模块化 `MiniApp` 接口接入，中心页、路由、收藏与邀请气泡自动生效。

**新增一个小应用（一行命令）：**

```
.\new-miniapp.ps1 -Id dice -Name "骰子" -Description "随机掷骰子小游戏"
```

脚手架会生成可编译占位实现并自动注册到 `MiniAppRegistry`，随后把占位页替换成真实业务即可；复杂小应用参考棋盘小应用分层（`domain/` 纯逻辑 + `ViewModel` 状态机 + `MiniAppInviteManager` 复用邀请流程）。详见 [`new-miniapp.ps1`](new-miniapp.ps1)。

### 6. Agent 模式

Agent 会话支持工具调用闭环：`AgentToolRegistry` 注册 56 个工具（应用/文件/系统/交互四类），`AgentExecutors` 提供读取类执行器（Shizuku 写入类需授权），`AgentWorkflowController` 负责多轮任务编排与重试；`AgentChatScreen` 提供独立聊天界面（工具痕迹穿插正文、角色库选择、危险操作确认与撤回）。

### 7. 无障碍读屏与主动消息

`active/` 模块承载：`ScreenReaderService`（无障碍读屏，供 Agent 感知屏幕与模拟操作）、`ActiveMessageService` / `TimeLibraryRepository`（按时间库定时主动发消息）、`OperationNotifyController` 与 `NotificationBridge`（行动通知弹窗与通知回复）。

## 数据存储

所有数据均存储在 Android 应用的私有目录中，**不上传任何用户数据**。

```
/data/data/com.quiddity.app/
├── files/quiddity-data/
│   ├── conversations.json      # 会话列表
│   ├── messages_<会话id>.json  # 各会话消息
│   └── settings.json           # 应用设置（明文）
├── datastore/
│   └── settings.preferences_pb # DataStore Preferences
└── ...
```

## 安全说明

- API Key 使用 **AES256-GCM** 加密保存
- 加密密钥由 Android Keystore 系统管理
- 所有网络通信使用 HTTPS
- 不集成任何数据分析 SDK
- 不收集任何用户行为数据

详见隐私声明（官网 `/privacy` 页面）。

## 路线图

- [x] 1.0.0：核心对话、多模型、压缩、记忆
- [x] 1.0.1：版本号递增（versionCode 1 → 2），重新签名发布
- [x] 1.0.2：版本号递增（versionCode 2 → 3），重新签名发布
- [x] 1.0.3：版本号递增（versionCode 3 → 4），重写 UpdateChecker（DownloadManager + FileProvider），修复应用内更新下载
- [x] 1.1.0：联网搜索（RAG），版本号递增（versionCode 4 → 5），AI 可实时联网检索 + 来源列表 + 手动/自动模式 + 搜索范围控制 + 缓存去重
- [x] 1.1.1：修复部分手机检测更新失败 / 启动下载失败，版本号递增（versionCode 5 → 6），UpdateChecker 多源 fallback + installApk 改用 FileProvider + downloadApk 路径修正
- [x] 1.2.0：主动消息（时间库），AI 每天定时主动发消息，版本号递增（versionCode 7 → 8）
- [x] 1.3.0：数据导出 schema v2 接口预留（角色库 / 群聊接口 / 记忆调用式字段与提示词），版本号递增（versionCode 8 → 9）
- [x] 1.4.0：小应用框架上线（骰子 / 间谍游戏 / 拆弹 / 棋盘），版本号递增（versionCode 11 → 12）
- [x] 1.5.0：会话多选 / 导出长图 / 发送延迟 / 群聊点名回复与记忆压缩，版本号递增（versionCode 12 → 13）
- [x] 1.5.x：联网搜索细化与稳定性修复（versionCode 13 → 14）
- [x] 1.6.0：Agent 模式（工具调用 / 无障碍读屏 / 主动消息 / 56 个工具），版本号递增（versionCode 14 → 16，含修复版）
- [ ] 1.7.0：插件系统
- [ ] 2.0.0：端侧模型（llama.cpp / MediaPipe）

## 相关仓库

| 仓库 | 描述 |
|---|---|
| [Quiddity-website](https://github.com/jiuan-9/Quiddity-website) | 官方网站（React + Vite） |
| [Quiddity-Chat-Windows](https://github.com/jiuan-9/Quiddity-Chat-Windows) | Quiddity-Chat 桌面端安装包（Electron，源码暂未公开） |
| [Quiddity-Agent](https://github.com/jiuan-9/Quiddity-Agent) | Quiddity-Agent 桌面端（Electron，源码暂未公开） |

## 贡献

欢迎提交 Issue 与 Pull Request。提交代码前请阅读 [CONTRIBUTING.md](./CONTRIBUTING.md)。

## 许可证

本项目基于 **MIT 协议**开源，详见 [LICENSE](./LICENSE)。

## 致谢

- Jetpack Compose 团队
- OkHttp 团队
- 所有为开源 AI 生态贡献的开发者

---

知所不尽，往复不止 — Know no bounds, repeat no end.
