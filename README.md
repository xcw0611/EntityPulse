# EntityPulse (实体动态聚合中心)

EntityPulse 是一款针对单独的**“人”、“创作者”或“工作室（如 Team Cherry）”**为中心的跨平台社交媒体动态聚合与通知管理 Android 应用。

解决的核心痛点：用户想长期追踪特定主体的动态，但该主体分布在不同的社交媒体平台（X/Twitter、YouTube、Bilibili、Steam 公告、Reddit、官方博客等），在各个平台间频繁往返成本高且容易漏看。通过 EntityPulse，用户只需为主体绑定关联账号，App 即可在手机本地统一汇总时序动态，并通过系统通知即时触达。

---

## 🌟 核心特性

1. **以“主体”为绝对中心**：
   - 建立人物/工作室资料卡，将名下的 X/Twitter、YouTube、Bilibili、Steam、Reddit、Weibo、Bluesky 以及标准 RSS 订阅源统一挂载管理。
2. **混合双重视角**：
   - **主体看板 (Hub)**：人物墙视图，直观展示头像、关联平台徽标、未读角标与最新动态缩略语；
   - **综合动态流 (Feed)**：时序瀑布流，支持按人物 FilterChip 快速过滤、搜索、仅看未读与已读标记。
3. **智能平台识别 (PlatformResolver)**：
   - 支持直接粘贴任意社媒主页 URL（如 `https://x.com/TeamCherryGames` 或 `https://store.steampowered.com/app/1030300`），系统自动识别平台并转为对应 RSS/RSSHub 路由。
   - 内置连接测试能力，添加前可即时获取一条最新动态预览。
4. **分级富媒体通知系统 (NotificationHelper)**：
   - 支持按主体单独设置 VIP 强提醒；
   - 系统通知带主体头像、平台徽标、富文本摘要；
   - 提供快捷操作按钮：「在应用中查看」与「在官方 App / 浏览器中打开」；
   - 支持免打扰（Do Not Disturb）时段智能静音。
5. **纯端侧无服务器 & 抗封锁高可用**：
   - Steam、YouTube、博客走原生直连；社媒走多公共 RSSHub 镜像轮询；
   - 【设置】中支持填入自建私有 RSSHub 地址与 Access Token；
   - 纯净初始状态，支持全量配置 JSON 备份与标准 OPML 导出/导入。

---

## 🛠 技术架构

- **语言与规范**: Kotlin 2.0+ / Android 14+ (minSdk 26, targetSdk 35)
- **界面体系**: Jetpack Compose + Material 3 (自适应动态配色)
- **架构模式**: MVI / Clean Architecture (ViewModel + StateFlow + Repository)
- **持久化**: Jetpack Room Database (SQLite, 包含 Entity / Channel / FeedItem 级联关系)
- **后台任务**: Jetpack WorkManager (`SyncWorker` 定时后台轮询，电量友好调度)
- **通知机制**: Android NotificationManager (VIP 高优先级通道 + 普通通道 + BigTextStyle / BigPictureStyle)
- **网络与解析**: OkHttp 4 + Android XmlPullParser (RSS 2.0 / Atom 1.0)
- **图片加载**: Coil 3 (Compose 异步图片与缓存)

---

## 📁 目录结构

```text
EntityPulse/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/entitypulse/app/
│   │   │   │   ├── backup/             # JSON / OPML 备份与恢复
│   │   │   │   ├── data/               # Room 数据库、DAO、数据实体
│   │   │   │   │   ├── dao/
│   │   │   │   │   └── model/
│   │   │   │   ├── network/            # 智能平台识别器、RSS/Atom 解析器、节点管理
│   │   │   │   ├── notification/       # 分级富媒体通知分发器
│   │   │   │   ├── repository/         # 数据同步与聚合仓库
│   │   │   │   ├── sync/               # WorkManager 后台轮询 Worker
│   │   │   │   └── ui/                 # Jetpack Compose 界面层
│   │   │   │       ├── dialogs/        # 添加主体与添加渠道对话框
│   │   │   │       ├── navigation/     # 导航路由
│   │   │   │       ├── screens/        # 看板、信息流、主体主页、沉浸详情、设置
│   │   │   │       └── theme/          # Material 3 品牌配色与排版
│   │   │   ├── res/                    # 字符资源、主题与自适应图标
│   │   │   └── AndroidManifest.xml
│   │   └── test/                       # 单元测试 (PlatformResolverTest, FeedParserTest)
│   └── build.gradle.kts
├── gradle/
│   ├── libs.versions.toml              # 依赖版本编排 Version Catalog
│   └── wrapper/
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 🚀 导入与运行指南

1. **在 Android Studio 中打开**：
   - 打开 Android Studio (推荐 Hedgehog / Iguana / Koala 或更新版本)；
   - 点击 **File -> Open**，选择目录：
     `C:\Users\shinichimigi\.gemini\antigravity\scratch\EntityPulse`；
   - 等待 Gradle 自动完成依赖下载与同步。
2. **运行测试**：
   - 在项目右键点击 `app/src/test`，选择 **Run 'Tests in app.test'** 验证平台识别器与解析器。
3. **部署至手机或模拟器**：
   - 连接 Android 8.0+ 物理机或启动 Android 模拟器，点击顶部绿色 **Run (Shift+F10)** 即可安装体验。
