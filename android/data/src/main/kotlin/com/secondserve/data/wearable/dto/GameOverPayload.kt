package com.secondserve.data.wearable.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class GameOverPayload(
    @Json(name = "type") val type: String = "GAME_OVER",
    @Json(name = "ts") val ts: Long,
    @Json(name = "score_snapshot") val score_snapshot: MatchScoreDto
)
