package com.secondserve.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.secondserve.data.remote.api.JwtInterceptor
import com.secondserve.data.remote.api.VpsApiService
import com.secondserve.data.remote.auth.AuthService
import com.secondserve.data.remote.auth.AuthRepository
import com.secondserve.data.remote.auth.AuthRepositoryImpl
import com.secondserve.data.remote.security.JwtTokenStore
import com.secondserve.data.remote.security.TokenStore

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideTokenStore(@ApplicationContext context: Context): TokenStore {
        return JwtTokenStore(context)
    }

    @Provides
    @Singleton
    fun provideJwtInterceptor(tokenStore: TokenStore): JwtInterceptor {
        return JwtInterceptor(tokenStore)
    }

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder().build()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(jwtInterceptor: JwtInterceptor): OkHttpClient {
        val logging = HttpLoggingInterceptor()
            .apply { level = HttpLoggingInterceptor.Level.BASIC }
        return OkHttpClient.Builder()
            .addInterceptor(jwtInterceptor)
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    fun provideVpsApiService(okHttpClient: OkHttpClient, moshi: Moshi): VpsApiService {
        return Retrofit.Builder()
            .baseUrl("https://secondserve.example.com/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(okHttpClient)
            .build()
            .create(VpsApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthService(
        vpsApiService: VpsApiService,
        tokenStore: TokenStore
    ): AuthService {
        return AuthService(vpsApiService, tokenStore)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(
        authService: AuthService,
        tokenStore: TokenStore
    ): AuthRepository {
        return AuthRepositoryImpl(authService, tokenStore)
    }
}
