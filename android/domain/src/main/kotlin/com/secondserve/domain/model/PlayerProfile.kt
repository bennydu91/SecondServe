package com.secondserve.domain.model

data class PlayerProfile(
    val id: Int = 1,
    val currentSeries: String?,
    val currentPoints: Int?,
    val updatedAt: Long
)
