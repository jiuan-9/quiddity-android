package com.quiddity.app.domain

import com.quiddity.app.data.remote.ChatApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
