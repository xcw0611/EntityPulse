package com.entitypulse.app.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun EditAvatarDialog(
    entityName: String,
    currentAvatarUrl: String?,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
    onSearchImage: suspend (String) -> String?
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    var avatarInput by remember { mutableStateOf(currentAvatarUrl ?: "") }
    var isSearching by remember { mutableStateOf(false) }
    var searchFeedback by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "修改主体封面 / 头像", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. 头像预览区
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (avatarInput.isNotBlank()) {
                        AsyncImage(
                            model = avatarInput,
                            contentDescription = entityName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "暂无封面",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    if (isSearching) {
                        Surface(
                            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                // 2. 全网智能抓取与辅助按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            isSearching = true
                            searchFeedback = null
                            coroutineScope.launch {
                                val found = onSearchImage(entityName)
                                isSearching = false
                                if (!found.isNullOrBlank()) {
                                    avatarInput = found
                                    searchFeedback = "已自动匹配到官方高清封面！"
                                } else {
                                    searchFeedback = "全网未找到该主体的海报，请手动粘贴图片链接。"
                                }
                            }
                        },
                        enabled = !isSearching,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isSearching) "检索中..." else "全网检索封面")
                    }

                    OutlinedButton(
                        onClick = {
                            val clipText = clipboardManager.getText()?.text?.trim()
                            if (!clipText.isNullOrBlank() && (clipText.startsWith("http://") || clipText.startsWith("https://"))) {
                                avatarInput = clipText
                                searchFeedback = "已从剪贴板粘贴图片链接"
                            } else {
                                searchFeedback = "剪贴板未检测到有效的图片 URL"
                            }
                        }
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    }

                    OutlinedButton(
                        onClick = {
                            avatarInput = ""
                            searchFeedback = "已清空封面"
                        }
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "清空", modifier = Modifier.size(16.dp))
                    }
                }

                searchFeedback?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // 3. 自定义图片链接输入框
                OutlinedTextField(
                    value = avatarInput,
                    onValueChange = { avatarInput = it },
                    label = { Text("图片网络链接 (URL)") },
                    placeholder = { Text("https://example.com/cover.jpg") },
                    singleLine = false,
                    maxLines = 3,
                    trailingIcon = {
                        if (avatarInput.isNotBlank()) {
                            IconButton(onClick = { avatarInput = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "清除")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(avatarInput.trim().ifBlank { null })
                    onDismiss()
                }
            ) {
                Text("保存封面")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
