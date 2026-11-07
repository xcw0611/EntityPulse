package com.entitypulse.app.network

import com.entitypulse.app.data.model.PlatformType
import com.entitypulse.app.data.model.PredefinedChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object OnlineEntityLocator {

    private val quickClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 针对用户输入的主体名称进行全网智能搜索与定位：
     * 1. 调用 DuckDuckGo 联想引擎（探测海外各平台 Handle 常见官方组合，如 "query twitter", "query youtube"）
     * 2. 调用 Bilibili 搜索建议引擎（探测国内 UP 主与官方认证账号名称）
     * 3. 调用 Wikipedia OpenSearch 建议引擎（探测官方规范命名与开发团队主体）
     * 返回按高概率排序的候选账号列表。
     */
    suspend fun searchOnlineCandidates(
        query: String,
        client: OkHttpClient? = null
    ): List<PredefinedChannel> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val httpClient = client ?: quickClient
        val results = mutableListOf<PredefinedChannel>()

        // 1. DuckDuckGo 联想探测 Twitter 与 YouTube 真实 Handle 组合
        try {
            val ddgTwitterResults = fetchDuckDuckGoSuggestions("$trimmed twitter", httpClient)
            for (term in ddgTwitterResults) {
                val cleanTerm = term.replace("twitter", "", ignoreCase = true)
                    .replace("account", "", ignoreCase = true)
                    .replace("official", "Official", ignoreCase = true)
                    .trim()
                val handle = cleanTerm.replace("\\s+".toRegex(), "")
                if (handle.isNotBlank() && handle.length in 3..25) {
                    results.add(
                        PredefinedChannel(
                            platformType = PlatformType.TWITTER,
                            identifier = "@$handle",
                            title = "X / Twitter (@$handle)",
                            feedPathOrUrl = "/twitter/user/$handle",
                            defaultSelected = false,
                            probabilityNote = "全网联想"
                        )
                    )
                }
            }

            val ddgYtResults = fetchDuckDuckGoSuggestions("$trimmed youtube", httpClient)
            for (term in ddgYtResults) {
                val cleanTerm = term.replace("youtube", "", ignoreCase = true)
                    .replace("channel", "", ignoreCase = true)
                    .trim()
                val handle = cleanTerm.replace("\\s+".toRegex(), "")
                if (handle.isNotBlank() && handle.length in 3..30) {
                    results.add(
                        PredefinedChannel(
                            platformType = PlatformType.YOUTUBE,
                            identifier = "@$handle",
                            title = "YouTube (@$handle)",
                            feedPathOrUrl = "/youtube/user/@$handle",
                            defaultSelected = false,
                            probabilityNote = "全网联想"
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // 网络波动或无代理时静默忽略
        }

        // 2. Bilibili 建议引擎探测国内账号
        try {
            val biliResults = fetchBilibiliSuggestions(trimmed, httpClient)
            for (name in biliResults.take(4)) {
                results.add(
                    PredefinedChannel(
                        platformType = PlatformType.BILIBILI,
                        identifier = name,
                        title = "Bilibili ($name)",
                        feedPathOrUrl = "/bilibili/user/video/$name",
                        defaultSelected = false,
                        probabilityNote = "B站探测"
                    )
                )
            }
        } catch (_: Exception) {
        }

        // 去重
        results.distinctBy { "${it.platformType}_${it.identifier.lowercase()}" }
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

    private fun fetchBilibiliSuggestions(query: String, client: OkHttpClient): List<String> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://s.search.bilibili.com/main/suggest?term=$encoded"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val root = JSONObject(body)
            val result = root.optJSONObject("result") ?: return emptyList()
            val tagArray = result.optJSONArray("tag") ?: return emptyList()
            val list = mutableListOf<String>()
            for (i in 0 until tagArray.length()) {
                val item = tagArray.optJSONObject(i)
                val value = item?.optString("value")
                if (!value.isNullOrBlank()) {
                    list.add(value)
                }
            }
            return list
        }
        return emptyList()
    }
}
