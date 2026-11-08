package com.entitypulse.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.entitypulse.desktop.data.DatabaseManager
import com.entitypulse.desktop.data.model.*
import com.entitypulse.desktop.network.NodeManager
import com.entitypulse.desktop.repository.FeedRepository
import com.entitypulse.desktop.ui.components.DesktopEntityList
import com.entitypulse.desktop.ui.components.DesktopFeedCard
import com.entitypulse.desktop.ui.components.DesktopInspectorPanel
import com.entitypulse.desktop.ui.dialogs.*
import com.entitypulse.desktop.ui.theme.DarkBackground
import com.entitypulse.desktop.ui.theme.DarkBorder
import com.entitypulse.desktop.ui.theme.DarkSurfaceElevated
import com.entitypulse.desktop.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDesktopView() {
    val coroutineScope = rememberCoroutineScope()

    // Singletons
    val nodeManager = remember { NodeManager() }
    val database = remember { DatabaseManager() }
    val repository = remember { FeedRepository(database, nodeManager) }

    // States
    val entities by database.entitiesState.collectAsState()
    var selectedEntityId by remember { mutableStateOf<Long?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showUnreadOnly by remember { mutableStateOf(false) }
    var timeRangeFilter by remember { mutableStateOf(nodeManager.defaultTimeRange) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Feeds state
    var allFeeds by remember { mutableStateOf<List<FeedItemWithMeta>>(emptyList()) }
    var channelsForSelected by remember { mutableStateOf<List<SourceChannelRecord>>(emptyList()) }
    val feedListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val showScrollToTop by remember { derivedStateOf { feedListState.firstVisibleItemIndex > 1 } }

    // Dialog flags
    var showAddEntityDialog by remember { mutableStateOf(false) }
    var showAddChannelDialog by remember { mutableStateOf(false) }
    var showEditAvatarDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // User message banner
    var userMessage by remember { mutableStateOf<String?>(null) }

    // Clipboard suggestion banner
    var clipboardSuggestion by remember { mutableStateOf<String?>(null) }

    // Reload feeds when filters or entity selection change
    fun reloadFeeds() {
        coroutineScope.launch {
            val list = repository.getFeeds(selectedEntityId, limit = 200)
            allFeeds = list
            if (selectedEntityId != null) {
                channelsForSelected = repository.getChannelsForEntity(selectedEntityId!!)
            } else {
                channelsForSelected = emptyList()
            }
        }
    }

    LaunchedEffect(selectedEntityId) {
        reloadFeeds()
    }

    // Filter feeds according to query, unread, and time
    val displayedFeeds = remember(allFeeds, searchQuery, showUnreadOnly, timeRangeFilter) {
        val now = System.currentTimeMillis()
        val cutoff = if (timeRangeFilter == TimeRangeFilter.ALL) 0L else (now - timeRangeFilter.durationMillis)
        allFeeds.filter { feedMeta ->
            val matchTime = feedMeta.item.publishedAt >= cutoff
            val matchQuery = if (searchQuery.isBlank()) true else {
                feedMeta.item.title.contains(searchQuery, ignoreCase = true) ||
                        feedMeta.item.contentPlainText.contains(searchQuery, ignoreCase = true) ||
                        feedMeta.entityName.contains(searchQuery, ignoreCase = true)
            }
            val matchUnread = if (showUnreadOnly) !feedMeta.item.isRead else true
            matchTime && matchQuery && matchUnread
        }
    }

    val selectedEntityRecord = remember(entities, selectedEntityId) {
        entities.firstOrNull { it.record.id == selectedEntityId }?.record
    }

    // Clipboard poller (checks every 3 seconds for new social profile URL)
    LaunchedEffect(Unit) {
        var lastClip = ""
        while (true) {
            delay(3000)
            val clip = DesktopBrowserHelper.getClipboardText()?.trim() ?: ""
            if (clip.isNotBlank() && clip != lastClip && (clip.contains("x.com/") || clip.contains("twitter.com/") || clip.contains("youtube.com/") || clip.contains("bilibili.com/"))) {
                lastClip = clip
                clipboardSuggestion = clip
            }
        }
    }

    fun showToast(msg: String) {
        userMessage = msg
        coroutineScope.launch {
            delay(4000)
            if (userMessage == msg) userMessage = null
        }
    }

    // Refresh all channels
    fun refreshAll() {
        coroutineScope.launch {
            isRefreshing = true
            try {
                val results = repository.syncAllChannels()
                val totalNew = results.sumOf { it.second.size }
                reloadFeeds()
                showToast("同步完成，拉取到 $totalNew 条全新动态")
            } catch (e: Exception) {
                showToast("同步出错: ${e.message}")
            } finally {
                isRefreshing = false
            }
        }
    }

    // 3-Column Layout
    Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Column: Entities List
            DesktopEntityList(
                entities = entities,
                selectedEntityId = selectedEntityId,
                nodeManager = nodeManager,
                isRefreshing = isRefreshing,
                onSelectEntity = { id -> selectedEntityId = id },
                onAddEntityClick = { showAddEntityDialog = true },
                onOpenSettingsClick = { showSettingsDialog = true },
                onRefreshAllClick = { refreshAll() },
                onMarkAllReadClick = {
                    coroutineScope.launch {
                        repository.markAllAsRead()
                        reloadFeeds()
                        showToast("已将所有动态标记为已读")
                    }
                }
            )

            // Center Column: Feeds Stream
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                // Header Bar: Title + Search + Filters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (selectedEntityRecord != null) "「${selectedEntityRecord.name}」动态流" else "全网主体综合动态流",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "当前显示 ${displayedFeeds.size} 条动态",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Search box
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("在动态流中搜索关键词...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFF1F5F9),
                            unfocusedTextColor = Color(0xFFF1F5F9)
                        ),
                        modifier = Modifier.width(260.dp).height(50.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Mark all read button
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                if (selectedEntityId != null) {
                                    repository.markAllAsReadForEntity(selectedEntityId!!)
                                } else {
                                    repository.markAllAsRead()
                                }
                                reloadFeeds()
                                showToast("已标记为已读")
                            }
                        }
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = "全部已读", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // Refresh current button
                    IconButton(
                        onClick = { refreshAll() },
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Time filter chips & Unread switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TimeRangeFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = timeRangeFilter == filter,
                            onClick = { timeRangeFilter = filter },
                            label = { Text(filter.displayName) }
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    FilterChip(
                        selected = showUnreadOnly,
                        onClick = { showUnreadOnly = !showUnreadOnly },
                        label = { Text("仅看未读") },
                        leadingIcon = {
                            Icon(
                                imageVector = if (showUnreadOnly) Icons.Default.Check else Icons.Default.FilterList,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Feeds Waterfall / Stream
                if (displayedFeeds.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.padding(32.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Inbox,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (allFeeds.isEmpty()) "暂无动态或尚未同步" else "没有符合当前筛选条件的动态",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "可尝试调整上方时间跨度范围、关闭「仅看未读」或点击右上角「同步」拉取最新内容。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        LazyColumn(
                            state = feedListState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(bottom = 36.dp)
                        ) {
                            items(displayedFeeds, key = { it.item.id }) { feedMeta ->
                                DesktopFeedCard(
                                    feedMeta = feedMeta,
                                    nodeManager = nodeManager,
                                    onToggleRead = { isRead ->
                                        coroutineScope.launch {
                                            repository.markAsRead(feedMeta.item.id, isRead)
                                            reloadFeeds()
                                        }
                                    },
                                    onToggleBookmark = { isBookmarked ->
                                        coroutineScope.launch {
                                            repository.toggleBookmark(feedMeta.item.id, isBookmarked)
                                            reloadFeeds()
                                        }
                                    },
                                    onSelectEntity = {
                                        selectedEntityId = feedMeta.item.entityId
                                    }
                                )
                            }
                        }

                        // 回到顶部悬浮按钮
                        AnimatedVisibility(
                            visible = showScrollToTop,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 16.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    coroutineScope.launch {
                                        feedListState.animateScrollToItem(0)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "回到顶部",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("回到顶部", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Right Column: Entity Inspector & Channel Manager
            DesktopInspectorPanel(
                selectedEntity = selectedEntityRecord,
                channels = channelsForSelected,
                nodeManager = nodeManager,
                onEditAvatarClick = { showEditAvatarDialog = true },
                onToggleVip = { isVip ->
                    selectedEntityId?.let { id ->
                        coroutineScope.launch {
                            repository.toggleVip(id, isVip)
                            reloadFeeds()
                        }
                    }
                },
                onToggleNotify = { isNotify ->
                    selectedEntityId?.let { id ->
                        coroutineScope.launch {
                            repository.toggleNotify(id, isNotify)
                            reloadFeeds()
                        }
                    }
                },
                onAddChannelClick = { showAddChannelDialog = true },
                onDeleteChannel = { channelId ->
                    coroutineScope.launch {
                        repository.deleteChannel(channelId)
                        reloadFeeds()
                        showToast("已删除该频道")
                    }
                },
                onToggleChannel = { channelId, enabled ->
                    coroutineScope.launch {
                        repository.toggleChannel(channelId, enabled)
                        reloadFeeds()
                    }
                },
                onTestChannel = { feedUrl ->
                    repository.testChannel(feedUrl)
                },
                onDeleteEntity = {
                    selectedEntityId?.let { id ->
                        coroutineScope.launch {
                            repository.deleteEntity(id)
                            selectedEntityId = null
                            reloadFeeds()
                            showToast("已取消关注并删除主体")
                        }
                    }
                }
            )
        }

        // Floating Toast Message Banner
        AnimatedVisibility(
            visible = userMessage != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    text = userMessage ?: "",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }

        // Clipboard Suggestion Pill (Floating at top-right of center)
        AnimatedVisibility(
            visible = clipboardSuggestion != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "发现剪贴板中的社媒链接: ${clipboardSuggestion?.take(36)}...",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            clipboardSuggestion = null
                            showAddChannelDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("一键绑定", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(
                        onClick = { clipboardSuggestion = null },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "忽略", modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }

    // --- Dialogs ---

    if (showAddEntityDialog) {
        AddEntityDesktopDialog(
            nodeManager = nodeManager,
            onDismiss = { showAddEntityDialog = false },
            onConfirm = { name, bio, avatarUrl, isVip, category, channels ->
                coroutineScope.launch {
                    val newEntityId = repository.createEntity(
                        name = name,
                        bio = bio,
                        avatarUrl = avatarUrl,
                        isVip = isVip,
                        category = category
                    )
                    for (ch in channels) {
                        repository.addChannel(
                            entityId = newEntityId,
                            platformType = ch.platformType,
                            identifier = ch.identifier,
                            resolvedFeedUrl = ch.feedPathOrUrl,
                            title = ch.title
                        )
                    }
                    selectedEntityId = newEntityId
                    reloadFeeds()
                    showToast("已成功添加主体「$name」并绑定相关渠道")
                }
            }
        )
    }

    if (showAddChannelDialog && selectedEntityRecord != null) {
        AddChannelDesktopDialog(
            entityName = selectedEntityRecord.name,
            onDismiss = { showAddChannelDialog = false },
            onTestConnection = { feedUrl -> repository.testChannel(feedUrl) },
            onConfirm = { platformType, identifier, resolvedFeedUrl, title ->
                coroutineScope.launch {
                    repository.addChannel(
                        entityId = selectedEntityRecord.id,
                        platformType = platformType,
                        identifier = identifier,
                        resolvedFeedUrl = resolvedFeedUrl,
                        title = title
                    )
                    reloadFeeds()
                    showToast("已成功为「${selectedEntityRecord.name}」绑定频道「$title」")
                }
            }
        )
    }

    if (showEditAvatarDialog && selectedEntityRecord != null) {
        EditAvatarDesktopDialog(
            entityName = selectedEntityRecord.name,
            currentAvatarUrl = selectedEntityRecord.avatarUrl,
            nodeManager = nodeManager,
            onDismiss = { showEditAvatarDialog = false },
            onConfirm = { newAvatar ->
                coroutineScope.launch {
                    repository.updateEntityAvatar(selectedEntityRecord.id, newAvatar)
                    reloadFeeds()
                    showToast("主体封面已更新")
                }
            }
        )
    }

    if (showSettingsDialog) {
        SettingsDesktopDialog(
            nodeManager = nodeManager,
            onDismiss = { showSettingsDialog = false },
            onSettingsChanged = {
                timeRangeFilter = nodeManager.defaultTimeRange
                reloadFeeds()
                showToast("配置已更新")
            }
        )
    }
}
