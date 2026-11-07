package com.entitypulse.app.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

object AppLogger {

    private const val TAG = "EntityPulse"
    private const val MAX_MEMORY_LOGS = 250
    private val memoryLogs = ConcurrentLinkedQueue<String>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    @Volatile
    private var logFile: File? = null

    fun init(context: Context) {
        try {
            val logDir = File(context.getExternalFilesDir(null), "logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            val target = File(logDir, "sync_debug.log")
            logFile = target
            i(TAG, "诊断日志系统已就绪，文件位置: ${target.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "初始化日志文件失败", e)
        }
    }

    fun getLogFilePath(): String {
        return logFile?.absolutePath ?: "内部存储/Android/data/com.entitypulse.app/files/logs/sync_debug.log"
    }

    fun d(tag: String, message: String) {
        log("DEBUG", tag, message, null)
        Log.d(tag, message)
    }

    fun i(tag: String, message: String) {
        log("INFO", tag, message, null)
        Log.i(tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log("WARN", tag, message, throwable)
        Log.w(tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log("ERROR", tag, message, throwable)
        Log.e(tag, message, throwable)
    }

    private fun log(level: String, tag: String, message: String, throwable: Throwable?) {
        val time = dateFormat.format(Date())
        val sb = StringBuilder()
        sb.append("[$time] [$level] [$tag] ").append(message)
        if (throwable != null) {
            sb.append("\n  异常类型: ").append(throwable.javaClass.name).append(": ").append(throwable.localizedMessage)
            val stack = throwable.stackTraceToString()
            sb.append("\n  堆栈信息: ").append(stack.take(600))
        }
        val entry = sb.toString()

        memoryLogs.add(entry)
        while (memoryLogs.size > MAX_MEMORY_LOGS) {
            memoryLogs.poll()
        }

        val file = logFile ?: return
        try {
            synchronized(this) {
                // 若单个日志文件超过 2MB，进行回滚清空
                if (file.exists() && file.length() > 2 * 1024 * 1024) {
                    file.delete()
                }
                FileWriter(file, true).use { fw ->
                    fw.write(entry)
                    fw.write("\n")
                }
            }
        } catch (_: Exception) {}
    }

    fun getRecentLogsText(): String {
        return if (memoryLogs.isEmpty()) {
            "当前暂无运行时诊断日志记录。\n您可返回首页下拉刷新或在主体详情页触发同步，系统将在此实时记录详细网络链路、HTTP状态与报错堆栈。"
        } else {
            memoryLogs.joinToString("\n\n")
        }
    }

    fun clearLogs() {
        memoryLogs.clear()
        try {
            logFile?.delete()
        } catch (_: Exception) {}
    }
}
