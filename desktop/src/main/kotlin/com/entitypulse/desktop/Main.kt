package com.entitypulse.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.entitypulse.desktop.ui.MainDesktopView
import com.entitypulse.desktop.ui.theme.EntityPulseDesktopTheme
import com.entitypulse.desktop.util.AppLogger
import java.awt.Dimension

fun main() = application {
    AppLogger.i("DesktopApp", "启动 EntityPulse PC 桌面端 (Compose Multiplatform for Desktop)...")

    val windowState = WindowState(width = 1360.dp, height = 860.dp)

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "EntityPulse - 全网社媒与动态聚合 (PC桌面端)"
    ) {
        window.minimumSize = Dimension(1000, 650)
        EntityPulseDesktopTheme {
            MainDesktopView()
        }
    }
}
