package com.secondserve.data.wearable.dto

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StartSessionPayloadTest {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @Test
    fun `StartSessionPayload serializes with correct type field`() {
        val payload = StartSessionPayload(
            ts = 1000L, sessionId = 42L,
            matchFormat = "BEST_OF_3", thirdSetRule = "FULL_ADVANTAGE"
        )
        val json = moshi.adapter(StartSessionPayload::class.java).toJson(payload)
        assertTrue(json.contains("\"type\":\"START_SESSION\""))
        assertTrue(json.contains("\"sessionId\":42"))
        assertTrue(json.contains("\"matchFormat\":\"BEST_OF_3\""))
        assertTrue(json.contains("\"thirdSetRule\":\"FULL_ADVANTAGE\""))
    }

    @Test
    fun `StartSessionPayload round-trips through Moshi`() {
        val original = StartSessionPayload(
            ts = 9999L, sessionId = 7L,
            matchFormat = "BEST_OF_1", thirdSetRule = "FULL_ADVANTAGE"
        )
        val json = moshi.adapter(StartSessionPayload::class.java).toJson(original)
        val restored = moshi.adapter(StartSessionPayload::class.java).fromJson(json)!!
        assertEquals(original.sessionId, restored.sessionId)
        assertEquals(original.matchFormat, restored.matchFormat)
        assertEquals(original.type, restored.type)
    }

    @Test
    fun `StartSessionRequestPayload serializes with correct type field`() {
        val payload = StartSessionRequestPayload(
            ts = 1000L, matchFormat = "BEST_OF_3", thirdSetRule = "SUPER_TIE_BREAK_10"
        )
        val json = moshi.adapter(StartSessionRequestPayload::class.java).toJson(payload)
        assertTrue(json.contains("\"type\":\"START_SESSION_REQUEST\""))
        assertTrue(json.contains("\"thirdSetRule\":\"SUPER_TIE_BREAK_10\""))
    }
}
