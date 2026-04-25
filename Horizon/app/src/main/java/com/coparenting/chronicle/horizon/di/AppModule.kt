package com.coparenting.chronicle.horizon.di

import android.content.Context
import com.coparenting.chronicle.horizon.data.preferences.AppPreferences
import com.coparenting.chronicle.horizon.data.remote.sms.SmsDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideSmsDataSource(@ApplicationContext context: Context): SmsDataSource =
        SmsDataSource(context)

    @Provides @Singleton
    fun provideAppPreferences(@ApplicationContext context: Context): AppPreferences =
        AppPreferences(context)
}
