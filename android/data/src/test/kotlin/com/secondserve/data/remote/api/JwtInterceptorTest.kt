package com.secondserve.data.remote.api

import io.mockk.mockk
import io.mockk.every
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Protocol
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.secondserve.data.remote.security.TokenStore

class JwtInterceptorTest {

    private lateinit var tokenStore: TokenStore
    private lateinit var interceptor: JwtInterceptor
    private lateinit var mockChain: Interceptor.Chain

    @BeforeEach
    fun setup() {
        tokenStore = mockk()
        interceptor = JwtInterceptor(tokenStore)
        mockChain = mockk()
    }

    @Test
    fun testInterceptorAddsAuthorizationHeaderWhenTokenExists() {
        val token = "test.jwt.token"
        val request = Request.Builder().url("https://example.com/api").build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()

        every { tokenStore.getToken() } returns token
        every { mockChain.request() } returns request
        every { mockChain.proceed(any()) } returns response

        val result = interceptor.intercept(mockChain)

        verify {
            mockChain.proceed(match { it.header("Authorization") == "Bearer $token" })
        }
        assertEquals(200, result.code)
    }

    @Test
    fun testInterceptorSkipsAuthorizationHeaderWhenTokenIsNull() {
        val request = Request.Builder().url("https://example.com/api").build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()

        every { tokenStore.getToken() } returns null
        every { mockChain.request() } returns request
        every { mockChain.proceed(any()) } returns response

        val result = interceptor.intercept(mockChain)

        verify {
            mockChain.proceed(request)
        }
        assertEquals(200, result.code)
    }

    @Test
    fun testInterceptorDoesNotAddAuthorizationHeaderTwice() {
        val token = "test.jwt.token"
        val existingRequest = Request.Builder()
            .url("https://example.com/api")
            .addHeader("Authorization", "Bearer old.token")
            .build()
        val response = Response.Builder()
            .request(existingRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()

        every { tokenStore.getToken() } returns token
        every { mockChain.request() } returns existingRequest
        every { mockChain.proceed(any()) } returns response

        interceptor.intercept(mockChain)

        verify {
            mockChain.proceed(match { request ->
                // Should have the new token
                request.header("Authorization") == "Bearer $token"
            })
        }
    }
}
