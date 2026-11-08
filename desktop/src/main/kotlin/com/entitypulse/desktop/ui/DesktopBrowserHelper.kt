package com.entitypulse.desktop.ui

import com.entitypulse.desktop.util.AppLogger
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.net.URI

object DesktopBrowserHelper {

    /**
     * 在用户 Windows 系统默认浏览器 (Chrome/Edge/Firefox) 中打开指定 URL
     */
    fun openInBrowser(url: String): Boolean {
        return try {
            val trimmed = url.trim()
            if (trimmed.isBlank()) return false
            var cleanUrl = trimmed
            if (cleanUrl.startsWith("/twitter/user/")) {
                cleanUrl = "https://x.com/${cleanUrl.removePrefix("/twitter/user/").substringBefore('/')}"
            } else if (cleanUrl.startsWith("/")) {
                cleanUrl = "https://x.com$cleanUrl"
            } else if (!cleanUrl.startsWith("http://", ignoreCase = true) && !cleanUrl.startsWith("https://", ignoreCase = true)) {
                cleanUrl = "https://$cleanUrl"
            }
            cleanUrl = cleanUrl.replace("https://twitter.com/", "https://x.com/")
            val uri = URI(cleanUrl)
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri)
                AppLogger.i("DesktopBrowserHelper", "已在系统默认浏览器中打开: $uri")
                true
            } else {
                // Windows cmd / start fallback
                val runtime = Runtime.getRuntime()
                runtime.exec(arrayOf("rundll32", "url.dll,FileProtocolHandler", uri.toString()))
                true
            }
        } catch (e: Exception) {
            AppLogger.e("DesktopBrowserHelper", "调用系统浏览器失败: ${e.message}", e)
            false
        }
    }

    /**
     * 读取当前 Windows 剪贴板中的纯文本内容
     */
    fun getClipboardText(): String? {
        return try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                clipboard.getData(DataFlavor.stringFlavor) as? String
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 将文本写入 Windows 系统剪贴板
     */
    fun setClipboardText(text: String) {
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val selection = StringSelection(text)
            clipboard.setContents(selection, selection)
        } catch (e: Exception) {
            AppLogger.w("DesktopBrowserHelper", "写入剪贴板失败: ${e.message}")
        }
    }
}
