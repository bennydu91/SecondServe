package com.secondserve.data.local.db.entity

import com.secondserve.domain.model.PlayerProfile
import com.secondserve.domain.model.RankingEntry

fun PlayerProfileEntity.toDomain(): PlayerProfile = PlayerProfile(
    id = id,
    currentSeries = currentSeries,
    currentPoints = currentPoints,
    playStyle = playStyle,
    preferredSurfaces = preferredSurfaces
        ?.split(",")
        ?.filter { it.isNotBlank() }
        ?: emptyList(),
    coachInstruction1 = coachInstruction1,
    coachInstruction2 = coachInstruction2,
    coachInstruction3 = coachInstruction3,
    updatedAt = updatedAt
)

fun RankingHistoryEntity.toDomain(): RankingEntry = RankingEntry(
    id = id,
    series = series,
    points = points,
    recordedAt = recordedAt
)

fun List<String>.toPreferredSurfacesString(): String? =
    if (isEmpty()) null else joinToString(",")
