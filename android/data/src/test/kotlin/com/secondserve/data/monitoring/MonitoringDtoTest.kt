// android/data/src/test/kotlin/com/secondserve/data/monitoring/MonitoringDtoTest.kt
package com.secondserve.data.monitoring

import com.secondserve.data.monitoring.dto.MonitoringEventDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MonitoringDtoTest {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(MonitoringEventDto::class.java)

    @Test
    fun `serialize MonitoringEventDto to JSON`() {
        val dto = MonitoringEventDto(
            eventType = "android.match.started",
            payload = mapOf("session_id" to 42L),
            source = "android",
        )
        val json = adapter.toJson(dto)
        assert(json.contains("android.match.started"))
        assert(json.contains("android"))
    }

    @Test
    fun `deserialize MonitoringEventDto from JSON`() {
        val json = """{"event_type":"wear.error","payload":{},"source":"wear","timestamp":1234567890}"""
        val dto = adapter.fromJson(json)
        assertNotNull(dto)
        assertEquals("wear.error", dto.eventType)
        assertEquals("wear", dto.source)
    }
}
