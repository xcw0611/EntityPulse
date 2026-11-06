package com.entitypulse.app.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.entitypulse.app.ui.theme.VipGold

@Composable
fun AddEntityDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, bio: String?, avatarUrl: String?, isVip: Boolean, category: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("游戏 / 创作者") }
    var isVip by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "添加关注主体", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "例如关注某个工作室（Team Cherry）或特定创作者。创建后可在其名下绑定 X/Twitter、YouTube、Steam 等各平台账号。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("主体名称 (例如: Team Cherry)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("简介描述 (选填)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = avatarUrl,
                    onValueChange = { avatarUrl = it },
                    label = { Text("头像图片 URL (选填)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("分类标签") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "VIP",
                            tint = if (isVip) VipGold else MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = "设为 VIP 强提醒主体", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "新动态到达时触发最高级别系统横幅与铃声",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = isVip,
                        onCheckedChange = { isVip = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name, bio, avatarUrl, isVip, category)
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
