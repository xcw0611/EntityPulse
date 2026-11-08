package com.entitypulse.app.data.model

data class PredefinedChannel(
    val platformType: PlatformType,
    val identifier: String,
    val title: String,
    val feedPathOrUrl: String,
    val defaultSelected: Boolean = true,
    val probabilityNote: String = "用户锁定"
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
                )
            )
        ),
        PredefinedEntity(
            name = "游戏科学 (Game Science)",
            category = "游戏开发商",
            bio = "《黑神话：悟空》(Black Myth: Wukong) 开发团队",
            avatarUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co5z8f.png",
            isVip = true,
            channels = listOf(
                PredefinedChannel(
                    platformType = PlatformType.BILIBILI,
                    identifier = "UID: 64228925",
                    title = "Bilibili (黑神话之悟空官方)",
                    feedPathOrUrl = "/bilibili/user/video/64228925"
                ),
                PredefinedChannel(
                    platformType = PlatformType.STEAM,
                    identifier = "App: 2358720",
                    title = "Steam · 黑神话社区公告",
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
            name = "地狱客栈 (Hazbin Hotel)",
            category = "欧美动画 / IP",
            bio = "Vivienne Medrano (SpindleHorse) 创作的独立成人歌舞动画系列",
            avatarUrl = "https://m.media-amazon.com/images/M/MV5BN2E1ZWM3NWUtOTgyMy00MjBkLTgzZDAtMGVhNGQ4NWVlNmQyXkEyXkFqcGc@._V1_.jpg",
            isVip = true,
            channels = listOf(
                PredefinedChannel(
                    platformType = PlatformType.YOUTUBE,
                    identifier = "@SpindleHorse",
                    title = "YouTube (Vivienne Medrano / SpindleHorse 主创频道)",
                    feedPathOrUrl = "/youtube/user/@SpindleHorse"
                ),
                PredefinedChannel(
                    platformType = PlatformType.TWITTER,
                    identifier = "@HazbinHotel",
                    title = "X / Twitter (@HazbinHotel 官方主页)",
                    feedPathOrUrl = "/twitter/user/HazbinHotel"
                ),
                PredefinedChannel(
                    platformType = PlatformType.TWITTER,
                    identifier = "@HellaversePrime",
                    title = "X / Twitter (@HellaversePrime 剧集官推)",
                    feedPathOrUrl = "/twitter/user/HellaversePrime"
                )
            )
        ),
        PredefinedEntity(
            name = "孤独摇滚！(Bocchi the Rock!)",
            category = "日本动漫",
            bio = "芳文社漫画改编，CloverWorks 制作的热门青春音乐番剧",
            avatarUrl = "https://lain.bgm.tv/pic/cover/l/1b/c2/367500_Q6E67.jpg",
            isVip = false,
            channels = listOf(
                PredefinedChannel(
                    platformType = PlatformType.TWITTER,
                    identifier = "@BTR_anime",
                    title = "X / Twitter (アニメ『ぼっち・ざ・ろっく！』公式)",
                    feedPathOrUrl = "/twitter/user/BTR_anime"
                ),
                PredefinedChannel(
                    platformType = PlatformType.BILIBILI,
                    identifier = "UID: 1989433299",
                    title = "Bilibili (孤独摇滚中国官方运营)",
                    feedPathOrUrl = "/bilibili/user/video/1989433299"
                )
            )
        ),
        PredefinedEntity(
            name = "CD PROJEKT RED",
            category = "游戏开发商",
            bio = "《巫师》系列与《赛博朋克 2077》波兰游戏开发巨头",
            avatarUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co1r7f.png",
            isVip = false,
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
}
