package com.secondserve.feature.coaching

import com.secondserve.domain.model.CoachingAnalysis
import com.secondserve.domain.model.CoachingSynthesis

data class CoachingUiState(
    val isLoading: Boolean = false,
    val analyses: List<CoachingAnalysis> = emptyList(),
    val synthesis: CoachingSynthesis? = null,
    val synthesisInProgress: Boolean = false,
    val error: String? = null
)

sealed class CoachingSideEffect
