package com.zwyft.horizon.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.zwyft.horizon.data.dao.ContactDao
import com.zwyft.horizon.data.dao.JournalEntryDao
import com.zwyft.horizon.data.dao.MessageDao
import com.zwyft.horizon.data.dao.SettingDao
import com.zwyft.horizon.data.entity.ContactEntity
import com.zwyft.horizon.data.entity.JournalEntryEntity
import com.zwyft.horizon.data.entity.MessageEntity
import com.zwyft.horizon.data.entity.SettingEntity
import java.util.Date

@Database(
    entities = [
        MessageEntity::class,
        ContactEntity::class,
        JournalEntryEntity::class,
        SettingEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class HorizonDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun contactDao(): ContactDao
    abstract fun journalEntryDao(): JournalEntryDao
    abstract fun settingDao(): SettingDao

    companion object {
        private const val DB_NAME = "horizon.db"

        @Volatile
        private var INSTANCE: HorizonDatabase? = null

        fun getInstance(context: Context): HorizonDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HorizonDatabase::class.java,
                    DB_NAME
                )
                    .setQueryCallback({ sql, args ->
                        // Log queries in debug builds
                    }, { /* executor */ })
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

/**
 * Room TypeConverters for Date <-> Long persistence.
 */
class Converters {
    @androidx.room.TypeConverter
    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }

    @androidx.room.TypeConverter
    fun dateToTimestamp(date: Date?): Long? = date?.time
}
