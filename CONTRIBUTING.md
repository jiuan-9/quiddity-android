# 贡献指南

感谢你有兴趣为 Quiddity Android 贡献代码！在提交 PR 之前，请阅读以下内容。

## 行为准则

- 保持友善、包容、专业
- 建设性反馈，不攻击个人
- 优先沟通，再争论

## 提交流程

### 1. Fork 与克隆

```bash
# 1. 在 GitHub 上 Fork 本仓库
# 2. 克隆你的 fork
git clone https://github.com/<your-username>/quiddity-android.git
cd quiddity-android

# 3. 添加 upstream
git remote add upstream https://github.com/jiuan-9/quiddity-android.git
```

### 2. 创建分支

```bash
git checkout -b feat/your-feature-name
# 或
git checkout -b fix/issue-number-description
```

**分支命名规范**：
- `feat/*` — 新功能
- `fix/*` — Bug 修复
- `refactor/*` — 重构
- `docs/*` — 文档
- `chore/*` — 杂项

### 3. 开发

- 遵循 [Kotlin 官方代码风格](https://kotlinlang.org/docs/coding-conventions.html)
- 保持 `main` 分支的代码风格一致
- 提交前确保 `./gradlew build` 与 `./gradlew test` 通过

### 4. 提交

```bash
git add .
git commit -m "feat(chat): 支持会话导入导出

- 将会话导出为 JSON 文件
- 支持从 JSON 文件导入会话
- 添加导入导出 UI 入口

Closes #123"
```

**Commit 消息规范**（参考 [Conventional Commits](https://www.conventionalcommits.org/)）：

```
<type>(<scope>): <subject>

<body>

<footer>
```

- `type`: feat / fix / refactor / docs / style / test / chore
- `scope`（可选）: 模块名，如 chat / settings / data / ui
- `subject`: 中文或英文，祈使句，≤ 50 字符
- `body`: 详细说明（可选）
- `footer`: 关联 Issue（`Closes #123`）

### 5. 推送与 PR

```bash
git push origin feat/your-feature-name
```

然后在 GitHub 上创建 Pull Request，目标分支为 `main`。

## 代码规范

### Kotlin

- 使用 `ktlint` 风格（`./gradlew ktlintCheck`）
- 优先使用 `val` 而非 `var`
- 优先使用不可变集合（`List` 而非 `MutableList`）
- 协程作用域使用 `viewModelScope` / `lifecycleScope` / 自定义 `ApplicationScope`
- ViewModel 不得持有 `Context` 引用

### Compose

- 状态提升：状态由 ViewModel 管理，UI 仅消费
- 单向数据流：事件从 UI 流向 ViewModel，状态从 ViewModel 流向 UI
- Composable 函数保持纯净，副作用放在 `LaunchedEffect` / `SideEffect`
- 使用 `Modifier` 链式调用，注意顺序

### 测试

- 单元测试覆盖核心业务逻辑（`ChatRepository` / `ApiCatalogManager` 等）
- UI 测试覆盖关键页面（`HomeScreenTest` / `ChatScreenTest` 等）
- 测试命名：`should_<expected>_when_<condition>`

## 安全

- **不要**提交 `keystore.properties`（已加入 `.gitignore`）
- **不要**提交任何 API Key、Token、密码
- 发现安全漏洞请私下联系：`qu9190agent@163.com`

## 发布流程

1. 维护者发起 `release/vX.Y.Z` 分支
2. 维护者更新 `app/build.gradle.kts` 中的 `versionCode` 与 `versionName`
3. 维护者创建 Git Tag：`git tag vX.Y.Z`
4. 维护者构建 Release APK 并签名
5. 维护者将 APK 上传至 [官网](https://quiddity-3by.pages.dev/) 下载页
6. 维护者在官网公告区发布更新公告

## 联系方式

- 邮箱：qu9190agent@163.com
- 反馈：[GitHub Issues](https://github.com/jiuan-9/quiddity-android/issues)

---

再次感谢你的贡献！
