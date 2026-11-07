package com.entitypulse.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object TranslationManager {

    private val cache = ConcurrentHashMap<String, String>()

    private val defaultClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 翻译指定文本为目标语言（默认简体中文 zh-CN）
     * 具备双引擎保障（Google 翻译引擎优先，MyMemory 引擎兜底）与内存缓存
     */
    suspend fun translate(
        text: String,
        targetLang: String = "zh-CN",
        client: OkHttpClient? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.success("")
        }

        val cacheKey = "${targetLang}_${trimmed.hashCode()}"
        val cached = cache[cacheKey]
        if (cached != null) {
            return@withContext Result.success(cached)
        }

        val httpClient = client ?: defaultClient

        // 1. 尝试 Google 翻译免登录接口
        try {
            val googleResult = translateViaGoogle(trimmed, targetLang, httpClient)
            if (googleResult.isNotBlank()) {
                cache[cacheKey] = googleResult
                return@withContext Result.success(googleResult)
            }
        } catch (_: Exception) {
            // Google 失败或超时，自动无缝降级到 MyMemory
        }

        // 2. 备用引擎：MyMemory Translation API
        try {
            val myMemoryResult = translateViaMyMemory(trimmed, targetLang, httpClient)
            if (myMemoryResult.isNotBlank()) {
                cache[cacheKey] = myMemoryResult
                return@withContext Result.success(myMemoryResult)
            }
        } catch (e: Exception) {
            return@withContext Result.failure(IllegalStateException("翻译服务暂不可用: ${e.localizedMessage ?: "连接失败"}", e))
        }

        Result.failure(IllegalStateException("未能解析翻译结果"))
    }

    private fun translateViaGoogle(text: String, targetLang: String, client: OkHttpClient): String {
        // 分段切片以防超长 URL 限制
        val chunk = text.take(1500)
        val encoded = URLEncoder.encode(chunk, "UTF-8")
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetLang&dt=t&q=$encoded"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Google HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw IllegalStateException("空响应")
            val root = JSONArray(body)
            val segments = root.getJSONArray(0)
            val sb = java.lang.StringBuilder()
            for (i in 0 until segments.length()) {
                val seg = segments.getJSONArray(i)
                val part = seg.optString(0)
                if (part.isNotBlank()) {
                    sb.append(part)
                }
            }
            return sb.toString().trim()
        }
    }

    private fun translateViaMyMemory(text: String, targetLang: String, client: OkHttpClient): String {
        val chunk = text.take(500)
        val encoded = URLEncoder.encode(chunk, "UTF-8")
        val url = "https://api.mymemory.translated.net/get?q=$encoded&langpair=autodetect|$targetLang"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("MyMemory HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw IllegalStateException("空响应")
            val root = JSONObject(body)
            val responseData = root.optJSONObject("responseData") ?: throw IllegalStateException("解析异常")
            val translated = responseData.optString("translatedText")

            return decodeHtmlEntities(translated).trim()
        }
    }

    private fun decodeHtmlEntities(str: String): String {
        return str
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
    }
}
