package com.entitypulse.app.data.model

data class PredefinedChannel(
    val platformType: PlatformType,
    val identifier: String,
    val title: String,
    val feedPathOrUrl: String,
    val defaultSelected: Boolean = true,
    val probabilityNote: String = "推测"
)

data class PlatformCandidateGroup(
    val platformType: PlatformType,
    val candidates: List<PredefinedChannel>,
    var selectedIndex: Int = 0,
    var isEnabled: Boolean = true
) {
    val currentSelectedChannel: PredefinedChannel
        get() = candidates.getOrNull(selectedIndex) ?: candidates.first()
}

data class PredefinedEntity(
    val name: String,
    val category: String,
    val bio: String,
    val avatarUrl: String?,
    val isVip: Boolean = false,
    val channels: List<PredefinedChannel>
)

object EntityCatalog {

    val POPULAR_ENTITIES: List<PredefinedEntity> = listOf(
        PredefinedEntity(
            name = "Team Cherry",
            category = "游戏工作室",
            bio = "《空洞骑士》(Hollow Knight) 与《丝之歌》(Silksong) 独立游戏开发团队",
            avatarUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co20x5.png",
            isVip = true,
            channels = listOf(
                PredefinedChannel(
                    platformType = PlatformType.STEAM,
                    identifier = "App: 1030300",
                    title = "Steam · 丝之歌官方公告",
                    feedPathOrUrl = "https://steamcommunity.com/games/1030300/rss/"
                ),
                PredefinedChannel(
                    platformType = PlatformType.STEAM,
                    identifier = "App: 367520",
                    title = "Steam · 空洞骑士官方公告",
                    feedPathOrUrl = "https://steamcommunity.com/games/367520/rss/"
                ),
                PredefinedChannel(
                    platformType = PlatformType.TWITTER,
                    identifier = "@TeamCherryGames",
                    title = "X / Twitter (@TeamCherryGames)",
                    feedPathOrUrl = "/twitter/user/TeamCherryGames"
                ),
                PredefinedChannel(
                    platformType = PlatformType.YOUTUBE,
                    identifier = "@TeamCherry",
                    title = "YouTube (@TeamCherry)",
                    feedPathOrUrl = "/youtube/user/@TeamCherry"
                ),
                PredefinedChannel(
                    platformType = PlatformType.RSS_CUSTOM,
                    identifier = "teamcherry.com.au",
                    title = "官方开发博客 (teamcherry.com.au)",
                    feedPathOrUrl = "https://www.teamcherry.com.au/blog?format=rss"
                )
            )
        ),
        PredefinedEntity(
            name = "游戏科学 (Game Science)",
            category = "游戏工作室",
            bio = "《黑神话：悟空》(Black Myth: Wukong) 开发商",
            avatarUrl = "https://www.gamesci.com.cn/images/logo.png",
            isVip = true,
            channels = listOf(
                PredefinedChannel(
                    platformType = PlatformType.BILIBILI,
                    identifier = "UID: 642389251",
                    title = "Bilibili (黑神话悟空官方)",
                    feedPathOrUrl = "/bilibili/user/video/642389251"
                ),
                PredefinedChannel(
                    platformType = PlatformType.STEAM,
                    identifier = "App: 2358720",
                    title = "Steam · 黑神话悟空官方公告",
                    feedPathOrUrl = "https://steamcommunity.com/games/2358720/rss/"
                ),
                PredefinedChannel(
                    platformType = PlatformType.TWITTER,
                    identifier = "@BlackMythGame",
                    title = "X / Twitter (@BlackMythGame)",
                    feedPathOrUrl = "/twitter/user/BlackMythGame"
                ),
                PredefinedChannel(
                    platformType = PlatformType.YOUTUBE,
                    identifier = "@BlackMythGame",
                    title = "YouTube (@BlackMythGame)",
                    feedPathOrUrl = "/youtube/user/@BlackMythGame"
                )
            )
        ),
        PredefinedEntity(
            name = "Nintendo / 任天堂",
            category = "主机游戏厂商",
            bio = "知名游戏及主机娱乐公司（Switch, 塞尔达传说, 马力欧）",
            avatarUrl = "https://assets.nintendo.com/image/upload/v1642106193/ncom/global/icons/nintendo-logo.png",
            channels = listOf(
                PredefinedChannel(
                    platformType = PlatformType.TWITTER,
                    identifier = "@NintendoAmerica",
                    title = "X / Twitter (@NintendoAmerica)",
                    feedPathOrUrl = "/twitter/user/NintendoAmerica"
                ),
                PredefinedChannel(
                    platformType = PlatformType.YOUTUBE,
                    identifier = "@NintendoAmerica",
                    title = "YouTube (@NintendoAmerica)",
                    feedPathOrUrl = "/youtube/user/@NintendoAmerica"
                ),
                PredefinedChannel(
                    platformType = PlatformType.BILIBILI,
                    identifier = "UID: 434914183",
                    title = "Bilibili (腾讯NintendoSwitch)",
                    feedPathOrUrl = "/bilibili/user/video/434914183"
                )
            )
        ),
        PredefinedEntity(
            name = "PlayStation",
            category = "主机游戏厂商",
            bio = "索尼互动娱乐 PlayStation 官方最新动态",
            avatarUrl = "https://gmedia.playstation.com/is/image/SIEPDC/playstation-family-logo",
            channels = listOf(
                PredefinedChannel(
                    platformType = PlatformType.TWITTER,
                    identifier = "@PlayStation",
                    title = "X / Twitter (@PlayStation)",
                    feedPathOrUrl = "/twitter/user/PlayStation"
                ),
                PredefinedChannel(
                    platformType = PlatformType.YOUTUBE,
                    identifier = "@PlayStation",
                    title = "YouTube (@PlayStation)",
                    feedPathOrUrl = "/youtube/user/@PlayStation"
                ),
                PredefinedChannel(
                    platformType = PlatformType.BILIBILI,
                    identifier = "UID: 2090886105",
                    title = "Bilibili (PlayStation中国)",
                    feedPathOrUrl = "/bilibili/user/video/2090886105"
                )
            )
        ),
        PredefinedEntity(
            name = "米哈游 (miHoYo / HoYoverse)",
            category = "游戏厂商",
            bio = "《原神》、《崩坏：星穹铁道》、《绝区零》开发商",
            avatarUrl = "https://webstatic.mihoyo.com/upload/op-public/2021/10/09/b8004fdb88b9bf227ca0ba1b6ee2eb9d_6946654761048897534.png",
            channels = listOf(
                PredefinedChannel(
                    platformType = PlatformType.BILIBILI,
                    identifier = "UID: 401742377",
                    title = "Bilibili (原神官方)",
                    feedPathOrUrl = "/bilibili/user/video/401742377"
                ),
                PredefinedChannel(
                    platformType = PlatformType.BILIBILI,
                    identifier = "UID: 1340190821",
                    title = "Bilibili (崩坏星穹铁道)",
                    feedPathOrUrl = "/bilibili/user/video/1340190821"
                ),
                PredefinedChannel(
                    platformType = PlatformType.TWITTER,
                    identifier = "@GenshinImpact",
                    title = "X / Twitter (@GenshinImpact)",
                    feedPathOrUrl = "/twitter/user/GenshinImpact"
                ),
                PredefinedChannel(
                    platformType = PlatformType.YOUTUBE,
                    identifier = "@GenshinImpact",
                    title = "YouTube (@GenshinImpact)",
                    feedPathOrUrl = "/youtube/user/@GenshinImpact"
                )
            )
        ),
        PredefinedEntity(
            name = "CD PROJEKT RED",
            category = "游戏工作室",
            bio = "《巫师》系列与《赛博朋克 2077》开发商",
            avatarUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co2l7z.png",
            channels = listOf(
                PredefinedChannel(
                    platformType = PlatformType.TWITTER,
                    identifier = "@CDPROJEKTRED",
                    title = "X / Twitter (@CDPROJEKTRED)",
                    feedPathOrUrl = "/twitter/user/CDPROJEKTRED"
                ),
                PredefinedChannel(
                    platformType = PlatformType.STEAM,
                    identifier = "App: 1091500",
                    title = "Steam · 赛博朋克2077公告",
                    feedPathOrUrl = "https://steamcommunity.com/games/1091500/rss/"
                ),
                PredefinedChannel(
                    platformType = PlatformType.BILIBILI,
                    identifier = "UID: 20857700",
                    title = "Bilibili (CDPR官方)",
                    feedPathOrUrl = "/bilibili/user/video/20857700"
                )
            )
        )
    )

    /**
     * 智能猜测与匹配：
     * 1. 优先在内置知名主体库中模糊匹配；
     * 2. 若未匹配到，则针对各平台生成多个易混淆/高概率备选 Handle，并结合全网在线搜索结果；
     * 3. 按平台分组呈现，供用户在弹窗内灵活挑选、切换或微调。
     */
    fun generateGroupedCandidates(
        query: String,
        onlineAdditions: List<PredefinedChannel> = emptyList()
    ): List<PlatformCandidateGroup> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        val clean = trimmed.replace("\\s+".toRegex(), "").filter { it.isLetterOrDigit() || it == '_' }

        // 1. 若匹配到内置知名库
        val match = POPULAR_ENTITIES.firstOrNull {
            it.name.contains(trimmed, ignoreCase = true) || trimmed.contains(it.name, ignoreCase = true)
        }

        if (match != null) {
            val groups = mutableListOf<PlatformCandidateGroup>()
            val channelsByPlatform = match.channels.groupBy { it.platformType }

            for ((platform, channels) in channelsByPlatform) {
                val candidateList = channels.map { it.copy(probabilityNote = "官方认证") }.toMutableList()

                // 补充常见备选变体
                if (platform == PlatformType.TWITTER && clean.isNotBlank()) {
                    val rawHandle = "@$clean"
                    if (candidateList.none { it.identifier.equals(rawHandle, ignoreCase = true) }) {
                        candidateList.add(
                            PredefinedChannel(
                                platformType = PlatformType.TWITTER,
                                identifier = rawHandle,
                                title = "X / Twitter ($rawHandle)",
                                feedPathOrUrl = "/twitter/user/$clean",
                                defaultSelected = false,
                                probabilityNote = "通用简称"
                            )
                        )
                    }
                }

                // 注入全网在线探测到的候选项
                val onlineMatches = onlineAdditions.filter { it.platformType == platform }
                for (onl in onlineMatches) {
                    if (candidateList.none { it.identifier.equals(onl.identifier, ignoreCase = true) }) {
                        candidateList.add(onl)
                    }
                }

                groups.add(
                    PlatformCandidateGroup(
                        platformType = platform,
                        candidates = candidateList,
                        selectedIndex = 0,
                        isEnabled = true
                    )
                )
            }
            return groups
        }

        // 2. 自定义输入：若有在线检索结果则优先使用真实账号，否则生成基础标准账号
        if (clean.isBlank()) return emptyList()

        val groups = mutableListOf<PlatformCandidateGroup>()

        // (1) X / Twitter 候选组
        val twitterCandidates = mutableListOf<PredefinedChannel>()
        val twOnline = onlineAdditions.filter { it.platformType == PlatformType.TWITTER }
        if (twOnline.isNotEmpty()) {
            twitterCandidates.addAll(twOnline)
        } else {
            twitterCandidates.add(
                PredefinedChannel(PlatformType.TWITTER, "@$clean", "X / Twitter (@$clean)", "/twitter/user/$clean", true, "标准账号")
            )
        }
        groups.add(PlatformCandidateGroup(PlatformType.TWITTER, twitterCandidates, 0, true))

        // (2) Bilibili 候选组
        val biliCandidates = mutableListOf<PredefinedChannel>()
        val biliOnline = onlineAdditions.filter { it.platformType == PlatformType.BILIBILI }
        if (biliOnline.isNotEmpty()) {
            biliCandidates.addAll(biliOnline)
        } else {
            biliCandidates.add(
                PredefinedChannel(PlatformType.BILIBILI, trimmed, "Bilibili ($trimmed)", "/bilibili/user/video/$trimmed", true, "原名检索")
            )
        }
        groups.add(PlatformCandidateGroup(PlatformType.BILIBILI, biliCandidates, 0, true))

        // (3) YouTube 候选组
        val ytCandidates = mutableListOf<PredefinedChannel>()
        val ytOnline = onlineAdditions.filter { it.platformType == PlatformType.YOUTUBE }
        if (ytOnline.isNotEmpty()) {
            ytCandidates.addAll(ytOnline)
        } else {
            ytCandidates.add(
                PredefinedChannel(PlatformType.YOUTUBE, "@$clean", "YouTube (@$clean)", "/youtube/user/@$clean", false, "标准频道")
            )
        }
        groups.add(PlatformCandidateGroup(PlatformType.YOUTUBE, ytCandidates, 0, true))

        // (4) Steam 候选组 (仅当有检测到 Steam 项目时默认呈现)
        val steamOnline = onlineAdditions.filter { it.platformType == PlatformType.STEAM }
        if (steamOnline.isNotEmpty()) {
            groups.add(PlatformCandidateGroup(PlatformType.STEAM, steamOnline, 0, true))
        }

        // (5) 官网直链 (若在线检索探测到了官网)
        val siteOnline = onlineAdditions.filter { it.platformType == PlatformType.RSS_CUSTOM }
        if (siteOnline.isNotEmpty()) {
            groups.add(PlatformCandidateGroup(PlatformType.RSS_CUSTOM, siteOnline, 0, true))
        }

        // (6) Reddit 候选组
        val redditOnline = onlineAdditions.filter { it.platformType == PlatformType.REDDIT }
        if (redditOnline.isNotEmpty()) {
            groups.add(PlatformCandidateGroup(PlatformType.REDDIT, redditOnline, 0, false))
        }

        return groups
    }

    fun findOrGenerateCandidates(query: String): List<PredefinedChannel> {
        return generateGroupedCandidates(query).filter { it.isEnabled }.map { it.currentSelectedChannel }
    }
}
