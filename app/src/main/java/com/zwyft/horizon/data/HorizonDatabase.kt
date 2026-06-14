package com.zwyft.horizon.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.zwyft.horizon.data.dao.BatchProgressDao
import com.zwyft.horizon.data.dao.ContactDao
import com.zwyft.horizon.data.dao.JournalEntryDao
import com.zwyft.horizon.data.dao.MessageAttachmentDao
import com.zwyft.horizon.data.dao.MessageDao
import com.zwyft.horizon.data.dao.SettingDao
import com.zwyft.horizon.data.entity.BatchProgressEntity
import com.zwyft.horizon.data.entity.ContactEntity
import com.zwyft.horizon.data.entity.JournalEntryEntity
import com.zwyft.horizon.data.entity.MessageAttachmentEntity
import com.zwyft.horizon.data.entity.MessageEntity
import com.zwyft.horizon.data.entity.SettingEntity
import java.util.Date

@Database(
    entities = [
        MessageEntity::class,
        ContactEntity::class,
        JournalEntryEntity::class,
        SettingEntity::class,
        MessageAttachmentEntity::class,
        BatchProgressEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class HorizonDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao
    abstract fun contactDao(): ContactDao
    abstract fun journalEntryDao(): JournalEntryDao
    abstract fun settingDao(): SettingDao
    abstract fun messageAttachmentDao(): MessageAttachmentDao
    abstract fun batchProgressDao(): BatchProgressDao

    companion object {
        private const val DB_NAME = "horizon.db"

        @Volatile
        private var INSTANCE: HorizonDatabase? = null

        /**
         * Migration 1→2: Add `userNotes TEXT` and `userEdited INTEGER`
         * columns to journal_entries. These are nullable/optional, so
         * existing rows are untouched.
         */
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE journal_entries ADD COLUMN userNotes TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE journal_entries ADD COLUMN userEdited INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): HorizonDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HorizonDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .fallbackToDestructiveMigrationOnDowngrade()
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
