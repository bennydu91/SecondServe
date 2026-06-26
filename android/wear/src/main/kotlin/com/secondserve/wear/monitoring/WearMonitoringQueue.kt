// android/wear/src/main/kotlin/com/secondserve/wear/monitoring/WearMonitoringQueue.kt
package com.secondserve.wear.monitoring

import com.secondserve.data.wearable.DataLayerClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearMonitoringQueue @Inject constructor(
    private val dataLayerClient: DataLayerClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun enqueueEvent(eventType: String, payload: Map<String, String> = emptyMap()) {
        scope.launch {
            val result = dataLayerClient.sendMonitorEvent(eventType, payload)
            Timber.d("WearMonitoringQueue: enqueueEvent %s -> %s", eventType, result)
        }
    }

    fun enqueueError(error: String, stacktrace: String) {
        scope.launch {
            dataLayerClient.sendMonitorError(error, stacktrace)
        }
    }
}
