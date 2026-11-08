package com.entitypulse.desktop.network

import com.entitypulse.desktop.util.AppLogger
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory

data class ParsedFeedItem(
    val guid: String,
    val title: String,
    val link: String,
    val author: String?,
    val publishedAt: Long,
    val contentPlainText: String,
    val contentHtml: String?,
    val thumbnailUrl: String?,
    val mediaUrls: List<String>
)

data class ParsedFeed(
    val title: String,
    val description: String?,
    val link: String?,
    val items: List<ParsedFeedItem>
)

object FeedParser {

    private val rfc822Formats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US),
        SimpleDateFormat("dd MMM yyyy HH:mm:ss Z", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    )

    private val htmlTagPattern = Pattern.compile("<[^>]*>")
    private val imgTagPattern = Pattern.compile("<img\\s+[^>]*src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)

    fun parse(xmlContent: String): ParsedFeed {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8)))
        doc.documentElement.normalize()

        val rootName = doc.documentElement.nodeName.lowercase()
        return if (rootName == "feed") {
            parseAtom(doc.documentElement)
        } else {
            parseRss(doc.documentElement)
        }
    }

    private fun parseRss(root: Element): ParsedFeed {
        val channelNode = getDirectChild(root, "channel") ?: root
        val feedTitle = getChildText(channelNode, "title") ?: "未命名订阅源"
        val feedDesc = getChildText(channelNode, "description")
        val feedLink = getChildText(channelNode, "link")

        val items = mutableListOf<ParsedFeedItem>()
        val itemNodes = channelNode.getElementsByTagName("item")
        for (i in 0 until itemNodes.length) {
            val itemElem = itemNodes.item(i) as? Element ?: continue
            val title = getChildText(itemElem, "title") ?: "无标题动态"
            val link = getChildText(itemElem, "link") ?: ""
            val guid = getChildText(itemElem, "guid") ?: (if (link.isNotBlank()) link else "$feedTitle-$i")
            val author = getChildText(itemElem, "author") ?: getChildText(itemElem, "dc:creator")
            val pubDateStr = getChildText(itemElem, "pubDate")
            val publishedAt = parseDate(pubDateStr)

            val rawHtml = getChildText(itemElem, "content:encoded")
                ?: getChildText(itemElem, "description")
                ?: ""

            val plainText = htmlTagPattern.matcher(rawHtml).replaceAll("").trim()
            val mediaUrls = extractImageUrls(rawHtml)

            // 检查 enclosure 标签
            val enclosureNodes = itemElem.getElementsByTagName("enclosure")
            for (j in 0 until enclosureNodes.length) {
                val encElem = enclosureNodes.item(j) as? Element ?: continue
                val url = encElem.getAttribute("url")
                val type = encElem.getAttribute("type")
                if (url.isNotBlank() && (type.startsWith("image/") || type.startsWith("video/"))) {
                    if (!mediaUrls.contains(url)) mediaUrls.add(url)
                }
            }

            val thumb = mediaUrls.firstOrNull()

            items.add(
                ParsedFeedItem(
                    guid = guid,
                    title = title,
                    link = link,
                    author = author,
                    publishedAt = publishedAt,
                    contentPlainText = plainText.ifBlank { title },
                    contentHtml = rawHtml.ifBlank { null },
                    thumbnailUrl = thumb,
                    mediaUrls = mediaUrls
                )
            )
        }

        return ParsedFeed(title = feedTitle, description = feedDesc, link = feedLink, items = items)
    }

    private fun parseAtom(root: Element): ParsedFeed {
        val feedTitle = getChildText(root, "title") ?: "未命名 Atom 源"
        val feedDesc = getChildText(root, "subtitle")
        val feedLink = getAtomLink(root)

        val items = mutableListOf<ParsedFeedItem>()
        val entryNodes = root.getElementsByTagName("entry")
        for (i in 0 until entryNodes.length) {
            val entryElem = entryNodes.item(i) as? Element ?: continue
            val title = getChildText(entryElem, "title") ?: "无标题"
            val link = getAtomLink(entryElem)
            val guid = getChildText(entryElem, "id") ?: (if (link.isNotBlank()) link else "$feedTitle-$i")
            val authorElem = getDirectChild(entryElem, "author")
            val author = authorElem?.let { getChildText(it, "name") }
            val pubDateStr = getChildText(entryElem, "published") ?: getChildText(entryElem, "updated")
            val publishedAt = parseDate(pubDateStr)

            val rawHtml = getChildText(entryElem, "content")
                ?: getChildText(entryElem, "summary")
                ?: ""
            val plainText = htmlTagPattern.matcher(rawHtml).replaceAll("").trim()
            val mediaUrls = extractImageUrls(rawHtml)

            // YouTube media:group 支持
            val mediaGroup = getDirectChild(entryElem, "media:group")
            var ytThumb: String? = null
            if (mediaGroup != null) {
                val mediaThumb = getDirectChild(mediaGroup, "media:thumbnail")
                if (mediaThumb != null) {
                    val url = mediaThumb.getAttribute("url")
                    if (url.isNotBlank()) {
                        ytThumb = url
                        if (!mediaUrls.contains(url)) mediaUrls.add(url)
                    }
                }
            }

            items.add(
                ParsedFeedItem(
                    guid = guid,
                    title = title,
                    link = link,
                    author = author,
                    publishedAt = publishedAt,
                    contentPlainText = plainText.ifBlank { title },
                    contentHtml = rawHtml.ifBlank { null },
                    thumbnailUrl = ytThumb ?: mediaUrls.firstOrNull(),
                    mediaUrls = mediaUrls
                )
            )
        }

        return ParsedFeed(title = feedTitle, description = feedDesc, link = feedLink, items = items)
    }

    private fun getDirectChild(parent: Element, tagName: String): Element? {
        val list = parent.childNodes
        for (i in 0 until list.length) {
            val node = list.item(i)
            if (node.nodeType == Node.ELEMENT_NODE && node.nodeName.equals(tagName, ignoreCase = true)) {
                return node as Element
            }
        }
        return null
    }

    private fun getChildText(parent: Element, tagName: String): String? {
        val list = parent.getElementsByTagName(tagName)
        if (list.length > 0) {
            return list.item(0).textContent?.trim()
        }
        return null
    }

    private fun getAtomLink(elem: Element): String {
        val links = elem.getElementsByTagName("link")
        for (i in 0 until links.length) {
            val l = links.item(i) as? Element ?: continue
            val rel = l.getAttribute("rel")
            if (rel.isBlank() || rel.equals("alternate", ignoreCase = true)) {
                return l.getAttribute("href")
            }
        }
        return ""
    }

    private fun extractImageUrls(html: String): MutableList<String> {
        val urls = mutableListOf<String>()
        val matcher = imgTagPattern.matcher(html)
        while (matcher.find()) {
            val src = matcher.group(1)
            if (src != null && src.isNotBlank() && !urls.contains(src)) {
                urls.add(src)
            }
        }
        return urls
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return System.currentTimeMillis()
        for (fmt in rfc822Formats) {
            try {
                val d = fmt.parse(dateStr.trim())
                if (d != null) return d.time
            } catch (_: Exception) {}
        }
        return System.currentTimeMillis()
    }
}
