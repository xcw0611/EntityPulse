package com.entitypulse.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.entitypulse.app.notification.NotificationHelper
import com.entitypulse.app.ui.MainViewModel
import com.entitypulse.app.ui.screens.MainScreen
import com.entitypulse.app.ui.theme.EntityPulseTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // 用户授权结果处理
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 请求 Android 13+ 运行时通知权限
        checkAndRequestNotificationPermission()

        val initialPostId = intent?.getLongExtra(NotificationHelper.EXTRA_POST_ID, -1L)?.takeIf { it > 0 }
        val initialEntityId = intent?.getLongExtra(NotificationHelper.EXTRA_ENTITY_ID, -1L)?.takeIf { it > 0 }

        setContent {
            EntityPulseTheme {
                MainScreen(
                    viewModel = viewModel,
                    initialPostId = initialPostId,
                    initialEntityId = initialEntityId
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(permission)
            }
        }
    }
}
