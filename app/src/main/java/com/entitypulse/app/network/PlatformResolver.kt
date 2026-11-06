package com.entitypulse.app.network

import com.entitypulse.app.data.model.PlatformType
import java.net.URI

data class ResolvedPlatform(
    val platformType: PlatformType,
    val identifier: String,
    val suggestedTitle: String,
    val feedPathOrUrl: String,
    val isDirectRss: Boolean
)

object PlatformResolver {

    /**
     * 根据用户输入的网址或 ID，自动推断平台类型并生成规范的订阅路径或直链 RSS。
     */
    fun resolve(input: String): ResolvedPlatform {
        val trimmed = input.trim()

        // 1. Twitter / X
        val twitterRegex = """(?:https?://)?(?:www\.)?(?:twitter\.com|x\.com)/([a-zA-Z0-9_]{1,50})(?:[/?#].*)?""".toRegex(RegexOption.IGNORE_CASE)
        val twitterMatch = twitterRegex.matchEntire(trimmed)
        if (twitterMatch != null) {
            val username = twitterMatch.groupValues[1]
            return ResolvedPlatform(
                platformType = PlatformType.TWITTER,
                identifier = "@$username",
                suggestedTitle = "X / Twitter (@$username)",
                feedPathOrUrl = "/twitter/user/$username",
                isDirectRss = false
            )
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
        val biliRegex = """(?:https?://)?space\.bilibili\.com/(\d+)(?:[/?#].*)?""".toRegex(RegexOption.IGNORE_CASE)
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
        val ytCustomRegex = """(?:https?://)?(?:www\.)?youtube\.com/(@[a-zA-Z0-9_.\-]+)(?:[/?#].*)?""".toRegex(RegexOption.IGNORE_CASE)
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
        val ytChannelRegex = """(?:https?://)?(?:www\.)?youtube\.com/channel/(UC[a-zA-Z0-9_\-]+)(?:[/?#].*)?""".toRegex(RegexOption.IGNORE_CASE)
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

        // 4. Steam 社区公告
        val steamRegex = """(?:https?://)?store\.steampowered\.com/app/(\d+)(?:[/?#].*)?""".toRegex(RegexOption.IGNORE_CASE)
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
        val redditRegex = """(?:https?://)?(?:www\.)?reddit\.com/r/([a-zA-Z0-9_]+)(?:[/?#].*)?""".toRegex(RegexOption.IGNORE_CASE)
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
        val weiboRegex = """(?:https?://)?(?:www\.)?weibo\.com/u/(\d+)(?:[/?#].*)?""".toRegex(RegexOption.IGNORE_CASE)
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
        val bskyRegex = """(?:https?://)?bsky\.app/profile/([a-zA-Z0-9_.\-]+)(?:[/?#].*)?""".toRegex(RegexOption.IGNORE_CASE)
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
        val ghRegex = """(?:https?://)?github\.com/([a-zA-Z0-9_\-]+)(?:[/?#].*)?""".toRegex(RegexOption.IGNORE_CASE)
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

        // 9. 默认通用 RSS / Atom
        val host = runCatching { URI(trimmed).host }.getOrNull() ?: "自定义源"
        return ResolvedPlatform(
            platformType = PlatformType.RSS_CUSTOM,
            identifier = trimmed,
            suggestedTitle = "RSS ($host)",
            feedPathOrUrl = trimmed,
            isDirectRss = true
        )
    }

    /**
     * 将解析出的路径拼接为可请求的完整绝对 URL。
     * 若为相对路径，则使用当前活跃的 RSSHub 节点基地址拼接；若带有 access token 则自动附带。
     */
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
