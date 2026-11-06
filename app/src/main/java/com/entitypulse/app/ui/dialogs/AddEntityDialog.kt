package com.entitypulse.app.ui.dialogs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.entitypulse.app.data.model.EntityCatalog
import com.entitypulse.app.data.model.PredefinedChannel
import com.entitypulse.app.ui.screens.feed.PlatformBadge
import com.entitypulse.app.ui.theme.VipGold

@Composable
fun AddEntityDialog(
    onDismiss: () -> Unit,
    onConfirm: (
        name: String,
        bio: String?,
        avatarUrl: String?,
        isVip: Boolean,
        category: String,
        selectedChannels: List<PredefinedChannel>
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("游戏 / 创作者") }
    var isVip by remember { mutableStateOf(false) }

    // 智能推断的跨平台账号候选列表与勾选状态
    var candidateChannels by remember { mutableStateOf<List<PredefinedChannel>>(emptyList()) }
    val selectedChannelMap = remember { mutableStateMapOf<String, Boolean>() }

    // 当主体名称改变时，自动触发跨平台账号智能识别
    LaunchedEffect(name) {
        if (name.isNotBlank()) {
            val candidates = EntityCatalog.findOrGenerateCandidates(name)
            candidateChannels = candidates
            candidates.forEach { ch ->
                if (!selectedChannelMap.containsKey(ch.feedPathOrUrl)) {
                    selectedChannelMap[ch.feedPathOrUrl] = ch.defaultSelected
                }
            }
        } else {
            candidateChannels = emptyList()
            selectedChannelMap.clear()
        }
    }

    val selectedCount = candidateChannels.count { selectedChannelMap[it.feedPathOrUrl] == true }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "添加关注主体", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "快速选择或输入主体，系统将自动识别并提供各平台官方账号一键绑定：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 快捷精选主体推荐 Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EntityCatalog.POPULAR_ENTITIES.forEach { popular ->
                        SuggestionChip(
                            onClick = {
                                name = popular.name
                                bio = popular.bio
                                avatarUrl = popular.avatarUrl ?: ""
                                category = popular.category
                                isVip = popular.isVip
                                candidateChannels = popular.channels
                                selectedChannelMap.clear()
                                popular.channels.forEach { ch ->
                                    selectedChannelMap[ch.feedPathOrUrl] = true
                                }
                            },
                            label = { Text(popular.name) }
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("主体名称 (例如: Team Cherry)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // --- 智能候选账号展示与多选区域 ---
                if (candidateChannels.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "发现各平台账号 ($selectedCount/${candidateChannels.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                TextButton(
                                    onClick = {
                                        val allSelected = candidateChannels.all { selectedChannelMap[it.feedPathOrUrl] == true }
                                        candidateChannels.forEach { ch ->
                                            selectedChannelMap[ch.feedPathOrUrl] = !allSelected
                                        }
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        if (candidateChannels.all { selectedChannelMap[it.feedPathOrUrl] == true }) "取消全选" else "全选",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            candidateChannels.forEach { channel ->
                                val isChecked = selectedChannelMap[channel.feedPathOrUrl] == true
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            selectedChannelMap[channel.feedPathOrUrl] = checked
                                        }
                                    )
                                    PlatformBadge(channel.platformType)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = channel.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = channel.identifier,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("简介描述 (选填)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = avatarUrl,
                    onValueChange = { avatarUrl = it },
                    label = { Text("头像图片 URL (选填)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("分类标签") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "VIP",
                            tint = if (isVip) VipGold else MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = "设为 VIP 强提醒主体", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "新动态到达时触发最高级别系统横幅与铃声",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isVip,
                        onCheckedChange = { isVip = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val chosen = candidateChannels.filter { selectedChannelMap[it.feedPathOrUrl] == true }
                        onConfirm(name, bio, avatarUrl, isVip, category, chosen)
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text(
                    if (selectedCount > 0) "创建并一键绑定 ($selectedCount)" else "创建主体"
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
