package com.entitypulse.app.data.dao

import androidx.room.*
import com.entitypulse.app.data.model.EntityRecord
import com.entitypulse.app.data.model.EntityWithChannels
import kotlinx.coroutines.flow.Flow

@Dao
interface EntityDao {

    @Query("SELECT * FROM entities ORDER BY isVip DESC, updatedAt DESC")
    fun observeAll(): Flow<List<EntityRecord>>

    @Query("SELECT * FROM entities WHERE id = :id")
    suspend fun getById(id: Long): EntityRecord?

    @Query("SELECT * FROM entities WHERE id = :id")
    fun observeById(id: Long): Flow<EntityRecord?>

    @Transaction
    @Query("SELECT * FROM entities WHERE id = :id")
    fun observeEntityWithChannels(id: Long): Flow<EntityWithChannels?>

    @Transaction
    @Query("SELECT * FROM entities WHERE id = :id")
    suspend fun getEntityWithChannels(id: Long): EntityWithChannels?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: EntityRecord): Long

    @Update
    suspend fun update(entity: EntityRecord)

    @Delete
    suspend fun delete(entity: EntityRecord)

    @Query("UPDATE entities SET isVip = :isVip, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateVipStatus(id: Long, isVip: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE entities SET isNotifyEnabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateNotifyStatus(id: Long, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE entities SET avatarUrl = :avatarUrl, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateAvatar(id: Long, avatarUrl: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM entities")
    suspend fun getAll(): List<EntityRecord>

    @Query("""
        SELECT e.*, 
            COALESCE(SUM(CASE WHEN f.isRead = 0 THEN 1 ELSE 0 END), 0) AS unreadCount,
            (SELECT f2.title FROM feed_items f2 WHERE f2.entityId = e.id ORDER BY f2.publishedAt DESC LIMIT 1) AS latestFeedTitle,
            (SELECT f3.publishedAt FROM feed_items f3 WHERE f3.entityId = e.id ORDER BY f3.publishedAt DESC LIMIT 1) AS latestFeedPublishedAt,
            '' AS channelTypes
        FROM entities e
        LEFT JOIN feed_items f ON e.id = f.entityId
        GROUP BY e.id
        ORDER BY e.isVip DESC, e.updatedAt DESC
    """)
    fun observeEntitiesWithStats(): Flow<List<com.entitypulse.app.data.model.EntityWithUnreadCount>>
}
