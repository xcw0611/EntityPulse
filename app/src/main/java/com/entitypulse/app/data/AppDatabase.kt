package com.entitypulse.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.entitypulse.app.data.dao.ChannelDao
import com.entitypulse.app.data.dao.EntityDao
import com.entitypulse.app.data.dao.FeedDao
import com.entitypulse.app.data.model.EntityRecord
import com.entitypulse.app.data.model.FeedItemRecord
import com.entitypulse.app.data.model.PlatformType
import com.entitypulse.app.data.model.SourceChannelRecord

class Converters {
    @androidx.room.TypeConverter
    fun fromPlatformType(value: PlatformType): String = value.name

    @androidx.room.TypeConverter
    fun toPlatformType(value: String): PlatformType = PlatformType.fromString(value)
}

@Database(
    entities = [
        EntityRecord::class,
        SourceChannelRecord::class,
        FeedItemRecord::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun entityDao(): EntityDao
    abstract fun channelDao(): ChannelDao
    abstract fun feedDao(): FeedDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "entity_pulse.db"
                ).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
