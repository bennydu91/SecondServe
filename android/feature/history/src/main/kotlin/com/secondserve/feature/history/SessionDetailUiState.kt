package com.secondserve.feature.history

import com.secondserve.domain.model.CoachingCacheEntry
import com.secondserve.domain.model.Session

sealed class SessionDetailUiState {
    object Loading : SessionDetailUiState()
    data class Content(val session: Session, val advices: List<CoachingCacheEntry>) : SessionDetailUiState()
    data class Error(val message: String) : SessionDetailUiState()
}
