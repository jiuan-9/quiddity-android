# Quiddity-Android v1.0.3

- **versionCode**: 4
- **versionName**: 1.0.3
- **签名**: D:/Quiddity-Keys/android/release.keystore (alias: quiddity)
- **APK 大小**: 2,900,565 bytes (≈ 2.77 MB)
- **SHA256**: ba93aa3dfe4731e2dcae03c4a208ec6de9f86810955aa621ee565f21a8326814
- **Release URL**: https://github.com/jiuan-9/Quiddity-website/releases/tag/v1.0.3
- **APK 直链**: https://github.com/jiuan-9/Quiddity-website/releases/download/v1.0.3/quiddity-1.0.3.apk
- **构建时间**: 2026-07-31
- **发布状态**: 🔄 准备中
- **兼容**: v1.0.0 / v1.0.1 / v1.0.2 数据可保留，直接覆盖安装即可

## 更新（修复 + 优化）

### 修复

- **应用内「检查更新」下载链路**：重写 UpdateChecker，使用系统 DownloadManager + FileProvider，APK 下载完成后可直接在应用内唤起安装，无需跳浏览器
- **Android 7.0+ FileUriExposedException**：安装必须用 FileProvider 的 content:// URI（之前用 file:// 在 Android 7.0+ 会抛异常）
- **Android 13+ RECEIVER_NOT_EXPORTED**：DownloadManager.ACTION_DOWNLOAD_COMPLETE 广播必须用新签名注册（之前会抛 SecurityException）

### 优化

- **下载体验**：实时进度条 + 状态回调（pending / running / paused / successful / failed / canceled），下载失败可一键重试
- **版本解析**：raw.githubusercontent 兜底 → GitHub Releases API 自动取最新 APK 直链
- **URL 兜底**：官网首页 / 空 URL 自动回退到 GitHub Releases latest
- **「不再提醒此版本」持久化**：SharedPreferences 存储 dismiss 状态

## 兼容性

- v1.0.0 / v1.0.1 / v1.0.2 → v1.0.3：直接覆盖安装，数据完全保留
