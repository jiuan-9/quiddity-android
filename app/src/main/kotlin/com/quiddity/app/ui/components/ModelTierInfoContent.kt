package com.quiddity.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quiddity.app.domain.ApiCatalogManager

/**
 * 《模型分配方案》说明内容（可复用组件）。
 *
 * 将原本内嵌在 [ModelTierInfoDialog] 中的说明内容提取出来，供
 * 文档抽屉、弹窗等多处复用，避免重复实现。
 *
 * @param searchQuery 搜索关键词。非空时按模型名称过滤，未匹配的层级整段隐藏；
 *   全部未匹配时由调用方负责展示占位文案。
 */
@Composable
fun ModelTierInfoContent(
    catalogManager: ApiCatalogManager,
    modifier: Modifier = Modifier,
    searchQuery: String = ""
) {
    val tiered = catalogManager.tieredModels()
    val q = searchQuery.trim().lowercase()
    // 按搜索词过滤每个分级的模型列表；过滤后为空的分级不渲染，避免出现空卡片。
    val filteredFull = filterModels(tiered[ApiCatalogManager.ModelTier.FULL].orEmpty(), q)
    val filteredAdv = filterModels(tiered[ApiCatalogManager.ModelTier.ADVANCED].orEmpty(), q)
    val filteredBasic = filterModels(tiered[ApiCatalogManager.ModelTier.BASIC].orEmpty(), q)
    val anyMatch = filteredFull.isNotEmpty() || filteredAdv.isNotEmpty() || filteredBasic.isNotEmpty()

    Column(modifier = modifier) {
        Text(
            text = "《模型分配方案》",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.size(12.dp))
        // 搜索时隐藏说明性长文本，让用户更快定位到目标模型。
        if (q.isEmpty()) {
            Text(
                text = "我们深知您对 AI 陪伴体验的期待。本次分级不是“阉割”，而是根据各模型在复杂叙事指令遵循、长对话一致性、输出多样性三项实测得分，并综合成本与厂商主流度做出的保护性划分。低分级模型在开放全部设定后，反而容易输出重复、崩坏人设或上下文断裂的内容，导致体验更差。通过限制字段并注入经过验证的轻量指令，我们让每一级模型都能在其能力范围内稳定发挥。完整级模型可自由定制所有字段。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.size(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Text(
                    text = "如果您确定要自己决定模型的等级，那么请使用自定义添加模型：自定义服务商的模型将自动归为完整级，您可自行评估其实际表现。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(modifier = Modifier.size(16.dp))
        }

        if (q.isNotEmpty() && !anyMatch) {
            Text(
                text = "未找到匹配的模型",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 24.dp)
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (filteredFull.isNotEmpty()) {
                    TierSection(
                        title = "完整级",
                        subtitle = "全部功能开放 · 可编辑所有字段",
                        models = filteredFull,
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
                if (filteredAdv.isNotEmpty()) {
                    TierSection(
                        title = "进阶级",
                        subtitle = "除“你希望ta是什么样的”外全部开放",
                        models = filteredAdv,
                        accentColor = MaterialTheme.colorScheme.secondary
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
                if (filteredBasic.isNotEmpty()) {
                    TierSection(
                        title = "基础级",
                        subtitle = "仅开放：名字、身份、性格",
                        models = filteredBasic,
                        accentColor = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

/**
 * 按搜索词过滤模型名列表（忽略大小写、子串匹配）。
 */
private fun filterModels(models: List<String>, query: String): List<String> {
    if (query.isEmpty()) return models
    return models.filter { it.lowercase().contains(query) }
}

@Composable
private fun TierSection(
    title: String,
    subtitle: String,
    models: List<String>,
    accentColor: androidx.compose.ui.graphics.Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (title == "完整级") Icons.Filled.Star else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = accentColor,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 28.dp)
        )
        Spacer(modifier = Modifier.size(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            models.forEach { model ->
                Text(
                    text = "· $model",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
