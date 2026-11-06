package com.entitypulse.app.network

import android.content.Context
import android.content.SharedPreferences

class NodeManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("rsshub_nodes", Context.MODE_PRIVATE)

    companion object {
        const val PREF_CUSTOM_NODE = "pref_custom_node"
        const val PREF_ACCESS_TOKEN = "pref_access_token"
        const val PREF_SYNC_INTERVAL_MIN = "pref_sync_interval_min"
        const val PREF_DND_ENABLED = "pref_dnd_enabled"
        const val PREF_DND_START_HOUR = "pref_dnd_start_hour"
        const val PREF_DND_END_HOUR = "pref_dnd_end_hour"

        val PUBLIC_MIRRORS = listOf(
            "https://rsshub.app",
            "https://rsshub.rssforever.com",
            "https://rss.owo.nz"
        )
    }

    var customNode: String
        get() = prefs.getString(PREF_CUSTOM_NODE, "") ?: ""
        set(value) = prefs.edit().putString(PREF_CUSTOM_NODE, value.trim()).apply()

    var accessToken: String
        get() = prefs.getString(PREF_ACCESS_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(PREF_ACCESS_TOKEN, value.trim()).apply()

    var syncIntervalMinutes: Int
        get() = prefs.getInt(PREF_SYNC_INTERVAL_MIN, 30)
        set(value) = prefs.edit().putInt(PREF_SYNC_INTERVAL_MIN, value).apply()

    var isDndEnabled: Boolean
        get() = prefs.getBoolean(PREF_DND_ENABLED, false)
        set(value) = prefs.edit().putBoolean(PREF_DND_ENABLED, value).apply()

    var dndStartHour: Int
        get() = prefs.getInt(PREF_DND_START_HOUR, 23)
        set(value) = prefs.edit().putInt(PREF_DND_START_HOUR, value).apply()

    var dndEndHour: Int
        get() = prefs.getInt(PREF_DND_END_HOUR, 8)
        set(value) = prefs.edit().putInt(PREF_DND_END_HOUR, value).apply()

    fun getActiveRssHubBaseUrl(): String {
        val custom = customNode.trimEnd('/')
        return if (custom.isNotBlank()) custom else PUBLIC_MIRRORS.first()
    }

    fun getAllCandidateNodes(): List<String> {
        val list = mutableListOf<String>()
        val custom = customNode.trimEnd('/')
        if (custom.isNotBlank()) {
            list.add(custom)
        }
        list.addAll(PUBLIC_MIRRORS)
        return list.distinct()
    }
}
