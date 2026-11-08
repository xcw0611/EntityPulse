package com.entitypulse.desktop.data

import com.entitypulse.desktop.data.model.*
import com.entitypulse.desktop.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement

class DatabaseManager {

    private val dbPath: String
    private val connection: Connection
    private val _entitiesState = MutableStateFlow<List<EntityWithStats>>(emptyList())
    val entitiesState: StateFlow<List<EntityWithStats>> = _entitiesState.asStateFlow()

    private val _refreshTrigger = MutableStateFlow(0L)

    init {
        val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
        val dir = File(appData, "EntityPulse")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val file = File(dir, "data.db")
        dbPath = file.absolutePath
        AppLogger.i("DatabaseManager", "初始化本地 SQLite 数据库: $dbPath")

        connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
        createTablesIfNotExist()
        refreshEntitiesInternal()
        if (_entitiesState.value.isEmpty()) {
            seedPopularEntities()
        }
    }

    private fun seedPopularEntities() {
        AppLogger.i("DatabaseManager", "数据库为空，初始化精选关注主体...")
        for (pop in EntityCatalog.POPULAR_ENTITIES) {
            val sql = "INSERT INTO entities (name, avatarUrl, bio, category, isVip, isNotifyEnabled, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
            var newId = 0L
            connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { pstmt ->
                pstmt.setString(1, pop.name)
                pstmt.setString(2, pop.avatarUrl)
                pstmt.setString(3, pop.bio)
                pstmt.setString(4, pop.category)
                pstmt.setInt(5, if (pop.isVip) 1 else 0)
                pstmt.setInt(6, 1)
                val now = System.currentTimeMillis()
                pstmt.setLong(7, now)
                pstmt.setLong(8, now)
                pstmt.executeUpdate()
                pstmt.generatedKeys.use { rs ->
                    if (rs.next()) newId = rs.getLong(1)
                }
            }
            if (newId > 0) {
                for (ch in pop.channels) {
                    val chSql = "INSERT INTO channels (entityId, platformType, identifier, resolvedFeedUrl, title, isEnabled, lastSyncTime, createdAt) VALUES (?, ?, ?, ?, ?, 1, 0, ?)"
                    connection.prepareStatement(chSql).use { pstmt ->
                        pstmt.setLong(1, newId)
                        pstmt.setString(2, ch.platformType.name)
                        pstmt.setString(3, ch.identifier)
                        pstmt.setString(4, ch.feedPathOrUrl)
                        pstmt.setString(5, ch.title)
                        pstmt.setLong(6, System.currentTimeMillis())
                        pstmt.executeUpdate()
                    }
                }
            }
        }
        refreshEntitiesInternal()
    }

    private fun createTablesIfNotExist() {
        connection.createStatement().use { stmt ->
            // 1. Entities
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS entities (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    avatarUrl TEXT,
                    bio TEXT,
                    category TEXT DEFAULT '默认',
                    isVip INTEGER DEFAULT 0,
                    isNotifyEnabled INTEGER DEFAULT 1,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                );
            """.trimIndent())

            // 2. Channels
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS channels (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    entityId INTEGER NOT NULL,
                    platformType TEXT NOT NULL,
                    identifier TEXT NOT NULL,
                    resolvedFeedUrl TEXT NOT NULL,
                    title TEXT NOT NULL,
                    isEnabled INTEGER DEFAULT 1,
                    lastSyncTime INTEGER DEFAULT 0,
                    lastError TEXT,
                    createdAt INTEGER NOT NULL,
                    FOREIGN KEY(entityId) REFERENCES entities(id) ON DELETE CASCADE
                );
            """.trimIndent())

            // 3. Feed Items
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS feed_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    entityId INTEGER NOT NULL,
                    channelId INTEGER NOT NULL,
                    guid TEXT UNIQUE,
                    title TEXT NOT NULL,
                    contentPlainText TEXT NOT NULL,
                    contentHtml TEXT,
                    author TEXT,
                    publishedAt INTEGER NOT NULL,
                    originalUrl TEXT,
                    thumbnailUrl TEXT,
                    mediaUrlsJson TEXT,
                    isRead INTEGER DEFAULT 0,
                    isBookmarked INTEGER DEFAULT 0,
                    fetchedAt INTEGER NOT NULL,
                    FOREIGN KEY(entityId) REFERENCES entities(id) ON DELETE CASCADE,
                    FOREIGN KEY(channelId) REFERENCES channels(id) ON DELETE CASCADE
                );
            """.trimIndent())
        }
    }

    private fun notifyDataChanged() {
        _refreshTrigger.value = System.currentTimeMillis()
        refreshEntitiesInternal()
    }

    private fun refreshEntitiesInternal() {
        val sql = """
            SELECT e.id, e.name, e.avatarUrl, e.bio, e.category, e.isVip, e.isNotifyEnabled, e.createdAt, e.updatedAt,
                COALESCE(SUM(CASE WHEN f.isRead = 0 THEN 1 ELSE 0 END), 0) AS unreadCount,
                (SELECT f2.title FROM feed_items f2 WHERE f2.entityId = e.id ORDER BY f2.publishedAt DESC LIMIT 1) AS latestFeedTitle,
                (SELECT f3.publishedAt FROM feed_items f3 WHERE f3.entityId = e.id ORDER BY f3.publishedAt DESC LIMIT 1) AS latestFeedPublishedAt
            FROM entities e
            LEFT JOIN feed_items f ON e.id = f.entityId
            GROUP BY e.id
            ORDER BY e.isVip DESC, e.updatedAt DESC
        """.trimIndent()

        val list = mutableListOf<EntityWithStats>()
        connection.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                while (rs.next()) {
                    val record = EntityRecord(
                        id = rs.getLong("id"),
                        name = rs.getString("name"),
                        avatarUrl = rs.getString("avatarUrl"),
                        bio = rs.getString("bio"),
                        category = rs.getString("category") ?: "默认",
                        isVip = rs.getInt("isVip") == 1,
                        isNotifyEnabled = rs.getInt("isNotifyEnabled") == 1,
                        createdAt = rs.getLong("createdAt"),
                        updatedAt = rs.getLong("updatedAt")
                    )
                    val unread = rs.getInt("unreadCount")
                    val latestTitle = rs.getString("latestFeedTitle")
                    val latestTime = rs.getLong("latestFeedPublishedAt")
                    list.add(EntityWithStats(record, unread, latestTitle, if (latestTime > 0) latestTime else null))
                }
            }
        }
        _entitiesState.value = list
    }

    // --- Entity Operations ---
    suspend fun createEntity(
        name: String,
        avatarUrl: String? = null,
        bio: String? = null,
        category: String = "默认",
        isVip: Boolean = false,
        isNotifyEnabled: Boolean = true
    ): Long = withContext(Dispatchers.IO) {
        val sql = "INSERT INTO entities (name, avatarUrl, bio, category, isVip, isNotifyEnabled, createdAt, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        var newId: Long = 0
        connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { pstmt ->
            pstmt.setString(1, name)
            pstmt.setString(2, avatarUrl)
            pstmt.setString(3, bio)
            pstmt.setString(4, category)
            pstmt.setInt(5, if (isVip) 1 else 0)
            pstmt.setInt(6, if (isNotifyEnabled) 1 else 0)
            val now = System.currentTimeMillis()
            pstmt.setLong(7, now)
            pstmt.setLong(8, now)
            pstmt.executeUpdate()
            pstmt.generatedKeys.use { rs ->
                if (rs.next()) newId = rs.getLong(1)
            }
        }
        notifyDataChanged()
        newId
    }

    suspend fun updateEntityAvatar(id: Long, avatarUrl: String?) = withContext(Dispatchers.IO) {
        val sql = "UPDATE entities SET avatarUrl = ?, updatedAt = ? WHERE id = ?"
        connection.prepareStatement(sql).use { pstmt ->
            pstmt.setString(1, avatarUrl)
            pstmt.setLong(2, System.currentTimeMillis())
            pstmt.setLong(3, id)
            pstmt.executeUpdate()
        }
        notifyDataChanged()
    }

    suspend fun toggleEntityVip(id: Long, isVip: Boolean) = withContext(Dispatchers.IO) {
        val sql = "UPDATE entities SET isVip = ?, updatedAt = ? WHERE id = ?"
        connection.prepareStatement(sql).use { pstmt ->
            pstmt.setInt(1, if (isVip) 1 else 0)
            pstmt.setLong(2, System.currentTimeMillis())
            pstmt.setLong(3, id)
            pstmt.executeUpdate()
        }
        notifyDataChanged()
    }

    suspend fun toggleEntityNotify(id: Long, isNotify: Boolean) = withContext(Dispatchers.IO) {
        val sql = "UPDATE entities SET isNotifyEnabled = ?, updatedAt = ? WHERE id = ?"
        connection.prepareStatement(sql).use { pstmt ->
            pstmt.setInt(1, if (isNotify) 1 else 0)
            pstmt.setLong(2, System.currentTimeMillis())
            pstmt.setLong(3, id)
            pstmt.executeUpdate()
        }
        notifyDataChanged()
    }

    suspend fun deleteEntity(id: Long) = withContext(Dispatchers.IO) {
        connection.prepareStatement("DELETE FROM feed_items WHERE entityId = ?").use {
            it.setLong(1, id)
            it.executeUpdate()
        }
        connection.prepareStatement("DELETE FROM channels WHERE entityId = ?").use {
            it.setLong(1, id)
            it.executeUpdate()
        }
        connection.prepareStatement("DELETE FROM entities WHERE id = ?").use {
            it.setLong(1, id)
            it.executeUpdate()
        }
        notifyDataChanged()
    }

    // --- Channel Operations ---
    suspend fun getChannelsForEntity(entityId: Long): List<SourceChannelRecord> = withContext(Dispatchers.IO) {
        val sql = "SELECT * FROM channels WHERE entityId = ? ORDER BY id ASC"
        val list = mutableListOf<SourceChannelRecord>()
        connection.prepareStatement(sql).use { pstmt ->
            pstmt.setLong(1, entityId)
            pstmt.executeQuery().use { rs ->
                while (rs.next()) {
                    list.add(
                        SourceChannelRecord(
                            id = rs.getLong("id"),
                            entityId = rs.getLong("entityId"),
                            platformType = PlatformType.valueOf(rs.getString("platformType")),
                            identifier = rs.getString("identifier"),
                            resolvedFeedUrl = rs.getString("resolvedFeedUrl"),
                            title = rs.getString("title"),
                            isEnabled = rs.getInt("isEnabled") == 1,
                            lastSyncTime = rs.getLong("lastSyncTime"),
                            lastError = rs.getString("lastError"),
                            createdAt = rs.getLong("createdAt")
                        )
                    )
                }
            }
        }
        list
    }

    suspend fun getAllEnabledChannels(): List<SourceChannelRecord> = withContext(Dispatchers.IO) {
        val sql = "SELECT * FROM channels WHERE isEnabled = 1 ORDER BY id ASC"
        val list = mutableListOf<SourceChannelRecord>()
        connection.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                while (rs.next()) {
                    list.add(
                        SourceChannelRecord(
                            id = rs.getLong("id"),
                            entityId = rs.getLong("entityId"),
                            platformType = PlatformType.valueOf(rs.getString("platformType")),
                            identifier = rs.getString("identifier"),
                            resolvedFeedUrl = rs.getString("resolvedFeedUrl"),
                            title = rs.getString("title"),
                            isEnabled = rs.getInt("isEnabled") == 1,
                            lastSyncTime = rs.getLong("lastSyncTime"),
                            lastError = rs.getString("lastError"),
                            createdAt = rs.getLong("createdAt")
                        )
                    )
                }
            }
        }
        list
    }

    suspend fun addChannel(
        entityId: Long,
        platformType: PlatformType,
        identifier: String,
        resolvedFeedUrl: String,
        title: String
    ): Long = withContext(Dispatchers.IO) {
        val sql = "INSERT INTO channels (entityId, platformType, identifier, resolvedFeedUrl, title, isEnabled, lastSyncTime, createdAt) VALUES (?, ?, ?, ?, ?, 1, 0, ?)"
        var newId: Long = 0
        connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { pstmt ->
            pstmt.setLong(1, entityId)
            pstmt.setString(2, platformType.name)
            pstmt.setString(3, identifier)
            pstmt.setString(4, resolvedFeedUrl)
            pstmt.setString(5, title)
            pstmt.setLong(6, System.currentTimeMillis())
            pstmt.executeUpdate()
            pstmt.generatedKeys.use { rs ->
                if (rs.next()) newId = rs.getLong(1)
            }
        }
        notifyDataChanged()
        newId
    }

    suspend fun toggleChannel(channelId: Long, isEnabled: Boolean) = withContext(Dispatchers.IO) {
        val sql = "UPDATE channels SET isEnabled = ? WHERE id = ?"
        connection.prepareStatement(sql).use { pstmt ->
            pstmt.setInt(1, if (isEnabled) 1 else 0)
            pstmt.setLong(2, channelId)
            pstmt.executeUpdate()
        }
        notifyDataChanged()
    }

    suspend fun deleteChannel(channelId: Long) = withContext(Dispatchers.IO) {
        connection.prepareStatement("DELETE FROM feed_items WHERE channelId = ?").use {
            it.setLong(1, channelId)
            it.executeUpdate()
        }
        connection.prepareStatement("DELETE FROM channels WHERE id = ?").use {
            it.setLong(1, channelId)
            it.executeUpdate()
        }
        notifyDataChanged()
    }

    suspend fun updateChannelSyncStatus(channelId: Long, syncTime: Long, lastError: String?) = withContext(Dispatchers.IO) {
        val sql = "UPDATE channels SET lastSyncTime = ?, lastError = ? WHERE id = ?"
        connection.prepareStatement(sql).use { pstmt ->
            pstmt.setLong(1, syncTime)
            pstmt.setString(2, lastError)
            pstmt.setLong(3, channelId)
            pstmt.executeUpdate()
        }
        notifyDataChanged()
    }

    // --- Feed Items Operations ---
    suspend fun getFeeds(entityId: Long? = null, limit: Int = 150): List<FeedItemWithMeta> = withContext(Dispatchers.IO) {
        val whereClause = if (entityId != null) "WHERE f.entityId = ?" else ""
        val sql = """
            SELECT f.*, e.name AS entityName, e.avatarUrl AS entityAvatar, c.platformType AS platformType, c.title AS channelTitle
            FROM feed_items f
            JOIN entities e ON f.entityId = e.id
            JOIN channels c ON f.channelId = c.id
            $whereClause
            ORDER BY f.publishedAt DESC
            LIMIT ?
        """.trimIndent()

        val list = mutableListOf<FeedItemWithMeta>()
        connection.prepareStatement(sql).use { pstmt ->
            var idx = 1
            if (entityId != null) {
                pstmt.setLong(idx++, entityId)
            }
            pstmt.setInt(idx, limit)
            pstmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val item = FeedItemRecord(
                        id = rs.getLong("id"),
                        entityId = rs.getLong("entityId"),
                        channelId = rs.getLong("channelId"),
                        guid = rs.getString("guid") ?: "",
                        title = rs.getString("title"),
                        contentPlainText = rs.getString("contentPlainText"),
                        contentHtml = rs.getString("contentHtml"),
                        author = rs.getString("author"),
                        publishedAt = rs.getLong("publishedAt"),
                        originalUrl = rs.getString("originalUrl"),
                        thumbnailUrl = rs.getString("thumbnailUrl"),
                        mediaUrlsJson = rs.getString("mediaUrlsJson"),
                        isRead = rs.getInt("isRead") == 1,
                        isBookmarked = rs.getInt("isBookmarked") == 1,
                        fetchedAt = rs.getLong("fetchedAt")
                    )
                    val pType = runCatching { PlatformType.valueOf(rs.getString("platformType")) }.getOrDefault(PlatformType.RSS_CUSTOM)
                    list.add(
                        FeedItemWithMeta(
                            item = item,
                            entityName = rs.getString("entityName") ?: "",
                            entityAvatar = rs.getString("entityAvatar"),
                            platformType = pType,
                            channelTitle = rs.getString("channelTitle") ?: ""
                        )
                    )
                }
            }
        }
        list
    }

    suspend fun insertIgnoreFeedItem(item: FeedItemRecord): Long = withContext(Dispatchers.IO) {
        val sql = """
            INSERT OR IGNORE INTO feed_items 
            (entityId, channelId, guid, title, contentPlainText, contentHtml, author, publishedAt, originalUrl, thumbnailUrl, mediaUrlsJson, isRead, isBookmarked, fetchedAt) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        var newId: Long = -1
        connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { pstmt ->
            pstmt.setLong(1, item.entityId)
            pstmt.setLong(2, item.channelId)
            pstmt.setString(3, item.guid)
            pstmt.setString(4, item.title)
            pstmt.setString(5, item.contentPlainText)
            pstmt.setString(6, item.contentHtml)
            pstmt.setString(7, item.author)
            pstmt.setLong(8, item.publishedAt)
            pstmt.setString(9, item.originalUrl)
            pstmt.setString(10, item.thumbnailUrl)
            pstmt.setString(11, item.mediaUrlsJson)
            pstmt.setInt(12, if (item.isRead) 1 else 0)
            pstmt.setInt(13, if (item.isBookmarked) 1 else 0)
            pstmt.setLong(14, item.fetchedAt)
            val affected = pstmt.executeUpdate()
            if (affected > 0) {
                pstmt.generatedKeys.use { rs ->
                    if (rs.next()) newId = rs.getLong(1)
                }
            }
        }
        if (newId != -1L) notifyDataChanged()
        newId
    }

    suspend fun markFeedRead(feedId: Long, isRead: Boolean) = withContext(Dispatchers.IO) {
        val sql = "UPDATE feed_items SET isRead = ? WHERE id = ?"
        connection.prepareStatement(sql).use { pstmt ->
            pstmt.setInt(1, if (isRead) 1 else 0)
            pstmt.setLong(2, feedId)
            pstmt.executeUpdate()
        }
        notifyDataChanged()
    }

    suspend fun markAllReadForEntity(entityId: Long) = withContext(Dispatchers.IO) {
        val sql = "UPDATE feed_items SET isRead = 1 WHERE entityId = ?"
        connection.prepareStatement(sql).use { pstmt ->
            pstmt.setLong(1, entityId)
            pstmt.executeUpdate()
        }
        notifyDataChanged()
    }

    suspend fun markAllRead() = withContext(Dispatchers.IO) {
        val sql = "UPDATE feed_items SET isRead = 1"
        connection.createStatement().use { stmt ->
            stmt.executeUpdate(sql)
        }
        notifyDataChanged()
    }

    suspend fun toggleBookmark(feedId: Long, isBookmarked: Boolean) = withContext(Dispatchers.IO) {
        val sql = "UPDATE feed_items SET isBookmarked = ? WHERE id = ?"
        connection.prepareStatement(sql).use { pstmt ->
            pstmt.setInt(1, if (isBookmarked) 1 else 0)
            pstmt.setLong(2, feedId)
            pstmt.executeUpdate()
        }
        notifyDataChanged()
    }
}
