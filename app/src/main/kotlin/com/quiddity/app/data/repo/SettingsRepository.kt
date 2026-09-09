package com.quiddity.app.data.repo

import com.quiddity.app.data.local.SettingsStore
import com.quiddity.app.data.model.AppSettings
import com.quiddity.app.data.model.ApiCatalogEntry
import com.quiddity.app.util.CryptoUtils
import com.quiddity.app.util.QuiddityConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
// 当前规则：暴露 Flow 给 UI；提供 currentSnapshot 避免初始化期间空状态。
class SettingsRepository(private val store: SettingsStore) {

    private val _snapshot = MutableStateFlow(AppSettings.Default)
    val snapshot: StateFlow<AppSettings> = _snapshot.asStateFlow()

