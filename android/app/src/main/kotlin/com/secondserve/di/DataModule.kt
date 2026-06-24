package com.secondserve.di

import android.content.Context
import androidx.room.Room
import com.secondserve.core.ai.InferenceEngine
import com.secondserve.core.ai.di.VpsMistralEngine
import com.secondserve.data.local.PlayerDataStore
import com.secondserve.data.local.dao.AxisSuggestionDao
import com.secondserve.data.local.dao.CoachingAnalysisDao
import com.secondserve.data.local.dao.CoachingCacheDao
import com.secondserve.data.local.dao.CoachingSynthesisDao
import com.secondserve.data.local.dao.PlayerProfileDao
import com.secondserve.data.local.dao.SessionDao
import com.secondserve.data.local.dao.SyncQueueDao
import com.secondserve.data.local.dao.WorkAxisDao
import com.secondserve.data.local.db.SecondServeDatabase
import com.secondserve.data.remote.api.VpsApiService
import com.secondserve.data.repository.PlayerProfileRepositoryImpl
import com.secondserve.data.repository.WorkAxisRepositoryImpl
import com.secondserve.data.repository.NotificationRepositoryImpl
import com.secondserve.data.worker.AnalysisSchedulerImpl
import com.secondserve.data.worker.NotificationSchedulerImpl
import com.secondserve.data.worker.SynthesisSchedulerImpl
import com.secondserve.data.worker.SyncSchedulerImpl
import com.secondserve.domain.analysis.AnalysisScheduler
import com.secondserve.domain.notification.NotificationScheduler
import com.secondserve.domain.synthesis.SynthesisScheduler
import com.secondserve.domain.event.DataLayerEventBus
import com.secondserve.domain.repository.NotificationRepository
import com.secondserve.domain.repository.PlayerProfileRepository
import com.secondserve.domain.repository.WorkAxisRepository
import com.secondserve.domain.sync.SyncScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideSecondServeDatabase(@ApplicationContext context: Context): SecondServeDatabase =
        Room.databaseBuilder(
            context,
            SecondServeDatabase::class.java,
            SecondServeDatabase.DB_NAME
        )
        .addMigrations(
            SecondServeDatabase.MIGRATION_1_2,
            SecondServeDatabase.MIGRATION_2_3,
            SecondServeDatabase.MIGRATION_3_4,
            SecondServeDatabase.MIGRATION_4_5,
            SecondServeDatabase.MIGRATION_5_6,
            SecondServeDatabase.MIGRATION_6_7,
            SecondServeDatabase.MIGRATION_7_8,
            SecondServeDatabase.MIGRATION_8_9,
            SecondServeDatabase.MIGRATION_9_10,
            SecondServeDatabase.MIGRATION_10_11
        )
        .build()

    @Provides
    @Singleton
    fun providePlayerProfileDao(db: SecondServeDatabase): PlayerProfileDao =
        db.playerProfileDao()

    @Provides
    @Singleton
    fun provideWorkAxisDao(db: SecondServeDatabase): WorkAxisDao =
        db.workAxisDao()

    @Provides
    @Singleton
    fun provideSessionDao(db: SecondServeDatabase): SessionDao =
        db.sessionDao()

    @Provides
    @Singleton
    fun provideSyncQueueDao(db: SecondServeDatabase): SyncQueueDao =
        db.syncQueueDao()

    @Provides
    @Singleton
    fun provideCoachingCacheDao(db: SecondServeDatabase): CoachingCacheDao =
        db.coachingCacheDao()

    @Provides
    @Singleton
    fun provideCoachingAnalysisDao(db: SecondServeDatabase): CoachingAnalysisDao =
        db.coachingAnalysisDao()

    @Provides
    @Singleton
    fun provideCoachingSynthesisDao(db: SecondServeDatabase): CoachingSynthesisDao =
        db.coachingSynthesisDao()

    @Provides
    @Singleton
    fun provideAxisSuggestionDao(db: SecondServeDatabase): AxisSuggestionDao =
        db.axisSuggestionDao()

    @Provides
    @Singleton
    fun provideDataLayerEventBus(): DataLayerEventBus = DataLayerEventBus()

    @Provides
    @Singleton
    fun provideSyncScheduler(@ApplicationContext context: Context): SyncScheduler =
        SyncSchedulerImpl(context)

    @Provides
    @Singleton
    fun provideAnalysisScheduler(@ApplicationContext context: Context): AnalysisScheduler =
        AnalysisSchedulerImpl(context)

    @Provides
    @Singleton
    fun provideSynthesisScheduler(@ApplicationContext context: Context): SynthesisScheduler =
        SynthesisSchedulerImpl(context)

    @Provides
    @Singleton
    fun providePlayerDataStore(@ApplicationContext context: Context): PlayerDataStore =
        PlayerDataStore(context)

    @Provides
    @Singleton
    fun provideWorkAxisRepository(
        dao: WorkAxisDao,
        suggestionDao: AxisSuggestionDao,
        analysisDao: CoachingAnalysisDao,
        synthesisDao: CoachingSynthesisDao,
        vpsApiService: VpsApiService,
        @VpsMistralEngine vpsMistralEngine: InferenceEngine
    ): WorkAxisRepository =
        WorkAxisRepositoryImpl(dao, suggestionDao, analysisDao, synthesisDao, vpsApiService, vpsMistralEngine)

    @Provides
    @Singleton
    fun providePlayerProfileRepository(
        dao: PlayerProfileDao,
        vpsApiService: VpsApiService,
        workAxisRepository: WorkAxisRepository
    ): PlayerProfileRepository =
        PlayerProfileRepositoryImpl(dao, vpsApiService, workAxisRepository)

    @Provides
    @Singleton
    fun provideNotificationScheduler(@ApplicationContext context: Context): NotificationScheduler =
        NotificationSchedulerImpl(context)

    @Provides
    @Singleton
    fun provideNotificationRepository(
        playerDataStore: PlayerDataStore,
        notificationScheduler: NotificationScheduler
    ): NotificationRepository =
        NotificationRepositoryImpl(playerDataStore, notificationScheduler)

}
