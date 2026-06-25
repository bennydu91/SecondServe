package com.secondserve.data.remote.api

data class GoogleAuthRequest(val google_id_token: String)

data class TokenResponse(val token: String)

data class HealthResponse(val status: String)
