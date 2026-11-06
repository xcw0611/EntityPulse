package com.entitypulse.app.network

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

data class ParsedFeed(
    val title: String,
    val description: String?,
    val link: String?,
    val items: List<ParsedFeedItem>
)

data class ParsedFeedItem(
    val guid: String,
    val title: String,
    val link: String,
    val author: String?,
    val publishedAt: Long,
    val contentHtml: String?,
    val contentPlainText: String,
    val thumbnailUrl: String?,
    val mediaUrls: List<String>
)

object FeedParser {

    private val dateFormats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    )

    fun parse(xmlString: String): ParsedFeed {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xmlString))

        var eventType = parser.eventType
        var isAtom = false
        var channelTitle = ""
        var channelDesc: String? = null
        var channelLink: String? = null
        val items = mutableListOf<ParsedFeedItem>()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                val name = parser.name?.lowercase()
                when (name) {
                    "feed" -> isAtom = true
                    "channel" -> isAtom = false
                    "item" -> {
                        val item = parseRssItem(parser)
                        if (item != null) items.add(item)
                    }
                    "entry" -> {
                        val item = parseAtomEntry(parser)
                        if (item != null) items.add(item)
                    }
                    "title" -> if (channelTitle.isEmpty()) channelTitle = parser.nextTextSafely()
                    "description" -> if (channelDesc == null && !isAtom) channelDesc = parser.nextTextSafely()
                    "subtitle" -> if (channelDesc == null && isAtom) channelDesc = parser.nextTextSafely()
                    "link" -> if (channelLink == null) {
                        val href = parser.getAttributeValue(null, "href")
                        channelLink = href ?: parser.nextTextSafely()
                    }
                }
            }
            eventType = parser.next()
        }

        return ParsedFeed(
            title = channelTitle,
            description = channelDesc,
            link = channelLink,
            items = items
        )
    }

    private fun parseRssItem(parser: XmlPullParser): ParsedFeedItem? {
        var title = ""
        var link = ""
        var guid = ""
        var author: String? = null
        var pubDateStr = ""
        var description = ""
        var content = ""
        var thumb: String? = null
        val mediaList = mutableListOf<String>()

        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.name?.lowercase() == "item")) {
            if (eventType == XmlPullParser.START_TAG) {
                val tag = parser.name?.lowercase()
                when (tag) {
                    "title" -> title = parser.nextTextSafely()
                    "link" -> link = parser.nextTextSafely()
                    "guid" -> guid = parser.nextTextSafely()
                    "author", "creator", "dc:creator" -> author = parser.nextTextSafely()
                    "pubdate" -> pubDateStr = parser.nextTextSafely()
                    "description" -> description = parser.nextTextSafely()
                    "encoded", "content:encoded" -> content = parser.nextTextSafely()
                    "enclosure" -> {
                        val url = parser.getAttributeValue(null, "url")
                        val type = parser.getAttributeValue(null, "type")
                        if (url != null) {
                            if (type?.startsWith("image") == true && thumb == null) {
                                thumb = url
                            }
                            mediaList.add(url)
                        }
                    }
                    "media:content", "content" -> {
                        val url = parser.getAttributeValue(null, "url")
                        if (url != null) {
                            if (thumb == null) thumb = url
                            mediaList.add(url)
                        }
                    }
                    "media:thumbnail", "thumbnail" -> {
                        val url = parser.getAttributeValue(null, "url")
                        if (url != null && thumb == null) thumb = url
                    }
                }
            }
            eventType = parser.next()
        }

        val html = if (content.isNotBlank()) content else description
        val plainText = stripHtml(html)
        val extractedImages = extractImagesFromHtml(html)
        if (thumb == null && extractedImages.isNotEmpty()) {
            thumb = extractedImages.first()
        }
        mediaList.addAll(extractedImages.filterNot { mediaList.contains(it) })

        val finalTitle = title.ifBlank { plainText.take(60).ifBlank { "无标题动态" } }
        val finalGuid = guid.ifBlank { link.ifBlank { "${finalTitle.hashCode()}_${pubDateStr.hashCode()}" } }
        val publishedAt = parseDate(pubDateStr)

        return ParsedFeedItem(
            guid = finalGuid,
            title = finalTitle,
            link = link,
            author = author,
            publishedAt = publishedAt,
            contentHtml = html,
            contentPlainText = plainText,
            thumbnailUrl = thumb,
            mediaUrls = mediaList
        )
    }

    private fun parseAtomEntry(parser: XmlPullParser): ParsedFeedItem? {
        var title = ""
        var link = ""
        var id = ""
        var author: String? = null
        var updatedStr = ""
        var summary = ""
        var content = ""
        var thumb: String? = null
        val mediaList = mutableListOf<String>()

        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.name?.lowercase() == "entry")) {
            if (eventType == XmlPullParser.START_TAG) {
                val tag = parser.name?.lowercase()
                when (tag) {
                    "title" -> title = parser.nextTextSafely()
                    "id" -> id = parser.nextTextSafely()
                    "published", "updated" -> if (updatedStr.isEmpty()) updatedStr = parser.nextTextSafely()
                    "summary" -> summary = parser.nextTextSafely()
                    "content" -> content = parser.nextTextSafely()
                    "link" -> {
                        val rel = parser.getAttributeValue(null, "rel")
                        val href = parser.getAttributeValue(null, "href")
                        if (href != null) {
                            if (rel == null || rel == "alternate") {
                                link = href
                            } else if (rel == "enclosure") {
                                mediaList.add(href)
                            }
                        }
                    }
                    "media:thumbnail" -> {
                        val url = parser.getAttributeValue(null, "url")
                        if (url != null && thumb == null) thumb = url
                    }
                    "author" -> {
                        // 子标签解析 author name
                        while (!(parser.next() == XmlPullParser.END_TAG && parser.name?.lowercase() == "author")) {
                            if (parser.eventType == XmlPullParser.START_TAG && parser.name?.lowercase() == "name") {
                                author = parser.nextTextSafely()
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        val html = if (content.isNotBlank()) content else summary
        val plainText = stripHtml(html)
        val extractedImages = extractImagesFromHtml(html)
        if (thumb == null && extractedImages.isNotEmpty()) {
            thumb = extractedImages.first()
        }
        mediaList.addAll(extractedImages.filterNot { mediaList.contains(it) })

        val finalTitle = title.ifBlank { plainText.take(60).ifBlank { "无标题动态" } }
        val finalGuid = id.ifBlank { link.ifBlank { "${finalTitle.hashCode()}_${updatedStr.hashCode()}" } }
        val publishedAt = parseDate(updatedStr)

        return ParsedFeedItem(
            guid = finalGuid,
            title = finalTitle,
            link = link,
            author = author,
            publishedAt = publishedAt,
            contentHtml = html,
            contentPlainText = plainText,
            thumbnailUrl = thumb,
            mediaUrls = mediaList
        )
    }

    fun parseDate(dateStr: String): Long {
        if (dateStr.isBlank()) return System.currentTimeMillis()
        val cleaned = dateStr.trim()
        for (fmt in dateFormats) {
            try {
                val parsed = fmt.parse(cleaned)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        return System.currentTimeMillis()
    }

    fun stripHtml(html: String): String {
        if (html.isBlank()) return ""
        var text = html.replace("<style(?s:.)*?</style>".toRegex(), "")
            .replace("<script(?s:.)*?</script>".toRegex(), "")
            .replace("<br\\s*/?>".toRegex(RegexOption.IGNORE_CASE), "\n")
            .replace("</p>".toRegex(RegexOption.IGNORE_CASE), "\n\n")
            .replace("<[^>]+>".toRegex(), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
        return text.trim().replace("\n{3,}".toRegex(), "\n\n")
    }

    private fun extractImagesFromHtml(html: String): List<String> {
        val images = mutableListOf<String>()
        val imgPattern = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        val matcher = imgPattern.matcher(html)
        while (matcher.find()) {
            val src = matcher.group(1)
            if (!src.isNullOrBlank() && !src.startsWith("data:")) {
                images.add(src)
            }
        }
        return images
    }

    private fun XmlPullParser.nextTextSafely(): String {
        val result = runCatching { this.nextText() }.getOrDefault("")
        return result.trim()
    }
}
