package com.secondserve.data.remote.api

import retrofit2.http.GET
import retrofit2.http.POST

interface VpsApiService {
    @POST("api/v1/auth/init")
    suspend fun initAuth(): TokenResponse

    @GET("api/v1/health")
    suspend fun health(): HealthResponse
}
