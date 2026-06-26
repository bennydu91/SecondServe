// android/data/src/main/kotlin/com/secondserve/data/monitoring/dto/MonitoringDto.kt
package com.secondserve.data.monitoring.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MonitoringEventDto(
    @Json(name = "event_type") val eventType: String,
    @Json(name = "payload") val payload: Map<String, Any> = emptyMap(),
    @Json(name = "source") val source: String = "android",
    @Json(name = "timestamp") val timestampMs: Long = System.currentTimeMillis(),
)

@JsonClass(generateAdapter = true)
data class MonitoringStatusDto(
    @Json(name = "status") val status: String,
    @Json(name = "count") val count: Int? = null,
)
