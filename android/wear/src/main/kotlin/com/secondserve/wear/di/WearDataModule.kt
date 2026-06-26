package com.secondserve.wear.di

import android.content.Context
import androidx.room.Room
import com.secondserve.data.local.dao.SessionDao
import com.secondserve.data.local.dao.SyncQueueDao
import com.secondserve.data.local.db.SecondServeDatabase
import com.secondserve.data.monitoring.dto.MonitoringEventDto
import com.secondserve.data.monitoring.dto.MonitoringStatusDto
import com.secondserve.data.remote.api.GoogleAuthRequest
import com.secondserve.data.remote.api.HealthResponse
import com.secondserve.data.remote.api.TokenResponse
import com.secondserve.data.remote.api.VpsApiService
import com.secondserve.data.remote.api.dto.PendingNotificationResponse
import com.secondserve.data.remote.api.dto.ProfileDetailsRequest
import com.secondserve.data.remote.api.dto.ProfileDetailsResponse
import com.secondserve.data.remote.api.dto.ProfileSummaryDto
import com.secondserve.data.remote.api.dto.RankingEntryDto
import com.secondserve.data.remote.api.dto.RankingRequest
import com.secondserve.data.remote.api.dto.SyncPushRequest
import com.secondserve.data.remote.api.dto.SyncPushResponse
import com.secondserve.data.remote.api.dto.WorkAxesResponse
import com.secondserve.data.remote.api.dto.WorkAxisRequest
import com.secondserve.data.remote.api.dto.WorkAxisResponse
import com.secondserve.domain.event.DataLayerEventBus
import com.secondserve.domain.notification.NotificationScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for the wear app.
 *
 * The :data module is included transitively for DataLayerClient. It also brings phone-specific
 * components (DataLayerListener, HiltWorkers) whose Hilt entry points require SessionDao,
 * SyncQueueDao, SecondServeDatabase, and NotificationScheduler.
 *
 * These stubs satisfy the Hilt compile-time checks. They are never called at runtime
 * because the phone-side services and workers do not run on the watch.
 */
@Module
@InstallIn(SingletonComponent::class)
object WearDataModule {

    @Provides
    @Singleton
    fun provideDataLayerEventBus(): DataLayerEventBus = DataLayerEventBus()

    // --- Phone-component stubs — satisfy Hilt binding graph, never used at runtime on watch ---

    @Provides
    @Singleton
    fun provideSecondServeDatabase(@ApplicationContext context: Context): SecondServeDatabase =
        Room.inMemoryDatabaseBuilder(context, SecondServeDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    @Provides
    @Singleton
    fun provideSessionDao(db: SecondServeDatabase): SessionDao = db.sessionDao()

    @Provides
    @Singleton
    fun provideSyncQueueDao(db: SecondServeDatabase): SyncQueueDao = db.syncQueueDao()

    @Provides
    @Singleton
    fun provideNotificationScheduler(): NotificationScheduler = NoOpNotificationScheduler

    @Provides
    @Singleton
    fun provideVpsApiService(): VpsApiService = NoOpVpsApiService
}

private object NoOpNotificationScheduler : NotificationScheduler {
    override fun scheduleDaily() {}
    override fun scheduleEvery2Days() {}
    override fun scheduleWeekly() {}
    override fun cancel() {}
    override fun schedulePreMatchReminder(sessionId: Long, triggerAtMs: Long) {}
    override fun cancelPreMatchReminder(sessionId: Long) {}
}

// Stub — satisfies Hilt binding graph for MonitoringModule; never called at runtime on watch.
private object NoOpVpsApiService : VpsApiService {
    override suspend fun initAuth(request: GoogleAuthRequest): TokenResponse = error("N/A on Wear OS")
    override suspend fun health(): HealthResponse = error("N/A on Wear OS")
    override suspend fun getProfile(): ProfileSummaryDto = error("N/A on Wear OS")
    override suspend fun saveRanking(request: RankingRequest): RankingEntryDto = error("N/A on Wear OS")
    override suspend fun updateProfileDetails(request: ProfileDetailsRequest): ProfileDetailsResponse = error("N/A on Wear OS")
    override suspend fun getWorkAxes(): WorkAxesResponse = error("N/A on Wear OS")
    override suspend fun createWorkAxis(request: WorkAxisRequest): WorkAxisResponse = error("N/A on Wear OS")
    override suspend fun updateWorkAxis(id: Long, request: WorkAxisRequest): WorkAxisResponse = error("N/A on Wear OS")
    override suspend fun deleteWorkAxis(id: Long) = error("N/A on Wear OS")
    override suspend fun syncPush(request: SyncPushRequest): SyncPushResponse = error("N/A on Wear OS")
    override suspend fun getPendingNotification(sessionId: Long): PendingNotificationResponse = error("N/A on Wear OS")
    override suspend fun sendMonitoringEvent(event: MonitoringEventDto): MonitoringStatusDto = error("N/A on Wear OS")
    override suspend fun sendMonitoringEventBatch(events: List<MonitoringEventDto>): MonitoringStatusDto = error("N/A on Wear OS")
}
