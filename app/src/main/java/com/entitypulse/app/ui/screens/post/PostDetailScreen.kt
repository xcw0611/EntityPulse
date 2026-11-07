package com.entitypulse.app.ui.screens.post

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.entitypulse.app.data.model.FeedItemWithMeta
import com.entitypulse.app.network.TranslationManager
import com.entitypulse.app.ui.MainViewModel
import com.entitypulse.app.ui.screens.feed.PlatformBadge
import com.entitypulse.app.ui.theme.VipGold
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    postId: Long,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var itemWithMeta by remember { mutableStateOf<FeedItemWithMeta?>(null) }

    var isTranslated by remember { mutableStateOf(false) }
    var isTranslating by remember { mutableStateOf(false) }
    var translatedText by remember { mutableStateOf<String?>(null) }
    var translationError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(postId) {
        itemWithMeta = viewModel.repository.getFeedItemWithMeta(postId)
        // 进入详情页后自动标为已读
        viewModel.markItemRead(postId, true)
    }

    val item = itemWithMeta

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item?.entityName ?: "动态详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (item != null) {
                        IconButton(onClick = {
                            viewModel.toggleItemBookmark(item.item.id, item.item.isBookmarked)
                            itemWithMeta = item.copy(item = item.item.copy(isBookmarked = !item.item.isBookmarked))
                        }) {
                            Icon(
                                imageVector = if (item.item.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "收藏",
                                tint = if (item.item.isBookmarked) VipGold else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, item.item.title)
                                putExtra(Intent.EXTRA_TEXT, "${item.item.title}\n${item.item.originalUrl}")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "分享动态"))
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "分享")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (item != null) {
                Surface(
                    tonalElevation = 4.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (isTranslated) {
                                    isTranslated = false
                                } else if (translatedText != null) {
                                    isTranslated = true
                                } else {
                                    isTranslating = true
                                    translationError = null
                                    coroutineScope.launch {
                                        val res = TranslationManager.translate(
                                            text = item.item.contentPlainText.ifBlank { item.item.title }
                                        )
                                        isTranslating = false
                                        res.onSuccess {
                                            translatedText = it
                                            isTranslated = true
                                        }.onFailure { e ->
                                            translationError = e.localizedMessage ?: "翻译失败"
                                        }
                                    }
                                }
                            },
                            enabled = !isTranslating,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isTranslating) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("翻译中...")
                            } else {
                                Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isTranslated) "查看原文" else "一键翻译")
                            }
                        }

                        if (item.item.originalUrl.isNotBlank()) {
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.item.originalUrl)).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("原网查看")
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        if (item == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val feed = item.item
            val mediaUrls = remember(feed.mediaUrlsJson) {
                if (!feed.mediaUrlsJson.isNullOrBlank()) {
                    runCatching {
                        val arr = JSONArray(feed.mediaUrlsJson)
                        (0 until arr.length()).map { arr.getString(it) }
                    }.getOrDefault(emptyList())
                } else if (!feed.thumbnailUrl.isNullOrBlank()) {
                    listOf(feed.thumbnailUrl)
                } else {
                    emptyList()
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // 主体名片横幅
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!item.entityAvatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = item.entityAvatarUrl,
                            contentDescription = item.entityName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Text(
                                text = item.entityName.take(1).uppercase(),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.entityName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (item.isEntityVip) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "VIP",
                                    tint = VipGold,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(feed.publishedAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    PlatformBadge(item.platformType)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 文章/动态主标题
                Text(
                    text = feed.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                if (!feed.author.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "作者: ${feed.author}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                // 正文内容
                if (isTranslated && !translatedText.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Translate,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "由智能双引擎翻译（已译为简体中文）",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Text(
                        text = translatedText ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.3f
                    )
                } else {
                    Text(
                        text = feed.contentPlainText,
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.3f
                    )
                }

                if (translationError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "翻译提示: $translationError",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // 媒体图片展示
                if (mediaUrls.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "媒体预览 (${mediaUrls.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    mediaUrls.forEach { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}
