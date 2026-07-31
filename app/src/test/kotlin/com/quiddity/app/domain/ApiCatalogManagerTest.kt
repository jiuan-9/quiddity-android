package com.quiddity.app.domain

import com.quiddity.app.data.remote.ChatApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
 * [ApiCatalogManager] 单元测试。
 *
 * 验证模型分级与应用内模型列表的一致性，防止 provider 新增模型后分级表遗漏
 * 或自定义模型等级规则被破坏。
 */
class ApiCatalogManagerTest {

    private val manager = ApiCatalogManager(ChatApi())

    @Test
    fun `all provider models have a tier`() {
        val providerModels = manager.providers
            .filter { it.id != "custom" }
            .flatMap { it.models }
            .toSet()

        val tieredModels = manager.tieredModels().values.flatten().toSet()

        assertEquals(
            providerModels,
            tieredModels,
            "provider 模型列表与分级表必须完全一致，差集：" +
                "${(providerModels - tieredModels) + (tieredModels - providerModels)}"
        )
    }

    @Test
    fun `supported model count is exactly 54`() {
        val total = manager.tieredModels().values.sumOf { it.size }
        assertEquals(54, total, "应用内置支持的模型总数应为 54")
    }

    @Test
    fun `deepseek-v4-flash is full tier`() {
        assertEquals(
            ApiCatalogManager.ModelTier.FULL,
            manager.getModelTier("deepseek-v4-flash", "deepseek")
        )
        assertEquals(
            ApiCatalogManager.ModelTier.FULL,
            manager.getModelTier("deepseek-ai/DeepSeek-V4-Flash", "siliconflow")
        )
    }

    @Test
    fun `custom provider models are always full tier`() {
        assertEquals(
            ApiCatalogManager.ModelTier.FULL,
            manager.getModelTier("any-model", "custom")
        )
        assertEquals(
            ApiCatalogManager.ModelTier.FULL,
            manager.getModelTier("", "custom")
        )
    }

    @Test
    fun `unknown model falls back to full tier`() {
        assertEquals(
            ApiCatalogManager.ModelTier.FULL,
            manager.getModelTier("not-listed-model", "openai")
        )
    }

    @Test
    fun `tiered models are grouped correctly`() {
        val tiered = manager.tieredModels()
        assertTrue(tiered.containsKey(ApiCatalogManager.ModelTier.FULL))
        assertTrue(tiered.containsKey(ApiCatalogManager.ModelTier.ADVANCED))
        assertTrue(tiered.containsKey(ApiCatalogManager.ModelTier.BASIC))

        val all = tiered.values.flatten()
        assertEquals(all.size, all.toSet().size, "分级表中不允许重复模型 ID")
    }
}
