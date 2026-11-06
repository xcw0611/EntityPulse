package com.entitypulse.app.ui.screens.feed

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.entitypulse.app.data.model.FeedItemWithMeta
import com.entitypulse.app.data.model.PlatformType
import com.entitypulse.app.ui.MainViewModel
import com.entitypulse.app.ui.screens.hub.formatRelativeTime
import com.entitypulse.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: MainViewModel,
    onNavigateToPost: (postId: Long) -> Unit
) {
    val feeds by viewModel.feeds.collectAsState()
    val entities by viewModel.entities.collectAsState()
    val selectedEntityId by viewModel.selectedEntityFilterId.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val showUnreadOnly by viewModel.showUnreadOnly.collectAsState()
    var searchActive by remember { mutableStateOf(false) }
    val searchQuery by viewModel.searchQuery.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "综合动态流",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleShowUnreadOnly() }) {
                        Icon(
                            imageVector = if (showUnreadOnly) Icons.Default.FilterAlt else Icons.Default.FilterAltOff,
                            contentDescription = "仅看未读",
                            tint = if (showUnreadOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                    IconButton(
                        onClick = { viewModel.refreshAll() },
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "刷新")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 搜索框
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("搜索动态标题、正文或人物...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "清除")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // 顶部主体过滤 Chip 列表
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedEntityId == null,
                    onClick = { viewModel.selectEntityFilter(null) },
                    label = { Text("全部主体") }
                )
                entities.forEach { entityItem ->
                    FilterChip(
                        selected = selectedEntityId == entityItem.entity.id,
                        onClick = {
                            if (selectedEntityId == entityItem.entity.id) {
                                viewModel.selectEntityFilter(null)
                            } else {
                                viewModel.selectEntityFilter(entityItem.entity.id)
                            }
                        },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(entityItem.entity.name)
                                if (entityItem.unreadCount > 0) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Badge { Text("${entityItem.unreadCount}") }
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (feeds.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank() || showUnreadOnly) "未找到符合条件的动态" else "暂无动态内容\n可在看板中添加主体并绑定平台账号",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(feeds, key = { it.item.id }) { itemWithMeta ->
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
    }
}

@Composable
fun FeedItemCard(
    item: FeedItemWithMeta,
    onClick: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleBookmark: () -> Unit
) {
    val context = LocalContext.current
    val feed = item.item

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (feed.isRead) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (feed.isRead) 1.dp else 3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // 第一行：主体信息与平台徽章
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!item.entityAvatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.entityAvatarUrl,
                        contentDescription = item.entityName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Text(
                            text = item.entityName.take(1).uppercase(),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.entityName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        if (item.isEntityVip) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "VIP",
                                tint = VipGold,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = formatRelativeTime(feed.publishedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // 平台标签徽标
                PlatformBadge(item.platformType)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 标题
            Text(
                text = feed.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (feed.isRead) FontWeight.Normal else FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // 正文纯文本预览
            if (feed.contentPlainText.isNotBlank() && feed.contentPlainText != feed.title) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = feed.contentPlainText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 缩略图
            if (!feed.thumbnailUrl.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                AsyncImage(
                    model = feed.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            // 底部操作栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleRead, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (feed.isRead) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = "已读状态",
                            tint = if (feed.isRead) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onToggleBookmark, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (feed.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "收藏",
                            tint = if (feed.isBookmarked) VipGold else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 分享
                    IconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, feed.title)
                                putExtra(Intent.EXTRA_TEXT, "${feed.title}\n${feed.originalUrl}")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "分享动态"))
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "分享",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    // 打开原网页
                    IconButton(
                        onClick = {
                            if (feed.originalUrl.isNotBlank()) {
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(feed.originalUrl))
                                context.startActivity(browserIntent)
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "打开原帖",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlatformBadge(platformType: PlatformType) {
    val color = when (platformType) {
        PlatformType.TWITTER -> TwitterColor
        PlatformType.BILIBILI -> BilibiliColor
        PlatformType.YOUTUBE -> YouTubeColor
        PlatformType.STEAM -> SteamColor
        PlatformType.REDDIT -> RedditColor
        PlatformType.WEIBO -> WeiboColor
        PlatformType.BLUESKY -> BlueskyColor
        PlatformType.GITHUB -> MaterialTheme.colorScheme.onSurface
        PlatformType.RSS_CUSTOM -> RssColor
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = platformType.displayName,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
