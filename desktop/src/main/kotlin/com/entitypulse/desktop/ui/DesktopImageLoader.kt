package com.entitypulse.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.entitypulse.desktop.network.NodeManager
import com.entitypulse.desktop.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image
import java.util.concurrent.ConcurrentHashMap

object DesktopImageCache {
    private val memoryCache = ConcurrentHashMap<String, ImageBitmap>()

    fun get(url: String): ImageBitmap? = memoryCache[url]
    fun put(url: String, bitmap: ImageBitmap) {
        if (memoryCache.size > 200) {
            val oldest = memoryCache.keys().nextElement()
            memoryCache.remove(oldest)
        }
        memoryCache[url] = bitmap
    }
}

suspend fun loadRemoteImage(url: String, client: OkHttpClient): ImageBitmap? = withContext(Dispatchers.IO) {
    val cached = DesktopImageCache.get(url)
    if (cached != null) return@withContext cached

    try {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val bytes = response.body?.bytes() ?: return@withContext null
            val skiaImage = Image.makeFromEncoded(bytes)
            val composeBitmap = skiaImage.toComposeImageBitmap()
            DesktopImageCache.put(url, composeBitmap)
            composeBitmap
        }
    } catch (e: Exception) {
        AppLogger.w("DesktopImageLoader", "加载图片失败 [$url]: ${e.message}")
        null
    }
}

@Composable
fun DesktopAsyncImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    nodeManager: NodeManager? = null,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackColor: Color = Color(0xFF2D2E30)
) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(if (url != null) DesktopImageCache.get(url) else null) }
    var isLoading by remember(url) { mutableStateOf(url != null && bitmap == null) }
    var hasError by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            bitmap = null
            isLoading = false
            hasError = false
            return@LaunchedEffect
        }
        if (bitmap != null) {
            isLoading = false
            return@LaunchedEffect
        }

        isLoading = true
        hasError = false
        val client = nodeManager?.getOkHttpClient(connectTimeoutSec = 8, readTimeoutSec = 10)
            ?: NodeManager().getOkHttpClient(connectTimeoutSec = 8, readTimeoutSec = 10)
        val loaded = loadRemoteImage(url, client)
        if (loaded != null) {
            bitmap = loaded
            hasError = false
        } else {
            hasError = true
        }
        isLoading = false
    }

    Box(
        modifier = modifier.background(fallbackColor),
        contentAlignment = Alignment.Center
    ) {
        when {
            bitmap != null -> {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale
                )
            }
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
            hasError -> {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = "图片加载失败",
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(24.dp)
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "无图片",
                    tint = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
