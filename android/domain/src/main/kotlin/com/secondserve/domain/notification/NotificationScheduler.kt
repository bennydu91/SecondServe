package com.secondserve.domain.notification

interface NotificationScheduler {
    fun scheduleDaily()
    fun scheduleEvery2Days()
    fun scheduleWeekly()
    fun cancel()
}
