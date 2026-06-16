package com.secondserve.data.remote.api

import com.secondserve.data.remote.auth.AuthService
import com.secondserve.data.remote.security.TokenStore
import javax.inject.Provider
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val tokenStore: TokenStore,
    private val authServiceProvider: Provider<AuthService>
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Avoid infinite retry loops
        if (response.request.header("X-Auth-Retried") != null) return null

        val reAuthResult = runBlocking { authServiceProvider.get().reauthenticate() }
        if (reAuthResult.isFailure) return null

        val newToken = tokenStore.getToken() ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .header("X-Auth-Retried", "true")
            .build()
    }
}
