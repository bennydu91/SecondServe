package com.secondserve.data.remote.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

interface TokenStore {
    fun saveToken(token: String)
    fun getToken(): String?
    fun hasToken(): Boolean
    fun clearToken()
}

class JwtTokenStore(private val context: Context) : TokenStore {

    private val encryptedSharedPreferences: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "jwt_store",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            throw IllegalStateException("Failed to initialize secure token storage", e)
        }
    }

    override fun saveToken(token: String) {
        encryptedSharedPreferences.edit().putString(JWT_TOKEN_KEY, token).apply()
    }

    override fun getToken(): String? = encryptedSharedPreferences.getString(JWT_TOKEN_KEY, null)

    override fun hasToken(): Boolean = encryptedSharedPreferences.contains(JWT_TOKEN_KEY)

    override fun clearToken() {
        encryptedSharedPreferences.edit().remove(JWT_TOKEN_KEY).apply()
    }

    companion object {
        private const val JWT_TOKEN_KEY = "jwt_token"
    }
}
