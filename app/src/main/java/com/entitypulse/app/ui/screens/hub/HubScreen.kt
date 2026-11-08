package com.entitypulse.app.ui.screens.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.entitypulse.app.data.model.EntityRecord
import com.entitypulse.app.data.model.EntityWithUnreadCount
import com.entitypulse.app.ui.MainViewModel
import com.entitypulse.app.ui.dialogs.AddEntityDialog
import com.entitypulse.app.ui.dialogs.EditAvatarDialog
import com.entitypulse.app.ui.theme.VipGold
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubScreen(
    viewModel: MainViewModel,
    onNavigateToEntity: (entityId: Long) -> Unit
) {
    val entities by viewModel.entities.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var showAddEntityDialog by remember { mutableStateOf(false) }
    var editingAvatarEntity by remember { mutableStateOf<EntityRecord?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "主体看板",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        val totalUnread = entities.sumOf { it.unreadCount }
                        if (totalUnread > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ) {
                                Text("$totalUnread")
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshAll() },
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "刷新全部")
                        }
                    }
                    IconButton(onClick = { viewModel.markAllRead() }) {
                        Icon(imageVector = Icons.Default.DoneAll, contentDescription = "全部标为已读")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddEntityDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("添加主体") }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (entities.isEmpty()) {
                // 纯净初始状态引导页
                EmptyHubView(onAddClick = { showAddEntityDialog = true })
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 320.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(entities, key = { it.entity.id }) { item ->
                        EntityCard(
                            item = item,
                            onClick = { onNavigateToEntity(item.entity.id) },
                            onToggleVip = { viewModel.toggleEntityVip(item.entity.id, item.entity.isVip) },
                            onToggleNotify = { viewModel.toggleEntityNotify(item.entity.id, item.entity.isNotifyEnabled) },
                            onEditAvatar = { editingAvatarEntity = item.entity }
                        )
                    }
                }
            }
        }
    }

    if (showAddEntityDialog) {
        AddEntityDialog(
            viewModel = viewModel,
            onDismiss = { showAddEntityDialog = false },
            onConfirm = { name, bio, avatarUrl, isVip, category, selectedChannels ->
                viewModel.addEntityWithChannels(name, bio, avatarUrl, isVip, category, selectedChannels)
            }
        )
    }

    editingAvatarEntity?.let { ent ->
        EditAvatarDialog(
            entityName = ent.name,
            currentAvatarUrl = ent.avatarUrl,
            onDismiss = { editingAvatarEntity = null },
            onConfirm = { newUrl ->
                viewModel.updateEntityAvatar(ent.id, newUrl)
            },
            onSearchImage = { name ->
                viewModel.searchCoverImage(name)
            }
        )
    }
}

@Composable
fun EntityCard(
    item: EntityWithUnreadCount,
    onClick: () -> Unit,
    onToggleVip: () -> Unit,
    onToggleNotify: () -> Unit,
    onEditAvatar: () -> Unit
) {
    val entity = item.entity

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (entity.isVip) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 第一行：头像、名称、VIP、通知开关与未读角标
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 头像（点击可修改封面/头像）
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.clickable(onClick = onEditAvatar)
                ) {
                    if (!entity.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = entity.avatarUrl,
                            contentDescription = entity.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Text(
                                text = entity.name.take(1).uppercase(),
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 标题与分类
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = entity.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (entity.isVip) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "VIP",
                                tint = VipGold,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = entity.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // 未读红点
                if (item.unreadCount > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ) {
                        Text("${item.unreadCount}")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                // 快捷操作
                IconButton(onClick = onToggleVip, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (entity.isVip) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "VIP切换",
                        tint = if (entity.isVip) VipGold else MaterialTheme.colorScheme.outline
                    )
                }
            }

            // 绑定的平台渠道标签
            if (item.channelTypes.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item.channelTypes.split(",").distinct().take(4).forEach { platformName ->
                        SuggestionChip(
                            onClick = onClick,
                            label = { Text(platformName, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(26.dp)
                        )
                    }
                }
            }

            // 最新动态摘要
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            if (!item.latestFeedTitle.isNullOrBlank()) {
                val timeStr = item.latestFeedPublishedAt?.let { formatRelativeTime(it) } ?: ""
                Text(
                    text = item.latestFeedTitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (timeStr.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "最新更新 · $timeStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                Text(
                    text = "暂无动态，点击进入绑定平台账号",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun EmptyHubView(onAddClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Hub,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "欢迎使用 EntityPulse",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "以“人”或“工作室”为中心集成管理所有社交账号动态。\n例如创建「Team Cherry」，绑定其 X/Twitter、YouTube 与 Steam，无需在各平台来回奔波即可接收统一通知。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAddClick) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("添加第一个关注主体")
        }
    }
}

fun formatRelativeTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        diff < 0 -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes}分钟前"
        hours < 24 -> "${hours}小时前"
        days < 7 -> "${days}天前"
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
    }
}
