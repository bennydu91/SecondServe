package com.secondserve.data.remote.auth

import com.secondserve.data.remote.security.TokenStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface AuthRepository {
    suspend fun initAuth(googleIdToken: String): Result<Unit>
    fun hasToken(): Boolean
}

class AuthRepositoryImpl(
    private val authService: AuthService,
    private val tokenStore: TokenStore
) : AuthRepository {

    private val mutex = Mutex()

    override suspend fun initAuth(googleIdToken: String): Result<Unit> = mutex.withLock {
        authService.initAuth(googleIdToken).map { }
    }

    override fun hasToken(): Boolean = tokenStore.hasToken()
}
