package com.entitypulse.app.network

import com.entitypulse.app.util.AppLogger
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
     * 快速验证 X (Twitter) 账号是否真实存在且有效（防伪造与空号）
     */
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
                // 如果页面含有 hasResults:false 且 timeline entries 为空，说明账号不存在或未公开
                if (body.contains("\"hasResults\":false") && body.contains("\"entries\":[]")) {
                    return false
                }
                true
            }
        } catch (_: Exception) {
            true // 遇到网络波动时保守放行，由后续同步具体报错判定
        }
    }

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

        AppLogger.d("TwitterSyndication", "请求 Twitter Syndication: $url")
        client.newCall(request).execute().use { response ->
            if (response.code == 404) {
                AppLogger.w("TwitterSyndication", "Twitter (X) 账号不存在: @$cleanUser [HTTP 404]")
                throw IllegalStateException("X (Twitter) 上未找到该账号 (@$cleanUser) [HTTP 404 Not Found]。该主体可能未注册同名推特，或官方账号使用了不同名称。建议在【主体详情】中修改或删除该渠道（非网络故障）。")
            }
            if (response.code == 429) {
                AppLogger.w("TwitterSyndication", "Twitter (X) 接口遇到频控限制: HTTP 429")
                throw IllegalStateException("X (Twitter) 接口触发了临时访问频控 (HTTP 429 Too Many Requests)，防刷机制生效，将在片刻后自动恢复。此非网络或代理故障，请稍候再试。")
            }
            if (!response.isSuccessful) {
                AppLogger.w("TwitterSyndication", "Twitter 响应失败: HTTP ${response.code} ${response.message}")
                throw IllegalStateException("Twitter Syndication HTTP ${response.code} ${response.message}")
            }
            val html = response.body?.string() ?: throw IllegalStateException("Twitter 响应内容为空")

            val matcher = nextDataPattern.matcher(html)
            if (!matcher.find()) {
                val preview = html.take(250).replace("\n", " ")
                AppLogger.w("TwitterSyndication", "未在 Twitter 页面找到 __NEXT_DATA__。预览: $preview")
                throw IllegalStateException("未能在 Twitter 页面中解析到数据结构，请检查代理配置或稍后重试")
            }

            val jsonStr = matcher.group(1)?.trim() ?: throw IllegalStateException("Twitter 数据为空")
            val root = JSONObject(jsonStr)
            val props = root.optJSONObject("props")
            val pageProps = props?.optJSONObject("pageProps")
            val hasResults = pageProps?.optBoolean("hasResults", true) ?: true
            val timeline = pageProps?.optJSONObject("timeline")
            val entries = timeline?.optJSONArray("entries")

            if (!hasResults && (entries == null || entries.length() == 0)) {
                AppLogger.w("TwitterSyndication", "Twitter 用户 @$cleanUser 不存在或无公开推文")
                throw IllegalStateException("X (Twitter) 上未找到账号 @$cleanUser 的公开推文 [404 Not Found]。该主体可能未在此平台开设独立账号，或账号名称不同。建议在【主体详情】中核对修改。")
            }

            val parsed = parseSyndicationJson(cleanUser, jsonStr)
            AppLogger.i("TwitterSyndication", "成功拉取 Twitter 用户 @$cleanUser 最新动态，共 ${parsed.items.size} 条")
            return parsed
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

                val firstMedia = mediaUrls.firstOrNull()
                val plainText = fullText.trim()
                val title = plainText.take(60).replace("\n", " ").ifBlank { "来自 @$username 的推文" }

                items.add(
                    ParsedFeedItem(
                        guid = "tw_$idStr",
                        title = title,
                        link = tweetUrl,
                        author = author,
                        publishedAt = publishedAt,
                        contentHtml = "<p>$fullText</p>",
                        contentPlainText = plainText,
                        thumbnailUrl = firstMedia,
                        mediaUrls = mediaUrls
                    )
                )
            }
        }

        return ParsedFeed(
            title = "@$username - X / Twitter",
            description = "来自 Twitter 官方 Syndication 接口的最新推文",
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
