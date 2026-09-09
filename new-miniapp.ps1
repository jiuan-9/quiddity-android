#Requires -Version 5.1
<#
.SYNOPSIS
  小应用脚手架：生成一个可编译的最小小应用实现，并自动注册到 MiniAppRegistry。

.DESCRIPTION
  用法（PowerShell，仓库根目录执行）：
    .\new-miniapp.ps1 -Id dice -Name "骰子" -Description "随机掷骰子小游戏"
    .\new-miniapp.ps1 -Id memory -Name "记忆翻牌" -Description "翻牌锻炼记忆力" -Icon Star

  参数：
    -Id          小应用英文标识（字母开头，仅字母/数字），将用于路由与收藏，需全局唯一。
    -Name        中文展示名称。
    -Description 一句话描述（显示在小应用中心卡片）。
    -Icon        Material 图标名（PascalCase，来自 material-icons-extended），默认 Extension。

  生成效果：
    1. 在 ui/miniapps/<id>/ 生成 <Pascal>MiniApp.kt（单文件、可直接编译的占位实现）；
    2. 自动把 import 与注册条目写入 MiniAppRegistry.all（MiniApp.kt）；
    3. 收藏、中心页、路由、邀请气泡与注册表可拓展性测试均自动生效，无需改导航代码。

  验证命令：
    .\gradlew.bat :app:testDebugUnitTest --tests "com.quiddity.app.ui.miniapps.MiniAppRegistryTest"
    .\gradlew.bat :app:compileDebugKotlin
#>
param(
    [Parameter(Mandatory = $true)][string]$Id,
    [Parameter(Mandatory = $true)][string]$Name,
    [Parameter(Mandatory = $true)][string]$Description,
    [string]$Icon = "Extension"
)

$ProgressPreference = 'SilentlyContinue'
$ErrorActionPreference = 'Stop'
$root = if ([string]::IsNullOrWhiteSpace($PSScriptRoot)) { (Get-Location).Path } else { $PSScriptRoot }

# ---------- 入参校验 ----------

if ($Id -notmatch '^[A-Za-z][A-Za-z0-9]*$') {
    throw "Id 只能包含英文字母和数字，且以字母开头（当前：$Id）"
}
if ($Id -match 'MiniApp') {
    throw "Id 不要包含 'MiniApp'，类名会自动拼接该后缀（当前：$Id）"
}
if ($Id -match 'board') {
    throw "id 与现有小应用 board 冲突，请换一个"
}
if ([string]::IsNullOrWhiteSpace($Name)) { throw "Name 不能为空" }
if ([string]::IsNullOrWhiteSpace($Description)) { throw "Description 不能为空" }
if ($Icon -notmatch '^[A-Za-z]\w*$') { throw "Icon 应为 Material 图标名（如 Extension、Casino），不需要 Icons.Rounded. 前缀" }

$pascal = $Id.Substring(0, 1).ToUpperInvariant() + $Id.Substring(1)
$className = $pascal + "MiniApp"
$pkgId = $Id.ToLowerInvariant()
$pkg = "com.quiddity.app.ui.miniapps.$pkgId"
$routeName = $pascal + "Route"
$miniAppsRel = "app/src/main/kotlin/com/quiddity/app/ui/miniapps"
$targetDir = Join-Path $root (Join-Path $miniAppsRel $pkgId)
$targetFile = Join-Path $targetDir "$className.kt"
$registryFile = Join-Path $root (Join-Path $miniAppsRel "MiniApp.kt")

if (-not (Test-Path -LiteralPath $registryFile)) {
    throw "找不到注册表文件：$registryFile"
}
if (Test-Path -LiteralPath $targetFile) {
    throw "文件已存在，请先处理后再运行：$targetFile"
}
$registryText = [System.IO.File]::ReadAllText($registryFile)
if ($registryText.Contains($className)) {
    throw "小应用已注册过，请换一个 id：$className"
}

# ---------- 生成 Kotlin 模板 ----------

$template = @'
package @PKG@

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.@ICONFN@
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.quiddity.app.ui.miniapps.MiniApp
import com.quiddity.app.ui.miniapps.MiniAppHost

/*
 * @APPNAME@ 小应用。
 *
 * 由 new-miniapp.ps1 脚手架生成，可直接编译运行。
 * 扩展顺序（复杂小应用参考棋盘 board 的分层）：
 * 1. 纯 Kotlin 业务逻辑放入 domain/ 子包（可单测）；
 * 2. 需要 LLM/AI 时复用 ServiceLocator.chatApi 与 ApiCatalogManager；
 * 3. 需要"邀请角色"流程时复用 MiniAppInviteManager（自动写邀请气泡与对局记忆）；
 * 4. 页面与状态机变复杂后，按模块拆分文件，并用 viewModel(factory=...) 接 ViewModel。
 */
object @CLASSNAME@ : MiniApp {
    override val id: String = "@APPID@"
    override val name: String = "@APPNAME@"
    override val description: String = "@APPDESC@"
    override val icon: ImageVector = Icons.Rounded.@ICONFN@

    @Composable
    override fun Content(host: MiniAppHost) {
        var route by remember { mutableStateOf<@ROUTNAME@>(@ROUTNAME@.Home) }
        Root(host = host, route = route, onNavigate = { route = it })
    }
}

/** 小应用内部路由：页面流转由自身状态机管理，不污染主 NavHost。 */
private sealed interface @ROUTNAME@ {
    data object Home : @ROUTNAME@

    data object Detail : @ROUTNAME@
}

@Composable
private fun Root(
    host: MiniAppHost,
    route: @ROUTNAME@,
    onNavigate: (@ROUTNAME@) -> Unit
) {
    AnimatedContent(
        targetState = route,
        transitionSpec = {
            (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { full -> full / 6 })
                .togetherWith(fadeOut(tween(160)) + slideOutHorizontally(tween(160)) { full -> -full / 6 })
        },
        label = "@APPID@_route"
    ) { current ->
        when (current) {
            @ROUTNAME@.Home -> HomeScreen(host = host, onOpenDetail = { onNavigate(@ROUTNAME@.Detail) })
            @ROUTNAME@.Detail -> DetailScreen(host = host, onBack = { onNavigate(@ROUTNAME@.Home) })
        }
    }
}

@Composable
private fun HomeScreen(
    host: MiniAppHost,
    onOpenDetail: () -> Unit
) {
    BackHandler(onBack = host.onExit)
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.@ICONFN@,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text("@APPNAME@", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "@APPDESC@",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = onOpenDetail) { Text("进入示例页面") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { host.onToast("宿主 Toast 可用") }) { Text("演示宿主能力") }
        }
    }
}

@Composable
private fun DetailScreen(
    host: MiniAppHost,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Spacer(Modifier.width(8.dp))
            Text("示例详情", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(20.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "这里是核心交互占位区：把这里的静态内容替换成你的小应用真实业务。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        // 示例动作；真实业务里可通过 host.onOpenConversation(会话id) 跳转私聊
                        host.onToast("欢迎使用小应用")
                    }
                ) { Text("演示动作") }
            }
        }
    }
}
'@

# ---------- 落盘模板 ----------

$kotlin = $template.Replace('@PKG@', $pkg).Replace('@APPID@', $pkgId).Replace('@APPNAME@', $Name).Replace('@APPDESC@', $Description).Replace('@CLASSNAME@', $className).Replace('@ROUTNAME@', $routeName).Replace('@ICONFN@', $Icon)

New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
[System.IO.File]::WriteAllText($targetFile, $kotlin, [System.Text.UTF8Encoding]::new($false))
Write-Host "已生成: $targetFile"

# ---------- 注册到 MiniAppRegistry ----------

$utf8 = [System.Text.UTF8Encoding]::new($false)
$lines = [System.IO.File]::ReadAllLines($registryFile, $utf8)

$lastImportIdx = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
    if ($lines[$i] -match '^import com\.quiddity\.app\.ui\.miniapps\.') { $lastImportIdx = $i }
}
if ($lastImportIdx -lt 0) { throw "MiniApp.kt 中未找到 miniapps 相关 import，请手动登记" }

$pre = New-Object System.Collections.Generic.List[string]
for ($i = 0; $i -le $lastImportIdx; $i++) { $pre.Add($lines[$i]) }
$pre.Add("import com.quiddity.app.ui.miniapps.$pkgId.$className")
for ($i = $lastImportIdx + 1; $i -lt $lines.Count; $i++) { $pre.Add($lines[$i]) }

$lastEntryIdx = -1
for ($i = 0; $i -lt $pre.Count; $i++) {
    if ($pre[$i] -match '^\s+[A-Za-z0-9_]+MiniApp\s*$') { $lastEntryIdx = $i }
}
if ($lastEntryIdx -lt 0) { throw "MiniApp.kt 中未找到注册表条目，请手动登记" }

$indent = [regex]::Match($pre[$lastEntryIdx], '^(\s+)').Groups[1].Value
if ($pre[$lastEntryIdx] -notmatch ',\s*$') { $pre[$lastEntryIdx] = $pre[$lastEntryIdx] + ',' }

$final = New-Object System.Collections.Generic.List[string]
for ($i = 0; $i -lt $pre.Count; $i++) {
    $final.Add($pre[$i])
    if ($i -eq $lastEntryIdx) { $final.Add($indent + $className) }
}
[System.IO.File]::WriteAllLines($registryFile, $final, $utf8)
Write-Host "已注册: MiniAppRegistry.all 新增 $className"

# ---------- 结束：输出下一步 ----------

Write-Host ""
Write-Host "====================== 执行完成 ======================"
Write-Host "小应用 $Name ($pkgId) 已创建并注册，收藏/中心页/路由自动生效。"
Write-Host "快速验证："
Write-Host "  .\gradlew.bat :app:testDebugUnitTest --tests ``"com.quiddity.app.ui.miniapps.MiniAppRegistryTest``""
Write-Host "  .\gradlew.bat :app:compileDebugKotlin"
Write-Host "下一步：打开 $targetFile，把 Detail 占位页替换成真实交互；"
Write-Host "复杂逻辑参考 board 棋盘：domain 纯逻辑 + ViewModel + MiniAppInviteManager。"
Write-Host ""