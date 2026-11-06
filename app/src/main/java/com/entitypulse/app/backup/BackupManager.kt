package com.entitypulse.app.backup

import android.content.Context
import android.net.Uri
import com.entitypulse.app.data.AppDatabase
import com.entitypulse.app.data.model.EntityRecord
import com.entitypulse.app.data.model.PlatformType
import com.entitypulse.app.data.model.SourceChannelRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class BackupManager(
    private val context: Context,
    private val database: AppDatabase
) {
    private val entityDao = database.entityDao()
    private val channelDao = database.channelDao()

    suspend fun exportToJson(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject()
            root.put("version", 1)
            root.put("app", "EntityPulse")
            root.put("exportTime", System.currentTimeMillis())

            val entitiesArray = JSONArray()
            val entities = entityDao.getAll()

            for (entity in entities) {
                val entityObj = JSONObject().apply {
                    put("name", entity.name)
                    put("avatarUrl", entity.avatarUrl ?: "")
                    put("bio", entity.bio ?: "")
                    put("category", entity.category)
                    put("isVip", entity.isVip)
                    put("isNotifyEnabled", entity.isNotifyEnabled)
                }

                val channels = channelDao.getByEntityId(entity.id)
                val channelsArray = JSONArray()
                for (ch in channels) {
                    val chObj = JSONObject().apply {
                        put("platformType", ch.platformType.name)
                        put("identifier", ch.identifier)
                        put("resolvedFeedUrl", ch.resolvedFeedUrl)
                        put("title", ch.title)
                        put("isEnabled", ch.isEnabled)
                    }
                    channelsArray.put(chObj)
                }
                entityObj.put("channels", channelsArray)
                entitiesArray.put(entityObj)
            }

            root.put("entities", entitiesArray)

            context.contentResolver.openOutputStream(uri)?.use { os ->
                OutputStreamWriter(os).use { writer ->
                    writer.write(root.toString(2))
                }
            } ?: throw IllegalStateException("无法写入目标文件")
        }
    }

    suspend fun importFromJson(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val jsonStr = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
            } ?: throw IllegalStateException("无法读取选中的备份文件")

            val root = JSONObject(jsonStr)
            val entitiesArray = root.optJSONArray("entities") ?: return@runCatching 0

            var importedCount = 0
            for (i in 0 until entitiesArray.length()) {
                val eObj = entitiesArray.getJSONObject(i)
                val entityRecord = EntityRecord(
                    name = eObj.getString("name"),
                    avatarUrl = eObj.optString("avatarUrl").ifBlank { null },
                    bio = eObj.optString("bio").ifBlank { null },
                    category = eObj.optString("category", "默认"),
                    isVip = eObj.optBoolean("isVip", false),
                    isNotifyEnabled = eObj.optBoolean("isNotifyEnabled", true)
                )
                val entityId = entityDao.insert(entityRecord)

                val channelsArray = eObj.optJSONArray("channels")
                if (channelsArray != null) {
                    for (j in 0 until channelsArray.length()) {
                        val chObj = channelsArray.getJSONObject(j)
                        val channel = SourceChannelRecord(
                            entityId = entityId,
                            platformType = PlatformType.fromString(chObj.optString("platformType")),
                            identifier = chObj.optString("identifier"),
                            resolvedFeedUrl = chObj.getString("resolvedFeedUrl"),
                            title = chObj.optString("title", "订阅源"),
                            isEnabled = chObj.optBoolean("isEnabled", true)
                        )
                        channelDao.insert(channel)
                    }
                }
                importedCount++
            }
            importedCount
        }
    }

    suspend fun exportToOpml(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val sb = StringBuilder()
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            sb.append("<opml version=\"2.0\">\n")
            sb.append("  <head>\n")
            sb.append("    <title>EntityPulse Subscriptions</title>\n")
            sb.append("    <dateCreated>${System.currentTimeMillis()}</dateCreated>\n")
            sb.append("  </head>\n")
            sb.append("  <body>\n")

            val channels = channelDao.getAllEnabledChannels()
            for (ch in channels) {
                val entity = entityDao.getById(ch.entityId)
                val entityName = entity?.name ?: "Unknown"
                sb.append("    <outline text=\"${escapeXml(entityName)} - ${escapeXml(ch.title)}\" ")
                sb.append("title=\"${escapeXml(ch.title)}\" type=\"rss\" ")
                sb.append("xmlUrl=\"${escapeXml(ch.resolvedFeedUrl)}\"/>\n")
            }

            sb.append("  </body>\n")
            sb.append("</opml>")

            context.contentResolver.openOutputStream(uri)?.use { os ->
                OutputStreamWriter(os).use { writer ->
                    writer.write(sb.toString())
                }
            } ?: throw IllegalStateException("无法写入目标文件")
        }
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
