package com.quiddity.app.data.local

import android.content.Context
import android.util.AtomicFile
import com.quiddity.app.data.model.Conversation
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
            emptyList()
        }
    }

    private fun writeConversationsAtomic(list: List<Conversation>) {
        val text = json.encodeToString(ListSerializer(Conversation.serializer()), list)
        runCatching {
            var stream = conversationsAtomicFile.startWrite()
            try {
                stream.write(text.toByteArray(Charsets.UTF_8))
                stream.flush()
                conversationsAtomicFile.finishWrite(stream)
            } catch (t: Throwable) {
                conversationsAtomicFile.failWrite(stream)
                throw t
            }
        }.onFailure { android.util.Log.e("ConversationStore", "写入 conversations.json 失败", it) }
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
            emptyList()
        }
    }

    private fun writeMessagesAtomic(convId: String, messages: List<Message>) {
        val af = atomicMessagesFile(convId)
        val text = json.encodeToString(ListSerializer(Message.serializer()), messages)
        runCatching {
            var stream = af.startWrite()
            try {
                stream.write(text.toByteArray(Charsets.UTF_8))
                stream.flush()
                af.finishWrite(stream)
            } catch (t: Throwable) {
                af.failWrite(stream)
                throw t
            }
        }.onFailure { android.util.Log.e("ConversationStore", "写入 messages_$convId.json 失败", it) }
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

    suspend fun appendMessage(message: Message) = withContext(Dispatchers.IO) {
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
            writeMessagesAtomic(convId, snapshot)
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
        val newList = _conversations.value.map { conv ->
            if (conv.id == convId) conv.copy(lastMessagePreview = preview, updatedAt = now)
            else conv
        }
        writeConversationsAtomic(newList)
        _conversations.value = newList
    }

    suspend fun updateMessage(message: Message) = withContext(Dispatchers.IO) {
        val convId = message.conversationId
        getMessageLock(convId).withLock {
            val cache = messagesCache[convId] ?: return@withLock
            val idx = cache.indexOfFirst { it.id == message.id }
            if (idx < 0) return@withLock
            cache[idx] = message
            val snapshot = cache.toList()
            messagesFlows[convId]?.value = snapshot
            writeMessagesAtomic(convId, snapshot)
        }
    }

    suspend fun replaceMessages(convId: String, messages: List<Message>) = withContext(Dispatchers.IO) {
        getMessageLock(convId).withLock {
            messagesCache[convId] = messages.toMutableList()
            messagesFlows[convId]?.value = messages
            writeMessagesAtomic(convId, messages)
        }
        val preview = messages.lastOrNull()?.content
            ?.replace("\n", " ")
            ?.trim()
            ?.let { if (it.length > 60) it.take(60) + "…" else it }
            .orEmpty()
        val now = System.currentTimeMillis()
        val newList = _conversations.value.map { conv ->
            if (conv.id == convId) conv.copy(lastMessagePreview = preview, updatedAt = now)
            else conv
        }
        writeConversationsAtomic(newList)
        _conversations.value = newList
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
}
