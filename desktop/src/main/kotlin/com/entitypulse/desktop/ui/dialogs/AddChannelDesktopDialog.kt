package com.entitypulse.desktop.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.entitypulse.desktop.data.model.PlatformType
import com.entitypulse.desktop.network.ParsedFeedItem
import com.entitypulse.desktop.network.PlatformResolver
import com.entitypulse.desktop.network.ResolvedPlatform
import com.entitypulse.desktop.ui.DesktopBrowserHelper
import com.entitypulse.desktop.ui.theme.DarkBorder
import com.entitypulse.desktop.ui.theme.DarkSurfaceElevated
import kotlinx.coroutines.launch
import java.net.URLEncoder

@Composable
fun AddChannelDesktopDialog(
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

    LaunchedEffect(inputUrlOrHandle) {
        val trimmed = inputUrlOrHandle.trim()
        if (trimmed.isNotBlank()) {
            val r = PlatformResolver.resolve(trimmed)
            resolved = r
            if (customTitle.isBlank() || customTitle.startsWith("X /") || customTitle.startsWith("Bilibili") || customTitle.startsWith("YouTube") || customTitle.startsWith("Steam") || customTitle.startsWith("Reddit")) {
                customTitle = r.suggestedTitle
            }
        } else {
            resolved = null
        }
        testResult = null
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(680.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AddLink,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "为「$entityName」绑定平台动态源",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                // Browser & clipboard shortcuts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val clip = DesktopBrowserHelper.getClipboardText()
                            if (!clip.isNullOrBlank()) {
                                inputUrlOrHandle = clip.trim()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("粘贴剪贴板网址")
                    }

                    FilledTonalButton(
                        onClick = {
                            val q = runCatching { URLEncoder.encode("$entityName 官方社交媒体", "UTF-8") }.getOrDefault(entityName)
                            DesktopBrowserHelper.openInBrowser("https://www.bing.com/search?q=$q")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("在系统浏览器查找")
                    }
                }

                // Preset platform quick links
                Text(
                    text = "快速在系统浏览器中打开平台检索该主体：",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val quickSites = listOf(
                        "B站" to "https://search.bilibili.com/all?keyword=${URLEncoder.encode(entityName, "UTF-8")}",
                        "X (Twitter)" to "https://x.com/search?q=${URLEncoder.encode(entityName, "UTF-8")}&f=user",
                        "YouTube" to "https://www.youtube.com/results?search_query=${URLEncoder.encode(entityName, "UTF-8")}",
                        "Steam" to "https://store.steampowered.com/search/?term=${URLEncoder.encode(entityName, "UTF-8")}"
                    )
                    quickSites.forEach { (name, link) ->
                        AssistChip(
                            onClick = { DesktopBrowserHelper.openInBrowser(link) },
                            label = { Text(name) },
                            leadingIcon = { Icon(Icons.Default.Launch, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        )
                    }
                }

                HorizontalDivider(color = DarkBorder)

                // Input URL or Handle
                OutlinedTextField(
                    value = inputUrlOrHandle,
                    onValueChange = { inputUrlOrHandle = it },
                    label = { Text("平台主页网址 / 用户 Handle / 官方 RSS 链接") },
                    placeholder = { Text("例如: https://x.com/TeamCherryGames 或 @TeamCherryGames") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Resolved status card
                resolved?.let { res ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = Color(res.platformType.badgeColor).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = res.platformType.displayName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(res.platformType.badgeColor),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "标识: ${res.identifier}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                FilledTonalButton(
                                    onClick = {
                                        isTesting = true
                                        testResult = null
                                        coroutineScope.launch {
                                            testResult = runCatching { onTestConnection(res.feedPathOrUrl) }
                                            isTesting = false
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp),
                                    enabled = !isTesting
                                ) {
                                    if (isTesting) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("测试连通性", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            Text(
                                text = "路由目标: ${res.feedPathOrUrl}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Test connection outcome
                            testResult?.let { result ->
                                result.fold(
                                    onSuccess = { item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF065F46).copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text("连通成功！最新动态预览：", style = MaterialTheme.typography.labelSmall, color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                                                Text(item.title.ifBlank { item.contentPlainText }.take(80), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                            }
                                        }
                                    },
                                    onFailure = { err ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color(0xFF7F1D1D).copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(err.message ?: "连接失败", style = MaterialTheme.typography.bodySmall, color = Color(0xFFFCA5A5))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Channel Title
                OutlinedTextField(
                    value = customTitle,
                    onValueChange = { customTitle = it },
                    label = { Text("频道显示标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

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
                            val r = resolved
                            if (r != null) {
                                onConfirm(
                                    r.platformType,
                                    r.identifier,
                                    r.feedPathOrUrl,
                                    customTitle.ifBlank { r.suggestedTitle }
                                )
                                onDismiss()
                            }
                        },
                        enabled = resolved != null
                    ) {
                        Text("保存并绑定该频道")
                    }
                }
            }
        }
    }
}
