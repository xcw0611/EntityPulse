package com.entitypulse.app.repository

import com.entitypulse.app.data.AppDatabase
import com.entitypulse.app.data.model.*
import com.entitypulse.app.network.FeedParser
import com.entitypulse.app.network.NodeManager
import com.entitypulse.app.network.ParsedFeedItem
import com.entitypulse.app.network.PlatformResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.IOException
import java.util.concurrent.TimeUnit

class FeedRepository(
    private val database: AppDatabase,
    private val nodeManager: NodeManager
) {
    private val entityDao = database.entityDao()
    private val channelDao = database.channelDao()
    private val feedDao = database.feedDao()

    private fun getHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)

        val proxy = nodeManager.getProxy()
        if (proxy != null) {
            builder.proxy(proxy)
        }
        return builder.build()
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
        val xml = fetchFeedXml(feedPathOrUrl)
        val parsed = FeedParser.parse(xml)
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
        try {
            val xml = fetchFeedXml(channel.resolvedFeedUrl)
            val parsedFeed = FeedParser.parse(xml)

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
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: "同步失败"
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

    private suspend fun fetchFeedXml(feedPathOrUrl: String): String = withContext(Dispatchers.IO) {
        val isDirectUrl = feedPathOrUrl.startsWith("http://", ignoreCase = true) ||
                feedPathOrUrl.startsWith("https://", ignoreCase = true)

        if (isDirectUrl) {
            try {
                return@withContext executeRequest(feedPathOrUrl)
            } catch (e: Exception) {
                throw diagnoseNetworkError(e, feedPathOrUrl)
            }
        }

        // 路径为社媒路由（如 /twitter/user/...）
        val candidateNodes = nodeManager.getAllCandidateNodes()
        var lastError: Exception? = null

        // 1. 尝试配置的所有 RSSHub 节点池
        for (node in candidateNodes) {
            val fullUrl = PlatformResolver.buildFinalUrl(
                feedPathOrUrl = feedPathOrUrl,
                baseRssHubUrl = node,
                token = nodeManager.accessToken.ifBlank { null }
            )
            try {
                return@withContext executeRequest(fullUrl)
            } catch (e: Exception) {
                lastError = e
            }
        }

        // 2. 若为 X / Twitter 且 RSSHub 均无法连接，尝试备用 Nitter 镜像池
        if (feedPathOrUrl.startsWith("/twitter/user/", ignoreCase = true)) {
            val username = feedPathOrUrl.removePrefix("/twitter/user/").trim()
            val nitterMirrors = listOf(
                "https://nitter.privacydev.net/$username/rss",
                "https://xcancel.com/$username/rss",
                "https://nitter.poast.org/$username/rss"
            )
            for (mirror in nitterMirrors) {
                try {
                    return@withContext executeRequest(mirror)
                } catch (e: Exception) {
                    lastError = e
                }
            }
        }

        throw diagnoseNetworkError(lastError ?: IllegalStateException("无法连通节点"), feedPathOrUrl)
    }

    private fun executeRequest(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.8")
            .build()

        getHttpClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code} ${response.message}")
            }
            val body = response.body?.string()
            if (body.isNullOrBlank()) {
                throw IllegalStateException("响应内容为空")
            }
            return body
        }
    }

    private fun diagnoseNetworkError(e: Throwable, target: String): Exception {
        val msg = e.localizedMessage ?: ""
        val isConnectionClosed = msg.contains("closed", ignoreCase = true) ||
                msg.contains("reset", ignoreCase = true) ||
                msg.contains("EOF", ignoreCase = true)
        val isTimeout = msg.contains("timeout", ignoreCase = true) || msg.contains("timed out", ignoreCase = true)

        val friendlyReason = when {
            isConnectionClosed -> "连接被对端中断或重置 (Connection closed)。若正在访问 X/Twitter、YouTube 等海外站点，通常由于国内直连受限，请在【设置】中配置本地代理端口 (如 127.0.0.1:7890) 或开启网络加速后重试。"
            isTimeout -> "请求超时。目标服务器响应过慢，请检查网络环境或在【设置】中切换自建 RSSHub 节点。"
            msg.contains("403") || msg.contains("404") -> "目标源受限或不存在 ($msg)。建议在【设置】中配置具备鉴权 Token 的自建 RSSHub 实例。"
            else -> "网络连接失败: $msg"
        }
        return IllegalStateException(friendlyReason, e)
    }
}
