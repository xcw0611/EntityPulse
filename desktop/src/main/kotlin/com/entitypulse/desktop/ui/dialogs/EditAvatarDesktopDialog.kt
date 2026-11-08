package com.entitypulse.desktop.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.entitypulse.desktop.network.NodeManager
import com.entitypulse.desktop.network.OnlineEntityLocator
import com.entitypulse.desktop.ui.DesktopAsyncImage
import com.entitypulse.desktop.ui.DesktopBrowserHelper
import com.entitypulse.desktop.ui.theme.DarkBorder
import kotlinx.coroutines.launch

@Composable
fun EditAvatarDesktopDialog(
    entityName: String,
    currentAvatarUrl: String?,
    nodeManager: NodeManager,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var avatarInput by remember { mutableStateOf(currentAvatarUrl ?: "") }
    var isSearching by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(520.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "修改「$entityName」封面 / 头像",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                // Image preview
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.5.dp, DarkBorder, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    DesktopAsyncImage(
                        url = avatarInput.ifBlank { null },
                        contentDescription = entityName,
                        nodeManager = nodeManager,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (isSearching) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = Color.Black.copy(alpha = 0.5f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                // Fast action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            isSearching = true
                            feedbackMessage = null
                            coroutineScope.launch {
                                val found = OnlineEntityLocator.fetchIpCoverImage(entityName, nodeManager.getOkHttpClient())
                                isSearching = false
                                if (!found.isNullOrBlank()) {
                                    avatarInput = found
                                    feedbackMessage = "已全网检索并匹配到高清封面"
                                } else {
                                    feedbackMessage = "未能检索到封面，可手动输入图片网址"
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isSearching
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("全网智能匹配封面")
                    }

                    FilledTonalButton(
                        onClick = {
                            val clip = DesktopBrowserHelper.getClipboardText()
                            if (!clip.isNullOrBlank() && (clip.startsWith("http://") || clip.startsWith("https://"))) {
                                avatarInput = clip.trim()
                                feedbackMessage = "已粘贴剪贴板图片链接"
                            } else {
                                feedbackMessage = "剪贴板中未包含有效图片 URL"
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("粘贴图片链接")
                    }
                }

                if (feedbackMessage != null) {
                    Text(
                        text = feedbackMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Input URL field
                OutlinedTextField(
                    value = avatarInput,
                    onValueChange = { avatarInput = it },
                    label = { Text("封面图片网络地址 (URL)") },
                    singleLine = true,
                    trailingIcon = {
                        if (avatarInput.isNotBlank()) {
                            IconButton(onClick = { avatarInput = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "清空")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Dialog bottom actions
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
                        onConfirm(avatarInput.trim().ifBlank { null })
                        onDismiss()
                    }) {
                        Text("保存封面")
                    }
                }
            }
        }
    }
}
