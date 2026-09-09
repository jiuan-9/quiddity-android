package com.quiddity.app.data.local

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/*
 * 小应用本地偏好：收藏列表持久化（mini-apps.json，AtomicFile 原子写）。
 * 结构简单、可被未来"最近使用"等扩展字段向后兼容（ignoreUnknownKeys）。
 */
@Serializable
data class MiniAppPrefs(
    val favorites: List<String> = emptyList()
)

class MiniAppStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val dataDir: File by lazy {
        File(context.filesDir, "quiddity-data").apply { mkdirs() }
    }

    private val prefsFile: File by lazy {
        File(dataDir, "mini-apps.json")
    }

    private val prefsAtomicFile: AtomicFile by lazy {
        AtomicFile(prefsFile)
    }

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val writeMutex = Mutex()

    suspend fun load() = withContext(Dispatchers.IO) {
        _favorites.value = readPrefs().favorites.toSet()
    }

    fun isFavorite(id: String): Boolean = id in _favorites.value

    suspend fun toggleFavorite(id: String) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val current = _favorites.value
            val next = if (id in current) current - id else current + id
            writePrefs(MiniAppPrefs(favorites = next.sorted()))
            _favorites.value = next
        }
    }

    private fun readPrefs(): MiniAppPrefs {
        if (!prefsFile.exists()) return MiniAppPrefs()
        return runCatching {
            val text = prefsAtomicFile.openRead().use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
            if (text.isBlank()) MiniAppPrefs()
            else json.decodeFromString(MiniAppPrefs.serializer(), text)
        }.getOrElse {
            android.util.Log.w("MiniAppStore", "读取 mini-apps.json 失败", it)
            MiniAppPrefs()
        }
    }

    private fun writePrefs(prefs: MiniAppPrefs) {
        val text = json.encodeToString(MiniAppPrefs.serializer(), prefs)
        runCatching {
            var stream = prefsAtomicFile.startWrite()
            try {
                stream.write(text.toByteArray(Charsets.UTF_8))
                stream.flush()
                prefsAtomicFile.finishWrite(stream)
            } catch (t: Throwable) {
                prefsAtomicFile.failWrite(stream)
                throw t
            }
        }.onFailure { android.util.Log.e("MiniAppStore", "写入 mini-apps.json 失败", it) }
    }
}
