package com.entitypulse.app.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class EntityWithChannels(
    @Embedded val entity: EntityRecord,
    @Relation(
        parentColumn = "id",
        entityColumn = "entityId"
    )
    val channels: List<SourceChannelRecord>
)

data class EntityWithUnreadCount(
    @Embedded val entity: EntityRecord,
    val unreadCount: Int = 0,
    val latestFeedTitle: String? = null,
    val latestFeedPublishedAt: Long? = null,
    val channelTypes: String = "" // 逗号分隔的平台列表
)

data class FeedItemWithMeta(
    @Embedded val item: FeedItemRecord,
    val entityName: String,
    val entityAvatarUrl: String?,
    val isEntityVip: Boolean,
    val platformType: PlatformType,
    val channelTitle: String
)
