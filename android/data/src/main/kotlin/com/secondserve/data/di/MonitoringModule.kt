// android/data/src/main/kotlin/com/secondserve/data/di/MonitoringModule.kt
package com.secondserve.data.di

import com.secondserve.data.monitoring.MonitoringClient
import com.secondserve.data.remote.api.VpsApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MonitoringModule {

    @Provides
    @Singleton
    fun provideMonitoringClient(api: VpsApiService): MonitoringClient =
        MonitoringClient(api)
}
