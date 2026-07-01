package com.secondserve.data.remote.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SyncPushRequest(
    val sessions: List<SyncSessionDto>,
    @Json(name = "deleted_session_ids") val deletedSessionIds: List<Long> = emptyList()
)

@JsonClass(generateAdapter = true)
data class SyncSessionDto(
    @Json(name = "client_id") val clientId: Long,
    val surface: String,
    @Json(name = "match_format") val matchFormat: String,
    @Json(name = "third_set_rule") val thirdSetRule: String,
    val opponent: String?,
    @Json(name = "competition_type") val competitionType: String?,
    val tournament: String?,
    val status: String,
    @Json(name = "session_type") val sessionType: String,
    val result: String?,
    @Json(name = "feeling_rating") val feelingRating: Int?,
    @Json(name = "feeling_comment") val feelingComment: String?,
    @Json(name = "created_at") val createdAt: Long,
    @Json(name = "updated_at") val updatedAt: Long,
    @Json(name = "scheduled_at") val scheduledAt: Long? = null,
    @Json(name = "score_text") val scoreText: String? = null,
    @Json(name = "first_serve_percent_self") val firstServePercentSelf: Int? = null,
    @Json(name = "first_serve_percent_opponent") val firstServePercentOpponent: Int? = null,
    @Json(name = "winners_self") val winnersSelf: Int? = null,
    @Json(name = "winners_opponent") val winnersOpponent: Int? = null
)

@JsonClass(generateAdapter = true)
data class SyncPushResponse(
    @Json(name = "synced_sessions") val syncedSessions: Int
)
