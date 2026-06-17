package com.secondserve.data.wearable.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class ScoreEventPayload(
    @Json(name = "type") val type: String = "SCORE_EVENT",
    @Json(name = "ts") val ts: Long,
    @Json(name = "score") val score: MatchScoreDto
)
