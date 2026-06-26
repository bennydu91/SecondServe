// android/data/src/main/kotlin/com/secondserve/data/monitoring/MonitoringEventQueue.kt
package com.secondserve.data.monitoring

import com.secondserve.data.monitoring.dto.MonitoringEventDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val FLUSH_INTERVAL_MS = 5 * 60 * 1000L
private const val MAX_QUEUE_SIZE = 50

@Singleton
class MonitoringEventQueue @Inject constructor(
    private val client: MonitoringClient,
    private val appScope: CoroutineScope,
) {
    private val queue = mutableListOf<MonitoringEventDto>()
    private val mutex = Mutex()

    init {
        appScope.launch {
            while (true) {
                delay(FLUSH_INTERVAL_MS)
                flush()
            }
        }
    }

    suspend fun enqueue(eventType: String, payload: Map<String, Any> = emptyMap(), source: String = "android") {
        val dto = MonitoringEventDto(eventType = eventType, payload = payload, source = source)
        val shouldFlush = mutex.withLock {
            queue.add(dto)
            queue.size >= MAX_QUEUE_SIZE
        }
        if (shouldFlush) flush()
    }

    suspend fun flush() {
        val events = mutex.withLock {
            if (queue.isEmpty()) return
            queue.toList().also { queue.clear() }
        }
        Timber.d("MonitoringEventQueue: flushing %d events", events.size)
        client.sendBatch(events)
    }
}
