package com.entitypulse.app.network

import com.entitypulse.app.data.model.EntityCatalog
import com.entitypulse.app.data.model.PlatformCandidateGroup
import com.entitypulse.app.data.model.PlatformType
import com.entitypulse.app.data.model.PredefinedChannel
import com.entitypulse.app.util.AppLogger
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

    private val twitterPattern = Pattern.compile("https?://(?:twitter|x)\\.com/([a-zA-Z0-9_]{3,30})", Pattern.CASE_INSENSITIVE)
    private val youtubePattern = Pattern.compile("https?://(?:www\\.)?youtube\\.com/(?:@|user/|c/)?([a-zA-Z0-9_.-]{3,35})", Pattern.CASE_INSENSITIVE)
    private val bilibiliSpacePattern = Pattern.compile("space\\.bilibili\\.com/(\\d+)", Pattern.CASE_INSENSITIVE)

    private val IGNORED_TWITTER_HANDLES = setOf(
        "intent", "share", "home", "hashtag", "login", "search", "i", "settings",
        "privacy", "tos", "explore", "about", "help", "download", "signup", "jobs", "status"
    )

    private val IGNORED_YOUTUBE_HANDLES = setOf(
        "watch", "embed", "results", "channel", "playlist", "feed", "live", "about"
    )

    data class WebSocialLinks(
        val twitterHandles: List<String> = emptyList(),
        val youtubeChannels: List<String> = emptyList(),
        val bilibiliUids: List<Long> = emptyList(),
        val relatedSites: List<String> = emptyList()
    )

    private data class WikiResult(
        val title: String,
        val extract: String,
        val romajiOrEnglishName: String,
        val wikibaseItem: String? = null,
        val imageUrl: String? = null,
        val twitterHandles: List<String> = emptyList(),
        val youtubeHandles: List<String> = emptyList(),
        val bilibiliUids: List<Long> = emptyList(),
        val officialSites: List<String> = emptyList()
    )

    /**
     * 针对用户输入的内容：
     * 1. 优先定位其所属的 动漫IP、游戏IP、工作室或创作者实体（通过 Bangumi、Steam、萌娘百科、Wikipedia 等知识网络）
     * 2. 针对该定位到的 IP，利用全网已知权威资源（维基百科、萌娘百科、Wikidata 关系图谱、官方/制作方站点社交直链）
     *    挖掘其真实且经权威认证的社媒账号，彻底杜绝凭空臆测同名账号与死板机械的字符串拼凑。
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
        var detectedWikibaseItem: String? = null

        val discoveredTwitterHandles = mutableListOf<Pair<String, String>>() // Handle to Note
        val discoveredYouTubeChannels = mutableListOf<Pair<String, String>>() // Channel/Handle to Note
        val discoveredBilibiliUids = mutableListOf<Pair<Long, String>>() // UID to Note
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

        // 4. 尝试萌娘百科 (Moegirlpedia) 检索（国内超全 ACG/动画/创作者外链与官方网站）
        try {
            val moegirlResult = queryMoegirlpedia(detectedName, client) ?: queryMoegirlpedia(trimmed, client)
            if (moegirlResult != null) {
                if (detectedBio.isNullOrBlank() && moegirlResult.extract.isNotBlank()) {
                    detectedBio = moegirlResult.extract
                }
                moegirlResult.twitterHandles.forEach { discoveredTwitterHandles.add(it to "萌百官方收录") }
                moegirlResult.youtubeHandles.forEach { discoveredYouTubeChannels.add(it to "萌百官方收录") }
                moegirlResult.bilibiliUids.forEach { discoveredBilibiliUids.add(it to "萌百官方收录") }
                discoveredOfficialLinks.addAll(moegirlResult.officialSites)
            }
        } catch (_: Exception) {}

        // 5. 尝试 Wikipedia 维基百科知识图谱与官方链接检索 (挖掘最准确的官方 X/Twitter 与官网)
        try {
            val wikiResult = queryWikipedia(detectedName, client) ?: queryWikipedia(trimmed, client)
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
                detectedWikibaseItem = wikiResult.wikibaseItem

                wikiResult.twitterHandles.forEach { discoveredTwitterHandles.add(it to "维基官方直链") }
                wikiResult.youtubeHandles.forEach { discoveredYouTubeChannels.add(it to "维基官方直链") }
                wikiResult.bilibiliUids.forEach { discoveredBilibiliUids.add(it to "维基官方直链") }
                discoveredOfficialLinks.addAll(wikiResult.officialSites)
            }
        } catch (_: Exception) {}

        // 6. 若存在英文原名，检索英文维基百科 (获取更全面的海外官方社交媒体条目)
        val enName = detectedOriginalName?.takeIf { it.isNotBlank() }
        if (!enName.isNullOrBlank()) {
            try {
                val enWikiResult = queryEnglishWikipedia(enName, client)
                if (enWikiResult != null) {
                    enWikiResult.twitterHandles.forEach { discoveredTwitterHandles.add(it to "英文维基收录") }
                    enWikiResult.youtubeHandles.forEach { discoveredYouTubeChannels.add(it to "英文维基收录") }
                    enWikiResult.bilibiliUids.forEach { discoveredBilibiliUids.add(it to "英文维基收录") }
                    discoveredOfficialLinks.addAll(enWikiResult.officialSites)
                }
            } catch (_: Exception) {}
        }

        // 7. 若定位到 Wikidata 实体（QID），提取结构化属性（包括其制作公司与创作者的主页与频道）
        val qid = detectedWikibaseItem
        if (!qid.isNullOrBlank()) {
            try {
                val wdLinks = queryWikidata(qid, client)
                wdLinks.twitterHandles.forEach { discoveredTwitterHandles.add(it to "知识图谱认证") }
                wdLinks.youtubeChannels.forEach { discoveredYouTubeChannels.add(it to "知识图谱认证") }
                wdLinks.bilibiliUids.forEach { discoveredBilibiliUids.add(it to "知识图谱认证") }
                discoveredOfficialLinks.addAll(wdLinks.relatedSites)
            } catch (_: Exception) {}
        }

        // 8. 深度遍历官网与制作方站点，提取官网页脚与导航中的官方社交账号直链
        val siteUrl = discoveredOfficialLinks.firstOrNull() ?: detectedOfficialSite
        if (discoveredOfficialLinks.isNotEmpty()) {
            for (site in discoveredOfficialLinks.distinct().take(4)) {
                try {
                    val siteSocial = scrapeSocialLinksFromWebsite(site, client)
                    siteSocial.twitterHandles.forEach { discoveredTwitterHandles.add(it to "官网认证直链") }
                    siteSocial.youtubeChannels.forEach { discoveredYouTubeChannels.add(it to "制作方官网频道") }
                    siteSocial.bilibiliUids.forEach { discoveredBilibiliUids.add(it to "官网认证B站") }
                } catch (_: Exception) {}
            }
        }

        // 9. 针对 Bilibili 平台进行精准官方认证过滤
        val discoveredBilibiliCreators = mutableListOf<BilibiliResolver.BilibiliCreator>()
        // (a) 首先注入权威资料中提取到的 B 站 UID
        for ((biliUid, note) in discoveredBilibiliUids.distinctBy { it.first }) {
            try {
                val creator = BilibiliResolver.getCreatorByUid(biliUid, client)
                if (creator != null) {
                    discoveredBilibiliCreators.add(creator)
                }
            } catch (_: Exception) {}
        }
        // (b) 若未在权威资料中收录，检索 B 站搜索接口，严格仅采纳官方认证或高契合度 UP 主
        if (discoveredBilibiliCreators.isEmpty()) {
            try {
                val creators = BilibiliResolver.searchCreators(detectedName, client)
                val matched = creators.filter { c ->
                    c.isOfficial ||
                            c.uname.contains("官方") ||
                            c.uname.contains("动画") ||
                            c.uname.contains("手游") ||
                            (c.fans >= 30000 && c.uname.equals(detectedName, ignoreCase = true))
                }
                discoveredBilibiliCreators.addAll(matched)
            } catch (_: Exception) {}
        }

        // 10. 社交媒体账号存在性与真实性匹配校验 (验证从权威资料获取的账号)
        val candidateGroups = mutableListOf<PlatformCandidateGroup>()

        // (1) X / Twitter 分组
        val verifiedTwitter = mutableListOf<Pair<String, String>>()
        for ((handle, note) in discoveredTwitterHandles.distinctBy { it.first.lowercase() }) {
            if (TwitterSyndicationParser.checkUserExists(handle, client)) {
                verifiedTwitter.add(handle to note)
            } else {
                AppLogger.i("OnlineEntityLocator", "过滤不存在的推特账号: @$handle (HTTP 404)")
            }
        }
        val twCandidates = mutableListOf<PredefinedChannel>()
        if (verifiedTwitter.isNotEmpty()) {
            verifiedTwitter.forEachIndexed { idx, (handle, note) ->
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
            candidateGroups.add(PlatformCandidateGroup(PlatformType.TWITTER, twCandidates, 0, isEnabled = true))
        } else {
            // 未在权威网络资料中收录官方账号：明确提示用户，且默认禁用该渠道，彻底避免臆测假账号导致同步报错
            twCandidates.add(
                PredefinedChannel(
                    platformType = PlatformType.TWITTER,
                    identifier = "未收录",
                    title = "X / Twitter (网络资料未收录独立官方账号)",
                    feedPathOrUrl = "",
                    defaultSelected = false,
                    probabilityNote = "未发现独立官方推特 (可能未开设或由发行方代发)"
                )
            )
            candidateGroups.add(PlatformCandidateGroup(PlatformType.TWITTER, twCandidates, 0, isEnabled = false))
        }

        // (2) YouTube 分组
        val verifiedYouTube = mutableListOf<Pair<String, String>>()
        for ((channelName, note) in discoveredYouTubeChannels.distinctBy { it.first.lowercase() }) {
            val clean = channelName.removePrefix("@")
            if (YouTubeResolver.checkHandleExists(clean, client)) {
                verifiedYouTube.add(clean to note)
            } else {
                AppLogger.i("OnlineEntityLocator", "过滤不存在的 YouTube 频道: @$clean (HTTP 404)")
            }
        }
        val ytCandidates = mutableListOf<PredefinedChannel>()
        if (verifiedYouTube.isNotEmpty()) {
            verifiedYouTube.forEachIndexed { idx, (clean, note) ->
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
            candidateGroups.add(PlatformCandidateGroup(PlatformType.YOUTUBE, ytCandidates, 0, isEnabled = true))
        } else {
            // 未在权威网络资料中收录官方频道：明确提示用户，且默认禁用该渠道
            ytCandidates.add(
                PredefinedChannel(
                    platformType = PlatformType.YOUTUBE,
                    identifier = "未收录",
                    title = "YouTube (网络资料未收录独立官方频道)",
                    feedPathOrUrl = "",
                    defaultSelected = false,
                    probabilityNote = "未发现独立官方频道 (视频可能在制作方或主创频道发布)"
                )
            )
            candidateGroups.add(PlatformCandidateGroup(PlatformType.YOUTUBE, ytCandidates, 0, isEnabled = false))
        }

        // (3) Bilibili 分组
        val biliCandidates = mutableListOf<PredefinedChannel>()
        if (discoveredBilibiliCreators.isNotEmpty()) {
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
            candidateGroups.add(PlatformCandidateGroup(PlatformType.BILIBILI, biliCandidates, 0, isEnabled = true))
        } else {
            // 未在权威网络资料中收录官方账号：明确提示用户，且默认禁用该渠道
            biliCandidates.add(
                PredefinedChannel(
                    platformType = PlatformType.BILIBILI,
                    identifier = "未收录",
                    title = "Bilibili (网络资料未收录B站官方号)",
                    feedPathOrUrl = "",
                    defaultSelected = false,
                    probabilityNote = "该作品暂未在B站设立官方认证账号"
                )
            )
            candidateGroups.add(PlatformCandidateGroup(PlatformType.BILIBILI, biliCandidates, 0, isEnabled = false))
        }

        // (4) Steam 游戏官方公告（若定位到 Steam App）
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

        // (5) 官方网站 / 开发博客
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

        // (6) Reddit 社区讨论版块（仅作为社群补充，默认不作为主力消息源勾选）
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
                            feedPathOrUrl = "/reddit/subreddit/$redditName",
                            defaultSelected = false,
                            probabilityNote = "粉丝社群专版 (非官方)"
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
        val searchUrl = "https://api.bgm.tv/search/subject/$encoded?type=2&responseGroup=small"
        val searchReq = Request.Builder()
            .url(searchUrl)
            .header("User-Agent", "EntityPulse/1.0 (Android)")
            .build()

        var subjectId = 0
        var origName = ""
        var cnName = ""
        var cover = ""

        client.newCall(searchReq).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val root = JSONObject(body)
            val list = root.optJSONArray("list") ?: return null
            if (list.length() == 0) return null
            val first = list.optJSONObject(0) ?: return null
            subjectId = first.optInt("id", 0)
            origName = first.optString("name", "")
            cnName = first.optString("name_cn", "")
            cover = first.optJSONObject("images")?.optString("large", "") ?: ""
        }

        if (subjectId == 0) return null

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

    private fun queryMoegirlpedia(query: String, client: OkHttpClient): WikiResult? {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val openSearchUrl = "https://zh.moegirl.org.cn/api.php?action=opensearch&search=$encoded&limit=2&format=json"
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
            val queryUrl = "https://zh.moegirl.org.cn/api.php?action=query&titles=$encodedTitle&prop=extlinks|extracts|pageprops&ellimit=50&format=json&explaintext=1&exintro=1"
            val qReq = Request.Builder().url(queryUrl).header("User-Agent", "Mozilla/5.0").build()

            client.newCall(qReq).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val root = JSONObject(body)
                val pages = root.optJSONObject("query")?.optJSONObject("pages") ?: return null
                val keys = pages.keys()
                if (!keys.hasNext()) return null
                val pageKey = keys.next()
                if (pageKey == "-1") return null
                val page = pages.optJSONObject(pageKey) ?: return null

                val extract = page.optString("extract", "")
                val extlinks = page.optJSONArray("extlinks")
                val twHandles = mutableListOf<String>()
                val ytHandles = mutableListOf<String>()
                val biliUids = mutableListOf<Long>()
                val sites = mutableListOf<String>()

                if (extlinks != null) {
                    for (i in 0 until extlinks.length()) {
                        val link = extlinks.optJSONObject(i)?.optString("*", "") ?: continue
                        parseExternalLink(link, twHandles, ytHandles, biliUids, sites)
                    }
                }

                WikiResult(
                    title = articleTitle,
                    extract = extract,
                    romajiOrEnglishName = "",
                    wikibaseItem = null,
                    imageUrl = null,
                    twitterHandles = twHandles.distinct(),
                    youtubeHandles = ytHandles.distinct(),
                    bilibiliUids = biliUids.distinct(),
                    officialSites = sites.distinct()
                )
            }
        } catch (_: Exception) {
            null
        }
    }

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
            if (pageKey == "-1") return null
            val page = pages.optJSONObject(pageKey) ?: return null

            val extract = page.optString("extract", "")
            val defaultSort = page.optJSONObject("pageprops")?.optString("defaultsort", "") ?: ""
            val wikibaseItem = page.optJSONObject("pageprops")?.optString("wikibase_item", null)
            val extlinks = page.optJSONArray("extlinks")

            val twHandles = mutableListOf<String>()
            val ytHandles = mutableListOf<String>()
            val biliUids = mutableListOf<Long>()
            val sites = mutableListOf<String>()

            if (extlinks != null) {
                for (i in 0 until extlinks.length()) {
                    val link = extlinks.optJSONObject(i)?.optString("*", "") ?: continue
                    parseExternalLink(link, twHandles, ytHandles, biliUids, sites)
                }
            }

            return WikiResult(
                title = articleTitle,
                extract = extract,
                romajiOrEnglishName = defaultSort,
                wikibaseItem = wikibaseItem,
                imageUrl = null,
                twitterHandles = twHandles.distinct(),
                youtubeHandles = ytHandles.distinct(),
                bilibiliUids = biliUids.distinct(),
                officialSites = sites.distinct()
            )
        }
    }

    private fun queryEnglishWikipedia(enTitle: String, client: OkHttpClient): WikiResult? {
        try {
            val encodedTitle = URLEncoder.encode(enTitle, "UTF-8")
            val queryUrl = "https://en.wikipedia.org/w/api.php?action=query&titles=$encodedTitle&prop=extlinks&ellimit=50&format=json"
            val req = Request.Builder().url(queryUrl).header("User-Agent", "Mozilla/5.0").build()

            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val root = JSONObject(body)
                val pages = root.optJSONObject("query")?.optJSONObject("pages") ?: return null
                val keys = pages.keys()
                if (!keys.hasNext()) return null
                val pageKey = keys.next()
                if (pageKey == "-1") return null
                val page = pages.optJSONObject(pageKey) ?: return null

                val extlinks = page.optJSONArray("extlinks")
                val twHandles = mutableListOf<String>()
                val ytHandles = mutableListOf<String>()
                val biliUids = mutableListOf<Long>()
                val sites = mutableListOf<String>()

                if (extlinks != null) {
                    for (i in 0 until extlinks.length()) {
                        val link = extlinks.optJSONObject(i)?.optString("*", "") ?: continue
                        parseExternalLink(link, twHandles, ytHandles, biliUids, sites)
                    }
                }

                return WikiResult(
                    title = enTitle,
                    extract = "",
                    romajiOrEnglishName = enTitle,
                    wikibaseItem = null,
                    imageUrl = null,
                    twitterHandles = twHandles.distinct(),
                    youtubeHandles = ytHandles.distinct(),
                    bilibiliUids = biliUids.distinct(),
                    officialSites = sites.distinct()
                )
            }
        } catch (_: Exception) {
            return null
        }
    }

    private fun queryWikidata(qid: String, client: OkHttpClient): WebSocialLinks {
        val twHandles = mutableListOf<String>()
        val ytChannels = mutableListOf<String>()
        val biliUids = mutableListOf<Long>()
        val sites = mutableListOf<String>()
        val relatedQids = mutableListOf<String>()

        try {
            val url = "https://www.wikidata.org/wiki/Special:EntityData/$qid.json"
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()

            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return WebSocialLinks()
                val body = response.body?.string() ?: return WebSocialLinks()
                val root = JSONObject(body)
                val entities = root.optJSONObject("entities") ?: return WebSocialLinks()
                val entity = entities.optJSONObject(qid) ?: return WebSocialLinks()
                val claims = entity.optJSONObject("claims") ?: return WebSocialLinks()

                claims.optJSONArray("P2002")?.let { pArr ->
                    for (i in 0 until pArr.length()) {
                        val v = pArr.optJSONObject(i)?.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.optString("value")
                        if (!v.isNullOrBlank()) twHandles.add(v)
                    }
                }

                claims.optJSONArray("P2397")?.let { pArr ->
                    for (i in 0 until pArr.length()) {
                        val v = pArr.optJSONObject(i)?.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.optString("value")
                        if (!v.isNullOrBlank()) ytChannels.add(v)
                    }
                }

                claims.optJSONArray("P6079")?.let { pArr ->
                    for (i in 0 until pArr.length()) {
                        val v = pArr.optJSONObject(i)?.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.optLong("value", 0L) ?: 0L
                        if (v > 0L) biliUids.add(v)
                    }
                }

                claims.optJSONArray("P856")?.let { pArr ->
                    for (i in 0 until pArr.length()) {
                        val v = pArr.optJSONObject(i)?.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.optString("value")
                        if (!v.isNullOrBlank()) sites.add(v)
                    }
                }

                for (prop in listOf("P170", "P272")) {
                    claims.optJSONArray(prop)?.let { pArr ->
                        for (i in 0 until minOf(pArr.length(), 2)) {
                            val targetId = pArr.optJSONObject(i)?.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.optJSONObject("value")?.optString("id")
                            if (!targetId.isNullOrBlank() && !relatedQids.contains(targetId)) {
                                relatedQids.add(targetId)
                            }
                        }
                    }
                }
            }

            for (relId in relatedQids) {
                if (twHandles.isNotEmpty() && ytChannels.isNotEmpty()) break
                val relUrl = "https://www.wikidata.org/wiki/Special:EntityData/$relId.json"
                val rReq = Request.Builder().url(relUrl).header("User-Agent", "Mozilla/5.0").build()
                client.newCall(rReq).execute().use { rResp ->
                    if (rResp.isSuccessful) {
                        val rBody = rResp.body?.string() ?: return@use
                        val rClaims = JSONObject(rBody).optJSONObject("entities")?.optJSONObject(relId)?.optJSONObject("claims") ?: return@use

                        rClaims.optJSONArray("P2002")?.let { arr ->
                            for (i in 0 until arr.length()) {
                                val v = arr.optJSONObject(i)?.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.optString("value")
                                if (!v.isNullOrBlank()) twHandles.add(v)
                            }
                        }
                        rClaims.optJSONArray("P2397")?.let { arr ->
                            for (i in 0 until arr.length()) {
                                val v = arr.optJSONObject(i)?.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.optString("value")
                                if (!v.isNullOrBlank()) ytChannels.add(v)
                            }
                        }
                        rClaims.optJSONArray("P856")?.let { arr ->
                            for (i in 0 until arr.length()) {
                                val v = arr.optJSONObject(i)?.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.optString("value")
                                if (!v.isNullOrBlank()) sites.add(v)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return WebSocialLinks(
            twitterHandles = twHandles.distinct(),
            youtubeChannels = ytChannels.distinct(),
            bilibiliUids = biliUids.distinct(),
            relatedSites = sites.distinct()
        )
    }

    private fun scrapeSocialLinksFromWebsite(url: String, client: OkHttpClient): WebSocialLinks {
        val twHandles = mutableListOf<String>()
        val ytChannels = mutableListOf<String>()
        val biliUids = mutableListOf<Long>()
        val relatedSites = mutableListOf<String>()

        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return WebSocialLinks()
                val html = response.body?.string() ?: return WebSocialLinks()

                val twM = twitterPattern.matcher(html)
                while (twM.find()) {
                    val h = twM.group(1)
                    if (!h.isNullOrBlank() && !IGNORED_TWITTER_HANDLES.contains(h.lowercase())) {
                        twHandles.add(h)
                    }
                }

                val ytM = youtubePattern.matcher(html)
                while (ytM.find()) {
                    val raw = ytM.group(1)
                    if (!raw.isNullOrBlank()) {
                        val clean = raw.removePrefix("@").removePrefix("c/").removePrefix("user/")
                        if (!IGNORED_YOUTUBE_HANDLES.contains(clean.lowercase())) {
                            ytChannels.add(clean)
                        }
                    }
                }

                val biliM = bilibiliSpacePattern.matcher(html)
                while (biliM.find()) {
                    val uid = biliM.group(1)?.toLongOrNull()
                    if (uid != null && uid > 0L) {
                        biliUids.add(uid)
                    }
                }
            }
        } catch (_: Exception) {}

        return WebSocialLinks(
            twitterHandles = twHandles.distinct(),
            youtubeChannels = ytChannels.distinct(),
            bilibiliUids = biliUids.distinct(),
            relatedSites = relatedSites.distinct()
        )
    }

    private fun parseExternalLink(
        link: String,
        twHandles: MutableList<String>,
        ytHandles: MutableList<String>,
        biliUids: MutableList<Long>,
        sites: MutableList<String>
    ) {
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
            if (!ch.isNullOrBlank()) {
                val clean = ch.removePrefix("@").removePrefix("c/").removePrefix("user/")
                if (!IGNORED_YOUTUBE_HANDLES.contains(clean.lowercase())) {
                    ytHandles.add(clean)
                }
            }
        }

        val biliM = bilibiliSpacePattern.matcher(link)
        if (biliM.find()) {
            val uid = biliM.group(1)?.toLongOrNull()
            if (uid != null && uid > 0L) {
                biliUids.add(uid)
            }
        }

        if (link.startsWith("http://") || link.startsWith("https://")) {
            val low = link.lowercase()
            if (!low.contains("wikipedia") && !low.contains("wikimedia") && !low.contains("google") &&
                !low.contains("archive.org") && !low.contains("twitter.com") && !low.contains("x.com") &&
                !low.contains("youtube.com") && !low.contains("facebook.com") && !low.contains("instagram.com") &&
                !low.contains("weibo.com") && !low.contains("bilibili.com") && !low.contains("imdb.com") &&
                !low.contains("fandom.com") && !low.contains("moegirl") && !low.contains("patreon.com")
            ) {
                sites.add(link)
            }
        }
    }
}
