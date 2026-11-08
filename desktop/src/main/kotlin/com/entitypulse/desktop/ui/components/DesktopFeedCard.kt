package com.entitypulse.desktop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.entitypulse.desktop.data.model.FeedItemWithMeta
import com.entitypulse.desktop.data.model.PlatformType
import com.entitypulse.desktop.network.NodeManager
import com.entitypulse.desktop.network.TranslationManager
import com.entitypulse.desktop.ui.DesktopAsyncImage
import com.entitypulse.desktop.ui.DesktopBrowserHelper
import com.entitypulse.desktop.ui.theme.DarkBorder
import com.entitypulse.desktop.ui.theme.DarkSurfaceElevated
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

private val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINA)

@Composable
fun DesktopFeedCard(
    feedMeta: FeedItemWithMeta,
    nodeManager: NodeManager,
    onToggleRead: (Boolean) -> Unit,
    onToggleBookmark: (Boolean) -> Unit,
    onSelectEntity: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val item = feedMeta.item

    var isTranslating by remember { mutableStateOf(false) }
    var translatedText by remember { mutableStateOf<String?>(null) }
    var isExpanded by remember { mutableStateOf(false) }

    val mediaUrls = remember(item.mediaUrlsJson) {
        val json = item.mediaUrlsJson
        if (!json.isNullOrBlank()) {
            runCatching {
                val array = JSONArray(json)
                (0 until array.length()).map { array.getString(it) }
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (item.isRead) 1.dp else 1.5.dp,
                color = if (item.isRead) DarkBorder else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isRead) DarkSurfaceElevated.copy(alpha = 0.6f) else DarkSurfaceElevated
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header row: Entity info + Platform badge + Date + Read status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Entity avatar (clickable to filter)
                DesktopAsyncImage(
                    url = feedMeta.entityAvatar,
                    contentDescription = feedMeta.entityName,
                    nodeManager = nodeManager,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onSelectEntity() }
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectEntity() }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = feedMeta.entityName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Platform Badge
                        Surface(
                            color = Color(feedMeta.platformType.badgeColor).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = feedMeta.platformType.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(feedMeta.platformType.badgeColor),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (!item.author.isNullOrBlank()) {
                        Text(
                            text = "@${item.author}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Date in yyyy/MM/dd HH:mm
                Text(
                    text = dateFormat.format(Date(item.publishedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Read / Unread toggle
                IconButton(
                    onClick = { onToggleRead(!item.isRead) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (item.isRead) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (item.isRead) "已读" else "未读",
                        tint = if (item.isRead) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Title
            if (item.title.isNotBlank()) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Body text
            if (item.contentPlainText.isNotBlank()) {
                Text(
                    text = item.contentPlainText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    maxLines = if (isExpanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.contentPlainText.length > 200) {
                    Text(
                        text = if (isExpanded) "收起内容" else "展开全文",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { isExpanded = !isExpanded }
                            .padding(vertical = 2.dp)
                    )
                }
            }

            // Translation block
            AnimatedVisibility(visible = translatedText != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("中文译文：", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = translatedText ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Media gallery
            if (mediaUrls.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    mediaUrls.take(4).forEach { url ->
                        DesktopAsyncImage(
                            url = url,
                            contentDescription = "媒体附件",
                            nodeManager = nodeManager,
                            modifier = Modifier
                                .weight(1f)
                                .height(160.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                                .clickable {
                                    DesktopBrowserHelper.openInBrowser(url)
                                }
                        )
                    }
                }
            } else if (!item.thumbnailUrl.isNullOrBlank()) {
                DesktopAsyncImage(
                    url = item.thumbnailUrl,
                    contentDescription = "缩略图",
                    nodeManager = nodeManager,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, DarkBorder, RoundedCornerShape(8.dp))
                        .clickable {
                            item.originalUrl?.let { DesktopBrowserHelper.openInBrowser(it) }
                        }
                )
            }

            // Action footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Translation button
                FilledTonalButton(
                    onClick = {
                        if (translatedText != null) {
                            translatedText = null
                        } else {
                            val textToTranslate = item.title + "\n" + item.contentPlainText
                            if (textToTranslate.isNotBlank()) {
                                isTranslating = true
                                coroutineScope.launch {
                                    val res = TranslationManager.translate(textToTranslate, client = nodeManager.getOkHttpClient())
                                    isTranslating = false
                                    translatedText = res.getOrNull()
                                }
                            }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp),
                    enabled = !isTranslating
                ) {
                    if (isTranslating) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (translatedText != null) "隐藏翻译" else "翻译为中文", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Bookmark toggle
                IconButton(
                    onClick = { onToggleBookmark(!item.isBookmarked) },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = if (item.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "收藏",
                        tint = if (item.isBookmarked) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Copy link button
                if (!item.originalUrl.isNullOrBlank()) {
                    IconButton(
                        onClick = { DesktopBrowserHelper.setClipboardText(item.originalUrl) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制链接",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Open in system browser button
                if (!item.originalUrl.isNullOrBlank()) {
                    Button(
                        onClick = { DesktopBrowserHelper.openInBrowser(item.originalUrl) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("在系统浏览器中打开原帖", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
