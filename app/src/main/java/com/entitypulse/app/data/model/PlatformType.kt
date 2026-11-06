package com.entitypulse.app.data.model

enum class PlatformType(
    val displayName: String,
    val iconName: String,
    val defaultBaseHost: String,
    val isRssNative: Boolean
) {
    TWITTER("X / Twitter", "twitter", "x.com", false),
    BILIBILI("Bilibili", "bilibili", "bilibili.com", false),
    YOUTUBE("YouTube", "youtube", "youtube.com", true),
    STEAM("Steam 公告", "steam", "store.steampowered.com", true),
    REDDIT("Reddit", "reddit", "reddit.com", true),
    WEIBO("微博", "weibo", "weibo.com", false),
    BLUESKY("Bluesky", "bluesky", "bsky.app", false),
    GITHUB("GitHub", "github", "github.com", true),
    RSS_CUSTOM("通用 RSS / Atom", "rss", "", true);

    companion object {
        fun fromString(value: String): PlatformType {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: RSS_CUSTOM
        }
    }
}
