package com.entitypulse.desktop.network

import com.entitypulse.desktop.util.AppLogger
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

object TwitterSyndicationParser {

    private val twitterDateFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.US)
    private val nextDataPattern = Pattern.compile("<script\\s+id=[\"']__NEXT_DATA__[\"'][^>]*>(.*?)</script>", Pattern.DOTALL)

    fun checkUserExists(username: String, client: OkHttpClient): Boolean {
        val clean = username.trim().removePrefix("@")
        if (clean.isBlank()) return false
        val url = "https://syndication.twitter.com/srv/timeline-profile/screen-name/$clean"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.code == 404) return false
                val body = response.body?.string() ?: return false
                if (body.contains("\"hasResults\":false") && body.contains("\"entries\":[]")) {
                    return false
                }
                true
            }
        } catch (_: Exception) {
            true
        }
    }

    fun fetchTimeline(username: String, client: OkHttpClient): ParsedFeed {
        val cleanUser = username.trim().removePrefix("@")
        val url = "https://syndication.twitter.com/srv/timeline-profile/screen-name/$cleanUser"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9,zh-CN;q=0.8,zh;q=0.7")
            .build()

        AppLogger.d("TwitterSyndication", "请求 Twitter Syndication: $url")
        client.newCall(request).execute().use { response ->
            if (response.code == 404) {
                AppLogger.w("TwitterSyndication", "Twitter (X) 账号不存在: @$cleanUser [HTTP 404]")
                throw IllegalStateException("X (Twitter) 上未找到该账号 (@$cleanUser) [HTTP 404 Not Found]。该主体可能未注册同名推特，或官方账号使用了不同名称。建议修改或删除该渠道（非网络故障）。")
            }
            if (response.code == 429) {
                AppLogger.w("TwitterSyndication", "Twitter Syndication 触发频控限制 (HTTP 429)")
                throw IllegalStateException("Twitter 官方接口触发了临时访问频控 (HTTP 429 Too Many Requests)，将在片刻后自动恢复。此非代理故障，请稍候再试。")
            }
            if (!response.isSuccessful) {
                AppLogger.w("TwitterSyndication", "Twitter Syndication HTTP 错误: ${response.code} ${response.message}")
                throw IllegalStateException("Twitter 接口请求失败: HTTP ${response.code} ${response.message}")
            }

            val html = response.body?.string() ?: throw IllegalStateException("Twitter Syndication 响应为空")
            return parseSyndicationHtml(cleanUser, html)
        }
    }

    private fun parseSyndicationHtml(username: String, html: String): ParsedFeed {
        val matcher = nextDataPattern.matcher(html)
        if (!matcher.find()) {
            throw IllegalStateException("未能从 Twitter 页面解析到 __NEXT_DATA__ 数据，可能页面已被反爬阻断")
        }
        val jsonStr = matcher.group(1)
        val root = JSONObject(jsonStr)
        val props = root.optJSONObject("props")
        val pageProps = props?.optJSONObject("pageProps")
        val timeline = pageProps?.optJSONObject("timeline")

        val hasResults = timeline?.optBoolean("hasResults", true) ?: true
        val entries = timeline?.optJSONArray("entries")
        if (!hasResults || entries == null || entries.length() == 0) {
            return ParsedFeed(
                title = "X / Twitter (@$username)",
                description = "该账号近期暂未发布任何公开推文",
                link = "https://x.com/$username",
                items = emptyList()
            )
        }

        val items = mutableListOf<ParsedFeedItem>()
        for (i in 0 until entries.length()) {
            val entryObj = entries.optJSONObject(i) ?: continue
            val content = entryObj.optJSONObject("content")
            val tweet = content?.optJSONObject("tweet") ?: continue

            val tweetId = tweet.optString("id_str").ifBlank { tweet.optLong("id", 0L).toString() }
            val text = tweet.optString("text", "")
            val createdAtStr = tweet.optString("created_at", "")
            val publishedAt = parseTwitterDate(createdAtStr)
            val user = tweet.optJSONObject("user")
            val authorName = user?.optString("name", username)
            val screenName = user?.optString("screen_name")?.ifBlank { username } ?: username
            val rawPermalink = tweet.optString("permalink", "")
            val canonicalTweetUrl = if (tweetId.isNotBlank() && tweetId != "0") {
                "https://x.com/$screenName/status/$tweetId"
            } else if (rawPermalink.startsWith("http://") || rawPermalink.startsWith("https://")) {
                rawPermalink.replace("twitter.com", "x.com")
            } else if (rawPermalink.isNotBlank()) {
                "https://x.com/${rawPermalink.trimStart('/')}"
            } else {
                "https://x.com/$screenName"
            }

            val mediaUrls = mutableListOf<String>()
            val mediaEntities = tweet.optJSONObject("entities")?.optJSONArray("media")
            if (mediaEntities != null) {
                for (j in 0 until mediaEntities.length()) {
                    val m = mediaEntities.optJSONObject(j) ?: continue
                    val mediaUrl = m.optString("media_url_https", "")
                    if (mediaUrl.isNotBlank() && !mediaUrls.contains(mediaUrl)) {
                        mediaUrls.add(mediaUrl)
                    }
                }
            }

            val photos = tweet.optJSONArray("photos")
            if (photos != null) {
                for (k in 0 until photos.length()) {
                    val p = photos.optJSONObject(k) ?: continue
                    val url = p.optString("url", "")
                    if (url.isNotBlank() && !mediaUrls.contains(url)) {
                        mediaUrls.add(url)
                    }
                }
            }

            val video = tweet.optJSONObject("video")
            val posterUrl = video?.optString("poster", "")
            if (!posterUrl.isNullOrBlank() && !mediaUrls.contains(posterUrl)) {
                mediaUrls.add(posterUrl)
            }

            items.add(
                ParsedFeedItem(
                    guid = tweetId,
                    title = text.take(60).replace("\n", " ") + (if (text.length > 60) "..." else ""),
                    link = canonicalTweetUrl,
                    author = authorName,
                    publishedAt = publishedAt,
                    contentPlainText = text,
                    contentHtml = text,
                    thumbnailUrl = mediaUrls.firstOrNull(),
                    mediaUrls = mediaUrls
                )
            )
        }

        return ParsedFeed(
            title = "X / Twitter (@$username)",
            description = "Twitter Syndication Feed",
            link = "https://x.com/$username",
            items = items
        )
    }

    private fun parseTwitterDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            twitterDateFormat.parse(dateStr)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }
}
