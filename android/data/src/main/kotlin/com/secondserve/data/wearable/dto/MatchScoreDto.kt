package com.secondserve.data.wearable.dto

import com.secondserve.domain.model.GamePoint
import com.secondserve.domain.model.MatchScore
import com.secondserve.domain.model.Player
import com.secondserve.domain.model.SetResult
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
data class SetResultDto(
    @Json(name = "gamesA") val gamesA: Int,
    @Json(name = "gamesB") val gamesB: Int
)

@JsonClass(generateAdapter = false)
data class MatchScoreDto(
    @Json(name = "completedSets") val completedSets: List<SetResultDto>,
    @Json(name = "currentSetGamesA") val currentSetGamesA: Int,
    @Json(name = "currentSetGamesB") val currentSetGamesB: Int,
    @Json(name = "currentGamePointsA") val currentGamePointsA: String,
    @Json(name = "currentGamePointsB") val currentGamePointsB: String,
    @Json(name = "tieBreakPointsA") val tieBreakPointsA: Int,
    @Json(name = "tieBreakPointsB") val tieBreakPointsB: Int,
    @Json(name = "isTieBreak") val isTieBreak: Boolean,
    @Json(name = "isSuperTieBreak") val isSuperTieBreak: Boolean,
    @Json(name = "isMatchOver") val isMatchOver: Boolean,
    @Json(name = "matchWinner") val matchWinner: String?
)

fun MatchScore.toDto() = MatchScoreDto(
    completedSets = completedSets.map { SetResultDto(it.gamesA, it.gamesB) },
    currentSetGamesA = currentSetGamesA,
    currentSetGamesB = currentSetGamesB,
    currentGamePointsA = currentGamePointsA.name,
    currentGamePointsB = currentGamePointsB.name,
    tieBreakPointsA = tieBreakPointsA,
    tieBreakPointsB = tieBreakPointsB,
    isTieBreak = isTieBreak,
    isSuperTieBreak = isSuperTieBreak,
    isMatchOver = isMatchOver,
    matchWinner = matchWinner?.name
)

fun MatchScoreDto.toDomain() = MatchScore(
    completedSets = completedSets.map { SetResult(it.gamesA, it.gamesB) },
    currentSetGamesA = currentSetGamesA,
    currentSetGamesB = currentSetGamesB,
    currentGamePointsA = GamePoint.valueOf(currentGamePointsA),
    currentGamePointsB = GamePoint.valueOf(currentGamePointsB),
    tieBreakPointsA = tieBreakPointsA,
    tieBreakPointsB = tieBreakPointsB,
    isTieBreak = isTieBreak,
    isSuperTieBreak = isSuperTieBreak,
    isMatchOver = isMatchOver,
    matchWinner = matchWinner?.let { Player.valueOf(it) }
)
