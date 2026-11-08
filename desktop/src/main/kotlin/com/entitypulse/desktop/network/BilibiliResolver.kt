package com.entitypulse.desktop.network

import com.entitypulse.desktop.util.AppLogger
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

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

object BilibiliResolver {

    fun searchUser(query: String, client: OkHttpClient): List<BilibiliCreator> {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val url = "https://api.bilibili.com/x/web-interface/search/type?search_type=bili_user&keyword=$encoded"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Referer", "https://search.bilibili.com/")
            .build()

        val results = mutableListOf<BilibiliCreator>()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val root = JSONObject(body)
                val code = root.optInt("code", -1)
                if (code != 0) return emptyList()

                val data = root.optJSONObject("data") ?: return emptyList()
                val resultList = data.optJSONArray("result") ?: return emptyList()

                for (i in 0 until minOf(resultList.length(), 4)) {
                    val userObj = resultList.optJSONObject(i) ?: continue
                    val mid = userObj.optLong("mid", 0L)
                    val uname = userObj.optString("uname", "")
                    val sign = userObj.optString("usign", "")
                    val fans = userObj.optLong("fans", 0L)
                    val videos = userObj.optInt("videos", 0)
                    var face = userObj.optString("upic", "")
                    if (face.startsWith("//")) face = "https:$face"
                    val officialVerify = userObj.optJSONObject("official_verify")
                    val verifyType = officialVerify?.optInt("type", -1) ?: -1
                    val verifyDesc = officialVerify?.optString("desc", "") ?: ""

                    if (mid > 0L && uname.isNotBlank()) {
                        results.add(
                            BilibiliCreator(
                                mid = mid,
                                uname = uname,
                                sign = sign,
                                fans = fans,
                                videos = videos,
                                avatarUrl = face,
                                isOfficial = verifyType >= 0,
                                verifyDesc = verifyDesc
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.w("BilibiliResolver", "B站创作者搜索失败: ${e.message}")
        }
        return results
    }

    fun getCreatorByUid(mid: Long, client: OkHttpClient): BilibiliCreator? {
        val url = "https://api.bilibili.com/x/space/acc/info?mid=$mid"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Referer", "https://space.bilibili.com/$mid")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val root = JSONObject(body)
                if (root.optInt("code", -1) != 0) return null
                val data = root.optJSONObject("data") ?: return null
                val uname = data.optString("name", "")
                val sign = data.optString("sign", "")
                var face = data.optString("face", "")
                if (face.startsWith("//")) face = "https:$face"
                val official = data.optJSONObject("official")
                val isOfficial = (official?.optInt("role", -1) ?: -1) > 0
                val title = official?.optString("title", "") ?: ""
                BilibiliCreator(
                    mid = mid,
                    uname = uname,
                    sign = sign,
                    fans = 0L,
                    videos = 0,
                    avatarUrl = face,
                    isOfficial = isOfficial,
                    verifyDesc = title
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    fun resolveNumericFeedPath(inputFeedPath: String, client: OkHttpClient): String {
        val trimmed = inputFeedPath.trim()
        val prefix = "/bilibili/user/video/"
        if (!trimmed.startsWith(prefix, ignoreCase = true)) return trimmed

        val keyword = trimmed.removePrefix(prefix).substringBefore('/').substringBefore('?').trim()
        if (keyword.toLongOrNull() != null) {
            return "/bilibili/user/video/$keyword"
        }

        AppLogger.i("BilibiliResolver", "B站关键词不是纯数字 UID ($keyword)，尝试联网解析...")
        val creators = searchUser(keyword, client)
        if (creators.isNotEmpty()) {
            val best = creators.firstOrNull { it.isOfficial } ?: creators.first()
            AppLogger.i("BilibiliResolver", "命中 B 站用户: ${best.uname} (UID: ${best.mid})")
            return "/bilibili/user/video/${best.mid}"
        }

        return trimmed
    }
}
