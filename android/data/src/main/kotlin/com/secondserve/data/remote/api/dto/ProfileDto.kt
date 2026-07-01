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
    @Json(name = "display_name") val displayName: String?,
    val club: String?,
    @Json(name = "current_series") val currentSeries: String?,
    @Json(name = "current_points") val currentPoints: Int?,
    @Json(name = "ranking_history") val rankingHistory: List<RankingEntryDto>,
    @Json(name = "play_style") val playStyle: String?,
    @Json(name = "preferred_surfaces") val preferredSurfaces: String?,
    @Json(name = "coach_instruction_1") val coachInstruction1: String?,
    @Json(name = "coach_instruction_2") val coachInstruction2: String?,
    @Json(name = "coach_instruction_3") val coachInstruction3: String?
)

data class ProfileDetailsRequest(
    @Json(name = "display_name") val displayName: String? = null,
    val club: String? = null,
    @Json(name = "play_style") val playStyle: String?,
    @Json(name = "preferred_surfaces") val preferredSurfaces: String?,
    @Json(name = "coach_instruction_1") val coachInstruction1: String?,
    @Json(name = "coach_instruction_2") val coachInstruction2: String?,
    @Json(name = "coach_instruction_3") val coachInstruction3: String?
)

data class ProfileDetailsResponse(
    @Json(name = "updated_at") val updatedAt: Long
)
