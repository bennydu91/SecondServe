package com.secondserve.data.local.db.entity

import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.Session
import com.secondserve.domain.model.SessionFormat
import com.secondserve.domain.model.SessionStatus
import com.secondserve.domain.model.SessionType
import com.secondserve.domain.model.ThirdSetRule
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MappersTest {

    private fun aSession(scoreText: String? = null) = Session(
        id = 1L,
        surface = "CLAY",
        format = SessionFormat(MatchFormat.BEST_OF_3, ThirdSetRule.FULL_ADVANTAGE),
        status = SessionStatus.COMPLETED,
        sessionType = SessionType.MATCH,
        result = "VICTORY",
        scoreText = scoreText,
        createdAt = 1_000_000L,
        updatedAt = 2_000_000L
    )

    @Test
    fun `toSyncDto includes scoreText when non-null`() {
        val dto = aSession(scoreText = "6-3 7-5").toSyncDto()
        assertEquals("6-3 7-5", dto.scoreText)
    }

    @Test
    fun `toSyncDto sets scoreText to null when absent`() {
        val dto = aSession(scoreText = null).toSyncDto()
        assertNull(dto.scoreText)
    }

    @Test
    fun `toSyncDto maps all mandatory fields`() {
        val dto = aSession().toSyncDto()
        assertEquals(1L, dto.clientId)
        assertEquals("CLAY", dto.surface)
        assertEquals("BEST_OF_3", dto.matchFormat)
        assertEquals("COMPLETED", dto.status)
        assertEquals(1_000_000L, dto.createdAt)
    }
}
