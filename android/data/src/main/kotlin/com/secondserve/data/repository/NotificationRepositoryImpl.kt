package com.secondserve.data.repository

import com.secondserve.data.local.PlayerDataStore
import com.secondserve.domain.notification.NotificationScheduler
import com.secondserve.domain.repository.NotificationRepository

class NotificationRepositoryImpl(
    private val playerDataStore: PlayerDataStore,
    private val notificationScheduler: NotificationScheduler
) : NotificationRepository {
    override fun getFrequency() = playerDataStore.getNotificationFrequency()
    override fun setFrequency(frequency: String) {
        playerDataStore.saveNotificationFrequency(frequency)
        when (frequency) {
            "DAILY" -> notificationScheduler.scheduleDaily()
            "EVERY_2_DAYS" -> notificationScheduler.scheduleEvery2Days()
            "WEEKLY" -> notificationScheduler.scheduleWeekly()
            "DISABLED" -> notificationScheduler.cancel()
            else -> notificationScheduler.cancel()
        }
    }
    override fun getSilentModeUntil() = playerDataStore.getSilentModeUntil()
    override fun setSilentModeUntil(epochMs: Long) =
        playerDataStore.saveSilentModeUntil(epochMs)
}
