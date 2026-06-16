package com.secondserve.data.remote.auth

import com.secondserve.data.remote.security.TokenStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AuthRepository {
    suspend fun initAuthIfNeeded(): Result<Unit>
    suspend fun reauthenticate(): Result<Unit>
}

class AuthRepositoryImpl(
    private val authService: AuthService,
    private val tokenStore: TokenStore
) : AuthRepository {

    private val mutex = Mutex()

    override suspend fun initAuthIfNeeded(): Result<Unit> = mutex.withLock {
        if (tokenStore.hasToken()) return@withLock Result.success(Unit)
        authService.initAuth().map { }
    }

    override suspend fun reauthenticate(): Result<Unit> =
        authService.reauthenticate().map { }
}
