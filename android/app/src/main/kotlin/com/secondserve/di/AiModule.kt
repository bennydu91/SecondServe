package com.secondserve.di

import com.secondserve.core.ai.InferenceEngine
import com.secondserve.core.ai.mock.MockInferenceEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindInferenceEngine(impl: MockInferenceEngine): InferenceEngine
}
