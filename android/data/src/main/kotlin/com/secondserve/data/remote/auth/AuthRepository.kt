package com.secondserve.data.remote.auth

import com.secondserve.data.remote.security.TokenStore

interface AuthRepository {
    suspend fun initAuthIfNeeded(): Result<Unit>
    suspend fun reauthenticate(): Result<Unit>
}

class AuthRepositoryImpl(
    private val authService: AuthService,
    private val tokenStore: TokenStore
) : AuthRepository {
    override suspend fun initAuthIfNeeded(): Result<Unit> {
        if (tokenStore.hasToken()) return Result.success(Unit)
        return authService.initAuth().map { }
    }

    override suspend fun reauthenticate(): Result<Unit> =
        authService.reauthenticate().map { }
}
