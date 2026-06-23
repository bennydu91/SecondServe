package com.secondserve.di

import com.secondserve.BuildConfig
import com.secondserve.core.ai.InferenceEngine
import com.secondserve.core.ai.di.GeminiEngine
import com.secondserve.core.ai.di.VpsMistralEngine
import com.secondserve.core.ai.gemini.GeminiNanoEngine
import com.secondserve.core.ai.vps.VpsMistralEngine as VpsMistralEngineImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindInferenceEngine(impl: GeminiNanoEngine): InferenceEngine

    @Binds
    @GeminiEngine
    @Singleton
    abstract fun bindGeminiEngine(impl: GeminiNanoEngine): InferenceEngine

    @Binds
    @VpsMistralEngine
    @Singleton
    abstract fun bindVpsMistralEngine(impl: VpsMistralEngineImpl): InferenceEngine

    companion object {
        @Provides
        @Named("vps_base_url")
        fun provideVpsBaseUrl(): String {
            require(BuildConfig.VPS_BASE_URL.isNotBlank()) { "BuildConfig.VPS_BASE_URL must be set for release builds" }
            return BuildConfig.VPS_BASE_URL
        }
    }
}
