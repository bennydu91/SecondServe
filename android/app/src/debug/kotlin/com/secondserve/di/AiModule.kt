package com.secondserve.di

import com.secondserve.core.ai.InferenceEngine
import com.secondserve.core.ai.mock.MockInferenceEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideInferenceEngine(): InferenceEngine = MockInferenceEngine()
}
