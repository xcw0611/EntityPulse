package com.entitypulse.desktop.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object AppLogger {

    enum class Level { DEBUG, INFO, WARN, ERROR }

    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val level: Level,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null
    ) {
        fun formatted(): String {
            val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
            val dateStr = df.format(Date(timestamp))
            val base = "[$dateStr] [${level.name}] [$tag] $message"
            return if (throwable != null) {
                "$base\n${throwable.stackTraceToString()}"
            } else base
        }
    }

    private val logs = CopyOnWriteArrayList<LogEntry>()
    private const val MAX_LOGS = 1000

    fun d(tag: String, message: String) = log(Level.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(Level.INFO, tag, message)
    fun w(tag: String, message: String, throwable: Throwable? = null) = log(Level.WARN, tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log(Level.ERROR, tag, message, throwable)

    private fun log(level: Level, tag: String, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(level = level, tag = tag, message = message, throwable = throwable)
        println(entry.formatted())
        logs.add(entry)
        if (logs.size > MAX_LOGS) {
            logs.removeAt(0)
        }
    }

    fun getAllLogs(): List<LogEntry> = logs.toList()

    fun exportLogsAsString(): String {
        return logs.joinToString("\n") { it.formatted() }
    }

    fun clear() {
        logs.clear()
    }
}
