package com.secondserve.data.remote.api

import com.secondserve.data.remote.api.dto.ProfileDetailsRequest
import com.secondserve.data.remote.api.dto.ProfileDetailsResponse
import com.secondserve.data.remote.api.dto.ProfileSummaryDto
import com.secondserve.data.remote.api.dto.RankingEntryDto
import com.secondserve.data.remote.api.dto.RankingRequest
import com.secondserve.data.remote.api.dto.PendingNotificationResponse
import com.secondserve.data.remote.api.dto.SyncPushRequest
import com.secondserve.data.remote.api.dto.SyncPushResponse
import com.secondserve.data.remote.api.dto.WorkAxesResponse
import com.secondserve.data.remote.api.dto.WorkAxisRequest
import com.secondserve.data.remote.api.dto.WorkAxisResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface VpsApiService {
    @POST("api/v1/auth/init")
    suspend fun initAuth(@Body request: GoogleAuthRequest): TokenResponse

    @GET("api/v1/health")
    suspend fun health(): HealthResponse

    @GET("api/v1/profile")
    suspend fun getProfile(): ProfileSummaryDto

    @POST("api/v1/profile/ranking")
    suspend fun saveRanking(@Body request: RankingRequest): RankingEntryDto

    @PUT("api/v1/profile/details")
    suspend fun updateProfileDetails(@Body request: ProfileDetailsRequest): ProfileDetailsResponse

    @GET("api/v1/work_axes")
    suspend fun getWorkAxes(): WorkAxesResponse

    @POST("api/v1/work_axes")
    suspend fun createWorkAxis(@Body request: WorkAxisRequest): WorkAxisResponse

    @PUT("api/v1/work_axes/{id}")
    suspend fun updateWorkAxis(@Path("id") id: Long, @Body request: WorkAxisRequest): WorkAxisResponse

    @DELETE("api/v1/work_axes/{id}")
    suspend fun deleteWorkAxis(@Path("id") id: Long)

    @POST("api/v1/sync/push")
    suspend fun syncPush(@Body request: SyncPushRequest): SyncPushResponse

    @GET("api/v1/notifications/pending")
    suspend fun getPendingNotification(@Query("session_id") sessionId: Long): PendingNotificationResponse
}
