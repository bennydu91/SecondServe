package com.secondserve.di

import android.content.Context
import androidx.room.Room
import com.secondserve.data.local.PlayerDataStore
import com.secondserve.data.local.dao.PlayerProfileDao
import com.secondserve.data.local.db.SecondServeDatabase
import com.secondserve.data.remote.api.VpsApiService
import com.secondserve.data.repository.PlayerProfileRepositoryImpl
import com.secondserve.domain.repository.PlayerProfileRepository
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
        .addMigrations(SecondServeDatabase.MIGRATION_1_2)
        .build()

    @Provides
    @Singleton
    fun providePlayerProfileDao(db: SecondServeDatabase): PlayerProfileDao =
        db.playerProfileDao()

    @Provides
    @Singleton
    fun providePlayerDataStore(@ApplicationContext context: Context): PlayerDataStore =
        PlayerDataStore(context)

    @Provides
    @Singleton
    fun providePlayerProfileRepository(
        dao: PlayerProfileDao,
        vpsApiService: VpsApiService,
        playerDataStore: PlayerDataStore
    ): PlayerProfileRepository =
        PlayerProfileRepositoryImpl(dao, vpsApiService, playerDataStore)
}
