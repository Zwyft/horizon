package com.zwyft.horizon.di

import android.content.Context
import androidx.room.Room
import com.zwyft.horizon.data.HorizonDatabase
import com.zwyft.horizon.data.dao.ContactDao
import com.zwyft.horizon.data.dao.JournalEntryDao
import com.zwyft.horizon.data.dao.MessageDao
import com.zwyft.horizon.data.dao.SettingDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): HorizonDatabase =
        HorizonDatabase.getInstance(ctx)

    @Provides
    fun provideMessageDao(db: HorizonDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideContactDao(db: HorizonDatabase): ContactDao = db.contactDao()

    @Provides
    fun provideJournalEntryDao(db: HorizonDatabase): JournalEntryDao = db.journalEntryDao()

    @Provides
    fun provideSettingDao(db: HorizonDatabase): SettingDao = db.settingDao()
}
