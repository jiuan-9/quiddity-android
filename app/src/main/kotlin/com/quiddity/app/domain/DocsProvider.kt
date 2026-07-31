package com.quiddity.app.domain

/**
 * 应用文档内容提供者。
 *
 * 集中维护设置页“文档”抽屉中展示的各类说明文档，避免在 UI 组件中硬编码大段文本。
 * 其中《API-KEY 获取方式》的链接与步骤从 [ApiCatalogManager] 内置服务商数据派生，
 * 保证后续新增/修改服务商时文档能自动同步。
 */
class DocsProvider(
    private val apiCatalogManager: ApiCatalogManager
) {

    /** API-KEY 获取方式文档中的单条服务商信息。 */
    data class AcquisitionDoc(
        val providerName: String,
        val steps: List<String>,
        val url: String
    )

    val modelTierDocTitle: String = "《模型分配方案》"

    val apiKeyDocTitle: String = "《API-KEY 获取方式》"

    val backupDocTitle: String = "《数据备份说明》"

    val backupDocBody: String = """
        您可以通过“数据导出”功能将当前应用中的全部设置、会话、消息、用户头像与各会话的 AI 头像/壁纸备份为一个 JSON 文件，保存到手机本地或云盘中。

        需要恢复时，使用“数据导入”选择之前的备份文件即可。导入会恢复设置、会话、消息以及所有头像与壁纸图片。

        建议定期导出备份，尤其是在进行大版本更新或清理手机存储之前。
    """.trimIndent()

    /**
     * 返回所有内置服务商的 API-KEY 获取文档。
     * 过滤掉自定义服务商（没有固定控制台地址）。
     */
    fun apiKeyAcquisitionDocs(): List<AcquisitionDoc> =
        apiCatalogManager.providers
            .filter { it.id != "custom" }
            .map { provider ->
                AcquisitionDoc(
                    providerName = provider.name.substringBefore("\n"),
                    steps = stepsForProvider(provider.id),
                    url = provider.keyUrl
                )
            }

    private fun stepsForProvider(id: String): List<String> = when (id) {
        "alibaba" -> listOf(
            "访问阿里云百炼/灵积控制台",
            "登录阿里云账号",
            "在 API-KEY 管理页面创建新密钥",
            "将 Key 粘贴到上方输入框即可"
        )
        "baidu" -> listOf(
            "访问百度智能云千帆平台",
            "登录百度账号",
            "进入应用接入并创建应用",
            "在应用详情页获取 API Key"
        )
        "siliconflow" -> listOf(
            "访问 SiliconCloud 控制台",
            "注册/登录 SiliconFlow 账号",
            "进入 API 密钥页面",
            "新建 API 密钥并复制"
        )
        "stepfun" -> listOf(
            "访问阶跃星辰开放平台",
            "注册/登录账号",
            "进入 API Key 管理",
            "创建 Key 并复制"
        )
        "iflytek" -> listOf(
            "访问讯飞开放平台",
            "登录账号",
            "进入讯飞星火开放服务页面",
            "在认证信息中获取 APIKey 与 APISecret"
        )
        "minimax" -> listOf(
            "访问 MiniMax 开放平台",
            "注册/登录账号",
            "进入密钥管理",
            "创建并复制 API Key"
        )
        "deepseek" -> listOf(
            "访问 DeepSeek 开放平台",
            "注册/登录账号",
            "进入 API keys 页面",
            "创建新 API key 并复制"
        )
        "tencent" -> listOf(
            "访问腾讯云混元大模型控制台",
            "登录腾讯云账号",
            "开通混元大模型服务",
            "在 API 密钥管理创建并复制"
        )
        "moonshot" -> listOf(
            "访问 Moonshot AI 开放平台",
            "注册/登录账号",
            "进入 API Key 管理",
            "创建 API Key 并复制"
        )
        "bytedance" -> listOf(
            "访问火山引擎方舟控制台",
            "登录火山引擎账号",
            "进入 API Key 管理",
            "创建并复制 API Key"
        )
        "zhipu" -> listOf(
            "访问智谱 AI 开放平台",
            "注册/登录账号",
            "进入 API Keys 页面",
            "添加新 API Key 并复制"
        )
        else -> listOf("请前往该服务商官方网站，登录账号后在控制台或开发者中心创建 API Key。")
    }
}
