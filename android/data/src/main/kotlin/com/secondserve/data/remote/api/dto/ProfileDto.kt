package com.secondserve.data.remote.api.dto

import com.squareup.moshi.Json

data class RankingRequest(
    val series: String,
    val points: Int
)

data class RankingEntryDto(
    val id: Int,
    val series: String,
    val points: Int,
    @Json(name = "recorded_at") val recordedAt: Long
)

data class ProfileSummaryDto(
    @Json(name = "current_series") val currentSeries: String?,
    @Json(name = "current_points") val currentPoints: Int?,
    @Json(name = "ranking_history") val rankingHistory: List<RankingEntryDto>
)
