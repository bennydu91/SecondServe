package com.secondserve.data.repository

import com.secondserve.data.local.PlayerDataStore
import com.secondserve.domain.notification.NotificationFrequency
import com.secondserve.domain.notification.NotificationScheduler
import com.secondserve.domain.repository.NotificationRepository

class NotificationRepositoryImpl(
    private val playerDataStore: PlayerDataStore,
    private val notificationScheduler: NotificationScheduler
) : NotificationRepository {
    override fun getFrequency(): NotificationFrequency = playerDataStore.getNotificationFrequency()
    override fun setFrequency(frequency: NotificationFrequency) {
        playerDataStore.saveNotificationFrequency(frequency)
        when (frequency) {
            NotificationFrequency.DAILY -> notificationScheduler.scheduleDaily()
            NotificationFrequency.EVERY_2_DAYS -> notificationScheduler.scheduleEvery2Days()
            NotificationFrequency.WEEKLY -> notificationScheduler.scheduleWeekly()
            NotificationFrequency.DISABLED -> notificationScheduler.cancel()
        }
    }
    override fun getSilentModeUntil() = playerDataStore.getSilentModeUntil()
    override fun setSilentModeUntil(epochMs: Long) =
        playerDataStore.saveSilentModeUntil(epochMs)
}
