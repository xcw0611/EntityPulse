package com.entitypulse.desktop.ui.dialogs

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.entitypulse.desktop.data.model.TimeRangeFilter
import com.entitypulse.desktop.network.NodeManager
import com.entitypulse.desktop.ui.theme.DarkBorder
import com.entitypulse.desktop.ui.theme.DarkSurfaceElevated
import com.entitypulse.desktop.util.AppLogger
import kotlinx.coroutines.launch

@Composable
fun SettingsDesktopDialog(
    nodeManager: NodeManager,
    onDismiss: () -> Unit,
    onSettingsChanged: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var isProxyEnabled by remember { mutableStateOf(nodeManager.isProxyEnabled) }
    var proxyHost by remember { mutableStateOf(nodeManager.proxyHost) }
    var proxyPort by remember { mutableStateOf(nodeManager.proxyPort.toString()) }
    var proxyType by remember { mutableStateOf(nodeManager.proxyType) }
    var defaultTimeRange by remember { mutableStateOf(nodeManager.defaultTimeRange) }
    var activeNode by remember { mutableStateOf(nodeManager.activeNode) }
    var accessToken by remember { mutableStateOf(nodeManager.accessToken) }

    var isTestingPing by remember { mutableStateOf(false) }
    var pingResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(680.dp)
                .heightIn(max = 660.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "设置与网络配置 (Clash Meta / 订阅偏好)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Clash Meta Section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Clash Meta 本地代理集成", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.weight(1f))
                                Switch(
                                    checked = isProxyEnabled,
                                    onCheckedChange = { isProxyEnabled = it }
                                )
                            }
                            Text(
                                text = "所有请求海外社媒（如 X / Twitter、YouTube、Google 翻译）统一经由本地 Clash Meta 代理端口转发，彻底解决中断重置与超时问题。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = proxyHost,
                                    onValueChange = { proxyHost = it },
                                    label = { Text("代理主机 Host") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1.5f),
                                    enabled = isProxyEnabled
                                )
                                OutlinedTextField(
                                    value = proxyPort,
                                    onValueChange = { proxyPort = it },
                                    label = { Text("代理端口 Port") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    enabled = isProxyEnabled
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (isProxyEnabled) "已启用代理路由 (默认 127.0.0.1:7890)" else "代理已禁用 (全部直连)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isProxyEnabled) Color(0xFF34D399) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Button(
                                    onClick = {
                                        isTestingPing = true
                                        pingResult = null
                                        coroutineScope.launch {
                                            val p = proxyPort.toIntOrNull() ?: 7890
                                            nodeManager.isProxyEnabled = isProxyEnabled
                                            nodeManager.proxyHost = proxyHost.trim()
                                            nodeManager.proxyPort = p
                                            val res = nodeManager.testProxyConnectivity()
                                            isTestingPing = false
                                            pingResult = res
                                        }
                                    },
                                    enabled = !isTestingPing && isProxyEnabled,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    if (isTestingPing) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("测试代理连通", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            // Ping result feedback
                            pingResult?.let { (isSuccess, message) ->
                                if (isSuccess) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF065F46).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(message, style = MaterialTheme.typography.bodySmall, color = Color(0xFF34D399))
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF7F1D1D).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(message, style = MaterialTheme.typography.bodySmall, color = Color(0xFFFCA5A5))
                                    }
                                }
                            }
                        }
                    }

                    // Feed Display Section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("动态流显示范围", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }

                            Text("选择启动时默认加载的历史动态范围：", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TimeRangeFilter.entries.forEach { filter ->
                                    FilterChip(
                                        selected = defaultTimeRange == filter,
                                        onClick = { defaultTimeRange = filter },
                                        label = { Text(filter.displayName) }
                                    )
                                }
                            }
                        }
                    }

                    // Custom RSSHub Section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.RssFeed, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("自建 RSSHub 节点 (可选)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }

                            OutlinedTextField(
                                value = activeNode,
                                onValueChange = { activeNode = it },
                                label = { Text("RSSHub 地址 (默认: https://rsshub.app)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = accessToken,
                                onValueChange = { accessToken = it },
                                label = { Text("访问 Token (可选)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // System Info
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("本地存储与日志", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
                            Text("本地数据库: $appData\\EntityPulse\\data.db", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("当前已记录运行日志: ${AppLogger.getAllLogs().size} 条", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(onClick = {
                        nodeManager.isProxyEnabled = isProxyEnabled
                        nodeManager.proxyHost = proxyHost.trim()
                        nodeManager.proxyPort = proxyPort.toIntOrNull() ?: 7890
                        nodeManager.proxyType = proxyType
                        nodeManager.defaultTimeRange = defaultTimeRange
                        nodeManager.activeNode = activeNode.trim()
                        nodeManager.accessToken = accessToken.trim()
                        onSettingsChanged()
                        onDismiss()
                    }) {
                        Text("保存配置")
                    }
                }
            }
        }
    }
}
