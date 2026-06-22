package com.secondserve.data.di

import com.secondserve.data.repository.CoachingRepositoryImpl
import com.secondserve.domain.repository.CoachingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CoachingModule {

    @Binds
    @Singleton
    abstract fun bindCoachingRepository(impl: CoachingRepositoryImpl): CoachingRepository
}
