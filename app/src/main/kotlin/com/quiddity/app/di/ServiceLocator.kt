package com.quiddity.app.di

import android.annotation.SuppressLint
import android.content.Context
import com.quiddity.app.data.local.ConversationStore
import com.quiddity.app.data.local.SettingsStore
import com.quiddity.app.data.remote.ChatApi
import com.quiddity.app.data.repo.ChatRepository
import com.quiddity.app.data.repo.ConversationRepository
import com.quiddity.app.data.repo.SettingsRepository
import com.quiddity.app.domain.ApiCatalogManager
import com.quiddity.app.domain.DocsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 手动依赖注入容器。
 * 在 [com.quiddity.app.QuiddityApp] 中初始化。
 *
 * 说明：本单例持有的是 [Context.getApplicationContext]，且仅在 [QuiddityApp.onCreate]
 * 中初始化一次。Lint 的 StaticFieldLeak 警告针对的是普通 Activity/Fragment 上下文泄漏，
 * 对全局 Application 单例不适用，因此整体抑制该检查。
 */
@SuppressLint("StaticFieldLeak")
object ServiceLocator {

    private lateinit var appContext: Context
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var settingsStore: SettingsStore
        private set
    lateinit var conversationStore: ConversationStore
        private set
    lateinit var chatApi: ChatApi
        private set

    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var conversationRepository: ConversationRepository
        private set
    lateinit var chatRepository: ChatRepository
        private set

    /**
     * 集中管理 Provider 预设、ID 生成、Key 加解密、连接测试。
     */
    lateinit var apiCatalogManager: ApiCatalogManager
        private set

    /**
     * 应用文档内容提供者。
     * 集中维护设置页“文档”抽屉中的说明文档，避免在 UI 中硬编码大段文本。
     */
    lateinit var docsProvider: DocsProvider
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        settingsStore = SettingsStore(appContext)
        conversationStore = ConversationStore(appContext)
        chatApi = ChatApi()

        settingsRepository = SettingsRepository(settingsStore)
        apiCatalogManager = ApiCatalogManager(chatApi)
        // 用于新会话创建时按模型分级初始化 contextLimit，以及预填默认 AI 人设身份。
        conversationRepository = ConversationRepository(
            store = conversationStore,
            settingsRepository = settingsRepository,
            apiCatalogManager = apiCatalogManager
        )
        docsProvider = DocsProvider(apiCatalogManager)
        chatRepository = ChatRepository(chatApi, conversationRepository, settingsRepository)

        // 启动时加载会话
        appScope.launch {
            conversationRepository.loadAll()
            // 详见 ConversationStore.migrateDeduplicateMessageIds
            conversationRepository.migrateDeduplicateMessageIds()
        }
    }
}
