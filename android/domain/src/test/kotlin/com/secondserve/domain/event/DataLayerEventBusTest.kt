package com.secondserve.domain.event

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DataLayerEventBusTest {

    @Test
    fun `emitStartSession emits sessionId on startSessionRequests`() = runTest {
        val bus = DataLayerEventBus()
        val values = mutableListOf<Long>()

        val job = launch {
            bus.startSessionRequests.take(1).collect { values.add(it) }
        }

        advanceUntilIdle()
        bus.emitStartSession(42L)
        advanceUntilIdle()
        job.join()

        assertEquals(listOf(42L), values)
    }
}
