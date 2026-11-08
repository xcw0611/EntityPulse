package com.entitypulse.app.network

import com.entitypulse.app.util.AppLogger
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.regex.Pattern

object YouTubeResolver {

    private val channelIdPattern = Pattern.compile("UC[a-zA-Z0-9_-]{20,30}")
    private val browseIdPattern = Pattern.compile("\"browseId\"\\s*:\\s*\"(UC[a-zA-Z0-9_-]{20,30})\"")
    private val externalIdPattern = Pattern.compile("\"externalId\"\\s*:\\s*\"(UC[a-zA-Z0-9_-]{20,30})\"")
    private val channelUrlPattern = Pattern.compile("/channel/(UC[a-zA-Z0-9_-]{20,30})")
    private val rssLinkPattern = Pattern.compile("<link\\s+[^>]*rel=[\"']alternate[\"'][^>]*type=[\"']application/rss\\+xml[\"'][^>]*href=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)

    fun isYouTubeTarget(target: String): Boolean {
        val t = target.trim().lowercase()
        return t.startsWith("/youtube/") ||
                t.contains("youtube.com") ||
                t.contains("youtu.be")
    }

    /**
     * 快速验证 YouTube 频道 Handle 是否真实存在（通过轻量 HEAD 请求）
     */
    fun checkHandleExists(handle: String, client: OkHttpClient): Boolean {
        val clean = handle.trim().removePrefix("@")
        if (clean.isBlank()) return false
        val url = "https://www.youtube.com/@$clean"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Accept-Language", "en-US,en;q=0.9")
            .head()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                response.code != 404
            }
        } catch (_: Exception) {
            true
        }
    }

    /**
     * 将任何 YouTube 渠道标识（如 /youtube/user/@TeamCherry、@TeamCherry、Channel URL）
     * 解析为 YouTube 原生官方 Atom XML 订阅链接：
     * https://www.youtube.com/feeds/videos.xml?channel_id=UC...
     */
    fun resolveToNativeRssUrl(target: String, client: OkHttpClient): String {
        val trimmed = target.trim()

        // 1. 如果本身已经是完整的 native RSS 链接
        if (trimmed.contains("feeds/videos.xml?channel_id=")) {
            return trimmed
        }

        // 2. 如果包含 channel_id 参数
        if (trimmed.contains("channel_id=")) {
            val cid = trimmed.substringAfter("channel_id=").substringBefore('&').substringBefore('?')
            if (cid.startsWith("UC")) {
                return "https://www.youtube.com/feeds/videos.xml?channel_id=$cid"
            }
        }

        // 3. 如果包含 /channel/UC...
        val directChannelM = channelUrlPattern.matcher(trimmed)
        if (directChannelM.find()) {
            val cid = directChannelM.group(1)
            return "https://www.youtube.com/feeds/videos.xml?channel_id=$cid"
        }

        // 4. 提取 Handle 或 用户名
        val handle = extractHandle(trimmed)
        if (handle.startsWith("UC") && handle.length >= 22) {
            return "https://www.youtube.com/feeds/videos.xml?channel_id=$handle"
        }

        // 5. 请求 YouTube 频道页面抓取 channelId
        val channelPageUrl = if (handle.startsWith("@")) {
            "https://www.youtube.com/$handle"
        } else {
            "https://www.youtube.com/@$handle"
        }

        AppLogger.d("YouTubeResolver", "正在解析 YouTube 频道 ID: $channelPageUrl")
        val request = Request.Builder()
            .url(channelPageUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 404) {
                throw IllegalStateException("YouTube 上不存在该频道 (@$handle) [HTTP 404]。该主体可能未在 YouTube 开设独立频道，或视频发布在制作团队/创作者频道中（例如 Hazbin Hotel 的官方视频统一发布在 @SpindleHorse 频道）。建议在【主体详情】中修改或删除此渠道。")
            }
            if (!response.isSuccessful) {
                throw IllegalStateException("YouTube 频道页面请求失败: HTTP ${response.code} ${response.message}")
            }
            val html = response.body?.string() ?: throw IllegalStateException("YouTube 页面响应为空")

            // 从 RSS link 标签匹配
            val rssM = rssLinkPattern.matcher(html)
            if (rssM.find()) {
                val feedUrl = rssM.group(1)
                if (!feedUrl.isNullOrBlank()) {
                    AppLogger.i("YouTubeResolver", "从 HTML 头部直接解析出 YouTube RSS: $feedUrl")
                    return feedUrl
                }
            }

            // 从 browseId 匹配
            val bM = browseIdPattern.matcher(html)
            if (bM.find()) {
                val cid = bM.group(1)
                AppLogger.i("YouTubeResolver", "解析出 YouTube browseId: $cid")
                return "https://www.youtube.com/feeds/videos.xml?channel_id=$cid"
            }

            // 从 externalId 匹配
            val eM = externalIdPattern.matcher(html)
            if (eM.find()) {
                val cid = eM.group(1)
                AppLogger.i("YouTubeResolver", "解析出 YouTube externalId: $cid")
                return "https://www.youtube.com/feeds/videos.xml?channel_id=$cid"
            }

            // 兜底正则查找 UC...
            val cM = channelIdPattern.matcher(html)
            if (cM.find()) {
                val cid = cM.group()
                AppLogger.i("YouTubeResolver", "正则兜底解析出 YouTube ChannelId: $cid")
                return "https://www.youtube.com/feeds/videos.xml?channel_id=$cid"
            }

            throw IllegalStateException("无法从 YouTube 页面解析出 Channel ID，请确认代理或网络环境连接正常")
        }
    }

    private fun extractHandle(target: String): String {
        var clean = target.trim()
        if (clean.startsWith("/youtube/user/")) clean = clean.removePrefix("/youtube/user/")
        if (clean.startsWith("/youtube/channel/")) clean = clean.removePrefix("/youtube/channel/")
        if (clean.contains("youtube.com/")) clean = clean.substringAfter("youtube.com/")
        if (clean.contains("youtu.be/")) clean = clean.substringAfter("youtu.be/")
        clean = clean.substringBefore('/').substringBefore('?')
        return clean
    }
}
