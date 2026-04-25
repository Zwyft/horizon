package com.coparenting.chronicle.horizon.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.coparenting.chronicle.horizon.data.local.database.dao.AnalyticsDao
import com.coparenting.chronicle.horizon.data.local.database.dao.ContactDao
import com.coparenting.chronicle.horizon.data.local.database.dao.DiaryDao
import com.coparenting.chronicle.horizon.data.local.database.dao.ManualJournalDao
import com.coparenting.chronicle.horizon.data.local.database.dao.MessageDao
import com.coparenting.chronicle.horizon.data.local.database.entity.AnalyticsEntity
import com.coparenting.chronicle.horizon.domain.model.Contact
import com.coparenting.chronicle.horizon.domain.model.DiaryEntry
import com.coparenting.chronicle.horizon.domain.model.ManualJournalEntry
import com.coparenting.chronicle.horizon.domain.model.Message

@Database(
    entities = [
        Message::class,
        DiaryEntry::class,
        Contact::class,
        AnalyticsEntity::class,
        ManualJournalEntry::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class HorizonDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun diaryDao(): DiaryDao
    abstract fun contactDao(): ContactDao
    abstract fun analyticsDao(): AnalyticsDao
    abstract fun manualJournalDao(): ManualJournalDao
}
