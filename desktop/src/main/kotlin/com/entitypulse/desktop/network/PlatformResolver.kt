package com.entitypulse.desktop.network

import com.entitypulse.desktop.data.model.PlatformType
import java.net.URI

data class ResolvedPlatform(
    val platformType: PlatformType,
    val identifier: String,
    val suggestedTitle: String,
    val feedPathOrUrl: String,
    val isDirectRss: Boolean
)

object PlatformResolver {

    private val TWITTER_RESERVED = setOf(
        "home", "explore", "notifications", "messages", "search", "settings", "i",
        "login", "signup", "intent", "share", "about", "tos", "privacy", "help", "jobs", "hashtag"
    )

    private val YOUTUBE_RESERVED = setOf(
        "watch", "embed", "results", "feed", "gaming", "live", "premium", "shorts", "playlist"
    )

    private val SEARCH_ENGINE_DOMAINS = setOf(
        "bing.com", "google.com", "baidu.com", "duckduckgo.com", "sogou.com", "yahoo.com"
    )

    fun isLockableProfile(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isBlank() || !trimmed.startsWith("http", ignoreCase = true)) return false
        val host = runCatching { URI(trimmed).host?.lowercase() }.getOrNull() ?: return false

        if (SEARCH_ENGINE_DOMAINS.any { host.contains(it) }) return false

        val res = resolve(trimmed)
        return when (res.platformType) {
            PlatformType.TWITTER -> res.identifier.startsWith("@") && res.identifier.length > 1
            PlatformType.YOUTUBE -> res.identifier.startsWith("@") || res.identifier.startsWith("UC") || res.identifier.isNotBlank()
            PlatformType.BILIBILI -> res.identifier.startsWith("UID:")
            PlatformType.STEAM -> res.identifier.startsWith("App:")
            PlatformType.REDDIT -> res.identifier.startsWith("r/")
            PlatformType.WEIBO -> res.identifier.startsWith("UID:")
            PlatformType.BLUESKY -> res.identifier.isNotBlank()
            PlatformType.GITHUB -> res.identifier.isNotBlank()
            PlatformType.RSS_CUSTOM -> !host.contains("wikipedia.org") && !host.contains("moegirl.org")
        }
    }

    fun resolve(input: String): ResolvedPlatform {
        val trimmed = input.trim()

        // 1. Twitter / X
        val twitterRegex = """(?:https?://)?(?:(?:www|mobile|m)\.)?(?:twitter\.com|x\.com)/([a-zA-Z0-9_]{1,50})(?:[/?#].*)?""".toRegex(RegexOption.IGNORE_CASE)
        val twitterMatch = twitterRegex.matchEntire(trimmed)
        if (twitterMatch != null) {
            val username = twitterMatch.groupValues[1]
            if (!TWITTER_RESERVED.contains(username.lowercase())) {
                return ResolvedPlatform(
                    platformType = PlatformType.TWITTER,
                    identifier = "@$username",
                    suggestedTitle = "X / Twitter (@$username)",
                    feedPathOrUrl = "/twitter/user/$username",
                    isDirectRss = false
                )
            }
        }
        if (trimmed.startsWith("@") && !trimmed.contains(" ")) {
            val username = trimmed.removePrefix("@")
            return ResolvedPlatform(
                platformType = PlatformType.TWITTER,
                identifier = "@$username",
                suggestedTitle = "X / Twitter (@$username)",
                feedPathOrUrl = "/twitter/user/$username",
                isDirectRss = false
            )
        }

        // 2. Bilibili
        val biliRegex = """(?:https?://)?(?:space\.bilibili\.com|(?:m|www)\.bilibili\.com/space)/(\d+)(?:[/?#].*)?""".toRegex(RegexOption.IGNORE_CASE)
        val biliMatch = biliRegex.matchEntire(trimmed)
        if (biliMatch != null) {
            val uid = biliMatch.groupValues[1]
            return ResolvedPlatform(
                platformType = PlatformType.BILIBILI,
                identifier = "UID: $uid",
                suggestedTitle = "Bilibili (UID: $uid)",
                feedPathOrUrl = "/bilibili/user/video/$uid",
                isDirectRss = false
            )
        }

        // 3. YouTube
        val ytCustomRegex = """(?:https?://)?(?:(?:www|m)\.)?youtube\.com/(@[a-zA-Z0-9_.\-]+)(?:[/?#].*)?""".toRegex(RegexOption.IGNORE_CASE)
        val ytCustomMatch = ytCustomRegex.matchEntire(trimmed)
        if (ytCustomMatch != null) {
            val handle = ytCustomMatch.groupValues[1]
            return ResolvedPlatform(
                platformType = PlatformType.YOUTUBE,
                identifier = handle,
                suggestedTitle = "YouTube ($handle)",
                feedPathOrUrl = "/youtube/user/$handle",
                isDirectRss = false
            )
        }

        val ytChannelRegex = """(?:https?://)?(?:(?:www|m)\.)?youtube\.com/channel/(UC[a-zA-Z0-9_\-]+)(?:[/?#].*)?""".toRegex(RegexOption.IGNORE_CASE)
        val ytChannelMatch = ytChannelRegex.matchEntire(trimmed)
        if (ytChannelMatch != null) {
            val channelId = ytChannelMatch.groupValues[1]
            return ResolvedPlatform(
                platformType = PlatformType.YOUTUBE,
                identifier = channelId,
                suggestedTitle = "YouTube 频道",
                feedPathOrUrl = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId",
                isDirectRss = true
            )
        }

        val ytAltRegex = """(?:https?://)?(?:(?:www|m)\.)?youtube\.com/(?:c|user)/([a-zA-Z0-9_.\-]+)(?:[/?#].*)?""".toRegex(RegexOption.IGNORE_CASE)
        val ytAltMatch = ytAltRegex.matchEntire(trimmed)
        if (ytAltMatch != null) {
            val name = ytAltMatch.groupValues[1]
            if (!YOUTUBE_RESERVED.contains(name.lowercase())) {
                return ResolvedPlatform(
                    platformType = PlatformType.YOUTUBE,
                    identifier = "@$name",
                    suggestedTitle = "YouTube (@$name)",
                    feedPathOrUrl = "/youtube/user/@$name",
                    isDirectRss = false
                )
            }
        }

        // 4. Steam
        val steamRegex = """(?:https?://)?(?:store\.steampowered\.com|steamcommunity\.com)/(?:app|games)/(\d+)(?:[/?#].*)?""".toRegex(RegexOption.IGNORE_CASE)
        val steamMatch = steamRegex.matchEntire(trimmed)
        if (steamMatch != null) {
            val appId = steamMatch.groupValues[1]
            return ResolvedPlatform(
                platformType = PlatformType.STEAM,
                identifier = "App: $appId",
                suggestedTitle = "Steam 公告 (App: $appId)",
                feedPathOrUrl = "https://steamcommunity.com/games/$appId/rss/",
                isDirectRss = true
            )
        }

        // 5. Reddit
        val redditRegex = """(?:https?://)?(?:(?:www|m)\.)?reddit\.com/r/([a-zA-Z0-9_]+)(?:[/?#].*)?""".toRegex(RegexOption.IGNORE_CASE)
        val redditMatch = redditRegex.matchEntire(trimmed)
        if (redditMatch != null) {
            val sub = redditMatch.groupValues[1]
            return ResolvedPlatform(
                platformType = PlatformType.REDDIT,
                identifier = "r/$sub",
                suggestedTitle = "Reddit (r/$sub)",
                feedPathOrUrl = "https://www.reddit.com/r/$sub/.rss",
                isDirectRss = true
            )
        }
        if (trimmed.startsWith("r/") && !trimmed.contains(" ")) {
            val sub = trimmed.removePrefix("r/")
            return ResolvedPlatform(
                platformType = PlatformType.REDDIT,
                identifier = "r/$sub",
                suggestedTitle = "Reddit (r/$sub)",
                feedPathOrUrl = "https://www.reddit.com/r/$sub/.rss",
                isDirectRss = true
            )
        }

        // 6. Weibo
        val weiboRegex = """(?:https?://)?(?:(?:www|m)\.)?weibo\.(?:com|cn)/u/(\d+)(?:[/?#].*)?""".toRegex(RegexOption.IGNORE_CASE)
        val weiboMatch = weiboRegex.matchEntire(trimmed)
        if (weiboMatch != null) {
            val uid = weiboMatch.groupValues[1]
            return ResolvedPlatform(
                platformType = PlatformType.WEIBO,
                identifier = "UID: $uid",
                suggestedTitle = "微博动态 ($uid)",
                feedPathOrUrl = "/weibo/user/$uid",
                isDirectRss = false
            )
        }

        // 7. Bluesky
        val bskyRegex = """(?:https?://)?(?:(?:www|m)\.)?bsky\.app/profile/([a-zA-Z0-9_.\-]+)(?:[/?#].*)?""".toRegex(RegexOption.IGNORE_CASE)
        val bskyMatch = bskyRegex.matchEntire(trimmed)
        if (bskyMatch != null) {
            val handle = bskyMatch.groupValues[1]
            return ResolvedPlatform(
                platformType = PlatformType.BLUESKY,
                identifier = handle,
                suggestedTitle = "Bluesky ($handle)",
                feedPathOrUrl = "/bluesky/profile/$handle",
                isDirectRss = false
            )
        }

        // 8. GitHub
        val ghRegex = """(?:https?://)?(?:(?:www)\.)?github\.com/([a-zA-Z0-9_\-]+)(?:[/?#].*)?""".toRegex(RegexOption.IGNORE_CASE)
        val ghMatch = ghRegex.matchEntire(trimmed)
        if (ghMatch != null) {
            val user = ghMatch.groupValues[1]
            return ResolvedPlatform(
                platformType = PlatformType.GITHUB,
                identifier = user,
                suggestedTitle = "GitHub ($user)",
                feedPathOrUrl = "https://github.com/$user.atom",
                isDirectRss = true
            )
        }

        // 9. 默认通用官网 / RSS 博客
        val host = runCatching { URI(trimmed).host }.getOrNull() ?: "官方网站"
        return ResolvedPlatform(
            platformType = PlatformType.RSS_CUSTOM,
            identifier = host,
            suggestedTitle = "$host 官网/博客",
            feedPathOrUrl = trimmed,
            isDirectRss = false
        )
    }

    fun buildFinalUrl(feedPathOrUrl: String, baseRssHubUrl: String, token: String? = null): String {
        if (feedPathOrUrl.startsWith("http://", ignoreCase = true) ||
            feedPathOrUrl.startsWith("https://", ignoreCase = true)
        ) {
            return feedPathOrUrl
        }
        val cleanBase = baseRssHubUrl.trimEnd('/')
        val cleanPath = if (feedPathOrUrl.startsWith("/")) feedPathOrUrl else "/$feedPathOrUrl"
        val full = "$cleanBase$cleanPath"
        return if (!token.isNullOrBlank()) {
            val separator = if (full.contains("?")) "&" else "?"
            "$full${separator}code=$token"
        } else {
            full
        }
    }
}
