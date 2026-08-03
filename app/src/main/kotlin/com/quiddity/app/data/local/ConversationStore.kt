package com.quiddity.app.data.local

import android.content.Context
import android.util.AtomicFile
import com.quiddity.app.data.model.Conversation
import com.quiddity.app.data.model.ConversationType
import com.quiddity.app.data.model.Message
import com.quiddity.app.util.QuiddityConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.concurrent.ConcurrentHashMap

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



class ConversationStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val dataDir: File by lazy {
        File(context.filesDir, "quiddity-data").apply { mkdirs() }
    }

    private val conversationsFile: File by lazy {
        File(dataDir, "conversations.json")
    }

    private val conversationsAtomicFile: AtomicFile by lazy {
        AtomicFile(conversationsFile)
    }

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    private val messagesCache = ConcurrentHashMap<String, MutableList<Message>>()
    private val messagesFlows = ConcurrentHashMap<String, MutableStateFlow<List<Message>>>()
    private val messageLocks = ConcurrentHashMap<String, Mutex>()
    private val loadLocks = ConcurrentHashMap<String, Mutex>()

    private fun getMessageLock(convId: String): Mutex =
        messageLocks.getOrPut(convId) { Mutex() }

    private fun getLoadLock(convId: String): Mutex =
        loadLocks.getOrPut(convId) { Mutex() }

    private fun messagesFile(convId: String): File = File(dataDir, "messages_$convId.json")

    private fun atomicMessagesFile(convId: String): AtomicFile = AtomicFile(messagesFile(convId))

    suspend fun loadAll() = withContext(Dispatchers.IO) {
        val list = readConversations()
        _conversations.value = list
    }

    suspend fun migrateDeduplicateMessageIds() = withContext(Dispatchers.IO) {
        var totalCleaned = 0
        var streamingCleaned = 0
        val convs = _conversations.value
        convs.forEach { conv ->
            getMessageLock(conv.id).withLock {
                val file = messagesFile(conv.id)
                if (!file.exists()) return@withLock
                val messages = runCatching {
                    json.decodeFromString(ListSerializer(Message.serializer()), file.readText())
                }.getOrDefault(emptyList())
                if (messages.isEmpty()) return@withLock

                val deduped = messages.withIndex()
                    .groupBy { it.value.id }
                    .map { (_, entries) -> entries.last().value }

                val cleaned = deduped.map { msg ->
                    if (msg.isStreaming) {
                        streamingCleaned++
                        msg.copy(isStreaming = false)
                    } else msg
                }

                if (cleaned.size != messages.size || cleaned != deduped) {
                    totalCleaned += (messages.size - cleaned.size)
                    messagesCache[conv.id] = cleaned.toMutableList()
                    messagesFlows.getOrPut(conv.id) { MutableStateFlow(cleaned) }
                        .value = cleaned
                    writeMessagesAtomic(conv.id, cleaned)
                }
            }
        }
        if (totalCleaned > 0) {
            android.util.Log.i("ConversationStore", "已清理 $totalCleaned 条重复消息 id")
        }
        if (streamingCleaned > 0) {
            android.util.Log.i("ConversationStore", "已重置 $streamingCleaned 条孤儿 streaming 消息")
        }
    }

    private fun readConversations(): List<Conversation> {
        if (!conversationsFile.exists()) return emptyList()
        return runCatching {
            val text = conversationsAtomicFile.openRead().use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
            if (text.isBlank()) emptyList()
            else json.decodeFromString(ListSerializer(Conversation.serializer()), text)
        }.getOrElse {
            android.util.Log.w("ConversationStore", "读取 conversations.json 失败", it)
            backupCorruptFile(conversationsFile)
            emptyList()
        }
    }

    /**
     * 损坏文件备份：解码失败时把原文件改名保留（.corrupt-<时间戳>），
     * 避免用户数据被静默当作"无记录"永久丢失。
     */
    private fun backupCorruptFile(file: File) {
        runCatching {
            if (file.exists()) {
                val bak = File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}")
                file.renameTo(bak)
            }
        }
    }

    /** @return 是否写盘成功（失败仅记录日志，调用方按需上报 UI） */
    private fun writeConversationsAtomic(list: List<Conversation>): Boolean {
        val text = json.encodeToString(ListSerializer(Conversation.serializer()), list)
        var success = true
        runCatching {
            var stream = conversationsAtomicFile.startWrite()
            try {
                stream.write(text.toByteArray(Charsets.UTF_8))
                stream.flush()
                conversationsAtomicFile.finishWrite(stream)
            } catch (t: Throwable) {
                conversationsAtomicFile.failWrite(stream)
                success = false
                throw t
            }
        }.onFailure { android.util.Log.e("ConversationStore", "写入 conversations.json 失败", it) }
        return success
    }

    suspend fun observeMessages(convId: String): StateFlow<List<Message>> {
        messagesCache[convId]?.let {
            return messagesFlows.getOrPut(convId) { MutableStateFlow(it.toList()) }
        }

        return getLoadLock(convId).withLock {
            messagesFlows[convId]?.let { return@withLock it }
            val messages = loadMessagesFromDisk(convId)
            messagesCache.getOrPut(convId) { messages.toMutableList() }
            messagesFlows.getOrPut(convId) { MutableStateFlow(messages) }
        }
    }

    private fun loadMessagesFromDisk(convId: String): List<Message> {
        messagesCache[convId]?.let { return it.toList() }
        val file = messagesFile(convId)
        if (!file.exists()) return emptyList()
        return runCatching {
            val text = atomicMessagesFile(convId).openRead().use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
            if (text.isBlank()) emptyList()
            else json.decodeFromString(ListSerializer(Message.serializer()), text)
        }.getOrElse {
            android.util.Log.w("ConversationStore", "读取 messages_$convId.json 失败", it)
            backupCorruptFile(file)
            emptyList()
        }
    }

    /** @return 是否写盘成功（失败仅记录日志，调用方按需上报 UI） */
    private fun writeMessagesAtomic(convId: String, messages: List<Message>): Boolean {
        val af = atomicMessagesFile(convId)
        val text = json.encodeToString(ListSerializer(Message.serializer()), messages)
        var success = true
        runCatching {
            var stream = af.startWrite()
            try {
                stream.write(text.toByteArray(Charsets.UTF_8))
                stream.flush()
                af.finishWrite(stream)
            } catch (t: Throwable) {
                af.failWrite(stream)
                success = false
                throw t
            }
        }.onFailure { android.util.Log.e("ConversationStore", "写入 messages_$convId.json 失败", it) }
        return success
    }

    suspend fun createConversation(conv: Conversation) = withContext(Dispatchers.IO) {
        val newList = listOf(conv) + _conversations.value
        writeConversationsAtomic(newList)
        _conversations.value = newList
    }

    suspend fun updateConversation(conv: Conversation) = withContext(Dispatchers.IO) {
        val newList = _conversations.value.map { if (it.id == conv.id) conv else it }
        writeConversationsAtomic(newList)
        _conversations.value = newList
    }

    suspend fun deleteConversation(convId: String) = withContext(Dispatchers.IO) {
        val newList = _conversations.value.filter { it.id != convId }
        writeConversationsAtomic(newList)
        _conversations.value = newList
        runCatching { messagesFile(convId).delete() }
            .onFailure { android.util.Log.w("ConversationStore", "删除 messages_$convId.json 失败", it) }
        getMessageLock(convId).withLock {
            messagesCache.remove(convId)
            messagesFlows.remove(convId)?.value = emptyList()
        }
    }

    /**
     * 批量删除多个会话：单次内存过滤 + 单次写盘，避免 N 个会话触发 N 次整文件重写。
     */
    suspend fun deleteConversations(convIds: List<String>) = withContext(Dispatchers.IO) {
        if (convIds.isEmpty()) return@withContext
        val target = convIds.toSet()
        val newList = _conversations.value.filterNot { it.id in target }
        writeConversationsAtomic(newList)
        _conversations.value = newList
        convIds.forEach { convId ->
            runCatching { messagesFile(convId).delete() }
                .onFailure { android.util.Log.w("ConversationStore", "删除 messages_$convId.json 失败", it) }
            getMessageLock(convId).withLock {
                messagesCache.remove(convId)
                messagesFlows.remove(convId)?.value = emptyList()
            }
        }
    }

    /** @return 是否写盘成功 */
    suspend fun appendMessage(message: Message): Boolean = withContext(Dispatchers.IO) {
        val convId = message.conversationId

        if (!messagesCache.containsKey(convId)) {
            getLoadLock(convId).withLock {
                if (!messagesCache.containsKey(convId)) {
                    val messages = loadMessagesFromDisk(convId)
                    messagesCache.getOrPut(convId) { messages.toMutableList() }
                    messagesFlows.getOrPut(convId) { MutableStateFlow(messages) }
                }
            }
        }

        getMessageLock(convId).withLock {
            val cache = messagesCache.getOrPut(convId) { mutableListOf() }
            val existingIdx = cache.indexOfFirst { it.id == message.id }
            if (existingIdx >= 0) {
                cache[existingIdx] = message
            } else {
                cache.add(message)
            }
            val snapshot = cache.toList()
            messagesFlows[convId]?.value = snapshot
            if (!writeMessagesAtomic(convId, snapshot)) return@withContext false
        }

        val preview = message.content
            .replace("\n", " ")
            .trim()
            .let {
                if (it.length > QuiddityConstants.MESSAGE_PREVIEW_MAX_CHARS)
                    it.take(QuiddityConstants.MESSAGE_PREVIEW_MAX_CHARS) + "…"
                else it
            }
        val now = System.currentTimeMillis()
        // 流式中的 AI 消息尚未定型：不更新会话预览与 updatedAt，
        // 避免每个 token 都重写 conversations.json（预览内容也是中间态）
        if (!message.isStreaming) {
            val newList = _conversations.value.map { conv ->
                if (conv.id == convId) conv.copy(lastMessagePreview = preview, updatedAt = now)
                else conv
            }
            if (!writeConversationsAtomic(newList)) return@withContext false
            _conversations.value = newList
        }
        true
    }

    /** @return 是否写盘成功 */
    suspend fun updateMessage(message: Message): Boolean = withContext(Dispatchers.IO) {
        val convId = message.conversationId
        getMessageLock(convId).withLock {
            val cache = messagesCache[convId] ?: return@withLock false
            val idx = cache.indexOfFirst { it.id == message.id }
            if (idx < 0) return@withLock false
            cache[idx] = message
            val snapshot = cache.toList()
            messagesFlows[convId]?.value = snapshot
            writeMessagesAtomic(convId, snapshot)
        }
    }

    /** @return 是否写盘成功 */
    suspend fun replaceMessages(convId: String, messages: List<Message>): Boolean = withContext(Dispatchers.IO) {
        getMessageLock(convId).withLock {
            messagesCache[convId] = messages.toMutableList()
            messagesFlows[convId]?.value = messages
            if (!writeMessagesAtomic(convId, messages)) return@withContext false
        }
        val preview = messages.lastOrNull()?.content
            ?.replace("\n", " ")
            ?.trim()
            ?.let {
                if (it.length > QuiddityConstants.MESSAGE_PREVIEW_MAX_CHARS)
                    it.take(QuiddityConstants.MESSAGE_PREVIEW_MAX_CHARS) + "…"
                else it
            }
            .orEmpty()
        val now = System.currentTimeMillis()
        val newList = _conversations.value.map { conv ->
            if (conv.id == convId) conv.copy(lastMessagePreview = preview, updatedAt = now)
            else conv
        }
        if (!writeConversationsAtomic(newList)) return@withContext false
        _conversations.value = newList
        true
    }

    suspend fun exportAll(): Map<String, List<Message>> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, List<Message>>()
        _conversations.value.forEach { conv ->
            result[conv.id] = loadMessagesFromDisk(conv.id)
        }
        result
    }

    suspend fun importAll(
        conversations: List<Conversation>,
        messages: Map<String, List<Message>>
    ) = withContext(Dispatchers.IO) {
        val merged = (conversations + _conversations.value)
            .distinctBy { it.id }
            .sortedWith(compareByDescending<Conversation> { it.pinned }.thenByDescending { it.updatedAt })
        writeConversationsAtomic(merged)
        _conversations.value = merged
        messages.forEach { (convId, msgs) ->
            getMessageLock(convId).withLock {
                messagesCache[convId] = msgs.toMutableList()
                messagesFlows[convId]?.value = msgs
                writeMessagesAtomic(convId, msgs)
            }
        }
    }

    /**
     * 替换式导入：删除全部现有会话与消息，写入导入数据。
     *
     * 与 [importAll]（合并模式）互补：用户选择"替换现有数据"时调用。
     *
     * 3.4 原子性要求：替换模式**先备份再替换**——先把本机数据文件改名 `.bak`，
     * 新数据写盘成功后再删备份；失败则回滚 `.bak`，保证替换中途崩溃不丢本机数据。
     */
    suspend fun replaceAll(
        conversations: List<Conversation>,
        messages: Map<String, List<Message>>
    ) = withContext(Dispatchers.IO) {
        val backedUpFiles = mutableListOf<Pair<File, File>>()
        try {
            // 1. 备份：本机数据文件改名 .bak（conversations.json + 各 messages_<id>.json）
            val convFile = conversationsFile
            if (convFile.exists()) {
                val bak = File(dataDir, "conversations.json.bak")
                if (convFile.renameTo(bak)) {
                    backedUpFiles += convFile to bak
                }
            }
            _conversations.value.forEach { conv ->
                getMessageLock(conv.id).withLock {
                    val file = messagesFile(conv.id)
                    if (file.exists()) {
                        val bak = File(dataDir, "messages_${conv.id}.json.bak")
                        if (file.renameTo(bak)) {
                            backedUpFiles += file to bak
                        }
                    }
                    messagesCache.remove(conv.id)
                    messagesFlows.remove(conv.id)?.value = emptyList()
                }
            }

            // 2. 写入新会话列表
            val sorted = conversations
                .sortedWith(compareByDescending<Conversation> { it.pinned }.thenByDescending { it.updatedAt })
            writeConversationsAtomic(sorted)
            _conversations.value = sorted

            // 3. 写入新消息
            messages.forEach { (convId, msgs) ->
                getMessageLock(convId).withLock {
                    messagesCache[convId] = msgs.toMutableList()
                    messagesFlows[convId]?.value = msgs
                    writeMessagesAtomic(convId, msgs)
                }
            }

            // 4. 成功：删除备份
            backedUpFiles.forEach { (_, bak) ->
                runCatching { bak.delete() }
            }
        } catch (t: Throwable) {
            // 5. 失败：回滚 .bak，恢复本机数据
            backedUpFiles.forEach { (orig, bak) ->
                runCatching {
                    if (bak.exists()) {
                        orig.delete()
                        bak.renameTo(orig)
                    }
                }
            }
            _conversations.value = readConversations()
            messagesCache.clear()
            messagesFlows.values.forEach { it.value = emptyList() }
            // 回滚后重新从磁盘加载消息，恢复内存态与磁盘一致，避免 UI 显示空聊天直到重启
            _conversations.value.forEach { conv ->
                getMessageLock(conv.id).withLock {
                    val restored = loadMessagesFromDisk(conv.id)
                    messagesCache[conv.id] = restored.toMutableList()
                    messagesFlows.getOrPut(conv.id) { MutableStateFlow(restored) }.value = restored
                }
            }
            throw t
        }
    }
}
