package com.secondserve.domain.model

data class RankingEntry(
    val id: Int = 0,
    val series: String,
    val points: Int,
    val recordedAt: Long
)
