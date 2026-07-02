package com.secondserve.data.remote.api.dto

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LiveShareDtoTest {

    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    @Test
    fun `CreateShareRequest serializes session_id in snake_case`() {
        val adapter = moshi.adapter(CreateShareRequest::class.java)
        val json = adapter.toJson(CreateShareRequest(sessionId = 42L))
        assert(json.contains("\"session_id\":42")) { "expected snake_case session_id in $json" }
    }

    @Test
    fun `CreateShareResponse deserializes from snake_case JSON`() {
        val adapter = moshi.adapter(CreateShareResponse::class.java)
        val json = """{"token":"tok-abc","url":"https://second-serve.app/live/tok-abc"}"""
        val dto = adapter.fromJson(json)
        assertNotNull(dto)
        assertEquals("tok-abc", dto.token)
        assertEquals("https://second-serve.app/live/tok-abc", dto.url)
    }

    @Test
    fun `LiveSetResultDto round-trips games_a and games_b in snake_case`() {
        val adapter = moshi.adapter(LiveSetResultDto::class.java)
        val original = LiveSetResultDto(gamesA = 6, gamesB = 4)
        val json = adapter.toJson(original)
        assert(json.contains("\"games_a\":6")) { "expected snake_case games_a in $json" }
        assert(json.contains("\"games_b\":4")) { "expected snake_case games_b in $json" }
        val restored = adapter.fromJson(json)
        assertEquals(original, restored)
    }

    @Test
    fun `LiveScoreUpdateRequest serializes every field in snake_case`() {
        val adapter = moshi.adapter(LiveScoreUpdateRequest::class.java)
        val request = LiveScoreUpdateRequest(
            completedSets = listOf(LiveSetResultDto(gamesA = 6, gamesB = 3)),
            currentSetGamesA = 2,
            currentSetGamesB = 1,
            currentSetPointLog = listOf("A", "A", "B"),
            currentGamePointsA = "THIRTY",
            currentGamePointsB = "FIFTEEN",
            tieBreakPointsA = 0,
            tieBreakPointsB = 0,
            isTieBreak = false,
            isSuperTieBreak = false,
            isMatchOver = false,
            matchWinner = null,
            playerAName = "Alice",
            playerBName = "Bob",
            surface = "CLAY",
            tournament = "Roland-Garros",
            competitionType = "TOURNAMENT",
            startedAt = 1700000000000L
        )
        val json = adapter.toJson(request)

        assert(json.contains("\"completed_sets\"")) { json }
        assert(json.contains("\"current_set_games_a\":2")) { json }
        assert(json.contains("\"current_set_games_b\":1")) { json }
        assert(json.contains("\"current_set_point_log\"")) { json }
        assert(json.contains("\"current_game_points_a\":\"THIRTY\"")) { json }
        assert(json.contains("\"current_game_points_b\":\"FIFTEEN\"")) { json }
        assert(json.contains("\"tie_break_points_a\":0")) { json }
        assert(json.contains("\"tie_break_points_b\":0")) { json }
        assert(json.contains("\"is_tie_break\":false")) { json }
        assert(json.contains("\"is_super_tie_break\":false")) { json }
        assert(json.contains("\"is_match_over\":false")) { json }
        // Moshi omits null-valued fields by default (no serializeNulls()), so match_winner
        // is simply absent from the JSON here rather than rendered as "match_winner":null.
        assert(!json.contains("\"match_winner\"")) { json }
        assert(json.contains("\"player_a_name\":\"Alice\"")) { json }
        assert(json.contains("\"player_b_name\":\"Bob\"")) { json }
        assert(json.contains("\"surface\":\"CLAY\"")) { json }
        assert(json.contains("\"tournament\":\"Roland-Garros\"")) { json }
        assert(json.contains("\"competition_type\":\"TOURNAMENT\"")) { json }
        assert(json.contains("\"started_at\":1700000000000")) { json }

        val restored = adapter.fromJson(json)
        assertEquals(request, restored)
    }

    @Test
    fun `LiveScoreUpdateRequest round-trips a match-over payload with a winner`() {
        val adapter = moshi.adapter(LiveScoreUpdateRequest::class.java)
        val request = LiveScoreUpdateRequest(
            completedSets = listOf(LiveSetResultDto(6, 4), LiveSetResultDto(6, 2)),
            currentSetGamesA = 0,
            currentSetGamesB = 0,
            currentSetPointLog = emptyList(),
            currentGamePointsA = "ZERO",
            currentGamePointsB = "ZERO",
            tieBreakPointsA = 0,
            tieBreakPointsB = 0,
            isTieBreak = false,
            isSuperTieBreak = false,
            isMatchOver = true,
            matchWinner = "A",
            playerAName = "Alice",
            playerBName = "Bob",
            surface = "HARD",
            tournament = null,
            competitionType = null,
            startedAt = 1700000001000L
        )
        val json = adapter.toJson(request)
        assert(json.contains("\"match_winner\":\"A\"")) { json }
        // Same Moshi null-omission behavior as above: absent, not rendered as null.
        assert(!json.contains("\"tournament\"")) { json }
        assert(!json.contains("\"competition_type\"")) { json }
        val restored = adapter.fromJson(json)
        assertEquals(request, restored)
    }
}
