package com.entitypulse.desktop.data.model

import androidx.compose.ui.graphics.Color

enum class PlatformType(
    val displayName: String,
    val iconName: String,
    val badgeColor: Long,
    val defaultBaseRoute: String = ""
) {
    TWITTER(
        displayName = "X (Twitter)",
        iconName = "twitter",
        badgeColor = 0xFF1D9BF0L,
        defaultBaseRoute = "/twitter/user/"
    ),
    BILIBILI(
        displayName = "Bilibili",
        iconName = "bilibili",
        badgeColor = 0xFFFB7299L,
        defaultBaseRoute = "/bilibili/user/video/"
    ),
    YOUTUBE(
        displayName = "YouTube",
        iconName = "youtube",
        badgeColor = 0xFFFF0000L,
        defaultBaseRoute = "/youtube/user/"
    ),
    STEAM(
        displayName = "Steam",
        iconName = "steam",
        badgeColor = 0xFF66C0F4L,
        defaultBaseRoute = "https://steamcommunity.com/games/"
    ),
    REDDIT(
        displayName = "Reddit",
        iconName = "reddit",
        badgeColor = 0xFFFF4500L,
        defaultBaseRoute = "https://reddit.com/r/"
    ),
    WEIBO(
        displayName = "微博",
        iconName = "weibo",
        badgeColor = 0xFFE6162DL,
        defaultBaseRoute = "/weibo/user/"
    ),
    BLUESKY(
        displayName = "Bluesky",
        iconName = "bluesky",
        badgeColor = 0xFF0085FFL,
        defaultBaseRoute = "/bluesky/profile/"
    ),
    GITHUB(
        displayName = "GitHub",
        iconName = "github",
        badgeColor = 0xFF8B949EL,
        defaultBaseRoute = "https://github.com/"
    ),
    RSS_CUSTOM(
        displayName = "官网 / RSS",
        iconName = "rss",
        badgeColor = 0xFFFFA500L,
        defaultBaseRoute = ""
    );

    val primaryColor: Color get() = Color(badgeColor)

    companion object {
        fun fromString(value: String): PlatformType {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: RSS_CUSTOM
        }
    }
}
