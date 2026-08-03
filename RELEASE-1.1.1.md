# Quiddity-Android v1.1.1 发布说明

> 版本号递增：versionCode 5 → 6 | versionName 1.1.0 → 1.1.1

\---

## 修复

### 修复部分手机「检查更新」直接失败

* **根因**：`UpdateChecker` 的版本检测源单一指向 `raw.githubusercontent.com`，国内网络环境下经常超时/被墙，导致检测直接失败
* **修复**：检测源改为多源 fallback：

  * Cloudflare Pages（https://quiddity-3by.pages.dev/version.json）← 国内可达，首选
  * GitHub Pages（https://jiuan-9.github.io/Quiddity-website/version.json）
  * raw.githubusercontent.com（GitHub raw，兜底）
  * 任一源成功即返回，失败才尝试下一个
* **增强**：每次请求附加 `Cache-Control: no-cache` + `?t=<timestamp>` 绕过 CDN 缓存

### 修复部分手机「启动下载失败」

* **根因**：`installApk()` 使用 `DownloadManager.getUriForDownloadedFile()` 返回的 `DownloadsProvider` URI 直接安装，部分 ROM（华为、MIUI 等）的安装器无法读取该 URI
* **修复**：`installApk()` 统一改用 `FileProvider.getUriForFile()` 包装本地 APK 文件，依赖 `Intent.FLAG\_GRANT\_READ\_URI\_PERMISSION` 授权给目标 Activity
* **移除**：无效的 `grantUriPermission` 自我授权（`context.packageName` 给自己授权毫无意义）
* **增强**：新增 `findApkInTargetDirs()` 兜底查找逻辑，在 `getExternalFilesDir(null)` / `cacheDir` / 公共 Downloads 三处扫描最新的 `quiddity-\*.apk`

### 修复 downloadApk 路径兼容性

* **根因**：`setDestinationInExternalFilesDir(context, DIR, fileName)` 中 `DIR=Environment.DIRECTORY\_DOWNLOADS` 在部分厂商 ROM 上落地路径与 `file\_paths.xml` 声明不匹配，导致 FileProvider 授权失败
* **修复**：改用 `setDestinationInExternalFilesDir(context, null, fileName)`（落地到 `Android/data/<package>/files/<fileName>`），配合 `file\_paths.xml` 中 `<external-files-path name="external\_files" path="." />` 覆盖整个目录
* **补充**：添加 `<cache-path name="cache\_root" path="." />` 作为兜底路径

### 增强多源 APK 直链兜底

* 新增 `APK\_FALLBACK\_URLS` 列表：Cloudflare CDN + GitHub Releases 两条预置 APK 直链
* `resolveApkUrl()` 在解析失败时回退到预置链接，而不是返回 null

\---

## 兼容性

* v1.0.0 \~ v1.1.0 用户的本地数据、会话、API Key、设置等完全保留，可直接覆盖安装
* 系统要求不变：Android 8.0（API 26）及以上

## 下载

官网：https://quiddity-3by.pages.dev/
APK 直链：https://quiddity-3by.pages.dev/downloads/quiddity-1.1.1.apk
GitHub 备用：https://github.com/jiuan-9/Quiddity-website/releases/download/v1.1.1/quiddity-1.1.1.apk

