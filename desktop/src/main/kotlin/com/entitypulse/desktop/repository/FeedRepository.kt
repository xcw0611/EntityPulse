package com.entitypulse.desktop.repository

import com.entitypulse.desktop.data.DatabaseManager
import com.entitypulse.desktop.data.model.*
import com.entitypulse.desktop.network.*
import com.entitypulse.desktop.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class FeedRepository(
    val database: DatabaseManager,
    val nodeManager: NodeManager
) {
    fun getHttpClient(): OkHttpClient {
        return nodeManager.getOkHttpClient(connectTimeoutSec = 12, readTimeoutSec = 18)
    }

    // --- Entity Operations ---
    suspend fun createEntity(
        name: String,
        avatarUrl: String? = null,
        bio: String? = null,
        category: String = "默认",
        isVip: Boolean = false,
        isNotifyEnabled: Boolean = true
    ): Long {
        return database.createEntity(
            name = name,
            avatarUrl = avatarUrl,
            bio = bio,
            category = category,
            isVip = isVip,
            isNotifyEnabled = isNotifyEnabled
        )
    }

    suspend fun updateEntityAvatar(id: Long, avatarUrl: String?) {
        database.updateEntityAvatar(id, avatarUrl?.trim()?.ifBlank { null })
    }

    suspend fun toggleVip(id: Long, isVip: Boolean) {
        database.toggleEntityVip(id, isVip)
    }

    suspend fun toggleNotify(id: Long, isNotify: Boolean) {
        database.toggleEntityNotify(id, isNotify)
    }

    suspend fun deleteEntity(id: Long) {
        database.deleteEntity(id)
    }

    // --- Channel Operations ---
    suspend fun getChannelsForEntity(entityId: Long): List<SourceChannelRecord> {
        return database.getChannelsForEntity(entityId)
    }

    suspend fun addChannel(
        entityId: Long,
        platformType: PlatformType,
        identifier: String,
        resolvedFeedUrl: String,
        title: String
    ): Long {
        return database.addChannel(
            entityId = entityId,
            platformType = platformType,
            identifier = identifier,
            resolvedFeedUrl = resolvedFeedUrl,
            title = title
        )
    }

    suspend fun toggleChannel(channelId: Long, isEnabled: Boolean) {
        database.toggleChannel(channelId, isEnabled)
    }

    suspend fun deleteChannel(channelId: Long) {
        database.deleteChannel(channelId)
    }

    // --- Feed Operations ---
    suspend fun getFeeds(entityId: Long? = null, limit: Int = 150): List<FeedItemWithMeta> {
        return database.getFeeds(entityId, limit)
    }

    suspend fun markAsRead(feedId: Long, isRead: Boolean = true) {
        database.markFeedRead(feedId, isRead)
    }

    suspend fun markAllAsReadForEntity(entityId: Long) {
        database.markAllReadForEntity(entityId)
    }

    suspend fun markAllAsRead() {
        database.markAllRead()
    }

    suspend fun toggleBookmark(feedId: Long, isBookmarked: Boolean) {
        database.toggleBookmark(feedId, isBookmarked)
    }

    // --- Sync & Network Engine ---

    /**
     * 测试指定的订阅源，返回抓取到的最新一条动态预览，若失败则抛出包含诊断建议的异常。
     */
    suspend fun testChannel(feedPathOrUrl: String): ParsedFeedItem = withContext(Dispatchers.IO) {
        val trimmed = feedPathOrUrl.trim()
        if (trimmed.isBlank() || trimmed.endsWith("/none", ignoreCase = true) || trimmed == "未收录") {
            throw IllegalStateException("该平台未收录独立官方账号或未配置有效订阅地址，无法测试连通性。")
        }
        val parsed = fetchFeed(feedPathOrUrl)
        if (parsed.items.isEmpty()) {
            throw IllegalStateException("连接成功，但该频道近期未发布任何公开动态")
        }
        parsed.items.first()
    }

    /**
     * 同步单个频道，返回本次抓取到的“全新动态项”列表
     */
    suspend fun syncChannel(channel: SourceChannelRecord): List<FeedItemRecord> = withContext(Dispatchers.IO) {
        val newSavedItems = mutableListOf<FeedItemRecord>()
        AppLogger.i("SyncEngine", "开始同步频道: ${channel.title} (ID=${channel.id}, URL=${channel.resolvedFeedUrl})")
        val feedUrl = channel.resolvedFeedUrl.trim()
        if (feedUrl.isBlank() || feedUrl.endsWith("/none", ignoreCase = true) || channel.identifier == "未收录") {
            val skippedMsg = "未配置有效订阅地址或未收录独立官方账号"
            AppLogger.i("SyncEngine", "频道 [${channel.title}] 跳过同步: $skippedMsg")
            database.updateChannelSyncStatus(channel.id, System.currentTimeMillis(), skippedMsg)
            return@withContext emptyList()
        }
        try {
            val parsedFeed = fetchFeed(channel.resolvedFeedUrl)

            for (item in parsedFeed.items) {
                val mediaJson = if (item.mediaUrls.isNotEmpty()) {
                    JSONArray(item.mediaUrls).toString()
                } else null

                val record = FeedItemRecord(
                    id = 0,
                    entityId = channel.entityId,
                    channelId = channel.id,
                    guid = item.guid,
                    title = item.title,
                    contentPlainText = item.contentPlainText,
                    contentHtml = item.contentHtml,
                    author = item.author,
                    publishedAt = item.publishedAt,
                    originalUrl = item.link,
                    thumbnailUrl = item.thumbnailUrl,
                    mediaUrlsJson = mediaJson
                )

                val rowId = database.insertIgnoreFeedItem(record)
                if (rowId != -1L) {
                    newSavedItems.add(record.copy(id = rowId))
                }
            }

            database.updateChannelSyncStatus(channel.id, System.currentTimeMillis(), null)
            AppLogger.i("SyncEngine", "频道 [${channel.title}] 同步成功，拉取到 ${parsedFeed.items.size} 条，新入库 ${newSavedItems.size} 条")
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "同步失败"
            AppLogger.e("SyncEngine", "频道 [${channel.title}] 同步异常: $errorMsg", e)
            database.updateChannelSyncStatus(channel.id, System.currentTimeMillis(), errorMsg)
        }
        newSavedItems
    }

    /**
     * 同步所有已启用的频道
     */
    suspend fun syncAllChannels(): List<Pair<SourceChannelRecord, List<FeedItemRecord>>> = withContext(Dispatchers.IO) {
        val channels = database.getAllEnabledChannels()
        val results = mutableListOf<Pair<SourceChannelRecord, List<FeedItemRecord>>>()
        for (channel in channels) {
            val newItems = syncChannel(channel)
            if (newItems.isNotEmpty()) {
                results.add(channel to newItems)
            }
        }
        results
    }

    suspend fun fetchFeed(feedPathOrUrl: String): ParsedFeed = withContext(Dispatchers.IO) {
        val cleanTarget = feedPathOrUrl.trim()
        if (cleanTarget.isBlank() || cleanTarget.endsWith("/none", ignoreCase = true) || cleanTarget == "未收录") {
            throw IllegalStateException("未配置有效订阅地址或未收录独立官方账号")
        }

        // 1. YouTube 原生官方 Atom XML 订阅抓取
        if (YouTubeResolver.isYouTubeTarget(cleanTarget)) {
            try {
                val nativeRssUrl = YouTubeResolver.resolveToNativeRssUrl(cleanTarget, getHttpClient())
                AppLogger.i("FeedRepository", "YouTube 渠道已解析为官方原生 Atom RSS: $nativeRssUrl")
                val xml = executeRequest(nativeRssUrl)
                return@withContext FeedParser.parse(xml)
            } catch (e: Exception) {
                AppLogger.e("FeedRepository", "YouTube 原生抓取异常: ${e.message}", e)
                throw diagnoseNetworkError(e, cleanTarget)
            }
        }

        // 2. 若为 Twitter / X，优先调用官方 Syndication 接口免登录抓取
        val isTwitter = cleanTarget.startsWith("/twitter/user/", ignoreCase = true) ||
                cleanTarget.contains("twitter.com", ignoreCase = true) ||
                cleanTarget.contains("x.com", ignoreCase = true)

        if (isTwitter) {
            val username = extractTwitterUsername(cleanTarget)
            if (username.isNotBlank()) {
                try {
                    return@withContext TwitterSyndicationParser.fetchTimeline(username, getHttpClient())
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    if (msg.contains("429") || msg.contains("频控")) {
                        throw e
                    }
                    AppLogger.w("FeedRepository", "Twitter Syndication 接口抓取失败 ($msg)，尝试备用 RSSHub 节点池")
                }
            }
        }

        // 3. Bilibili 动态/视频：若传入的是非数字关键词，自动联网解析为数字 UID
        var resolvedPath = cleanTarget
        if (resolvedPath.startsWith("/bilibili/user/video/", ignoreCase = true)) {
            resolvedPath = BilibiliResolver.resolveNumericFeedPath(resolvedPath, getHttpClient())
        }

        // 4. 传统 RSS/Atom 直连抓取或通过 RSSHub 镜像池
        val isDirectUrl = resolvedPath.startsWith("http://", ignoreCase = true) ||
                resolvedPath.startsWith("https://", ignoreCase = true)

        if (isDirectUrl) {
            try {
                val responseText = executeRequest(resolvedPath)
                val trimmed = responseText.trim()
                if (trimmed.startsWith("<!DOCTYPE", ignoreCase = true) || trimmed.startsWith("<html", ignoreCase = true)) {
                    // 尝试 RSS Autodiscovery 探测
                    val discoveredFeedUrl = discoverRssFeedUrl(trimmed, resolvedPath)
                    if (discoveredFeedUrl != null) {
                        AppLogger.i("FeedRepository", "从官网 HTML 中自动发现 RSS 订阅源: $discoveredFeedUrl")
                        val feedXml = executeRequest(discoveredFeedUrl)
                        return@withContext FeedParser.parse(feedXml)
                    }

                    // 尝试常见静态后缀
                    val commonSuffixes = listOf("/feed", "/rss", "/rss.xml", "/feed.xml", "/blog/feed")
                    for (suffix in commonSuffixes) {
                        try {
                            val candidate = resolvedPath.trimEnd('/') + suffix
                            val testXml = executeRequest(candidate)
                            val candTrim = testXml.trim()
                            if (!candTrim.startsWith("<html", ignoreCase = true) && !candTrim.startsWith("<!DOCTYPE", ignoreCase = true)) {
                                AppLogger.i("FeedRepository", "通过常见后缀命中 RSS: $candidate")
                                return@withContext FeedParser.parse(testXml)
                            }
                        } catch (_: Exception) {}
                    }

                    throw IllegalStateException("该官网主页未声明公开的 RSS/Atom 订阅源，建议配置该主体的社交媒体渠道（如 X、YouTube 等），或在右侧属性面板中直接点击在浏览器中打开。")
                }
                return@withContext FeedParser.parse(trimmed)
            } catch (e: Exception) {
                throw diagnoseNetworkError(e, resolvedPath)
            }
        }

        // 路径为社媒路由（如 /bilibili/user/... 或备用 /twitter/...）
        val candidateNodes = nodeManager.getAllCandidateNodes()
        var lastError: Exception? = null

        for (node in candidateNodes) {
            val fullUrl = PlatformResolver.buildFinalUrl(
                feedPathOrUrl = resolvedPath,
                baseRssHubUrl = node,
                token = nodeManager.accessToken.ifBlank { null }
            )
            try {
                val xml = executeRequest(fullUrl)
                return@withContext FeedParser.parse(xml)
            } catch (e: Exception) {
                lastError = e
            }
        }

        throw diagnoseNetworkError(lastError ?: IllegalStateException("无法连通任何数据节点"), resolvedPath)
    }

    private fun discoverRssFeedUrl(html: String, baseUrl: String): String? {
        val linkPattern = Pattern.compile(
            "<link\\s+[^>]*rel=[\"']alternate[\"'][^>]*type=[\"'](application/(?:rss|atom)\\+xml|text/xml)[\"'][^>]*href=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
        )
        var matcher = linkPattern.matcher(html)
        if (matcher.find()) {
            val href = matcher.group(2)
            if (!href.isNullOrBlank()) {
                return resolveRelativeUrl(baseUrl, href)
            }
        }
        val linkPatternReverse = Pattern.compile(
            "<link\\s+[^>]*type=[\"'](application/(?:rss|atom)\\+xml|text/xml)[\"'][^>]*rel=[\"']alternate[\"'][^>]*href=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
        )
        matcher = linkPatternReverse.matcher(html)
        if (matcher.find()) {
            val href = matcher.group(2)
            if (!href.isNullOrBlank()) {
                return resolveRelativeUrl(baseUrl, href)
            }
        }
        return null
    }

    private fun resolveRelativeUrl(baseUrl: String, href: String): String {
        return try {
            val baseUri = URI(baseUrl)
            baseUri.resolve(href).toString()
        } catch (_: Exception) {
            if (href.startsWith("http://", ignoreCase = true) || href.startsWith("https://", ignoreCase = true)) {
                href
            } else {
                baseUrl.trimEnd('/') + "/" + href.trimStart('/')
            }
        }
    }

    private fun extractTwitterUsername(pathOrUrl: String): String {
        val trimmed = pathOrUrl.trim()
        if (trimmed.startsWith("/twitter/user/", ignoreCase = true)) {
            return trimmed.removePrefix("/twitter/user/").substringBefore('/').substringBefore('?').trim()
        }
        if (trimmed.contains("twitter.com/") || trimmed.contains("x.com/")) {
            val after = if (trimmed.contains("x.com/")) trimmed.substringAfter("x.com/") else trimmed.substringAfter("twitter.com/")
            return after.substringBefore('/').substringBefore('?').removePrefix("@").trim()
        }
        return ""
    }

    private fun executeRequest(url: String): String {
        AppLogger.d("HttpRequest", "发起 HTTP 请求: $url")
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.8")
            .build()

        getHttpClient().newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) {
                val preview = body?.take(200)?.replace("\n", " ") ?: ""
                AppLogger.w("HttpRequest", "HTTP 失败: code=${response.code} msg=${response.message} [URL: $url] - 预览: $preview")
                throw IllegalStateException("HTTP ${response.code} ${response.message}")
            }
            if (body.isNullOrBlank()) {
                AppLogger.w("HttpRequest", "响应为空: HTTP ${response.code} [URL: $url]")
                throw IllegalStateException("响应内容为空")
            }
            AppLogger.d("HttpRequest", "请求成功: HTTP ${response.code} [URL: $url, 数据长度: ${body.length}]")
            return body
        }
    }

    private fun diagnoseNetworkError(e: Throwable, target: String): Exception {
        val msg = e.localizedMessage ?: ""

        if (msg.contains("未声明公开的 RSS") ||
            msg.contains("频控") ||
            msg.contains("未找到 YouTube 频道") ||
            msg.contains("不存在") ||
            msg.contains("未在") ||
            msg.contains("未发布任何公开动态") ||
            msg.contains("已注销") ||
            msg.contains("冻结") ||
            msg.contains("未收录") ||
            msg.contains("未配置有效订阅地址")
        ) {
            return if (e is Exception) e else IllegalStateException(msg, e)
        }

        // HTTP 404 / Not Found
        if (msg.contains("404") || msg.contains("Not Found", ignoreCase = true)) {
            val platformName = when {
                target.contains("twitter", true) || target.contains("x.com", true) -> "X (Twitter)"
                target.contains("youtube", true) || target.contains("youtu.be", true) -> "YouTube"
                target.contains("bilibili", true) -> "Bilibili"
                target.contains("steam", true) -> "Steam"
                target.contains("reddit", true) -> "Reddit"
                else -> "目标平台"
            }
            val idPreview = target.substringAfterLast('/').substringBefore('?').removePrefix("@")
            return IllegalStateException(
                "$platformName 上未找到该账号或内容 (@$idPreview) [HTTP 404]。\n" +
                        "该作品或创作者可能未在此平台开设独立官方账号，或官方使用了不同名称。\n" +
                        "此报错非网络故障，建议在右侧面板中核对/修改账号标识，或直接删除该渠道。"
            )
        }

        // HTTP 429
        if (msg.contains("429") || msg.contains("Too Many Requests", ignoreCase = true)) {
            return IllegalStateException(
                "目标平台触发了临时访问频控 (HTTP 429 Too Many Requests)，将在片刻后自动恢复。此非网络或代理故障，请稍候再试。"
            )
        }

        // HTTP 403
        if (msg.contains("403") || msg.contains("Forbidden", ignoreCase = true)) {
            return IllegalStateException(
                "目标节点触发了安全防护或鉴权限制 (HTTP 403 Forbidden)。建议在【设置】中配置本地代理端口（默认 7897）或在浏览器中直接打开。"
            )
        }

        // 真正的底层网络/代理连通异常
        val isConnectionClosed = msg.contains("closed", ignoreCase = true) ||
                msg.contains("reset", ignoreCase = true) ||
                msg.contains("EOF", ignoreCase = true)
        val isTimeout = msg.contains("timeout", ignoreCase = true) || msg.contains("timed out", ignoreCase = true)
        val isRefused = msg.contains("refused", ignoreCase = true) || msg.contains("ConnectException", ignoreCase = true)
        val isDnsFailed = msg.contains("Unable to resolve host", ignoreCase = true) || msg.contains("UnknownHost", ignoreCase = true)

        val friendlyReason = when {
            isRefused -> "代理端口连接被拒绝 (Connection Refused)。请确认 Clash Meta 代理客户端已启动且本地端口（如 7897）设置正确。"
            isDnsFailed -> "网络域名解析失败，请检查网络连接状态或 DNS 配置。"
            isConnectionClosed -> "网络连接被对端中断或重置 (Connection closed)。若正在访问海外站点，通常由于国内直连受限或代理节点不稳定，请检查 Clash Meta 节点连通性。"
            isTimeout -> "网络请求超时，目标服务器响应过慢，请检查网络延迟。"
            else -> "连接异常: $msg"
        }
        return IllegalStateException(friendlyReason, e)
    }
}
