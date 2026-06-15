package com.secondserve.data.remote.auth

import io.mockk.mockk
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
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
        val token = "test.jwt.token"
        coEvery { vpsApiService.initAuth() } returns TokenResponse(token)

        val result = authService.initAuth()

        assertTrue(result.isSuccess)
        assertEquals(token, result.getOrNull())
        coVerify { tokenStore.saveToken(token) }
    }

    @Test
    fun testInitAuthFailureReturnsException() = runTest {
        val exception = Exception("Network error")
        coEvery { vpsApiService.initAuth() } throws exception

        val result = authService.initAuth()

        assertTrue(result.isFailure)
        assertEquals(exception.message, result.exceptionOrNull()?.message)
    }

    @Test
    fun testReauthenticateCallsInitAuth() = runTest {
        val token = "new.jwt.token"
        coEvery { vpsApiService.initAuth() } returns TokenResponse(token)

        val result = authService.reauthenticate()

        assertTrue(result.isSuccess)
        coVerify { tokenStore.saveToken(token) }
    }
}
