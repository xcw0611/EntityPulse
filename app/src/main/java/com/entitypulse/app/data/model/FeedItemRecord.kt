package com.entitypulse.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "feed_items",
    foreignKeys = [
        ForeignKey(
            entity = EntityRecord::class,
            parentColumns = ["id"],
            childColumns = ["entityId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SourceChannelRecord::class,
            parentColumns = ["id"],
            childColumns = ["channelId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["guid"], unique = true),
        Index("entityId"),
        Index("channelId"),
        Index("publishedAt")
    ]
)
data class FeedItemRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entityId: Long,
    val channelId: Long,
    val guid: String,                    // 唯一哈希或 GUID 用于去重
    val title: String,
    val contentPlainText: String,
    val contentHtml: String? = null,
    val author: String? = null,
    val publishedAt: Long,
    val originalUrl: String,
    val thumbnailUrl: String? = null,
    val mediaUrlsJson: String? = null,    // 附加多图/媒体 JSON
    val isRead: Boolean = false,
    val isBookmarked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
