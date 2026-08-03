package com.quiddity.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore

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
 * 会话页 ViewModel 宿主：按会话 ID 管理 [ChatViewModel] 的生命周期。
 *
 * 规则（对应"退出会话释放内存，未完结等完结再释放"）：
 * - 聊天页退出组合时调用 [onScreenExit]：
 *   - 会话无未完结任务（流式 / 压缩 / 发送延迟）→ 立即释放；
 *   - 有未完结任务 → 标记"待释放"，任务完结后由 [ChatViewModel] 回调 [onConversationIdle] 再释放；
 * - 每个会话持有独立的 [ViewModelStore]，释放 = 移除并 [ViewModelStore.clear]
 *   （取消 viewModelScope、执行 onCleared）；
 * - 宿主随 Activity 销毁时全部释放；
 * - 未退出的会话：宿主不主动释放，任务完结后 VM 仍保留（下次进入无需重建）。
 */
class ChatViewModelHost(
    private val creator: (convId: String, onIdle: (String) -> Unit) -> ChatViewModel
) : ViewModel() {

    private val stores = mutableMapOf<String, ViewModelStore>()
    private val releaseRequests = mutableSetOf<String>()

    /** 获取（或创建）指定会话的 ViewModel。 */
    fun get(convId: String): ChatViewModel = vmOf(convId)

    /** 聊天页退出组合时调用。 */
    fun onScreenExit(convId: String) {
        val vm = stores[convId]?.let { vmOf(convId) } ?: return
        if (vm.hasActiveWork()) {
            releaseRequests += convId
        } else {
            release(convId)
        }
    }

    /** 会话全部任务完结（流式 / 压缩 / 发送延迟均空闲）时由 ChatViewModel 回调。 */
    private fun onConversationIdle(convId: String) {
        if (convId in releaseRequests) {
            release(convId)
        }
    }

    private fun release(convId: String) {
        releaseRequests -= convId
        stores.remove(convId)?.clear()
    }

    /** 按会话 ID 从独立 ViewModelStore 中获取（或创建）ChatViewModel。 */
    private fun vmOf(convId: String): ChatViewModel {
        val store = stores.getOrPut(convId) { ViewModelStore() }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                creator(convId) { id -> onConversationIdle(id) } as T
        }
        return ViewModelProvider(store, factory)[ChatViewModel::class.java]
    }

    override fun onCleared() {
        stores.values.forEach { it.clear() }
        stores.clear()
        releaseRequests.clear()
    }
}

class ChatViewModelHostFactory(
    private val creator: (convId: String, onIdle: (String) -> Unit) -> ChatViewModel
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ChatViewModelHost(creator) as T
}
