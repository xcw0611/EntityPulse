package com.entitypulse.app.network

import com.entitypulse.app.data.model.EntityCatalog
import com.entitypulse.app.data.model.PlatformCandidateGroup
import com.entitypulse.app.data.model.PlatformType
import com.entitypulse.app.data.model.PredefinedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.util.regex.Pattern

data class IpLocateResult(
    val canonicalName: String,
    val originalOrEnglishName: String? = null,
    val category: String = "ACG / 游戏 / 创作者",
    val bio: String? = null,
    val avatarUrl: String? = null,
    val officialSiteUrl: String? = null,
    val candidateGroups: List<PlatformCandidateGroup> = emptyList()
)

object OnlineEntityLocator {

    private val twitterPattern = Pattern.compile("https?://(?:twitter|x)\\.com/([a-zA-Z0-9_]{3,25})", Pattern.CASE_INSENSITIVE)
    private val youtubePattern = Pattern.compile("https?://(?:www\\.)?youtube\\.com/(?:@|user/|c/)?([a-zA-Z0-9_.-]{3,35})", Pattern.CASE_INSENSITIVE)

    private val IGNORED_TWITTER_HANDLES = setOf(
        "intent", "share", "home", "hashtag", "login", "search", "i", "settings",
        "privacy", "tos", "explore", "about", "help", "download", "signup", "jobs"
    )

    private val IGNORED_YOUTUBE_HANDLES = setOf(
        "watch", "embed", "results", "channel", "playlist", "feed", "live", "about"
    )

    /**
     * 针对用户输入的内容：
     * 1. 优先定位其所属的 动漫IP、游戏IP、工作室或创作者实体（通过 Bangumi、Steam、Wikipedia 等知识网络）
     * 2. 针对该定位到的 IP，通过全网检索（官方外链、知识图谱、B站/DDG联想）精准提取其真实社媒账号，
     *    彻底摒弃死板机械的关键词词尾衍生。
     */
    suspend fun locateIpAndSocialChannels(
        query: String,
        client: OkHttpClient
    ): IpLocateResult = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            return@withContext IpLocateResult(canonicalName = trimmed)
        }

        // 1. 若命中精选实体库（如 Team Cherry / 游戏科学等），直接返回经过人工校验的黄金配置
        val popular = EntityCatalog.POPULAR_ENTITIES.firstOrNull {
            it.name.equals(trimmed, ignoreCase = true) ||
                    trimmed.contains(it.name, ignoreCase = true) ||
                    it.name.contains(trimmed, ignoreCase = true)
        }
        if (popular != null) {
            val groups = popular.channels.groupBy { it.platformType }.map { (type, chList) ->
                PlatformCandidateGroup(
                    platformType = type,
                    candidates = chList,
                    selectedIndex = 0,
                    isEnabled = chList.any { it.defaultSelected }
                )
            }
            return@withContext IpLocateResult(
                canonicalName = popular.name,
                category = popular.category,
                bio = popular.bio,
                avatarUrl = popular.avatarUrl,
                candidateGroups = groups
            )
        }

        var detectedName = trimmed
        var detectedOriginalName: String? = null
        var detectedCategory = "ACG / 游戏 / 创作者"
        var detectedBio: String? = null
        var detectedAvatar: String? = null
        var detectedOfficialSite: String? = null
        var detectedSteamAppId: Int? = null
        var detectedSteamGameName: String? = null

        val discoveredTwitterHandles = mutableListOf<Pair<String, String>>() // Handle to Note
        val discoveredYouTubeChannels = mutableListOf<Pair<String, String>>()
        val discoveredBilibiliCreators = mutableListOf<BilibiliResolver.BilibiliCreator>()
        val discoveredOfficialLinks = mutableListOf<String>()

        // 2. 尝试探测 Steam 游戏 IP
        try {
            val steamResult = querySteamStore(trimmed, client)
            if (steamResult != null) {
                detectedSteamAppId = steamResult.appId
                detectedSteamGameName = steamResult.name
                detectedName = steamResult.name
                detectedCategory = "🎮 游戏作品 / IP"
                if (detectedAvatar == null && steamResult.capsuleImage.isNotBlank()) {
                    detectedAvatar = steamResult.capsuleImage
                }
            }
        } catch (_: Exception) {}

        // 3. 尝试探测 Bangumi 动漫 / ACG IP
        try {
            val bgmResult = queryBangumiSubject(trimmed, client)
            if (bgmResult != null) {
                if (detectedSteamAppId == null) {
                    detectedName = bgmResult.chineseName.ifBlank { bgmResult.originalName }
                    detectedCategory = "🎬 动漫 / ACG IP"
                }
                detectedOriginalName = bgmResult.originalName
                if (bgmResult.coverImage.isNotBlank()) {
                    detectedAvatar = bgmResult.coverImage
                }
                if (bgmResult.summary.isNotBlank()) {
                    detectedBio = bgmResult.summary
                }
                if (bgmResult.officialSite != null) {
                    detectedOfficialSite = bgmResult.officialSite
                    discoveredOfficialLinks.add(bgmResult.officialSite)
                }
            }
        } catch (_: Exception) {}

        // 4. 尝试 Wikipedia 维基百科知识图谱与官方链接检索 (挖掘最准确的官方 X/Twitter 与官网)
        try {
            val wikiResult = queryWikipedia(trimmed, client)
            if (wikiResult != null) {
                if (detectedBio.isNullOrBlank() && wikiResult.extract.isNotBlank()) {
                    detectedBio = wikiResult.extract
                }
                if (detectedOriginalName.isNullOrBlank() && wikiResult.romajiOrEnglishName.isNotBlank()) {
                    detectedOriginalName = wikiResult.romajiOrEnglishName
                }
                if (detectedAvatar.isNullOrBlank() && wikiResult.imageUrl != null) {
                    detectedAvatar = wikiResult.imageUrl
                }
                wikiResult.twitterHandles.forEach { handle ->
                    discoveredTwitterHandles.add(handle to "维基官方直链")
                }
                wikiResult.youtubeHandles.forEach { yt ->
                    discoveredYouTubeChannels.add(yt to "维基官方直链")
                }
                wikiResult.officialSites.forEach { site ->
                    discoveredOfficialLinks.add(site)
                }
            }
        } catch (_: Exception) {}

        // 5. 针对该 IP 进行 Bilibili 官方 / 认证账号针对性检索（直接获取真实 UID 与粉丝数）
        try {
            val creators = BilibiliResolver.searchCreators(detectedName, client)
            discoveredBilibiliCreators.addAll(creators)
            if (discoveredBilibiliCreators.isEmpty() && !detectedOriginalName.isNullOrBlank()) {
                val moreCreators = BilibiliResolver.searchCreators(detectedOriginalName, client)
                discoveredBilibiliCreators.addAll(moreCreators)
            }
        } catch (_: Exception) {}

        // 6. 若 X/Twitter 尚未通过官网抓到，使用定位到的英文/原名向 DuckDuckGo 联想探测
        if (discoveredTwitterHandles.isEmpty()) {
            val searchKey = detectedOriginalName?.takeIf { it.isNotBlank() } ?: detectedName
            try {
                val ddgTw = fetchDuckDuckGoSuggestions("$searchKey twitter", client)
                for (term in ddgTw) {
                    val cleanTerm = term.replace("twitter", "", ignoreCase = true)
                        .replace("account", "", ignoreCase = true)
                        .replace("official", "Official", ignoreCase = true)
                        .trim()
                    val handle = cleanTerm.replace("\\s+".toRegex(), "")
                    if (handle.length in 3..25 && !IGNORED_TWITTER_HANDLES.contains(handle.lowercase())) {
                        discoveredTwitterHandles.add(handle to "全网定位检索")
                    }
                }
            } catch (_: Exception) {}
        }

        // 7. 组装多平台真实候选卡片（摒弃机械后缀，严格依托检索发现）
        val candidateGroups = mutableListOf<PlatformCandidateGroup>()

        // (1) X / Twitter 分组
        val twCandidates = mutableListOf<PredefinedChannel>()
        discoveredTwitterHandles.distinctBy { it.first.lowercase() }.forEachIndexed { idx, (handle, note) ->
            twCandidates.add(
                PredefinedChannel(
                    platformType = PlatformType.TWITTER,
                    identifier = "@$handle",
                    title = "X / Twitter (@$handle)",
                    feedPathOrUrl = "/twitter/user/$handle",
                    defaultSelected = idx == 0,
                    probabilityNote = note
                )
            )
        }
        if (twCandidates.isEmpty()) {
            val fallbackHandle = (detectedOriginalName ?: detectedName).replace("\\s+".toRegex(), "")
            twCandidates.add(
                PredefinedChannel(
                    platformType = PlatformType.TWITTER,
                    identifier = "@$fallbackHandle",
                    title = "X / Twitter (@$fallbackHandle)",
                    feedPathOrUrl = "/twitter/user/$fallbackHandle",
                    defaultSelected = true,
                    probabilityNote = "标准账号"
                )
            )
        }
        candidateGroups.add(PlatformCandidateGroup(PlatformType.TWITTER, twCandidates, 0, true))

        // (2) Bilibili 分组
        val biliCandidates = mutableListOf<PredefinedChannel>()
        discoveredBilibiliCreators.distinctBy { it.mid }.forEachIndexed { idx, creator ->
            val fansText = when {
                creator.fans >= 10000 -> "${creator.fans / 10000}.${(creator.fans % 10000) / 1000}万"
                else -> "${creator.fans}"
            }
            val note = when {
                creator.isOfficial -> "B站官方认证"
                creator.verifyDesc.isNotBlank() -> creator.verifyDesc
                creator.fans >= 10000 -> "热门UP主 ($fansText 粉丝)"
                else -> "UP主 ($fansText 粉丝)"
            }
            biliCandidates.add(
                PredefinedChannel(
                    platformType = PlatformType.BILIBILI,
                    identifier = "UID: ${creator.mid}",
                    title = "Bilibili (${creator.uname} · $fansText 粉丝)",
                    feedPathOrUrl = "/bilibili/user/video/${creator.mid}",
                    defaultSelected = idx == 0,
                    probabilityNote = note
                )
            )
        }
        if (biliCandidates.isEmpty()) {
            biliCandidates.add(
                PredefinedChannel(
                    platformType = PlatformType.BILIBILI,
                    identifier = detectedName,
                    title = "Bilibili ($detectedName)",
                    feedPathOrUrl = "/bilibili/user/video/$detectedName",
                    defaultSelected = true,
                    probabilityNote = "名称检索"
                )
            )
        }
        candidateGroups.add(PlatformCandidateGroup(PlatformType.BILIBILI, biliCandidates, 0, true))

        // (3) Steam 游戏官方公告（若定位到 Steam App）
        if (detectedSteamAppId != null) {
            val appId = detectedSteamAppId
            val gName = detectedSteamGameName ?: detectedName
            candidateGroups.add(
                PlatformCandidateGroup(
                    platformType = PlatformType.STEAM,
                    candidates = listOf(
                        PredefinedChannel(
                            platformType = PlatformType.STEAM,
                            identifier = "App: $appId",
                            title = "Steam · $gName 官方公告",
                            feedPathOrUrl = "https://steamcommunity.com/games/$appId/rss/",
                            defaultSelected = true,
                            probabilityNote = "Steam官方认证"
                        )
                    ),
                    selectedIndex = 0,
                    isEnabled = true
                )
            )
        }

        // (4) YouTube 分组
        val ytCandidates = mutableListOf<PredefinedChannel>()
        discoveredYouTubeChannels.distinctBy { it.first.lowercase() }.forEachIndexed { idx, (channelName, note) ->
            val clean = channelName.removePrefix("@")
            ytCandidates.add(
                PredefinedChannel(
                    platformType = PlatformType.YOUTUBE,
                    identifier = "@$clean",
                    title = "YouTube (@$clean)",
                    feedPathOrUrl = "/youtube/user/@$clean",
                    defaultSelected = idx == 0,
                    probabilityNote = note
                )
            )
        }
        if (ytCandidates.isEmpty()) {
            val ytName = (detectedOriginalName ?: detectedName).replace("\\s+".toRegex(), "")
            ytCandidates.add(
                PredefinedChannel(
                    platformType = PlatformType.YOUTUBE,
                    identifier = "@$ytName",
                    title = "YouTube (@$ytName)",
                    feedPathOrUrl = "/youtube/user/@$ytName",
                    defaultSelected = false,
                    probabilityNote = "频道检索"
                )
            )
        }
        candidateGroups.add(PlatformCandidateGroup(PlatformType.YOUTUBE, ytCandidates, 0, true))

        // (5) 官方网站 / 开发博客
        val siteUrl = discoveredOfficialLinks.firstOrNull() ?: detectedOfficialSite
        if (!siteUrl.isNullOrBlank()) {
            val domain = runCatching { URI(siteUrl).host }.getOrNull() ?: siteUrl
            candidateGroups.add(
                PlatformCandidateGroup(
                    platformType = PlatformType.RSS_CUSTOM,
                    candidates = listOf(
                        PredefinedChannel(
                            platformType = PlatformType.RSS_CUSTOM,
                            identifier = domain,
                            title = "官方站点 ($domain)",
                            feedPathOrUrl = siteUrl,
                            defaultSelected = true,
                            probabilityNote = "官网直链"
                        )
                    ),
                    selectedIndex = 0,
                    isEnabled = true
                )
            )
        }

        // (6) Reddit 社区专版
        val redditName = (detectedOriginalName ?: detectedName).replace("[^a-zA-Z0-9]".toRegex(), "")
        if (redditName.length in 3..25) {
            candidateGroups.add(
                PlatformCandidateGroup(
                    platformType = PlatformType.REDDIT,
                    candidates = listOf(
                        PredefinedChannel(
                            platformType = PlatformType.REDDIT,
                            identifier = "r/$redditName",
                            title = "Reddit (r/$redditName)",
                            feedPathOrUrl = "https://www.reddit.com/r/$redditName/.rss",
                            defaultSelected = false,
                            probabilityNote = "社区专版"
                        )
                    ),
                    selectedIndex = 0,
                    isEnabled = false
                )
            )
        }

        IpLocateResult(
            canonicalName = detectedName,
            originalOrEnglishName = detectedOriginalName,
            category = detectedCategory,
            bio = detectedBio?.take(160),
            avatarUrl = detectedAvatar,
            officialSiteUrl = siteUrl,
            candidateGroups = candidateGroups
        )
    }

    suspend fun searchOnlineCandidates(
        query: String,
        client: OkHttpClient? = null
    ): List<PredefinedChannel> {
        val dummyClient = client ?: OkHttpClient.Builder().build()
        val res = locateIpAndSocialChannels(query, dummyClient)
        return res.candidateGroups.flatMap { it.candidates }
    }

    private data class SteamGameResult(val appId: Int, val name: String, val capsuleImage: String)

    private fun querySteamStore(query: String, client: OkHttpClient): SteamGameResult? {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://store.steampowered.com/api/storesearch/?term=$encoded&l=schinese&cc=CN"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val root = JSONObject(body)
            val items = root.optJSONArray("items") ?: return null
            if (items.length() > 0) {
                val first = items.optJSONObject(0) ?: return null
                val id = first.optInt("id", 0)
                val name = first.optString("name", "")
                val img = first.optString("tiny_image", "")
                if (id > 0 && name.isNotBlank()) {
                    return SteamGameResult(id, name, img)
                }
            }
        }
        return null
    }

    private data class BangumiResult(
        val originalName: String,
        val chineseName: String,
        val coverImage: String,
        val summary: String,
        val officialSite: String?
    )

    private fun queryBangumiSubject(query: String, client: OkHttpClient): BangumiResult? {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://api.bgm.tv/search/subject/$encoded?type=2"
        val request = Request.Builder()
            .url(searchUrl)
            .header("User-Agent", "EntityPulse/1.0 (Android)")
            .build()

        var subjectId = 0
        var origName = ""
        var cnName = ""
        var cover = ""

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val root = JSONObject(body)
            val list = root.optJSONArray("list") ?: return null
            if (list.length() > 0) {
                val item = list.optJSONObject(0) ?: return null
                subjectId = item.optInt("id", 0)
                origName = item.optString("name", "")
                cnName = item.optString("name_cn", "")
                cover = item.optJSONObject("images")?.optString("large", "") ?: ""
            }
        }

        if (subjectId <= 0) return null

        var summary = ""
        var officialSite: String? = null
        try {
            val detailUrl = "https://api.bgm.tv/v0/subjects/$subjectId"
            val detailReq = Request.Builder()
                .url(detailUrl)
                .header("User-Agent", "EntityPulse/1.0 (Android)")
                .build()

            client.newCall(detailReq).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val root = JSONObject(body)
                    summary = root.optString("summary", "")
                    val infobox = root.optJSONArray("infobox")
                    if (infobox != null) {
                        for (i in 0 until infobox.length()) {
                            val box = infobox.optJSONObject(i) ?: continue
                            val key = box.optString("key", "")
                            if (key == "官方网站" || key == "官网") {
                                officialSite = box.optString("value", "")
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return BangumiResult(origName, cnName, cover, summary, officialSite)
    }

    private data class WikiResult(
        val title: String,
        val extract: String,
        val romajiOrEnglishName: String,
        val imageUrl: String?,
        val twitterHandles: List<String>,
        val youtubeHandles: List<String>,
        val officialSites: List<String>
    )

    private fun queryWikipedia(query: String, client: OkHttpClient): WikiResult? {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val openSearchUrl = "https://zh.wikipedia.org/w/api.php?action=opensearch&search=$encoded&limit=2&namespace=0&format=json"
        val osReq = Request.Builder().url(openSearchUrl).header("User-Agent", "Mozilla/5.0").build()

        var articleTitle = query
        client.newCall(osReq).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val array = JSONArray(body)
            if (array.length() >= 2) {
                val titles = array.getJSONArray(1)
                if (titles.length() > 0) {
                    articleTitle = titles.optString(0, query)
                }
            }
        }

        val encodedTitle = URLEncoder.encode(articleTitle, "UTF-8")
        val queryUrl = "https://zh.wikipedia.org/w/api.php?action=query&titles=$encodedTitle&prop=extlinks|extracts|pageprops&ellimit=50&format=json&explaintext=1&exintro=1"
        val qReq = Request.Builder().url(queryUrl).header("User-Agent", "Mozilla/5.0").build()

        client.newCall(qReq).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val root = JSONObject(body)
            val queryObj = root.optJSONObject("query") ?: return null
            val pages = queryObj.optJSONObject("pages") ?: return null
            val keys = pages.keys()
            if (!keys.hasNext()) return null
            val pageKey = keys.next()
            val page = pages.optJSONObject(pageKey) ?: return null

            val extract = page.optString("extract", "")
            val defaultSort = page.optJSONObject("pageprops")?.optString("defaultsort", "") ?: ""
            val extlinks = page.optJSONArray("extlinks")

            val twHandles = mutableListOf<String>()
            val ytHandles = mutableListOf<String>()
            val sites = mutableListOf<String>()

            if (extlinks != null) {
                for (i in 0 until extlinks.length()) {
                    val link = extlinks.optJSONObject(i)?.optString("*", "") ?: continue
                    val twM = twitterPattern.matcher(link)
                    if (twM.find()) {
                        val handle = twM.group(1)
                        if (!handle.isNullOrBlank() && !IGNORED_TWITTER_HANDLES.contains(handle.lowercase())) {
                            twHandles.add(handle)
                        }
                    }

                    val ytM = youtubePattern.matcher(link)
                    if (ytM.find()) {
                        val ch = ytM.group(1)
                        if (!ch.isNullOrBlank() && !IGNORED_YOUTUBE_HANDLES.contains(ch.lowercase())) {
                            ytHandles.add(ch)
                        }
                    }

                    if (link.startsWith("http://") || link.startsWith("https://")) {
                        val low = link.lowercase()
                        if (!low.contains("wikipedia") && !low.contains("wikimedia") && !low.contains("google") &&
                            !low.contains("archive.org") && !low.contains("twitter.com") && !low.contains("x.com") &&
                            !low.contains("youtube.com") && !low.contains("facebook.com") && !low.contains("instagram.com") &&
                            !low.contains("weibo.com") && !low.contains("bilibili.com") && !low.contains("natalie.mu") &&
                            !low.contains("oricon.co.jp") && !low.contains("comicspace") && !low.contains("ddnavi")
                        ) {
                            sites.add(link)
                        }
                    }
                }
            }

            return WikiResult(
                title = articleTitle,
                extract = extract,
                romajiOrEnglishName = defaultSort,
                imageUrl = null,
                twitterHandles = twHandles.distinct(),
                youtubeHandles = ytHandles.distinct(),
                officialSites = sites.distinct()
            )
        }
    }

    private fun fetchDuckDuckGoSuggestions(query: String, client: OkHttpClient): List<String> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://duckduckgo.com/ac/?q=$encoded&type=list"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val array = JSONArray(body)
            if (array.length() >= 2) {
                val suggestions = array.getJSONArray(1)
                val list = mutableListOf<String>()
                for (i in 0 until suggestions.length()) {
                    val s = suggestions.optString(i)
                    if (s.isNotBlank()) list.add(s)
                }
                return list
            }
        }
        return emptyList()
    }


}
