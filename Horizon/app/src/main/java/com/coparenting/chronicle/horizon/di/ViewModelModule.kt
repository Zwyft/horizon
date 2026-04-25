package com.coparenting.chronicle.horizon.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.ViewModelProvider
import com.coparenting.chronicle.horizon.presentation.viewmodel.AnalyticsViewModel
import dagger.Binds
import dagger.BindsInstance
import dagger.Module
import dagger.Provides
import dagger.hilt.android.lifecycle.HiltViewModelFactory
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ViewModelModule {

    @Binds
    @IntoMap
    @ViewModelKey(AnalyticsViewModel::class)
    abstract fun bindAnalyticsViewModel(viewModel: AnalyticsViewModel): ViewModel

    @Binds
    abstract fun bindViewModelFactory(factory: HiltViewModelFactory): ViewModelProvider.Factory
}

// Key class for ViewModel mapping
@MapKey
@MustBeDocumented
@Target([AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER])
@Retention(AnnotationRetention.RUNTIME)
annotation class ViewModelKey(val value: KClass<out ViewModel>)
