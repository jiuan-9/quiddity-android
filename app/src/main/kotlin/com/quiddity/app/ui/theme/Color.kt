package com.quiddity.app.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * ============================================================================
 * 开发规范 (Development Specifications)
 * ============================================================================
 *
 * 1. 问题修复规范
 *    所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
 *    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
 *
 * 2. 代码注释规范
 *    文件内仅允许保留两类注释：
 *    - 当前规则说明注释（即本注释块）
 *    - 模块划分注释（用于标识代码功能模块边界）
 *    除此之外，禁止出现任何形式的代码注释（包括但不限于单行注释、多行注释、临时调试注释等）。
 *
 * 3. 构建交付要求
 *    在完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK 文件。
 *    APK 文件需满足以下条件：
 *    - 签名有效且符合发布标准
 *    - 包含完整的功能模块
 *    - 经过基础性能测试和兼容性测试
 *    以便在真实设备环境中进行功能验证和性能评估。
 *
 * ============================================================================
 */



/**
 * 奶白主题配色。
 * 主背景采用奶白色 #FAF9F6，营造温暖、人文化的 AI 伙伴氛围。
 * 所有色值见 PLAN.md 七、配色方案。
 */

// ===== 亮色主题 =====
val CreamBg = Color(0xFFFAF9F6)        // 主背景
val CardBg = Color(0xFFFFFFFF)         // 卡片 / AI 气泡
val CardBgWarm = Color(0xFFFEFDFB)     // AI 气泡（微暖白）
val InputBg = Color(0xFFFFFFFF)        // 输入框
val SecondaryBg = Color(0xFFF5F4ED)    // 次级容器
val TextPrimary = Color(0xFF1A1A1A)    // 主文字
val TextSecondary = Color(0xFF4D4C48)  // 次级文字
val TextTertiary = Color(0xFF87867F)   // 辅助文字
val Divider = Color(0xFFF0EEE6)        // 分割线
val UserBubbleLight = Color(0xFF3A3A3A) // 用户气泡（亮色模式：深灰近黑）
val UserBubbleTextLight = Color(0xFFFFFFFF)
val Danger = Color(0xFFB53333)         // 危险按钮
val Accent = Color(0xFF1A1A1A)         // 强调色（黑）

// ===== 暗色主题 =====
val DarkBg = Color(0xFF1A1A1A)
val DarkCardBg = Color(0xFF2A2A2A)     // AI 气泡（黑夜稍微暖一点的深色）
val DarkInputBg = Color(0xFF2A2A2A)
val DarkSecondaryBg = Color(0xFF242424)
val DarkTextPrimary = Color(0xFFFAF9F6)
val DarkTextSecondary = Color(0xFFC8C6BE)
val DarkTextTertiary = Color(0xFF87867F)
val DarkDivider = Color(0xFF3A3A3A)
val DarkUserBubble = Color(0xFF4A4A4A) // 用户气泡（黑夜稍微高一点的灰色，不显突兀）
val DarkUserBubbleText = Color(0xFFFFFFFF)
val DarkDanger = Color(0xFFC84545)
val DarkAccent = Color(0xFFFAF9F6)
