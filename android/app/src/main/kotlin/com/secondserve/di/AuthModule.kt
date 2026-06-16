package com.secondserve.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Provider
import javax.inject.Singleton
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import com.secondserve.BuildConfig
import com.secondserve.data.remote.api.JwtInterceptor
import com.secondserve.data.remote.api.TokenAuthenticator
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
    fun provideTokenAuthenticator(
        tokenStore: TokenStore,
        authServiceProvider: Provider<AuthService>
    ): TokenAuthenticator = TokenAuthenticator(tokenStore, authServiceProvider)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        jwtInterceptor: JwtInterceptor,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .authenticator(tokenAuthenticator)
            .addInterceptor(jwtInterceptor)
            .addInterceptor(logging)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideVpsApiService(okHttpClient: OkHttpClient, moshi: Moshi): VpsApiService {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.VPS_BASE_URL)
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
