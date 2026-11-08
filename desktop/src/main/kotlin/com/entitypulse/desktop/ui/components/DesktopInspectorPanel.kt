package com.entitypulse.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.entitypulse.desktop.data.model.EntityRecord
import com.entitypulse.desktop.data.model.PlatformType
import com.entitypulse.desktop.data.model.SourceChannelRecord
import com.entitypulse.desktop.network.NodeManager
import com.entitypulse.desktop.network.ParsedFeedItem
import com.entitypulse.desktop.ui.DesktopAsyncImage
import com.entitypulse.desktop.ui.DesktopBrowserHelper
import com.entitypulse.desktop.ui.theme.DarkBorder
import com.entitypulse.desktop.ui.theme.DarkSurface
import com.entitypulse.desktop.ui.theme.DarkSurfaceElevated
import com.entitypulse.desktop.ui.theme.VipGold
import kotlinx.coroutines.launch
import java.net.URLEncoder

@Composable
fun DesktopInspectorPanel(
    selectedEntity: EntityRecord?,
    channels: List<SourceChannelRecord>,
    nodeManager: NodeManager,
    onEditAvatarClick: () -> Unit,
    onToggleVip: (Boolean) -> Unit,
    onToggleNotify: (Boolean) -> Unit,
    onAddChannelClick: () -> Unit,
    onDeleteChannel: (Long) -> Unit,
    onToggleChannel: (Long, Boolean) -> Unit,
    onTestChannel: suspend (String) -> ParsedFeedItem,
    onDeleteEntity: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Channel testing states: channelId -> Result
    val testResults = remember { mutableStateMapOf<Long, Result<ParsedFeedItem>?>() }
    val testingChannelIds = remember { mutableStateMapOf<Long, Boolean>() }

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(380.dp)
            .background(DarkSurface)
            .border(width = 1.dp, color = DarkBorder, shape = RoundedCornerShape(0.dp))
            .padding(16.dp)
    ) {
        if (selectedEntity == null) {
            // Empty placeholder
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "未选中特定关注主体",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "在左侧列表中点击任一主体，即可在此管理官方渠道、测试连通性或更换封面。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            // Selected Entity Details
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header card with cover, name, VIP
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                DesktopAsyncImage(
                                    url = selectedEntity.avatarUrl,
                                    contentDescription = selectedEntity.name,
                                    nodeManager = nodeManager,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                                )
                                if (selectedEntity.isVip) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "VIP",
                                        tint = VipGold,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .align(Alignment.BottomEnd)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedEntity.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = selectedEntity.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // VIP toggle button
                            IconButton(onClick = { onToggleVip(!selectedEntity.isVip) }) {
                                Icon(
                                    imageVector = if (selectedEntity.isVip) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "VIP切换",
                                    tint = if (selectedEntity.isVip) VipGold else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!selectedEntity.bio.isNullOrBlank()) {
                            Text(
                                text = selectedEntity.bio,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Action chips under entity
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onEditAvatarClick,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("更换封面", style = MaterialTheme.typography.labelSmall)
                            }

                            FilledTonalButton(
                                onClick = {
                                    val q = runCatching { URLEncoder.encode("${selectedEntity.name} 官网", "UTF-8") }.getOrDefault(selectedEntity.name)
                                    DesktopBrowserHelper.openInBrowser("https://www.bing.com/search?q=$q")
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("浏览器找官网", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // Channels Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已绑定的社媒渠道 (${channels.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = onAddChannelClick,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "添加渠道", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                    }
                }

                if (channels.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("暂未绑定社媒账号或动态渠道", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = onAddChannelClick) {
                                Icon(Icons.Default.AddLink, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("立即绑定渠道")
                            }
                        }
                    }
                } else {
                    channels.forEach { channel ->
                        val isTesting = testingChannelIds[channel.id] == true
                        val testRes = testResults[channel.id]

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = Color(channel.platformType.badgeColor).copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = channel.platformType.displayName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(channel.platformType.badgeColor),
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = channel.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1
                                    )
                                    Switch(
                                        checked = channel.isEnabled,
                                        onCheckedChange = { onToggleChannel(channel.id, it) },
                                        modifier = Modifier.height(24.dp)
                                    )
                                }

                                Text(
                                    text = "标识: ${channel.identifier}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // Sync status or error
                                if (!channel.lastError.isNullOrBlank()) {
                                    Text(
                                        text = "状态: ${channel.lastError}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFF87171)
                                    )
                                }

                                // Actions
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilledTonalButton(
                                        onClick = {
                                            testingChannelIds[channel.id] = true
                                            testResults[channel.id] = null
                                            coroutineScope.launch {
                                                val r = runCatching { onTestChannel(channel.resolvedFeedUrl) }
                                                testResults[channel.id] = r
                                                testingChannelIds[channel.id] = false
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(26.dp),
                                        enabled = !isTesting
                                    ) {
                                        if (isTesting) {
                                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                        } else {
                                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("测试连通", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }

                                    Spacer(modifier = Modifier.weight(1f))

                                    IconButton(
                                        onClick = { onDeleteChannel(channel.id) },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "删除频道", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }

                                // Test feedback
                                testRes?.let { result ->
                                    result.fold(
                                        onSuccess = { item ->
                                            Text("连通正常: ${item.title.take(30)}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF34D399))
                                        },
                                        onFailure = { err ->
                                            Text("连通失败: ${err.message}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF87171))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = DarkBorder)

                // Danger zone: Delete entity
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("取消关注并删除该主体")
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认取消关注？") },
            text = { Text("确定要删除「${selectedEntity?.name}」及其所有绑定的社交媒体渠道和抓取的动态吗？此操作无法撤销。") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteEntity()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("确认删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}
