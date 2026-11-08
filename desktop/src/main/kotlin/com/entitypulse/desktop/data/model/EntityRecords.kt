package com.entitypulse.desktop.data.model

data class EntityRecord(
    val id: Long = 0,
    val name: String,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val category: String = "默认",
    val isVip: Boolean = false,
    val isNotifyEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class SourceChannelRecord(
    val id: Long = 0,
    val entityId: Long,
    val platformType: PlatformType,
    val identifier: String,
    val resolvedFeedUrl: String,
    val title: String,
    val isEnabled: Boolean = true,
    val lastSyncTime: Long = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class FeedItemRecord(
    val id: Long = 0,
    val entityId: Long,
    val channelId: Long,
    val guid: String,
    val title: String,
    val contentPlainText: String,
    val contentHtml: String? = null,
    val author: String? = null,
    val publishedAt: Long,
    val originalUrl: String? = null,
    val thumbnailUrl: String? = null,
    val mediaUrlsJson: String? = null,
    val isRead: Boolean = false,
    val isBookmarked: Boolean = false,
    val fetchedAt: Long = System.currentTimeMillis()
)

data class FeedItemWithMeta(
    val item: FeedItemRecord,
    val entityName: String,
    val entityAvatar: String?,
    val platformType: PlatformType,
    val channelTitle: String
)

data class EntityWithStats(
    val record: EntityRecord,
    val unreadCount: Int = 0,
    val latestFeedTitle: String? = null,
    val latestFeedPublishedAt: Long? = null
) {
    val entity: EntityRecord get() = record
}

enum class TimeRangeFilter(val displayName: String, val durationMillis: Long) {
    ONE_YEAR("近1年", 365L * 24 * 3600 * 1000L),
    SIX_MONTHS("近半年", 180L * 24 * 3600 * 1000L),
    ONE_MONTH("近1个月", 30L * 24 * 3600 * 1000L),
    ALL("全部", Long.MAX_VALUE);

    val label: String get() = displayName

    companion object {
        val DEFAULT = ONE_YEAR
    }
}
