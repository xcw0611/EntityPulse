package com.entitypulse.app.ui.screens.entity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.entitypulse.app.data.model.SourceChannelRecord
import com.entitypulse.app.data.model.TimeRangeFilter
import com.entitypulse.app.ui.MainViewModel
import com.entitypulse.app.ui.dialogs.AddChannelDialog
import com.entitypulse.app.ui.dialogs.EditAvatarDialog
import com.entitypulse.app.ui.screens.feed.FeedItemCard
import com.entitypulse.app.ui.screens.feed.PlatformBadge
import com.entitypulse.app.ui.screens.hub.formatRelativeTime
import com.entitypulse.app.ui.theme.VipGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntityDetailScreen(
    entityId: Long,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateToPost: (postId: Long) -> Unit
) {
    val entityWithChannelsFlow = remember(entityId) {
        viewModel.repository.observeEntityWithChannels(entityId)
    }
    val entityWithChannels by entityWithChannelsFlow.collectAsState(initial = null)

    val feedsFlow = remember(entityId) {
        viewModel.repository.observeFeeds(entityId)
    }
    val feeds by feedsFlow.collectAsState(initial = emptyList())
    val timeRangeFilter by viewModel.timeRangeFilter.collectAsState()
    var showTimeMenu by remember { mutableStateOf(false) }

    val filteredFeeds = remember(feeds, timeRangeFilter) {
        val now = System.currentTimeMillis()
        val cutoff = if (timeRangeFilter == TimeRangeFilter.ALL) 0L else (now - timeRangeFilter.durationMillis)
        feeds.filter { it.item.publishedAt >= cutoff }
    }

    var showAddChannelDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showEditAvatarDialog by remember { mutableStateOf(false) }

    val entity = entityWithChannels?.entity

    if (entity == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("主体详情") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entity.name, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.markAllReadForEntity(entityId) }) {
                        Icon(Icons.Default.DoneAll, contentDescription = "全部标为已读")
                    }
                    IconButton(onClick = { showDeleteConfirmDialog = true }) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "删除主体",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 1. 顶部主体资料卡片
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.BottomEnd,
                                modifier = Modifier
                                    .size(68.dp)
                                    .clickable { showEditAvatarDialog = true }
                            ) {
                                if (!entity.avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = entity.avatarUrl,
                                        contentDescription = entity.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                    )
                                } else {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text(
                                            text = entity.name.take(1).uppercase(),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                    }
                                }

                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    tonalElevation = 4.dp,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "修改封面/头像",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = entity.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (entity.isVip) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "VIP",
                                            tint = VipGold,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = entity.category,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                if (!entity.bio.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = entity.bio,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(8.dp))

                        // 开关控制：VIP 强提醒与通知总开关
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = if (entity.isVip) VipGold else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("VIP 核心强提醒", style = MaterialTheme.typography.bodyMedium)
                            }
                            Switch(
                                checked = entity.isVip,
                                onCheckedChange = { viewModel.toggleEntityVip(entity.id, entity.isVip) }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (entity.isNotifyEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                                    contentDescription = null,
                                    tint = if (entity.isNotifyEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("接收更新通知", style = MaterialTheme.typography.bodyMedium)
                            }
                            Switch(
                                checked = entity.isNotifyEnabled,
                                onCheckedChange = { viewModel.toggleEntityNotify(entity.id, entity.isNotifyEnabled) }
                            )
                        }
                    }
                }
            }

            // 2. 绑定的各平台账号/渠道列表
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "关联的社交媒体账号 (${entityWithChannels?.channels?.size ?: 0})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    FilledTonalButton(
                        onClick = { showAddChannelDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("关联新平台")
                    }
                }
            }

            val channels = entityWithChannels?.channels ?: emptyList()
            if (channels.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "尚未绑定任何社交媒体账号",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { showAddChannelDialog = true }) {
                                Text("立即添加 X/B站/Steam/YouTube 等平台")
                            }
                        }
                    }
                }
            } else {
                items(channels, key = { it.id }) { channel ->
                    ChannelItemCard(
                        channel = channel,
                        onToggle = { viewModel.toggleChannelEnabled(channel) },
                        onDelete = { viewModel.deleteChannel(channel) }
                    )
                }
            }

            // 3. 历史动态流列表
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "聚合动态 (${filteredFeeds.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Box {
                        FilledTonalButton(
                            onClick = { showTimeMenu = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(timeRangeFilter.displayName, style = MaterialTheme.typography.labelSmall)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp))
                        }

                        DropdownMenu(
                            expanded = showTimeMenu,
                            onDismissRequest = { showTimeMenu = false }
                        ) {
                            TimeRangeFilter.entries.forEach { filter ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (timeRangeFilter == filter) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                            } else {
                                                Spacer(modifier = Modifier.width(22.dp))
                                            }
                                            Text(
                                                text = if (filter == TimeRangeFilter.DEFAULT) "${filter.displayName} (默认)" else filter.displayName,
                                                fontWeight = if (timeRangeFilter == filter) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.setTimeRangeFilter(filter)
                                        showTimeMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (filteredFeeds.isEmpty()) {
                item {
                    Text(
                        text = if (feeds.isNotEmpty()) "在当前时间范围 (${timeRangeFilter.displayName}) 内暂无动态，可切换为「全部历史」查看" else "暂无抓取到的历史动态，点击右上角刷新进行抓取",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(filteredFeeds, key = { it.item.id }) { itemWithMeta ->
                    FeedItemCard(
                        item = itemWithMeta,
                        onClick = { onNavigateToPost(itemWithMeta.item.id) },
                        onToggleRead = { viewModel.markItemRead(itemWithMeta.item.id, !itemWithMeta.item.isRead) },
                        onToggleBookmark = { viewModel.toggleItemBookmark(itemWithMeta.item.id, itemWithMeta.item.isBookmarked) }
                    )
                }
            }
        }
    }

    if (showAddChannelDialog) {
        AddChannelDialog(
            entityName = entity.name,
            onDismiss = { showAddChannelDialog = false },
            onTestConnection = { feedUrl -> viewModel.testChannelConnection(feedUrl) },
            onConfirm = { platformType, identifier, resolvedFeedUrl, title ->
                viewModel.addChannelToEntity(
                    entityId = entity.id,
                    platformType = platformType,
                    identifier = identifier,
                    resolvedFeedUrl = resolvedFeedUrl,
                    title = title
                )
            }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("确认删除主体？") },
            text = { Text("将同时删除「${entity.name}」名下绑定的所有平台渠道以及相关历史动态。") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        viewModel.deleteEntity(entity)
                        showDeleteConfirmDialog = false
                        onBack()
                    }
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showEditAvatarDialog) {
        EditAvatarDialog(
            entityName = entity.name,
            currentAvatarUrl = entity.avatarUrl,
            onDismiss = { showEditAvatarDialog = false },
            onConfirm = { newUrl ->
                viewModel.updateEntityAvatar(entity.id, newUrl)
            },
            onSearchImage = { name ->
                viewModel.searchCoverImage(name)
            }
        )
    }
}

@Composable
fun ChannelItemCard(
    channel: SourceChannelRecord,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlatformBadge(channel.platformType)
            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = channel.identifier,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                if (channel.lastSyncTime != null) {
                    Text(
                        text = "上次同步: ${formatRelativeTime(channel.lastSyncTime)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                if (!channel.lastError.isNullOrBlank()) {
                    Text(
                        text = "同步异常: ${channel.lastError}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Switch(
                checked = channel.isEnabled,
                onCheckedChange = { onToggle() },
                modifier = Modifier.scale(0.85f)
            )

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "移除渠道",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
