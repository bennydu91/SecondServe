package com.secondserve.data.wearable.dto

import com.secondserve.domain.model.GamePoint
import com.secondserve.domain.model.MatchScore
import com.secondserve.domain.model.Player
import com.secondserve.domain.model.SetResult
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MatchScoreDtoTest {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @Test
    fun `MatchScore round-trips to DTO and back`() {
        val original = MatchScore(
            completedSets = listOf(SetResult(6, 4)),
            currentSetGamesA = 3,
            currentSetGamesB = 2,
            currentGamePointsA = GamePoint.THIRTY,
            currentGamePointsB = GamePoint.FIFTEEN
        )
        val dto = original.toDto()
        val restored = dto.toDomain()
        assertEquals(original, restored)
    }

    @Test
    fun `ScoreEventPayload serializes to valid JSON`() {
        val score = MatchScore(currentSetGamesA = 1)
        val payload = ScoreEventPayload(ts = 1234567890L, score = score.toDto())
        val json = moshi.adapter(ScoreEventPayload::class.java).toJson(payload)
        assertTrue(json.contains("\"type\":\"SCORE_EVENT\""))
        assertTrue(json.contains("\"ts\":1234567890"))
        assertTrue(json.contains("\"score\""))
    }

    @Test
    fun `GameOverPayload serializes with score_snapshot field`() {
        val score = MatchScore()
        val payload = GameOverPayload(ts = 9999L, score_snapshot = score.toDto())
        val json = moshi.adapter(GameOverPayload::class.java).toJson(payload)
        assertTrue(json.contains("\"type\":\"GAME_OVER\""))
        assertTrue(json.contains("\"score_snapshot\""))
    }

    @Test
    fun `enum values serialized as strings`() {
        val score = MatchScore(
            currentGamePointsA = GamePoint.ADVANTAGE,
            matchWinner = Player.B
        )
        val dto = score.toDto()
        assertEquals("ADVANTAGE", dto.currentGamePointsA)
        assertEquals("B", dto.matchWinner)
    }

    @Test
    fun `null matchWinner serializes to null`() {
        val score = MatchScore(matchWinner = null)
        val dto = score.toDto()
        assertNull(dto.matchWinner)
        val restored = dto.toDomain()
        assertNull(restored.matchWinner)
    }

    @Test
    fun `toDomain throws on unknown GamePoint string`() {
        val dto = MatchScoreDto(
            completedSets = emptyList(),
            currentSetGamesA = 0,
            currentSetGamesB = 0,
            currentGamePointsA = "INVALID_POINT",
            currentGamePointsB = "ZERO",
            tieBreakPointsA = 0,
            tieBreakPointsB = 0,
            isTieBreak = false,
            isSuperTieBreak = false,
            isMatchOver = false,
            matchWinner = null
        )
        assertThrows<IllegalArgumentException> { dto.toDomain() }
    }

    @Test
    fun `toDomain throws on unknown Player string`() {
        val dto = MatchScoreDto(
            completedSets = emptyList(),
            currentSetGamesA = 0,
            currentSetGamesB = 0,
            currentGamePointsA = "ZERO",
            currentGamePointsB = "ZERO",
            tieBreakPointsA = 0,
            tieBreakPointsB = 0,
            isTieBreak = false,
            isSuperTieBreak = false,
            isMatchOver = true,
            matchWinner = "C"
        )
        assertThrows<IllegalArgumentException> { dto.toDomain() }
    }
}
