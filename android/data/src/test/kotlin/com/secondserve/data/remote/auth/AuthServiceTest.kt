package com.secondserve.data.remote.auth

import io.mockk.mockk
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.secondserve.data.remote.api.GoogleAuthRequest
import com.secondserve.data.remote.api.TokenResponse
import com.secondserve.data.remote.api.VpsApiService
import com.secondserve.data.remote.security.TokenStore

class AuthServiceTest {

    private lateinit var vpsApiService: VpsApiService
    private lateinit var tokenStore: TokenStore
    private lateinit var authService: AuthService

    @BeforeEach
    fun setup() {
        vpsApiService = mockk()
        tokenStore = mockk(relaxed = true)
        authService = AuthService(vpsApiService, tokenStore)
    }

    @Test
    fun testInitAuthSuccessfullySavesToken() = runTest {
        val googleIdToken = "google.id.token"
        val jwtToken = "test.jwt.token"
        coEvery { vpsApiService.initAuth(GoogleAuthRequest(googleIdToken)) } returns TokenResponse(jwtToken)

        val result = authService.initAuth(googleIdToken)

        assertTrue(result.isSuccess)
        assertEquals(jwtToken, result.getOrNull())
        coVerify { tokenStore.saveToken(jwtToken) }
    }

    @Test
    fun testInitAuthFailureReturnsException() = runTest {
        val googleIdToken = "google.id.token"
        val exception = Exception("Network error")
        coEvery { vpsApiService.initAuth(GoogleAuthRequest(googleIdToken)) } throws exception

        val result = authService.initAuth(googleIdToken)

        assertTrue(result.isFailure)
        assertEquals(exception.message, result.exceptionOrNull()?.message)
    }

    @Test
    fun testInitAuthBlankTokenReturnsFailure() = runTest {
        val googleIdToken = "google.id.token"
        coEvery { vpsApiService.initAuth(GoogleAuthRequest(googleIdToken)) } returns TokenResponse("")

        val result = authService.initAuth(googleIdToken)

        assertTrue(result.isFailure)
    }
}
