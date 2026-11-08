package com.entitypulse.app.ui.dialogs

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.entitypulse.app.data.model.PlatformType
import com.entitypulse.app.data.model.PredefinedChannel
import com.entitypulse.app.network.ParsedFeedItem
import com.entitypulse.app.network.PlatformResolver
import com.entitypulse.app.network.ResolvedPlatform
import kotlinx.coroutines.launch
import java.net.URLEncoder

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

    // 内置浏览器小窗口
    var showBrowserDialog by remember { mutableStateOf(false) }

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

    if (showBrowserDialog) {
        val q = runCatching { URLEncoder.encode("$entityName 官方社交媒体", "UTF-8") }.getOrDefault(entityName)
        InAppBrowserDialog(
            initialUrl = "https://www.bing.com/search?q=$q",
            entityName = entityName,
            officialSiteUrl = null,
            addedChannels = emptyList(),
            onChannelAdded = { ch ->
                onConfirm(ch.platformType, ch.identifier, ch.feedPathOrUrl, ch.title)
                showBrowserDialog = false
                onDismiss()
            },
            onRemoveChannel = {},
            onDismiss = { showBrowserDialog = false }
        )
    }

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
                // 打开内置浏览器检索按钮
                Button(
                    onClick = { showBrowserDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("打开内置小窗口检索并锁定账号")
                }

                HorizontalDivider()

                Text(
                    text = "或者直接粘贴该主体在各大平台的个人主页网址、账号 handle 或官方标准 RSS 链接：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

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
                                    text = "已智能识别: ${r.platformType.displayName}",
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
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "测试通过！最新动态预览：",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                    res.onFailure { err ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "连接提示: ${err.message ?: "未知异常"}",
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
                    val finalTitle = customTitle.ifBlank { r.suggestedTitle }
                    onConfirm(r.platformType, r.identifier, r.feedPathOrUrl, finalTitle)
                    onDismiss()
                },
                enabled = resolved != null
            ) {
                Text("确认绑定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
