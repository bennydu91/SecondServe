package com.secondserve.domain.repository

import com.secondserve.domain.notification.NotificationFrequency

interface NotificationRepository {
    fun getFrequency(): NotificationFrequency
    fun setFrequency(frequency: NotificationFrequency)
    fun getSilentModeUntil(): Long
    fun setSilentModeUntil(epochMs: Long)
}
