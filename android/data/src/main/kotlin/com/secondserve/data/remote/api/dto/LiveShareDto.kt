package com.secondserve.data.remote.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CreateShareRequest(
    @Json(name = "session_id") val sessionId: Long
)

@JsonClass(generateAdapter = true)
data class CreateShareResponse(
    val token: String,
    val url: String
)

@JsonClass(generateAdapter = true)
data class LiveSetResultDto(
    @Json(name = "games_a") val gamesA: Int,
    @Json(name = "games_b") val gamesB: Int
)

@JsonClass(generateAdapter = true)
data class LiveScoreUpdateRequest(
    @Json(name = "completed_sets") val completedSets: List<LiveSetResultDto>,
    @Json(name = "current_set_games_a") val currentSetGamesA: Int,
    @Json(name = "current_set_games_b") val currentSetGamesB: Int,
    @Json(name = "current_set_point_log") val currentSetPointLog: List<String>,
    @Json(name = "current_game_points_a") val currentGamePointsA: String,
    @Json(name = "current_game_points_b") val currentGamePointsB: String,
    @Json(name = "tie_break_points_a") val tieBreakPointsA: Int,
    @Json(name = "tie_break_points_b") val tieBreakPointsB: Int,
    @Json(name = "is_tie_break") val isTieBreak: Boolean,
    @Json(name = "is_super_tie_break") val isSuperTieBreak: Boolean,
    @Json(name = "is_match_over") val isMatchOver: Boolean,
    @Json(name = "match_winner") val matchWinner: String?,
    @Json(name = "player_a_name") val playerAName: String,
    @Json(name = "player_b_name") val playerBName: String,
    val surface: String,
    val tournament: String?,
    @Json(name = "competition_type") val competitionType: String?,
    @Json(name = "started_at") val startedAt: Long
)
