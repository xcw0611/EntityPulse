package com.entitypulse.app.network

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

class NodeManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("rsshub_nodes", Context.MODE_PRIVATE)

    companion object {
        const val PREF_CUSTOM_NODE = "pref_custom_node"
        const val PREF_ACCESS_TOKEN = "pref_access_token"
        const val PREF_SYNC_INTERVAL_MIN = "pref_sync_interval_min"
        const val PREF_DND_ENABLED = "pref_dnd_enabled"
        const val PREF_DND_START_HOUR = "pref_dnd_start_hour"
        const val PREF_DND_END_HOUR = "pref_dnd_end_hour"

        const val PREF_PROXY_ENABLED = "pref_proxy_enabled"
        const val PREF_PROXY_TYPE = "pref_proxy_type"
        const val PREF_PROXY_HOST = "pref_proxy_host"
        const val PREF_PROXY_PORT = "pref_proxy_port"
        const val PREF_TIME_RANGE = "pref_time_range"

        val PUBLIC_MIRRORS = listOf(
            "https://rsshub.app",
            "https://rsshub.rssforever.com",
            "https://rss.owo.nz",
            "https://hub.slat.cc"
        )
    }

    var customNode: String
        get() = prefs.getString(PREF_CUSTOM_NODE, "") ?: ""
        set(value) = prefs.edit().putString(PREF_CUSTOM_NODE, value.trim()).apply()

    var accessToken: String
        get() = prefs.getString(PREF_ACCESS_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(PREF_ACCESS_TOKEN, value.trim()).apply()

    var syncIntervalMinutes: Int
        get() = prefs.getInt(PREF_SYNC_INTERVAL_MIN, 30)
        set(value) = prefs.edit().putInt(PREF_SYNC_INTERVAL_MIN, value).apply()

    var isDndEnabled: Boolean
        get() = prefs.getBoolean(PREF_DND_ENABLED, false)
        set(value) = prefs.edit().putBoolean(PREF_DND_ENABLED, value).apply()

    var dndStartHour: Int
        get() = prefs.getInt(PREF_DND_START_HOUR, 23)
        set(value) = prefs.edit().putInt(PREF_DND_START_HOUR, value).apply()

    var dndEndHour: Int
        get() = prefs.getInt(PREF_DND_END_HOUR, 8)
        set(value) = prefs.edit().putInt(PREF_DND_END_HOUR, value).apply()

    /**
     * 默认开启代理模式以适配 Clash Meta 等代理工具，保障海外与跨平台请求一致性
     */
    var isProxyEnabled: Boolean
        get() = prefs.getBoolean(PREF_PROXY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(PREF_PROXY_ENABLED, value).apply()

    var proxyType: String
        get() = prefs.getString(PREF_PROXY_TYPE, "HTTP") ?: "HTTP"
        set(value) = prefs.edit().putString(PREF_PROXY_TYPE, value.trim().uppercase()).apply()

    var proxyHost: String
        get() = prefs.getString(PREF_PROXY_HOST, "127.0.0.1") ?: "127.0.0.1"
        set(value) = prefs.edit().putString(PREF_PROXY_HOST, value.trim()).apply()

    var proxyPort: Int
        get() = prefs.getInt(PREF_PROXY_PORT, 7890)
        set(value) = prefs.edit().putInt(PREF_PROXY_PORT, value).apply()

    var defaultTimeRange: com.entitypulse.app.data.model.TimeRangeFilter
        get() {
            val raw = prefs.getString(PREF_TIME_RANGE, com.entitypulse.app.data.model.TimeRangeFilter.DEFAULT.name)
            return runCatching {
                com.entitypulse.app.data.model.TimeRangeFilter.valueOf(raw ?: com.entitypulse.app.data.model.TimeRangeFilter.DEFAULT.name)
            }.getOrDefault(com.entitypulse.app.data.model.TimeRangeFilter.DEFAULT)
        }
        set(value) = prefs.edit().putString(PREF_TIME_RANGE, value.name).apply()

    fun getProxy(): Proxy? {
        if (!isProxyEnabled) return null
        return runCatching {
            val type = if (proxyType.equals("SOCKS", ignoreCase = true) || proxyType.equals("SOCKS5", ignoreCase = true)) {
                Proxy.Type.SOCKS
            } else {
                Proxy.Type.HTTP
            }
            Proxy(type, InetSocketAddress(proxyHost.trim(), proxyPort))
        }.getOrNull()
    }

    /**
     * 全应用统一 OkHttpClient 构建工厂，强制确保所有子模块（RSS抓取、社媒探测、翻译引擎、连通测试）统一穿透代理
     */
    fun getOkHttpClient(
        connectTimeoutSec: Long = 10,
        readTimeoutSec: Long = 15
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)

        val proxy = getProxy()
        if (proxy != null) {
            builder.proxy(proxy)
        }
        return builder.build()
    }

    fun applyClashLocalPreset() {
        isProxyEnabled = true
        proxyHost = "127.0.0.1"
        proxyPort = 7890
        proxyType = "HTTP"
    }

    fun applyEmulatorHostPreset() {
        isProxyEnabled = true
        proxyHost = "10.0.2.2"
        proxyPort = 7890
        proxyType = "HTTP"
    }

    fun getActiveRssHubBaseUrl(): String {
        val custom = customNode.trimEnd('/')
        return if (custom.isNotBlank()) custom else PUBLIC_MIRRORS.first()
    }

    fun getAllCandidateNodes(): List<String> {
        val list = mutableListOf<String>()
        val custom = customNode.trimEnd('/')
        if (custom.isNotBlank()) {
            list.add(custom)
        }
        list.addAll(PUBLIC_MIRRORS)
        return list.distinct()
    }

    /**
     * 测试当前配置的代理与海外网络连通性，并返回延迟与诊断信息
     */
    suspend fun testProxyConnectivity(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val client = getOkHttpClient(connectTimeoutSec = 6, readTimeoutSec = 8)

        // 依次尝试 Google 204 与 Twitter
        val targets = listOf(
            "https://www.google.com/generate_204",
            "https://syndication.twitter.com"
        )

        for (url in targets) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    val latency = System.currentTimeMillis() - start
                    if (response.isSuccessful || response.code == 204 || response.code == 429) {
                        val proxyDesc = if (isProxyEnabled) "已成功经由 Clash Meta 代理 ($proxyHost:$proxyPort)" else "直连环境"
                        return@withContext true to "握手成功！延迟约 ${latency}ms ($proxyDesc)"
                    }
                }
            } catch (e: Exception) {
                // 继续尝试下一个测试点
            }
        }

        val errMsg = if (isProxyEnabled) {
            "无法通过 Clash Meta 代理连接 ($proxyHost:$proxyPort)。若在手机端请确认 Clash Meta 已启动且处于运行状态；若通过电脑局域网代理请确保开启了“允许局域网连接 (Allow LAN)”。"
        } else {
            "无法直连海外服务，请启用应用内置代理并连接 Clash Meta。"
        }
        false to errMsg
    }
}
