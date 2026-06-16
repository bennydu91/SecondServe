package com.secondserve.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PlayerDataStore(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "player_data_store",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            throw IllegalStateException("Failed to initialize secure player data storage", e)
        }
    }

    fun saveFftLicenseNumber(number: String) {
        prefs.edit().putString(KEY_FFT_LICENSE, number).apply()
    }

    fun getFftLicenseNumber(): String? = prefs.getString(KEY_FFT_LICENSE, null)

    fun clearFftLicenseNumber() {
        prefs.edit().remove(KEY_FFT_LICENSE).apply()
    }

    companion object {
        private const val KEY_FFT_LICENSE = "fft_license"
    }
}
