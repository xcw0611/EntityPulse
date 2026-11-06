package com.entitypulse.app

import com.entitypulse.app.data.model.PlatformType
import com.entitypulse.app.network.PlatformResolver
import org.junit.Assert.*
import org.junit.Test

class PlatformResolverTest {

    @Test
    fun testResolveTwitterUrl() {
        val resolved = PlatformResolver.resolve("https://x.com/TeamCherryGames")
        assertEquals(PlatformType.TWITTER, resolved.platformType)
        assertEquals("@TeamCherryGames", resolved.identifier)
        assertEquals("/twitter/user/TeamCherryGames", resolved.feedPathOrUrl)
        assertFalse(resolved.isDirectRss)
    }

    @Test
    fun testResolveTwitterHandle() {
        val resolved = PlatformResolver.resolve("@TeamCherryGames")
        assertEquals(PlatformType.TWITTER, resolved.platformType)
        assertEquals("@TeamCherryGames", resolved.identifier)
        assertEquals("/twitter/user/TeamCherryGames", resolved.feedPathOrUrl)
    }

    @Test
    fun testResolveBilibiliUrl() {
        val resolved = PlatformResolver.resolve("https://space.bilibili.com/282994")
        assertEquals(PlatformType.BILIBILI, resolved.platformType)
        assertEquals("UID: 282994", resolved.identifier)
        assertEquals("/bilibili/user/video/282994", resolved.feedPathOrUrl)
        assertFalse(resolved.isDirectRss)
    }

    @Test
    fun testResolveYouTubeHandle() {
        val resolved = PlatformResolver.resolve("https://www.youtube.com/@TeamCherry")
        assertEquals(PlatformType.YOUTUBE, resolved.platformType)
        assertEquals("@TeamCherry", resolved.identifier)
        assertEquals("/youtube/user/@TeamCherry", resolved.feedPathOrUrl)
    }

    @Test
    fun testResolveSteamUrl() {
        val resolved = PlatformResolver.resolve("https://store.steampowered.com/app/1030300/Hollow_Knight_Silksong/")
        assertEquals(PlatformType.STEAM, resolved.platformType)
        assertEquals("App: 1030300", resolved.identifier)
        assertEquals("https://steamcommunity.com/games/1030300/rss/", resolved.feedPathOrUrl)
        assertTrue(resolved.isDirectRss)
    }

    @Test
    fun testResolveRedditUrl() {
        val resolved = PlatformResolver.resolve("https://www.reddit.com/r/HollowKnight")
        assertEquals(PlatformType.REDDIT, resolved.platformType)
        assertEquals("r/HollowKnight", resolved.identifier)
        assertEquals("https://www.reddit.com/r/HollowKnight/.rss", resolved.feedPathOrUrl)
        assertTrue(resolved.isDirectRss)
    }

    @Test
    fun testResolveDirectRss() {
        val resolved = PlatformResolver.resolve("https://www.teamcherry.com.au/blog?format=rss")
        assertEquals(PlatformType.RSS_CUSTOM, resolved.platformType)
        assertEquals("https://www.teamcherry.com.au/blog?format=rss", resolved.feedPathOrUrl)
        assertTrue(resolved.isDirectRss)
    }

    @Test
    fun testBuildFinalUrl() {
        val direct = PlatformResolver.buildFinalUrl("https://example.com/rss", "https://rsshub.app")
        assertEquals("https://example.com/rss", direct)

        val relative = PlatformResolver.buildFinalUrl("/twitter/user/TeamCherry", "https://rsshub.app")
        assertEquals("https://rsshub.app/twitter/user/TeamCherry", relative)

        val withToken = PlatformResolver.buildFinalUrl("/twitter/user/TeamCherry", "https://my-rsshub.org", "secret123")
        assertEquals("https://my-rsshub.org/twitter/user/TeamCherry?code=secret123", withToken)
    }
}
