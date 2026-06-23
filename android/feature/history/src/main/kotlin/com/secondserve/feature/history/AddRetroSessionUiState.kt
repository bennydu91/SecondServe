package com.secondserve.feature.history

import com.secondserve.domain.model.MatchFormat
import com.secondserve.domain.model.ThirdSetRule

data class AddRetroSessionUiState(
    val selectedSurface: String? = null,
    val selectedMatchFormat: MatchFormat? = null,
    val selectedThirdSetRule: ThirdSetRule? = null,
    val opponent: String = "",
    val competitionType: String = "",
    val tournament: String = "",
    val scoreText: String = "",
    val selectedResult: String? = null,
    val matchDateMillis: Long? = null,
    val isLoading: Boolean = false
) {
    val canSubmit: Boolean get() =
        !isLoading &&
        selectedSurface != null &&
        selectedMatchFormat != null &&
        (selectedMatchFormat == MatchFormat.BEST_OF_1 || selectedThirdSetRule != null) &&
        selectedResult != null &&
        matchDateMillis != null
}

sealed class AddRetroSessionSideEffect {
    object SessionCreated : AddRetroSessionSideEffect()
    data class ShowError(val message: String) : AddRetroSessionSideEffect()
}
