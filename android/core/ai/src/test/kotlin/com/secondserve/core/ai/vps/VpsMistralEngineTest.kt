package com.secondserve.core.ai.vps

import com.secondserve.domain.AppResult
import com.secondserve.domain.model.ErrorCode
import com.secondserve.domain.model.InferenceEngineException
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class VpsMistralEngineTest {

    private lateinit var server: MockWebServer
    private lateinit var engine: VpsMistralEngine

    @BeforeEach
    fun setup() {
        server = MockWebServer()
        server.start()
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val client = OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .callTimeout(2, TimeUnit.SECONDS)
            .build()
        engine = VpsMistralEngine(client, server.url("/").toString(), moshi)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `generate success returns AppResult Success`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"content":"Conseil depuis VPS"}""")
                .addHeader("Content-Type", "application/json")
        )
        val result = engine.generate("Mon prompt")
        assertTrue(result is AppResult.Success)
        assertEquals("Conseil depuis VPS", (result as AppResult.Success).data)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.endsWith("/api/v1/coaching/analyze"))
        assertTrue(request.body.readUtf8().contains("Mon prompt"))
    }

    @Test
    fun `generate 500 returns NETWORK_UNAVAILABLE`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val result = engine.generate("prompt")
        assertTrue(result is AppResult.Error)
        val error = (result as AppResult.Error).exception
        assertTrue(error is InferenceEngineException)
        assertEquals(ErrorCode.NETWORK_UNAVAILABLE, (error as InferenceEngineException).errorCode)
    }

    @Test
    fun `generate timeout returns NETWORK_UNAVAILABLE`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val result = engine.generate("prompt")
        assertTrue(result is AppResult.Error)
        val error = (result as AppResult.Error).exception
        assertTrue(error is InferenceEngineException)
        assertEquals(ErrorCode.NETWORK_UNAVAILABLE, (error as InferenceEngineException).errorCode)
    }
}
