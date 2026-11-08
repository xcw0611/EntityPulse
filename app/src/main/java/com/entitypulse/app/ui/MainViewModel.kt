package com.entitypulse.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.entitypulse.app.backup.BackupManager
import com.entitypulse.app.data.AppDatabase
import com.entitypulse.app.data.model.*
import com.entitypulse.app.network.*
import com.entitypulse.app.notification.NotificationHelper
import com.entitypulse.app.util.AppLogger
import com.entitypulse.app.repository.FeedRepository
import com.entitypulse.app.sync.SyncWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    val nodeManager = NodeManager(application)
    val repository = FeedRepository(database, nodeManager)
    val backupManager = BackupManager(application, database)
    private val notificationHelper = NotificationHelper(application)

    init {
        TranslationManager.customClient = nodeManager.getOkHttpClient()
    }

    // 主体列表与统计流
    val entities: StateFlow<List<EntityWithUnreadCount>> = repository.observeEntitiesWithStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 综合信息流过滤状态
    private val _selectedEntityFilterId = MutableStateFlow<Long?>(null)
    val selectedEntityFilterId: StateFlow<Long?> = _selectedEntityFilterId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showUnreadOnly = MutableStateFlow(false)
    val showUnreadOnly: StateFlow<Boolean> = _showUnreadOnly.asStateFlow()

    private val _timeRangeFilter = MutableStateFlow(nodeManager.defaultTimeRange)
    val timeRangeFilter: StateFlow<TimeRangeFilter> = _timeRangeFilter.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val feeds: StateFlow<List<FeedItemWithMeta>> = combine(
        _selectedEntityFilterId.flatMapLatest { id -> repository.observeFeeds(id) },
        _searchQuery,
        _showUnreadOnly,
        _timeRangeFilter
    ) { items, query, unreadOnly, timeFilter ->
        val now = System.currentTimeMillis()
        val cutoff = if (timeFilter == TimeRangeFilter.ALL) 0L else (now - timeFilter.durationMillis)
        items.filter { item ->
            val matchTime = item.item.publishedAt >= cutoff
            val matchQuery = if (query.isBlank()) true else {
                item.item.title.contains(query, ignoreCase = true) ||
                        item.item.contentPlainText.contains(query, ignoreCase = true) ||
                        item.entityName.contains(query, ignoreCase = true)
            }
            val matchUnread = if (unreadOnly) !item.item.isRead else true
            matchTime && matchQuery && matchUnread
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 刷新状态与用户消息
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    fun selectEntityFilter(entityId: Long?) {
        _selectedEntityFilterId.value = entityId
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleShowUnreadOnly() {
        _showUnreadOnly.value = !_showUnreadOnly.value
    }

    fun setTimeRangeFilter(filter: TimeRangeFilter) {
        _timeRangeFilter.value = filter
        nodeManager.defaultTimeRange = filter
    }

    suspend fun searchIpCandidates(query: String): List<com.entitypulse.app.network.IpCandidate> {
        return com.entitypulse.app.network.OnlineEntityLocator.searchIpCandidates(query, nodeManager.getOkHttpClient())
    }

    suspend fun testCandidateChannel(feedUrl: String): Result<ParsedFeedItem> {
        return runCatching { repository.testChannel(feedUrl) }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val results = repository.syncAllChannels()
                var totalNew = 0
                for ((channel, items) in results) {
                    totalNew += items.size
                    val entity = database.entityDao().getById(channel.entityId) ?: continue
                    for (item in items.take(2)) {
                        notificationHelper.showFeedNotification(entity, channel, item)
                    }
                }
                _userMessage.emit("同步完成，发现 $totalNew 条新动态")
            } catch (e: Exception) {
                _userMessage.emit("同步出错: ${e.localizedMessage ?: "网络异常"}")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun addEntity(name: String, bio: String?, avatarUrl: String?, isVip: Boolean, category: String) {
        viewModelScope.launch {
            if (name.isBlank()) return@launch
            repository.createEntity(
                name = name.trim(),
                bio = bio?.trim()?.ifBlank { null },
                avatarUrl = avatarUrl?.trim()?.ifBlank { null },
                isVip = isVip,
                category = category.trim().ifBlank { "默认" }
            )
            _userMessage.emit("已创建主体: $name")
        }
    }

    fun addEntityWithChannels(
        name: String,
        bio: String?,
        avatarUrl: String?,
        isVip: Boolean,
        category: String,
        channels: List<PredefinedChannel>
    ) {
        viewModelScope.launch {
            if (name.isBlank()) return@launch
            val entityId = repository.createEntity(
                name = name.trim(),
                bio = bio?.trim()?.ifBlank { null },
                avatarUrl = avatarUrl?.trim()?.ifBlank { null },
                isVip = isVip,
                category = category.trim().ifBlank { "默认" }
            )
            for (ch in channels) {
                repository.addChannel(
                    entityId = entityId,
                    platformType = ch.platformType,
                    identifier = ch.identifier,
                    resolvedFeedUrl = ch.feedPathOrUrl,
                    title = ch.title
                )
            }
            _userMessage.emit("已创建主体「$name」并自动绑定 ${channels.size} 个社交账号！正在拉取动态...")
            refreshAll()
        }
    }

    fun updateProxySettings(enabled: Boolean, host: String, port: Int, type: String = "HTTP") {
        nodeManager.isProxyEnabled = enabled
        nodeManager.proxyHost = host
        nodeManager.proxyPort = port
        nodeManager.proxyType = type
        TranslationManager.customClient = nodeManager.getOkHttpClient()
        viewModelScope.launch {
            _userMessage.emit(if (enabled) "已开启统一代理: $type $host:$port" else "已停用应用代理")
        }
    }


    suspend fun testCandidateChannel(channel: PredefinedChannel): Result<ParsedFeedItem> {
        return runCatching {
            repository.testChannel(channel.feedPathOrUrl)
        }
    }

    fun testProxyConnection(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val res = nodeManager.testProxyConnectivity()
            onResult(res.first, res.second)
        }
    }

    fun deleteEntity(entity: EntityRecord) {
        viewModelScope.launch {
            repository.deleteEntity(entity)
            _userMessage.emit("已删除主体: ${entity.name}")
        }
    }

    fun toggleEntityVip(id: Long, currentVip: Boolean) {
        viewModelScope.launch {
            repository.toggleVip(id, !currentVip)
        }
    }

    fun updateEntityAvatar(id: Long, avatarUrl: String?) {
        viewModelScope.launch {
            repository.updateEntityAvatar(id, avatarUrl)
            _userMessage.emit("主体封面图片已更新")
        }
    }

    suspend fun searchCoverImage(query: String): String? {
        return com.entitypulse.app.network.OnlineEntityLocator.fetchIpCoverImage(query, nodeManager.getOkHttpClient())
    }

    fun toggleEntityNotify(id: Long, currentEnabled: Boolean) {
        viewModelScope.launch {
            repository.toggleNotify(id, !currentEnabled)
        }
    }

    fun resolvePlatformInput(input: String): ResolvedPlatform {
        return PlatformResolver.resolve(input)
    }

    suspend fun testChannelConnection(resolvedFeedUrl: String): ParsedFeedItem {
        return repository.testChannel(resolvedFeedUrl)
    }

    fun addChannelToEntity(
        entityId: Long,
        platformType: PlatformType,
        identifier: String,
        resolvedFeedUrl: String,
        title: String
    ) {
        viewModelScope.launch {
            repository.addChannel(
                entityId = entityId,
                platformType = platformType,
                identifier = identifier,
                resolvedFeedUrl = resolvedFeedUrl,
                title = title
            )
            _userMessage.emit("已添加平台渠道: $title")
            // 立即尝试同步该新频道
            val channels = database.channelDao().getByEntityId(entityId)
            val newChannel = channels.lastOrNull()
            if (newChannel != null) {
                repository.syncChannel(newChannel)
            }
        }
    }

    fun deleteChannel(channel: SourceChannelRecord) {
        viewModelScope.launch {
            repository.deleteChannel(channel)
            _userMessage.emit("已移除渠道: ${channel.title}")
        }
    }

    fun toggleChannelEnabled(channel: SourceChannelRecord) {
        viewModelScope.launch {
            repository.toggleChannel(channel.id, !channel.isEnabled)
        }
    }

    fun markItemRead(id: Long, isRead: Boolean = true) {
        viewModelScope.launch {
            repository.markAsRead(id, isRead)
        }
    }

    fun markAllReadForEntity(entityId: Long) {
        viewModelScope.launch {
            repository.markAllAsReadForEntity(entityId)
            _userMessage.emit("已全部标为已读")
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            repository.markAllAsRead()
            _userMessage.emit("已将全部动态标为已读")
        }
    }

    fun toggleItemBookmark(id: Long, currentBookmarked: Boolean) {
        viewModelScope.launch {
            repository.toggleBookmark(id, !currentBookmarked)
        }
    }

    fun sendTestNotification() {
        viewModelScope.launch {
            val dummyEntity = EntityRecord(name = "Team Cherry", isVip = true)
            val dummyChannel = SourceChannelRecord(
                entityId = 0,
                platformType = PlatformType.TWITTER,
                identifier = "@TeamCherryGames",
                resolvedFeedUrl = "",
                title = "X / Twitter"
            )
            val dummyItem = FeedItemRecord(
                entityId = 0,
                channelId = 0,
                guid = "test_${System.currentTimeMillis()}",
                title = "Hollow Knight: Silksong 开发进展更新！",
                contentPlainText = "我们正在全力以赴冲刺最后阶段，非常感谢各位玩家的耐心等待！更多演示即将公布。",
                publishedAt = System.currentTimeMillis(),
                originalUrl = "https://x.com/TeamCherryGames"
            )
            notificationHelper.showFeedNotification(dummyEntity, dummyChannel, dummyItem)
            _userMessage.emit("测试通知已发送")
        }
    }

    fun updateSyncInterval(minutes: Int) {
        nodeManager.syncIntervalMinutes = minutes
        SyncWorker.schedulePeriodicSync(getApplication(), minutes.toLong())
        viewModelScope.launch {
            _userMessage.emit("已更新后台同步间隔为 $minutes 分钟")
        }
    }

    fun exportBackupJson(uri: Uri) {
        viewModelScope.launch {
            backupManager.exportToJson(uri).onSuccess {
                _userMessage.emit("配置备份导出成功！")
            }.onFailure {
                _userMessage.emit("导出失败: ${it.localizedMessage}")
            }
        }
    }

    fun importBackupJson(uri: Uri) {
        viewModelScope.launch {
            backupManager.importFromJson(uri).onSuccess { count ->
                _userMessage.emit("成功导入 $count 个主体配置！正在同步最新动态...")
                refreshAll()
            }.onFailure {
                _userMessage.emit("导入失败: ${it.localizedMessage}")
            }
        }
    }

    fun exportOpml(uri: Uri) {
        viewModelScope.launch {
            backupManager.exportToOpml(uri).onSuccess {
                _userMessage.emit("OPML 订阅文件导出成功！")
            }.onFailure {
                _userMessage.emit("导出失败: ${it.localizedMessage}")
            }
        }
    }

    fun getRecentLogs(): String {
        return AppLogger.getRecentLogsText()
    }

    fun getLogFilePath(): String {
        return AppLogger.getLogFilePath()
    }

    fun clearAppLogs() {
        AppLogger.clearLogs()
        viewModelScope.launch {
            _userMessage.emit("诊断日志已清空")
        }
    }
}
