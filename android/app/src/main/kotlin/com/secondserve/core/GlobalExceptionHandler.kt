// android/app/src/main/kotlin/com/secondserve/core/GlobalExceptionHandler.kt
package com.secondserve.core

import com.secondserve.data.monitoring.MonitoringClient
import com.secondserve.data.monitoring.dto.MonitoringEventDto
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlobalExceptionHandler @Inject constructor(
    private val monitoringClient: MonitoringClient,
) : Thread.UncaughtExceptionHandler {

    private val original: Thread.UncaughtExceptionHandler? =
        Thread.getDefaultUncaughtExceptionHandler()

    fun install() {
        Thread.setDefaultUncaughtExceptionHandler(this)
        Timber.d("GlobalExceptionHandler: installed")
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        runBlocking {
            try {
                monitoringClient.sendEvent(MonitoringEventDto(
                    eventType = "android.error",
                    payload = mapOf(
                        "thread" to thread.name,
                        "error" to (throwable.message ?: "unknown"),
                        "stacktrace" to throwable.stackTraceToString().take(2000),
                    ),
                    source = "android",
                ))
            } catch (e: Exception) {
                Timber.e(e, "GlobalExceptionHandler: failed to report crash")
            }
        }
        original?.uncaughtException(thread, throwable)
    }
}
