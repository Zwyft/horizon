package com.coparenting.chronicle.horizon.di

import android.content.Context
import androidx.room.Room
import com.coparenting.chronicle.horizon.data.local.database.HorizonDatabase
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
    fun provideDatabase(@ApplicationContext context: Context): HorizonDatabase {
        return Room.databaseBuilder(
            context,
            HorizonDatabase::class.java,
            "horizon_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideMessageDao(db: HorizonDatabase) = db.messageDao()

    @Provides
    @Singleton
    fun provideDiaryDao(db: HorizonDatabase) = db.diaryDao()

    @Provides
    @Singleton
    fun provideContactDao(db: HorizonDatabase) = db.contactDao()

    @Provides
    @Singleton
    fun provideAnalyticsDao(db: HorizonDatabase) = db.analyticsDao()

    @Provides
    @Singleton
    fun provideManualJournalDao(db: HorizonDatabase) = db.manualJournalDao()
}
