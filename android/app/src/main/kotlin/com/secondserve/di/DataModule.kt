package com.secondserve.di

import android.content.Context
import androidx.room.Room
import com.secondserve.data.local.PlayerDataStore
import com.secondserve.data.local.dao.CoachingCacheDao
import com.secondserve.data.local.dao.PlayerProfileDao
import com.secondserve.data.local.dao.SessionDao
import com.secondserve.data.local.dao.SyncQueueDao
import com.secondserve.data.local.dao.WorkAxisDao
import com.secondserve.data.local.db.SecondServeDatabase
import com.secondserve.data.remote.api.VpsApiService
import com.secondserve.data.repository.PlayerProfileRepositoryImpl
import com.secondserve.data.repository.WorkAxisRepositoryImpl
import com.secondserve.data.worker.SyncSchedulerImpl
import com.secondserve.domain.event.DataLayerEventBus
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
            SecondServeDatabase.MIGRATION_5_6
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
    fun provideDataLayerEventBus(): DataLayerEventBus = DataLayerEventBus()

    @Provides
    @Singleton
    fun provideSyncScheduler(@ApplicationContext context: Context): SyncScheduler =
        SyncSchedulerImpl(context)

    @Provides
    @Singleton
    fun providePlayerDataStore(@ApplicationContext context: Context): PlayerDataStore =
        PlayerDataStore(context)

    @Provides
    @Singleton
    fun provideWorkAxisRepository(
        dao: WorkAxisDao,
        vpsApiService: VpsApiService
    ): WorkAxisRepository =
        WorkAxisRepositoryImpl(dao, vpsApiService)

    @Provides
    @Singleton
    fun providePlayerProfileRepository(
        dao: PlayerProfileDao,
        vpsApiService: VpsApiService,
        workAxisRepository: WorkAxisRepository
    ): PlayerProfileRepository =
        PlayerProfileRepositoryImpl(dao, vpsApiService, workAxisRepository)

}
