package com.secondserve.domain.model

data class LiveShareContext(
    val playerAName: String,
    val playerBName: String,
    val surface: String,
    val tournament: String?,
    val competitionType: String?,
    val startedAt: Long
)
