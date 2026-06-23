package com.secondserve.di

import com.secondserve.core.ai.InferenceEngine
import com.secondserve.core.ai.di.GeminiEngine
import com.secondserve.core.ai.di.VpsMistralEngine
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

    @Provides
    @GeminiEngine
    @Singleton
    fun provideGeminiEngine(): InferenceEngine = MockInferenceEngine()

    @Provides
    @VpsMistralEngine
    @Singleton
    fun provideVpsMistralEngine(): InferenceEngine = MockInferenceEngine()
}
