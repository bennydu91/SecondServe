package com.secondserve.domain.model

data class PlayerProfile(
    val id: Int = 1,
    val displayName: String?,
    val club: String?,
    val currentSeries: String?,
    val currentPoints: Int?,
    val playStyle: String?,
    val preferredSurfaces: List<String>,
    val coachInstruction1: String?,
    val coachInstruction2: String?,
    val coachInstruction3: String?,
    val updatedAt: Long
)
