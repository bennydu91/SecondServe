package com.secondserve.data.wearable

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DataLayerClientConstantsTest {
    @Test
    fun `PATH_START_SESSION has correct value`() {
        assertEquals("/secondserve/start_session", DataLayerClient.PATH_START_SESSION)
    }

    @Test
    fun `PATH_START_SESSION_REQUEST has correct value`() {
        assertEquals("/secondserve/start_session_request", DataLayerClient.PATH_START_SESSION_REQUEST)
    }
}
