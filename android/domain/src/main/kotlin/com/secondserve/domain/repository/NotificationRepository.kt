package com.secondserve.domain.repository

interface NotificationRepository {
    fun getFrequency(): String
    fun setFrequency(frequency: String)
    fun getSilentModeUntil(): Long
    fun setSilentModeUntil(epochMs: Long)
}
