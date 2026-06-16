package com.secondserve.domain.model

data class MatchContextProfile(
    val fftSeries: String? = null,
    val playStyle: String? = null,
    val activeWorkAxes: List<String> = emptyList()
)
