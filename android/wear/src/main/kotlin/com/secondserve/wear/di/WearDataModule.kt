package com.secondserve.wear.di

import com.secondserve.domain.event.DataLayerEventBus
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WearDataModule {

    @Provides
    @Singleton
    fun provideDataLayerEventBus(): DataLayerEventBus = DataLayerEventBus()
}
