package com.secondserve.feature.history

sealed class StatsUiState {
    object Loading : StatsUiState()
    data class Content(val stats: AggregatedStats) : StatsUiState()
    data class Error(val message: String) : StatsUiState()
}
