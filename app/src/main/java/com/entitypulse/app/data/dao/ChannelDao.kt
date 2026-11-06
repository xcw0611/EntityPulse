package com.entitypulse.app.data.dao

import androidx.room.*
import com.entitypulse.app.data.model.SourceChannelRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {

    @Query("SELECT * FROM source_channels WHERE entityId = :entityId")
    fun observeByEntityId(entityId: Long): Flow<List<SourceChannelRecord>>

    @Query("SELECT * FROM source_channels WHERE entityId = :entityId")
    suspend fun getByEntityId(entityId: Long): List<SourceChannelRecord>

    @Query("SELECT * FROM source_channels WHERE isEnabled = 1")
    suspend fun getAllEnabledChannels(): List<SourceChannelRecord>

    @Query("SELECT * FROM source_channels WHERE id = :id")
    suspend fun getById(id: Long): SourceChannelRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(channel: SourceChannelRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(channels: List<SourceChannelRecord>): List<Long>

    @Update
    suspend fun update(channel: SourceChannelRecord)

    @Delete
    suspend fun delete(channel: SourceChannelRecord)

    @Query("UPDATE source_channels SET lastSyncTime = :timestamp, lastError = :error WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, timestamp: Long, error: String? = null)

    @Query("UPDATE source_channels SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun setEnabled(id: Long, isEnabled: Boolean)
}
