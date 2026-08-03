package com.quiddity.app.data.repo

import com.quiddity.app.data.local.CharacterStore
import com.quiddity.app.data.model.Character
import kotlinx.coroutines.flow.StateFlow

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
