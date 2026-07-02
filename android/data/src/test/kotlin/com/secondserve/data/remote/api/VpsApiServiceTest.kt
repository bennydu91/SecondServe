package com.secondserve.data.remote.api

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VpsApiServiceTest {

    private lateinit var vpsApiService: VpsApiService

    @BeforeEach
    fun setup() {
        val moshi = Moshi.Builder().build()
        val okHttpClient = OkHttpClient.Builder().build()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://example.com/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(okHttpClient)
            .build()
        vpsApiService = retrofit.create(VpsApiService::class.java)
    }

    @Test
    fun testVpsApiServiceHasInitAuthEndpoint() {
        // This is a smoke test to verify the interface is correctly defined
        val method = vpsApiService::class.java.getDeclaredMethods()
            .find { it.name == "initAuth" }
        assertTrue(method != null, "initAuth method should exist in VpsApiService interface")
    }

    @Test
    fun testVpsApiServiceHasHealthEndpoint() {
        // Smoke test for health endpoint
        val method = vpsApiService::class.java.getDeclaredMethods()
            .find { it.name == "health" }
        assertTrue(method != null, "health method should exist in VpsApiService interface")
    }

    @Test
    fun testVpsApiServiceHasCreateLiveShareEndpoint() {
        // Smoke test to verify the createLiveShare endpoint is correctly defined
        val method = vpsApiService::class.java.getDeclaredMethods()
            .find { it.name == "createLiveShare" }
        assertTrue(method != null, "createLiveShare method should exist in VpsApiService interface")
    }

    @Test
    fun testVpsApiServiceHasPushLiveScoreEndpoint() {
        // Smoke test to verify the pushLiveScore endpoint is correctly defined
        val method = vpsApiService::class.java.getDeclaredMethods()
            .find { it.name == "pushLiveScore" }
        assertTrue(method != null, "pushLiveScore method should exist in VpsApiService interface")
    }
}
