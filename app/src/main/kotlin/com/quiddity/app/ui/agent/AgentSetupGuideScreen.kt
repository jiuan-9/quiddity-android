package com.quiddity.app.ui.agent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
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
        title = "Android 11+：无线调试启动 Shizuku（免电脑）",
        steps = listOf(
            "打开手机「设置」→「开发者选项」→ 打开「无线调试」。",
            "打开 Shizuku 应用 →「无线调试」→ 点「开始」。",
            "在「无线调试」中点击「使用配对码配对设备」，记下 6 位配对码。",
            "在 Shizuku 的通知中输入配对码，完成配对。",
            "返回 Shizuku 点「启动」，等待提示已运行。"
        )
    )
    GuideSection(
        title = "注意",
        steps = listOf(
            "每次手机重启后需重新启动一次（配对只需一次）。",
            "若一直「正在搜索配对服务」：允许 Shizuku 后台运行；小米机型把通知样式改为「Android」样式。",
            "配对失败或输入配对码无效：配对码已过期，重新点击「使用配对码配对设备」，在 60 秒内输入。",
            "启动后，回到本 App → Agent → 设置 → 权限 →「去开启」，同意 Binder 授权弹窗即可解锁进阶能力。"
        )
    )
}

@Composable
private fun PcGuide() {
    GuideSection(
        title = "Android 8-10：PC 一键授权（USB）",
        steps = listOf(
            "先开启「开发者选项」（入口见下方各系统对照表）。",
            "在「开发者选项」中打开「USB 调试」，首次弹出确认窗口时点「允许」；建议同时打开「USB 安装」。",
            "用数据线连接电脑，USB 模式选择「传输文件」。",
            "手机弹出「允许 USB 调试？」时，勾选「始终允许使用这台计算机进行调试」，点「确定」。",
            "在电脑上打开「Quiddity 授权助手」，点「一键授权」，工具会自动安装并启动 Shizuku。",
            "完成后回到本 App → Agent → 设置 → 权限 →「去开启」，同意 Binder 授权弹窗即可解锁进阶能力。"
        )
    )
    GuideSection(title = "各系统开启「开发者选项」入口") {
        BrandEntryRow("MIUI 11 / 12（Android 8-10）", "设置 → 我的设备 → 全部参数与信息 → 连点「MIUI 版本」7 次")
        BrandEntryRow("HyperOS（Android 11+）", "设置 → 我的设备 → 全部参数与信息 → 连点「HyperOS 版本」7 次")
        BrandEntryRow("EMUI 10 / HarmonyOS 2", "设置 → 关于手机 → 连点「版本号」7 次")
        BrandEntryRow("ColorOS 7 / realme UI 1", "设置 → 关于本机 → 连点「版本号」7 次")
        BrandEntryRow("Funtouch OS 9 / 10", "设置 → 关于手机 → 连点「软件版本号」7 次")
        BrandEntryRow("Magic UI 3.x", "设置 → 关于手机 → 连点「版本号」7 次")
        BrandEntryRow("One UI 2.x", "设置 → 关于手机 → 软件信息 → 连点「编译编号」7 次")
        BrandEntryRow("原生 Android", "设置 → 关于手机 → 连点「版本号」7 次")
    }
    GuideSection(
        title = "常见错误速查",
        steps = listOf(
            "检测不到设备：充电线/数据线损坏、USB 模式不是「传输文件」或缺少驱动 → 换数据线；USB 模式选「传输文件」；安装官方驱动后重新检测。",
            "一直显示「未授权」：手机锁屏或授权弹窗被关闭 → 解锁手机，点「允许 USB 调试」并勾选「始终允许」，重新检测。",
            "安装失败 INSTALL_FAILED_UPDATE_INCOMPATIBLE：已安装签名不一致的旧版 Shizuku → 先卸载旧版再重装（工具会自动尝试）。",
            "提示需要先在手机上打开一次：从未打开过 Shizuku → 在手机上打开一次 Shizuku 应用。",
            "Shizuku 已运行但仍锁定：未授予本 App 的 Binder 权限 → Agent 设置 → 权限 →「去开启」，同意授权弹窗。",
            "系统提示「版本不支持」：手机是 Android 11+ → 请改用「无线调试」方式。"
        )
    )
}

@Composable
private fun BrandEntryRow(brand: String, entry: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = brand,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(max = 150.dp)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = entry,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
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
private fun GuideSection(
    title: String,
    steps: List<String> = emptyList(),
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
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
            if (content != null) {
                content()
            }
        }
    }
}
