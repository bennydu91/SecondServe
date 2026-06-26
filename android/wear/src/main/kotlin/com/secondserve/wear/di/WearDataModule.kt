package com.secondserve.wear.di

import android.content.Context
import androidx.room.Room
import com.secondserve.data.local.dao.SessionDao
import com.secondserve.data.local.dao.SyncQueueDao
import com.secondserve.data.local.db.SecondServeDatabase
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
}

private object NoOpNotificationScheduler : NotificationScheduler {
    override fun scheduleDaily() {}
    override fun scheduleEvery2Days() {}
    override fun scheduleWeekly() {}
    override fun cancel() {}
    override fun schedulePreMatchReminder(sessionId: Long, triggerAtMs: Long) {}
    override fun cancelPreMatchReminder(sessionId: Long) {}
}
