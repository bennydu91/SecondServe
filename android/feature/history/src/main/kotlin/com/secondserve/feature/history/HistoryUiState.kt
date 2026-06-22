package com.secondserve.feature.history

import com.secondserve.domain.model.Session

sealed class HistoryUiState {
    object Loading : HistoryUiState()
    data class Content(val sessions: List<Session>) : HistoryUiState()
    data class Error(val message: String) : HistoryUiState()
}

sealed class HistorySideEffect {
    data class NavigateToDetail(val sessionId: Long) : HistorySideEffect()
}

internal fun Session.resultLabel(): String = when (result) {
    "VICTORY" -> "Victoire"
    "DEFEAT" -> "Défaite"
    "DRAW" -> "Nul"
    "ABANDONED" -> "Abandonné"
    else -> "N/A"
}
