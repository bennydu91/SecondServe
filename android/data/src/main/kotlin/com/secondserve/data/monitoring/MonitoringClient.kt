// android/data/src/main/kotlin/com/secondserve/data/monitoring/MonitoringClient.kt
package com.secondserve.data.monitoring

import com.secondserve.data.monitoring.dto.MonitoringEventDto
import com.secondserve.data.remote.api.VpsApiService
import com.secondserve.domain.AppResult
import timber.log.Timber
import javax.inject.Singleton

@Singleton
class MonitoringClient(
    private val api: VpsApiService,
) {
    suspend fun sendEvent(dto: MonitoringEventDto): AppResult<Unit> = try {
        api.sendMonitoringEvent(dto)
        AppResult.Success(Unit)
    } catch (e: Exception) {
        Timber.w(e, "MonitoringClient: sendEvent failed — %s", dto.eventType)
        AppResult.Error(e)
    }

    suspend fun sendBatch(events: List<MonitoringEventDto>): AppResult<Unit> {
        if (events.isEmpty()) return AppResult.Success(Unit)
        return try {
            api.sendMonitoringEventBatch(events)
            AppResult.Success(Unit)
        } catch (e: Exception) {
            Timber.w(e, "MonitoringClient: sendBatch failed (%d events)", events.size)
            AppResult.Error(e)
        }
    }
}
