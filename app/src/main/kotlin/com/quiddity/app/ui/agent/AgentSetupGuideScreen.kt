package com.quiddity.app.ui.agent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quiddity.app.domain.agent.AgentSetupPath
import com.quiddity.app.domain.agent.AgentSetupRouter

/*
 * ============================================================================
 * 开发规范 (Development Specifications)
 * ============================================================================
 *
 * 1. 问题修复规范
 *    所有代码问题修复必须采用系统性解决方案，严禁使用临时性补丁或 hack 手段。
 *    修复内容需完全融入现有代码架构，保证代码逻辑的连贯性、可维护性和可扩展性。
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
 * Agent 权限开启教程页：由 [AgentSetupRouter] 按系统版本路由到
 * 无线调试（Android 11+）/ PC 助手（Android 8-10）/ 不支持（<26）。
 */
@Composable
fun AgentSetupGuideScreen(
    path: AgentSetupPath = AgentSetupRouter.routeForCurrent(),
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // ===== 顶栏 =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "Agent 权限开启教程",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (path) {
                AgentSetupPath.WirelessDebugging -> WirelessGuide()
                AgentSetupPath.PcTool -> PcGuide()
                AgentSetupPath.Unsupported -> UnsupportedGuide()
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun WirelessGuide() {
    GuideSection(
        title = "Android 11+：无线调试（免电脑）",
        steps = listOf(
            "安装 Shizuku（官网或酷安下载，本机不内置）。",
            "打开系统设置 → 开发者选项 → 开启「无线调试」。",
            "进入 Shizuku，选择「通过无线调试启动」，按提示输入 6 位配对码。",
            "授权完成后返回本 App，设置页 Shizuku 状态变为「已授权」。",
            "之后在 Agent 设置中开启对应写入工具并添加白名单即可使用。"
        )
    )
    GuideSection(
        title = "常见问题",
        steps = listOf(
            "重启手机后 Shizuku 服务会失效，需重新启动（配对码可能需重新输入）。",
            "小米/澎湃等国产 ROM 需登录账号后才能在开发者选项中使用无线调试。",
            "屏幕/通知权限在系统设置中单独开启，与本教程互不影响。"
        )
    )
}

@Composable
private fun PcGuide() {
    GuideSection(
        title = "Android 8-10：PC 一键授权（USB）",
        steps = listOf(
            "在电脑上运行 Quiddity 授权助手（D:\\quiddity授权助手，需另行获取）。",
            "手机开启开发者选项 → USB 调试；小米/澎湃建议同时开启「USB 调试（安全设置）」。",
            "用数据线连接电脑，授权助手会自动检测设备并安装/启动 Shizuku。",
            "手机端弹出授权窗口时点击允许。",
            "完成后返回本 App，设置页 Shizuku 状态变为「已授权」。"
        )
    )
    GuideSection(
        title = "常见问题",
        steps = listOf(
            "重启手机后 Shizuku 服务会失效，需重新插线执行一键授权。",
            "无法识别设备时检查驱动、数据线是否支持数据传输、USB 调试是否开启。",
            "国产 ROM 需额外开启「USB 调试（安全设置）」并登录账号。"
        )
    )
}

@Composable
private fun UnsupportedGuide() {
    Text(
        text = "当前系统版本低于 Android 8.0，不支持 Shizuku 授权通道。\n\n" +
            "Agent 的只读能力（屏幕/通知/用量）仍可通过系统设置单独开启。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun GuideSection(title: String, steps: List<String>) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            steps.forEachIndexed { index, step ->
                Text(
                    text = "${index + 1}. $step",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
