package com.secondserve.data.local.db.entity

import com.secondserve.domain.model.PlayerProfile
import com.secondserve.domain.model.RankingEntry
import com.secondserve.domain.model.WorkAxis

fun String?.toPreferredSurfacesList(): List<String> =
    this?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

fun PlayerProfileEntity.toDomain(): PlayerProfile = PlayerProfile(
    id = id,
    currentSeries = currentSeries,
    currentPoints = currentPoints,
    playStyle = playStyle,
    preferredSurfaces = preferredSurfaces.toPreferredSurfacesList(),
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

fun WorkAxisEntity.toDomain(): WorkAxis = WorkAxis(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun List<String>.toPreferredSurfacesString(): String? =
    if (isEmpty()) null else joinToString(",")
