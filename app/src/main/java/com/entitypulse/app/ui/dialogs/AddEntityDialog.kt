package com.entitypulse.app.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.entitypulse.app.data.model.EntityCatalog
import com.entitypulse.app.data.model.PredefinedChannel
import com.entitypulse.app.network.IpCandidate
import com.entitypulse.app.network.PlatformResolver
import com.entitypulse.app.ui.MainViewModel
import com.entitypulse.app.ui.screens.feed.PlatformBadge
import com.entitypulse.app.ui.theme.VipGold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder

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
    var officialSiteUrl by remember { mutableStateOf("") }
    var isVip by remember { mutableStateOf(false) }

    // 主体 IP 检索状态与备选项列表
    var isSearchingIp by remember { mutableStateOf(false) }
    var ipCandidates by remember { mutableStateOf<List<IpCandidate>>(emptyList()) }
    var selectedCandidateIndex by remember { mutableIntStateOf(-1) }

    // 用户已锁定的渠道列表（由应用内浏览器或手动添加）
    var addedChannels by remember { mutableStateOf<List<PredefinedChannel>>(emptyList()) }

    // 应用内独立浏览器小窗口显示状态
    var showBrowserDialog by remember { mutableStateOf(false) }

    // 手动添加输入面板展开状态
    var showManualAddPanel by remember { mutableStateOf(false) }
    var manualInputUrl by remember { mutableStateOf("") }

    // 搜索主体 IP 的执行函数
    fun triggerIpSearch(targetQuery: String) {
        val query = targetQuery.trim()
        if (query.length < 2) {
            ipCandidates = emptyList()
            selectedCandidateIndex = -1
            return
        }
        isSearchingIp = true
        coroutineScope.launch {
            val results = viewModel.searchIpCandidates(query)
            isSearchingIp = false
            ipCandidates = results
            if (results.isNotEmpty() && selectedCandidateIndex == -1) {
                // 默认采纳首个高置信度候选的主体资料
                val first = results.first()
                if (bio.isBlank() && !first.bio.isNullOrBlank()) bio = first.bio
                if (avatarUrl.isBlank() && !first.avatarUrl.isNullOrBlank()) avatarUrl = first.avatarUrl
                if (!first.officialSiteUrl.isNullOrBlank()) officialSiteUrl = first.officialSiteUrl
                if (category == "ACG / 游戏 / 创作者" && first.category.isNotBlank()) category = first.category
            }
        }
    }

    // 防抖自动搜索 IP 候选
    LaunchedEffect(name) {
        val query = name.trim()
        if (query.length >= 2 && selectedCandidateIndex == -1) {
            delay(500)
            triggerIpSearch(query)
        }
    }

    // 浏览器弹窗
    if (showBrowserDialog) {
        val browserStartUrl = remember(officialSiteUrl, name) {
            if (officialSiteUrl.isNotBlank() && officialSiteUrl.startsWith("http")) {
                officialSiteUrl
            } else {
                val q = runCatching { URLEncoder.encode("$name 官方社交媒体", "UTF-8") }.getOrDefault(name)
                "https://www.bing.com/search?q=$q"
            }
        }

        InAppBrowserDialog(
            initialUrl = browserStartUrl,
            entityName = name.ifBlank { "目标主体" },
            officialSiteUrl = officialSiteUrl.ifBlank { null },
            addedChannels = addedChannels,
            onChannelAdded = { newChannel ->
                if (addedChannels.none { it.platformType == newChannel.platformType && it.identifier.equals(newChannel.identifier, ignoreCase = true) }) {
                    addedChannels = addedChannels + newChannel
                }
            },
            onRemoveChannel = { ch ->
                addedChannels = addedChannels.filterNot { it.platformType == ch.platformType && it.identifier.equals(ch.identifier, ignoreCase = true) }
            },
            onDismiss = { showBrowserDialog = false }
        )
    }

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
                    text = "输入主体名称，系统将全网搜索匹配主体 IP；确认主体后，可通过内置浏览器自由浏览并锁定官方社媒主页：",
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
                                addedChannels = popular.channels
                                selectedCandidateIndex = 0
                            },
                            label = { Text(popular.name) }
                        )
                    }
                }

                // 主体名称输入框 + 搜索按钮
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        selectedCandidateIndex = -1
                    },
                    label = { Text("主体或作品名称 (如: 地狱客栈 / 空洞骑士 / 黑神话)") },
                    singleLine = true,
                    trailingIcon = {
                        if (isSearchingIp) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { triggerIpSearch(name) }) {
                                Icon(Icons.Default.Search, contentDescription = "定位 IP")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // --- 1. 全网匹配到的主体 IP 备选项（若有多项）---
                if (ipCandidates.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "全网匹配到的主体 IP 备选项 (点击选择以自动填充):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        ipCandidates.forEachIndexed { index, candidate ->
                            val isSelected = selectedCandidateIndex == index
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCandidateIndex = index
                                        name = candidate.canonicalName
                                        if (!candidate.bio.isNullOrBlank()) bio = candidate.bio
                                        if (!candidate.avatarUrl.isNullOrBlank()) avatarUrl = candidate.avatarUrl
                                        if (candidate.category.isNotBlank()) category = candidate.category
                                        if (!candidate.officialSiteUrl.isNullOrBlank()) officialSiteUrl = candidate.officialSiteUrl
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp)
                                ) {
                                    if (!candidate.avatarUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = candidate.avatarUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = candidate.canonicalName + (candidate.originalOrEnglishName?.let { " ($it)" } ?: ""),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = candidate.sourceNote,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        if (!candidate.bio.isNullOrBlank()) {
                                            Text(
                                                text = candidate.bio,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline,
                                                maxLines = 2
                                            )
                                        }
                                    }
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            selectedCandidateIndex = index
                                            name = candidate.canonicalName
                                            if (!candidate.bio.isNullOrBlank()) bio = candidate.bio
                                            if (!candidate.avatarUrl.isNullOrBlank()) avatarUrl = candidate.avatarUrl
                                            if (candidate.category.isNotBlank()) category = candidate.category
                                            if (!candidate.officialSiteUrl.isNullOrBlank()) officialSiteUrl = candidate.officialSiteUrl
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // --- 2. 绑定社交媒体账号板块 ---
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "绑定社媒账号 (已锁定 ${addedChannels.size} 个)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        // 已添加账号列表
                        if (addedChannels.isNotEmpty()) {
                            addedChannels.forEach { ch ->
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        PlatformBadge(ch.platformType)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = ch.identifier,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = ch.title,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                addedChannels = addedChannels.filterNot {
                                                    it.platformType == ch.platformType && it.identifier == ch.identifier
                                                }
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "删除",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "尚未绑定社交账号。点击下方按钮即可打开独立浏览器小窗口，自由搜索或访问官网，一键锁定并自动识别保存账号。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        // 打开应用内独立小窗口浏览器按钮
                        Button(
                            onClick = {
                                if (name.isBlank()) {
                                    name = "目标主体"
                                }
                                showBrowserDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("打开内置小窗口检索并锁定账号")
                        }

                        // 手动快速粘贴辅助
                        TextButton(
                            onClick = { showManualAddPanel = !showManualAddPanel },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Text(
                                if (showManualAddPanel) "收起手动输入" else "已有社媒网址？点击手动粘贴",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }

                        AnimatedVisibility(visible = showManualAddPanel) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = manualInputUrl,
                                    onValueChange = { manualInputUrl = it },
                                    label = { Text("粘贴社媒网址或 Handle", style = MaterialTheme.typography.labelSmall) },
                                    placeholder = { Text("如 https://x.com/...", style = MaterialTheme.typography.labelSmall) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = {
                                        val target = manualInputUrl.trim()
                                        if (target.isNotBlank()) {
                                            val resolved = PlatformResolver.resolve(target)
                                            val ch = PredefinedChannel(
                                                platformType = resolved.platformType,
                                                identifier = resolved.identifier,
                                                title = resolved.suggestedTitle,
                                                feedPathOrUrl = resolved.feedPathOrUrl,
                                                defaultSelected = true,
                                                probabilityNote = "用户手动输入"
                                            )
                                            if (addedChannels.none { it.platformType == ch.platformType && it.identifier == ch.identifier }) {
                                                addedChannels = addedChannels + ch
                                            }
                                            manualInputUrl = ""
                                        }
                                    },
                                    enabled = manualInputUrl.isNotBlank()
                                ) {
                                    Text("添加")
                                }
                            }
                        }
                    }
                }

                // --- 3. 详细资料手动微调 ---
                Text(
                    text = "主体资料 (已根据匹配结果自动填充，可自由编辑):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("简介描述") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (avatarUrl.isNotBlank()) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "封面预览",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    OutlinedTextField(
                        value = avatarUrl,
                        onValueChange = { avatarUrl = it },
                        label = { Text("封面 / 头像图片 URL") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    if (name.isNotBlank()) {
                                        coroutineScope.launch {
                                            val img = viewModel.searchCoverImage(name)
                                            if (!img.isNullOrBlank()) avatarUrl = img
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = "全网搜图", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("分类标签") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // VIP 切换
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
                        onConfirm(name, bio, avatarUrl, isVip, category, addedChannels)
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text(
                    if (addedChannels.isNotEmpty()) "完成创建并绑定 (${addedChannels.size} 个账号)" else "创建主体 (暂不绑定账号)"
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
