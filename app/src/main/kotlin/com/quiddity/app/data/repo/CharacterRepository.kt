package com.quiddity.app.data.repo

import com.quiddity.app.data.local.CharacterStore
import com.quiddity.app.data.model.Character
import kotlinx.coroutines.flow.StateFlow

/**
 * 角色库仓库（4.1）：角色库 CRUD + resolveCharacter(id)。
 *
 * 1.3.0 提供完整数据层接口；角色库 UI（创建 / 编辑 / 邀请进群）随 2.0.0 群聊实体一并加入。
 */
class CharacterRepository(private val store: CharacterStore) {

    val characters: StateFlow<List<Character>> = store.characters

    suspend fun loadAll() = store.loadAll()

    /** 角色库 CRUD：列出全部角色。 */
    suspend fun listCharacters(): List<Character> = store.list()

    /** 角色库 CRUD：按 id 取单个角色。 */
    suspend fun getCharacter(id: String): Character? = store.get(id)

    /** 角色库 CRUD：新增 / 覆盖保存单个角色。 */
    suspend fun saveCharacter(character: Character) = store.save(character)

    /** 角色库 CRUD：按 id 删除角色。 */
    suspend fun deleteCharacter(id: String) = store.delete(id)

    /** 合并导入（本机优先）。 */
    suspend fun mergeCharacters(characters: List<Character>) = store.mergeAll(characters)

    /** 替换导入。 */
    suspend fun replaceCharacters(characters: List<Character>) = store.replaceAll(characters)

    /**
     * 解析角色档案（2.2）：返回档案；未命中返回 null（回退 conversation.persona 内嵌副本）。
     */
    suspend fun resolveCharacter(id: String?): Character? = store.resolveCharacter(id)
}
