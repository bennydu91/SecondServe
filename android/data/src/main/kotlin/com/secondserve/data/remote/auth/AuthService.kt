package com.secondserve.data.remote.auth

import com.secondserve.data.remote.api.VpsApiService
import com.secondserve.data.remote.security.TokenStore

class AuthService(
    private val vpsApiService: VpsApiService,
    private val tokenStore: TokenStore
) {
    suspend fun initAuth(): Result<String> = try {
        val response = vpsApiService.initAuth()
        val token = response.token
        tokenStore.saveToken(token)
        Result.success(token)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun reauthenticate(): Result<String> = initAuth()
}
