package com.coparenting.chronicle.horizon.di

import android.content.Context
import androidx.room.Room
import com.coparenting.chronicle.horizon.data.local.database.HorizonDatabase
import com.google.android.gms.tasks.Tasks
import dagger.BindsInstance
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
    fun provideMessageDao(database: HorizonDatabase): com.coparenting.chronicle.horizon.data.local.database.dao.MessageDao {
        return database.messageDao()
    }

    @Provides
    @Singleton
    fun provideDiaryDao(database: HorizonDatabase): com.coparenting.chronicle.horizon.data.local.database.dao.DiaryDao {
        return database.diaryDao()
    }

    @Provides
    @Singleton
    fun provideContactDao(database: HorizonDatabase): com.coparenting.chronicle.horizon.data.local.database.dao.ContactDao {
        return database.contactDao()
    }

    @Provides
    @Singleton
    fun provideAnalyticsDao(database: HorizonDatabase): com.coparenting.chronicle.horizon.data.local.database.dao.AnalyticsDao {
        return database.analyticsDao()
    }
}
