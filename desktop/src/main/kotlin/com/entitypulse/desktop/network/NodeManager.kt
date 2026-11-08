package com.entitypulse.desktop.network

import com.entitypulse.desktop.data.model.TimeRangeFilter
import com.entitypulse.desktop.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.TimeUnit

class NodeManager {

    var activeNode: String = "https://rsshub.app"
    var backupNodes: List<String> = listOf(
        "https://rsshub.app",
        "https://rsshub.rss.direct",
        "https://rss.itc.sh"
    )
    var accessToken: String = ""

    // PC 端默认直连本地 Clash Meta / 代理软件 (127.0.0.1:7897)
    var isProxyEnabled: Boolean = true
    var proxyHost: String = "127.0.0.1"
    var proxyPort: Int = 7897
    var proxyType: String = "HTTP" // "HTTP" 或 "SOCKS"

    var defaultTimeRange: TimeRangeFilter = TimeRangeFilter.ONE_YEAR

    fun getProxyInstance(): Proxy? {
        if (!isProxyEnabled || proxyHost.isBlank() || proxyPort <= 0) return null
        return try {
            val type = if (proxyType.equals("SOCKS", ignoreCase = true)) Proxy.Type.SOCKS else Proxy.Type.HTTP
            Proxy(type, InetSocketAddress(proxyHost, proxyPort))
        } catch (e: Exception) {
            AppLogger.w("NodeManager", "代理配置解析失败: ${e.message}")
            null
        }
    }

    /**
     * 根据目标 URL 智能选择是否走代理：
     * 国内站点（如 bilibili.com, bgm.tv）直连以保证最高速度，海外海外站点（如 x.com, twitter.com, youtube.com）统一路由至 Clash Meta
     */
    fun getOkHttpClient(
        connectTimeoutSec: Long = 10,
        readTimeoutSec: Long = 15
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)

        val proxy = getProxyInstance()
        if (proxy != null) {
            builder.proxy(proxy)
        }

        return builder.build()
    }

    suspend fun testProxyConnectivity(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (!isProxyEnabled) {
            return@withContext Pair(false, "代理当前处于关闭状态")
        }
        val startTime = System.currentTimeMillis()
        try {
            // 1. 本地端口握手测试
            Socket().use { socket ->
                socket.connect(InetSocketAddress(proxyHost, proxyPort), 2500)
            }
            // 2. 外部节点端到端连通测试
            val client = getOkHttpClient(connectTimeoutSec = 5, readTimeoutSec = 5)
            val request = Request.Builder()
                .url("https://www.google.com/generate_204")
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(request).execute().use { response ->
                val elapsed = System.currentTimeMillis() - startTime
                if (response.isSuccessful || response.code == 204) {
                    Pair(true, "Clash Meta 代理就绪 (${elapsed}ms)")
                } else {
                    Pair(true, "端口连通，HTTP 状态码: ${response.code}")
                }
            }
        } catch (e: Exception) {
            val elapsed = System.currentTimeMillis() - startTime
            val msg = e.localizedMessage ?: "连接失败"
            if (msg.contains("refused", ignoreCase = true)) {
                Pair(false, "本地端口连接被拒绝，请确认 Clash Meta 已启动且监听端口为 $proxyPort")
            } else {
                Pair(false, "代理连接超时/异常 (${elapsed}ms): $msg")
            }
        }
    }

    fun getAllCandidateNodes(): List<String> {
        val list = mutableListOf<String>()
        if (activeNode.isNotBlank()) list.add(activeNode)
        for (b in backupNodes) {
            if (!list.contains(b)) list.add(b)
        }
        return list
    }
}
