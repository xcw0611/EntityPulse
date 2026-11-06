package com.entitypulse.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "source_channels",
    foreignKeys = [
        ForeignKey(
            entity = EntityRecord::class,
            parentColumns = ["id"],
            childColumns = ["entityId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("entityId")]
)
data class SourceChannelRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entityId: Long,
    val platformType: PlatformType,
    val identifier: String,              // 例如 @TeamCherryGames, 282994, r/HollowKnight
    val resolvedFeedUrl: String,         // 最终 RSS 或 RSSHub 路径
    val title: String,                   // 展示标题，如 "Official Twitter"
    val lastSyncTime: Long? = null,
    val lastError: String? = null,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
