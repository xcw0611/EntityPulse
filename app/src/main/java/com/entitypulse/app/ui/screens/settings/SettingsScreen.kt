package com.entitypulse.app.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.entitypulse.app.network.NodeManager
import com.entitypulse.app.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val nodeManager = viewModel.nodeManager

    var customNodeInput by remember { mutableStateOf(nodeManager.customNode) }
    var accessTokenInput by remember { mutableStateOf(nodeManager.accessToken) }
    var selectedInterval by remember { mutableIntStateOf(nodeManager.syncIntervalMinutes) }

    var dndEnabled by remember { mutableStateOf(nodeManager.isDndEnabled) }
    var dndStartHour by remember { mutableIntStateOf(nodeManager.dndStartHour) }
    var dndEndHour by remember { mutableIntStateOf(nodeManager.dndEndHour) }

    var proxyEnabled by remember { mutableStateOf(nodeManager.isProxyEnabled) }
    var proxyType by remember { mutableStateOf(nodeManager.proxyType) }
    var proxyHost by remember { mutableStateOf(nodeManager.proxyHost) }
    var proxyPort by remember { mutableIntStateOf(nodeManager.proxyPort) }
    var testingProxy by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var testResultSuccess by remember { mutableStateOf<Boolean?>(null) }

    // SAF 文件选择器
    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportBackupJson(it) }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importBackupJson(it) }
    }

    val exportOpmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/xml")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportOpml(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置与配置", fontWeight = FontWeight.Bold) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section 1: 后台同步与通知
            Text(
                text = "后台同步与通知策略",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "后台轮询间隔 (基于 Android WorkManager 智能电量调度)",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    val intervals = listOf(15 to "15 分钟", 30 to "30 分钟", 60 to "1 小时", 120 to "2 小时")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        intervals.forEach { (mins, label) ->
                            FilterChip(
                                selected = selectedInterval == mins,
                                onClick = {
                                    selectedInterval = mins
                                    viewModel.updateSyncInterval(mins)
                                },
                                label = { Text(label) }
                            )
                        }
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("免打扰时段 (静音模式)", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "时段内新动态不触发横幅与声音提示",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(
                            checked = dndEnabled,
                            onCheckedChange = {
                                dndEnabled = it
                                nodeManager.isDndEnabled = it
                            }
                        )
                    }

                    if (dndEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("时段范围", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${String.format("%02d:00", dndStartHour)} 至 ${String.format("%02d:00", dndEndHour)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    HorizontalDivider()

                    FilledTonalButton(
                        onClick = { viewModel.sendTestNotification() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("发送一条测试富媒体通知")
                    }
                }
            }

            // Section 2: RSSHub 节点与网络连接
            Text(
                text = "RSSHub 节点与网络连接",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "原生 RSS 源（如 Steam、YouTube、官方博客等）默认走直连；社媒类（X/Twitter、B站、微博等）优先使用下方节点：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = customNodeInput,
                        onValueChange = {
                            customNodeInput = it
                            nodeManager.customNode = it
                        },
                        label = { Text("自建 RSSHub 实例地址 (选填)") },
                        placeholder = { Text("例如: https://my-rsshub.example.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = accessTokenInput,
                        onValueChange = {
                            accessTokenInput = it
                            nodeManager.accessToken = it
                        },
                        label = { Text("Access Code / Token (若自建节点启用了鉴权)") },
                        placeholder = { Text("例如: 123456") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "当前默认备选公共镜像池: ${NodeManager.PUBLIC_MIRRORS.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // Section 2.5: 网络加速与应用内置代理 (解决海外平台连接被重置问题)
            Text(
                text = "网络加速与应用内置代理",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "若拉取 X/Twitter、YouTube 等海外动态时提示连接被重置 (Connection closed)，可开启本项将应用网络请求直通手机上运行的代理客户端（支持 HTTP / SOCKS5 协议，常见端口：Clash 7890/7897，V2Ray 10808/10809）：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("启用应用网络代理", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Switch(
                            checked = proxyEnabled,
                            onCheckedChange = {
                                proxyEnabled = it
                                viewModel.updateProxySettings(it, proxyHost, proxyPort, proxyType)
                            }
                        )
                    }

                    if (proxyEnabled) {
                        // 代理协议选择
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("代理协议:", style = MaterialTheme.typography.labelMedium)
                            FilterChip(
                                selected = proxyType == "HTTP",
                                onClick = {
                                    proxyType = "HTTP"
                                    viewModel.updateProxySettings(proxyEnabled, proxyHost, proxyPort, "HTTP")
                                },
                                label = { Text("HTTP") }
                            )
                            FilterChip(
                                selected = proxyType == "SOCKS5" || proxyType == "SOCKS",
                                onClick = {
                                    proxyType = "SOCKS5"
                                    viewModel.updateProxySettings(proxyEnabled, proxyHost, proxyPort, "SOCKS5")
                                },
                                label = { Text("SOCKS5") }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = proxyHost,
                                onValueChange = {
                                    proxyHost = it
                                    viewModel.updateProxySettings(proxyEnabled, it, proxyPort, proxyType)
                                },
                                label = { Text("代理 IP") },
                                singleLine = true,
                                modifier = Modifier.weight(2f)
                            )
                            OutlinedTextField(
                                value = proxyPort.toString(),
                                onValueChange = {
                                    val p = it.toIntOrNull() ?: 7890
                                    proxyPort = p
                                    viewModel.updateProxySettings(proxyEnabled, proxyHost, p, proxyType)
                                },
                                label = { Text("端口") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 连通性测试按钮与反馈
                    OutlinedButton(
                        onClick = {
                            testingProxy = true
                            testResultText = null
                            testResultSuccess = null
                            viewModel.testProxyConnection { success, message ->
                                testingProxy = false
                                testResultSuccess = success
                                testResultText = message
                            }
                        },
                        enabled = !testingProxy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (testingProxy) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("正在测试连通性...")
                        } else {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("测试网络与代理连通性")
                        }
                    }

                    if (testResultText != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (testResultSuccess == true) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = if (testResultSuccess == true) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (testResultSuccess == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = testResultText ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (testResultSuccess == true) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // Section 3: 数据备份与迁移
            Text(
                text = "数据备份与跨设备迁移",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "所有关注的主体及绑定的各平台账号均保存在手机本地数据库，您可以随时导出或恢复：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                exportJsonLauncher.launch("EntityPulse_Backup_${System.currentTimeMillis()}.json")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("导出 JSON")
                        }

                        FilledTonalButton(
                            onClick = {
                                importJsonLauncher.launch(arrayOf("application/json", "text/*"))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("导入备份")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            exportOpmlLauncher.launch("EntityPulse_Feeds_${System.currentTimeMillis()}.opml")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.RssFeed, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("导出标准 OPML 订阅表")
                    }
                }
            }

            // Section 4: 关于
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("关于 EntityPulse", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "版本 1.0.0 · 纯端侧以人为中心的动态聚合与通知中心",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
