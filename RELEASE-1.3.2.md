# Quiddity-Android v1.3.2 发布说明

> 版本号递增：versionCode 10 → 11 | versionName 1.3.1 → 1.3.2

## 本版本内容

- 修复应用内更新下载到旧版本的问题：删除硬编码的 1.3.0 备用下载链接，
  改为通过 GitHub Releases API 动态解析最新版 APK，保证「检测到新版、下载的就是新版」
- 新增 3 个更新链路回归测试

## 验证

- `:app:testDebugUnitTest`：121 个单元测试全部通过（含新增 3 个更新解析测试）
- `:app:assembleRelease` 构建成功，APK 签名有效（v2 scheme）

## 构建产物

- **versionCode**: 11
- **versionName**: 1.3.2
- **APK 大小**: 2,991,801 bytes（≈ 2.85 MB）
- **SHA256**: BF7197D7CECC2E13AD79E63ED9C59D17F8EAC1DBBA5FCED622029B49402C9C81
- **签名**: D:/Quiddity-Keys/android/release.keystore (alias: quiddity)

## 兼容性

- 覆盖安装保留全部本地数据（conversations.json / messages_*.json 结构未变）
- 系统要求不变：Android 8.0（API 26）及以上
