package com.entitypulse.app

import com.entitypulse.app.network.FeedParser
import org.junit.Assert.*
import org.junit.Test

class FeedParserTest {

    @Test
    fun testStripHtml() {
        val html = "<p>Hello <b>World</b>!</p><br/>Check <a href=\"https://example.com\">this link</a> &amp; more."
        val stripped = FeedParser.stripHtml(html)
        assertTrue(stripped.contains("Hello World!"))
        assertTrue(stripped.contains("Check this link & more."))
        assertFalse(stripped.contains("<p>"))
        assertFalse(stripped.contains("<b>"))
    }

    @Test
    fun testParseRssXml() {
        val rssXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0">
                <channel>
                    <title>Team Cherry News</title>
                    <link>https://www.teamcherry.com.au</link>
                    <description>Hollow Knight and Silksong updates</description>
                    <item>
                        <title>Silksong Status Update</title>
                        <link>https://www.teamcherry.com.au/blog/silksong-update</link>
                        <guid>tc_post_101</guid>
                        <pubDate>Mon, 06 Nov 2026 12:00:00 +0000</pubDate>
                        <description><![CDATA[<p>Development is going great! Here is a screenshot: <img src="https://example.com/silksong.png"/></p>]]></description>
                    </item>
                </channel>
            </rss>
        """.trimIndent()

        val parsed = FeedParser.parse(rssXml)
        assertEquals("Team Cherry News", parsed.title)
        assertEquals(1, parsed.items.size)

        val item = parsed.items.first()
        assertEquals("Silksong Status Update", item.title)
        assertEquals("tc_post_101", item.guid)
        assertEquals("https://www.teamcherry.com.au/blog/silksong-update", item.link)
        assertEquals("https://example.com/silksong.png", item.thumbnailUrl)
        assertTrue(item.contentPlainText.contains("Development is going great!"))
    }

    @Test
    fun testParseAtomXml() {
        val atomXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
                <title>GitHub Updates</title>
                <link href="https://github.com/teamcherry" rel="alternate"/>
                <entry>
                    <id>tag:github.com,2008:Grit::Commit/12345</id>
                    <title>Release v1.5</title>
                    <updated>2026-11-06T10:00:00Z</updated>
                    <link rel="alternate" href="https://github.com/teamcherry/repo/releases/1.5"/>
                    <summary>Bug fixes and performance improvements.</summary>
                    <author>
                        <name>William Pellen</name>
                    </author>
                </entry>
            </feed>
        """.trimIndent()

        val parsed = FeedParser.parse(atomXml)
        assertEquals("GitHub Updates", parsed.title)
        assertEquals(1, parsed.items.size)

        val item = parsed.items.first()
        assertEquals("Release v1.5", item.title)
        assertEquals("tag:github.com,2008:Grit::Commit/12345", item.guid)
        assertEquals("William Pellen", item.author)
        assertEquals("Bug fixes and performance improvements.", item.contentPlainText)
    }
}
