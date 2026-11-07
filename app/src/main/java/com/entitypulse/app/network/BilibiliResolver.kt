package com.entitypulse.app.network

import com.entitypulse.app.util.AppLogger
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

object BilibiliResolver {

    data class BilibiliCreator(
        val mid: Long,
        val uname: String,
        val sign: String,
        val fans: Long,
        val videos: Int,
        val avatarUrl: String,
        val isOfficial: Boolean,
        val verifyDesc: String
    )

    /**
     * 针对关键词或原名搜索真实 Bilibili UP 主账号，返回携带真实数字 UID (mid) 的创作者列表
     */
    fun searchCreators(keyword: String, client: OkHttpClient): List<BilibiliCreator> {
        val clean = keyword.trim()
        if (clean.isBlank()) return emptyList()

        val encoded = URLEncoder.encode(clean, "UTF-8")
        val url = "https://api.bilibili.com/x/web-interface/search/type?search_type=bili_user&keyword=$encoded"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Referer", "https://search.bilibili.com/")
            .header("Accept", "application/json, text/plain, */*")
            .build()

        AppLogger.d("BilibiliResolver", "搜索 B 站创作者: $url")
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                AppLogger.w("BilibiliResolver", "B 站搜索接口返回 HTTP ${response.code}")
                return emptyList()
            }
            val body = response.body?.string() ?: return emptyList()
            val root = JSONObject(body)
            val code = root.optInt("code", -1)
            if (code != 0) {
                AppLogger.w("BilibiliResolver", "B 站接口返回 code=$code, msg=${root.optString("message")}")
                return emptyList()
            }

            val data = root.optJSONObject("data") ?: return emptyList()
            val resultArray = data.optJSONArray("result") ?: return emptyList()
            val creators = mutableListOf<BilibiliCreator>()

            for (i in 0 until minOf(resultArray.length(), 6)) {
                val item = resultArray.optJSONObject(i) ?: continue
                val mid = item.optLong("mid", 0L)
                if (mid <= 0L) continue

                val uname = item.optString("uname", "")
                val usign = item.optString("usign", "")
                val fans = item.optLong("fans", 0L)
                val videos = item.optInt("videos", 0)
                var upic = item.optString("upic", "")
                if (upic.startsWith("//")) upic = "https:$upic"

                val officialVerify = item.optJSONObject("official_verify")
                val vType = officialVerify?.optInt("type", -1) ?: -1
                val vDesc = officialVerify?.optString("desc", "") ?: ""
                val isOfficial = vType == 0 || vType == 1 || vDesc.isNotBlank()

                creators.add(
                    BilibiliCreator(
                        mid = mid,
                        uname = uname,
                        sign = usign,
                        fans = fans,
                        videos = videos,
                        avatarUrl = upic,
                        isOfficial = isOfficial,
                        verifyDesc = vDesc
                    )
                )
            }
            return creators
        }
    }

    /**
     * 如果用户输入的 B 站路径是 /bilibili/user/video/文本关键词，
     * 自动尝试将其解析为实际的数字 UID
     */
    fun resolveNumericFeedPath(path: String, client: OkHttpClient): String {
        if (!path.startsWith("/bilibili/user/video/", ignoreCase = true)) {
            return path
        }
        val target = path.removePrefix("/bilibili/user/video/").substringBefore('/').substringBefore('?').trim()
        // 如果已经是纯数字 UID，直接返回
        if (target.toLongOrNull() != null) {
            return path
        }

        // 尝试解析纯文本名称为数字 UID
        try {
            AppLogger.i("BilibiliResolver", "B 站路由包含非数字标识 [$target]，正在自动联网解析其数字 UID...")
            val creators = searchCreators(target, client)
            if (creators.isNotEmpty()) {
                val bestMid = creators.first().mid
                AppLogger.i("BilibiliResolver", "成功解析 [$target] 的 B 站 UID 为: $bestMid (${creators.first().uname})")
                return "/bilibili/user/video/$bestMid"
            }
        } catch (e: Exception) {
            AppLogger.w("BilibiliResolver", "解析 B 站 UID 失败: ${e.message}")
        }
        return path
    }
}
