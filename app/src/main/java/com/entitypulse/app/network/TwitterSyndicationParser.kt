package com.entitypulse.app.network

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern

object TwitterSyndicationParser {

    private val twitterDateFormat = SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.US)
    private val nextDataPattern = Pattern.compile("<script\\s+id=[\"']__NEXT_DATA__[\"'][^>]*>(.*?)</script>", Pattern.DOTALL)

    /**
     * 免登录直接从 Twitter 官方 Syndication 接口拉取用户最新推文时间线
     */
    fun fetchTimeline(username: String, client: OkHttpClient): ParsedFeed {
        val cleanUser = username.trim().removePrefix("@")
        val url = "https://syndication.twitter.com/srv/timeline-profile/screen-name/$cleanUser"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9,zh-CN;q=0.8,zh;q=0.7")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Twitter Syndication HTTP ${response.code} ${response.message}")
            }
            val html = response.body?.string() ?: throw IllegalStateException("Twitter 响应内容为空")

            val matcher = nextDataPattern.matcher(html)
            if (!matcher.find()) {
                throw IllegalStateException("未能在 Twitter 页面中解析到数据结构，请检查代理配置或稍后重试")
            }

            val jsonStr = matcher.group(1)?.trim() ?: throw IllegalStateException("Twitter 数据为空")
            return parseSyndicationJson(cleanUser, jsonStr)
        }
    }

    fun parseSyndicationJson(username: String, jsonStr: String): ParsedFeed {
        val root = JSONObject(jsonStr)
        val props = root.optJSONObject("props")
        val pageProps = props?.optJSONObject("pageProps")
        val timeline = pageProps?.optJSONObject("timeline")
        val entries = timeline?.optJSONArray("entries")

        val items = mutableListOf<ParsedFeedItem>()

        if (entries != null) {
            for (i in 0 until entries.length()) {
                val entry = entries.optJSONObject(i) ?: continue
                val type = entry.optString("type")
                if (type != "tweet") continue

                val content = entry.optJSONObject("content")
                val tweet = content?.optJSONObject("tweet") ?: continue

                val idStr = tweet.optString("id_str").ifBlank { tweet.optString("conversation_id_str") }
                if (idStr.isBlank()) continue

                val fullText = tweet.optString("full_text").ifBlank { tweet.optString("text") }
                val permalink = tweet.optString("permalink")
                val tweetUrl = if (permalink.isNotBlank()) {
                    if (permalink.startsWith("http")) permalink else "https://x.com$permalink"
                } else {
                    "https://x.com/$username/status/$idStr"
                }

                val createdAtStr = tweet.optString("created_at")
                val publishedAt = parseTwitterDate(createdAtStr)

                val userObj = tweet.optJSONObject("user")
                val author = userObj?.optString("name") ?: "@$username"

                // 提取多媒体图片
                val mediaUrls = mutableListOf<String>()
                val extEntities = tweet.optJSONObject("extended_entities")
                val extMedia = extEntities?.optJSONArray("media")
                if (extMedia != null) {
                    for (m in 0 until extMedia.length()) {
                        val mObj = extMedia.optJSONObject(m)
                        val mediaUrl = mObj?.optString("media_url_https")
                        if (!mediaUrl.isNullOrBlank() && !mediaUrls.contains(mediaUrl)) {
                            mediaUrls.add(mediaUrl)
                        }
                    }
                }

                // 兜底 entities.media
                if (mediaUrls.isEmpty()) {
                    val entities = tweet.optJSONObject("entities")
                    val media = entities?.optJSONArray("media")
                    if (media != null) {
                        for (m in 0 until media.length()) {
                            val mObj = media.optJSONObject(m)
                            val mediaUrl = mObj?.optString("media_url_https")
                            if (!mediaUrl.isNullOrBlank() && !mediaUrls.contains(mediaUrl)) {
                                mediaUrls.add(mediaUrl)
                            }
                        }
                    }
                }

                val thumb = mediaUrls.firstOrNull()
                val title = fullText.lineSequence().firstOrNull()?.take(70)?.ifBlank { "推文 $idStr" } ?: "推文 $idStr"

                items.add(
                    ParsedFeedItem(
                        guid = "tweet_$idStr",
                        title = title,
                        link = tweetUrl,
                        author = author,
                        publishedAt = publishedAt,
                        contentHtml = fullText.replace("\n", "<br/>"),
                        contentPlainText = fullText,
                        thumbnailUrl = thumb,
                        mediaUrls = mediaUrls
                    )
                )
            }
        }

        if (items.isEmpty()) {
            throw IllegalStateException("未获取到 @$username 的公开推文（账号可能私密或近期无动态）")
        }

        return ParsedFeed(
            title = "@$username on X",
            description = "X / Twitter @$username 官方推文时间线",
            link = "https://x.com/$username",
            items = items
        )
    }

    private fun parseTwitterDate(dateStr: String): Long {
        if (dateStr.isBlank()) return System.currentTimeMillis()
        return try {
            twitterDateFormat.parse(dateStr)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }
}
