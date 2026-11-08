package com.entitypulse.app.repository

import com.entitypulse.app.data.AppDatabase
import com.entitypulse.app.data.model.*
import com.entitypulse.app.network.BilibiliResolver
import com.entitypulse.app.network.FeedParser
import com.entitypulse.app.network.NodeManager
import com.entitypulse.app.network.ParsedFeed
import com.entitypulse.app.network.ParsedFeedItem
import com.entitypulse.app.network.PlatformResolver
import com.entitypulse.app.network.TwitterSyndicationParser
import com.entitypulse.app.network.YouTubeResolver
import com.entitypulse.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class FeedRepository(
    private val database: AppDatabase,
    private val nodeManager: NodeManager
) {
    private val entityDao = database.entityDao()
    private val channelDao = database.channelDao()
    private val feedDao = database.feedDao()

    private fun getHttpClient(): OkHttpClient {
        return nodeManager.getOkHttpClient(connectTimeoutSec = 12, readTimeoutSec = 18)
    }

    // --- Entity Operations ---
    fun observeEntitiesWithStats(): Flow<List<EntityWithUnreadCount>> =
        entityDao.observeEntitiesWithStats()

    fun observeEntity(id: Long): Flow<EntityRecord?> =
        entityDao.observeById(id)

    fun observeEntityWithChannels(id: Long): Flow<EntityWithChannels?> =
        entityDao.observeEntityWithChannels(id)

    suspend fun getEntityWithChannels(id: Long): EntityWithChannels? =
        entityDao.getEntityWithChannels(id)

    suspend fun createEntity(
        name: String,
        avatarUrl: String? = null,
        bio: String? = null,
        category: String = "默认",
        isVip: Boolean = false,
        isNotifyEnabled: Boolean = true
    ): Long {
        return entityDao.insert(
            EntityRecord(
                name = name,
                avatarUrl = avatarUrl,
                bio = bio,
                category = category,
                isVip = isVip,
                isNotifyEnabled = isNotifyEnabled
            )
        )
    }

    suspend fun updateEntity(entity: EntityRecord) {
        entityDao.update(entity.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteEntity(entity: EntityRecord) {
        entityDao.delete(entity)
    }

    suspend fun toggleVip(id: Long, isVip: Boolean) {
        entityDao.updateVipStatus(id, isVip)
    }

    suspend fun toggleNotify(id: Long, enabled: Boolean) {
        entityDao.updateNotifyStatus(id, enabled)
    }

    // --- Channel Operations ---
    fun observeChannelsForEntity(entityId: Long): Flow<List<SourceChannelRecord>> =
        channelDao.observeByEntityId(entityId)

    suspend fun addChannel(
        entityId: Long,
        platformType: PlatformType,
        identifier: String,
        resolvedFeedUrl: String,
        title: String
    ): Long {
        return channelDao.insert(
            SourceChannelRecord(
                entityId = entityId,
                platformType = platformType,
                identifier = identifier,
                resolvedFeedUrl = resolvedFeedUrl,
                title = title
            )
        )
    }

    suspend fun addChannels(channels: List<SourceChannelRecord>) {
        channelDao.insertAll(channels)
    }

    suspend fun deleteChannel(channel: SourceChannelRecord) {
        channelDao.delete(channel)
    }

    suspend fun toggleChannel(channelId: Long, isEnabled: Boolean) {
        channelDao.setEnabled(channelId, isEnabled)
    }

    // --- Feed Operations ---
    fun observeFeeds(entityId: Long? = null, limit: Int = 100): Flow<List<FeedItemWithMeta>> {
        return if (entityId != null) {
            feedDao.observeByEntityWithMeta(entityId, limit)
        } else {
            feedDao.observeAllWithMeta(limit)
        }
    }

    suspend fun getFeedItemWithMeta(id: Long): FeedItemWithMeta? =
        feedDao.getByIdWithMeta(id)

    suspend fun markAsRead(id: Long, isRead: Boolean = true) {
        feedDao.updateReadStatus(id, isRead)
    }

    suspend fun markAllAsReadForEntity(entityId: Long) {
        feedDao.markAllAsReadForEntity(entityId)
    }

    suspend fun markAllAsRead() {
        feedDao.markAllAsRead()
    }

    suspend fun toggleBookmark(id: Long, isBookmarked: Boolean) {
        feedDao.updateBookmarkStatus(id, isBookmarked)
    }

    // --- Sync & Network Engine ---

    /**
     * 测试指定的订阅源，返回抓取到的最新一条动态预览，若失败则抛出包含诊断建议的异常。
     */
    suspend fun testChannel(feedPathOrUrl: String): ParsedFeedItem = withContext(Dispatchers.IO) {
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
        try {
            val parsedFeed = fetchFeed(channel.resolvedFeedUrl)

            for (item in parsedFeed.items) {
                val mediaJson = if (item.mediaUrls.isNotEmpty()) {
                    JSONArray(item.mediaUrls).toString()
                } else null

                val record = FeedItemRecord(
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

                val rowId = feedDao.insertIgnore(record)
                if (rowId != -1L) {
                    newSavedItems.add(record.copy(id = rowId))
                }
            }

            channelDao.updateSyncStatus(channel.id, System.currentTimeMillis(), null)
            AppLogger.i("SyncEngine", "频道 [${channel.title}] 同步成功，拉取到 ${parsedFeed.items.size} 条，新入库 ${newSavedItems.size} 条")
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "同步失败"
            AppLogger.e("SyncEngine", "频道 [${channel.title}] 同步异常: $errorMsg", e)
            channelDao.updateSyncStatus(channel.id, System.currentTimeMillis(), errorMsg)
        }
        newSavedItems
    }

    /**
     * 同步所有已启用的频道
     */
    suspend fun syncAllChannels(): List<Pair<SourceChannelRecord, List<FeedItemRecord>>> = withContext(Dispatchers.IO) {
        val channels = channelDao.getAllEnabledChannels()
        val results = mutableListOf<Pair<SourceChannelRecord, List<FeedItemRecord>>>()
        for (channel in channels) {
            val newItems = syncChannel(channel)
            if (newItems.isNotEmpty()) {
                results.add(channel to newItems)
            }
        }
        results
    }

    private suspend fun fetchFeed(feedPathOrUrl: String): ParsedFeed = withContext(Dispatchers.IO) {
        // 1. YouTube 原生官方 Atom XML 订阅抓取（彻底摆脱公共 RSSHub 节点 403 阻断）
        if (YouTubeResolver.isYouTubeTarget(feedPathOrUrl)) {
            try {
                val nativeRssUrl = YouTubeResolver.resolveToNativeRssUrl(feedPathOrUrl, getHttpClient())
                AppLogger.i("FeedRepository", "YouTube 渠道已解析为官方原生 Atom RSS: $nativeRssUrl")
                val xml = executeRequest(nativeRssUrl)
                return@withContext FeedParser.parse(xml)
            } catch (e: Exception) {
                AppLogger.e("FeedRepository", "YouTube 原生抓取异常: ${e.message}", e)
                throw diagnoseNetworkError(e, feedPathOrUrl)
            }
        }

        // 2. 若为 Twitter / X，优先调用官方 Syndication 接口免登录抓取
        val isTwitter = feedPathOrUrl.startsWith("/twitter/user/", ignoreCase = true) ||
                feedPathOrUrl.contains("twitter.com", ignoreCase = true) ||
                feedPathOrUrl.contains("x.com", ignoreCase = true)

        if (isTwitter) {
            val username = extractTwitterUsername(feedPathOrUrl)
            if (username.isNotBlank()) {
                try {
                    return@withContext TwitterSyndicationParser.fetchTimeline(username, getHttpClient())
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    // 若是频控 429，直接向用户展示精准诊断提示，避免 fallback 到死节点产生 Connection closed
                    if (msg.contains("429") || msg.contains("频控")) {
                        throw e
                    }
                    AppLogger.w("FeedRepository", "Twitter Syndication 接口抓取失败 ($msg)，尝试备用 RSSHub 节点池")
                }
            }
        }

        // 3. Bilibili 动态/视频：若传入的是非数字关键词，自动联网解析为数字 UID
        var resolvedPath = feedPathOrUrl
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
                    // 该 URL 返回了网页 HTML 而非标准 XML，尝试 RSS Autodiscovery 探测
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

                    throw IllegalStateException("该官网主页未声明公开的 RSS/Atom 订阅源，建议配置该主体的社交媒体渠道（如 X、YouTube 等），或在【主体详情】中直接点击官网网址进行网页浏览。")
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

        // 1. 若已经是我们抛出的明确业务诊断，原样保留
        if (msg.contains("未声明公开的 RSS") ||
            msg.contains("频控") ||
            msg.contains("未找到 YouTube 频道") ||
            msg.contains("不存在") ||
            msg.contains("未在") ||
            msg.contains("未发布任何公开动态") ||
            msg.contains("已注销") ||
            msg.contains("冻结")
        ) {
            return if (e is Exception) e else IllegalStateException(msg, e)
        }

        // 2. 账号/资源不存在判定 (HTTP 404 / Not Found)
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
                "该作品或创作者可能未在此平台开设独立官方账号（例如很多作品的视频统一在所属制作公司或创作者个人频道发布），或官方使用了不同名称。\n" +
                "此报错非网络故障，建议在【主体详情】中核对/修改账号标识，或直接删除该渠道。"
            )
        }

        // 3. 频控限制 (HTTP 429 Too Many Requests)
        if (msg.contains("429") || msg.contains("Too Many Requests", ignoreCase = true)) {
            return IllegalStateException(
                "目标平台触发了临时访问频控 (HTTP 429 Too Many Requests)，将在片刻后自动恢复。此非网络或代理故障，请稍候再试。"
            )
        }

        // 4. 防爬虫/鉴权阻断 (HTTP 403 Forbidden)
        if (msg.contains("403") || msg.contains("Forbidden", ignoreCase = true)) {
            return IllegalStateException(
                "目标节点触发了安全防护或鉴权限制 (HTTP 403 Forbidden)。若使用的是公共 RSSHub，可能被 Cloudflare 拦截；建议在【设置】中配置具有 Token 的自建节点或通过浏览器直接访问。"
            )
        }

        // 5. 真正的底层网络/代理连通异常
        val isConnectionClosed = msg.contains("closed", ignoreCase = true) ||
                msg.contains("reset", ignoreCase = true) ||
                msg.contains("EOF", ignoreCase = true)
        val isTimeout = msg.contains("timeout", ignoreCase = true) || msg.contains("timed out", ignoreCase = true)
        val isRefused = msg.contains("refused", ignoreCase = true) || msg.contains("ConnectException", ignoreCase = true)
        val isDnsFailed = msg.contains("Unable to resolve host", ignoreCase = true) || msg.contains("UnknownHost", ignoreCase = true)

        val friendlyReason = when {
            isRefused -> "代理端口连接被拒绝 (Connection Refused)。请确认 Clash Meta 代理应用已启动且本地监听端口设置正确。"
            isDnsFailed -> "网络域名解析失败，请检查手机网络连接状态。"
            isConnectionClosed -> "网络连接被对端中断或重置 (Connection closed)。若正在访问海外站点，通常由于国内直连受限或代理节点不稳定，请检查 Clash Meta 节点连通性。"
            isTimeout -> "网络请求超时，目标服务器响应过慢，请检查网络延迟。"
            else -> "连接异常: $msg"
        }
        return IllegalStateException(friendlyReason, e)
    }
}
