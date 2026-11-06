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
import java.util.concurrent.TimeUnit

class FeedRepository(
    private val database: AppDatabase,
    private val nodeManager: NodeManager
) {
    private val entityDao = database.entityDao()
    private val channelDao = database.channelDao()
    private val feedDao = database.feedDao()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

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
     * 测试指定的订阅源，返回抓取到的最新一条动态预览，若失败则抛出异常。
     */
    suspend fun testChannel(feedPathOrUrl: String): ParsedFeedItem = withContext(Dispatchers.IO) {
        val xml = fetchFeedXml(feedPathOrUrl)
        val parsed = FeedParser.parse(xml)
        if (parsed.items.isEmpty()) {
            throw IllegalStateException("解析成功，但该源暂无任何动态内容")
        }
        parsed.items.first()
    }

    /**
     * 同步单个频道，返回本次抓取到的“全新动态项”列表（未曾入库且晚于上次同步时间）
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
                    // 成功插入新记录
                    newSavedItems.add(record.copy(id = rowId))
                }
            }

            channelDao.updateSyncStatus(channel.id, System.currentTimeMillis(), null)
        } catch (e: Exception) {
            channelDao.updateSyncStatus(channel.id, System.currentTimeMillis(), e.localizedMessage ?: "同步失败")
        }
        newSavedItems
    }

    /**
     * 同步所有已启用的频道，返回所有新入库的动态项（用于通知系统触发提醒）
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
            return@withContext executeRequest(feedPathOrUrl)
        }

        // 社媒类走 RSSHub 节点池
        val candidateNodes = nodeManager.getAllCandidateNodes()
        var lastError: Exception? = null

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

        throw lastError ?: IllegalStateException("无法连接到任何 RSSHub 节点，请在设置中配置有效节点或检查网络")
    }

    private fun executeRequest(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; EntityPulse) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
            .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.8")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}: ${response.message}")
            }
            val body = response.body?.string()
            if (body.isNullOrBlank()) {
                throw IllegalStateException("响应内容为空")
            }
            return body
        }
    }
}
