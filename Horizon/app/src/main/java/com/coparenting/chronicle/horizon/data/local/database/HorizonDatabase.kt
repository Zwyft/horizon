package com.coparenting.chronicle.horizon.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.coparenting.chronicle.horizon.data.local.database.dao.AnalyticsDao
import com.coparenting.chronicle.horizon.data.local.database.dao.ContactDao
import com.coparenting.chronicle.horizon.data.local.database.dao.DiaryDao
import com.coparenting.chronicle.horizon.data.local.database.dao.MessageDao
import com.coparenting.chronicle.horizon.data.local.database.entity.AnalyticsEntity
import com.coparenting.chronicle.horizon.data.local.database.entity.ContactEntity
import com.coparenting.chronicle.horizon.data.local.database.entity.DiaryEntryEntity
import com.coparenting.chronicle.horizon.data.local.database.entity.MessageEntity

@Database(
    entities = [
        MessageEntity::class,
        DiaryEntryEntity::class,
        ContactEntity::class,
        AnalyticsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class HorizonDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun diaryDao(): DiaryDao
    abstract fun contactDao(): ContactDao
    abstract fun analyticsDao(): AnalyticsDao
}
