package com.secondserve.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.secondserve.domain.notification.NotificationFrequency

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

    fun saveNotificationFrequency(frequency: NotificationFrequency) =
        prefs.edit().putString(KEY_NOTIF_FREQUENCY, frequency.name).apply()

    fun getNotificationFrequency(): NotificationFrequency {
        val raw = prefs.getString(KEY_NOTIF_FREQUENCY, "DAILY") ?: "DAILY"
        return NotificationFrequency.entries.firstOrNull { it.name == raw } ?: NotificationFrequency.DAILY
    }

    fun saveSilentModeUntil(epochMs: Long) =
        prefs.edit().putLong(KEY_SILENT_MODE_UNTIL, epochMs).apply()

    fun getSilentModeUntil(): Long =
        prefs.getLong(KEY_SILENT_MODE_UNTIL, 0L)

    companion object {
        private const val KEY_FFT_LICENSE = "fft_license"
        private const val KEY_NOTIF_FREQUENCY = "notification_frequency"
        private const val KEY_SILENT_MODE_UNTIL = "silent_mode_until"
    }
}
