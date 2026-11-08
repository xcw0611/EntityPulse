package com.entitypulse.desktop.network

import com.entitypulse.desktop.util.AppLogger
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern

object YouTubeResolver {

    private val channelIdPattern = Pattern.compile("https?://(?:www\\.)?youtube\\.com/channel/(UC[a-zA-Z0-9_\\-]+)", Pattern.CASE_INSENSITIVE)
    private val customUserPattern = Pattern.compile("https?://(?:www\\.)?youtube\\.com/(?:@|user/|c/)?([a-zA-Z0-9_.-]+)", Pattern.CASE_INSENSITIVE)
    private val rssLinkPattern = Pattern.compile("href=[\"'](https?://(?:www\\.)?youtube\\.com/feeds/videos\\.xml\\?channel_id=UC[a-zA-Z0-9_\\-]+)[\"']", Pattern.CASE_INSENSITIVE)
    private val browseIdPattern = Pattern.compile("[\"']browseId[\"']\\s*:\\s*[\"'](UC[a-zA-Z0-9_\\-]+)[\"']")
    private val externalIdPattern = Pattern.compile("[\"']externalId[\"']\\s*:\\s*[\"'](UC[a-zA-Z0-9_\\-]+)[\"']")

    fun isYouTubeTarget(feedPathOrUrl: String): Boolean {
        val low = feedPathOrUrl.lowercase()
        return low.contains("youtube.com") || low.contains("youtu.be") || low.startsWith("/youtube/")
    }

    fun checkHandleExists(handle: String, client: OkHttpClient): Boolean {
        val clean = handle.trim().removePrefix("@")
        if (clean.isBlank()) return false
        val url = if (clean.startsWith("UC") && clean.length >= 22) {
            "https://www.youtube.com/channel/$clean"
        } else {
            "https://www.youtube.com/@$clean"
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.code == 404) return false
                val body = response.body?.string() ?: return false
                if (body.contains("<title>404 Not Found</title>") ||
                    body.contains("This page isn't available") ||
                    body.contains("此页面不可用")
                ) {
                    return false
                }
                browseIdPattern.matcher(body).find() ||
                        externalIdPattern.matcher(body).find() ||
                        channelIdPattern.matcher(body).find()
            }
        } catch (_: Exception) {
            false
        }
    }

    fun resolveToNativeRssUrl(feedPathOrUrl: String, client: OkHttpClient): String {
        val trimmed = feedPathOrUrl.trim()
        val cidMatch = channelIdPattern.matcher(trimmed)
        if (cidMatch.find()) {
            val cid = cidMatch.group(1)
            return "https://www.youtube.com/feeds/videos.xml?channel_id=$cid"
        }

        if (trimmed.contains("feeds/videos.xml?channel_id=")) {
            return trimmed
        }

        val handle = when {
            trimmed.startsWith("/youtube/user/", ignoreCase = true) ->
                trimmed.removePrefix("/youtube/user/").substringBefore('/').substringBefore('?').removePrefix("@")
            trimmed.contains("youtube.com/") -> {
                val after = trimmed.substringAfter("youtube.com/")
                after.substringBefore('/').substringBefore('?').removePrefix("@")
            }
            trimmed.startsWith("@") -> trimmed.removePrefix("@")
            else -> trimmed
        }

        val targetUrl = if (handle.startsWith("UC") && handle.length >= 22) {
            "https://www.youtube.com/channel/$handle"
        } else {
            "https://www.youtube.com/@$handle"
        }

        AppLogger.d("YouTubeResolver", "解析 YouTube 目标原生 Atom 源: $targetUrl")
        val request = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 404) {
                throw IllegalStateException("YouTube 上不存在该频道 (@$handle) [HTTP 404 Not Found]。该主体可能未在 YouTube 开设独立频道，相关视频通常在所属制作方或主创频道中发布。建议核对修改或删除此渠道（非网络故障）。")
            }
            if (!response.isSuccessful) {
                throw IllegalStateException("YouTube 频道页面请求失败: HTTP ${response.code} ${response.message}")
            }
            val html = response.body?.string() ?: throw IllegalStateException("YouTube 页面响应为空")

            if (html.contains("<title>404 Not Found</title>") || html.contains("This page isn't available")) {
                throw IllegalStateException("YouTube 提示此频道不可用 (@$handle) [404]。该作品可能未在此平台开设独立频道。")
            }

            val rssM = rssLinkPattern.matcher(html)
            if (rssM.find()) {
                val feedUrl = rssM.group(1)
                AppLogger.i("YouTubeResolver", "从 HTML 提取到 YouTube 官方原生 Atom 订阅: $feedUrl")
                return feedUrl
            }

            val browseM = browseIdPattern.matcher(html)
            if (browseM.find()) {
                val cid = browseM.group(1)
                AppLogger.i("YouTubeResolver", "从 browseId 匹配到 YouTube Channel ID: $cid")
                return "https://www.youtube.com/feeds/videos.xml?channel_id=$cid"
            }

            val extM = externalIdPattern.matcher(html)
            if (extM.find()) {
                val cid = extM.group(1)
                AppLogger.i("YouTubeResolver", "从 externalId 匹配到 YouTube Channel ID: $cid")
                return "https://www.youtube.com/feeds/videos.xml?channel_id=$cid"
            }

            throw IllegalStateException("未能从 YouTube 页面解析到频道订阅标识，可能该频道已被设为隐藏或注销。")
        }
    }
}
