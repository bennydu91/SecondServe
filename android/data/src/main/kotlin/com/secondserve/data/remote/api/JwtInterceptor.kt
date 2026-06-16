package com.secondserve.data.remote.api

import okhttp3.Interceptor
import okhttp3.Response
import com.secondserve.data.remote.security.TokenStore

class JwtInterceptor(private val tokenStore: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val token = tokenStore.getToken()

        // Skip if no token (e.g., POST /auth/init)
        if (token == null) return chain.proceed(originalRequest)

        val authorizedRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        return chain.proceed(authorizedRequest)
    }
}
