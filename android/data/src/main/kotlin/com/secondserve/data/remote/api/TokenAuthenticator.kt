package com.secondserve.data.remote.api

import com.secondserve.data.remote.security.TokenStore
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val tokenStore: TokenStore
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        // OkHttp guarantees authenticate() is not called again after returning null — no loop guard needed.
        tokenStore.clearToken()
        return null
    }
}
