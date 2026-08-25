# Quiddity-Android 1.6.1 发布说明

版本号递增：versionCode 16 → 17 | versionName 1.6.0 → 1.6.1

## 本版本内容

### 修复

- 修复「Shizuku 已授权仍无法开启工具分类」：工具开关权限门控的兜底分支将
  剪贴板读写、打开应用、定时消息、睡眠、OCR 等无需系统权限的工具永久判定为
  「需 Shizuku 授权（未开启）」，污染整个分类的缺权限清单，导致即使已授权
  Shizuku，分类主开关仍提示「Shizuku 授权未开启，无法开启该类工具」。
  现兜底分支按实际 Shizuku 授权状态判定，无权限依赖的工具一律放行。

### 新增

- 接入 DeepSeek 官方最新多模态模型 `deepseek-v4-flash-vision-exp`
  （2026-08-21 上线的实验版视觉理解模型）：
  - DeepSeek 服务商预设模型列表新增该模型；
  - 登记进视觉模型清单：选中该模型对话时发送图片直接走当前对话 API 识图，
    无需再依赖视觉 OCR 兜底配置；
  - 模型能力分级为完整级（文本能力与 deepseek-v4-flash 持平，计费一致，
    单张图片最多折算 384 tokens）；
  - 图文混合输入沿用现有 OpenAI 兼容 image_url 通道，支持 base64 内联传图；
  - DeepSeek 思考开关按模型名匹配自动生效，联网搜索仍仅限官方
    deepseek-v4-flash（随官方开放范围维护）。

## 构建产物

- **versionCode**: 17
- **versionName**: 1.6.1（调试包 1.6.1-debug）
- 调试：`app\build\outputs\apk\debug\app-debug.apk`
- 发布：`app\build\outputs\apk\release\app-release.apk`
