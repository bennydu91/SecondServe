package com.secondserve.data.local.db.entity

import com.secondserve.data.remote.api.dto.SyncSessionDto
import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.PlayerProfile
import com.secondserve.domain.model.RankingEntry
import com.secondserve.domain.model.Session
import com.secondserve.domain.model.SessionFormat
import com.secondserve.domain.model.SessionStatus
import com.secondserve.domain.model.SessionType
import com.secondserve.domain.model.ThirdSetRule
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

fun SessionEntity.toDomain(): Session = Session(
    id = id,
    surface = surface,
    format = SessionFormat(
        matchFormat = MatchFormat.valueOf(matchFormat),
        thirdSetRule = ThirdSetRule.valueOf(thirdSetRule)
    ),
    opponent = opponent,
    competitionType = competitionType,
    tournament = tournament,
    status = SessionStatus.valueOf(status),
    sessionType = SessionType.valueOf(sessionType),
    result = result,
    feelingRating = feelingRating,
    feelingComment = feelingComment,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Session.toEntity(): SessionEntity = SessionEntity(
    id = id,
    surface = surface,
    matchFormat = format.matchFormat.name,
    thirdSetRule = format.thirdSetRule.name,
    opponent = opponent,
    competitionType = competitionType,
    tournament = tournament,
    status = status.name,
    sessionType = sessionType.name,
    result = result,
    feelingRating = feelingRating,
    feelingComment = feelingComment,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Session.toSyncDto(): SyncSessionDto = SyncSessionDto(
    clientId = id,
    surface = surface,
    matchFormat = format.matchFormat.name,
    thirdSetRule = format.thirdSetRule.name,
    opponent = opponent,
    competitionType = competitionType,
    tournament = tournament,
    status = status.name,
    sessionType = sessionType.name,
    result = result,
    feelingRating = feelingRating,
    feelingComment = feelingComment,
    createdAt = createdAt,
    updatedAt = updatedAt
)
