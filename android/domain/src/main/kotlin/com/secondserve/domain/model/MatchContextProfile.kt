package com.secondserve.domain.model

data class MatchContextProfile(
    val fftSeries: String? = null,
    val playStyle: String? = null,
    val preferredSurfaces: List<String> = emptyList(),
    val coachInstructions: List<String> = emptyList(),
    val activeWorkAxes: List<String> = emptyList()
)
