# Quiddity Android

> Quiddity AI 多模型对话 Android 客户端 — 一个客户端，聚合所有主流大模型。
>
> 知所不尽，往复不止 — Know no bounds, repeat no end.

[![Release](https://img.shields.io/badge/release-v1.1.1-blue)](#下载)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green)](#系统要求)
[![License](https://img.shields.io/badge/license-MIT-blue)](./LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-purple)](#技术栈)

## 项目简介

Quiddity Android 是 Quiddity 移动端的独立产品（与 Quiddity-Chat、Quiddity-Agent 桌面端完全分离，三个产品数据互不互通）。本仓库为 Android 端的独立实现。

### 核心特性

- **11 家 AI 服务商**，60+ 模型可选（基础级 / 进阶级 / 完整级 / 视觉级）
- **模型分配方案**：按场景自动匹配最优模型（写作 / 编程 / 翻译 / 视觉…）
- **多轮对话 + 上下文记忆**：可配置上下文轮数（1-200）
- **会话压缩（记忆库）**：进阶级模型默认每 40 轮自动压缩一次，节省 token
- **角色卡 / System Prompt**：完全自定义 AI 身份与人设
- **Markdown 渲染 + 代码高亮**：内置语法高亮与数学公式
- **图像识别（Vision 模型）**：上传图片自动调用多模态模型
- **暗黑 / 浅色主题**：跟随系统或手动切换
- **离线草稿 / 消息搜索 / 会话导出 / 一键分享**
- **本地加密存储**：API Key 与对话记录使用 EncryptedFile 加密保存
- **继续说 / 延迟发送 / 重新生成 / 撤回消息** 等完备的发送控制

## 下载

前往官网 [https://quiddity-3by.pages.dev/](https://quiddity-3by.pages.dev/) 下载最新版本（v1.1.1，约 2.77 MB）。

或直接下载：[`quiddity-1.1.1.apk`](https://github.com/jiuan-9/Quiddity-website/releases/download/v1.1.1/quiddity-1.1.1.apk)

## 系统要求

- **最低 Android 版本**：Android 8.0（API Level 26）
- **目标 Android 版本**：Android 14（API Level 34）
- **架构**：arm64-v8a（推荐）/ armeabi-v7a / x86_64
- **存储**：约 50 MB
- **网络**：需要联网访问 AI API（除本地模型外）

## 技术栈

- **语言**：Kotlin 2.0.21
- **UI**：Jetpack Compose（BOM 2024.10.00，Material 3）
- **架构**：MVVM + Repository + Hilt 依赖注入
- **数据持久化**：
  - DataStore Preferences（设置项）
  - EncryptedFile（API Key、敏感数据）
  - Room / 自有 JSON 持久化（对话与消息）
- **网络**：OkHttp 4.12 + okhttp-sse（流式响应）
- **图片加载**：Coil 2.7
- **协程**：kotlinx-coroutines 1.9
- **序列化**：kotlinx-serialization-json
- **导航**：Navigation Compose 2.8
- **构建工具**：Gradle 8.9 + AGP 8.7 + KSP 2.0.21

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
│       │   │   ├── di/                  # Hilt 依赖注入模块
│       │   │   ├── domain/              # 业务逻辑（ApiCatalogManager 等）
│       │   │   ├── ui/                  # UI 层
│       │   │   │   ├── chat/            # 对话页（核心）
│       │   │   │   ├── components/      # 通用组件
│       │   │   │   ├── conversations/   # 会话列表
│       │   │   │   ├── home/            # 首页
│       │   │   │   ├── navigation/      # 导航图
│       │   │   │   ├── settings/        # 设置页
│       │   │   │   ├── start/           # 启动页
│       │   │   │   └── theme/           # 主题
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
├── docs/                                # 进阶文档
│   └── 压缩会话流程说明.md              # 进阶级模型压缩流程详解
└── README.md                            # 本文件
```

## 快速开始

### 环境要求

- **JDK 17**（建议 `D:\开发工具\jdk-17`）
- **Android SDK 34**（建议 `D:\开发工具\android-sdk`）
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
- **多模型支持**：通过 `ApiCatalogManager` 统一管理 60+ 模型

### 2. 会话压缩（记忆库）

详见 [`docs/压缩会话流程说明.md`](docs/压缩会话流程说明.md)

- **进阶级（ADVANCED）**：默认 40 轮触发一次
- **完整级（FULL）**：默认 80 轮触发一次
- **基础级（BASIC）**：默认 12 轮触发一次
- **触发条件**：用户发送消息后，由 `ChatViewModel.checkMemoryBankCompression()` 检测

### 3. 上下文裁剪

[`ChatRepository.takeLastRounds()`](app/src/main/kotlin/com/quiddity/app/data/repo/ChatRepository.kt) 与 `takeFromRound()` 实现了**按"轮"（以 USER 消息为锚点）裁剪**的算法，正确处理"继续说"与"延迟发送"导致的单轮多消息情况。

### 4. 模型分配方案

[`ApiCatalogManager`](app/src/main/kotlin/com/quiddity/app/domain/ApiCatalogManager.kt) 定义了三档模型分级：

| 档位 | 默认上下文 | 压缩频率 | 典型模型 |
|---|---|---|---|
| 基础级（BASIC） | 12 轮 | 每 12 轮 | GPT-3.5、Qwen Turbo、Doubao Lite |
| 进阶级（ADVANCED） | 40 轮 | 每 40 轮 | GPT-4、Qwen Max、Kimi、DeepSeek |
| 完整级（FULL） | 80 轮 | 每 80 轮 | Claude 4、Gemini 2.5 Pro |
| 视觉级（VISION） | 16 轮 | 每 16 轮 | GPT-4V、Gemini Vision、Qwen-VL |

## 数据存储

所有数据均存储在 Android 应用的私有目录中，**不上传任何用户数据**。

```
/data/data/com.quiddity.app/
├── files/
│   ├── api_key.enc              # API Key（EncryptedFile 加密）
│   ├── settings.json           # 应用设置（明文）
│   └── ...
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
- [ ] 1.2.0：语音输入 / TTS 输出
- [ ] 1.3.0：图片生成（多模态输出）
- [ ] 1.4.0：插件系统
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
