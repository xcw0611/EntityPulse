package com.entitypulse.app.data.model

data class PredefinedChannel(
    val platformType: PlatformType,
    val identifier: String,
    val title: String,
    val feedPathOrUrl: String,
    val defaultSelected: Boolean = true
)

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
                    platformType = PlatformType.REDDIT,
                    identifier = "r/HollowKnight",
                    title = "Reddit (r/HollowKnight)",
                    feedPathOrUrl = "https://www.reddit.com/r/HollowKnight/.rss"
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
     * 2. 若未匹配到，则根据输入名称自动推断主流平台的默认 Handle / 路由，供用户一键勾选。
     */
    fun findOrGenerateCandidates(query: String): List<PredefinedChannel> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        // 1. 精确或模糊匹配内置库
        val match = POPULAR_ENTITIES.firstOrNull {
            it.name.contains(trimmed, ignoreCase = true) || trimmed.contains(it.name, ignoreCase = true)
        }
        if (match != null) {
            return match.channels
        }

        // 2. 通用推测生成器 (将名称转为无空格的标准 handle)
        val cleanHandle = trimmed.replace("\\s+".toRegex(), "").filter { it.isLetterOrDigit() || it == '_' }
        if (cleanHandle.isBlank()) return emptyList()

        return listOf(
            PredefinedChannel(
                platformType = PlatformType.TWITTER,
                identifier = "@$cleanHandle",
                title = "X / Twitter (@$cleanHandle)",
                feedPathOrUrl = "/twitter/user/$cleanHandle",
                defaultSelected = true
            ),
            PredefinedChannel(
                platformType = PlatformType.YOUTUBE,
                identifier = "@$cleanHandle",
                title = "YouTube (@$cleanHandle)",
                feedPathOrUrl = "/youtube/user/@$cleanHandle",
                defaultSelected = true
            ),
            PredefinedChannel(
                platformType = PlatformType.REDDIT,
                identifier = "r/$cleanHandle",
                title = "Reddit (r/$cleanHandle)",
                feedPathOrUrl = "https://www.reddit.com/r/$cleanHandle/.rss",
                defaultSelected = false
            ),
            PredefinedChannel(
                platformType = PlatformType.BLUESKY,
                identifier = "$cleanHandle.bsky.social",
                title = "Bluesky ($cleanHandle.bsky.social)",
                feedPathOrUrl = "/bluesky/profile/$cleanHandle.bsky.social",
                defaultSelected = false
            ),
            PredefinedChannel(
                platformType = PlatformType.GITHUB,
                identifier = cleanHandle,
                title = "GitHub ($cleanHandle)",
                feedPathOrUrl = "https://github.com/$cleanHandle.atom",
                defaultSelected = false
            )
        )
    }
}
