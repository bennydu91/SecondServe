package com.secondserve.core.ai.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeminiEngine

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class VpsMistralEngine
