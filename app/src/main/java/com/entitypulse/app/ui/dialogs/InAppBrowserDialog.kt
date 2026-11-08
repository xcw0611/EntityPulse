package com.entitypulse.app.ui.dialogs

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Message
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.entitypulse.app.data.model.PlatformType
import com.entitypulse.app.data.model.PredefinedChannel
import com.entitypulse.app.network.PlatformResolver
import com.entitypulse.app.ui.screens.feed.PlatformBadge
import java.net.URLEncoder

private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
private const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppBrowserDialog(
    initialUrl: String,
    entityName: String,
    officialSiteUrl: String? = null,
    addedChannels: List<PredefinedChannel>,
    onChannelAdded: (PredefinedChannel) -> Unit,
    onRemoveChannel: (PredefinedChannel) -> Unit,
    onDismiss: () -> Unit
) {
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var inputUrl by remember { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    // 默认开启电脑桌面模式，确保 B 站与 X 加载完整功能与登录框
    var isDesktopMode by remember { mutableStateOf(true) }

    // 登录菜单展开状态
    var showLoginMenu by remember { mutableStateOf(false) }

    // 成功提示横幅状态
    var bannerMessage by remember { mutableStateOf<String?>(null) }

    val resolvedPlatform = remember(currentUrl) { PlatformResolver.resolve(currentUrl) }
    val isLockable = remember(currentUrl) { PlatformResolver.isLockableProfile(currentUrl) }

    // 快捷传送门（哔哩哔哩默认加载桌面电脑端完整可交互首页 www.bilibili.com）
    val portals = remember(entityName, officialSiteUrl) {
        val list = mutableListOf<Pair<String, String>>()
        if (!officialSiteUrl.isNullOrBlank()) {
            list.add("🌐 官网直达" to officialSiteUrl)
        }
        val encodedName = runCatching { URLEncoder.encode("$entityName 官方社交媒体", "UTF-8") }.getOrDefault(entityName)
        list.add("🔍 搜索主体" to "https://www.bing.com/search?q=$encodedName")
        list.add("𝕏 X (Twitter)" to "https://x.com/")
        list.add("▶️ YouTube" to "https://www.youtube.com/")
        list.add("📺 哔哩哔哩" to "https://www.bilibili.com/")
        list.add("🎮 Steam" to "https://store.steampowered.com/")
        list.add("🔴 Reddit" to "https://www.reddit.com/")
        list.add("微博" to "https://weibo.com/")
        list
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 12.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // --- 顶部导航与地址栏 ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "关闭浏览器")
                        }

                        IconButton(
                            onClick = { webViewInstance?.goBack() },
                            enabled = canGoBack,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "后退")
                        }

                        IconButton(
                            onClick = { webViewInstance?.goForward() },
                            enabled = canGoForward,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "前进")
                        }

                        IconButton(
                            onClick = { webViewInstance?.reload() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
                        }

                        // 电脑桌面版 / 移动版一键切换开关（解决 B 站仅有视频与 X 移动端登录空白）
                        IconButton(
                            onClick = {
                                isDesktopMode = !isDesktopMode
                                webViewInstance?.settings?.apply {
                                    userAgentString = if (isDesktopMode) DESKTOP_UA else MOBILE_UA
                                }
                                webViewInstance?.reload()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isDesktopMode) Icons.Default.DesktopWindows else Icons.Default.PhoneAndroid,
                                contentDescription = if (isDesktopMode) "当前为电脑版" else "当前为手机版",
                                tint = if (isDesktopMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // 地址与搜索输入框
                        OutlinedTextField(
                            value = inputUrl,
                            onValueChange = { inputUrl = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            placeholder = { Text("输入网址或搜索关键词...", style = MaterialTheme.typography.bodySmall) },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        val target = inputUrl.trim()
                                        if (target.isNotBlank()) {
                                            val finalUrl = if (target.startsWith("http://") || target.startsWith("https://")) {
                                                target
                                            } else if (target.contains(".") && !target.contains(" ")) {
                                                "https://$target"
                                            } else {
                                                val q = runCatching { URLEncoder.encode(target, "UTF-8") }.getOrDefault(target)
                                                "https://www.bing.com/search?q=$q"
                                            }
                                            webViewInstance?.loadUrl(finalUrl)
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.ArrowForwardIos, contentDescription = "前往", modifier = Modifier.size(16.dp))
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                        )
                    }

                    // 网页加载进度条
                    if (isLoading && progress < 100) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .padding(top = 4.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // 快捷传送门与统一登录入口 Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 统一账号登入入口下拉菜单
                        Box {
                            AssistChip(
                                onClick = { showLoginMenu = true },
                                label = { Text("🔑 账号登录", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) },
                                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )

                            DropdownMenu(
                                expanded = showLoginMenu,
                                onDismissRequest = { showLoginMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("𝕏 X (Twitter) 登录页面") },
                                    onClick = {
                                        showLoginMenu = false
                                        val url = "https://x.com/i/flow/login"
                                        inputUrl = url
                                        webViewInstance?.loadUrl(url)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("📺 哔哩哔哩官方登录") },
                                    onClick = {
                                        showLoginMenu = false
                                        val url = "https://passport.bilibili.com/login"
                                        inputUrl = url
                                        webViewInstance?.loadUrl(url)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("▶️ Google / YouTube 登录") },
                                    onClick = {
                                        showLoginMenu = false
                                        val url = "https://accounts.google.com/ServiceLogin?service=youtube"
                                        inputUrl = url
                                        webViewInstance?.loadUrl(url)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("🎮 Steam 商店登录") },
                                    onClick = {
                                        showLoginMenu = false
                                        val url = "https://store.steampowered.com/login/"
                                        inputUrl = url
                                        webViewInstance?.loadUrl(url)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("🔴 Reddit 登录页面") },
                                    onClick = {
                                        showLoginMenu = false
                                        val url = "https://www.reddit.com/login/"
                                        inputUrl = url
                                        webViewInstance?.loadUrl(url)
                                    }
                                )
                            }
                        }

                        portals.forEach { (name, url) ->
                            SuggestionChip(
                                onClick = {
                                    inputUrl = url
                                    webViewInstance?.loadUrl(url)
                                },
                                label = { Text(name, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                // 成功横幅提醒
                AnimatedVisibility(visible = bannerMessage != null) {
                    bannerMessage?.let { msg ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { bannerMessage = null }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "关闭提醒", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                // --- 中间核心：应用内 WebView 网页浏览器 ---
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                // 开启三方 Cookie 保证 X / Bilibili 登录跨域授权不被拦截
                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    javaScriptCanOpenWindowsAutomatically = true
                                    setSupportMultipleWindows(true)
                                    userAgentString = if (isDesktopMode) DESKTOP_UA else MOBILE_UA
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                        if (url != null) {
                                            view?.loadUrl(url)
                                            return true
                                        }
                                        return false
                                    }

                                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                        // 保证在本地代理工具 (Clash Meta) 代理证书握手时顺利通行
                                        handler?.proceed()
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        isLoading = true
                                        url?.let {
                                            currentUrl = it
                                            inputUrl = it
                                        }
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isLoading = false
                                        url?.let {
                                            currentUrl = it
                                            inputUrl = it
                                        }
                                        pageTitle = view?.title ?: ""
                                        canGoBack = view?.canGoBack() == true
                                        canGoForward = view?.canGoForward() == true
                                        cookieManager.flush()
                                    }

                                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                        url?.let {
                                            currentUrl = it
                                            inputUrl = it
                                        }
                                        canGoBack = view?.canGoBack() == true
                                        canGoForward = view?.canGoForward() == true
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        progress = newProgress
                                        if (newProgress >= 100) isLoading = false
                                    }

                                    // 支持 X 登录与 OAuth 弹窗转发到当前 WebView 中无缝加载，避免空白卡死
                                    override fun onCreateWindow(
                                        view: WebView?,
                                        isDialog: Boolean,
                                        isUserGesture: Boolean,
                                        resultMsg: Message?
                                    ): Boolean {
                                        val transport = resultMsg?.obj as? WebView.WebViewTransport
                                        transport?.webView = view
                                        resultMsg?.sendToTarget()
                                        return true
                                    }
                                }

                                loadUrl(initialUrl)
                                webViewInstance = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // --- 底部：实时社媒网址识别与一键锁定面板 ---
                Surface(
                    tonalElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. 当前网址自动识别卡片
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isLockable) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                if (isLockable) {
                                    PlatformBadge(resolvedPlatform.platformType)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = resolvedPlatform.identifier,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = resolvedPlatform.suggestedTitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    val isAlreadyAdded = addedChannels.any {
                                        it.platformType == resolvedPlatform.platformType &&
                                                it.identifier.equals(resolvedPlatform.identifier, ignoreCase = true)
                                    }

                                    Button(
                                        onClick = {
                                            if (!isAlreadyAdded) {
                                                val ch = PredefinedChannel(
                                                    platformType = resolvedPlatform.platformType,
                                                    identifier = resolvedPlatform.identifier,
                                                    title = resolvedPlatform.suggestedTitle,
                                                    feedPathOrUrl = resolvedPlatform.feedPathOrUrl,
                                                    defaultSelected = true,
                                                    probabilityNote = "用户手动锁定"
                                                )
                                                onChannelAdded(ch)
                                                bannerMessage = "已锁定保存 [${resolvedPlatform.platformType.displayName}] ${resolvedPlatform.identifier}！可继续浏览并添加下一个。"
                                            } else {
                                                bannerMessage = "该账号已在列表中，无需重复添加。"
                                            }
                                        },
                                        enabled = !isAlreadyAdded,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            if (isAlreadyAdded) Icons.Default.Check else Icons.Default.AddLink,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (isAlreadyAdded) "已添加" else "识别并添加此账号")
                                    }
                                } else {
                                    Icon(
                                        Icons.Default.TravelExplore,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "正在浏览网页... 请在上方进入该主体的社媒主页（X、YouTube、B站空间或官网），系统将自动识别并点亮锁定按钮。",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // 2. 已添加账号横向清单
                        if (addedChannels.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "已锁定 (${addedChannels.size}):",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                addedChannels.forEach { ch ->
                                    InputChip(
                                        selected = true,
                                        onClick = { onRemoveChannel(ch) },
                                        label = { Text("${ch.platformType.displayName}: ${ch.identifier}") },
                                        trailingIcon = {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "移除",
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        // 3. 底部完成按钮
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (addedChannels.isNotEmpty()) "完成添加并返回 (${addedChannels.size} 个账号)" else "关闭浏览器并返回"
                            )
                        }
                    }
                }
            }
        }
    }
}
