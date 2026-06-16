package com.secondserve.data.remote.api

import com.secondserve.data.remote.api.dto.ProfileDetailsRequest
import com.secondserve.data.remote.api.dto.ProfileDetailsResponse
import com.secondserve.data.remote.api.dto.ProfileSummaryDto
import com.secondserve.data.remote.api.dto.RankingEntryDto
import com.secondserve.data.remote.api.dto.RankingRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

interface VpsApiService {
    @POST("api/v1/auth/init")
    suspend fun initAuth(): TokenResponse

    @GET("api/v1/health")
    suspend fun health(): HealthResponse

    @GET("api/v1/profile")
    suspend fun getProfile(): ProfileSummaryDto

    @POST("api/v1/profile/ranking")
    suspend fun saveRanking(@Body request: RankingRequest): RankingEntryDto

    @PUT("api/v1/profile/details")
    suspend fun updateProfileDetails(@Body request: ProfileDetailsRequest): ProfileDetailsResponse
}
