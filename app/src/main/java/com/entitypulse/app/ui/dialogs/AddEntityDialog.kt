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
import com.entitypulse.app.network.IpLocateResult
import com.entitypulse.app.ui.MainViewModel
import com.entitypulse.app.ui.screens.feed.PlatformBadge
import com.entitypulse.app.ui.theme.VipGold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AddEntityDialog(
    viewModel: MainViewModel,
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
    var category by remember { mutableStateOf("ACG / 游戏 / 创作者") }
    var isVip by remember { mutableStateOf(false) }

    // 智能 IP 识别与跨平台账号结果
    var candidateGroups by remember { mutableStateOf<List<PlatformCandidateGroup>>(emptyList()) }
    var isLocatingIp by remember { mutableStateOf(false) }
    var detectedIpResult by remember { mutableStateOf<IpLocateResult?>(null) }

    // 当输入主体名称变化时，自动触发：1. 知识网络 IP 定位 (Bangumi/Steam/Wiki) -> 2. 全网针对性检索官方社媒
    LaunchedEffect(name) {
        val query = name.trim()
        if (query.length >= 2) {
            delay(400) // 防抖
            isLocatingIp = true

            coroutineScope.launch {
                val res = viewModel.locateIpAndSocialChannels(query)
                isLocatingIp = false
                detectedIpResult = res

                if (res.candidateGroups.isNotEmpty()) {
                    candidateGroups = res.candidateGroups
                } else if (candidateGroups.isEmpty()) {
                    candidateGroups = EntityCatalog.generateGroupedCandidates(query)
                }

                // 智能自动填充探测到的资料，若用户未手动填写
                if (bio.isBlank() && !res.bio.isNullOrBlank()) {
                    bio = res.bio
                }
                if (avatarUrl.isBlank() && !res.avatarUrl.isNullOrBlank()) {
                    avatarUrl = res.avatarUrl
                }
                if (category == "ACG / 游戏 / 创作者" || category.isBlank()) {
                    category = res.category
                }
            }
        } else {
            isLocatingIp = false
            detectedIpResult = null
            if (query.isBlank()) {
                candidateGroups = emptyList()
            }
        }
    }

    val selectedCount = candidateGroups.count { it.isEnabled }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.TravelExplore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "智能识别与添加主体", style = MaterialTheme.typography.titleLarge)
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
                    text = "输入动漫IP、游戏IP、工作室或作者名称，系统将通过知识网络自动锁定实体，并针对性全网检索官方账号：",
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
                    label = { Text("IP 或主体名称 (如: 孤独摇滚 / 黑神话 / Team Cherry)") },
                    singleLine = true,
                    trailingIcon = {
                        if (isLocatingIp) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else if (detectedIpResult != null) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "已定位",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // --- 成功定位到的 IP 详情卡片 ---
                val ipRes = detectedIpResult
                if (ipRes != null && ipRes.canonicalName.isNotBlank()) {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "已锁定实体: ${ipRes.canonicalName}" + (ipRes.originalOrEnglishName?.let { " ($it)" } ?: ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = "分类: ${ipRes.category}" + (ipRes.officialSiteUrl?.let { " · 官网: $it" } ?: ""),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                // --- 针对该 IP 检索到的多平台账号面板 ---
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
                                        text = "全网检索到的账号 ($selectedCount/${candidateGroups.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    if (isLocatingIp) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "检索中...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
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

                                        // 若存在多个候选账号（例如官方推特 / PR推特 / 多语言分部），展示切换 Chips
                                        if (group.isEnabled && group.candidates.size > 1) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "备选候选账号 (点击切换):",
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
                    label = { Text("简介描述 (自动回填，可微调)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = avatarUrl,
                    onValueChange = { avatarUrl = it },
                    label = { Text("封面 / 头像图片 URL (已自动抓取)") },
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
