# Quiddity-Android 项目协作说明

本文档对所有 Codex 窗口/会话生效：打开本项目时自动加载，请先阅读再动手。

## 项目位置与环境

- 项目根目录：`D:\Quiddity-android`（所有窗口统一使用此路径）
- 当前版本：1.6.0（versionCode 15），主页为私聊/群聊双 Tab
- 构建：`D:\Quiddity-android\gradlew.bat :app:assembleDebug`（调试）/
  `:app:assembleRelease`（发布，自动签名）
- APK 输出只允许放在标准路径：
  - 调试：`app\build\outputs\apk\debug\app-debug.apk`
  - 发布：`app\build\outputs\apk\release\app-release.apk`
- 单元测试：`gradlew.bat :app:testDebugUnitTest`

## 模拟器使用（所有窗口统一方法）

### 启动模拟器

模拟器 AVD 为 `quiddity_test`，SDK 与 AVD 均在 D 盘。启动方式：

```bat
D:\start-emulator.bat
```

等价命令（启动脚本内部）：

```bat
set ANDROID_SDK_ROOT=D:\android-sdk
set ANDROID_AVD_HOME=D:\quiddity-avd
"D:\android-sdk\emulator\emulator.exe" -avd quiddity_test -gpu swiftshader -no-boot-anim -no-snapshot
```

**重要**：必须使用 ASCII 路径 `D:\android-sdk` 启动。任何经过中文路径的启动方式都会导致模拟器写坏 SDK、启动死锁。

### adb 与常用命令

adb 路径：`D:\android-sdk\platform-tools\adb.exe`（不要依赖 PATH）。

```bat
adb devices                        REM 查看设备
adb wait-for-device                REM 等待设备上线
adb shell getprop sys.boot_completed  REM 等待开机完成（输出 1 表示就绪）
adb install -r app-debug.apk       REM 覆盖安装
adb shell am start -n com.quiddity.app.debug/com.quiddity.app.MainActivity
adb shell am force-stop com.quiddity.app.debug
adb exec-out screencap -p > 截图.png
```

### 应用包名

- 调试包：`com.quiddity.app.debug`（versionName 1.6.0-debug，日常验证用这个）
- 正式包：`com.quiddity.app`（真机/发布用，模拟器里可能是旧版本，别搞混）

启动/操作模拟器一律用**调试包**，安装 APK 用 `app-debug.apk`。

### 模拟器常见问题

- `adb devices` 为空 / 连不上：模拟器可能没启动或已退出。先执行 `D:\start-emulator.bat`，再 `adb wait-for-device`。
- 启动后立即装包报 `Can't find service: package`：系统还在启动，等 `sys.boot_completed` 输出 1 再装。
- 模拟器里的应用数据在 `run-as com.quiddity.app.debug` 私有目录，读取用
  `adb exec-out run-as com.quiddity.app.debug cat files/quiddity-data/<文件>`；
  注意 Windows 下 adb 文本输出可能被转成 UTF-16LE，解析时按 UTF-16 解码，或先 base64 传输。

## 协作规范

- D 盘根目录只保留 `D:\start-emulator.bat`；测试临时文件（截图、日志、UI dump）用完即删。
- 多个窗口可能并行修改本仓库，动手前先 `git status` 确认工作区状态，避免覆盖他人改动。
- 提交信息用中文、描述清楚改动内容。
