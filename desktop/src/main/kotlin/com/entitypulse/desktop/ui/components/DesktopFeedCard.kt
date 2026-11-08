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

fun isForeignText(text: String): Boolean {
    val clean = text.trim()
    if (clean.isBlank()) return false
    var chineseCount = 0
    var latinCount = 0
    for (ch in clean) {
        val ub = Character.UnicodeBlock.of(ch)
        if (ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
            ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A) {
            chineseCount++
        } else if (ch.isLetter()) {
            latinCount++
        }
    }
    return latinCount > 6 && (chineseCount < latinCount / 2)
}

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

    val combinedOriginalText = remember(item.title, item.contentPlainText) {
        val t = item.title.trim()
        val c = item.contentPlainText.trim()
        if (t.isNotBlank() && c.isNotBlank() && !c.startsWith(t)) "$t\n\n$c" else if (c.isNotBlank()) c else t
    }

    val needsTranslation = remember(combinedOriginalText) {
        isForeignText(combinedOriginalText)
    }

    var isTranslating by remember { mutableStateOf(false) }
    var translatedText by remember { mutableStateOf<String?>(null) }
    var showOriginal by remember { mutableStateOf(!needsTranslation) }
    var isExpanded by remember { mutableStateOf(false) }

    // 自动在加载时直接请求中文翻译（若为非中文动态）
    LaunchedEffect(item.id, needsTranslation) {
        if (needsTranslation && translatedText == null) {
            isTranslating = true
            val res = TranslationManager.translate(combinedOriginalText, client = nodeManager.getOkHttpClient())
            val trans = res.getOrNull()
            if (!trans.isNullOrBlank() && trans != combinedOriginalText) {
                translatedText = trans
                showOriginal = false // 默认直接显示中文译文
            } else {
                showOriginal = true
            }
            isTranslating = false
        }
    }

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
                            color = Color(0xFFF1F5F9)
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

                        if (translatedText != null && !showOriginal) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "中文译文",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    if (!item.author.isNullOrBlank()) {
                        Text(
                            text = "@${item.author}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                // Date in yyyy/MM/dd HH:mm
                Text(
                    text = dateFormat.format(Date(item.publishedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF94A3B8)
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
                        tint = if (item.isRead) Color(0xFF64748B) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Text Content (Title & Body)
            // 当翻译就绪且处于译文模式时，直接呈现翻译后的完整段落
            val currentDisplayText = if (translatedText != null && !showOriginal) {
                translatedText!!
            } else {
                combinedOriginalText
            }

            if (currentDisplayText.isNotBlank()) {
                Text(
                    text = currentDisplayText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFF1F5F9),
                    maxLines = if (isExpanded) Int.MAX_VALUE else 6,
                    overflow = TextOverflow.Ellipsis
                )
                if (currentDisplayText.length > 200) {
                    Text(
                        text = if (isExpanded) "收起内容" else "展开全文",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { isExpanded = !isExpanded }
                            .padding(vertical = 2.dp)
                    )
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
                // 翻译 / 查看原文切换按钮
                if (needsTranslation || translatedText != null) {
                    FilledTonalButton(
                        onClick = {
                            if (translatedText != null) {
                                showOriginal = !showOriginal
                            } else {
                                isTranslating = true
                                coroutineScope.launch {
                                    val res = TranslationManager.translate(combinedOriginalText, client = nodeManager.getOkHttpClient())
                                    val t = res.getOrNull()
                                    if (!t.isNullOrBlank()) {
                                        translatedText = t
                                        showOriginal = false
                                    }
                                    isTranslating = false
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp),
                        enabled = !isTranslating
                    ) {
                        if (isTranslating) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("翻译中...", style = MaterialTheme.typography.labelSmall)
                        } else {
                            Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (translatedText != null && !showOriginal) "显示原文" else "显示译文",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Bookmark toggle
                IconButton(
                    onClick = { onToggleBookmark(!item.isBookmarked) },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = if (item.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "收藏",
                        tint = if (item.isBookmarked) Color(0xFFF59E0B) else Color(0xFF94A3B8),
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
                            tint = Color(0xFF94A3B8),
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
