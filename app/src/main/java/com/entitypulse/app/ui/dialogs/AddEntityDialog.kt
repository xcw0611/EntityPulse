package com.entitypulse.app.ui.dialogs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.entitypulse.app.data.model.EntityCatalog
import com.entitypulse.app.data.model.PlatformCandidateGroup
import com.entitypulse.app.data.model.PredefinedChannel
import com.entitypulse.app.network.OnlineEntityLocator
import com.entitypulse.app.ui.screens.feed.PlatformBadge
import com.entitypulse.app.ui.theme.VipGold
import kotlinx.coroutines.launch

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
    val coroutineScope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("游戏 / 创作者") }
    var isVip by remember { mutableStateOf(false) }

    // 智能推断与多变体候选组列表
    var candidateGroups by remember { mutableStateOf<List<PlatformCandidateGroup>>(emptyList()) }
    var isSearchingOnline by remember { mutableStateOf(false) }
    var onlineDiscoveredCount by remember { mutableIntStateOf(0) }

    // 当主体名称改变时，自动触发跨平台多变体规则生成 + 全网在线联想探测
    LaunchedEffect(name) {
        val query = name.trim()
        if (query.isNotBlank()) {
            // 1. 规则引擎秒级生成初始多变体
            candidateGroups = EntityCatalog.generateGroupedCandidates(query)

            // 2. 异步全网探测（DuckDuckGo / B站 / 维基等）
            isSearchingOnline = true
            onlineDiscoveredCount = 0
            coroutineScope.launch {
                val additions = OnlineEntityLocator.searchOnlineCandidates(query)
                isSearchingOnline = false
                if (additions.isNotEmpty()) {
                    onlineDiscoveredCount = additions.size
                    // 重新以合并后的在线结果渲染候选组
                    candidateGroups = EntityCatalog.generateGroupedCandidates(query, additions)
                }
            }
        } else {
            candidateGroups = emptyList()
            isSearchingOnline = false
            onlineDiscoveredCount = 0
        }
    }

    val selectedCount = candidateGroups.count { it.isEnabled }

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
                    text = "输入主体名称，系统将结合【智能全网定位】与【多变体账号候选】，为您推荐各社媒的最佳可能账户：",
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
                                candidateGroups = EntityCatalog.generateGroupedCandidates(popular.name)
                            },
                            label = { Text(popular.name) }
                        )
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("主体名称 (例如: Team Cherry / 游戏科学)") },
                    singleLine = true,
                    trailingIcon = {
                        if (isSearchingOnline) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else if (onlineDiscoveredCount > 0) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "已探测",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // --- 智能候选账号多平台分组确认面板 ---
                if (candidateGroups.isNotEmpty()) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "匹配各平台账号 ($selectedCount/${candidateGroups.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    if (isSearchingOnline) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "全网探测中...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else if (onlineDiscoveredCount > 0) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "已联想 $onlineDiscoveredCount 个变体",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = {
                                        val allSelected = candidateGroups.all { it.isEnabled }
                                        candidateGroups = candidateGroups.map { it.copy(isEnabled = !allSelected) }
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        if (candidateGroups.all { it.isEnabled }) "取消全选" else "全选",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            candidateGroups.forEachIndexed { groupIdx, group ->
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (group.isEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        // 平台主信息
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Checkbox(
                                                checked = group.isEnabled,
                                                onCheckedChange = { checked ->
                                                    candidateGroups = candidateGroups.toMutableList().also { list ->
                                                        list[groupIdx] = group.copy(isEnabled = checked)
                                                    }
                                                }
                                            )
                                            PlatformBadge(group.platformType)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = group.currentSelectedChannel.identifier,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "${group.currentSelectedChannel.title} [${group.currentSelectedChannel.probabilityNote}]",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        }

                                        // 若存在多个备选可能账户（解决不同社媒命名差异），展示切换 Chips
                                        if (group.isEnabled && group.candidates.size > 1) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "备选可能账户 (点击切换):",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState())
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                group.candidates.forEachIndexed { candIdx, cand ->
                                                    val isChosen = group.selectedIndex == candIdx
                                                    FilterChip(
                                                        selected = isChosen,
                                                        onClick = {
                                                            candidateGroups = candidateGroups.toMutableList().also { list ->
                                                                list[groupIdx] = group.copy(selectedIndex = candIdx)
                                                            }
                                                        },
                                                        label = {
                                                            Text(
                                                                text = "${cand.identifier} (${cand.probabilityNote})",
                                                                style = MaterialTheme.typography.labelSmall
                                                            )
                                                        }
                                                    )
                                                }
                                            }
                                        }
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
                        val chosenChannels = candidateGroups
                            .filter { it.isEnabled }
                            .map { it.currentSelectedChannel }
                        onConfirm(name, bio, avatarUrl, isVip, category, chosenChannels)
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
