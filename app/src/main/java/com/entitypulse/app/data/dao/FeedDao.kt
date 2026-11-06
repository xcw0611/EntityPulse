package com.entitypulse.app.data.dao

import androidx.room.*
import com.entitypulse.app.data.model.FeedItemRecord
import com.entitypulse.app.data.model.FeedItemWithMeta
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {

    @Query("""
        SELECT f.*, e.name AS entityName, e.avatarUrl AS entityAvatarUrl, e.isVip AS isEntityVip,
               sc.platformType AS platformType, sc.title AS channelTitle
        FROM feed_items f
        INNER JOIN entities e ON f.entityId = e.id
        INNER JOIN source_channels sc ON f.channelId = sc.id
        ORDER BY f.publishedAt DESC
        LIMIT :limit OFFSET :offset
    """)
    fun observeAllWithMeta(limit: Int = 100, offset: Int = 0): Flow<List<FeedItemWithMeta>>

    @Query("""
        SELECT f.*, e.name AS entityName, e.avatarUrl AS entityAvatarUrl, e.isVip AS isEntityVip,
               sc.platformType AS platformType, sc.title AS channelTitle
        FROM feed_items f
        INNER JOIN entities e ON f.entityId = e.id
        INNER JOIN source_channels sc ON f.channelId = sc.id
        WHERE f.entityId = :entityId
        ORDER BY f.publishedAt DESC
        LIMIT :limit OFFSET :offset
    """)
    fun observeByEntityWithMeta(entityId: Long, limit: Int = 100, offset: Int = 0): Flow<List<FeedItemWithMeta>>

    @Query("""
        SELECT f.*, e.name AS entityName, e.avatarUrl AS entityAvatarUrl, e.isVip AS isEntityVip,
               sc.platformType AS platformType, sc.title AS channelTitle
        FROM feed_items f
        INNER JOIN entities e ON f.entityId = e.id
        INNER JOIN source_channels sc ON f.channelId = sc.id
        WHERE f.channelId = :channelId
        ORDER BY f.publishedAt DESC
    """)
    fun observeByChannelWithMeta(channelId: Long): Flow<List<FeedItemWithMeta>>

    @Query("""
        SELECT f.*, e.name AS entityName, e.avatarUrl AS entityAvatarUrl, e.isVip AS isEntityVip,
               sc.platformType AS platformType, sc.title AS channelTitle
        FROM feed_items f
        INNER JOIN entities e ON f.entityId = e.id
        INNER JOIN source_channels sc ON f.channelId = sc.id
        WHERE f.id = :id
    """)
    suspend fun getByIdWithMeta(id: Long): FeedItemWithMeta?

    @Query("SELECT * FROM feed_items WHERE guid = :guid LIMIT 1")
    suspend fun getByGuid(guid: String): FeedItemRecord?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(item: FeedItemRecord): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(items: List<FeedItemRecord>): List<Long>

    @Query("UPDATE feed_items SET isRead = :isRead WHERE id = :id")
    suspend fun updateReadStatus(id: Long, isRead: Boolean)

    @Query("UPDATE feed_items SET isRead = 1 WHERE entityId = :entityId")
    suspend fun markAllAsReadForEntity(entityId: Long)

    @Query("UPDATE feed_items SET isRead = 1")
    suspend fun markAllAsRead()

    @Query("UPDATE feed_items SET isBookmarked = :isBookmarked WHERE id = :id")
    suspend fun updateBookmarkStatus(id: Long, isBookmarked: Boolean)

    @Query("DELETE FROM feed_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM feed_items WHERE isRead = 0")
    fun observeTotalUnreadCount(): Flow<Int>
}
