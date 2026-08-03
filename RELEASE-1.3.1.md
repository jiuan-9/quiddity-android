# Quiddity-Android v1.3.1 发布说明

> 版本号递增：versionCode 9 → 10 | versionName 1.3.0 → 1.3.1

## 本版本内容

- 聊天消息时间移到气泡下方，界面更清爽
- 时间库改为单一入口：入口收拢，设置更集中
- 首次使用时间库需密码解锁（与本地数据加密策略一致）

## 验证

- `:app:testDebugUnitTest`：全部单元测试通过
- `:app:assembleRelease` 构建成功，APK 签名有效（v2 scheme）

## 构建产物

- **versionCode**: 10
- **versionName**: 1.3.1
- **APK 大小**: 2,991,797 bytes（≈ 2.85 MB）
- **SHA256**: C36C5A35F4A8BF4108D5C1466599AF7988444DE863DD542B31B6A14A52C8D4F6
- **签名**: D:/Quiddity-Keys/android/release.keystore (alias: quiddity)

## 兼容性

- 覆盖安装保留全部本地数据（conversations.json / messages_*.json 结构未变）
- 系统要求不变：Android 8.0（API 26）及以上
