package com.quiddity.app.data.local

import android.content.Context
import android.util.AtomicFile
import com.quiddity.app.data.model.Character
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 角色库存储（4.1）：`characters.json` 持久化 + CRUD。
 *
 * 角色库只放身份类数据（persona / userPersona / 固定记忆 / 头像）；
 * 会话级数据留在 [com.quiddity.app.data.model.Conversation] 上，不进角色库。
 */
class CharacterStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val dataDir: File by lazy {
        File(context.filesDir, "quiddity-data").apply { mkdirs() }
    }

    private val charactersFile: File by lazy {
        File(dataDir, "characters.json")
    }

    private val charactersAtomicFile: AtomicFile by lazy {
        AtomicFile(charactersFile)
    }

    private val _characters = MutableStateFlow<List<Character>>(emptyList())
    val characters: StateFlow<List<Character>> = _characters.asStateFlow()

    private val writeMutex = Mutex()

    suspend fun loadAll() = withContext(Dispatchers.IO) {
        _characters.value = readCharacters()
    }

    private fun readCharacters(): List<Character> {
        if (!charactersFile.exists()) return emptyList()
        return runCatching {
            val text = charactersAtomicFile.openRead().use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
            if (text.isBlank()) emptyList()
            else json.decodeFromString(ListSerializer(Character.serializer()), text)
        }.getOrElse {
            android.util.Log.w("CharacterStore", "读取 characters.json 失败", it)
            emptyList()
        }
    }

    private fun writeCharactersAtomic(list: List<Character>) {
        val text = json.encodeToString(ListSerializer(Character.serializer()), list)
        runCatching {
            var stream = charactersAtomicFile.startWrite()
            try {
                stream.write(text.toByteArray(Charsets.UTF_8))
                stream.flush()
                charactersAtomicFile.finishWrite(stream)
            } catch (t: Throwable) {
                charactersAtomicFile.failWrite(stream)
                throw t
            }
        }.onFailure { android.util.Log.e("CharacterStore", "写入 characters.json 失败", it) }
    }

    /** 角色库 CRUD：列出全部角色。 */
    suspend fun list(): List<Character> = _characters.value

    /** 角色库 CRUD：按 id 取单个角色。 */
    suspend fun get(id: String): Character? = _characters.value.firstOrNull { it.id == id }

    /** 角色库 CRUD：新增 / 覆盖保存单个角色。 */
    suspend fun save(character: Character) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val newList = _characters.value.map { if (it.id == character.id) character else it } +
                if (_characters.value.any { it.id == character.id }) emptyList() else listOf(character)
            writeCharactersAtomic(newList)
            _characters.value = newList
        }
    }

    /** 角色库 CRUD：按 id 删除角色。 */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val newList = _characters.value.filterNot { it.id == id }
            writeCharactersAtomic(newList)
            _characters.value = newList
        }
    }

    /**
     * 合并导入：按 id 去重，本机优先（已存在不覆盖），新增其余。
     */
    suspend fun mergeAll(characters: List<Character>) = withContext(Dispatchers.IO) {
        if (characters.isEmpty()) return@withContext
        writeMutex.withLock {
            val existing = _characters.value.map { it.id }.toSet()
            val newList = _characters.value + characters.filterNot { it.id in existing }
            writeCharactersAtomic(newList)
            _characters.value = newList
        }
    }

    /**
     * 替换导入：清空现有角色库，写入文件全部角色。
     */
    suspend fun replaceAll(characters: List<Character>) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            writeCharactersAtomic(characters)
            _characters.value = characters
        }
    }

    /**
     * 解析角色档案（2.2）：按 id 返回档案；未命中返回 null（调用方回退 conversation.persona 内嵌副本）。
     */
    suspend fun resolveCharacter(id: String?): Character? {
        if (id.isNullOrBlank()) return null
        return _characters.value.firstOrNull { it.id == id }
    }
}
