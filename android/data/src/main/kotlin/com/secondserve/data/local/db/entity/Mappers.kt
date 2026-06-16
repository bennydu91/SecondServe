package com.secondserve.data.local.db.entity

import com.secondserve.domain.model.PlayerProfile
import com.secondserve.domain.model.RankingEntry

fun PlayerProfileEntity.toDomain(): PlayerProfile = PlayerProfile(
    id = id,
    currentSeries = currentSeries,
    currentPoints = currentPoints,
    updatedAt = updatedAt
)

fun RankingHistoryEntity.toDomain(): RankingEntry = RankingEntry(
    id = id,
    series = series,
    points = points,
    recordedAt = recordedAt
)
