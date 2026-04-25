package com.coparenting.chronicle.horizon.di

import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import com.coparenting.chronicle.horizon.worker.DailyScanWorker
import com.coparenting.chronicle.horizon.worker.DailyScanWorker.Factory as DailyScanWorkerFactory
import com.coparenting.chronicle.horizon.worker.NotificationWorker
import com.coparenting.chronicle.horizon.worker.NotificationWorker.Factory as NotificationWorkerFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.worker.HiltWorkerFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkerModule {

    @Binds
    abstract fun bindWorkerFactory(hiltWorkerFactory: HiltWorkerFactory): WorkerFactory

    // Worker factories for assisted injection
    @BindsInstance
    abstract fun bindDailyScanWorkerFactory(factory: DailyScanWorkerFactory): DailyScanWorker.AssistedFactory

    @BindsInstance
    abstract fun bindNotificationWorkerFactory(factory: NotificationWorkerFactory): NotificationWorker.AssistedFactory
}
