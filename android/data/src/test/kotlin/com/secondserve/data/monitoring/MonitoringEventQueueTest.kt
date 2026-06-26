// android/data/src/test/kotlin/com/secondserve/data/monitoring/MonitoringEventQueueTest.kt
package com.secondserve.data.monitoring

import com.secondserve.data.monitoring.dto.MonitoringEventDto
import com.secondserve.domain.AppResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MonitoringEventQueueTest {

    private val client = mockk<MonitoringClient>(relaxed = true)

    private fun makeQueue(scope: TestScope) = MonitoringEventQueue(client, scope.backgroundScope)

    @Test
    fun `flush sends batched events`() = runTest {
        val queue = makeQueue(this)
        coEvery { client.sendBatch(any()) } returns AppResult.Success(Unit)

        queue.enqueue("match.started", mapOf("session_id" to 1L))
        queue.enqueue("match.started", mapOf("session_id" to 2L))
        queue.flush()

        val slot = slot<List<MonitoringEventDto>>()
        coVerify { client.sendBatch(capture(slot)) }
        assertEquals(2, slot.captured.size)
    }

    @Test
    fun `flush clears queue after send`() = runTest {
        val queue = makeQueue(this)
        coEvery { client.sendBatch(any()) } returns AppResult.Success(Unit)

        queue.enqueue("match.started", emptyMap())
        queue.flush()
        queue.flush() // second flush — queue should be empty

        coVerify(exactly = 1) { client.sendBatch(any()) }
    }
}
