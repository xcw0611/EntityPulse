package com.entitypulse.app.network

import com.entitypulse.app.data.model.EntityCatalog
import com.entitypulse.app.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * 主体 IP 定位候选实体数据模型
 */
data class IpCandidate(
    val canonicalName: String,
    val originalOrEnglishName: String? = null,
    val category: String = "ACG / 游戏 / 创作者",
    val bio: String? = null,
    val avatarUrl: String? = null,
    val officialSiteUrl: String? = null,
    val sourceNote: String = "全网知识网络"
)

/**
 * 专注于主体 IP（动漫、游戏、创作者、工作室）的知识网络全网定位与歧义消解。
 * 遵循用户指示：完全移除社媒账号的自动盲猜与网络抓取，所有社媒账号转由用户在应用内浏览器自由锁定。
 */
object OnlineEntityLocator {

    /**
     * 根据用户输入的关键词，全网检索并返回多个可能匹配的主体 IP 备选项
     */
    suspend fun searchIpCandidates(
        query: String,
        client: OkHttpClient
    ): List<IpCandidate> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val candidates = mutableListOf<IpCandidate>()
        AppLogger.i("OnlineEntityLocator", "开始全网定位主体 IP 候选: $trimmed")

        // 1. 精选主体库快速前置命中
        val popular = EntityCatalog.POPULAR_ENTITIES.firstOrNull {
            it.name.equals(trimmed, ignoreCase = true) ||
                    trimmed.contains(it.name, ignoreCase = true) ||
                    it.name.contains(trimmed, ignoreCase = true)
        }
        if (popular != null) {
            candidates.add(
                IpCandidate(
                    canonicalName = popular.name,
                    category = popular.category,
                    bio = popular.bio,
                    avatarUrl = popular.avatarUrl,
                    sourceNote = "精选认证主体"
                )
            )
        }

        // 2. Steam 游戏商店检索
        try {
            val steamGames = querySteamStore(trimmed, client)
            for (game in steamGames) {
                if (candidates.none { it.canonicalName.equals(game.canonicalName, ignoreCase = true) }) {
                    candidates.add(game)
                }
            }
        } catch (e: Exception) {
            AppLogger.w("OnlineEntityLocator", "Steam 检索失败: ${e.message}")
        }

        // 3. Bangumi 动漫/游戏 IP 检索
        try {
            val bgmSubjects = queryBangumiSubjects(trimmed, client)
            for (subject in bgmSubjects) {
                if (candidates.none { it.canonicalName.equals(subject.canonicalName, ignoreCase = true) }) {
                    candidates.add(subject)
                }
            }
        } catch (e: Exception) {
            AppLogger.w("OnlineEntityLocator", "Bangumi 检索失败: ${e.message}")
        }

        // 4. 萌娘百科 ACG 知识库检索
        try {
            val moegirlCandidates = queryMoegirlpedia(trimmed, client)
            for (moe in moegirlCandidates) {
                if (candidates.none { it.canonicalName.equals(moe.canonicalName, ignoreCase = true) }) {
                    candidates.add(moe)
                }
            }
        } catch (e: Exception) {
            AppLogger.w("OnlineEntityLocator", "萌娘百科检索失败: ${e.message}")
        }

        // 5. 维基百科知识图谱检索
        try {
            val wikiCandidates = queryWikipedia(trimmed, client)
            for (wiki in wikiCandidates) {
                if (candidates.none { it.canonicalName.equals(wiki.canonicalName, ignoreCase = true) }) {
                    candidates.add(wiki)
                }
            }
        } catch (e: Exception) {
            AppLogger.w("OnlineEntityLocator", "维基百科检索失败: ${e.message}")
        }

        // 若上述均未检索到，则构建一个基于用户输入的默认主体配置
        if (candidates.isEmpty()) {
            candidates.add(
                IpCandidate(
                    canonicalName = trimmed,
                    category = "自定义主体",
                    bio = "用户自定义关注主体",
                    sourceNote = "手动输入"
                )
            )
        }

        AppLogger.i("OnlineEntityLocator", "主体 IP 定位完成，共发现 ${candidates.size} 个候选实体")
        candidates.take(5)
    }

    // --- Steam 游戏商店 ---
    private fun querySteamStore(query: String, client: OkHttpClient): List<IpCandidate> {
        val list = mutableListOf<IpCandidate>()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://store.steampowered.com/api/storesearch/?term=$encoded&l=schinese&cc=CN"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val root = JSONObject(body)
            val items = root.optJSONArray("items") ?: return emptyList()
            for (i in 0 until minOf(items.length(), 2)) {
                val item = items.optJSONObject(i) ?: continue
                val id = item.optInt("id", 0)
                val name = item.optString("name", "")
                val img = item.optString("tiny_image", "")
                if (id > 0 && name.isNotBlank()) {
                    list.add(
                        IpCandidate(
                            canonicalName = name,
                            category = "🎮 游戏作品 / IP",
                            bio = "Steam 热门游戏作品 (App ID: $id)",
                            avatarUrl = img.ifBlank { null },
                            officialSiteUrl = "https://store.steampowered.com/app/$id/",
                            sourceNote = "Steam 游戏库"
                        )
                    )
                }
            }
        }
        return list
    }

    // --- Bangumi 番组计划 ---
    private fun queryBangumiSubjects(query: String, client: OkHttpClient): List<IpCandidate> {
        val list = mutableListOf<IpCandidate>()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://api.bgm.tv/search/subject/$encoded?type=2&responseGroup=small"
        val searchReq = Request.Builder()
            .url(searchUrl)
            .header("User-Agent", "EntityPulse/1.0 (Android)")
            .build()

        client.newCall(searchReq).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val root = JSONObject(body)
            val items = root.optJSONArray("list") ?: return emptyList()
            for (i in 0 until minOf(items.length(), 2)) {
                val first = items.optJSONObject(i) ?: continue
                val subjectId = first.optInt("id", 0)
                val origName = first.optString("name", "")
                val cnName = first.optString("name_cn", "")
                val cover = first.optJSONObject("images")?.optString("large", "") ?: ""
                val name = cnName.ifBlank { origName }

                if (name.isNotBlank()) {
                    list.add(
                        IpCandidate(
                            canonicalName = name,
                            originalOrEnglishName = origName.ifBlank { null },
                            category = "🎬 动漫 / ACG 作品",
                            bio = "Bangumi 番组条目 ID: $subjectId",
                            avatarUrl = cover.ifBlank { null },
                            officialSiteUrl = "https://bgm.tv/subject/$subjectId",
                            sourceNote = "Bangumi 番组计划"
                        )
                    )
                }
            }
        }
        return list
    }

    // --- 萌娘百科 ---
    private fun queryMoegirlpedia(query: String, client: OkHttpClient): List<IpCandidate> {
        val list = mutableListOf<IpCandidate>()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val openSearchUrl = "https://zh.moegirl.org.cn/api.php?action=opensearch&search=$encoded&limit=2&format=json"
        val osReq = Request.Builder().url(openSearchUrl).header("User-Agent", "Mozilla/5.0").build()

        val foundTitles = mutableListOf<String>()
        client.newCall(osReq).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val array = runCatching { JSONArray(body) }.getOrNull()
                if (array != null && array.length() >= 2) {
                    val titles = array.getJSONArray(1)
                    for (i in 0 until titles.length()) {
                        val t = titles.optString(i, "")
                        if (t.isNotBlank()) foundTitles.add(t)
                    }
                }
            }
        }

        for (title in foundTitles.take(2)) {
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            val queryUrl = "https://zh.moegirl.org.cn/api.php?action=query&titles=$encodedTitle&prop=extracts|pageprops&format=json&explaintext=1&exintro=1"
            val qReq = Request.Builder().url(queryUrl).header("User-Agent", "Mozilla/5.0").build()

            client.newCall(qReq).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val root = JSONObject(body)
                    val pages = root.optJSONObject("query")?.optJSONObject("pages") ?: return@use
                    val keys = pages.keys()
                    if (keys.hasNext()) {
                        val pageKey = keys.next()
                        if (pageKey != "-1") {
                            val page = pages.optJSONObject(pageKey) ?: return@use
                            val extract = page.optString("extract", "").take(160)
                            list.add(
                                IpCandidate(
                                    canonicalName = title,
                                    category = "📖 ACG / 亚文化 IP",
                                    bio = extract.ifBlank { "萌娘百科收录条目" },
                                    avatarUrl = null,
                                    officialSiteUrl = "https://zh.moegirl.org.cn/$title",
                                    sourceNote = "萌娘百科"
                                )
                            )
                        }
                    }
                }
            }
        }
        return list
    }

    // --- 维基百科 ---
    private fun queryWikipedia(query: String, client: OkHttpClient): List<IpCandidate> {
        val list = mutableListOf<IpCandidate>()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://zh.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=$encoded&gsrlimit=2&prop=extracts|pageimages&exintro=1&explaintext=1&piprop=original&format=json"
        val req = Request.Builder().url(searchUrl).header("User-Agent", "Mozilla/5.0").build()

        client.newCall(req).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val root = JSONObject(body)
                val pages = root.optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
                val keys = pages.keys()
                while (keys.hasNext()) {
                    val pageObj = pages.optJSONObject(keys.next()) ?: continue
                    val title = pageObj.optString("title", "")
                    val extract = pageObj.optString("extract", "").take(160)
                    val originalImg = pageObj.optJSONObject("original")?.optString("source", "")

                    if (title.isNotBlank()) {
                        list.add(
                            IpCandidate(
                                canonicalName = title,
                                category = "📚 百科认证实体",
                                bio = extract.ifBlank { "维基百科知名词条" },
                                avatarUrl = originalImg?.ifBlank { null },
                                officialSiteUrl = "https://zh.wikipedia.org/wiki/$title",
                                sourceNote = "维基百科"
                            )
                        )
                    }
                }
            }
        }
        return list
    }
}
