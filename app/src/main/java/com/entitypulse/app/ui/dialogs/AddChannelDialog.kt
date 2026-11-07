package com.entitypulse.app.ui.dialogs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.entitypulse.app.data.model.EntityCatalog
import com.entitypulse.app.data.model.PlatformType
import com.entitypulse.app.network.OnlineEntityLocator
import com.entitypulse.app.network.ParsedFeedItem
import com.entitypulse.app.network.PlatformResolver
import com.entitypulse.app.network.ResolvedPlatform
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddChannelDialog(
    entityName: String,
    onDismiss: () -> Unit,
    onTestConnection: suspend (feedUrl: String) -> ParsedFeedItem,
    onConfirm: (platformType: PlatformType, identifier: String, resolvedFeedUrl: String, title: String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var inputUrlOrHandle by remember { mutableStateOf("") }
    var resolved by remember { mutableStateOf<ResolvedPlatform?>(null) }
    var customTitle by remember { mutableStateOf("") }

    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Result<ParsedFeedItem>?>(null) }

    // 当输入变动时，实时自动识别
    LaunchedEffect(inputUrlOrHandle) {
        if (inputUrlOrHandle.isNotBlank()) {
            val r = PlatformResolver.resolve(inputUrlOrHandle)
            resolved = r
            if (customTitle.isBlank() || customTitle.startsWith("X /") || customTitle.startsWith("Bilibili") || customTitle.startsWith("YouTube") || customTitle.startsWith("Steam") || customTitle.startsWith("Reddit")) {
                customTitle = r.suggestedTitle
            }
        } else {
            resolved = null
        }
        testResult = null
    }

    var groupedCandidates by remember(entityName) {
        mutableStateOf(EntityCatalog.generateGroupedCandidates(entityName))
    }

    LaunchedEffect(entityName) {
        val online = OnlineEntityLocator.searchOnlineCandidates(entityName)
        if (online.isNotEmpty()) {
            groupedCandidates = EntityCatalog.generateGroupedCandidates(entityName, online)
        }
    }

    val allCandidateChannels = remember(groupedCandidates) {
        groupedCandidates.flatMap { it.candidates }
    }

    val presetPlatforms = listOf(
        PlatformType.TWITTER to "https://x.com/",
        PlatformType.YOUTUBE to "https://www.youtube.com/@",
        PlatformType.BILIBILI to "https://space.bilibili.com/",
        PlatformType.STEAM to "https://store.steampowered.com/app/",
        PlatformType.REDDIT to "https://reddit.com/r/",
        PlatformType.WEIBO to "https://weibo.com/u/",
        PlatformType.BLUESKY to "https://bsky.app/profile/",
        PlatformType.RSS_CUSTOM to "https://"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "为「$entityName」绑定平台账号", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "支持直接粘贴该主体在各大平台的个人主页网址、账号 handle 或官方标准 RSS 链接：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (allCandidateChannels.isNotEmpty()) {
                    Text(
                        text = "智能匹配与全网联想到的「$entityName」可能账号：",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        allCandidateChannels.forEach { rec ->
                            SuggestionChip(
                                onClick = {
                                    inputUrlOrHandle = rec.feedPathOrUrl
                                    customTitle = rec.title
                                },
                                label = { Text("${rec.platformType.displayName}: ${rec.identifier} (${rec.probabilityNote})") }
                            )
                        }
                    }
                }

                // 快捷模板选择行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetPlatforms.forEach { (type, prefix) ->
                        FilterChip(
                            selected = resolved?.platformType == type,
                            onClick = {
                                if (inputUrlOrHandle.isBlank() || !inputUrlOrHandle.startsWith(prefix)) {
                                    inputUrlOrHandle = prefix
                                }
                            },
                            label = { Text(type.displayName) }
                        )
                    }
                }

                OutlinedTextField(
                    value = inputUrlOrHandle,
                    onValueChange = { inputUrlOrHandle = it },
                    label = { Text("主页网址 / 用户名 / 频道 ID") },
                    placeholder = { Text("例如: https://x.com/TeamCherryGames") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // 识别结果徽章
                resolved?.let { r ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "已智能匹配: ${r.platformType.displayName}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "标识: ${r.identifier} | 路由: ${r.feedPathOrUrl}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = customTitle,
                    onValueChange = { customTitle = it },
                    label = { Text("展示名称 (例如: Official X / Twitter)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 测试连接按钮与测试状态展示
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            val r = resolved ?: return@FilledTonalButton
                            isTesting = true
                            testResult = null
                            coroutineScope.launch {
                                try {
                                    val item = onTestConnection(r.feedPathOrUrl)
                                    testResult = Result.success(item)
                                } catch (e: Exception) {
                                    testResult = Result.failure(e)
                                } finally {
                                    isTesting = false
                                }
                            }
                        },
                        enabled = resolved != null && !isTesting
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("正在连接测试...")
                        } else {
                            Text("测试源连通性")
                        }
                    }
                }

                testResult?.let { res ->
                    res.onSuccess { item ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "连接成功！最新动态预览：",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }.onFailure { err ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "测试失败: ${err.localizedMessage ?: "连接超时"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val r = resolved ?: return@Button
                    onConfirm(
                        r.platformType,
                        r.identifier,
                        r.feedPathOrUrl,
                        customTitle.ifBlank { r.suggestedTitle }
                    )
                    onDismiss()
                },
                enabled = resolved != null
            ) {
                Text("确认添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
