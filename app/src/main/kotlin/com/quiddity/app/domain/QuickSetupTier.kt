package com.quiddity.app.domain

import com.quiddity.app.data.model.Persona
import com.quiddity.app.data.model.UserPersona
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
    val includesMemory: Boolean,
    val densityRequirement: String
) {
    ROUGH(
        chineseName = "粗略",
        maxChars = 500,
        maxWords = 300,
        requiredTier = ApiCatalogManager.ModelTier.BASIC,
        includesMemory = false,
        densityRequirement = "剖析提炼：把用户原话归位到各字段，用 1-3 句精炼讲清，禁止扩写与注水。"
    ),
    CONCRETE(
        chineseName = "具体",
        maxChars = 2000,
        maxWords = 1200,
        requiredTier = ApiCatalogManager.ModelTier.ADVANCED,
        includesMemory = true,
        densityRequirement = "剖析归位：用户原话的细节全部保留并按字段归位，内容充实；缺失处只做最小推断，禁止自行添加设定。"
    ),
    COMPREHENSIVE(
        chineseName = "全面",
        maxChars = 5000,
        maxWords = 3500,
        requiredTier = ApiCatalogManager.ModelTier.FULL,
        includesMemory = true,
        densityRequirement = "深度剖析：从用户描述中挖掘并结构化全部信息，原话细节完整保留，内容详尽；推断必须源于原意，禁止注水。"
    );

