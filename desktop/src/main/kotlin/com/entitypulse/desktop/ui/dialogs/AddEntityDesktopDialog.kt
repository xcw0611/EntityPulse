package com.entitypulse.desktop.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.entitypulse.desktop.data.model.PredefinedChannel
import com.entitypulse.desktop.network.IpCandidate
import com.entitypulse.desktop.network.NodeManager
import com.entitypulse.desktop.network.OnlineEntityLocator
import com.entitypulse.desktop.ui.DesktopAsyncImage
import com.entitypulse.desktop.ui.DesktopBrowserHelper
import com.entitypulse.desktop.ui.theme.DarkBorder
import com.entitypulse.desktop.ui.theme.DarkSurfaceElevated
import com.entitypulse.desktop.ui.theme.VipGold
import kotlinx.coroutines.launch
import java.net.URLEncoder

@Composable
fun AddEntityDesktopDialog(
    nodeManager: NodeManager,
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

    var isSearchingIp by remember { mutableStateOf(false) }
    var ipCandidates by remember { mutableStateOf<List<IpCandidate>>(emptyList()) }
    var selectedCandidateIndex by remember { mutableStateOf(-1) }
    var addedChannels by remember { mutableStateOf<List<PredefinedChannel>>(emptyList()) }

    fun triggerIpSearch() {
        val query = name.trim()
        if (query.length < 2) {
            ipCandidates = emptyList()
            selectedCandidateIndex = -1
            return
        }
        isSearchingIp = true
        coroutineScope.launch {
            val results = OnlineEntityLocator.searchIpCandidates(query, nodeManager.getOkHttpClient())
            isSearchingIp = false
            ipCandidates = results
            if (results.isNotEmpty() && selectedCandidateIndex == -1) {
                val first = results.first()
                if (bio.isBlank() && !first.bio.isNullOrBlank()) bio = first.bio
                if (avatarUrl.isBlank() && !first.avatarUrl.isNullOrBlank()) avatarUrl = first.avatarUrl
                if (!first.officialSiteUrl.isNullOrBlank()) officialSiteUrl = first.officialSiteUrl
                if (category == "ACG / 游戏 / 创作者" && first.category.isNotBlank()) category = first.category
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(760.dp)
                .wrapContentHeight()
                .heightIn(max = 750.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Title bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.TravelExplore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "添加关注主体 (全网 IP 定位)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Search Input row (at the top, right below title)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = {
                                name = it
                                selectedCandidateIndex = -1
                            },
                            label = { Text("主体或作品名称 (如: 地狱客栈 / 黑神话:悟空 / Team Cherry)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { triggerIpSearch() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            onClick = { triggerIpSearch() },
                            enabled = !isSearchingIp && name.trim().length >= 2,
                            modifier = Modifier.height(54.dp)
                        ) {
                            if (isSearchingIp) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("正在定位...")
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("定位主体 IP")
                            }
                        }
                    }

                    // Candidates list (if searched and found)
                    if (ipCandidates.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurfaceElevated, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "全网匹配到的主体 IP 备选项 (点击即可快速选定并填充资料):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            ipCandidates.forEachIndexed { idx, candidate ->
                                val isSelected = selectedCandidateIndex == idx
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            selectedCandidateIndex = idx
                                            name = candidate.canonicalName
                                            bio = candidate.bio ?: ""
                                            avatarUrl = candidate.avatarUrl ?: ""
                                            category = candidate.category
                                            if (!candidate.officialSiteUrl.isNullOrBlank()) {
                                                officialSiteUrl = candidate.officialSiteUrl
                                            }
                                        },
                                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        DesktopAsyncImage(
                                            url = candidate.avatarUrl,
                                            contentDescription = candidate.canonicalName,
                                            nodeManager = nodeManager,
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = candidate.canonicalName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = candidate.category,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = candidate.sourceNote,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (!candidate.bio.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = candidate.bio,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2
                                                )
                                            }
                                        }
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "已选定",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Entity detail settings
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Left: Cover preview
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("主体封面", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                            DesktopAsyncImage(
                                url = avatarUrl.ifBlank { null },
                                contentDescription = name,
                                nodeManager = nodeManager,
                                modifier = Modifier
                                    .size(90.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, DarkBorder, RoundedCornerShape(12.dp))
                            )
                            FilledTonalButton(
                                onClick = {
                                    coroutineScope.launch {
                                        val img = OnlineEntityLocator.fetchIpCoverImage(name, nodeManager.getOkHttpClient())
                                        if (!img.isNullOrBlank()) avatarUrl = img
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("联网重查封面", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        // Right: Inputs
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = bio,
                                onValueChange = { bio = it },
                                label = { Text("主体简介 / 描述") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = category,
                                    onValueChange = { category = it },
                                    label = { Text("分类") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = avatarUrl,
                                    onValueChange = { avatarUrl = it },
                                    label = { Text("封面 URL (可选)") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = isVip,
                                    onCheckedChange = { isVip = it },
                                    colors = CheckboxDefaults.colors(checkedColor = VipGold)
                                )
                                Text(
                                    text = "设为 VIP 重点特别关注",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isVip) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isVip) VipGold else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                FilledTonalButton(
                                    onClick = {
                                        val searchKeyword = runCatching { URLEncoder.encode("$name 官方社交媒体", "UTF-8") }.getOrDefault(name)
                                        DesktopBrowserHelper.openInBrowser("https://www.bing.com/search?q=$searchKeyword")
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.heightIn(min = 36.dp)
                                ) {
                                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("浏览器查找官方账号", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onConfirm(
                                    name.trim(),
                                    bio.trim().ifBlank { null },
                                    avatarUrl.trim().ifBlank { null },
                                    isVip,
                                    category.trim().ifBlank { "默认" },
                                    addedChannels
                                )
                                onDismiss()
                            }
                        },
                        enabled = name.isNotBlank()
                    ) {
                        Text("确认添加主体")
                    }
                }
            }
        }
    }
}
