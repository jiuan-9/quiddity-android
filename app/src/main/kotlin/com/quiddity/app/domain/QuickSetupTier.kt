package com.quiddity.app.domain

import com.quiddity.app.data.model.Persona
import com.quiddity.app.data.model.UserPersona
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
 * 快速设定档位：与模型等级 [ApiCatalogManager.ModelTier] **层级解锁**绑定。
 *
 * 解锁规则（高级别解锁低档位）：
 * - [ROUGH] 粗略：BIC 模型可使用，500/300 字，仅生成 AI 名字/身份/性格 + 用户人设 5 项，不含记忆。
 * - [CONCRETE] 具体：ADVANCED 模型可使用，并可回退选择 ROUGH，2000/1200 字，AI 名字/身份/性格/外观/世界背景 + 用户人设 5 项 + 记忆。
 * - [COMPREHENSIVE] 全面：FULL 模型可使用，并可回退选择 ROUGH / CONCRETE，5000/3500 字，AI 全部字段（含期望特质）+ 用户人设 5 项 + 记忆。
 *
 * 用户可在可用档位中自由选择，UI 用滑动指示器展示当前档位并允许点击切换。
 */
enum class QuickSetupTier(
    val chineseName: String,
    val maxChars: Int,
    val maxWords: Int,
    val requiredTier: ApiCatalogManager.ModelTier,
    val includesMemory: Boolean
) {
    ROUGH(
        chineseName = "粗略",
        maxChars = 500,
        maxWords = 300,
        requiredTier = ApiCatalogManager.ModelTier.BASIC,
        includesMemory = false
    ),
    CONCRETE(
        chineseName = "具体",
        maxChars = 2000,
        maxWords = 1200,
        requiredTier = ApiCatalogManager.ModelTier.ADVANCED,
        includesMemory = true
    ),
    COMPREHENSIVE(
        chineseName = "全面",
        maxChars = 5000,
        maxWords = 3500,
        requiredTier = ApiCatalogManager.ModelTier.FULL,
        includesMemory = true
    );

    // ===== 三条开发规范（位于文件中间位置） =====
    // 1. 问题修复规范：所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
    //    修复内容需完全融入现有代码架构，确保代码逻辑的连贯性、可维护性和可扩展性。
    // 2. 代码注释规范：文件内仅允许保留两类注释——当前规则说明注释与模块划分注释（// ===== xxx =====）。
    //    除此之外，禁止出现任何形式的代码注释（包括单行 / 多行 / 临时调试注释）。
    // 3. 构建交付要求：完成所有开发任务并通过单元测试和集成测试后，必须将项目打包为标准 APK。
    //    APK 需满足：签名有效且符合发布标准、包含完整功能模块、经过基础性能与兼容性测试。

    /**
     * 该档位需要生成的 AI 人设字段标识（用于提示词中列出字段清单）。
     * 与 [Persona] 字段对应，顺序为输出顺序。
     */
    fun aiPersonaFields(): List<AiPersonaField> = when (this) {
        ROUGH -> listOf(AiPersonaField.NAME, AiPersonaField.PERSONA, AiPersonaField.CHARACTER)
        CONCRETE -> listOf(
            AiPersonaField.NAME, AiPersonaField.PERSONA, AiPersonaField.CHARACTER,
            AiPersonaField.APPEARANCE, AiPersonaField.WORLD_BACKGROUND
        )
        COMPREHENSIVE -> listOf(
            AiPersonaField.NAME, AiPersonaField.PERSONA, AiPersonaField.CHARACTER,
            AiPersonaField.APPEARANCE, AiPersonaField.WORLD_BACKGROUND, AiPersonaField.DESIRED
        )
    }

    companion object {
        /**
         * 返回当前模型等级下可用的档位列表（按枚举顺序：ROUGH → CONCRETE → COMPREHENSIVE）。
         * 高级别解锁所有低档位：FULL 可用全部 3 档，ADVANCED 可用 2 档，BASIC 可用 1 档。
         */
        fun availableTiers(currentTier: ApiCatalogManager.ModelTier): List<QuickSetupTier> {
            return entries.filter { tier ->
                when (currentTier) {
                    ApiCatalogManager.ModelTier.FULL -> true
                    ApiCatalogManager.ModelTier.ADVANCED -> tier != COMPREHENSIVE
                    ApiCatalogManager.ModelTier.BASIC -> tier == ROUGH
                }
            }
        }

        /**
         * 当前模型等级下默认档位（最高可用档位）。
         */
        fun defaultForTier(tier: ApiCatalogManager.ModelTier): QuickSetupTier = when (tier) {
            ApiCatalogManager.ModelTier.FULL -> COMPREHENSIVE
            ApiCatalogManager.ModelTier.ADVANCED -> CONCRETE
            ApiCatalogManager.ModelTier.BASIC -> ROUGH
        }
    }
}

/** AI 人设字段标识（与 [Persona] 字段一一对应，用于提示词与解析）。 */
enum class AiPersonaField(val label: String) {
    NAME("[名字]"),
    PERSONA("[身份背景]"),
    CHARACTER("[性格]"),
    APPEARANCE("[外观]"),
    WORLD_BACKGROUND("[世界背景]"),
    DESIRED("[期望特质]")
}

/** 用户人设字段标识（与 [UserPersona] 字段一一对应）。 */
enum class UserPersonaField(val label: String) {
    NAME("[名字]"),
    IDENTITY("[身份]"),
    GENDER("[性别]"),
    AGE("[年龄]"),
    APPEARANCE("[外观]")
}

/**
 * 快速设定解析结果。
 * - [persona] / [userPersona] / [scene] / [memory] 由 [QuickSetupPrompt.parseQuickSetupResult] 填充；
 * - 缺失字段为空字符串，保证一一对应且不污染既有数据。
 */
data class QuickSetupResult(
    val persona: Persona,
    val userPersona: UserPersona,
    val scene: String,
    val memory: String
)
